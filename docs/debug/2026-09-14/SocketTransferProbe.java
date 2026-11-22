import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone synthetic TCP control. No Zorik/Android dependencies or real stream URLs. */
public class SocketTransferProbe {
    private static final byte[] BODY = new byte[256 * 1024];
    private static final byte[] HEADER = ("HTTP/1.1 200 OK\r\nContent-Length: " + BODY.length +
            "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    static { for (int i = 0; i < BODY.length; i++) BODY[i] = (byte)(i % 251); }

    public static void main(String[] args) throws Exception {
        int rounds = Integer.parseInt(args[0]);
        boolean buffered = args.length < 2 || !args[1].equals("raw");
        int failures = 0;
        System.out.println("runtime=" + System.getProperty("java.vm.name") + " " +
                System.getProperty("java.version") + " os=" + System.getProperty("os.name"));
        System.out.println("receiveBuffer=" + Integer.getInteger("probe.receiveBuffer", 8192) +
                " holdOpen=" + Boolean.getBoolean("probe.holdOpen"));
        for (int round = 0; round < rounds; round++) failures += runRound(round, buffered);
        System.out.println("SUMMARY transfers=" + rounds * 3 + " failures=" + failures);
        if (failures != 0) System.exit(1);
    }

    private static int runRound(int round, boolean buffered) throws Exception {
        ExecutorService readers = Executors.newFixedThreadPool(3);
        ExecutorService writers = Executors.newFixedThreadPool(3);
        CountDownLatch started = new CountDownLatch(3);
        List<Future<Boolean>> outcomes = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                final String id = round + "/" + i;
                outcomes.add(readers.submit(() -> transfer(id, buffered, writers, started)));
            }
            int failures = 0;
            for (Future<Boolean> outcome : outcomes) if (!outcome.get(20, TimeUnit.SECONDS)) failures++;
            return failures;
        } finally {
            readers.shutdownNow();
            writers.shutdownNow();
            readers.awaitTermination(5, TimeUnit.SECONDS);
            writers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static boolean transfer(String id, boolean buffered, ExecutorService writers,
                                    CountDownLatch started) throws Exception {
        long start = System.nanoTime();
        try (ServerSocket listener = new ServerSocket(0, 3, InetAddress.getByName("127.0.0.1"));
             Socket client = new Socket()) {
            client.setReceiveBufferSize(Integer.getInteger("probe.receiveBuffer", 8192));
            client.connect(new InetSocketAddress("127.0.0.1", listener.getLocalPort()), 5000);
            client.setSoTimeout(5000);
            final Socket server = listener.accept();
            CountDownLatch bodyRead = new CountDownLatch(1);
            int localPort = client.getLocalPort();
            int remotePort = client.getPort();
            client.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            Future<?> sent = writers.submit(() -> {
                try (Socket owned = server) {
                    owned.setSoTimeout(5000);
                    readHeader(new BufferedInputStream(owned.getInputStream()));
                    started.countDown();
                    BufferedOutputStream out = new BufferedOutputStream(owned.getOutputStream());
                    out.write(HEADER);
                    out.flush();
                    out.write(BODY);
                    out.flush();
                    // Diagnostic control only: compare immediate close with keeping the same
                    // socket alive until the peer has read the body. Neither changes its bytes.
                    if (Boolean.getBoolean("probe.holdOpen")) bodyRead.await(7, TimeUnit.SECONDS);
                }
                return null;
            });
            int received = 0;
            InputStream in = buffered ? new BufferedInputStream(client.getInputStream()) : client.getInputStream();
            try {
                if (!started.await(5, TimeUnit.SECONDS)) throw new AssertionError("writer barrier timed out");
                byte[] header = readHeader(in);
                if (!Arrays.equals(HEADER, header)) throw new AssertionError("headers differ");
                byte[] bytes = new byte[BODY.length];
                while (received < bytes.length) {
                    int n = in.read(bytes, received, bytes.length - received);
                    if (n < 0) throw new AssertionError("early EOF: " + received);
                    received += n;
                }
                if (!Arrays.equals(BODY, bytes)) throw new AssertionError("body differs");
                bodyRead.countDown();
                if (in.read() != -1) throw new AssertionError("unexpected trailing data");
                sent.get(5, TimeUnit.SECONDS);
                System.out.println("PASS " + id + " bytes=" + received + " ms=" + elapsed(start));
                return true;
            } catch (Exception | AssertionError error) {
                System.out.println("FAIL " + id + " bytes=" + received + " available=" + in.available() +
                        " writerDone=" + sent.isDone() + " ports=" + localPort + "->" + remotePort +
                        " ms=" + elapsed(start) + " " + error);
                if (System.getProperty("os.name").startsWith("Windows")) dumpConnection(localPort, remotePort);
                client.close();
                sent.get(5, TimeUnit.SECONDS);
                return false;
            } finally { bodyRead.countDown(); server.close(); }
        }
    }

    private static byte[] readHeader(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        String suffix = "";
        while (!suffix.endsWith("\r\n\r\n")) {
            int value = in.read();
            if (value < 0 || bytes.size() > 16384) throw new AssertionError("invalid header");
            bytes.write(value);
            suffix = (suffix + (char)value);
            if (suffix.length() > 4) suffix = suffix.substring(suffix.length() - 4);
        }
        return bytes.toByteArray();
    }

    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }

    private static void dumpConnection(int local, int remote) throws Exception {
        Process process = new ProcessBuilder("netstat.exe", "-ano", "-p", "tcp").start();
        java.io.BufferedReader lines = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
        String line;
        while ((line = lines.readLine()) != null) {
            if (line.contains("127.0.0.1:" + local + " ") || line.contains("127.0.0.1:" + remote + " "))
                System.out.println("TCP " + line.trim());
        }
        process.waitFor(5, TimeUnit.SECONDS);
    }
}
