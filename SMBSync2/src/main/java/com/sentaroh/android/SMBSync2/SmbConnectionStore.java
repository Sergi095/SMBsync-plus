package com.sentaroh.android.SMBSync2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.sentaroh.android.Utilities.EncryptUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/** Explorer connections are saved separately from sync tasks and encrypted at rest. */
final class SmbConnectionStore {
    // Keep this key and the AndroidKeyStore alias stable across app updates.
    private static final String STORAGE_KEY = "smbsync_plus_explorer_connections_v1";

    static final class Connection {
        String id = UUID.randomUUID().toString();
        String name = "";
        String host = "";
        String port = "";
        String user = "";
        String password = "";
        String domain = "";
        String protocol = SyncTaskItem.SYNC_FOLDER_SMB_PROTOCOL_DEFAULT;
        boolean signing;
    }

    private static String key(Context context) throws Exception {
        return Build.VERSION.SDK_INT >= 28
                ? KeyStoreUtil.getGeneratedPasswordNewVersion(context, Constants.SMBSYNC2_KEY_STORE_ALIAS)
                : KeyStoreUtil.getGeneratedPasswordOldVersion(context, Constants.SMBSYNC2_KEY_STORE_ALIAS);
    }

    static ArrayList<Connection> load(Context context) throws IOException {
        ArrayList<Connection> result = new ArrayList<>();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String stored = preferences.getString(STORAGE_KEY, "");
        if (stored.isEmpty()) return result;
        try {
            String json = EncryptUtil.decrypt(Base64.decode(stored, Base64.NO_WRAP),
                    EncryptUtil.initDecryptEnv(key(context)));
            JSONArray values = new JSONArray(json);
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i);
                Connection connection = new Connection();
                connection.id = value.getString("id");
                connection.name = value.optString("name", "");
                connection.host = value.getString("host");
                connection.port = value.optString("port", "");
                connection.user = value.optString("user", "");
                connection.password = value.optString("password", "");
                connection.domain = value.optString("domain", "");
                connection.protocol = value.optString("protocol", SyncTaskItem.SYNC_FOLDER_SMB_PROTOCOL_DEFAULT);
                connection.signing = value.optBoolean("signing", false);
                result.add(connection);
            }
            return result;
        } catch (Exception e) {
            throw new IOException("Saved SMB connections could not be unlocked.", e);
        }
    }

    static void save(Context context, ArrayList<Connection> connections) throws IOException {
        try {
            JSONArray values = new JSONArray();
            for (Connection connection : connections) {
                JSONObject value = new JSONObject();
                value.put("id", connection.id);
                value.put("name", connection.name);
                value.put("host", connection.host);
                value.put("port", connection.port);
                value.put("user", connection.user);
                value.put("password", connection.password);
                value.put("domain", connection.domain);
                value.put("protocol", connection.protocol);
                value.put("signing", connection.signing);
                values.put(value);
            }
            String encrypted = Base64.encodeToString(EncryptUtil.encrypt(values.toString(),
                    EncryptUtil.initDecryptEnv(key(context))), Base64.NO_WRAP);
            if (!PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(STORAGE_KEY, encrypted).commit()) {
                throw new IOException("Could not save SMB connections.");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not securely save SMB connections.", e);
        }
    }
}
