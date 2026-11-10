package com.flyingcomputer.gndfull.edgetx;

import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Copies bundled EdgeTX SD seed (layout + SOUNDS/en) into app-private storage. */
public final class EdgeTxSdSeedInstaller {
    private static final String TAG = "EdgeTxSdSeed";
    static final String ASSET_ZIP = "edgetx/edgetx-sd-seed.zip";
    static final String SD_SEED_VERSION = "2.12.3";

    private EdgeTxSdSeedInstaller() {}

    public static File ensureSdRoot(Context context) throws IOException {
        File sdRoot = new File(context.getFilesDir(), "edgetx/sd");
        if (!sdRoot.mkdirs() && !sdRoot.isDirectory()) {
            throw new IOException("cannot create " + sdRoot);
        }
        if (isSeeded(sdRoot)) {
            return sdRoot;
        }
        try (InputStream in = context.getAssets().open(ASSET_ZIP)) {
            unzip(in, sdRoot);
            Log.i(TAG, "seeded SD to " + sdRoot.getAbsolutePath());
        } catch (IOException missing) {
            Log.w(
                    TAG,
                    "missing asset "
                            + ASSET_ZIP
                            + "; run scripts/fetch-edgetx-sd-assets.sh before assemble");
            throw missing;
        }
        return sdRoot;
    }

    private static boolean isSeeded(File sdRoot) {
        File marker = new File(sdRoot, "edgetx.sdcard.version");
        if (!marker.isFile()) {
            return false;
        }
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new java.io.FileInputStream(marker), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line != null && SD_SEED_VERSION.equals(line.trim());
        } catch (IOException e) {
            Log.w(TAG, "version read failed: " + e.getMessage());
            return false;
        }
    }

    private static void unzip(InputStream zipStream, File destDir) throws IOException {
        byte[] buffer = new byte[8192];
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(zipStream))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) {
                        throw new IOException("mkdir failed: " + out);
                    }
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                        throw new IOException("mkdir failed: " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zin.read(buffer)) > 0) {
                            fos.write(buffer, 0, n);
                        }
                    }
                }
                zin.closeEntry();
            }
        }
    }
}
