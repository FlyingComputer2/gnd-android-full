package com.flyingcomputer.gndfull;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The app's user-visible storage root, {@code Download/FlyingComputer} (needs "All files access",
 * MANAGE_EXTERNAL_STORAGE): the Files app and a PC over USB see it, and it survives APK
 * reinstalls. Without access it falls back to the app's private files directory.
 *
 * <pre>
 *   FlyingComputer/
 *     edgetx/sd/   the EdgeTX radio's SD card (models, scripts, sounds; EdgeTxSdSeedInstaller)
 *     scp/         rover files (the CLI's "rov fs" console: get / put over udpfs)
 * </pre>
 */
public final class AppStorage {
    public static final String PUBLIC_RELATIVE = "FlyingComputer";
    public static final String EDGETX = "edgetx";
    public static final String SCP = "scp";

    private AppStorage() {}

    public static File publicRoot() {
        File downloads =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloads, PUBLIC_RELATIVE);
    }

    /** True when the public root can be written (All files access granted). */
    public static boolean publicUsable() {
        return canUseDirectory(publicRoot());
    }

    /** The public root when usable, else the app's private files directory. */
    public static File root(Context context) {
        File pub = publicRoot();
        return canUseDirectory(pub) ? pub : context.getFilesDir();
    }

    public static File scp(Context context) {
        File d = new File(root(context), SCP);
        if (!d.isDirectory()) {
            d.mkdirs();
        }
        return d;
    }

    /** Creates dir if needed and checks a file can be written there. */
    public static boolean canUseDirectory(File dir) {
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
}
