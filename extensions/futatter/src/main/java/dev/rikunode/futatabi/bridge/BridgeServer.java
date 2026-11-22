package dev.rikunode.futatabi.bridge;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Read-only local HTTP bridge, scoped to the app process and loopback. */
final class BridgeServer {
    private static final String TAG = "FutatabiBridge";
    private static final int PORT = 18765;
    private final AimogeAdapter aimoge;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);

    BridgeServer(Context context) {
        aimoge = new AimogeAdapter(context);
    }

    void run() {
        try (ServerSocket server = new ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(30000);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket client = server.accept();
                    workers.execute(() -> serve(client));
                } catch (java.net.SocketTimeoutException ignored) {
                    // Periodically detect interruption without a busy poll.
                }
            }
        } catch (Exception ex) {
            // A busy port must not crash or block the host app.
            Log.w(TAG, "Local port unavailable; disabling bridge: " + ex.getClass().getSimpleName());
        } finally {
            workers.shutdownNow();
        }
    }

    private void serve(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(10000);
            BufferedReader request = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(client.getInputStream()), StandardCharsets.US_ASCII));
            String first = request.readLine();
            if (first == null || first.length() > 2048) return;
            String[] tokens = first.split(" ");
            if (tokens.length != 3 || !tokens[2].startsWith("HTTP/1.")) return;
            String method = tokens[0], path = tokens[1];
            // Ignore request headers, but consume within limits to prevent slowloris.
            int headerBytes = 0;
            for (int i = 0; i < 40; i++) {
                String line = request.readLine();
                if (line == null || line.isEmpty()) break;
                headerBytes += line.length();
                if (headerBytes > 8192) return;
            }
            OutputStream output = new BufferedOutputStream(client.getOutputStream());
            if (!"GET".equals(method)) {
                reply(output, 403, "text/plain; charset=utf-8", "Read only".getBytes(StandardCharsets.UTF_8));
                return;
            }
            BridgeResponse response;
            if (path.startsWith("/aimoge/")) {
                response = aimoge.route(path);
            } else if (path.startsWith("/animanch/") || path.startsWith("/mebuki/")) {
                response = BridgeResponse.text(503, "Adapter not enabled");
            } else {
                response = BridgeResponse.text(404, "Not found");
            }
            reply(output, response.status, response.contentType, response.bytes);
        } catch (Exception ex) {
            Log.w(TAG, "Request failed: " + ex.getClass().getSimpleName());
        }
    }

    private static void reply(OutputStream output, int status, String type, byte[] body) throws Exception {
        String phrase = status == 200 ? "OK" : status == 403 ? "Forbidden" :
                status == 404 ? "Not Found" : "Service Unavailable";
        String headers = "HTTP/1.1 " + status + " " + phrase +
                "\r\nContent-Type: " + type +
                "\r\nContent-Length: " + body.length +
                "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff" +
                "\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    static final class BridgeResponse {
        final int status;
        final String contentType;
        final byte[] bytes;
        BridgeResponse(int status, String contentType, byte[] bytes) {
            this.status = status; this.contentType = contentType; this.bytes = bytes;
        }
        static BridgeResponse text(int status, String message) {
            return new BridgeResponse(status, "text/plain; charset=utf-8",
                    message.getBytes(StandardCharsets.UTF_8));
        }
        static BridgeResponse html(byte[] bytes) {
            return new BridgeResponse(200, "text/html; charset=Shift_JIS", bytes);
        }
        static BridgeResponse image(byte[] bytes) {
            return new BridgeResponse(200, "image/jpeg", bytes);
        }
    }
}
