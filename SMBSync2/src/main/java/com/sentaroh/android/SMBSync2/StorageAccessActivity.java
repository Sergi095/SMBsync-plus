package com.sentaroh.android.SMBSync2;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Requests broad file access before the legacy file database is opened. */
public final class StorageAccessActivity extends Activity {
    private boolean opened;
    private TextView description;
    static boolean hasAccess() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (hasAccess()) {
            openApp();
            return;
        }
        int unit = (int) getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(24 * unit, 24 * unit, 24 * unit, 24 * unit);
        content.setBackgroundColor(Color.rgb(8, 16, 31));
        TextView title = new TextView(this);
        title.setText(R.string.storage_access_title);
        title.setTextSize(25);
        title.setTextColor(Color.WHITE);
        content.addView(title);
        description = new TextView(this);
        description.setText(R.string.storage_access_explanation);
        description.setTextSize(16);
        description.setTextColor(Color.rgb(160, 178, 195));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = 12 * unit;
        content.addView(description, descriptionParams);
        Button settings = new Button(this);
        settings.setText(R.string.storage_access_open_settings);
        settings.setOnClickListener(view -> openSettings());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, 52 * unit);
        buttonParams.topMargin = 24 * unit;
        content.addView(settings, buttonParams);
        setContentView(content);
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasAccess()) openApp();
    }

    private void openSettings() {
        try {
            Intent settings = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            settings.setData(Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException ignored) {
                description.setText(R.string.storage_access_manual_settings);
            }
        }
    }

    private void openApp() {
        if (opened) return;
        opened = true;
        startActivity(new Intent(this, ActivityMain.class));
        finish();
    }
}
