# Publishing on F-Droid

Everything the F-Droid build server needs is already in this repository:

* `fastlane/metadata/android/{en-US,de-DE}/…` — title, short and full
  description, changelog, icon, feature graphic and phone screenshots.
* An unsigned release build (`./gradlew assembleRelease` without
  `keystore.properties`) — F-Droid signs the APK with its own key.
* No prebuilt binaries apart from the Gradle wrapper and the source-built
  `libscan.so` (built from `app/src/main/jni` with NDK r28c).
* No anti-features: no ads, no tracking, no network permission.

## Submission

F-Droid does not pull repositories automatically. Inclusion is requested with a
merge request against [fdroiddata](https://gitlab.com/fdroid/fdroiddata):

1. Fork `https://gitlab.com/fdroid/fdroiddata`.
2. Create `metadata/com.google.android.diskusage.yml` with the content of
   [`com.google.android.diskusage.yml`](com.google.android.diskusage.yml)
   (adjust `CurrentVersion`, `CurrentVersionCode` and `Builds` to the latest
   release).
3. Verify the metadata locally:

   ```bash
   fdroid lint com.google.android.diskusage
   fdroid readmeta
   fdroid rewritemeta com.google.android.diskusage
   fdroid checkupdates com.google.android.diskusage
   ```

4. Commit, push to your fork and open a merge request titled
   `New app: DiskUsage (com.google.android.diskusage)`.
5. Answer the reviewer questions. The most likely ones:
   * **`MANAGE_EXTERNAL_STORAGE`** — needed because the app's whole purpose is
     analysing all files; without it only the app's sandbox is readable.
   * **`QUERY_ALL_PACKAGES`** — needed to list installed applications and their
     sizes.
   * **NDK build** — `app/src/main/jni/Android.mk` is built by AGP's
     `ndkBuild` integration; no prebuilt `.so` is shipped.
6. After the MR is merged the app appears in the F-Droid repository with the
   next index update. Subsequent releases are picked up automatically through
   the Git tags (`v*`) created by this repository's release workflow.

## Version codes

`versionCode = major * 10000 + minor * 100 + patch` (`5.1.2` → `50102`), derived
from `version.properties`. The first version published here (`5.0.0` → `50000`)
is higher than the old `5.0-alpha1` (`5001`) so existing installations can
upgrade.

If F-Droid's version auto-detection needs the mapping explicitly, add:

```yaml
VercodeOperation:
  - '10000 * %c1 + 100 * %c2 + %c3'
```

## Maintainer checklist for a new release

```bash
./scripts/bump-version.sh minor     # or patch/major
git tag v$(sed -n 's/^versionName=//p' version.properties)
git push origin --tags
```

The release workflow builds, signs (if secrets are configured) and publishes the
APK. F-Droid then picks up the new tag, provided its metadata references the
repository's tags.
