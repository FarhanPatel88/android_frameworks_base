/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screenrecord;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import static com.android.systemui.statusbar.phone.StatusBar.SYSTEM_DIALOG_REASON_SCREENSHOT;
import static android.provider.Settings.System.SCREENRECORD_ENABLE_MIC;
import static android.provider.Settings.System.SCREENRECORD_SHOW_TAPS;
import static android.provider.Settings.System.SCREENRECORD_LOW_QUALITY;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Activity to select screen recording options
 */
public class ScreenRecordDialog extends Activity {
    private static final String TAG = "ScreenRecord";

    private static final int REQUEST_CODE_PERMISSIONS = 201;
    private static final int REQUEST_CODE_PERMISSIONS_AUDIO = 202;

    private static final int REQUEST_CODE_VIDEO = 301;
    private static final int REQUEST_CODE_VIDEO_TAPS = 302;
    private static final int REQUEST_CODE_VIDEO_LOW = 305;
    private static final int REQUEST_CODE_VIDEO_TAPS_LOW = 307;

    private static final int REQUEST_CODE_VIDEO_AUDIO = 401;
    private static final int REQUEST_CODE_VIDEO_AUDIO_TAPS = 402;
    private static final int REQUEST_CODE_VIDEO_AUDIO_LOW = 405;
    private static final int REQUEST_CODE_VIDEO_AUDIO_TAPS_LOW = 406;

    private boolean mUseAudio;
    private boolean mShowTaps;
    private boolean mLowQuality;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_record_dialog);

        Window window = getWindow();
        assert window != null;

        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);

        final Switch micSwitch = findViewById(R.id.switch_mic);
        final Switch tapsSwitch = findViewById(R.id.switch_taps);
        final Switch qualitySwitch = findViewById(R.id.switch_low_quality);

        initialCheckSwitch(micSwitch, SCREENRECORD_ENABLE_MIC);
        initialCheckSwitch(tapsSwitch, SCREENRECORD_SHOW_TAPS);
        initialCheckSwitch(qualitySwitch, SCREENRECORD_LOW_QUALITY);

        setSwitchListener(micSwitch, SCREENRECORD_ENABLE_MIC);
        setSwitchListener(tapsSwitch, SCREENRECORD_SHOW_TAPS);
        setSwitchListener(qualitySwitch, SCREENRECORD_LOW_QUALITY);

        final Button recordButton = findViewById(R.id.record_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUseAudio = micSwitch.isChecked();
                mShowTaps = tapsSwitch.isChecked();
                mLowQuality = qualitySwitch.isChecked();
                Log.d(TAG, "Record button clicked: audio " + mUseAudio + ", taps " + mShowTaps + ", quality " + mLowQuality);

                if (mUseAudio && ScreenRecordDialog.this.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Requesting permission for audio");
                    ScreenRecordDialog.this.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_CODE_PERMISSIONS_AUDIO);
                } else {
                    ScreenRecordDialog.this.requestScreenCapture();
                }
            }
        });
        final Button cancelButton = findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ScreenRecordDialog.this.finish();
            }
        });

        try {
            ActivityManagerWrapper.getInstance().closeSystemWindows(
                    SYSTEM_DIALOG_REASON_SCREENSHOT).get(
                    3000/*timeout*/, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {}
    }

    private void initialCheckSwitch(Switch sw, String setting) {
        sw.setChecked(
                Settings.System.getIntForUser(this.getContentResolver(),
                        setting, 0, UserHandle.USER_CURRENT) == 1);
    }

    private void setSwitchListener(Switch sw, String setting) {
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Settings.System.putIntForUser(this.getContentResolver(),
                    setting, isChecked ? 1 : 0, UserHandle.USER_CURRENT);
        });
    }

    private void requestScreenCapture() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        assert mediaProjectionManager != null;
        Intent permissionIntent = mediaProjectionManager.createScreenCaptureIntent();

        if (mLowQuality) {
            if (mUseAudio) {
                startActivityForResult(permissionIntent,
                        mShowTaps ? REQUEST_CODE_VIDEO_AUDIO_TAPS_LOW : REQUEST_CODE_VIDEO_AUDIO_LOW);
            } else {
                startActivityForResult(permissionIntent,
                        mShowTaps ? REQUEST_CODE_VIDEO_TAPS_LOW : REQUEST_CODE_VIDEO_LOW);
            }
        } else {
            if (mUseAudio) {
                startActivityForResult(permissionIntent,
                        mShowTaps ? REQUEST_CODE_VIDEO_AUDIO_TAPS : REQUEST_CODE_VIDEO_AUDIO);
            } else {
                startActivityForResult(permissionIntent,
                        mShowTaps ? REQUEST_CODE_VIDEO_TAPS : REQUEST_CODE_VIDEO);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mShowTaps = requestCode == REQUEST_CODE_VIDEO_TAPS
                || requestCode == REQUEST_CODE_VIDEO_AUDIO_TAPS
                || requestCode == REQUEST_CODE_VIDEO_TAPS_LOW
                || requestCode == REQUEST_CODE_VIDEO_AUDIO_TAPS_LOW;
        mLowQuality = ((requestCode > 304 && requestCode < 309)
                || (requestCode > 404 && requestCode < 409));
        switch (requestCode) {
            case REQUEST_CODE_PERMISSIONS:
                int permission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                            getResources().getString(R.string.screenrecord_permission_error),
                            Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    requestScreenCapture();
                }
                break;
            case REQUEST_CODE_PERMISSIONS_AUDIO:
                int videoPermission = checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
                int audioPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
                if (videoPermission != PackageManager.PERMISSION_GRANTED
                        || audioPermission != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                            getResources().getString(R.string.screenrecord_permission_error),
                            Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    requestScreenCapture();
                }
                break;
            default:
                if (resultCode == RESULT_OK) {
                    mUseAudio = requestCode > 400 && requestCode < 409;
                    startForegroundService(
                            RecordingService.getStartIntent(this, resultCode, data, mUseAudio,
                                    mShowTaps, mLowQuality));
                } else {
                    Toast.makeText(this,
                            getResources().getString(R.string.screenrecord_permission_error),
                            Toast.LENGTH_SHORT).show();
                }
                finish();
        }
    }
}
