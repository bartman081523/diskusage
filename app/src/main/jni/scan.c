/*
 * Copyright (C) 2008-2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/*
 * Fast directory tree scanner.
 *
 * The scanner serialises one entity per record:
 *
 *   'D' | name NUL | blocks NUL | bytes NUL      (directory)
 *   'F' | name NUL | blocks NUL | bytes NUL      (file)
 *   'D' | name NUL | "<No access>" | ...         (unreadable directory)
 *   'Z'                                          (end of directory listing)
 *
 *
 * Historically the code was compiled as a shared library that was executed as
 * a child process ("rename it to lib*.so so Android lets you exec it"). That
 * stopped working: the resulting ELF is a shared object without a startup
 * routine, so exec() crashes immediately on modern Android. The tree walk is
 * now called through JNI; the child process path is only kept as a last resort
 * for root mode.
 */

#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <stdlib.h>
#include <jni.h>
#include <pthread.h>

#define MAX_OUTPUT_BYTES (128u * 1024u * 1024u)

static dev_t dev;
static const char sep = 0;

static char *out_buf;
static size_t out_len;
static size_t out_cap;
static int out_overflow;
static pthread_mutex_t out_lock = PTHREAD_MUTEX_INITIALIZER;

struct Entity {
  long long sizeInBlocks;
  long long sizeInBytes;
  const char *name;
  struct Entity *next;
  char isdir;
};

void scan_dir(const char *path, struct Entity *entity);

static const char *getName(const char *path) {
  const char *slash = strrchr(path, '/');
  return slash ? slash + 1 : path;
}

static int out_reserve(size_t extra) {
  if (out_overflow) return -1;
  if (out_len + extra > MAX_OUTPUT_BYTES) {
    out_overflow = 1;
    return -1;
  }
  if (out_len + extra <= out_cap) return 0;
  size_t new_cap = out_cap ? out_cap * 2 : 65536;
  while (new_cap < out_len + extra) new_cap *= 2;
  if (new_cap > MAX_OUTPUT_BYTES) new_cap = MAX_OUTPUT_BYTES;
  char *grown = (char *) realloc(out_buf, new_cap);
  if (grown == NULL) {
    out_overflow = 1;
    return -1;
  }
  out_buf = grown;
  out_cap = new_cap;
  return 0;
}

static void out_bytes(const char *data, size_t len) {
  if (out_reserve(len) != 0) return;
  memcpy(out_buf + out_len, data, len);
  out_len += len;
}

static void out_char(char c) {
  out_bytes(&c, 1);
}

static void out_str(const char *s) {
  out_bytes(s, strlen(s));
}

static void out_long(long long value) {
  char buffer[32];
  int len = snprintf(buffer, sizeof(buffer), "%lld", value);
  if (len > 0) out_bytes(buffer, (size_t) len);
}

static void out_reset(void) {
  out_len = 0;
  out_overflow = 0;
}

static void dump(char type) {
  out_char(type);
}

static void dump_file(struct Entity *entity) {
  out_char(entity->isdir ? 'D' : 'F');
  out_str(entity->name);
  out_char(sep);
  out_long(entity->sizeInBlocks);
  out_char(sep);
  out_long(entity->sizeInBytes);
  out_char(sep);
}

static const char *get_error(void) {
  switch (errno) {
    case EACCES:
      return " <No access>";
    case ENOENT:
    case ENOTDIR:
      return " <deleted>";
    default:
      return " <error>";
  }
}

static void dump_error(char type, const char *path,
                       long long sizeInBlocks, long long sizeInBytes) {
  const char *suffix = get_error();
  out_char(type);
  out_str(getName(path));
  out_str(suffix);
  out_char(sep);
  out_long(sizeInBlocks);
  out_char(sep);
  out_long(sizeInBytes);
  out_char(sep);
}

static struct Entity *make_entity_internal(const char *path, struct stat *stbuf) {
  struct Entity *e = (struct Entity *) malloc(sizeof(struct Entity));
  if (e == NULL) return NULL;
  e->name = strdup(getName(path));
  if (e->name == NULL) {
    free(e);
    return NULL;
  }
  e->sizeInBlocks = stbuf->st_blocks;
  e->sizeInBytes = stbuf->st_size;
  e->isdir = S_ISDIR(stbuf->st_mode);
  e->next = NULL;
  return e;
}

