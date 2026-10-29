-- =============================================================================
-- Canteen Digital Ordering System - Database Schema & Complete Campus Menu
-- Currency: Indian Rupees (₹)
-- All items listed individually with accurate, distinct photography
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
    name VARCHAR(150) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL DEFAULT 'Cooked Snacks',
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0.00), -- Price in Indian Rupees (₹)
    stock_quantity INT NOT NULL CHECK (stock_quantity >= 0),
    image_url VARCHAR(500) DEFAULT NULL,
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
-- Complete Campus Canteen Menu (65 Individual Items)
-- Every item has its own distinct, accurate photo URL & ₹ pricing
-- -----------------------------------------------------------------------------
INSERT INTO items (name, category, price, stock_quantity, image_url) VALUES

-- =============================================================================
-- 1. SOUTH & NORTH INDIAN LUNCH (12:00 PM - 3:00 PM)
-- =============================================================================
('Meals', 'Lunch', 50.00, 40, 
 'https://media.istockphoto.com/id/1157123113/photo/nepali-thali-set.jpg?s=1024x1024&w=is&k=20&c=rTLSyFoXFX8L4WOBV1yNbGk7vg354wnJ_f2dyxY_eXw='),

('Vegetable Biriyani', 'Lunch', 60.00, 35, 
 'https://media.istockphoto.com/id/980135772/photo/indian-vegetable-pulav-or-biryani-made-using-basmati-rice-served-in-terracotta-bowl-selective.jpg?s=1024x1024&w=is&k=20&c=oP7U90Br4F44hSp2vCWeC4lhz2NQ4qd4raErLZWKxXA='),

('Sambar Rice ', 'Lunch', 35.00, 35, 
 'https://media.istockphoto.com/id/1255854119/photo/sambhar-rice.jpg?s=1024x1024&w=is&k=20&c=ZeCoLFZtqOfNsfoOTQNaxRmPhiGl9bePJ1rkGWhohx0='),

('Parotta', 'Lunch', 60.00, 25, 
 'https://media.istockphoto.com/id/2286733000/photo/kerala-porotta-known-as-paratha-or-parotta.jpg?s=1024x1024&w=is&k=20&c=_Uu0FR8hbVclvxayYSLICKkXqRfOcsHwdGJzK8zJMvk='),

('Curd Rice (with Mustard & Pickle)', 'Lunch', 30.00, 40, 
 'https://media.istockphoto.com/id/1314941198/photo/curd-rice-with-pomegranate-cilantro-ginger-on-a-black-background.jpg?s=1024x1024&w=is&k=20&c=gfMYoLghExBzM39m-qyaNH9MR4X1Skfsx4gYmlkvDbA='),

('Chola Puri', 'Lunch', 45.00, 35, 
 'https://media.istockphoto.com/id/1321061416/photo/chickpeas-masala-and-bhatura-or-puri-garnished-with-fresh-green-coriander-and-ingredients.jpg?s=1024x1024&w=is&k=20&c=9KRKSmE4KLd4bwyuswYgpmVoprF-8L-kfZtK0rDscC8='),

('Egg Kothu Parotta', 'Lunch', 60.00, 25, 
 'https://www.licious.in/blog/wp-content/uploads/2022/12/Shutterstock_1432403939.jpg'),

-- =============================================================================
-- 2. COOKED SNACKS (All Day - Individual Items)
-- =============================================================================
('Samosa', 'Cooked Snacks', 10.00, 60, 
 'https://media.istockphoto.com/id/502663193/photo/punjabi-samosa-32.jpg?s=1024x1024&w=is&k=20&c=i3NOW5EAkmPI1vYNherMmVQzWX_71hsj16JDEdmTF00='),

('Cream Bun', 'Cooked Snacks', 10.00, 50, 
 'https://www.christinascucina.com/wp-content/uploads/2012/11/IMG_8916.jpg'),

('Jam Bun', 'Cooked Snacks', 10.00, 45, 
 'https://tse3.mm.bing.net/th/id/OIP.Q_EEscwjBwWZQgGOsr6DMQHaJQ?r=0&w=960&h=1200&rs=1&pid=ImgDetMain&o=7&rm=3'),

('Veg Puff', 'Cooked Snacks', 20.00, 45, 
 'https://floursandfrostings.com/wp-content/uploads/2018/03/IMG_20180306_084523_451.jpg'),

