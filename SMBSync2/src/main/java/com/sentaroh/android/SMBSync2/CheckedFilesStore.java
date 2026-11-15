package com.sentaroh.android.SMBSync2;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/** A private, per-run record of every source file visited by the sync scanner. */
final class CheckedFilesStore {
    private CheckedFilesStore() {}

    static final class Writer {
        final File file;
        private final BufferedWriter output;
        private boolean failed;

        Writer(Context context) throws IOException {
            File directory = new File(context.getFilesDir(), "checked_files");
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create checked files directory");
            file = new File(directory, UUID.randomUUID().toString() + ".txt");
            output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        }

        void add(String path) {
            if (failed) return;
            try {
                output.write(Base64.encodeToString(path.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                output.newLine();
            } catch (IOException e) {
                failed = true;
            }
        }

        String finish() {
            try {
                output.close();
            } catch (IOException e) {
                failed = true;
            }
            if (failed) {
                file.delete();
                return "";
            }
            return file.getAbsolutePath();
        }
    }

    static ArrayList<String> read(String filePath, int limit) {
        ArrayList<String> result = new ArrayList<>();
        if (filePath == null || filePath.isEmpty()) return result;
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while (result.size() < limit && (line = input.readLine()) != null) {
                try {
                    result.add(new String(Base64.decode(line, Base64.NO_WRAP), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ignored) {
                    // Keep the remaining entries available if one record is damaged.
                }
            }
        } catch (IOException ignored) {
            // A run may predate this record or its history may have been removed.
        }
        return result;
    }
}