static struct Entity *make_entity(const char *path) {
  struct stat stbuf;
  /* lstat(): symbolic links are reported as links and never followed, so a
     link does not "take" the space of its target. */
  if (lstat(path, &stbuf) < 0) return NULL;
  if (stbuf.st_dev != dev) return NULL;
  return make_entity_internal(path, &stbuf);
}

static char *makePath(const char *base, const char *name) {
  size_t baseLen = strlen(base);
  size_t nameLen = strlen(name);
  char *res = (char *) malloc(baseLen + nameLen + 2);
  if (res == NULL) return NULL;
  memcpy(res, base, baseLen);
  res[baseLen] = '/';
  memcpy(res + baseLen + 1, name, nameLen);
  res[baseLen + 1 + nameLen] = 0;
  return res;
}

void scan_dir(const char *path, struct Entity *dirEntity) {
  struct Entity *curr;
  struct Entity *prev;
  struct Entity *first = NULL;
  struct Entity **last = &first;

  DIR *dir = opendir(path);
  if (dir == NULL) {
    dump_error('D', path, dirEntity->sizeInBlocks, dirEntity->sizeInBytes);
    dump('Z');
    return;
  }
  dump_file(dirEntity);

  struct dirent *entity;
  while ((entity = readdir(dir)) != NULL) {
    if (entity->d_name[0] == 0 ||
        (entity->d_name[0] == '.' &&
         (entity->d_name[1] == 0 ||
          (entity->d_name[1] == '.' && entity->d_name[2] == 0)))) {
      continue;
    }
    char *entityPath = makePath(path, entity->d_name);
    if (entityPath == NULL) continue;
    struct Entity *e = make_entity(entityPath);
    free(entityPath);
    if (e == NULL) continue;
    if (!e->isdir) {
      dump_file(e);
      free((void *) e->name);
      free(e);
      continue;
    }
    *last = e;
    last = &(e->next);
  }
  *last = NULL;

  closedir(dir);

  curr = first;
  while (curr != NULL) {
    char *entityPath = makePath(path, curr->name);
    if (entityPath != NULL) {
      scan_dir(entityPath, curr);
      free(entityPath);
    }
    free((void *) curr->name);
    prev = curr;
    curr = curr->next;
    free(prev);
  }
  dump('Z');
}

static void scan_tree(const char *path) {
  struct stat stbuf;
  if (lstat(path, &stbuf) == -1) {
    dump_error('D', path, 1, 0);
    dump('Z');
    return;
  }
  dev = stbuf.st_dev;
  struct Entity *root = make_entity_internal(path, &stbuf);
  if (root == NULL) {
    dump_error('D', path, 1, 0);
    dump('Z');
    return;
  }
  scan_dir(path, root);
  free((void *) root->name);
  free(root);
}

/*
 * JNI entry point: runs the scan and returns the serialised tree.
 *
 * Returns NULL when the tree does not fit into MAX_OUTPUT_BYTES; the caller
 * then falls back to the (slower) Java scanner.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_google_android_diskusage_datasource_fast_NativeScannerStream_scanNative(
    JNIEnv *env, jclass clazz, jstring jpath) {
  (void) clazz;
  const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
  if (path == NULL) return NULL;

  pthread_mutex_lock(&out_lock);
  out_reset();
  scan_tree(path);
  (*env)->ReleaseStringUTFChars(env, jpath, path);

  jbyteArray result = NULL;
  if (!out_overflow && out_len > 0) {
    result = (*env)->NewByteArray(env, (jsize) out_len);
    if (result != NULL) {
      (*env)->SetByteArrayRegion(env, result, 0, (jsize) out_len,
                                 (const jbyte *) out_buf);
    }
  }
  free(out_buf);
  out_buf = NULL;
  out_cap = 0;
  out_len = 0;
  pthread_mutex_unlock(&out_lock);
  return result;
}

/* Fallback entry point for the legacy child-process mode. */
int main(int argc, char **argv) {
  if (argc < 2) {
    fprintf(stderr, "Usage: %s <directory>\n", argv[0]);
    return 1;
  }
  if (strchr(argv[1], '/') == NULL) {
    fprintf(stderr, "Need an absolute path\n");
    return 1;
  }
  out_reset();
  scan_tree(argv[1]);
  if (out_len > 0) {
    fwrite(out_buf, 1, out_len, stdout);
    fflush(stdout);
  }
  free(out_buf);
  out_buf = NULL;
  return out_overflow ? 2 : 0;
}
