-- ============================================================
-- POS System - PostgreSQL Schema for Fly.io
-- Run this in your Fly.io Postgres database
-- ============================================================

-- Products
CREATE TABLE IF NOT EXISTS products (
    id             VARCHAR(20) PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(50),
    stock          INT DEFAULT 0,
    par_level      INT DEFAULT 0,
    price          DECIMAL(10,2) DEFAULT 0,
    expiry_date    VARCHAR(20),
    last_restocked VARCHAR(100) DEFAULT 'Initial Seed'
);

-- Orders
CREATE TABLE IF NOT EXISTS orders (
    order_id          SERIAL PRIMARY KEY,
    order_date        TIMESTAMP DEFAULT NOW(),
    subtotal          DECIMAL(10,2),
    discount_type     VARCHAR(50),
    discount_id_number VARCHAR(50),
    discount_amount   DECIMAL(10,2) DEFAULT 0,
    total             DECIMAL(10,2),
    payment_method    VARCHAR(50),
    account_info      VARCHAR(100),
    cash_tendered     DECIMAL(10,2),
    change_amount     DECIMAL(10,2),
    customer_id       INT
);

-- Order Items
CREATE TABLE IF NOT EXISTS order_items (
    item_id      SERIAL PRIMARY KEY,
    order_id     INT REFERENCES orders(order_id),
    product_id   VARCHAR(20),
    product_name VARCHAR(100),
    quantity     INT,
    unit_price   DECIMAL(10,2),
    subtotal     DECIMAL(10,2)
);

-- Restock Log
CREATE TABLE IF NOT EXISTS restock_log (
    log_id          SERIAL PRIMARY KEY,
    log_date        TIMESTAMP DEFAULT NOW(),
    product_id      VARCHAR(20),
    product_name    VARCHAR(100),
    quantity_added  INT,
    old_stock       INT,
    new_stock       INT,
    restocked_by    VARCHAR(50)
);

-- Time Log
CREATE TABLE IF NOT EXISTS time_log (
    log_id      SERIAL PRIMARY KEY,
    staff_role  VARCHAR(50),
    staff_name  VARCHAR(100),
    action      VARCHAR(50),
    log_time    TIMESTAMP DEFAULT NOW()
);

-- Audit Trail
CREATE TABLE IF NOT EXISTS audit_trail (
    id          SERIAL PRIMARY KEY,
    event_time  TIMESTAMP DEFAULT NOW(),
    event_type  VARCHAR(100),
    details     TEXT
);

-- Customers
CREATE TABLE IF NOT EXISTS customers (
    customer_id   SERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

-- Add customer_id FK to orders
ALTER TABLE orders ADD CONSTRAINT fk_orders_customer
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
    ON DELETE SET NULL;

-- ── Seed Data (matches DatabaseDraft1.java seedData()) ────────────────────
INSERT INTO products (id, name, category, stock, par_level, price, expiry_date) VALUES
('101','Jasmine Rice 5k','Grains',20,5,250.00,'2027-12-01'),
('102','Brown Rice 2k','Grains',15,3,180.00,'2027-10-15'),
('103','White Bread','Grains',10,2,65.00,'2026-03-31'),
('104','Spaghetti Pasta','Grains',25,5,45.00,'2028-01-20'),
('105','Oatmeal 1kg','Grains',12,4,120.00,'2027-05-12'),
('201','Fresh Milk 1L','Dairy',12,4,85.00,'2026-04-05'),
('202','Cheddar Cheese','Dairy',30,10,55.00,'2026-06-15'),
('203','Salted Butter','Dairy',15,5,95.00,'2026-09-20'),
('204','Greek Yogurt','Dairy',8,2,110.00,'2026-04-10'),
('205','Heavy Cream','Dairy',10,3,150.00,'2026-05-01'),
('301','Canned Tuna','Canned',50,10,35.50,'2028-11-30'),
('302','Corned Beef','Canned',40,8,48.00,'2029-02-14'),
('303','Sardines','Canned',60,15,22.00,'2028-08-22'),
('304','Green Peas','Canned',30,5,28.00,'2028-05-10'),
('305','Condensed Milk','Canned',25,5,62.00,'2027-12-25'),
('401','Potato Chips','Snacks',40,10,42.00,'2026-08-15'),
('402','Chocolate Bar','Snacks',50,12,35.00,'2027-01-10'),
('403','Mixed Nuts','Snacks',20,5,85.00,'2026-11-05'),
('404','Biscuits Pack','Snacks',45,10,15.00,'2026-12-20'),
('405','Gummy Bears','Snacks',30,5,25.00,'2027-03-15')
ON CONFLICT (id) DO NOTHING;
