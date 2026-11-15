package com.sentaroh.android.SMBSync2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sentaroh.android.Utilities.SystemInfo;
import com.sentaroh.android.Utilities.ThemeUtil;

import java.util.Locale;

/** A concise overview with the full diagnostics available only on request. */
public final class SystemInfoActivity extends AppCompatActivity {
    private int surface, panel, primary, secondary, accent;
    private LinearLayout content;
    private TextView rawDetails;
    private TextView detailsToggle;
    private LinearLayout detailsActions;

    @Override protected void onCreate(Bundle state) {
        GlobalParameters gp = GlobalWorkArea.getGlobalParameters(this);
        setTheme(gp.applicationTheme);
        super.onCreate(state);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        boolean light = ThemeUtil.isLightThemeUsed(this);
        surface = light ? Color.rgb(244, 247, 251) : Color.rgb(8, 16, 31);
        panel = light ? Color.WHITE : Color.rgb(27, 39, 59);
        primary = light ? Color.rgb(20, 34, 56) : Color.WHITE;
        secondary = light ? Color.rgb(86, 105, 125) : Color.rgb(160, 178, 195);
        accent = light ? Color.rgb(0, 105, 138) : Color.rgb(41, 197, 239);
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        if (light) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(surface);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(content);
        setContentView(scroll);

        TextView back = text("‹  More", 15, accent, true);
        back.setPadding(0, dp(4), 0, dp(16));
        back.setOnClickListener(view -> finish());
        content.addView(back);
        content.addView(text("Device & app", 26, primary, true));
        TextView subtitle = text("Quick details for troubleshooting", 14, secondary, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        content.addView(subtitle);

        addCard("App", getString(R.string.app_name) + "  ·  " + SystemInfo.getApplVersionName(this));
        addCard("Device", Build.MANUFACTURER + " " + Build.MODEL + "  ·  Android " + Build.VERSION.RELEASE);
        addCard("Network", networkSummary());
        StatFs storage = new StatFs(getFilesDir().getPath());
        addCard("Device storage", formatBytes(storage.getAvailableBytes()) + " available of "
                + formatBytes(storage.getTotalBytes()));

        detailsToggle = action("Show technical details", view -> toggleDetails());
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(48));
        toggleParams.topMargin = dp(8);
        content.addView(detailsToggle, toggleParams);
        rawDetails = text("", 12, secondary, false);
        rawDetails.setTypeface(Typeface.MONOSPACE);
        rawDetails.setTextIsSelectable(true);
        rawDetails.setPadding(dp(14), dp(14), dp(14), dp(14));
        rawDetails.setBackground(rounded(panel));
        rawDetails.setVisibility(View.GONE);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(10);
        content.addView(rawDetails, detailParams);
    }

    private void toggleDetails() {
        boolean showing = rawDetails.getVisibility() == View.VISIBLE;
        if (showing) {
            rawDetails.setVisibility(View.GONE);
            if (detailsActions != null) detailsActions.setVisibility(View.GONE);
            detailsToggle.setText("Show technical details");
        } else {
            GlobalParameters gp = GlobalWorkArea.getGlobalParameters(this);
            StringBuilder details = new StringBuilder();
            for (String line : CommonUtilities.listSystemInfo(this, gp)) details.append(line).append('\n');
            rawDetails.setText(details.toString());
            rawDetails.setVisibility(View.VISIBLE);
            detailsToggle.setText("Hide technical details");
            addDetailsActionsIfNeeded();
            detailsActions.setVisibility(View.VISIBLE);
        }
    }

    private boolean detailsActionsAdded;
    private void addDetailsActionsIfNeeded() {
        if (detailsActionsAdded) return;
        detailsActionsAdded = true;
        detailsActions = new LinearLayout(this);
        detailsActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, dp(48));
        row.topMargin = dp(10);
        content.addView(detailsActions, row);
        TextView copy = action("Copy details", view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("SMBsync+ device details", rawDetails.getText()));
            Toast.makeText(this, "Details copied", Toast.LENGTH_SHORT).show();
        });
        detailsActions.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        TextView share = action("Share details", view -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, rawDetails.getText().toString());
            startActivity(Intent.createChooser(send, "Share device details"));
        });
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, -1, 1);
        shareParams.leftMargin = dp(8);
        detailsActions.addView(share, shareParams);
    }

    private void addCard(String title, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(panel));
        TextView heading = text(title.toUpperCase(Locale.US), 12, accent, true);
        card.addView(heading);
        TextView detail = text(value, 16, primary, false);
        detail.setPadding(0, dp(5), 0, 0);
        card.addView(detail);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        content.addView(card, params);
    }

    private String networkSummary() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = manager == null ? null : manager.getActiveNetworkInfo();
        if (network == null || !network.isConnected()) return "No active connection";
        if (network.getType() == ConnectivityManager.TYPE_WIFI) return "Connected over Wi-Fi";
        if (network.getType() == ConnectivityManager.TYPE_MOBILE) return "Connected over mobile data";
        return "Connected over " + network.getTypeName();
    }

    private String formatBytes(long bytes) {
        double gib = bytes / 1073741824.0;
        return String.format(Locale.US, "%.1f GB", gib);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private TextView action(String value, View.OnClickListener listener) {
        TextView view = text(value, 15, accent, true);
        view.setGravity(android.view.Gravity.CENTER);
        view.setBackground(rounded(panel));
        view.setOnClickListener(listener);
        return view;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private int dp(int amount) {
        return (int) (amount * getResources().getDisplayMetrics().density + .5f);
    }
}
