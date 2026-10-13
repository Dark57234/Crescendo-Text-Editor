package music;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Tiny web server on 127.0.0.1 that serves player.html.
 * YouTube rejects pages opened as files (error 153), so the page needs a real http:// address.
 */
public class LocalPlayerServer {

    private HttpServer server;

    /** Starts the server on 127.0.0.1 using any free port. */
    public void start() throws IOException {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        server = HttpServer.create(address, 0);

        // When someone asks for /player, send them player.html
        server.createContext("/player", exchange -> {
            try (InputStream in = LocalPlayerServer.class.getResourceAsStream("player.html")) {
                if (in == null) {
                    byte[] msg = "player.html not found".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, msg.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(msg);
                    }
                    return;
                }
                byte[] html = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(html);
                }
            }
        });

        server.start();
    }

    /** Full address of the player, e.g. http://127.0.0.1:53817/player */
    public String getUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/player";
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
