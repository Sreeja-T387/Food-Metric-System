import java.awt.Desktop;
import java.net.URI;
import java.util.Scanner;

/**
 * Main - Server Entry Point for the Canteen Touch-Screen Kiosk.
 * Launches Java's embedded HttpServer on http://localhost:8080
 * and provides an interactive admin console with manual restocking.
 */
public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        printBanner();

        // 1. Verify Database Connection
        System.out.println("[Step 1/3] Testing MySQL Database Connection...");
        if (!DatabaseConnection.testConnection()) {
            System.err.println("\n[!] Error: Unable to connect to MySQL database 'canteen_db'.");
            System.err.println("Please ensure:");
            System.err.println("  1. MySQL Service is running.");
            System.err.println("  2. Database & schema were created (mysql -u root -p < schema.sql).");
            System.err.println("  3. Credentials in 'db.properties' are correct (default user: root).");
            System.out.println("\nAttempting to continue anyway, but API calls will fail until MySQL is accessible...\n");
        } else {
            System.out.println("[Step 1/3] Database connection verified successfully!\n");
        }

        // 2. Start Embedded HTTP Kiosk Server
        System.out.println("[Step 2/3] Initializing Java Embedded Web Server (com.sun.net.httpserver)...");
        KioskServer server = new KioskServer(PORT);
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("[Error] Failed to start HTTP server on port " + PORT + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }

        String kioskUrl = "http://localhost:" + PORT;

        // 3. Attempt to auto-launch the Kiosk in default browser
        System.out.println("[Step 3/3] Opening Touch-Screen Kiosk in Web Browser...");
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(kioskUrl));
                System.out.println("  -> Browser opened to: " + kioskUrl);
            } else {
                System.out.println("  -> Please open your browser and navigate to: " + kioskUrl);
            }
        } catch (Exception e) {
            System.out.println("  -> Please manually open your browser to: " + kioskUrl);
        }

        System.out.println("\n==============================================================================");
        System.out.println("  KIOSK SERVER READY!");
        System.out.println("  - Web Kiosk Interface : " + kioskUrl);
        System.out.println("  - Items API Endpoint  : " + kioskUrl + "/api/items");
        System.out.println("  - Order API Endpoint  : " + kioskUrl + "/api/order");
        System.out.println("  - Currency Display    : Indian Rupees (₹)");
        System.out.println("==============================================================================");
        System.out.println("Commands: Type 'restock' (interactive manual restocking), 'status' (view live inventory), or 'exit' (stop).");

        ItemDAO itemDAO = new ItemDAO();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n[Admin Console] > ");
            if (!scanner.hasNextLine()) break;

            String cmd = scanner.nextLine().trim().toLowerCase();
            if ("exit".equals(cmd) || "stop".equals(cmd) || "quit".equals(cmd)) {
                System.out.println("Shutting down Kiosk Server...");
                server.stop();
                break;
            } else if ("status".equals(cmd) || "inventory".equals(cmd)) {
                itemDAO.printLiveInventory();
            } else if ("restock".equals(cmd)) {
                handleInteractiveRestock(itemDAO, scanner);
            } else if (!cmd.isEmpty()) {
                System.out.println("Available commands:");
                System.out.println("  'restock' - Manually restock an item with custom quantity");
                System.out.println("  'status'  - Display real-time live inventory table");
                System.out.println("  'exit'    - Stop the kiosk server and exit");
            }
        }
    }

    /**
     * Interactive manual restocking command.
     * Prompts for Item ID and Quantity to add, executes SQL UPDATE,
     * and displays the updated inventory table immediately.
     */
    private static void handleInteractiveRestock(ItemDAO itemDAO, Scanner scanner) {
        System.out.println("\n===================== MANUAL RESTOCKING =====================");
        itemDAO.printLiveInventory();

        System.out.print("Enter Item ID to restock: ");
        String idInput = scanner.nextLine().trim();
        int itemId;
        try {
            itemId = Integer.parseInt(idInput);
        } catch (NumberFormatException e) {
            System.out.println("[!] Error: Item ID must be a valid integer.\n");
            return;
        }

        Item item = itemDAO.getItemById(itemId);
        if (item == null) {
            System.out.println("[!] Error: No item found in database with ID: " + itemId + "\n");
            return;
        }

        System.out.printf("Selected Item: %s (Current Stock: %d | Price: ₹%.2f)%n",
                item.getName(), item.getStockQuantity(), item.getPrice());

        System.out.print("Enter quantity to add: ");
        String qtyInput = scanner.nextLine().trim();
        int quantityToAdd;
        try {
            quantityToAdd = Integer.parseInt(qtyInput);
            if (quantityToAdd <= 0) {
                System.out.println("[!] Error: Quantity to add must be greater than 0.\n");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("[!] Error: Quantity must be a positive integer.\n");
            return;
        }

        // Execute SQL UPDATE items SET stock_quantity = stock_quantity + ? WHERE id = ?
        boolean success = itemDAO.restockItem(itemId, quantityToAdd);
        if (success) {
            Item updatedItem = itemDAO.getItemById(itemId);
            int newStock = updatedItem != null ? updatedItem.getStockQuantity() : item.getStockQuantity() + quantityToAdd;
            System.out.printf("\n[Admin] Successfully restocked '%s' (+%d units). New Stock: %d%n",
                    item.getName(), quantityToAdd, newStock);

            // Display updated inventory table immediately
            itemDAO.printLiveInventory();
        } else {
            System.err.println("[!] Error: Failed to execute restock update in MySQL database.\n");
        }
    }

    private static void printBanner() {
        System.out.println("==============================================================================");
        System.out.println("          CANTEEN DIGITAL ORDERING SYSTEM - TOUCH-SCREEN KIOSK               ");
        System.out.println("          Java Embedded HttpServer + MySQL ACID Concurrency Engine           ");
        System.out.println("==============================================================================");
    }
}
