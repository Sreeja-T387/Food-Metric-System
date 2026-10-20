-- =============================================================================
-- Canteen Digital Ordering System - Database Schema & Kiosk Catalog
-- Currency: Indian Rupees (₹)
-- DBMS: MySQL 8.0+ / InnoDB (Row-Level Locking & ACID Compliance)
-- =============================================================================

CREATE DATABASE IF NOT EXISTS canteen_db;
USE canteen_db;

-- Drop existing tables in reverse dependency order
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS items;

-- -----------------------------------------------------------------------------
-- 1. Items Table: Stores menu items, categories, images & real-time stock
-- -----------------------------------------------------------------------------
CREATE TABLE items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL DEFAULT 'Snacks',
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0.00), -- Price in Indian Rupees (₹)
    stock_quantity INT NOT NULL CHECK (stock_quantity >= 0),
    image_url VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 2. Orders Table: Stores customer order transactions
-- -----------------------------------------------------------------------------
CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL DEFAULT 'Kiosk Customer',
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (total_amount >= 0.00), -- Total in ₹
    order_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 3. Order Items Table: Line items linking orders to purchased items
-- -----------------------------------------------------------------------------
CREATE TABLE order_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    item_id INT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10, 2) NOT NULL CHECK (unit_price >= 0.00), -- Unit price in ₹
    subtotal DECIMAL(10, 2) NOT NULL CHECK (subtotal >= 0.00),     -- Subtotal in ₹
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_item FOREIGN KEY (item_id) REFERENCES items(id)
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- Indexes for Concurrency & Fast Category Filtering
-- -----------------------------------------------------------------------------
CREATE INDEX idx_items_category ON items(category);
CREATE INDEX idx_items_name ON items(name);
CREATE INDEX idx_orders_customer ON orders(customer_name);
CREATE INDEX idx_orders_created ON orders(created_at);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_item ON order_items(item_id);

-- -----------------------------------------------------------------------------
-- Initial Sample Menu & Morning Stock Restocking Data
-- Seeded with initial stock counts (e.g. Jam Bun with 50 counts)
-- All prices listed in Indian Rupees (₹)
-- -----------------------------------------------------------------------------
INSERT INTO items (name, category, price, stock_quantity, image_url) VALUES
-- Snacks
('Jam Bun', 'Snacks', 25.00, 50, 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&auto=format&fit=crop&q=80'),
('Crispy Veg Samosa', 'Snacks', 20.00, 40, 'https://images.unsplash.com/photo-1601050690597-df0568f70950?w=400&auto=format&fit=crop&q=80'),
('French Fries Basket', 'Snacks', 50.00, 35, 'https://images.unsplash.com/photo-1576107232684-1279f3908594?w=400&auto=format&fit=crop&q=80'),
('Cheese Veg Puff', 'Snacks', 25.00, 30, 'https://images.unsplash.com/photo-1621236378699-8597fee6a1ce?w=400&auto=format&fit=crop&q=80'),

-- Lunch
('Chicken Biryani Bowl', 'Lunch', 120.00, 25, 'https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=400&auto=format&fit=crop&q=80'),
('Veggie Club Sandwich', 'Lunch', 60.00, 30, 'https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=400&auto=format&fit=crop&q=80'),
('Paneer Tikka Wrap', 'Lunch', 80.00, 20, 'https://images.unsplash.com/photo-1626700051175-6818013e1d4f?w=400&auto=format&fit=crop&q=80'),
('Margherita Pizza Slice', 'Lunch', 70.00, 15, 'https://images.unsplash.com/photo-1534308983496-4fabb1a015ee?w=400&auto=format&fit=crop&q=80'),

-- Drinks
('Fresh Orange Juice', 'Drinks', 40.00, 30, 'https://images.unsplash.com/photo-1613478223719-2ab802602423?w=400&auto=format&fit=crop&q=80'),
('Iced Cold Coffee', 'Drinks', 50.00, 40, 'https://images.unsplash.com/photo-1517701550927-30cf4ba1dba5?w=400&auto=format&fit=crop&q=80'),
('Masala Chai Tea', 'Drinks', 15.00, 60, 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=400&auto=format&fit=crop&q=80'),
('Mango Smoothie', 'Drinks', 60.00, 25, 'https://images.unsplash.com/photo-1623065422902-30a2d299bbe4?w=400&auto=format&fit=crop&q=80'),

-- Biscuits
('Choco Chip Cookies', 'Biscuits', 30.00, 45, 'https://images.unsplash.com/photo-1499636136210-6f4ee915583e?w=400&auto=format&fit=crop&q=80'),
('Butter Biscuits Pack', 'Biscuits', 20.00, 50, 'https://images.unsplash.com/photo-1558961363-fa8fdf82db35?w=400&auto=format&fit=crop&q=80'),
('Cream Oreo Pack', 'Biscuits', 25.00, 35, 'https://images.unsplash.com/photo-1563729784474-d77dbb933a9e?w=400&auto=format&fit=crop&q=80'),
('Almond Biscotti', 'Biscuits', 40.00, 20, 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&auto=format&fit=crop&q=80');
