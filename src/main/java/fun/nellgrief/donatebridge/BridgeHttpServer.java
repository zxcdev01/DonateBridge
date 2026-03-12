package fun.nellgrief.donatebridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class BridgeHttpServer {

    private final DonateBridge plugin;
    private final int port;
    private final String secret;
    private final RankGiver rankGiver;
    private HttpServer server;
    private final Logger log;

    public BridgeHttpServer(DonateBridge plugin, int port, String secret, RankGiver rankGiver) {
        this.plugin    = plugin;
        this.port      = port;
        this.secret    = secret;
        this.rankGiver = rankGiver;
        this.log       = plugin.getLogger();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/donate", new DonateHandler());
            server.createContext("/health", exchange -> {
                byte[] resp = "OK".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
            });
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            log.info("HTTP bridge запущен на порту " + port);
        } catch (IOException e) {
            log.severe("Не удалось запустить HTTP bridge: " + e.getMessage());
            log.severe("Порт " + port + " занят? Смени http-port в config.yml");
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            log.info("HTTP bridge остановлен.");
        }
    }

    private class DonateHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            String authHeader = exchange.getRequestHeaders().getFirst("X-Bridge-Secret");
            if (authHeader == null || !authHeader.equals(secret)) {
                String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
                log.warning("Отклонён запрос с неверным токеном от " + ip);
                respond(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            String nickname = extractJsonString(body, "nickname");
            String product  = extractJsonString(body, "product");
            String txId     = extractJsonString(body, "transactionId");

            if (nickname == null || nickname.isBlank() || product == null || product.isBlank()) {
                respond(exchange, 400, "{\"error\":\"Missing nickname or product\"}");
                return;
            }

            log.info("Получена оплата → игрок=" + nickname + " товар=" + product + " tx=" + txId);

            new BukkitRunnable() {
                @Override public void run() {
                    rankGiver.giveRank(nickname, product);
                }
            }.runTask(plugin);

            respond(exchange, 200, "{\"success\":true}");
        }

        private void respond(HttpExchange ex, int code, String body) throws IOException {
            ex.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }

        private String extractJsonString(String json, String key) {
            String needle = "\"" + key + "\"";
            int idx = json.indexOf(needle);
            if (idx < 0) return null;
            idx += needle.length();
            while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == ':')) idx++;
            if (idx >= json.length()) return null;
            if (json.charAt(idx) == '"') {
                int start = idx + 1;
                int end = start;
                while (end < json.length()) {
                    if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
                    end++;
                }
                return json.substring(start, end);
            }
            int start = idx;
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }
}