('Egg Puff', 'Cooked Snacks', 25.00, 35, 
 'https://bf1af2.akinoncloudcdn.com/products/2025/04/28/149959/c98d2ace-8c0d-47c2-a249-307a8bd07761_size3840_cropCenter.jpg'),

('Bread Omelette ', 'Cooked Snacks', 35.00, 35, 
 'https://www.awesomecuisine.com/wp-content/uploads/2020/03/bread-omelette.jpg'),

-- =============================================================================
-- 3. PACKAGED SNACKS (MRP - Individual Items)
-- =============================================================================
('Lays ', 'Packaged Snacks', 30.00, 60, 
 'https://www.allrecipes.com/thmb/LAfNFUjS5nZBIjOB53D1gS_GB3k=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/ar-lays-taste-test-group-4x3-67f87a8f3c4c4710b50ab762b7f90257.jpg'),

('Kurkure', 'Packaged Snacks', 10.00, 60, 
 'https://m.media-amazon.com/images/I/81NT2IUh9kL._SL1024_.jpg'),

('Hide and Seek', 'Packaged Snacks', 30.00, 50, 
 'https://5.imimg.com/data5/SELLER/Default/2021/2/PI/YV/ZA/14475970/hide-n-seek-biscuit-1000x1000.jpg'),

('Jim Jam', 'Packaged Snacks', 10.00, 40, 
 'https://www.kesargrocery.com/images/P/britannia%20jim%20jam.jpeg'),

('Diary Milk', 'Packaged Snacks', 40.00, 60, 
 'https://m.media-amazon.com/images/I/718ecxjECuL._SL1500_.jpg'),

('Milky Bar', 'Packaged Snacks', 10.00, 50, 
 'https://tse4.mm.bing.net/th/id/OIP.rsqx-QSzYsQi3gO7TDHsjwHaHa?r=0&rs=1&pid=ImgDetMain&o=7&rm=3'),

('Sunfeast Marie Gold', 'Packaged Snacks', 10.00, 50, 
 'https://m.media-amazon.com/images/I/815U83opWmL._SL1500_.jpg'),

('Snickers', 'Packaged Snacks', 20.00, 40, 
 'https://tse3.mm.bing.net/th/id/OIP.aJ0ln-dZpMEftm4EMOLB_AHaHa?r=0&rs=1&pid=ImgDetMain&o=7&rm=3'),

('Bourbon', 'Packaged Snacks', 10.00, 60, 
 'https://5.imimg.com/data5/SELLER/Default/2025/3/492455667/TO/IL/PW/241833818/britannia-bourbon-biscuit-1000x1000.jpg'),

-- =============================================================================
-- 4. COLD DRINKS & JUICES (Chilled - Individual Items)
-- =============================================================================
('Sprite ', 'Cold Drinks', 10.00, 45, 
 'https://tse2.mm.bing.net/th/id/OIP.CQ3k5psbBKLfBEzyRJ074wHaHa?r=0&rs=1&pid=ImgDetMain&o=7&rm=3'),

('Appy Fizz', 'Cold Drinks', 10.00, 40, 
 'https://5.imimg.com/data5/SELLER/Default/2020/12/RN/UY/SD/91097980/appy-fizz-160ml-bottle-mrp-10-500x500.jpg'),
 
('Lassi (Chilled)', 'Cold Drinks', 25.00, 35, 
 'https://tse2.mm.bing.net/th/id/OIP.FOzM3rawwOUqIk7-JHaVPQHaHa?r=0&rs=1&pid=ImgDetMain&o=7&rm=3'),

('Frooti', 'Cold Drinks', 10.00, 35, 
 'https://5.imimg.com/data5/OP/GB/PC/SELLER-102894273/frooti-1000x1000.jpg'),

('Vibro Panner Soda', 'Cold Drinks', 30.00, 30, 
 'https://cdn.grofers.com/da/cms-assets/cms/product/880e6b7d-173b-4b70-bf30-6ae7cff6245c.jpg'),

('Diet Coke', 'Cold Drinks', 50.00, 35, 
 'https://wallpaperaccess.com/full/6755311.jpg'),

('Cold Coffee ', 'Cold Drinks', 40.00, 40, 
 'https://5.imimg.com/data5/SELLER/Default/2025/4/501906423/BW/LE/LD/87692996/assorted-cold-coffee-pack-500x500.png');
