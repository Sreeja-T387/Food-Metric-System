import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KioskServer - Embedded HTTP Web Server for Canteen Touch-Screen Kiosk.
 * Built with standard JDK com.sun.net.httpserver.HttpServer.
 *
 * Endpoints:
 * - GET  /              : Serves the touch-screen kiosk HTML UI (index.html)
 * - GET  /index.html    : Serves the touch-screen kiosk HTML UI
 * - GET  /api/items     : Returns live items & current stock counts in JSON
 * - POST /api/order     : Atomically processes order placement & stock decrement
 * - OPTIONS *           : Handles CORS preflight
 */
public class KioskServer {

    private final int port;
    private HttpServer server;
    private final ItemDAO itemDAO;
    private final OrderDAO orderDAO;

    public KioskServer(int port) {
        this.port = port;
        this.itemDAO = new ItemDAO();
        this.orderDAO = new OrderDAO();
    }

    /**
     * Starts the embedded HTTP server.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(16)); // Thread pool for concurrent kiosk requests

        // Register Handlers
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/items", new ItemsHandler());
        server.createContext("/api/order", new OrderHandler());

        server.start();
        System.out.println("==============================================================================");
        System.out.printf("  CANTEEN TOUCH-SCREEN KIOSK SERVER IS RUNNING ON: http://localhost:%d%n", port);
        System.out.println("  Serving interactive Web Front-End directly from Java!");
        System.out.println("==============================================================================");
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(1);
            System.out.println("[KioskServer] Server stopped.");
        }
    }

    /**
     * Serves index.html or static files.
     */
    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.equals("/index.html")) {
                serveFile(exchange, "index.html", "text/html; charset=UTF-8");
            } else {
                // If requesting other files in the directory
                File file = new File(path.substring(1));
                if (file.exists() && !file.isDirectory()) {
                    String contentType = getContentType(file.getName());
                    serveFile(exchange, file.getPath(), contentType);
                } else {
                    // Fallback to index.html for single-page kiosk app
                    serveFile(exchange, "index.html", "text/html; charset=UTF-8");
                }
            }
        }

        private void serveFile(HttpExchange exchange, String filePath, String contentType) throws IOException {
            File file = new File(filePath);
            byte[] bytes;

            if (file.exists() && !file.isDirectory()) {
                try (InputStream in = new FileInputStream(file)) {
                    bytes = in.readAllBytes();
                }
            } else {
                String error = "<h1>404 - Kiosk File Not Found</h1><p>Looking for: " + filePath + "</p>";
                bytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getContentType(String fileName) {
            if (fileName.endsWith(".html")) return "text/html; charset=UTF-8";
            if (fileName.endsWith(".css")) return "text/css; charset=UTF-8";
            if (fileName.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (fileName.endsWith(".json")) return "application/json; charset=UTF-8";
            if (fileName.endsWith(".png")) return "image/png";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
            if (fileName.endsWith(".svg")) return "image/svg+xml";
            return "text/plain; charset=UTF-8";
        }
    }

    /**
     * GET /api/items - Fetches real-time catalog and live stock from MySQL.
     */
    private class ItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                String json = itemDAO.getAllItemsAsJson();
                sendJsonResponse(exchange, 200, json);
            } catch (Exception e) {
                System.err.println("[KioskServer] Error fetching items: " + e.getMessage());
                sendJsonResponse(exchange, 500, "{\"error\":\"Failed to fetch menu items: " + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * POST /api/order - Handles atomic stock decrement & order placement.
     */
    private class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            try {
                OrderRequestPayload payload = parseOrderJson(body);

                if (payload.items.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"No items provided in order.\"}");
                    return;
                }

                OrderDAO.OrderResult result = orderDAO.placeOrder(payload.customerName, payload.items);
                int statusCode = result.isSuccess() ? 200 : 409;
                sendJsonResponse(exchange, statusCode, result.toJson());

            } catch (Exception e) {
                System.err.println("[KioskServer] Order processing error: " + e.getMessage());
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid order payload: " + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * Lightweight JSON Parser for Order Payloads without external libraries.
     */
    private static class OrderRequestPayload {
        String customerName = "Kiosk Customer";
        List<OrderDAO.OrderItemRequest> items = new ArrayList<>();
    }

    private static OrderRequestPayload parseOrderJson(String json) {
        OrderRequestPayload payload = new OrderRequestPayload();
        if (json == null || json.trim().isEmpty()) {
            return payload;
        }

        // Extract customerName
        Matcher customerMatcher = Pattern.compile("\"customerName\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (customerMatcher.find()) {
            payload.customerName = customerMatcher.group(1);
        }

        // Case 1: Array of items -> [ {"itemId": 1, "quantity": 2}, ... ]
        Matcher itemMatcher = Pattern.compile("\\{[^}]*\"itemId\"\\s*:\\s*(\\d+)[^}]*\"quantity\"\\s*:\\s*(\\d+)[^}]*\\}").matcher(json);
        while (itemMatcher.find()) {
            int itemId = Integer.parseInt(itemMatcher.group(1));
            int quantity = Integer.parseInt(itemMatcher.group(2));
            payload.items.add(new OrderDAO.OrderItemRequest(itemId, quantity));
        }

        // Case 2: Inverted order inside object -> [ {"quantity": 2, "itemId": 1}, ... ]
        if (payload.items.isEmpty()) {
            Matcher altItemMatcher = Pattern.compile("\\{[^}]*\"quantity\"\\s*:\\s*(\\d+)[^}]*\"itemId\"\\s*:\\s*(\\d+)[^}]*\\}").matcher(json);
            while (altItemMatcher.find()) {
                int quantity = Integer.parseInt(altItemMatcher.group(1));
                int itemId = Integer.parseInt(altItemMatcher.group(2));
                payload.items.add(new OrderDAO.OrderItemRequest(itemId, quantity));
            }
        }

        // Case 3: Flat single item payload -> {"itemId": 1, "quantity": 1}
        if (payload.items.isEmpty()) {
            Matcher singleItem = Pattern.compile("\"itemId\"\\s*:\\s*(\\d+)").matcher(json);
            Matcher singleQty = Pattern.compile("\"quantity\"\\s*:\\s*(\\d+)").matcher(json);
            if (singleItem.find() && singleQty.find()) {
                int id = Integer.parseInt(singleItem.group(1));
                int qty = Integer.parseInt(singleQty.group(1));
                payload.items.add(new OrderDAO.OrderItemRequest(id, qty));
            }
        }

        return payload;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
