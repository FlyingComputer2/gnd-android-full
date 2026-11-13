package com.flyingcomputer.gndfull.edgetx;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Copies bundled EdgeTX SD seed (layout + SOUNDS/en) into a persist directory that
 * survives APK reinstall. {@code RADIO/*.yml} and {@code MODELS/*.yml} are never
 * overwritten once present.
 */
public final class EdgeTxSdSeedInstaller {
    private static final String TAG = "EdgeTxSdSeed";
    static final String ASSET_ZIP = "edgetx/edgetx-sd-seed.zip";
    static final String SD_SEED_VERSION = "2.12.3";
    static final String PERSIST_RELATIVE = "FlyingComputer/edgetx/sd";
    private static final String PRIVATE_RELATIVE = "edgetx/sd";

    private EdgeTxSdSeedInstaller() {}

    public static File ensureSdRoot(Context context) throws IOException {
        File privateRoot = new File(context.getFilesDir(), PRIVATE_RELATIVE);
        File persistRoot = publicPersistRoot();
        File sdRoot = selectSdRoot(persistRoot, privateRoot);
        if (!sdRoot.equals(privateRoot) && privateRoot.isDirectory()) {
            copyUserSettingsIfMissing(privateRoot, sdRoot);
        }
        if (isSeeded(sdRoot)) {
            Log.i(TAG, "SD root " + sdRoot.getAbsolutePath());
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

    static File publicPersistRoot() {
        File downloads =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloads, PERSIST_RELATIVE);
    }

    static File selectSdRoot(File persistRoot, File privateRoot) throws IOException {
        if (canUseDirectory(persistRoot)) {
            return persistRoot;
        }
        if (!privateRoot.mkdirs() && !privateRoot.isDirectory()) {
            throw new IOException("cannot create " + privateRoot);
        }
        return privateRoot;
    }

    static boolean canUseDirectory(File dir) {
        if (dir == null) {
            return false;
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            return false;
        }
        File probe = new File(dir, ".gndfull-write-test");
        try (FileOutputStream fos = new FileOutputStream(probe)) {
            fos.write('x');
        } catch (IOException e) {
            return false;
        }
        if (!probe.delete()) {
            probe.deleteOnExit();
        }
        return true;
    }

    static boolean isUserSettingsPath(String zipName) {
        if (zipName == null || zipName.isEmpty()) {
            return false;
        }
        String name = zipName.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        int slash = name.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        String top = name.substring(0, slash);
        if (!"RADIO".equalsIgnoreCase(top) && !"MODELS".equalsIgnoreCase(top)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    static boolean isSeeded(File sdRoot) {
        File marker = new File(sdRoot, "edgetx.sdcard.version");
        if (!marker.isFile()) {
            return false;
        }
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(marker), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line != null && SD_SEED_VERSION.equals(line.trim());
        } catch (IOException e) {
            return false;
        }
    }

    static void copyUserSettingsIfMissing(File fromRoot, File toRoot) throws IOException {
        copyYamlTree(new File(fromRoot, "RADIO"), new File(toRoot, "RADIO"));
        copyYamlTree(new File(fromRoot, "MODELS"), new File(toRoot, "MODELS"));
    }

    static void copyYamlTree(File fromDir, File toDir) throws IOException {
        if (!fromDir.isDirectory()) {
            return;
        }
        File[] files = fromDir.listFiles();
        if (files == null) {
            return;
        }
        if (!toDir.mkdirs() && !toDir.isDirectory()) {
            throw new IOException("cannot create " + toDir);
        }
        for (File src : files) {
            if (!src.isFile() || !isUserSettingsPath(fromDir.getName() + "/" + src.getName())) {
                continue;
            }
            File dest = new File(toDir, src.getName());
            if (dest.exists()) {
                continue;
            }
            copyFile(src, dest);
        }
    }

    static void unzip(InputStream zipStream, File destDir) throws IOException {
        byte[] buffer = new byte[8192];
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(zipStream))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                File out = resolveZipEntry(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) {
                        throw new IOException("mkdir failed: " + out);
                    }
                    zin.closeEntry();
                    continue;
                }
                if (out.isFile() && isUserSettingsPath(entry.getName())) {
                    zin.closeEntry();
                    continue;
                }
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
                zin.closeEntry();
            }
        }
    }

    static File resolveZipEntry(File destDir, String name) throws IOException {
        File out = new File(destDir, name);
        String destPath = destDir.getCanonicalPath();
        String outPath = out.getCanonicalPath();
        if (!outPath.equals(destPath) && !outPath.startsWith(destPath + File.separator)) {
            throw new IOException("zip slip: " + name);
        }
        return out;
    }

    private static void copyFile(File src, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create " + parent);
        }
        try (FileInputStream in = new FileInputStream(src);
                FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }
}
