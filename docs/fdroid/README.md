# Publishing on F-Droid

**Status:** not submitted yet. F-Droid answered the earlier request for
`com.google.android.diskusage` with:

> „We don't accept forks without original dev's approval."
> „please change appid, icon, pics, text, and, after 3 months, submit it as a new
> app, see Policy Quality point 6"

So the app was rebranded to **DiskUsage NG** with the new app id
`org.org.diskusageng` (see the history below). A new-app submission is possible
once the project has three months of active development (Inclusion Policy
"Quality" #6), i.e. from **February 2027** — or earlier if the "proven users"
exception can be demonstrated.

## What is required (and already done)

| Requirement (Inclusion Policy "Forks") | Status |
|---|---|
| fresh Android application ID | ✅ `org.org.diskusageng` |
| different name | ✅ "DiskUsage NG" (non-translatable `app_name`) |
| different icon | ✅ new navy/three-bar icon, adaptive + monochrome + TV banner |
| different strings / store texts | ✅ fastlane metadata rewritten (en + de) |
| store screenshots | ⏳ refresh before submitting |
| 3 months of active development (Quality #6) | ⏳ from Feb 2027 |

## Submission (when due)

1. Copy [`org.org.diskusageng.yml`](org.org.diskusageng.yml) to
   `metadata/org.org.diskusageng.yml` in an fdroiddata fork.
2. Run `fdroid lint org.org.diskusageng`,
   `fdroid rewritemeta org.org.diskusageng` and
   `fdroid checkupdates org.org.diskusageng`.
3. Push the branch and open a **"New app"** MR.
4. Answer the expected questions:
   * `MANAGE_EXTERNAL_STORAGE` — the app's core purpose is analysing all shared
     storage.
   * `QUERY_ALL_PACKAGES` — listing installed applications.
   * `PACKAGE_USAGE_STATS` — optional, only better per-app sizes.
   * Fork history — the original app is unmaintained since 2023, this is a fresh
     app id with its own name/icon/texts, exactly as the policy asks.

## History (closed MRs)

| MR | Result |
|---|---|
| [!58150](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/58150) | closed: CI checks failed (metadata not canonical, `checkupdates` lacked `UpdateCheckData`) |
| [!58153](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/58153) | closed: *"We don't accept forks without original dev's approval."* |
| [!58163](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/58163) | closed: *"please change appid, icon, pics, text, and, after 3 months, submit it as a new app"* — all pipeline checks were green |

## Local verification

```bash
python3 -m venv fdroidvenv && fdroidvenv/bin/pip install fdroidserver
git clone --depth 1 --filter=blob:none --sparse git@gitlab.com:fdroid/fdroiddata.git
cd fdroiddata && git sparse-checkout set --no-cone '/metadata/org.org.diskusageng.yml'
fdroidvenv/bin/fdroid lint org.org.diskusageng
```

`fdroid lint` needs `config/categories.yml`, `config/antiFeatures.yml` and the
`config/*.png` icons in the working tree; the sparse clone does not fetch them
(`git show HEAD:config/... > config/...`).

## What the fork is

Same code base as before, rebranded: AGP 9.5.1/Gradle 9.9.1/JDK 17, SDK 37,
NDK r28c, JNI scanner, `StorageManager` volume discovery, app sizes via
`PackageManager`/`StorageStatsManager`, edge-to-edge UI, permissions screen and
predictive-back handling.
