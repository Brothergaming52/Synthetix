package org.bg52.synthetix.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bg52.synthetix.SynthetixPlugin;

import java.io.*;
import java.net.InetSocketAddress;

/**
 * Lightweight, built-in HTTP server for self-hosting the compiled Synthetix resource pack.
 */
public class ResourcePackServer implements ResourcePackHost {

    private final SynthetixPlugin plugin;
    private final String bindAddress;
    private final int port;
    private final File packFile;
    private HttpServer server;
    private volatile boolean running = false;

    public ResourcePackServer(SynthetixPlugin plugin, String bindAddress, int port, File packFile) {
        this.plugin = plugin;
        this.bindAddress = bindAddress != null && !bindAddress.trim().isEmpty() ? bindAddress.trim() : "0.0.0.0";
        this.port = port;
        this.packFile = packFile;
    }

    @Override
    public synchronized void start() {
        if (running) return;

        try {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            PackHandler handler = new PackHandler();
            server.createContext("/pack.zip", handler);
            server.createContext("/synthetix.zip", handler);
            server.setExecutor(null); // default executor
            server.start();
            running = true;
            plugin.getLogger().info("[PackServer] Embedded HTTP server successfully started on " + bindAddress + ":" + port);
        } catch (IOException e) {
            plugin.getLogger().severe("[PackServer] Failed to start embedded HTTP server on port " + port + ": " + e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            running = false;
            plugin.getLogger().info("[PackServer] Embedded HTTP server stopped.");
        }
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private class PackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!packFile.exists() || packFile.length() == 0) {
                String response = "Resource pack is currently compiling or not found.";
                byte[] bytes = response.getBytes();
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"synthetix-pack.zip\"");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, packFile.length());

            try (InputStream in = new BufferedInputStream(new FileInputStream(packFile), 65536);
                 OutputStream out = exchange.getResponseBody()) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, count);
                }
                out.flush();
            }
        }
    }
}

