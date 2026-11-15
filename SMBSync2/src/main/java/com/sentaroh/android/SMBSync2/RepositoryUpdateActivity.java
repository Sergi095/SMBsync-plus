package com.sentaroh.android.SMBSync2;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks the owner's GitHub Releases and installs only a newer APK signed by this app's key. */
public class RepositoryUpdateActivity extends Activity {
    public static final String REPOSITORY = "https://github.com/Sergi095/SMBsync-plus";
    public static final String RELEASES_URL = REPOSITORY + "/releases";
    private static final String RELEASE_API = "https://api.github.com/repos/Sergi095/SMBsync-plus/releases/latest";
    private static final long MAX_APK_BYTES = 80L * 1024 * 1024;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button check, download;
    private Release available;
    private File verifiedApk;

    private static class Release {
        String tag, version, url, sha256;
        long size;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 32, 24, 24);
        page.setBackgroundColor(Color.rgb(9, 18, 34));
        TextView title = label("App updates", 27, Color.WHITE);
        page.addView(title);
        page.addView(label("Installed: " + BuildConfig.VERSION_NAME, 15, Color.rgb(170, 189, 207)));
        page.addView(label("Update source: " + RELEASES_URL, 14, Color.rgb(170, 189, 207)));
        Button openReleases = button("Open release page");
        openReleases.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(RELEASES_URL))));
        page.addView(openReleases);
        page.addView(label("Check for a signed SMBsync+ release from this repository.",
                15, Color.WHITE));
        status = label("Ready to check.", 15, Color.rgb(41, 197, 239));
        page.addView(status);
        check = button("Check for updates");
        check.setOnClickListener(v -> checkForUpdates());
        page.addView(check);
        download = button("Download and install");
        download.setVisibility(View.GONE);
        download.setOnClickListener(v -> downloadUpdate());
        page.addView(download);
        setContentView(page);
    }

    private TextView label(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setPadding(0, 0, 0, 22);
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        return view;
    }

    private void showStatus(String value) {
        runOnUiThread(() -> { if (!isFinishing()) status.setText(value); });
    }

    private void checkForUpdates() {
        check.setEnabled(false);
        download.setVisibility(View.GONE);
        available = null;
        status.setText("Checking GitHub Releases…");
        io.execute(() -> {
            try {
                Release release = fetchRelease();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    check.setEnabled(true);
                    if (!newerThanInstalled(release.version)) {
                        status.setText("You have the latest release.");
                    } else {
                        available = release;
                        status.setText("Version " + release.version + " is available.");
                        download.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    check.setEnabled(true);
                    status.setText(error.getMessage());
                });
            }
        });
    }

    private Release fetchRelease() throws Exception {
        HttpURLConnection connection = open(RELEASE_API);
        try {
            if (connection.getResponseCode() == 404)
                throw new IllegalStateException("No SMBsync+ release is available in the owner's repository yet.");
            if (connection.getResponseCode() != 200)
                throw new IllegalStateException("Could not check GitHub Releases (HTTP " + connection.getResponseCode() + ").");
            byte[] body = readLimited(connection.getInputStream(), 1024 * 1024);
            JSONObject json = new JSONObject(new String(body, java.nio.charset.StandardCharsets.UTF_8));
            String tag = json.getString("tag_name");
            if (!tag.matches("v[0-9]+\\.[0-9]+-plus\\.[0-9]+"))
                throw new IllegalStateException("Release version has an unexpected format.");
            String filename = "SMBsync-plus-" + tag + ".apk";
            String expectedUrl = REPOSITORY + "/releases/download/" + tag + "/" + filename;
            JSONArray assets = json.getJSONArray("assets");
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                if (!filename.equals(asset.optString("name"))) continue;
                Release release = new Release();
                release.tag = tag;
                release.version = tag.substring(1);
                release.url = asset.getString("browser_download_url");
                release.size = asset.getLong("size");
                String digest = asset.optString("digest");
                if (!expectedUrl.equals(release.url) || !digest.matches("sha256:[a-fA-F0-9]{64}")
                        || release.size <= 0 || release.size > MAX_APK_BYTES)
                    throw new IllegalStateException("Release APK metadata could not be verified.");
                release.sha256 = digest.substring(7);
                return release;
            }
            throw new IllegalStateException("The latest release has no SMBsync+ APK.");
        } finally { connection.disconnect(); }
    }

    private static boolean newerThanInstalled(String version) {
        String[] newest = version.replace("-plus.", ".").split("\\.");
        String[] installed = BuildConfig.VERSION_NAME.replace("-plus.", ".").split("\\.");
        for (int index = 0; index < Math.max(newest.length, installed.length); index++) {
            int left = index < newest.length ? Integer.parseInt(newest[index]) : 0;
            int right = index < installed.length ? Integer.parseInt(installed[index]) : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "SMBsync-plus");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static byte[] readLimited(InputStream input, int max) throws Exception {
        try (InputStream stream = input; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > max) throw new IllegalStateException("Release response is too large.");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void downloadUpdate() {
        if (available == null) return;
        Release release = available;
        check.setEnabled(false);
        download.setEnabled(false);
        status.setText("Downloading from GitHub…");
        io.execute(() -> {
            File part = new File(getCacheDir(), "SMBsync-plus-update.part.apk");
            try {
                HttpURLConnection connection = open(release.url);
                try {
                    if (connection.getResponseCode() != 200 || !"https".equals(connection.getURL().getProtocol()))
                        throw new IllegalStateException("APK download failed.");
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long total = 0;
                    try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(part)) {
                        byte[] buffer = new byte[65536];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            total += count;
                            if (total > MAX_APK_BYTES || total > release.size)
                                throw new IllegalStateException("APK exceeds its release size.");
                            output.write(buffer, 0, count);
                            digest.update(buffer, 0, count);
                        }
                        output.getFD().sync();
                    }
                    if (total != release.size || !release.sha256.equalsIgnoreCase(hex(digest.digest())))
                        throw new IllegalStateException("APK checksum does not match the release.");
                } finally { connection.disconnect(); }
                verifyPackage(part, release.version);
                File apk = new File(getCacheDir(), "SMBsync-plus-update.apk");
                if (apk.exists() && !apk.delete()) throw new IllegalStateException("Could not replace previous update.");
                if (!part.renameTo(apk)) throw new IllegalStateException("Could not save verified update.");
                verifiedApk = apk;
                runOnUiThread(() -> { if (!isFinishing()) { check.setEnabled(true); download.setEnabled(true); status.setText("Downloaded and verified. Confirm installation in Android."); installUpdate(); } });
            } catch (Exception error) {
                showStatus("Update failed: " + error.getMessage());
                runOnUiThread(() -> { check.setEnabled(true); download.setEnabled(true); });
            } finally { if (part.exists()) part.delete(); }
        });
    }

    private void verifyPackage(File apk, String version) throws Exception {
        PackageManager pm = getPackageManager();
        int flags = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= 28) flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo incoming = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(getPackageName(), flags);
        if (incoming == null || !getPackageName().equals(incoming.packageName)
                || !version.equals(incoming.versionName)
                || (Build.VERSION.SDK_INT >= 28 ? incoming.getLongVersionCode() <= installed.getLongVersionCode()
                : incoming.versionCode <= installed.versionCode))
            throw new IllegalStateException("Downloaded APK is not a newer SMBsync+ build.");
        Signature[] next = signers(incoming), current = signers(installed);
        if (next == null || current == null || next.length != current.length)
            throw new IllegalStateException("APK signing information is missing.");
        for (int index = 0; index < next.length; index++)
            if (!Arrays.equals(next[index].toByteArray(), current[index].toByteArray()))
                throw new IllegalStateException("APK signing key differs from the installed app.");
    }

    private static Signature[] signers(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            Signature[] result = info.signingInfo.getApkContentsSigners();
            if (result != null && result.length > 0) return result;
        }
        return info.signatures;
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return text.toString();
    }

    private void installUpdate() {
        if (verifiedApk == null || !verifiedApk.isFile()) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText("Allow SMBsync+ to install apps, then return here.");
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())), 1701);
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", verifiedApk);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.setClipData(ClipData.newRawUri("SMBsync+ update", uri));
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(install);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1701 && Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls())
            installUpdate();
    }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}
