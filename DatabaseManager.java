import java.sql.*;

public class DatabaseManager {

    // ── Fly.io PostgreSQL connection ──────────────────────────────────────────
    // These values come from environment variables set on Fly.io
    // Locally you can set them too, or override with a .env approach
    private static final String DB_URL  = System.getenv("DATABASE_URL") != null
        ? System.getenv("DATABASE_URL")
        : "jdbc:postgresql://localhost:5432/pos_system";
    private static final String DB_USER = System.getenv("DB_USER")  != null
        ? System.getenv("DB_USER")  : "postgres";
    private static final String DB_PASS = System.getenv("DB_PASS")  != null
        ? System.getenv("DB_PASS")  : "postgres";

    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }
        return connection;
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.out.println("[DB] Could not close connection: " + e.getMessage());
        }
    }

    // ── PRODUCTS ──────────────────────────────────────────────────────────────
    // PostgreSQL: IF EXISTS → INSERT ... ON CONFLICT DO UPDATE
    public static void saveProduct(String id, Product p) {
        String sql = """
            INSERT INTO products (id, name, category, stock, par_level, price, expiry_date, last_restocked)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
              SET stock = EXCLUDED.stock,
                  last_restocked = EXCLUDED.last_restocked
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, p.name);
            ps.setString(3, p.category);
            ps.setInt(4, p.stock);
            ps.setInt(5, p.parLevel);
            ps.setDouble(6, p.price);
            ps.setString(7, p.expiryDate);
            ps.setString(8, p.lastRestocked);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveProduct error: " + e.getMessage());
        }
    }

    public static void updateStock(String productId, int newStock, String lastRestocked) {
        String sql = "UPDATE products SET stock = ?, last_restocked = ? WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, lastRestocked);
            ps.setString(3, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] updateStock error: " + e.getMessage());
        }
    }

    // ── ORDERS ────────────────────────────────────────────────────────────────
    // PostgreSQL: OUTPUT INSERTED.order_id → RETURNING order_id
    public static int saveOrder(double subtotal, Discount discount, Payment payment,
                                double discountAmount, double total) {
        return saveOrderFull(subtotal, discount, payment, discountAmount, total, -1, null, null, "Completed");
    }

    public static int saveOrderFull(double subtotal, Discount discount, Payment payment,
                                    double discountAmount, double total,
                                    int customerId, String fulfillment, String deliveryAddress, String orderStatus) {
        String sql = """
            INSERT INTO orders
              (subtotal, discount_type, discount_id_number, discount_amount,
               total, payment_method, account_info, cash_tendered, change_amount,
               customer_id, fulfillment, delivery_address, order_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING order_id
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setDouble(1, subtotal);
            ps.setString(2, discount != null ? discount.getType() : null);
            ps.setString(3, discount != null ? discount.idNumber : null);
            ps.setDouble(4, discountAmount);
            ps.setDouble(5, total);
            ps.setString(6, payment.getMethod());
            ps.setString(7, payment instanceof CashPayment ? null : payment.accountInfo);
            ps.setObject(8, payment instanceof CashPayment ? ((CashPayment) payment).amountTendered : null);
            ps.setObject(9, payment instanceof CashPayment ? ((CashPayment) payment).change : null);
            ps.setObject(10, customerId > 0 ? customerId : null);
            ps.setString(11, fulfillment);
            ps.setString(12, deliveryAddress);
            ps.setString(13, orderStatus != null ? orderStatus : "Completed");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("[DB] saveOrderFull error: " + e.getMessage());
        }
        return -1;
    }

    public static void saveOrderItem(int orderId, String productId, Product p, int qty) {
        String sql = """
            INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, subtotal)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, productId);
            ps.setString(3, p.name);
            ps.setInt(4, qty);
            ps.setDouble(5, p.price);
            ps.setDouble(6, p.price * qty);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveOrderItem error: " + e.getMessage());
        }
    }

    // ── RESTOCK LOG ───────────────────────────────────────────────────────────
    public static void saveRestockLog(String productId, String productName,
                                      int added, int oldStock, int newStock, String role) {
        String sql = """
            INSERT INTO restock_log (product_id, product_name, quantity_added, old_stock, new_stock, restocked_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, productId);
            ps.setString(2, productName);
            ps.setInt(3, added);
            ps.setInt(4, oldStock);
            ps.setInt(5, newStock);
            ps.setString(6, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveRestockLog error: " + e.getMessage());
        }
    }

    // ── TIME LOG ──────────────────────────────────────────────────────────────
    public static void saveTimeLog(String role, String name, String action) {
        String sql = "INSERT INTO time_log (staff_role, staff_name, action, log_time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, name);
            ps.setString(3, action);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveTimeLog error: " + e.getMessage());
        }
    }

    // ── AUDIT TRAIL ───────────────────────────────────────────────────────────
    public static void saveAuditEvent(String eventType, String details) {
        String sql = "INSERT INTO audit_trail (event_type, details) VALUES (?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveAuditEvent error: " + e.getMessage());
        }
    }
}
