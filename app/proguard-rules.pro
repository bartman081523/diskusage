# Keep the Timber log calls but make sure no logging remains in release builds.
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# The application class is referenced from the manifest.
-keep class com.google.android.diskusage.DiskUsageApplication { *; }

# Keep the AIDL stub used for package size lookups on older platform versions.
-keep class com.google.android.diskusage.datasource.AppStatsCallback { *; }
