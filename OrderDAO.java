import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * OrderDAO - Core Transaction & Concurrency Management for Canteen Orders.
 *
 * Implements strict ACID transaction guarantees:
 * 1. Pessimistic Row Locking (`SELECT ... FOR UPDATE`): Prevents race conditions during concurrent orders.
 * 2. Atomic Stock Decrement (`stock = stock - quantity` with conditional barrier): Prevents negative stock.
 * 3. Atomic Order & Order-Items Persistence: Rolls back all changes if any single item check fails.
 */
public class OrderDAO {

    /**
     * Data Transfer Object for an item requested within an order.
     */
    public static class OrderItemRequest {
        private final int itemId;
        private final int quantity;

        public OrderItemRequest(int itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }

        public int getItemId() {
            return itemId;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    /**
     * Result object containing the outcome of an order transaction.
     */
    public static class OrderResult {
        private final boolean success;
        private final int orderId;
        private final String message;
        private final double totalAmount;

        public OrderResult(boolean success, int orderId, String message, double totalAmount) {
            this.success = success;
            this.orderId = orderId;
            this.message = message;
            this.totalAmount = totalAmount;
        }

        public static OrderResult failure(String message) {
            return new OrderResult(false, -1, message, 0.00);
        }

        public static OrderResult success(int orderId, String message, double totalAmount) {
            return new OrderResult(true, orderId, message, totalAmount);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getOrderId() {
            return orderId;
        }

        public String getMessage() {
            return message;
        }

        public double getTotalAmount() {
            return totalAmount;
        }

        public String toJson() {
            String safeMsg = message != null ? message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") : "";
            return String.format(
                "{\"success\":%b,\"orderId\":%d,\"message\":\"%s\",\"totalAmount\":%.2f}",
                success, orderId, safeMsg, totalAmount
            );
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("[SUCCESS] Order #%d | Total: ₹%.2f | %s", orderId, totalAmount, message);
            } else {
                return String.format("[FAILED] %s", message);
            }
        }
    }

    /**
     * DTO for representing order summary when viewing recent orders.
     */
    public static class OrderSummary {
        private final int orderId;
        private final String customerName;
        private final double totalAmount;
        private final String status;
        private final Timestamp createdAt;

        public OrderSummary(int orderId, String customerName, double totalAmount, String status, Timestamp createdAt) {
            this.orderId = orderId;
            this.customerName = customerName;
            this.totalAmount = totalAmount;
            this.status = status;
            this.createdAt = createdAt;
        }

        public int getOrderId() { return orderId; }
        public String getCustomerName() { return customerName; }
        public double getTotalAmount() { return totalAmount; }
        public String getStatus() { return status; }
        public Timestamp getCreatedAt() { return createdAt; }
    }

    /**
     * Places a single-item order atomically with concurrency control.
     * Decrements the item's stock (`stock = stock - quantity`).
     */
    public OrderResult placeOrder(String customerName, int itemId, int quantity) {
        List<OrderItemRequest> items = Collections.singletonList(new OrderItemRequest(itemId, quantity));
        return placeOrder(customerName, items);
    }

    /**
     * Places a multi-item order inside an atomic database transaction.
     * Uses pessimistic locking (`FOR UPDATE`) to prevent race conditions.
     */
    public OrderResult placeOrder(String customerName, List<OrderItemRequest> requestedItems) {
        if (customerName == null || customerName.trim().isEmpty()) {
            customerName = "Kiosk Customer";
        }
        if (requestedItems == null || requestedItems.isEmpty()) {
            return OrderResult.failure("Order must contain at least one item.");
        }

        for (OrderItemRequest req : requestedItems) {
            if (req.getQuantity() <= 0) {
                return OrderResult.failure("Order quantity must be at least 1. Found invalid quantity for item ID " + req.getItemId());
            }
        }

        // Sort items by ID before locking to prevent database deadlocks in concurrent multi-item orders
        List<OrderItemRequest> sortedItems = new ArrayList<>(requestedItems);
        sortedItems.sort(Comparator.comparingInt(OrderItemRequest::getItemId));

        Connection conn = null;
        PreparedStatement lockStmt = null;
        PreparedStatement decrementStmt = null;
        PreparedStatement insertOrderStmt = null;
        PreparedStatement insertOrderItemStmt = null;

        String lockSql = "SELECT id, name, price, stock_quantity FROM items WHERE id = ? FOR UPDATE";
        String decrementSql = "UPDATE items SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?";
        String insertOrderSql = "INSERT INTO orders (customer_name, total_amount, order_status) VALUES (?, ?, 'COMPLETED')";
        String insertOrderItemSql = "INSERT INTO order_items (order_id, item_id, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?)";

        try {
            conn = DatabaseConnection.getConnection();

            // 1. Begin Atomic Transaction
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            lockStmt = conn.prepareStatement(lockSql);
            decrementStmt = conn.prepareStatement(decrementSql);

            double totalAmount = 0.00;

            // Internal helper container for locked item details
            class ResolvedItem {
                final int id;
                final String name;
                final double price;
                final int quantity;
                final int remainingStock;

                ResolvedItem(int id, String name, double price, int quantity, int remainingStock) {
                    this.id = id;
                    this.name = name;
                    this.price = price;
                    this.quantity = quantity;
                    this.remainingStock = remainingStock;
                }
            }

            List<ResolvedItem> resolvedItems = new ArrayList<>();

            // 2. Lock & Validate each item's live stock
            for (OrderItemRequest req : sortedItems) {
                lockStmt.setInt(1, req.getItemId());
                try (ResultSet rs = lockStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return OrderResult.failure("Item ID " + req.getItemId() + " does not exist in the menu.");
                    }

                    int itemId = rs.getInt("id");
                    String itemName = rs.getString("name");
                    double price = rs.getDouble("price");
                    int currentStock = rs.getInt("stock_quantity");

                    // Check if stock is completely exhausted or insufficient
                    if (currentStock <= 0) {
                        conn.rollback();
                        return OrderResult.failure(String.format(
                                "Order blocked: '%s' is completely OUT OF STOCK (Stock: 0).", itemName));
                    }

                    if (currentStock < req.getQuantity()) {
                        conn.rollback();
                        return OrderResult.failure(String.format(
                                "Order blocked: Insufficient stock for '%s'. Available: %d, Requested: %d.",
                                itemName, currentStock, req.getQuantity()));
                    }

                    int newStock = currentStock - req.getQuantity();
                    resolvedItems.add(new ResolvedItem(itemId, itemName, price, req.getQuantity(), newStock));
                    totalAmount += (price * req.getQuantity());
                }
            }

            // 3. Atomically decrement stock for all items
            for (ResolvedItem resolved : resolvedItems) {
                decrementStmt.setInt(1, resolved.quantity);
                decrementStmt.setInt(2, resolved.id);
                decrementStmt.setInt(3, resolved.quantity); // Defense-in-depth barrier

                int rowsAffected = decrementStmt.executeUpdate();
                if (rowsAffected == 0) {
                    conn.rollback();
                    return OrderResult.failure(String.format(
                            "Concurrent update collision: Stock for '%s' was modified by another transaction.",
                            resolved.name));
                }
            }

            // 4. Insert master order record
            insertOrderStmt = conn.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS);
            insertOrderStmt.setString(1, customerName.trim());
            insertOrderStmt.setDouble(2, totalAmount);
            insertOrderStmt.executeUpdate();

            int generatedOrderId = -1;
            try (ResultSet orderRs = insertOrderStmt.getGeneratedKeys()) {
                if (orderRs.next()) {
                    generatedOrderId = orderRs.getInt(1);
                }
            }

            if (generatedOrderId == -1) {
                conn.rollback();
                return OrderResult.failure("Failed to generate order ID.");
            }

            // 5. Insert order item records
            insertOrderItemStmt = conn.prepareStatement(insertOrderItemSql);
            for (ResolvedItem resolved : resolvedItems) {
                insertOrderItemStmt.setInt(1, generatedOrderId);
                insertOrderItemStmt.setInt(2, resolved.id);
                insertOrderItemStmt.setInt(3, resolved.quantity);
                insertOrderItemStmt.setDouble(4, resolved.price);
                insertOrderItemStmt.setDouble(5, resolved.price * resolved.quantity);
                insertOrderItemStmt.addBatch();
            }
            insertOrderItemStmt.executeBatch();

            // 6. Commit Transaction - All operations succeed atomically
            conn.commit();

            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < resolvedItems.size(); i++) {
                ResolvedItem ri = resolvedItems.get(i);
                summary.append(String.format("%dx %s", ri.quantity, ri.name));
                if (i < resolvedItems.size() - 1) summary.append(", ");
            }

            return OrderResult.success(generatedOrderId, "Order #" + generatedOrderId + " placed successfully (" + summary + ")!", totalAmount);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Roll back on any SQL exception
                } catch (SQLException ex) {
                    System.err.println("[OrderDAO] Rollback error: " + ex.getMessage());
                }
            }
            return OrderResult.failure("Transaction aborted due to database error: " + e.getMessage());
        } finally {
            // Restore autocommit and clean up resources
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
            DatabaseConnection.closeQuietly(lockStmt, decrementStmt, insertOrderStmt, insertOrderItemStmt, conn);
        }
    }

    /**
     * Fetches the latest orders for audit and display.
     */
    public List<OrderSummary> getRecentOrders(int limit) {
        List<OrderSummary> list = new ArrayList<>();
        String sql = "SELECT id, customer_name, total_amount, order_status, created_at FROM orders ORDER BY id DESC LIMIT ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new OrderSummary(
                            rs.getInt("id"),
                            rs.getString("customer_name"),
                            rs.getDouble("total_amount"),
                            rs.getString("order_status"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[OrderDAO] Error fetching recent orders: " + e.getMessage());
        }
        return list;
    }
}
