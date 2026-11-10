package com.flyingcomputer.gndfull.edgetx;

import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies gnd-android-full defaults to EdgeTX {@code RADIO/radio.yml} on the SD seed root. */
public final class EdgeTxRadioPrefs {
    private static final String TAG = "EdgeTxRadioPrefs";
    private static final String PREFS_MARKER = "edgetx.prefs.version";
    /** Bump to re-apply defaults on existing installs. */
    static final String PREFS_VERSION = "2";

    /** EdgeTX UI "Mode 2" is {@code stickMode: 1}. */
    public static final int STICK_MODE_2 = 1;

    private static final Pattern STICK_MODE_LINE =
            Pattern.compile("^(stickMode:\\s*)\\d+\\s*$", Pattern.MULTILINE);

    private EdgeTxRadioPrefs() {}

  public enum ApplyResult {
        NOOP,
        /** {@code radio.yml} already patched before wasm loaded settings. */
        READY,
        /** Wait for first boot to create {@code radio.yml}, then patch and restart radio once. */
        NEEDS_POST_BOOT_PATCH,
    }

    /** Patches {@code radio.yml} before wasm start when the file already exists. */
    public static ApplyResult applyDefaults(File sdRoot) throws IOException {
        if (sdRoot == null || !sdRoot.isDirectory()) {
            throw new IOException("invalid sd root");
        }
        File marker = new File(sdRoot, PREFS_MARKER);
        if (isPrefsApplied(marker)) {
            return ApplyResult.NOOP;
        }
        File radio = radioYaml(sdRoot);
        if (!radio.isFile()) {
            return ApplyResult.NEEDS_POST_BOOT_PATCH;
        }
        patchStickMode(radio, STICK_MODE_2);
        writeMarker(marker);
        return ApplyResult.READY;
    }

    /** After first boot created {@code radio.yml}, patch stick mode and mark prefs applied. */
    public static boolean completePostBootDefaults(File sdRoot) throws IOException {
        File marker = new File(sdRoot, PREFS_MARKER);
        if (isPrefsApplied(marker)) {
            return false;
        }
        File radio = radioYaml(sdRoot);
        if (!radio.isFile()) {
            return false;
        }
        boolean patched = patchStickMode(radio, STICK_MODE_2);
        writeMarker(marker);
        return patched;
    }

    static boolean patchStickMode(File radioYaml, int stickMode) throws IOException {
        String body = readUtf8(radioYaml);
        Matcher matcher = STICK_MODE_LINE.matcher(body);
        String next;
        if (matcher.find()) {
            String line = matcher.group();
            int current = Integer.parseInt(line.replaceAll("\\D+", ""));
            if (current == stickMode) {
                return false;
            }
            next = matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + stickMode));
        } else {
            String suffix = body.endsWith("\n") || body.isEmpty() ? "" : "\n";
            next = body + suffix + "stickMode: " + stickMode + "\n";
        }
        writeUtf8(radioYaml, next);
        return true;
    }

    private static File radioYaml(File sdRoot) {
        return new File(sdRoot, "RADIO/radio.yml");
    }

    private static boolean isPrefsApplied(File marker) throws IOException {
        if (!marker.isFile()) {
            return false;
        }
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(marker), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line != null && PREFS_VERSION.equals(line.trim());
        }
    }

    private static void writeMarker(File marker) throws IOException {
        File parent = marker.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create " + parent);
        }
        try (BufferedWriter writer =
                new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(marker), StandardCharsets.UTF_8))) {
            writer.write(PREFS_VERSION);
            writer.newLine();
        }
    }

    private static String readUtf8(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void writeUtf8(File file, String body) throws IOException {
        try (BufferedWriter writer =
                new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(body);
            if (!body.endsWith("\n")) {
                writer.newLine();
            }
        }
    }
}
