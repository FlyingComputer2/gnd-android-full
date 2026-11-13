package com.flyingcomputer.gndfull.edgetx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EdgeTxSdSeedInstallerTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void isUserSettingsPath_matchesRadioAndModelsYaml() {
        assertTrue(EdgeTxSdSeedInstaller.isUserSettingsPath("RADIO/radio.yml"));
        assertTrue(EdgeTxSdSeedInstaller.isUserSettingsPath("./MODELS/model1.yml"));
        assertTrue(EdgeTxSdSeedInstaller.isUserSettingsPath("MODELS\\labels.yaml"));
        assertFalse(EdgeTxSdSeedInstaller.isUserSettingsPath("RADIO/README.txt"));
        assertFalse(EdgeTxSdSeedInstaller.isUserSettingsPath("SOUNDS/en/hello.wav"));
        assertFalse(EdgeTxSdSeedInstaller.isUserSettingsPath("radio.yml"));
        assertFalse(EdgeTxSdSeedInstaller.isUserSettingsPath(""));
    }

    @Test
    public void unzip_skipsExistingUserYamlAndOverwritesOtherFiles() throws Exception {
        File dest = folder.newFolder("sd");
        File radioDir = new File(dest, "RADIO");
        assertTrue(radioDir.mkdirs());
        writeUtf8(new File(radioDir, "radio.yml"), "stickMode: 0\n");
        writeUtf8(new File(radioDir, "README.txt"), "old\n");

        byte[] zip = zipOf(
                entry("RADIO/radio.yml", "stickMode: 9\n"),
                entry("RADIO/README.txt", "seed\n"),
                entry("MODELS/model1.yml", "name: SEED\n"),
                entry("edgetx.sdcard.version", "2.12.3\n"));
        EdgeTxSdSeedInstaller.unzip(new ByteArrayInputStream(zip), dest);

        assertEquals("stickMode: 0\n", readUtf8(new File(radioDir, "radio.yml")));
        assertEquals("seed\n", readUtf8(new File(radioDir, "README.txt")));
        assertEquals("name: SEED\n", readUtf8(new File(dest, "MODELS/model1.yml")));
        assertTrue(EdgeTxSdSeedInstaller.isSeeded(dest));
    }

    @Test
    public void copyUserSettingsIfMissing_doesNotOverwriteExistingYaml() throws Exception {
        File from = folder.newFolder("from");
        File to = folder.newFolder("to");
        File fromRadio = new File(from, "RADIO");
        File toRadio = new File(to, "RADIO");
        assertTrue(fromRadio.mkdirs());
        assertTrue(toRadio.mkdirs());
        writeUtf8(new File(fromRadio, "radio.yml"), "stickMode: 1\n");
        writeUtf8(new File(fromRadio, "README.txt"), "ignore me\n");
        writeUtf8(new File(toRadio, "radio.yml"), "stickMode: 0\n");
        File fromModels = new File(from, "MODELS");
        assertTrue(fromModels.mkdirs());
        writeUtf8(new File(fromModels, "model1.yml"), "name: KEEP\n");

        EdgeTxSdSeedInstaller.copyUserSettingsIfMissing(from, to);

        assertEquals("stickMode: 0\n", readUtf8(new File(toRadio, "radio.yml")));
        assertFalse(new File(toRadio, "README.txt").exists());
        assertEquals("name: KEEP\n", readUtf8(new File(to, "MODELS/model1.yml")));
    }

    @Test
    public void selectSdRoot_fallsBackWhenPersistIsNotADirectory() throws Exception {
        File persist = folder.newFile("not-a-dir");
        File priv = folder.newFolder("priv");
        assertEquals(priv, EdgeTxSdSeedInstaller.selectSdRoot(persist, priv));
    }

    @Test
    public void selectSdRoot_prefersWritablePersist() throws Exception {
        File persist = folder.newFolder("persist");
        File priv = folder.newFolder("priv");
        assertEquals(persist, EdgeTxSdSeedInstaller.selectSdRoot(persist, priv));
    }

    @Test
    public void resolveZipEntry_rejectsPathEscape() throws Exception {
        File dest = folder.newFolder("sd");
        try {
            EdgeTxSdSeedInstaller.resolveZipEntry(dest, "../evil.yml");
            throw new AssertionError("expected IOException");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("zip slip"));
        }
    }

    private static void writeUtf8(File file, String body) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readUtf8(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static final class ZipItem {
        final String name;
        final String body;

        ZipItem(String name, String body) {
            this.name = name;
            this.body = body;
        }
    }

    private static ZipItem entry(String name, String body) {
        return new ZipItem(name, body);
    }

    private static byte[] zipOf(ZipItem... items) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (ZipItem item : items) {
                zos.putNextEntry(new ZipEntry(item.name));
                zos.write(item.body.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
