# Publishing on F-Droid

**Status:** DiskUsage already exists in F-Droid (`com.google.android.diskusage`,
last version there was `5.0-alpha1`). So this is an *update* of the existing app
entry, not a new package.

* Open merge request: <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/58153>
* Fork used for the MR: <https://gitlab.com/ch-zz/fdroiddata>
* Branch: `diskusage-5.0.1`
* Metadata file: [`com.google.android.diskusage.yml`](com.google.android.diskusage.yml)
  (copy of `metadata/com.google.android.diskusage.yml` in the MR)

## What the MR changes

| Field | Before | After |
|---|---|---|
| `Repo` / `SourceCode` / `IssueTracker` | `IvanVolosyuk/diskusage` | `bartman081523/diskusage` (maintained fork) |
| `Builds` | up to `5.0-alpha1` (`5001`) | adds `5.0.1` (`50001`) |
| `CurrentVersion` | `5.0-alpha1` | `5.0.1` |
| `MaintainerNotes` | system.jar note | fork/modernisation, version-code scheme, SDK/NDK notes |

The old build entries are kept so that F-Droid's history stays intact.

## Local verification

```bash
python3 -m venv fdroidvenv && fdroidvenv/bin/pip install fdroidserver
git clone --depth 1 --filter=blob:none --sparse git@gitlab.com:fdroid/fdroiddata.git
cd fdroiddata && git sparse-checkout set --no-cone '/metadata/com.google.android.diskusage.yml'
fdroidvenv/bin/fdroid lint com.google.android.diskusage      # passes
```

`fdroid lint` needs `config/categories.yml`, `config/antiFeatures.yml` and the
`config/*.png` icons in the working tree (the sparse clone does not fetch them).

## Updating the MR for the next release

1. Append a new `Builds` entry (commit `v<version>`, `output:` with the versioned
   APK name) and bump `CurrentVersion`/`CurrentVersionCode`.
2. Push to the fork; the MR updates automatically.

```bash
cd fdroiddata
git fetch origin master && git rebase origin/master
$EDITOR metadata/com.google.android.diskusage.yml
git commit -m "DiskUsage: update to 5.0.2" && git push fork diskusage-5.0.1
```

## Version codes

`versionCode = major * 10000 + minor * 100 + patch` (`5.1.2` → `50102`), derived
from `version.properties`. `5.0.1` → `50001` is the first version after the
`5.0-alpha1` (`5001`) that F-Droid built, so devices update normally.

## Possible review questions

* **`MANAGE_EXTERNAL_STORAGE`** — the app's purpose is analysing all shared
  storage; without it only the app sandbox is readable.
* **`QUERY_ALL_PACKAGES`** — needed to list installed applications and their
  sizes.
* **`PACKAGE_USAGE_STATS`** — optional, only improves per-app sizes.
* **SDK 37 / NDK r28c** — if the buildserver image lacks
  `platforms;android-37.0` or `build-tools;36.0.0`, they can be installed in
  `prebuild` via `sdkmanager`.
* **Fork instead of upstream** — upstream stopped at `5.0-alpha1`; the fork is
  the actively maintained source and ships the same app id, so users keep
  receiving updates.
