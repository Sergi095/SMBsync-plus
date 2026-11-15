package com.sentaroh.android.SMBSync2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.sentaroh.android.Utilities.NotifyEvent;

import java.io.IOException;
import java.util.ArrayList;

/** Settings for explorer SMB servers; paths and shares are chosen in Files. */
public class SmbConnectionsActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(8, 16, 31);
    private static final int PANEL = Color.rgb(27, 39, 59);
    private static final int ACCENT = Color.rgb(41, 197, 239);
    private static final int SECONDARY = Color.rgb(160, 178, 195);
    private final ArrayList<SmbConnectionStore.Connection> connections = new ArrayList<>();
    private GlobalParameters gp;
    private LinearLayout page;
    private FrameLayout lockOverlay;
    private boolean locked;
    private boolean promptVisible;
    private boolean loadFailed;
    private long lastHandledBackgroundEpoch = SMBsyncPlusApplication.backgroundEpoch();

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(new GlobalParameters().setNewLocale(base, false));
    }

    @Override protected void onCreate(Bundle state) {
        gp = GlobalWorkArea.getGlobalParameters(this);
        setTheme(gp.applicationTheme == R.style.ModernDark ? R.style.ModernDark : gp.applicationTheme);
        super.onCreate(state);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        setContentView(page);
        installLockOverlay();
        if (protectedByPassword() && !gp.appPasswordAuthValidated) showLocked();
        try { connections.addAll(SmbConnectionStore.load(this)); }
        catch (IOException e) { loadFailed = true; showError(e.getMessage()); }
        render();
    }

    private boolean protectedByPassword() {
        return !gp.settingSecurityApplicationPasswordHashValue.isEmpty()
                && gp.settingSecurityApplicationPasswordUseAppStartup;
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable card() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL);
        background.setCornerRadius(dp(14));
        return background;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView action(String value, View.OnClickListener listener) {
        TextView view = text(value, 15, ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackground(card());
        view.setOnClickListener(listener);
        return view;
    }

    private void render() {
        page.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(content);
        TextView back = action("‹  Settings", view -> finish());
        content.addView(back, new LinearLayout.LayoutParams(dp(130), -2));
        TextView title = text("SMB connections", 28, Color.WHITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(22);
        content.addView(title, titleParams);
        TextView help = text("Save servers here, then open any share and folder from Files. Passwords are encrypted on this device.", 14, SECONDARY);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2);
        helpParams.topMargin = dp(10);
        content.addView(help, helpParams);
        TextView add = action("＋  Add SMB connection", view -> edit(null));
        add.setEnabled(!loadFailed);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, -2);
        addParams.topMargin = dp(22);
        content.addView(add, addParams);
        if (connections.isEmpty()) {
            TextView empty = text(loadFailed ? "Saved servers could not be unlocked." : "No saved servers yet.",
                    15, SECONDARY);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, -2);
            emptyParams.topMargin = dp(28);
            content.addView(empty, emptyParams);
        }
        for (SmbConnectionStore.Connection connection : connections) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackground(card());
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = dp(12);
            content.addView(row, rowParams);
            row.addView(text(connection.name.isEmpty() ? connection.host : connection.name, 18, Color.WHITE));
            row.addView(text(connection.host + (connection.port.isEmpty() ? "" : ":" + connection.port)
                    + "  ·  " + (connection.user.isEmpty() ? "Guest" : connection.user)
                    + (connection.password.isEmpty() ? "" : "  ·  Password saved"), 13, SECONDARY));
            LinearLayout buttons = new LinearLayout(this);
            LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
            buttonsParams.topMargin = dp(12);
            row.addView(buttons, buttonsParams);
            buttons.addView(action("Edit", view -> edit(connection)));
            TextView remove = action("Remove", view -> remove(connection));
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-2, -2);
            removeParams.leftMargin = dp(10);
            buttons.addView(remove, removeParams);
        }
    }

    private EditText field(LinearLayout form, String hint, int inputType, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(SECONDARY);
        input.setTextColor(Color.WHITE);
        input.setInputType(inputType);
        input.setText(value);
        form.addView(input);
        return input;
    }

    private void edit(SmbConnectionStore.Connection current) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        scroll.addView(form);
        EditText name = field(form, "Display name (optional)", InputType.TYPE_CLASS_TEXT,
                current == null ? "" : current.name);
        EditText host = field(form, "Server address or name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                current == null ? "" : current.host);
        EditText port = field(form, "Port (default 445)", InputType.TYPE_CLASS_NUMBER,
                current == null ? "" : current.port);
        EditText user = field(form, "Username (optional)", InputType.TYPE_CLASS_TEXT,
                current == null ? "" : current.user);
        EditText password = field(form, current == null ? "Password (optional)" : "Password (leave blank to keep current)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, "");
        EditText domain = field(form, "Domain (optional)", InputType.TYPE_CLASS_TEXT,
                current == null ? "" : current.domain);
        Spinner protocol = new Spinner(this);
        ArrayAdapter<String> protocols = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"SMB2/3 (recommended)", "SMB1 (legacy)"});
        protocols.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocol.setAdapter(protocols);
        protocol.setSelection(current != null && SyncTaskItem.SYNC_FOLDER_SMB_PROTOCOL_SMB1.equals(current.protocol) ? 1 : 0);
        form.addView(protocol);
        CheckBox signing = new CheckBox(this);
        signing.setText("Require SMB signing");
        signing.setChecked(current != null && current.signing);
        form.addView(signing);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(current == null ? "Add SMB connection" : "Edit SMB connection")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String address = host.getText().toString().trim();
            String portValue = port.getText().toString().trim();
            if (address.isEmpty() || address.contains("/") || address.contains("\\") || address.contains(" ")) {
                host.setError("Enter a server address without a path");
                host.requestFocus();
                return;
            }
            if (!portValue.isEmpty()) {
                try {
                    int parsed = Integer.parseInt(portValue);
                    if (parsed < 1 || parsed > 65535) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    port.setError("Use a port from 1 to 65535");
                    port.requestFocus();
                    return;
                }
            }
            SmbConnectionStore.Connection value = new SmbConnectionStore.Connection();
            if (current != null) value.id = current.id;
            value.name = name.getText().toString().trim();
            value.host = address;
            value.port = portValue;
            value.user = user.getText().toString().trim();
            value.password = password.length() > 0 ? password.getText().toString()
                    : current == null ? "" : current.password;
            value.domain = domain.getText().toString().trim();
            value.protocol = protocol.getSelectedItemPosition() == 1
                    ? SyncTaskItem.SYNC_FOLDER_SMB_PROTOCOL_SMB1 : SyncTaskItem.SYNC_FOLDER_SMB_PROTOCOL_DEFAULT;
            value.signing = signing.isChecked();
            ArrayList<SmbConnectionStore.Connection> updated = new ArrayList<>(connections);
            if (current == null) updated.add(value);
            else updated.set(updated.indexOf(current), value);
            if (persist(updated)) {
                dialog.dismiss();
                connections.clear();
                connections.addAll(updated);
                render();
            }
        }));
        dialog.show();
    }

    private void remove(SmbConnectionStore.Connection connection) {
        new AlertDialog.Builder(this).setTitle("Remove SMB connection?")
                .setMessage(connection.name.isEmpty() ? connection.host : connection.name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    ArrayList<SmbConnectionStore.Connection> updated = new ArrayList<>(connections);
                    updated.remove(connection);
                    if (persist(updated)) {
                        connections.clear();
                        connections.addAll(updated);
                        render();
                    }
                }).show();
    }

    private boolean persist(ArrayList<SmbConnectionStore.Connection> updated) {
        try { SmbConnectionStore.save(this, updated); return true; }
        catch (IOException e) { showError(e.getMessage()); return false; }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("SMB connections").setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private void installLockOverlay() {
        lockOverlay = new FrameLayout(this);
        lockOverlay.setBackgroundColor(BG);
        lockOverlay.setClickable(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.addView(text("Unlock SMBsync+", 26, Color.WHITE));
        panel.addView(text("Your saved SMB servers are locked.", 15, SECONDARY));
        TextView button = action("Unlock", view -> requestUnlock());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(20);
        panel.addView(button, buttonParams);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        position.leftMargin = dp(20);
        position.rightMargin = dp(20);
        lockOverlay.addView(panel, position);
        ((FrameLayout) findViewById(android.R.id.content)).addView(lockOverlay,
                new FrameLayout.LayoutParams(-1, -1));
        lockOverlay.setVisibility(View.GONE);
    }

    private void showLocked() {
        locked = true;
        page.setVisibility(View.GONE);
        lockOverlay.setVisibility(View.VISIBLE);
    }

    private void requestUnlock() {
        if (!locked || promptVisible) return;
        promptVisible = true;
        gp.appPasswordAuthValidated = false;
        NotifyEvent result = new NotifyEvent(this);
        result.setListener(new NotifyEvent.NotifyEventListener() {
            @Override public void positiveResponse(Context context, Object[] objects) {
                promptVisible = false;
                locked = false;
                page.setVisibility(View.VISIBLE);
                lockOverlay.setVisibility(View.GONE);
            }
            @Override public void negativeResponse(Context context, Object[] objects) {
                promptVisible = false;
                finish();
            }
        });
        ApplicationPasswordUtil.applicationPasswordAuthentication(gp, this,
                getSupportFragmentManager(), new CommonUtilities(this, "SMB connections", gp, getSupportFragmentManager()),
                true, result, ApplicationPasswordUtil.APPLICATION_PASSWORD_RESOURCE_START_APPLICATION);
    }

    @Override protected void onResume() {
        super.onResume();
        gp.loadSettingsParms(this);
        long backgroundEpoch = SMBsyncPlusApplication.backgroundEpoch();
        boolean returnedFromBackground = backgroundEpoch != lastHandledBackgroundEpoch;
        lastHandledBackgroundEpoch = backgroundEpoch;
        if (protectedByPassword()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            if (returnedFromBackground && !gp.appPasswordAuthValidated) showLocked();
            if (locked) requestUnlock();
        } else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override protected void onPause() {
        super.onPause();
    }
}
