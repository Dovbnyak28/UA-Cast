using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

// Independent .NET control for a Windows TCP symptom, not an app implementation.
public static class TcpTransferDotNetProbe {
    private static readonly byte[] Body = new byte[256 * 1024];
    static TcpTransferDotNetProbe() {
        for (int i = 0; i < Body.Length; i++) Body[i] = (byte)(i % 251);
    }

    public static int Run(int rounds) {
        int failures = 0;
        for (int round = 0; round < rounds; round++) {
            using (var started = new CountdownEvent(3)) {
                var tests = new Task<bool>[3];
                for (int n = 0; n < 3; n++) tests[n] = Task.Run(() => Transfer(started));
                if (!Task.WaitAll(tests, 20000)) throw new Exception("task deadline exceeded");
                foreach (var test in tests) if (!test.Result) failures++;
            }
        }
        Console.WriteLine("DOTNET_SUMMARY transfers=" + rounds * 3 + " failures=" + failures);
        return failures;
    }

    private static bool Transfer(CountdownEvent started) {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        try {
            using (var client = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp)) {
                client.ReceiveBufferSize = 8192;
                client.ReceiveTimeout = 5000;
                client.Connect((IPEndPoint)listener.LocalEndpoint);
                using (var server = listener.AcceptSocket()) {
                    byte[] request = Encoding.ASCII.GetBytes("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n");
                    using (var input = new NetworkStream(client, false)) {
                        input.Write(request, 0, request.Length);
                        var writer = Task.Run(() => {
                            using (var output = new NetworkStream(server, false)) {
                                output.ReadTimeout = 5000;
                                ReadHeader(output);
                                started.Signal();
                                byte[] header = Encoding.ASCII.GetBytes("HTTP/1.1 200 OK\r\nContent-Length: " +
                                    Body.Length + "\r\nConnection: close\r\n\r\n");
                                output.Write(header, 0, header.Length);
                                output.Write(Body, 0, Body.Length);
                                output.Flush();
                            }
                            server.Close();
                        });
                        int received = 0;
                        try {
                            if (!started.Wait(5000)) throw new Exception("writer barrier timeout");
                            ReadHeader(input);
                            byte[] bytes = new byte[Body.Length];
                            while (received < bytes.Length) {
                                int n = input.Read(bytes, received, bytes.Length - received);
                                if (n == 0) throw new Exception("early EOF");
                                received += n;
                            }
                            for (int i = 0; i < bytes.Length; i++) if (bytes[i] != Body[i]) throw new Exception("bad byte");
                            if (input.ReadByte() != -1) throw new Exception("extra body");
                            Console.WriteLine("PASS bytes=" + received);
                            return true;
                        } catch (Exception error) {
                            Console.WriteLine("FAIL bytes=" + received + " writerDone=" + writer.IsCompleted + " " + error.Message);
                            return false;
                        } finally {
                            client.Close();
                            if (!writer.Wait(5000)) throw new Exception("writer did not exit");
                        }
                    }
                }
            }
        } finally { listener.Stop(); }
    }

    private static void ReadHeader(Stream stream) {
        string suffix = "";
        int length = 0;
        while (!suffix.EndsWith("\r\n\r\n")) {
            int value = stream.ReadByte();
            if (value < 0 || length++ >= 16384) throw new Exception("invalid header");
            suffix += (char)value;
            if (suffix.Length > 4) suffix = suffix.Substring(suffix.Length - 4);
        }
    }
}
