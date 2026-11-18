/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.google.android.diskusage.ui;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.ActivityCommonBinding;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.ui.common.EdgeToEdge;
import splitties.toast.ToastKt;
import timber.log.Timber;

/**
 * Requests the permissions the analyser needs before handing over to
 * {@link DiskUsage}.
 *
 * <ul>
 *   <li>Android 11+ (API 30): "All files access" - without it only the app's own
 *       directory is readable.</li>
 *   <li>Android 6..10: the classic READ/WRITE_EXTERNAL_STORAGE permissions.</li>
 *   <li>Usage access (optional): only needed for exact per-app data sizes. The
 *       app degrades gracefully and no longer blocks on it.</li>
 * </ul>
 *
 * Permissions are re-checked in {@link #onResume()} instead of relying on the
 * result callback, because some vendors don't deliver it (or return the user to
 * a different task).
 */
public class PermissionRequestActivity extends AppCompatActivity {
    private static final int DISKUSAGE_REQUEST_CODE = 10;

    private boolean forwarded = false;
    private boolean usageAccessAsked = false;

    private final ActivityResultLauncher<String[]> legacyStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (hasStorageAccess()) {
                            requestUsageAccessOrForward();
                        } else {
                            ToastKt.toast(R.string.dialog_external_storage_access_error);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityCommonBinding binding = ActivityCommonBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.applyInsets(binding.getRoot());

        Intent i = getIntent();
        final String key = i.getStringExtra(DiskUsage.KEY_KEY);
        if (key == null) {
            finish();
            return;
        }
        MountPoint mountPoint = MountPoint.getForKey(this, key);
        if (mountPoint == null) {
            finish();
            return;
        }

        if (hasStorageAccess()) {
            requestUsageAccessOrForward();
        } else {
            requestStorageAccess();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!forwarded && hasStorageAccess()) {
            requestUsageAccessOrForward();
        }
    }

    private void requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_all_files_access_title)
                    .setMessage(R.string.dialog_all_files_access_desc)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Throwable t) {
                            Timber.w(t, "Cannot open app specific all-files settings");
                            try {
                                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                            } catch (Throwable inner) {
                                ToastKt.toast(R.string.dialog_external_storage_access_error);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        } else {
            legacyStoragePermissionLauncher.launch(new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        }
    }

    private void requestUsageAccessOrForward() {
        if (forwarded) return;
        if (hasUsageAccess() || usageAccessAsked) {
            forwardToDiskUsage();
            return;
        }
        usageAccessAsked = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_usage_access_title)
                .setMessage(R.string.dialog_usage_access_desc)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (Throwable t) {
                        Timber.w(t, "Cannot open usage access settings");
                        forwardToDiskUsage();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> forwardToDiskUsage())
                .setOnCancelListener(dialog -> forwardToDiskUsage())
                .show();
    }

    public void forwardToDiskUsage() {
        if (forwarded) return;
        forwarded = true;
        Intent input = getIntent();
        Intent diskusage = new Intent(this, DiskUsage.class);
        diskusage.putExtra(DiskUsage.KEY_KEY, input.getStringExtra(DiskUsage.KEY_KEY));
        diskusage.putExtra(DiskUsage.STATE_KEY, input.getBundleExtra(DiskUsage.STATE_KEY));
        startActivityForResult(diskusage, DISKUSAGE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DISKUSAGE_REQUEST_CODE) {
            setResult(0, data);
            finish();
        }
    }

    /** True when the app can read the whole shared storage. */
    static boolean hasStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    static boolean hasUsageAccess(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOpsManager == null) return false;
            int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid, applicationInfo.packageName);
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasUsageAccess() {
        return hasUsageAccess(this);
    }
}
