import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.stream.Collectors;

/**
 * Lightweight REST API server for the POS HTML UI.
 * Uses only JDK built-ins (com.sun.net.httpserver) + your existing DatabaseManager.
 *
 * Endpoints:
 *   GET  /api/products          → all products from DB
 *   GET  /api/orders            → all orders from DB
 *   GET  /api/order-items?orderId=N → items for one order
 *   GET  /api/restock-log       → restock_log table
 *   GET  /api/time-log          → time_log table
 *   GET  /api/audit-trail       → audit_trail table
 *   POST /api/checkout          → save order + items + update stock
 *   POST /api/restock           → restock a product
 *   POST /api/time-log          → save login/logout
 *
 * Run: javac -cp .;mssql-jdbc.jar PosServer.java DatabaseManager.java [all other .java files]
 *       java  -cp .;mssql-jdbc.jar com.pos.PosServer
 */
// build: 1779997628
public class PosServer {

    private static final int PORT = System.getenv("PORT") != null
        ? Integer.parseInt(System.getenv("PORT")) : 8080;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/products",      new ProductsHandler());
        server.createContext("/api/orders",        new OrdersHandler());
        server.createContext("/api/order-items",   new OrderItemsHandler());
        server.createContext("/api/orders/stats",   new OrderStatsHandler());
        server.createContext("/api/restock-log",   new RestockLogHandler());
        server.createContext("/api/time-log",      new TimeLogHandler());
        server.createContext("/api/audit-trail",   new AuditTrailHandler());
        server.createContext("/api/checkout",      new CheckoutHandler());
        server.createContext("/api/restock",       new RestockHandler());
        server.createContext("/api/customer/register", new CustomerRegisterHandler());
        server.createContext("/api/customer/login",    new CustomerLoginHandler());
        server.createContext("/api/customer/orders",   new CustomerOrdersHandler());
        server.createContext("/api/staff",             new StaffHandler());
        server.createContext("/api/customer/points",   new CustomerPointsHandler());
        server.createContext("/api/customer/address",  new CustomerAddressHandler());
        server.createContext("/api/store",             new StoreHandler());
        server.createContext("/api/delivery-logs",     new DeliveryLogsHandler());
        server.createContext("/api/products/manage",   new ProductManageHandler());
        server.createContext("/api/orders/status",     new OrderStatusHandler());
        server.createContext("/",                  new StaticHandler());   // serves pos_system.html

