import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * ItemDAO - Data Access Object for Menu Items & Live Stock Management.
 * Handles inventory reads, category filtering, morning restocking, and JSON serialization.
 */
public class ItemDAO {

    /**
     * Retrieves all items from the database.
     * @return List of Item objects
     */
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT id, name, category, price, stock_quantity, image_url, created_at, updated_at FROM items ORDER BY id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Item item = mapResultSetToItem(rs);
                items.add(item);
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error fetching all items: " + e.getMessage());
        }
        return items;
    }

    /**
     * Retrieves all items formatted as a JSON array string for the Kiosk API.
     * @return JSON string array
     */
    public String getAllItemsAsJson() {
        List<Item> items = getAllItems();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(items.get(i).toJson());
            if (i < items.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Retrieves items filtered by category.
     * @param category Category name (e.g., 'Lunch', 'Drinks', 'Snacks', 'Biscuits')
     * @return List of matching Item objects
     */
    public List<Item> getItemsByCategory(String category) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT id, name, category, price, stock_quantity, image_url, created_at, updated_at FROM items WHERE category = ? ORDER BY id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, category);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapResultSetToItem(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error fetching items by category '" + category + "': " + e.getMessage());
        }
        return items;
    }

    /**
     * Retrieves a single item by its ID.
     * @param id The item ID
     * @return Item object or null if not found
     */
    public Item getItemById(int id) {
        String sql = "SELECT id, name, category, price, stock_quantity, image_url, created_at, updated_at FROM items WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error fetching item with ID " + id + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves a single item by its unique name.
     * @param name The item name
     * @return Item object or null if not found
     */
    public Item getItemByName(String name) {
        String sql = "SELECT id, name, category, price, stock_quantity, image_url, created_at, updated_at FROM items WHERE name = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error fetching item by name '" + name + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Restocks an item: Increments stock quantity in MySQL.
     * SQL: UPDATE items SET stock_quantity = stock_quantity + ? WHERE id = ?
     *
     * @param itemId The item ID
     * @param additionalQuantity Quantity to add (must be > 0)
     * @return true if restock succeeded, false otherwise
     */
    public boolean restockItem(int itemId, int additionalQuantity) {
        if (additionalQuantity <= 0) {
            System.err.println("[ItemDAO] Restock quantity must be greater than zero.");
            return false;
        }

        String sql = "UPDATE items SET stock_quantity = stock_quantity + ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, additionalQuantity);
            stmt.setInt(2, itemId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error restocking item ID " + itemId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Sets exact stock count for an item (e.g. morning inventory initialization).
     *
     * @param itemId The item ID
     * @param exactStock The exact new stock quantity (must be >= 0)
     * @return true if update succeeded, false otherwise
     */
    public boolean setExactStock(int itemId, int exactStock) {
        if (exactStock < 0) {
            System.err.println("[ItemDAO] Stock count cannot be negative.");
            return false;
        }

        String sql = "UPDATE items SET stock_quantity = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, exactStock);
            stmt.setInt(2, itemId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error setting stock for item ID " + itemId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Adds a new food item to the catalog.
     *
     * @param item Item to insert
     * @return Generated ID or -1 on failure
     */
    public int addItem(Item item) {
        String sql = "INSERT INTO items (name, category, price, stock_quantity, image_url) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, item.getName());
            stmt.setString(2, item.getCategory() != null ? item.getCategory() : "Snacks");
            stmt.setDouble(3, item.getPrice());
            stmt.setInt(4, item.getStockQuantity());
            stmt.setString(5, item.getImageUrl());

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error adding new item: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Prints a formatted real-time inventory table to the console with Indian Rupee (₹) pricing.
     */
    public void printLiveInventory() {
        List<Item> items = getAllItems();
        System.out.println("\n================================================= LIVE CANTEEN INVENTORY =================================================");
        System.out.printf("%-4s | %-42s | %-16s | %-10s | %-12s | %-12s%n", "ID", "Item Name", "Category", "Price (₹)", "Stock Count", "Status");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------");

        for (Item item : items) {
            String status;
            if (item.getStockQuantity() == 0) {
                status = "OUT OF STOCK";
            } else if (item.getStockQuantity() <= 5) {
                status = "LOW STOCK";
            } else {
                status = "AVAILABLE";
            }

            System.out.printf("%-4d | %-42s | %-16s | ₹%-9.2f | %-12d | %-12s%n",
                    item.getId(),
                    item.getName(),
                    item.getCategory(),
                    item.getPrice(),
                    item.getStockQuantity(),
                    status);
        }
        System.out.println("=========================================================================================================================\n");
    }

    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        return new Item(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getDouble("price"),
                rs.getInt("stock_quantity"),
                rs.getString("image_url"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
        );
    }
}
