package com.flyingcomputer.gndfull.edgetx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EdgeTxRadioPrefsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void patchStickMode_replacesExistingValue() throws Exception {
        File radio = folder.newFile("radio.yml");
        try (FileOutputStream fos = new FileOutputStream(radio)) {
            fos.write("header: 1\nstickMode: 0\n".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(EdgeTxRadioPrefs.patchStickMode(radio, EdgeTxRadioPrefs.STICK_MODE_2));
        String body = new String(java.nio.file.Files.readAllBytes(radio.toPath()), StandardCharsets.UTF_8);
        assertTrue(body.contains("stickMode: 1"));
        assertFalse(EdgeTxRadioPrefs.patchStickMode(radio, EdgeTxRadioPrefs.STICK_MODE_2));
    }

    @Test
    public void patchStickMode_appendsWhenMissing() throws Exception {
        File radio = folder.newFile("radio.yml");
        try (FileOutputStream fos = new FileOutputStream(radio)) {
            fos.write("header: 1\n".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(EdgeTxRadioPrefs.patchStickMode(radio, EdgeTxRadioPrefs.STICK_MODE_2));
        String body = new String(java.nio.file.Files.readAllBytes(radio.toPath()), StandardCharsets.UTF_8);
        assertEquals("header: 1\nstickMode: 1\n", body);
    }

    @Test
    public void applyDefaults_doesNotRewriteExistingRadioYaml() throws Exception {
        File sd = folder.newFolder("sd");
        File radioDir = new File(sd, "RADIO");
        assertTrue(radioDir.mkdirs());
        File radio = new File(radioDir, "radio.yml");
        String original = "checksum: 12979\nstickMode: 0\n";
        try (FileOutputStream fos = new FileOutputStream(radio)) {
            fos.write(original.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(EdgeTxRadioPrefs.ApplyResult.READY, EdgeTxRadioPrefs.applyDefaults(sd));
        String body = new String(java.nio.file.Files.readAllBytes(radio.toPath()), StandardCharsets.UTF_8);
        assertEquals(original, body);
        assertEquals(EdgeTxRadioPrefs.ApplyResult.NOOP, EdgeTxRadioPrefs.applyDefaults(sd));
        assertEquals(
                original,
                new String(java.nio.file.Files.readAllBytes(radio.toPath()), StandardCharsets.UTF_8));
    }
}