        server.setExecutor(null);
        server.start();
        System.out.println("[POS] Server running at http://localhost:" + PORT);
        System.out.println("[POS] Open http://localhost:" + PORT + "/pos_system.html in your browser");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static String readBody(HttpExchange ex) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining());
        }
    }

    static String escape(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    static String q(String s) { return s == null ? "null" : "\"" + s + "\""; }

    // ── GET /api/products ─────────────────────────────────────────────────────
    static class ProductsHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT id, name, category, stock, par_level, price, expiry_date, last_restocked FROM products";
            try (Connection c = DatabaseManager.getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append(String.format(
                        "{\"id\":%s,\"name\":%s,\"category\":%s,\"stock\":%d," +
                        "\"parLevel\":%d,\"price\":%.2f,\"expiry\":%s,\"lastRestocked\":%s}",
                        q(rs.getString("id")), q(rs.getString("name")),
                        q(rs.getString("category")), rs.getInt("stock"),
                        rs.getInt("par_level"), rs.getDouble("price"),
                        q(rs.getString("expiry_date")), q(rs.getString("last_restocked"))));
                    first = false;
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}");
                return;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────
    static class OrdersHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT o.order_id, o.order_date, o.subtotal, o.discount_type, o.discount_amount, " +
                         "o.total, o.payment_method, o.cash_tendered, o.change_amount, o.account_info, " +
                         "o.fulfillment, o.delivery_address, o.order_status, " +
                         "c.name AS customer_name " +
                         "FROM orders o LEFT JOIN customers c ON o.customer_id = c.customer_id " +
                         "ORDER BY o.order_date DESC";
            try (Connection c = DatabaseManager.getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append(String.format(
                        "{\"orderId\":%d,\"orderDate\":%s,\"subtotal\":%.2f," +
                        "\"discountType\":%s,\"discountAmount\":%.2f,\"total\":%.2f," +
                        "\"paymentMethod\":%s,\"cashTendered\":%s,\"changeAmount\":%s," +
                        "\"accountInfo\":%s,\"customerName\":%s}",
                        rs.getInt("order_id"), q(rs.getString("order_date")),
                        rs.getDouble("subtotal"), q(rs.getString("discount_type")),
                        rs.getDouble("discount_amount"), rs.getDouble("total"),
                        q(rs.getString("payment_method")),
                        rs.getObject("cash_tendered") == null ? "null" : rs.getDouble("cash_tendered")+"",
                        rs.getObject("change_amount")  == null ? "null" : rs.getDouble("change_amount")+"",
                        q(rs.getString("account_info")),
                        q(rs.getString("customer_name"))));
                    first = false;
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}");
                return;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ── GET /api/order-items?orderId=N ────────────────────────────────────────
    static class OrderItemsHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            String query = ex.getRequestURI().getQuery();
            int orderId = -1;
            if (query != null && query.startsWith("orderId=")) {
                try { orderId = Integer.parseInt(query.split("=")[1]); } catch (Exception ignored) {}
            }
            if (orderId == -1) { sendJson(ex, 400, "{\"error\":\"Missing orderId\"}"); return; }
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT product_id, product_name, quantity, unit_price, subtotal " +
                         "FROM order_items WHERE order_id = ?";
            try (Connection c = DatabaseManager.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, orderId);
                ResultSet rs = ps.executeQuery();
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append(String.format(
                        "{\"productId\":%s,\"productName\":%s,\"quantity\":%d," +
                        "\"unitPrice\":%.2f,\"subtotal\":%.2f}",
                        q(rs.getString("product_id")), q(rs.getString("product_name")),
                        rs.getInt("quantity"), rs.getDouble("unit_price"), rs.getDouble("subtotal")));
                    first = false;
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}");
                return;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ── GET /api/restock-log ──────────────────────────────────────────────────
    static class RestockLogHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT log_date, product_id, product_name, quantity_added, " +
                         "old_stock, new_stock, restocked_by FROM restock_log ORDER BY log_date DESC";
            try (Connection c = DatabaseManager.getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append(String.format(
                        "{\"logDate\":%s,\"productId\":%s,\"productName\":%s," +
                        "\"quantityAdded\":%d,\"oldStock\":%d,\"newStock\":%d,\"restockedBy\":%s}",
                        q(rs.getString("log_date")), q(rs.getString("product_id")),
                        q(rs.getString("product_name")), rs.getInt("quantity_added"),
                        rs.getInt("old_stock"), rs.getInt("new_stock"),
                        q(rs.getString("restocked_by"))));
                    first = false;
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}");
                return;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ── GET /api/time-log ─────────────────────────────────────────────────────
    static class TimeLogHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if ("POST".equals(ex.getRequestMethod())) {
                // save time log entry from UI
                String body = readBody(ex);
                String role   = jsonVal(body, "role");
                String name   = jsonVal(body, "name");
                String action = jsonVal(body, "action");
                DatabaseManager.saveTimeLog(role, name, action);
                sendJson(ex, 200, "{\"ok\":true}");
                return;
            }
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT staff_role, staff_name, action, log_time FROM time_log ORDER BY log_time DESC";
            try (Connection c = DatabaseManager.getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append(String.format(
                        "{\"role\":%s,\"name\":%s,\"action\":%s,\"logTime\":%s}",
                        q(rs.getString("staff_role")), q(rs.getString("staff_name")),
                        q(rs.getString("action")), q(rs.getString("log_time"))));
                    first = false;
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}");
                return;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ── GET /api/audit-trail ──────────────────────────────────────────────────
    static class AuditTrailHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT event_type, details, event_time FROM audit_trail ORDER BY event_time DESC";
            try (Connection c = DatabaseManager.getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append("{")
                      .append("\"eventType\":").append(q(rs.getString("event_type"))).append(",")
                      .append("\"details\":").append(escape(rs.getString("details"))).append(",")
                      .append("\"createdAt\":").append(q(rs.getString("event_time")))
                      .append("}");
                    first = false;
                }
            } catch (SQLException e) {
                sendJson(ex, 500, escape(e.getMessage()));
                return;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ── POST /api/checkout    // ── POST /api/checkout ────────────────────────────────────────────────────
    // Body: { "subtotal":X, "discountType":"PWD"|"Senior"|null,
    //         "discountIdNumber":"...", "discountAmount":X, "total":X,
    //         "paymentMethod":"Cash"|"Card"|"GCash"|"Other",
    //         "accountInfo":"...", "cashTendered":X, "changeAmount":X,
    //         "items":[{"productId":"101","productName":"...","qty":2,"unitPrice":250.0},...] }
    static class CheckoutHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, "{\"error\":\"POST only\"}"); return; }
            String body = readBody(ex);
            try {
                double subtotal       = dbl(body, "subtotal");
                String discountType   = jsonVal(body, "discountType");
                String discIdNumber   = jsonVal(body, "discountIdNumber");
                double discountAmount = dbl(body, "discountAmount");
                double total          = dbl(body, "total");
                String paymentMethod  = jsonVal(body, "paymentMethod");
                String accountInfo    = jsonVal(body, "accountInfo");
                String fulfillment   = jsonVal(body, "fulfillment"); // "pickup" or "delivery"
                String deliveryAddr  = jsonVal(body, "deliveryAddress");
                String customerName  = jsonVal(body, "customerName");
                Double cashTendered   = hasKey(body,"cashTendered") ? dbl(body,"cashTendered") : null;
                Double changeAmount   = hasKey(body,"changeAmount")  ? dbl(body,"changeAmount")  : null;
                int customerId        = hasKey(body,"customerId") ? (int)dbl(body,"customerId") : -1;

                // Build lightweight adapter objects for DatabaseManager
                Discount discount = buildDiscount(discountType, discIdNumber);
                Payment  payment  = buildPayment(paymentMethod, accountInfo, cashTendered, changeAmount);

                // Determine order status: delivery = Pending, pickup = Completed
                String orderStatus = "delivery".equals(fulfillment) ? "Pending" : "Completed";
                int orderId = DatabaseManager.saveOrderFull(subtotal, discount, payment,
                    discountAmount, total, customerId, fulfillment, deliveryAddr, orderStatus);
                // Link order to customer if logged in
                if (orderId > 0 && customerId > 0) {
                    try (Connection c2 = DatabaseManager.getConnection();
                         PreparedStatement ps2 = c2.prepareStatement(
                             "UPDATE orders SET customer_id = ? WHERE order_id = ?")) {
                        ps2.setInt(1, customerId);
                        ps2.setInt(2, orderId);
                        ps2.executeUpdate();
                    } catch (Exception ignored) {}
                }
                if (orderId < 0) { sendJson(ex, 500, "{\"error\":\"saveOrder failed\"}"); return; }

                // Parse items array and save each + update stock
                String itemsJson = arrayBlock(body, "items");
                String[] itemTokens = itemsJson.split("\\},\\{");
                for (String tok : itemTokens) {
                    String pid      = jsonVal(tok, "productId");
                    String pname    = jsonVal(tok, "productName");
                    int    qty      = (int) dbl(tok, "qty");
                    double uprice   = dbl(tok, "unitPrice");
                    int    newStock = (int) dbl(tok, "newStock");
                    String lastR    = jsonVal(tok, "lastRestocked");

                    // Use a minimal Product adapter
                    ProductAdapter p = new ProductAdapter(pname, uprice);
                    DatabaseManager.saveOrderItem(orderId, pid, p, qty);
                    DatabaseManager.updateStock(pid, newStock, lastR);
                }

                // Award/deduct loyalty points
                if (customerId > 0) {
                    try (Connection cp = DatabaseManager.getConnection()) {
                        if ("Points".equals(paymentMethod)) {
                            int pointsUsed = (int) Math.ceil(total * 5);
                            PreparedStatement pp = cp.prepareStatement(
                                "UPDATE customers SET loyalty_points = GREATEST(0, loyalty_points - ?) WHERE customer_id = ?");
                            pp.setInt(1, pointsUsed); pp.setInt(2, customerId); pp.executeUpdate();
                        } else {
                            int pointsEarned = (int)(subtotal * 0.2);
                            PreparedStatement pp = cp.prepareStatement(
                                "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE customer_id = ?");
                            pp.setInt(1, pointsEarned); pp.setInt(2, customerId); pp.executeUpdate();
                        }
                    } catch (Exception ignored) {}
                }

                // Get updated points to return
                int updatedPoints = 0;
                if (customerId > 0) {
                    try (Connection cp = DatabaseManager.getConnection();
                         PreparedStatement pp = cp.prepareStatement("SELECT loyalty_points FROM customers WHERE customer_id = ?")) {
                        pp.setInt(1, customerId);
                        ResultSet rp = pp.executeQuery();
                        if (rp.next()) updatedPoints = rp.getInt(1);
                    } catch (Exception ignored) {}
                }

                DatabaseManager.saveAuditEvent("SALE",
                    "Order#" + orderId + " | Total: " + String.format("%.2f", total) +
                    " | Payment: " + paymentMethod);

                // Save delivery log if customer chose delivery
                if ("delivery".equals(fulfillment) && deliveryAddr != null) {
                    String itemsJsonStr = jsonVal(body, "itemsJson");
                    try (Connection cd = DatabaseManager.getConnection();
                         PreparedStatement pd = cd.prepareStatement(
                             "INSERT INTO delivery_logs (order_id,customer_name,delivery_address,payment_method,total,status,items_json) VALUES (?,?,?,?,?,?,?)")) {
                        pd.setInt(1, orderId); pd.setString(2, customerName != null ? customerName : "Guest");
                        pd.setString(3, deliveryAddr); pd.setString(4, paymentMethod);
                        pd.setDouble(5, total); pd.setString(6, "Pending"); pd.setString(7, itemsJsonStr);
                        pd.executeUpdate();
                    } catch (Exception ignored) {}
                }

                sendJson(ex, 200, "{\"ok\":true,\"orderId\":" + orderId + ",\"loyaltyPoints\":" + updatedPoints + "}");

            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}");
            }
        }
    }

    // ── POST /api/restock ─────────────────────────────────────────────────────
    // Body: { "productId":"101","productName":"...","added":10,
    //         "oldStock":5,"newStock":15,"role":"Manager","lastRestocked":"..." }
    static class RestockHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, "{\"error\":\"POST only\"}"); return; }
            String body = readBody(ex);
            try {
                String pid      = jsonVal(body, "productId");
                String pname    = jsonVal(body, "productName");
                int    added    = (int) dbl(body, "added");
                int    oldStock = (int) dbl(body, "oldStock");
                int    newStock = (int) dbl(body, "newStock");
                String role     = jsonVal(body, "role");
                String lastR    = jsonVal(body, "lastRestocked");

                DatabaseManager.saveRestockLog(pid, pname, added, oldStock, newStock, role);
                DatabaseManager.updateStock(pid, newStock, lastR);
                DatabaseManager.saveAuditEvent("RESTOCK",
                    "ID: " + pid + " | " + pname + " | +" + added +
                    " | " + oldStock + "→" + newStock + " | By: " + role);

                sendJson(ex, 200, "{\"ok\":true}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}");
            }
        }
    }


    // ── GET /api/orders/stats ──────────────────────────────────────────────────
    static class OrderStatsHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            try (Connection c = DatabaseManager.getConnection();
                 Statement st = c.createStatement()) {
                ResultSet rs1 = st.executeQuery(
                    "SELECT COUNT(*) as cnt, COALESCE(SUM(total),0) as rev, COALESCE(SUM(discount_amount),0) as disc FROM orders");
                int totalOrders = 0;
                double totalRevenue = 0;
                double totalDiscount = 0;
                if (rs1.next()) {
                    totalOrders   = rs1.getInt("cnt");
                    totalRevenue  = rs1.getDouble("rev");
                    totalDiscount = rs1.getDouble("disc");
                }
                ResultSet rs2 = st.executeQuery("SELECT COALESCE(SUM(quantity),0) as items FROM order_items");
                int totalItems = 0;
                if (rs2.next()) totalItems = rs2.getInt("items");
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"totalOrders\":").append(totalOrders).append(",");
                sb.append("\"totalRevenue\":").append(totalRevenue).append(",");
                sb.append("\"totalDiscount\":").append(totalDiscount).append(",");
                sb.append("\"totalItems\":").append(totalItems);
                sb.append("}");
                sendJson(ex, 200, sb.toString());
            } catch (SQLException e) {
                sendJson(ex, 500, escape(e.getMessage()));
            }
        }
    }

    // ── POST /api/customer/register ─────────────────────────────────────────
    // Body: { "name":"...", "email":"...", "password":"..." }
    static class CustomerRegisterHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, "{}"); return; }
            String body = readBody(ex);
            String name  = jsonVal(body, "name");
            String email = jsonVal(body, "email");
            String pass  = jsonVal(body, "password");
            if (name == null || email == null || pass == null) {
                sendJson(ex, 400, "{\"error\":\"Missing fields\"}"); return;
            }
            // Check if email already exists
            String checkSql = "SELECT customer_id FROM customers WHERE email = ?";
            try (Connection c = DatabaseManager.getConnection();
                 PreparedStatement ps = c.prepareStatement(checkSql)) {
                ps.setString(1, email);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) { sendJson(ex, 409, "{\"error\":\"Email already registered\"}"); return; }
            } catch (SQLException e) { sendJson(ex, 500, escape(e.getMessage())); return; }
            // Insert new customer
            String insertSql = "INSERT INTO customers (name, email, password_hash) VALUES (?, ?, ?) RETURNING customer_id";
            try (Connection c = DatabaseManager.getConnection();
                 PreparedStatement ps = c.prepareStatement(insertSql)) {
                ps.setString(1, name);
                ps.setString(2, email);
                ps.setString(3, pass); // plain for now; hash in production
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int id = rs.getInt(1);
                    StringBuilder sb = new StringBuilder();
                    sb.append("{").append("\"ok\":true,").append("\"customerId\":").append(id)
                      .append(",\"name\":").append(q(name)).append(",\"email\":").append(q(email))
                      .append(",\"loyaltyPoints\":0}");
                    sendJson(ex, 200, sb.toString());
                }
            } catch (SQLException e) { sendJson(ex, 500, escape(e.getMessage())); }
        }
    }

    // ── POST /api/customer/login ──────────────────────────────────────────────
    // Body: { "email":"...", "password":"..." }
    static class CustomerLoginHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, "{}"); return; }
            String body  = readBody(ex);
            String email = jsonVal(body, "email");
            String pass  = jsonVal(body, "password");
            String sql = "SELECT customer_id, name, email, loyalty_points FROM customers WHERE email = ? AND password_hash = ?";
            try (Connection c = DatabaseManager.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, pass);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{").append("\"ok\":true,")
                      .append("\"customerId\":").append(rs.getInt("customer_id")).append(",")
                      .append("\"name\":").append(q(rs.getString("name"))).append(",")
                      .append("\"email\":").append(q(rs.getString("email"))).append(",")
                      .append("\"loyaltyPoints\":").append(rs.getInt("loyalty_points")).append("}");
                    sendJson(ex, 200, sb.toString());
                } else {
                    sendJson(ex, 401, "{\"error\":\"Invalid email or password\"}");
                }
            } catch (SQLException e) { sendJson(ex, 500, escape(e.getMessage())); }
        }
    }

    // ── GET /api/customer/orders?customerId=N ────────────────────────────────
    static class CustomerOrdersHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            String query = ex.getRequestURI().getQuery();
            int customerId = -1;
            if (query != null && query.contains("customerId=")) {
                try { customerId = Integer.parseInt(query.split("customerId=")[1].split("&")[0]); } catch (Exception ignored) {}
            }
            if (customerId < 0) { sendJson(ex, 400, "{\"error\":\"Missing customerId\"}"); return; }
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT o.order_id, o.order_date, o.subtotal, o.discount_type, o.discount_amount, o.total, " +
                         "o.payment_method, o.account_info, o.cash_tendered, o.change_amount, " +
                         "o.fulfillment, o.delivery_address, o.order_status " +
                         "FROM orders o WHERE o.customer_id = ? ORDER BY o.order_date DESC";
            try (Connection c = DatabaseManager.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, customerId);
                ResultSet rs = ps.executeQuery();
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append("{")
                      .append("\"orderId\":").append(rs.getInt("order_id")).append(",")
                      .append("\"orderDate\":").append(q(rs.getString("order_date"))).append(",")
                      .append("\"subtotal\":").append(rs.getDouble("subtotal")).append(",")
                      .append("\"discountType\":").append(q(rs.getString("discount_type"))).append(",")
                      .append("\"discountAmount\":").append(rs.getDouble("discount_amount")).append(",")
                      .append("\"total\":").append(rs.getDouble("total")).append(",")
                      .append("\"paymentMethod\":").append(q(rs.getString("payment_method"))).append(",")
                      .append("\"accountInfo\":").append(q(rs.getString("account_info"))).append(",")
                      .append("\"cashTendered\":").append(rs.getObject("cash_tendered")==null?"null":rs.getDouble("cash_tendered")+"").append(",")
                      .append("\"changeAmount\":").append(rs.getObject("change_amount")==null?"null":rs.getDouble("change_amount")+"").append(",")
                      .append("\"fulfillment\":").append(q(rs.getString("fulfillment"))).append(",")
                      .append("\"deliveryAddress\":").append(q(rs.getString("delivery_address"))).append(",")
                      .append("\"orderStatus\":").append(q(rs.getString("order_status") != null ? rs.getString("order_status") : "Completed"))
                      .append("}");
                    first = false;
                }
            } catch (SQLException e) { sendJson(ex, 500, escape(e.getMessage())); return; }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ── PATCH /api/orders/status ──────────────────────────────────────────────
    static class OrderStatusHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
            String body = readBody(ex);
            String orderIdStr = jsonVal(body, "orderId");
            String status = jsonVal(body, "status");
            if (orderIdStr == null || status == null) { sendJson(ex, 400, "{\"error\":\"Missing orderId or status\"}"); return; }
            try (Connection c = DatabaseManager.getConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE orders SET order_status=? WHERE order_id=?")) {
                ps.setString(1, status); ps.setInt(2, Integer.parseInt(orderIdStr)); ps.executeUpdate();
                // Also update delivery_logs status if delivery
                try (PreparedStatement ps2 = c.prepareStatement("UPDATE delivery_logs SET status=? WHERE order_id=?")) {
                    ps2.setString(1, status); ps2.setInt(2, Integer.parseInt(orderIdStr)); ps2.executeUpdate();
                } catch (Exception ignored) {}
                sendJson(ex, 200, "{\"ok\":true}");
            } catch (SQLException e) { sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}"); }
        }
    }

    // ── GET /api/customer/points?customerId=N ────────────────────────────────
    static class CustomerPointsHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            String query = ex.getRequestURI().getQuery();
            int customerId = -1;
            if (query != null && query.contains("customerId=")) {
                try { customerId = Integer.parseInt(query.split("customerId=")[1].split("&")[0]); } catch (Exception ignored) {}
            }
            if (customerId < 0) { sendJson(ex, 400, "{\"error\":\"Missing customerId\"}"); return; }
            String sql = "SELECT loyalty_points FROM customers WHERE customer_id = ?";
            try (Connection c = DatabaseManager.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, customerId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    sendJson(ex, 200, "{\"points\":" + rs.getInt("loyalty_points") + "}");
                } else {
                    sendJson(ex, 404, "{\"error\":\"Customer not found\"}");
                }
            } catch (SQLException e) { sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}"); }
        }
    }

    // ── GET/POST/DELETE /api/staff ───────────────────────────────────────────
    static class StaffHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            String method = ex.getRequestMethod();

            if ("GET".equals(method)) {
                StringBuilder sb = new StringBuilder("[");
                String sql = "SELECT id, name, email, password_hash FROM staff ORDER BY name";
                try (Connection c = DatabaseManager.getConnection();
                     Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        sb.append(String.format("{\"id\":%d,\"name\":%s,\"email\":%s,\"password\":%s}",
                            rs.getInt("id"), q(rs.getString("name")), q(rs.getString("email")), q(rs.getString("password_hash"))));
                        first = false;
                    }
                } catch (SQLException e) { sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}"); return; }
                sb.append("]");
                sendJson(ex, 200, sb.toString());

            } else if ("POST".equals(method)) {
                String body = readBody(ex);
                String name = jsonVal(body, "name");
                String pass = jsonVal(body, "password");
                String idStr = jsonVal(body, "id");
                if (name == null || pass == null) { sendJson(ex, 400, "{\"error\":\"name and password required\"}"); return; }
                try (Connection c = DatabaseManager.getConnection()) {
                    if (idStr != null) {
                        String email = jsonVal(body, "email");
                        PreparedStatement ps = c.prepareStatement("UPDATE staff SET name=?, email=?, password_hash=? WHERE id=?");
                        ps.setString(1, name); ps.setString(2, email); ps.setString(3, pass); ps.setInt(4, Integer.parseInt(idStr));
                        ps.executeUpdate();
                    } else {
                        String email = jsonVal(body, "email");
                        PreparedStatement ps = c.prepareStatement("INSERT INTO staff (name, email, password_hash) VALUES (?,?,?)");
                        ps.setString(1, name); ps.setString(2, email); ps.setString(3, pass);
                        ps.executeUpdate();
                    }
                } catch (SQLException e) { sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}"); return; }
                sendJson(ex, 200, "{\"ok\":true}");

            } else if ("DELETE".equals(method)) {
                String query = ex.getRequestURI().getQuery();
                if (query == null || !query.startsWith("id=")) { sendJson(ex, 400, "{\"error\":\"Missing id\"}"); return; }
                int id = Integer.parseInt(query.split("=")[1]);
                try (Connection c = DatabaseManager.getConnection();
                     PreparedStatement ps = c.prepareStatement("DELETE FROM staff WHERE id=?")) {
                    ps.setInt(1, id); ps.executeUpdate();
                } catch (SQLException e) { sendJson(ex, 500, "{\"error\":" + escape(e.getMessage()) + "}"); return; }
                sendJson(ex, 200, "{\"ok\":true}");
            } else {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            }
        }
    }

    // ── GET/PUT /api/customer/address ────────────────────────────────────────
    static class CustomerAddressHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if ("GET".equals(ex.getRequestMethod())) {
                String q = ex.getRequestURI().getQuery();
                int cid = -1;
                if (q != null && q.contains("customerId=")) try { cid = Integer.parseInt(q.split("customerId=")[1].split("&")[0]); } catch (Exception e2) {}
                if (cid < 0) { sendJson(ex, 400, "{\"error\":\"Missing customerId\"}"); return; }
                try (Connection c = DatabaseManager.getConnection();
                     PreparedStatement ps = c.prepareStatement("SELECT address FROM customers WHERE customer_id=?")) {
                    ps.setInt(1, cid); ResultSet rs = ps.executeQuery();
                    if (rs.next()) sendJson(ex, 200, "{\"address\":" + q(rs.getString("address")) + "}");
                    else sendJson(ex, 404, "{\"address\":null}");
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); }
            } else if ("POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                String idStr = jsonVal(body, "customerId");
                String address = jsonVal(body, "address");
                if (idStr == null) { sendJson(ex, 400, "{\"error\":\"Missing customerId\"}"); return; }
                try (Connection c = DatabaseManager.getConnection();
                     PreparedStatement ps = c.prepareStatement("UPDATE customers SET address=? WHERE customer_id=?")) {
                    ps.setString(1, address); ps.setInt(2, Integer.parseInt(idStr)); ps.executeUpdate();
                    sendJson(ex, 200, "{\"ok\":true}");
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); }
            } else { sendJson(ex, 405, "{\"error\":\"Method not allowed\"}"); }
        }
    }

    // ── GET/POST /api/store ───────────────────────────────────────────────────
    static class StoreHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if ("GET".equals(ex.getRequestMethod())) {
                try (Connection c = DatabaseManager.getConnection();
                     Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT name, address, phone FROM store_info WHERE id=1")) {
                    if (rs.next()) sendJson(ex, 200, String.format("{\"name\":%s,\"address\":%s,\"phone\":%s}",
                        q(rs.getString("name")), q(rs.getString("address")), q(rs.getString("phone"))));
                    else sendJson(ex, 200, "{\"name\":\"Tyrone\'s POS\",\"address\":null,\"phone\":null}");
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); }
            } else if ("POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                String name = jsonVal(body, "name"); String address = jsonVal(body, "address"); String phone = jsonVal(body, "phone");
                try (Connection c = DatabaseManager.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO store_info (id,name,address,phone) VALUES (1,?,?,?) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name,address=EXCLUDED.address,phone=EXCLUDED.phone")) {
                    ps.setString(1, name); ps.setString(2, address); ps.setString(3, phone); ps.executeUpdate();
                    sendJson(ex, 200, "{\"ok\":true}");
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); }
            } else { sendJson(ex, 405, "{\"error\":\"Method not allowed\"}"); }
        }
    }

    // ── GET/POST /api/delivery-logs ───────────────────────────────────────────
    static class DeliveryLogsHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            if ("GET".equals(ex.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                String sql = "SELECT d.id, d.order_id, d.customer_name, d.delivery_address, d.payment_method, d.total, d.status, d.created_at, d.items_json FROM delivery_logs d ORDER BY d.created_at DESC";
                try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        sb.append(String.format("{\"id\":%d,\"orderId\":%d,\"customerName\":%s,\"deliveryAddress\":%s,\"paymentMethod\":%s,\"total\":%.2f,\"status\":%s,\"createdAt\":%s,\"itemsJson\":%s}",
                            rs.getInt("id"), rs.getInt("order_id"), q(rs.getString("customer_name")),
                            q(rs.getString("delivery_address")), q(rs.getString("payment_method")),
                            rs.getDouble("total"), q(rs.getString("status")), q(rs.getString("created_at")),
                            rs.getString("items_json") != null ? rs.getString("items_json") : "[]"));
                        first = false;
                    }
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); return; }
                sb.append("]"); sendJson(ex, 200, sb.toString());
            } else if ("POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                String orderId = jsonVal(body, "orderId"); String customerName = jsonVal(body, "customerName");
                String deliveryAddress = jsonVal(body, "deliveryAddress"); String paymentMethod = jsonVal(body, "paymentMethod");
                String total = jsonVal(body, "total"); String itemsJson = jsonVal(body, "itemsJson");
                try (Connection c = DatabaseManager.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO delivery_logs (order_id,customer_name,delivery_address,payment_method,total,status,items_json) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setInt(1, orderId != null ? Integer.parseInt(orderId) : 0);
                    ps.setString(2, customerName); ps.setString(3, deliveryAddress);
                    ps.setString(4, paymentMethod); ps.setDouble(5, total != null ? Double.parseDouble(total) : 0);
                    ps.setString(6, "Pending"); ps.setString(7, itemsJson); ps.executeUpdate();
                    sendJson(ex, 200, "{\"ok\":true}");
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); }
            } else { sendJson(ex, 405, "{\"error\":\"Method not allowed\"}"); }
        }
    }

    // ── GET/POST/DELETE /api/products/manage ─────────────────────────────────
    static class ProductManageHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 200, "{}"); return; }
            String method = ex.getRequestMethod();
            if ("POST".equals(method)) {
                String body = readBody(ex);
                String id = jsonVal(body, "id"); String name = jsonVal(body, "name");
                String category = jsonVal(body, "category"); String price = jsonVal(body, "price");
                String stock = jsonVal(body, "stock"); String parLevel = jsonVal(body, "parLevel");
                String expiryDate = jsonVal(body, "expiryDate");
                if (id == null || name == null) { sendJson(ex, 400, "{\"error\":\"id and name required\"}"); return; }
                String sql = "INSERT INTO products (id,name,category,stock,par_level,price,expiry_date,last_restocked) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name,category=EXCLUDED.category,price=EXCLUDED.price,par_level=EXCLUDED.par_level,expiry_date=EXCLUDED.expiry_date";
                try (Connection c = DatabaseManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, id); ps.setString(2, name); ps.setString(3, category != null ? category : "General");
                    ps.setInt(4, stock != null ? Integer.parseInt(stock) : 0);
                    ps.setInt(5, parLevel != null ? Integer.parseInt(parLevel) : 5);
                    ps.setDouble(6, price != null ? Double.parseDouble(price) : 0);
                    ps.setString(7, expiryDate); ps.setString(8, "Manual Entry");
                    ps.executeUpdate(); sendJson(ex, 200, "{\"ok\":true}");
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); }
            } else if ("DELETE".equals(method)) {
                String q = ex.getRequestURI().getQuery();
                if (q == null || !q.contains("id=")) { sendJson(ex, 400, "{\"error\":\"Missing id\"}"); return; }
                String id = q.split("id=")[1].split("&")[0];
                try (Connection c = DatabaseManager.getConnection();
                     PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE id=?")) {
                    ps.setString(1, id); ps.executeUpdate(); sendJson(ex, 200, "{\"ok\":true}");
                } catch (SQLException e2) { sendJson(ex, 500, "{\"error\":" + escape(e2.getMessage()) + "}"); }
            } else { sendJson(ex, 405, "{\"error\":\"Method not allowed\"}"); }
        }
    }

    // ── Static file server (serves pos_system.html from working dir) ──────────
    static class StaticHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if ("/".equals(path)) path = "/pos_system.html";
            File f = new File("." + path);
            if (!f.exists() || f.isDirectory()) {
                String msg = "404 Not Found: " + path;
                ex.sendResponseHeaders(404, msg.length());
                ex.getResponseBody().write(msg.getBytes());
                ex.getResponseBody().close();
                return;
            }
            String ct = path.endsWith(".html") ? "text/html" :
                        path.endsWith(".js")   ? "application/javascript" : "text/plain";
            ex.getResponseHeaders().set("Content-Type", ct);
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    // ── JSON mini-parser helpers ──────────────────────────────────────────────

    static String jsonVal(String json, String key) {
        String pattern = "\"" + key + "\"";
        int i = json.indexOf(pattern);
        if (i < 0) return null;
        i += pattern.length();
        while (i < json.length() && (json.charAt(i) == ':' || json.charAt(i) == ' ')) i++;
        if (i >= json.length()) return null;
        if (json.charAt(i) == '"') {
            int end = json.indexOf('"', i + 1);
            return end < 0 ? null : json.substring(i + 1, end);
        }
        if (json.startsWith("null", i)) return null;
        int end = i;
        while (end < json.length() && ",}]\n".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(i, end).trim();
    }

    static double dbl(String json, String key) {
        String v = jsonVal(json, key);
        return v == null ? 0 : Double.parseDouble(v);
    }

    static boolean hasKey(String json, String key) {
        return json.contains("\"" + key + "\"");
    }

    static String arrayBlock(String json, String key) {
        int start = json.indexOf("\"" + key + "\"");
        if (start < 0) return "[]";
        start = json.indexOf('[', start);
        int depth = 0, end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) break; }
            end++;
        }
        String block = json.substring(start + 1, end).trim();
        return block.isEmpty() ? "" : block.replaceAll("^\\[|\\]$", "");
    }

    // ── Discount adapters (mirror your Java classes) ──────────────────────────

    static Discount buildDiscount(String type, String idNumber) {
        if ("PWD".equals(type)) {
            PWDDiscount d = new PWDDiscount(); d.idNumber = idNumber; return d;
        } else if ("Senior".equals(type)) {
            SeniorDiscount d = new SeniorDiscount(); d.idNumber = idNumber; return d;
        }
        return null;
    }

    static Payment buildPayment(String method, String accountInfo,
                                Double cashTendered, Double change) {
        if ("Cash".equals(method)) {
            CashPayment p = new CashPayment();
            p.amountTendered = cashTendered != null ? cashTendered : 0;
            p.change         = change       != null ? change       : 0;
            p.accountInfo    = "N/A";
            return p;
        } else if ("Card".equals(method)) {
            CardPayment p = new CardPayment(); p.accountInfo = accountInfo; return p;
        } else if ("GCash".equals(method)) {
            GCashPayment p = new GCashPayment(); p.accountInfo = accountInfo; return p;
        } else {
            // Generic digital fallback
            GCashPayment p = new GCashPayment();
            p.accountInfo = accountInfo != null ? accountInfo : "N/A"; return p;
        }
    }

    // ── ProductAdapter — calls the real Product constructor ───
    // Constructor signature from DatabaseDraft1: Product(name, category, stock, parLevel, price, expiry)
    static class ProductAdapter extends Product {
        ProductAdapter(String name, double price) {
            super(name, "", 0, 0, price, "");
        }
    }
}
