import java.sql.Timestamp;

/**
 * Item - Model class representing a food or drink item in the canteen inventory.
 * Contains category, pricing (in ₹), stock count, and image URL for Kiosk display.
 */
public class Item {
    private int id;
    private String name;
    private String category;
    private double price;
    private int stockQuantity;
    private String imageUrl;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Item() {}

    public Item(int id, String name, String category, double price, int stockQuantity, String imageUrl) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.imageUrl = imageUrl;
    }

    public Item(int id, String name, String category, double price, int stockQuantity, String imageUrl, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Checks if the item is currently in stock.
     */
    public boolean isAvailable() {
        return this.stockQuantity > 0;
    }

    /**
     * Checks if there is enough stock to fulfill a requested quantity.
     */
    public boolean hasSufficientStock(int requestedQuantity) {
        return requestedQuantity > 0 && this.stockQuantity >= requestedQuantity;
    }

    /**
     * Converts this item into a JSON string for Kiosk REST API response.
     */
    public String toJson() {
        String safeName = escapeJson(name != null ? name : "");
        String safeCategory = escapeJson(category != null ? category : "Snacks");
        String safeImg = escapeJson(imageUrl != null ? imageUrl : "");

        return String.format(
            "{\"id\":%d,\"name\":\"%s\",\"category\":\"%s\",\"price\":%.2f,\"stock\":%d,\"imageUrl\":\"%s\"}",
            id, safeName, safeCategory, price, stockQuantity, safeImg
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        String stockStatus = stockQuantity > 0 ? String.format("%3d units", stockQuantity) : "OUT OF STOCK";
        return String.format("[ID: %2d] %-20s | %-10s | Price: ₹%-6.2f | Stock: %s",
                id, name, category, price, stockStatus);
    }
}
