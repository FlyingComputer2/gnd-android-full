package com.flyingcomputer.gndfull.udpfs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flyingcomputer.gndfull.cli.ScpConsole;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UdpfsClientTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    /** The fake server behind a link that drops, duplicates and reorders. */
    static final class LossyLink implements ScpConsole.Link {
        final FakeUdpfsServer server = new FakeUdpfsServer();
        final Deque<byte[]> rx = new ArrayDeque<>();
        final Random rnd;
        final double loss;

        LossyLink(double loss, long seed) {
            this.loss = loss;
            this.rnd = new Random(seed);
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void drain() {
            rx.clear();
        }

        @Override
        public boolean send(byte[] d) {
            if (rnd.nextDouble() < loss) {
                return true;
            }
            int copies = rnd.nextDouble() < 0.1 ? 2 : 1;
            for (int i = 0; i < copies; i++) {
                byte[] r = server.handle(d);
                if (rnd.nextDouble() >= loss) {
                    if (!rx.isEmpty() && rnd.nextDouble() < 0.3) {
                        rx.addFirst(r);
                    } else {
                        rx.addLast(r);
                    }
                }
            }
            return true;
        }

        @Override
        public byte[] receive(long timeoutMs) {
            return rx.poll();
        }
    }

    static UdpfsClient.Config fast() {
        UdpfsClient.Config c = new UdpfsClient.Config();
        c.initialRtoMs = 1;
        c.minRtoMs = 1;
        c.maxRtoMs = 20;
        c.giveUpMs = 30000;
        return c;
    }

    @Test
    public void wireRequestIsLittleEndianAfterOpAndReqid() {
        byte[] b = new UdpfsWire.Request(UdpfsWire.OP_READ, 0x01020304).i32(7).u64(0x1122).u32(1387).toBytes();
        assertArrayEquals(
                new byte[] {3, 4, 3, 2, 1, 7, 0, 0, 0, 0x22, 0x11, 0, 0, 0, 0, 0, 0, 0x6b, 5, 0, 0},
                b);
        assertEquals("ENOENT", UdpfsWire.errnoName(2));
        assertEquals("E999", UdpfsWire.errnoName(999));
    }

    @Test
    public void getAndPutThroughTheConsoleOverALossyLink() throws Exception {
        File root = folder.newFolder("root");
        File remote = folder.newFolder("remote");
        byte[] photo = new byte[200 * 1024 + 13];
        new Random(1).nextBytes(photo);
        Files.write(new File(remote, "photo.jpg").toPath(), photo);
        File models = new File(root, "edgetx/sd/MODELS");
        assertTrue(models.mkdirs());
        Files.write(new File(models, "model01.yml").toPath(), "name: M1\n".getBytes());

        LossyLink link = new LossyLink(0.2, 3);
        ScpConsole c = new ScpConsole(link, () -> root, fast());
        List<String> out = new ArrayList<>();
        c.run("get " + remote + "/photo.jpg", out::add);
        c.run("put edgetx/sd/MODELS/model01.yml " + remote, out::add);
        c.run("ls -l " + remote, out::add);
        c.run("list_open", out::add);
        c.run("get /nonexistent ../escape", out::add);

        assertArrayEquals(photo, Files.readAllBytes(new File(root, "scp/photo.jpg").toPath()));
        assertEquals("name: M1\n", new String(Files.readAllBytes(new File(remote, "model01.yml").toPath())));
        String all = String.join("\n", out);
        assertTrue(all, all.contains("model01.yml"));
        assertTrue(all, all.contains("(none)"));
        assertTrue(all, all.contains("'..' not allowed"));
        assertEquals(0, link.server.openHandles());
        assertTrue(!new File(root, "scp/photo.jpg.part").exists());
    }
}
