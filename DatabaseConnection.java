import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DatabaseConnection - Production-grade JDBC Connection Utility
 * Manages database connectivity with configuration loading, fallback defaults,
 * and safe resource cleanup.
 */
public class DatabaseConnection {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/canteen_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root";
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    static {
        loadConfiguration();
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseConnection] Error: MySQL JDBC Driver not found in classpath.");
            System.err.println("Ensure mysql-connector-j-*.jar is placed in lib/ or the classpath.");
        }
    }

    /**
     * Loads database configuration from db.properties or falls back to sensible defaults.
     */
    private static void loadConfiguration() {
        Properties props = new Properties();
        File propFile = new File("db.properties");

        if (propFile.exists()) {
            try (InputStream input = new FileInputStream(propFile)) {
                props.load(input);
            } catch (IOException e) {
                System.err.println("[DatabaseConnection] Warning: Could not read db.properties, using defaults. " + e.getMessage());
            }
        } else {
            // Attempt to load from classpath
            try (InputStream input = DatabaseConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (input != null) {
                    props.load(input);
                }
            } catch (IOException ignored) {}
        }

        dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = props.getProperty("db.url", DEFAULT_URL);
        }

        dbUser = System.getenv("DB_USER");
        if (dbUser == null || dbUser.trim().isEmpty()) {
            dbUser = props.getProperty("db.user", DEFAULT_USER);
        }

        dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null) {
            dbPassword = props.getProperty("db.password", DEFAULT_PASSWORD);
        }
    }

    /**
     * Retrieves an active database connection.
     * @return java.sql.Connection object
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Tests the database connection.
     * @return true if connection succeeds, false otherwise
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Safely closes one or more AutoCloseable resources (Connection, Statement, ResultSet).
     * @param closeables varargs of AutoCloseable resources
     */
    public static void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // Suppress close exception in cleanup
                }
            }
        }
    }

    /**
     * Allows dynamic override of credentials (useful for interactive tests).
     */
    public static void setCredentials(String url, String user, String password) {
        dbUrl = url;
        dbUser = user;
        dbPassword = password;
    }
}
