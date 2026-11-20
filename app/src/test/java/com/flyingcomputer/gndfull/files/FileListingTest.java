package com.flyingcomputer.gndfull.files;

import static org.junit.Assert.assertEquals;

import com.flyingcomputer.gndfull.udpfs.UdpfsWire;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class FileListingTest {
    private static FileListing.Entry f(String name, long mtime) {
        return new FileListing.Entry(name, UdpfsWire.TYPE_FILE, 10, mtime);
    }

    private static FileListing.Entry d(String name, long mtime) {
        return new FileListing.Entry(name, UdpfsWire.TYPE_DIR, 0, mtime);
    }

    private static List<String> names(List<FileListing.Entry> l) {
        List<String> out = new ArrayList<>();
        for (FileListing.Entry e : l) {
            out.add(e.name());
        }
        return out;
    }

    @Test
    public void paths() {
        assertEquals("/", FileListing.normalize(""));
        assertEquals("/", FileListing.normalize("/.."));
        assertEquals("/sdcard/photo", FileListing.normalize(" sdcard//photo/./ "));
        assertEquals("/sdcard", FileListing.normalize("/sdcard/photo/.."));
        assertEquals("/sdcard", FileListing.parent("/sdcard/photo"));
        assertEquals("/", FileListing.parent("/sdcard"));
        assertEquals("/", FileListing.parent("/"));
        assertEquals("/sdcard/a.jpg", FileListing.child("/sdcard/", "a.jpg"));
        assertEquals("/a", FileListing.child("/", "a"));
    }

    @Test
    public void typeLabel() {
        assertEquals("dir", FileListing.typeLabel(d("photo", 0)));
        assertEquals("jpg", FileListing.typeLabel(f("ROV_0001.JPG", 0)));
        assertEquals("file", FileListing.typeLabel(f("README", 0)));
        assertEquals("file", FileListing.typeLabel(f(".hidden", 0)));
        assertEquals("file", FileListing.typeLabel(f("trailing.", 0)));
    }

    @Test
    public void sortKeepsDirectoriesFirst() {
        List<FileListing.Entry> l = new ArrayList<>(List.of(
                f("b.txt", 3), d("zdir", 1), f("a.jpg", 5), d("Adir", 9), f("C.conf", 1)));
        FileListing.sort(l, FileListing.Column.NAME, true);
        assertEquals(List.of("Adir", "zdir", "a.jpg", "b.txt", "C.conf"), names(l));
        FileListing.sort(l, FileListing.Column.NAME, false);
        assertEquals(List.of("zdir", "Adir", "C.conf", "b.txt", "a.jpg"), names(l));
        FileListing.sort(l, FileListing.Column.TYPE, true);
        assertEquals(List.of("Adir", "zdir", "C.conf", "a.jpg", "b.txt"), names(l));
        FileListing.sort(l, FileListing.Column.MODIFIED, false);
        assertEquals(List.of("Adir", "zdir", "a.jpg", "b.txt", "C.conf"), names(l));
    }

    @Test
    public void sortBySize() {
        List<FileListing.Entry> l = new ArrayList<>(List.of(
                new FileListing.Entry("big", UdpfsWire.TYPE_FILE, 5000, 0),
                d("dir", 0),
                new FileListing.Entry("small", UdpfsWire.TYPE_FILE, 3, 0),
                new FileListing.Entry("mid", UdpfsWire.TYPE_FILE, 700, 0)));
        FileListing.sort(l, FileListing.Column.SIZE, true);
        assertEquals(List.of("dir", "small", "mid", "big"), names(l));
        FileListing.sort(l, FileListing.Column.SIZE, false);
        assertEquals(List.of("dir", "big", "mid", "small"), names(l));
    }

    @Test
    public void selectionSummary() {
        assertEquals("0 selected", FileListing.selectionSummary(List.of()));
        assertEquals("1 selected: 1 file 10 B", FileListing.selectionSummary(List.of(f("a", 0))));
        assertEquals("3 selected: 2 files 20 B, 1 dir",
                FileListing.selectionSummary(List.of(f("a", 0), d("x", 0), f("b", 0))));
        assertEquals("2 selected: 2 dirs", FileListing.selectionSummary(List.of(d("x", 0), d("y", 0))));
    }
}
