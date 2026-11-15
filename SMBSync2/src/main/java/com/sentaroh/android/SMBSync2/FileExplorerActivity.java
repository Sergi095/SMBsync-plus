package com.sentaroh.android.SMBSync2;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.MimeTypeMap;

import com.sentaroh.jcifs.JcifsAuth;
import com.sentaroh.jcifs.JcifsFile;
import com.sentaroh.android.Utilities.NotifyEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A location-first browser for phone storage and configured SMB servers. */
public class FileExplorerActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(8, 16, 31);
    private static final int PANEL = Color.rgb(27, 39, 59);
    private static final int ACCENT = Color.rgb(41, 197, 239);
    private static final int SECONDARY = Color.rgb(160, 178, 195);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Entry> allEntries = new ArrayList<>();
    private final ArrayList<Entry> visibleEntries = new ArrayList<>();
    private GlobalParameters gp;
    private LinearLayout rootView;
    private long lastHandledBackgroundEpoch = SMBsyncPlusApplication.backgroundEpoch();
    private boolean explorerLocked;
    private boolean unlockPromptVisible;
    private FrameLayout lockOverlay;
    private TextView heading, subtitle, pathLabel, status, storageText, pasteLabel;
    private EditText search;
    private LinearLayout storageCard, clipboardBar;
    private ProgressBar storageBar;
    private ListView list;
    private BaseAdapter adapter;
    private String localPath;
    private String smbPath;
    private SmbLocation smbLocation;
    private Entry clipboard;
    private int loadGeneration = 0;
    private int sortMode = 0;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(new GlobalParameters().setNewLocale(base, false));
    }

    @Override
    protected void onCreate(Bundle state) {
        gp = GlobalWorkArea.getGlobalParameters(this);
        setTheme(gp.applicationTheme == R.style.ModernDark ? R.style.ModernDark : gp.applicationTheme);
        super.onCreate(state);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (!gp.settingSecurityApplicationPasswordHashValue.equals(""))
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        buildScreen();
        installLockOverlay();
        showHome();
        if (isProtected() && !gp.appPasswordAuthValidated) showExplorerLocked();
    }

    private boolean isProtected() {
        return !gp.settingSecurityApplicationPasswordHashValue.equals("")
                && gp.settingSecurityApplicationPasswordUseAppStartup;
    }

    private void installLockOverlay() {
        FrameLayout content = findViewById(android.R.id.content);
        lockOverlay = new FrameLayout(this);
        lockOverlay.setBackgroundColor(BG);
        lockOverlay.setClickable(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(26), dp(24), dp(26), dp(24));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.smbsync_plus_icon);
        panel.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = text("Unlock SMBsync+", 26, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(16);
        panel.addView(title, titleParams);
        panel.addView(text("Your phone files and network locations are locked.", 15, SECONDARY, false));
        TextView unlock = action("Unlock", view -> requestExplorerUnlock());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(22);
        panel.addView(unlock, buttonParams);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        panelParams.leftMargin = dp(20);
        panelParams.rightMargin = dp(20);
        lockOverlay.addView(panel, panelParams);
        content.addView(lockOverlay, new FrameLayout.LayoutParams(-1, -1));
        lockOverlay.setVisibility(View.GONE);
    }

    private void showExplorerLocked() {
        explorerLocked = true;
        rootView.setVisibility(View.GONE);
        lockOverlay.setVisibility(View.VISIBLE);
    }

    private void showExplorerUnlocked() {
        explorerLocked = false;
        rootView.setVisibility(View.VISIBLE);
        lockOverlay.setVisibility(View.GONE);
    }

    private void requestExplorerUnlock() {
        if (!explorerLocked || unlockPromptVisible) return;
        unlockPromptVisible = true;
        gp.appPasswordAuthValidated = false;
        NotifyEvent unlock = new NotifyEvent(this);
        unlock.setListener(new NotifyEvent.NotifyEventListener() {
            @Override public void positiveResponse(Context context, Object[] objects) {
                unlockPromptVisible = false;
                showExplorerUnlocked();
                if (localPath == null && smbLocation == null) showHome();
            }
            @Override public void negativeResponse(Context context, Object[] objects) {
                unlockPromptVisible = false;
                finish();
            }
        });
        ApplicationPasswordUtil.applicationPasswordAuthentication(gp, this,
                getSupportFragmentManager(),
                new CommonUtilities(this, "Explorer", gp, getSupportFragmentManager()),
                true, unlock, ApplicationPasswordUtil.APPLICATION_PASSWORD_RESOURCE_START_APPLICATION);
    }

    @Override protected void onResume() {
        super.onResume();
        gp.loadSettingsParms(this);
        if (!gp.settingSecurityApplicationPasswordHashValue.equals(""))
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        long backgroundEpoch = SMBsyncPlusApplication.backgroundEpoch();
        boolean returnedFromBackground = backgroundEpoch != lastHandledBackgroundEpoch;
        lastHandledBackgroundEpoch = backgroundEpoch;
        if ((returnedFromBackground && !gp.appPasswordAuthValidated) || explorerLocked) {
            if (isProtected()) {
                showExplorerLocked();
                requestExplorerUnlock();
            } else showExplorerUnlocked();
        }
        if (!explorerLocked && localPath == null && smbLocation == null) showHome();
    }

    @Override protected void onPause() {
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable card(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private TextView action(String label, View.OnClickListener listener) {
        TextView v = text(label, 14, ACCENT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(13), dp(10), dp(13), dp(10));
        v.setBackground(card(PANEL, 10));
        v.setOnClickListener(listener);
        return v;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(17), dp(20), dp(12));
        root.addView(top);
        TextView brand = text("SMBsync+  /  FILES", 12, ACCENT, true);
        top.addView(brand);
        heading = text("Files", 29, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        top.addView(heading, titleParams);
        subtitle = text("Browse your phone and connected NAS", 14, SECONDARY, false);
        top.addView(subtitle);

        storageCard = new LinearLayout(this);
        storageCard.setOrientation(LinearLayout.VERTICAL);
        storageCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        storageCard.setBackground(card(PANEL, 16));
        LinearLayout.LayoutParams storageParams = new LinearLayout.LayoutParams(-1, -2);
        storageParams.topMargin = dp(17);
        top.addView(storageCard, storageParams);
        storageText = text("Phone storage", 15, Color.WHITE, true);
        storageCard.addView(storageText);
        storageBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        storageBar.setMax(100);
        storageBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(5));
        barParams.topMargin = dp(10);
        storageCard.addView(storageBar, barParams);
        storageCard.setOnClickListener(v -> showLocal(Environment.getExternalStorageDirectory().getPath()));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search this location");
        search.setHintTextColor(SECONDARY);
        search.setTextColor(Color.WHITE);
        search.setPadding(dp(16), dp(10), dp(16), dp(10));
        search.setBackground(card(PANEL, 12));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.topMargin = dp(14);
        top.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            public void afterTextChanged(Editable e) { }
        });

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, -2);
        toolsParams.topMargin = dp(14);
        top.addView(tools, toolsParams);
        TextView up = action("‹  Back", v -> goUp());
        tools.addView(up);
        pathLabel = text("Locations", 14, SECONDARY, false);
        pathLabel.setSingleLine(true);
        pathLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        pathLabel.setGravity(Gravity.CENTER_VERTICAL);
        pathLabel.setPadding(dp(10), 0, dp(8), 0);
        tools.addView(pathLabel, new LinearLayout.LayoutParams(0, dp(44), 1));
        tools.addView(action("Sort", v -> chooseSort()));
        tools.addView(action("Connections ⚙", v -> startActivity(
                new Intent(this, SmbConnectionsActivity.class))));
        TextView addFolder = action("+", v -> createFolder());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(43), dp(43));
        addParams.leftMargin = dp(8);
        tools.addView(addFolder, addParams);
        addFolder.setTag("new-folder");

        status = text("", 13, SECONDARY, false);
        status.setPadding(dp(21), dp(3), dp(20), dp(7));
        root.addView(status);
        list = new ListView(this);
        list.setDivider(null);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setBackgroundColor(BG);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(visibleEntries.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            options(visibleEntries.get(position));
            return true;
        });

        clipboardBar = new LinearLayout(this);
        clipboardBar.setGravity(Gravity.CENTER_VERTICAL);
        clipboardBar.setPadding(dp(14), dp(9), dp(14), dp(9));
        clipboardBar.setBackgroundColor(PANEL);
        pasteLabel = text("", 14, Color.WHITE, false);
        pasteLabel.setSingleLine(true);
        pasteLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        clipboardBar.addView(pasteLabel, new LinearLayout.LayoutParams(0, -2, 1));
        clipboardBar.addView(action("Paste here", v -> paste()));
        clipboardBar.setVisibility(View.GONE);
        root.addView(clipboardBar);
    }

    private void showHome() {
        loadGeneration++;
        localPath = null;
        smbPath = null;
        smbLocation = null;
        heading.setText("Files");
        subtitle.setText("Local storage and SMB servers");
        pathLabel.setText("Locations");
        storageCard.setVisibility(View.VISIBLE);
        File root = Environment.getExternalStorageDirectory();
        try {
            StatFs fs = new StatFs(root.getPath());
            long total = fs.getTotalBytes(), used = total - fs.getAvailableBytes();
            storageText.setText("Phone storage  ·  " + formatSize(used) + " of " + formatSize(total) + " used");
            storageBar.setProgress(total > 0 ? (int) (used * 100 / total) : 0);
        } catch (Exception ignored) { storageText.setText("Phone storage"); }
        allEntries.clear();
        allEntries.add(Entry.localLocation("Phone storage", root.getPath(), "Browse all local folders"));
        addShortcut("Downloads", Environment.DIRECTORY_DOWNLOADS);
        addShortcut("Pictures", Environment.DIRECTORY_PICTURES);
        addShortcut("Documents", Environment.DIRECTORY_DOCUMENTS);
        Set<String> seen = new HashSet<>();
        try {
            for (SmbConnectionStore.Connection saved : SmbConnectionStore.load(this)) {
                SmbLocation location = new SmbLocation(saved);
                if (seen.add(location.key())) allEntries.add(Entry.smbLocation(location));
            }
        } catch (IOException e) {
            error("SMB connections", e.getMessage());
        }
        if (gp.syncTaskList != null) for (SyncTaskItem task : gp.syncTaskList) {
            if (SyncTaskItem.SYNC_FOLDER_TYPE_SMB.equals(task.getMasterFolderType())) addSmbLocation(task, true, seen);
            if (SyncTaskItem.SYNC_FOLDER_TYPE_SMB.equals(task.getTargetFolderType())) addSmbLocation(task, false, seen);
        }
        status.setText(allEntries.size() + " locations  ·  Long press files for actions");
        filter();
        updateClipboard();
    }

    private void addShortcut(String label, String directory) {
        File f = Environment.getExternalStoragePublicDirectory(directory);
        if (f.isDirectory()) allEntries.add(Entry.localLocation(label, f.getPath(), "Local folder"));
    }

    private void addSmbLocation(SyncTaskItem task, boolean source, Set<String> seen) {
        SmbLocation location = new SmbLocation(task, source);
        if (location.host.isEmpty() || !seen.add(location.key())) return;
        allEntries.add(Entry.smbLocation(location));
    }

    private void showLocal(String path) {
        localPath = path;
        smbPath = null;
        smbLocation = null;
        heading.setText(path.equals(Environment.getExternalStorageDirectory().getPath())
                ? "Phone storage" : new File(path).getName());
        subtitle.setText("Phone storage");
        pathLabel.setText(path);
        storageCard.setVisibility(View.GONE);
        loadLocal(path);
        updateClipboard();
    }

    private void loadLocal(String path) {
        int generation = ++loadGeneration;
        status.setText("Checking folder…");
        io.execute(() -> {
            File[] files = new File(path).listFiles();
            ArrayList<Entry> result = new ArrayList<>();
            if (files != null) for (File f : files) {
                result.add(Entry.file(f.getName(), f.getPath(), f.isDirectory(), f.length(), f.lastModified(), null));
            }
            runOnUiThread(() -> {
                if (generation != loadGeneration || isFinishing()) return;
                if (files == null) { status.setText("This folder cannot be read. Check storage access."); return; }
                setEntries(result);
            });
        });
    }

    private void showSmb(SmbLocation location, String relativePath) {
        localPath = null;
        smbLocation = location;
        smbPath = relativePath;
        heading.setText(relativePath.equals("/") ? location.host : lastSegment(relativePath));
        subtitle.setText(relativePath.equals("/") ? "Network  ·  Browse shares" : "Network  ·  " + location.host);
        pathLabel.setText("NAS  ›  " + location.host + relativePath);
        storageCard.setVisibility(View.GONE);
        search.setText("");
        allEntries.clear();
        filter();
        updateClipboard();
        int generation = ++loadGeneration;
        status.setText("Connecting to NAS…");
        io.execute(() -> {
            ArrayList<Entry> result = new ArrayList<>();
            String error = null;
            try {
                JcifsFile[] files = new JcifsFile(location.url(relativePath), location.auth(gp)).listFiles();
                for (JcifsFile f : files) {
                    boolean directory = f.isDirectory();
                    String name = stripSlash(f.getName());
                    result.add(Entry.file(name, f.getPath(), directory,
                            directory ? 0 : f.length(), f.getLastModified(), location));
                }
            } catch (Exception e) {
                Log.e("SMBsync+ Explorer", "Could not browse SMB location", e);
                error = describeFailure(e);
            }
            String finalError = error;
            runOnUiThread(() -> {
                if (generation != loadGeneration || isFinishing()) return;
                if (finalError != null) {
                    status.setText("Could not open NAS. Check its address, VPN, and credentials.");
                    error("Network location", finalError);
                } else setEntries(result);
            });
        });
    }

    private void setEntries(ArrayList<Entry> result) {
        allEntries.clear();
        allEntries.addAll(result);
        sortEntries();
        status.setText(allEntries.size() + (allEntries.size() == 1 ? " item" : " items"));
        filter();
    }

    private void filter() {
        if (adapter == null) return;
        String term = search.getText().toString().trim().toLowerCase(Locale.ENGLISH);
        visibleEntries.clear();
        for (Entry e : allEntries) if (e.name.toLowerCase(Locale.ENGLISH).contains(term)) visibleEntries.add(e);
        adapter.notifyDataSetChanged();
    }

    private void sortEntries() {
        Comparator<Entry> order = (a, b) -> {
            if (a.dir != b.dir) return a.dir ? -1 : 1;
            if (sortMode == 1 && a.modified != b.modified) return Long.compare(b.modified, a.modified);
            if (sortMode == 2 && a.size != b.size) return Long.compare(b.size, a.size);
            return a.name.compareToIgnoreCase(b.name);
        };
        allEntries.sort(order);
    }

    private void chooseSort() {
        new AlertDialog.Builder(this).setTitle("Sort files")
                .setSingleChoiceItems(new String[]{"Name", "Newest first", "Largest first"}, sortMode,
                        (dialog, which) -> { sortMode = which; sortEntries(); filter(); dialog.dismiss(); })
                .show();
    }

    private void open(Entry e) {
        if (e.home) {
            if (e.location == null) showLocal(e.path);
            else showSmb(e.location, "/");
        } else if (e.dir) {
            if (e.location == null) showLocal(e.path);
            else showSmb(e.location, smbPath + e.name + "/");
        } else if (e.location == null) openFile(new File(e.path));
        else previewRemote(e);
    }

    private void goUp() {
        if (localPath != null) {
            if (localPath.equals(Environment.getExternalStorageDirectory().getPath())) showHome();
            else {
                File parent = new File(localPath).getParentFile();
                if (parent == null || !parent.getPath().startsWith("/storage/")) showHome();
                else showLocal(parent.getPath());
            }
        } else if (smbLocation != null) {
            if (smbPath.equals("/")) showHome();
            else {
                String trimmed = smbPath.substring(0, smbPath.length() - 1);
                int slash = trimmed.lastIndexOf('/');
                showSmb(smbLocation, trimmed.substring(0, slash + 1));
            }
        } else finish();
    }

    @Override public void onBackPressed() { goUp(); }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
            String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ENGLISH));
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, mime == null ? "application/octet-stream" : mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, "Open file"));
        } catch (Exception e) { error("Open file", e.getMessage()); }
    }

    private void previewRemote(Entry e) {
        if (e.size > 150L * 1024 * 1024) { error("Open file", "This file is too large to preview. Copy it to phone storage first."); return; }
        status.setText("Downloading preview…");
        io.execute(() -> {
            try {
                File dir = new File(getCacheDir(), "explorer_preview");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create preview cache");
                File target = new File(dir, UUID.randomUUID() + "-" + e.name);
                try (InputStream in = new JcifsFile(e.path, e.location.auth(gp)).getInputStream();
                     OutputStream out = new FileOutputStream(target)) { transfer(in, out); }
                runOnUiThread(() -> { status.setText("Preview ready"); openFile(target); });
            } catch (Exception failure) { runOnUiThread(() -> error("Open network file", failure.getMessage())); }
        });
    }

    private void options(Entry e) {
        if (e.home) { open(e); return; }
        if (e.location != null && "/".equals(smbPath)) {
            new AlertDialog.Builder(this).setTitle(e.name)
                    .setItems(new String[]{"Open share", "Copy share", "Details"}, (dialog, which) -> {
                        if (which == 0) open(e);
                        else if (which == 1) { clipboard = e; updateClipboard(); }
                        else details(e);
                    }).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(e.name)
                .setItems(new String[]{"Open", "Copy", "Rename", "Delete", "Details"}, (dialog, which) -> {
                    if (which == 0) open(e);
                    else if (which == 1) { clipboard = e; updateClipboard(); Toast.makeText(this, "Choose a destination folder", Toast.LENGTH_SHORT).show(); }
                    else if (which == 2) rename(e);
                    else if (which == 3) confirmDelete(e);
                    else details(e);
                }).show();
    }

    private void details(Entry e) {
        String text = (e.dir ? "Folder" : "File · " + formatSize(e.size)) + "\n"
                + "Modified: " + DateFormat.getDateTimeInstance().format(new Date(e.modified)) + "\n"
                + e.path;
        new AlertDialog.Builder(this).setTitle(e.name).setMessage(text).setPositiveButton("OK", null).show();
    }

    private void rename(Entry e) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(e.name);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Rename").setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!validName(name)) { error("Rename", "Enter a valid file name."); return; }
                    runOperation("Renaming…", () -> {
                        String parent = parentPath(e.path, e.location != null);
                        String target = childPath(parent, name, e.dir, e.location != null);
                        if (exists(target, e.location)) throw new IOException("A file with that name already exists.");
                        renamePath(e.path, target, e.location);
                    });
                }).show();
    }

    private void confirmDelete(Entry e) {
        new AlertDialog.Builder(this).setTitle("Delete " + e.name + "?")
                .setMessage("This action cannot be undone. Only empty folders can be deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> runOperation("Deleting…", () -> {
                    if (e.location == null) {
                        if (!new File(e.path).delete()) throw new IOException("Could not delete. The folder may contain files.");
                    } else new JcifsFile(e.path, e.location.auth(gp)).delete();
                })).show();
    }

    private void createFolder() {
        if (localPath == null && smbLocation == null) { showLocal(Environment.getExternalStorageDirectory().getPath()); return; }
        if (smbLocation != null && "/".equals(smbPath)) {
            error("New folder", "Open a share before creating a folder."); return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Folder name");
        new AlertDialog.Builder(this).setTitle("New folder").setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!validName(name)) { error("New folder", "Enter a valid folder name."); return; }
                    String parent = currentDirectory();
                    SmbLocation location = smbLocation;
                    runOperation("Creating folder…", () -> {
                        String path = childPath(parent, name, true, location != null);
                        if (exists(path, location)) throw new IOException("A folder with that name already exists.");
                        mkdir(path, location);
                    });
                }).show();
    }

    private void updateClipboard() {
        clipboardBar.setVisibility(clipboard == null ? View.GONE : View.VISIBLE);
        if (clipboard != null) pasteLabel.setText("Copy: " + clipboard.name);
    }

    private void paste() {
        if (clipboard == null || (localPath == null && smbLocation == null)) {
            error("Paste", "Open a destination folder first."); return;
        }
        if (smbLocation != null && "/".equals(smbPath)) {
            error("Paste", "Open a share before pasting files."); return;
        }
        Entry source = clipboard;
        String destination = currentDirectory();
        SmbLocation location = smbLocation;
        if (source.dir && destination.startsWith(source.path)) {
            error("Paste", "A folder cannot be copied inside itself."); return;
        }
        runOperation("Copying " + source.name + "…", () -> {
            CopyCount count = new CopyCount();
            copyEntry(source, destination, location, count, 0);
            runOnUiThread(() -> Toast.makeText(this,
                    "Copied " + count.copied + " · Skipped existing " + count.skipped, Toast.LENGTH_LONG).show());
        });
    }

    private void copyEntry(Entry source, String destinationDir, SmbLocation destinationLocation, CopyCount count, int depth) throws Exception {
        if (depth > 64) throw new IOException("Folder nesting is too deep.");
        if (!validName(source.name)) throw new IOException("Invalid file name: " + source.name);
        String destination = childPath(destinationDir, source.name, source.dir, destinationLocation != null);
        if (source.dir) {
            if (!exists(destination, destinationLocation)) mkdir(destination, destinationLocation);
            else if (!isDirectory(destination, destinationLocation)) throw new IOException("A file blocks folder " + source.name);
            if (source.location == null) {
                File[] children = new File(source.path).listFiles();
                if (children == null) throw new IOException("Cannot read " + source.name);
                for (File child : children) copyEntry(Entry.file(child.getName(), child.getPath(), child.isDirectory(),
                        child.length(), child.lastModified(), null), destination, destinationLocation, count, depth + 1);
            } else {
                JcifsFile[] children = new JcifsFile(source.path, source.location.auth(gp)).listFiles();
                for (JcifsFile child : children) {
                    boolean folder = child.isDirectory();
                    copyEntry(Entry.file(stripSlash(child.getName()), child.getPath(), folder,
                            folder ? 0 : child.length(), child.getLastModified(), source.location),
                            destination, destinationLocation, count, depth + 1);
                }
            }
        } else {
            if (exists(destination, destinationLocation)) { count.skipped++; return; }
            String temporary = childPath(destinationDir, ".smbsyncplus-" + UUID.randomUUID() + ".partial", false,
                    destinationLocation != null);
            try {
                try (InputStream input = source.location == null ? new FileInputStream(source.path)
                        : new JcifsFile(source.path, source.location.auth(gp)).getInputStream();
                     OutputStream output = destinationLocation == null ? new FileOutputStream(temporary)
                             : new JcifsFile(temporary, destinationLocation.auth(gp)).getOutputStream()) {
                    transfer(input, output);
                }
                if (exists(destination, destinationLocation)) { count.skipped++; return; }
                renamePath(temporary, destination, destinationLocation);
                count.copied++;
            } finally { if (exists(temporary, destinationLocation)) deletePath(temporary, destinationLocation); }
        }
    }

    private void runOperation(String message, Operation operation) {
        status.setText(message);
        io.execute(() -> {
            try {
                operation.run();
                runOnUiThread(() -> { if (!isFinishing()) reload(); });
            } catch (Exception failure) {
                runOnUiThread(() -> { if (!isFinishing()) error("File operation", failure.getMessage()); });
            }
        });
    }

    private void reload() {
        if (localPath != null) loadLocal(localPath);
        else if (smbLocation != null) showSmb(smbLocation, smbPath);
        else showHome();
    }

    private String currentDirectory() { return smbLocation == null ? localPath : smbLocation.url(smbPath); }
    private boolean exists(String path, SmbLocation location) throws Exception {
        return location == null ? new File(path).exists() : new JcifsFile(path, location.auth(gp)).exists();
    }
    private boolean isDirectory(String path, SmbLocation location) throws Exception {
        return location == null ? new File(path).isDirectory() : new JcifsFile(path, location.auth(gp)).isDirectory();
    }
    private void mkdir(String path, SmbLocation location) throws Exception {
        if (location == null) { if (!new File(path).mkdirs()) throw new IOException("Could not create folder"); }
        else new JcifsFile(path, location.auth(gp)).mkdirs();
    }
    private void renamePath(String from, String to, SmbLocation location) throws Exception {
        if (location == null) {
            if (!new File(from).renameTo(new File(to))) throw new IOException("Could not rename file");
        } else {
            JcifsAuth auth = location.auth(gp);
            new JcifsFile(from, auth).renameTo(new JcifsFile(to, auth));
        }
    }
    private void deletePath(String path, SmbLocation location) throws Exception {
        if (location == null) { if (!new File(path).delete()) throw new IOException("Could not remove temporary file"); }
        else new JcifsFile(path, location.auth(gp)).delete();
    }
    private static void transfer(InputStream input, OutputStream output) throws IOException {
        byte[] bytes = new byte[64 * 1024];
        int n;
        while ((n = input.read(bytes)) != -1) output.write(bytes, 0, n);
    }
    private static String stripSlash(String name) { return name.endsWith("/") ? name.substring(0, name.length() - 1) : name; }
    private static String lastSegment(String path) {
        String p = stripSlash(path);
        return p.substring(p.lastIndexOf('/') + 1);
    }
    private static String parentPath(String path, boolean remote) {
        String p = stripSlash(path);
        int slash = p.lastIndexOf('/');
        return remote ? p.substring(0, slash + 1) : p.substring(0, slash);
    }
    private static String childPath(String parent, String name, boolean directory, boolean remote) {
        if (remote) return parent + (parent.endsWith("/") ? "" : "/") + name + (directory ? "/" : "");
        return new File(parent, name).getPath();
    }
    private static boolean validName(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return false;
        String invalid = "/\\:*?\"<>|";
        for (int i = 0; i < name.length(); i++) if (invalid.indexOf(name.charAt(i)) >= 0) return false;
        return true;
    }
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.ENGLISH, "%.1f %s", value, units[unit]);
    }
    private static String describeFailure(Throwable failure) {
        StringBuilder message = new StringBuilder();
        for (Throwable cause = failure; cause != null && message.length() < 500; cause = cause.getCause()) {
            String detail = cause.getMessage();
            if (detail == null || detail.isEmpty()) continue;
            if (message.length() > 0) message.append("\nCaused by: ");
            message.append(detail);
        }
        return message.length() == 0 ? failure.getClass().getSimpleName() : message.toString();
    }
    private void error(String title, String detail) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(detail == null ? "Please try again." : detail)
                .setPositiveButton("OK", null).show();
    }

    @Override protected void onDestroy() { loadGeneration++; io.shutdownNow(); super.onDestroy(); }

    private interface Operation { void run() throws Exception; }
    private static class CopyCount { int copied, skipped; }

    private static class Entry {
        final String name, path, note;
        final boolean dir, home;
        final long size, modified;
        final SmbLocation location;
        Entry(String name, String path, String note, boolean dir, boolean home, long size, long modified, SmbLocation location) {
            this.name=name; this.path=path; this.note=note; this.dir=dir; this.home=home;
            this.size=size; this.modified=modified; this.location=location;
        }
        static Entry localLocation(String name, String path, String note) {
            return new Entry(name, path, note, true, true, 0, 0, null);
        }
        static Entry smbLocation(SmbLocation location) {
            return new Entry(location.displayName, location.url("/"),
                    "Browse all shares · " + location.taskName, true, true, 0, 0, location);
        }
        static Entry file(String name, String path, boolean dir, long size, long modified, SmbLocation location) {
            return new Entry(name, path, "", dir, false, size, modified, location);
        }
    }

    private static class SmbLocation {
        final String host, port, user, password, domain, protocol, taskName, displayName;
        final boolean signing, negotiation;
        SmbLocation(SyncTaskItem task, boolean source) {
            String name = source ? task.getMasterSmbHostName() : task.getTargetSmbHostName();
            String addr = source ? task.getMasterSmbAddr() : task.getTargetSmbAddr();
            host = name.isEmpty() ? addr : name;
            port = source ? task.getMasterSmbPort() : task.getTargetSmbPort();
            user = source ? task.getMasterSmbUserName() : task.getTargetSmbUserName();
            password = source ? task.getMasterSmbPassword() : task.getTargetSmbPassword();
            domain = source ? task.getMasterSmbDomain() : task.getTargetSmbDomain();
            protocol = source ? task.getMasterSmbProtocol() : task.getTargetSmbProtocol();
            signing = source ? task.isMasterSmbIpcSigningEnforced() : task.isTargetSmbIpcSigningEnforced();
            negotiation = source ? task.isMasterSmbUseSmb2Negotiation() : task.isTargetSmbUseSmb2Negotiation();
            taskName = task.getSyncTaskName();
            displayName = host;
        }
        SmbLocation(SmbConnectionStore.Connection saved) {
            host = saved.host;
            port = saved.port;
            user = saved.user;
            password = saved.password;
            domain = saved.domain;
            protocol = saved.protocol;
            signing = saved.signing;
            negotiation = false;
            taskName = "Saved connection";
            displayName = saved.name.isEmpty() ? saved.host : saved.name;
        }
        String key() { return host + ":" + port + ":" + user + ":" + domain + ":" + protocol + ":" + signing + ":" + password.hashCode(); }
        String url(String path) {
            String address = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
            return "smb://" + address + (port.isEmpty() ? "" : ":" + port) + path;
        }
        JcifsAuth auth(GlobalParameters gp) {
            int level = Integer.parseInt(protocol);
            String account = user.isEmpty() ? null : user;
            String secret = password.isEmpty() ? null : password;
            if (protocol.equals(SyncTaskItem.SYNC_FOLDER_SMB_PROTOCOL_SMB1))
                return new JcifsAuth(JcifsAuth.JCIFS_FILE_SMB1, domain, account, secret);
            Properties properties = new Properties();
            properties.setProperty("jcifs.smb.client.responseTimeout", gp.settingsSmbClientResponseTimeout);
            return new JcifsAuth(level, domain, account, secret, signing, negotiation, properties);
        }
    }

    private class EntryAdapter extends BaseAdapter {
        public int getCount() { return visibleEntries.size(); }
        public Object getItem(int position) { return visibleEntries.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View old, ViewGroup parent) {
            Entry e = visibleEntries.get(position);
            LinearLayout outer = new LinearLayout(FileExplorerActivity.this);
            outer.setPadding(dp(16), dp(4), dp(16), dp(4));
            LinearLayout row = new LinearLayout(FileExplorerActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(15), dp(14), dp(14), dp(14));
            row.setBackground(card(PANEL, 14));
            outer.addView(row, new LinearLayout.LayoutParams(-1, -2));
            ImageView icon = new ImageView(FileExplorerActivity.this);
            icon.setImageResource((e.location != null && e.home) || e.path == null ? R.drawable.explorer_nas
                    : e.dir ? R.drawable.explorer_folder : R.drawable.explorer_file);
            row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout labels = new LinearLayout(FileExplorerActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelsParams.leftMargin = dp(15);
            row.addView(labels, labelsParams);
            TextView name = text(e.name, 16, Color.WHITE, true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            labels.addView(name);
            String detail = e.home ? e.note : e.location != null && "/".equals(smbPath)
                    ? "Share" : e.dir ? "Folder" : formatSize(e.size);
            if (!e.home && e.modified > 0) detail += "  ·  " + DateFormat.getDateInstance().format(new Date(e.modified));
            TextView note = text(detail, 12, SECONDARY, false);
            labels.addView(note);
            TextView arrow = text("›", 25, SECONDARY, false);
            row.addView(arrow);
            return outer;
        }
    }
}
