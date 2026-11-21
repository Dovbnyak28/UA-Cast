import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
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

/** Run with the actual debug APK on dalvikvm's classpath; no APK install or Application startup. */
public class ActualProxyTransferProbe {
    private static final byte[] BODY = new byte[256 * 1024];
    static { for (int i = 0; i < BODY.length; i++) BODY[i] = (byte)(i % 251); }

    public static void main(String[] args) throws Exception {
        int rounds = Integer.parseInt(args[0]);
        ClassLoader loader = ActualProxyTransferProbe.class.getClassLoader();
        Class<?> serverClass = Class.forName("com.uacastplayer.data.cast.ProxyHttpServer");
        Class<?> authType = Class.forName("kotlin.jvm.functions.Function1");
        Class<?> responseType = Class.forName("kotlin.jvm.functions.Function2");
        Object unit = Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        Object auth = Proxy.newProxyInstance(loader, new Class<?>[]{authType}, (p, m, a) -> true);
        int transfers = 0;
        for (int round = 0; round < rounds; round++) {
            CountDownLatch started = new CountDownLatch(3);
            Object respond = Proxy.newProxyInstance(loader, new Class<?>[]{responseType}, (p, m, a) -> {
                OutputStream out = (OutputStream)a[1];
                started.countDown();
                out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + BODY.length +
                        "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(BODY);
                out.flush();
                return unit;
            });
            Object server = serverClass.getConstructor(authType, int.class, responseType)
                    .newInstance(auth, 15000, respond);
            int port = (Integer)serverClass.getMethod("start").invoke(server);
            ExecutorService readers = Executors.newFixedThreadPool(3);
            List<Future<?>> results = new ArrayList<>();
            long began = System.nanoTime();
            try {
                for (int i = 0; i < 3; i++) results.add(readers.submit(() -> { receive(port, started); return null; }));
                for (Future<?> result : results) result.get(15, TimeUnit.SECONDS);
                transfers += 3;
                System.out.println("PASS round=" + round + " ms=" + (System.nanoTime() - began) / 1000000);
            } finally {
                serverClass.getMethod("stop").invoke(server);
                readers.shutdownNow();
                readers.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
        System.out.println("ACTUAL_PROXY_SUMMARY transfers=" + transfers + " failures=0");
    }

    private static void receive(int port, CountDownLatch started) throws Exception {
        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(8192);
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write("GET /large HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            if (!started.await(5, TimeUnit.SECONDS)) throw new AssertionError("handler did not start");
            InputStream input = new BufferedInputStream(socket.getInputStream());
            StringBuilder header = new StringBuilder();
            while (!header.toString().endsWith("\r\n\r\n")) {
                int next = input.read();
                if (next < 0 || header.length() > 16384) throw new AssertionError("invalid header");
                header.append((char)next);
            }
            if (!header.toString().contains("Content-Length: " + BODY.length)) throw new AssertionError("wrong length");
            byte[] received = new byte[BODY.length];
            int offset = 0;
            while (offset < received.length) {
                int count = input.read(received, offset, received.length - offset);
                if (count < 0) throw new AssertionError("EOF at " + offset);
                offset += count;
            }
            if (!Arrays.equals(received, BODY)) throw new AssertionError("corrupt body");
            if (input.read() != -1) throw new AssertionError("missing EOF");
        }
    }
}
