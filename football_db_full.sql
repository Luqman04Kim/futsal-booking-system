-- Create Database (if not already created)
CREATE DATABASE IF NOT EXISTS football_db;
USE football_db;

-- Drop tables if they exist to start fresh (Order matters for FK constraints)
DROP TABLE IF EXISTS problem_reports;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS price_matrix;
DROP TABLE IF EXISTS pricing_rules; -- If you used the rule-based approach
DROP TABLE IF EXISTS fields;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS branches;

-- 1. Branches Table
CREATE TABLE branches (
    branch_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    location VARCHAR(255),
    contact_number VARCHAR(255),
    image_url VARCHAR(255),
    latitude DOUBLE,
    longitude DOUBLE,
    manager_id BIGINT -- Will be linked later
) ENGINE=InnoDB;

-- 2. Users Table (Staff, Admin, Customer)
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255),
    role VARCHAR(20), -- 'ADMIN', 'STAFF', 'CUSTOMER'
    image_url VARCHAR(255),
    position VARCHAR(255),
    phone_number VARCHAR(255),
    age INT,
    branch_id BIGINT,
    
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- update branch manager constraint (circular dependency resolution)
ALTER TABLE branches
ADD CONSTRAINT FK_branch_manager
FOREIGN KEY (manager_id) REFERENCES users(user_id) ON DELETE SET NULL;

-- 3. Fields Table
CREATE TABLE fields (
    field_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(255), -- Grass, Turf, etc.
    status VARCHAR(50), -- Available, Booked, Maintenance
    size VARCHAR(20), -- 5v5, 7v7
    image_url VARCHAR(255),
    
    -- Legacy/Base price columns (kept for compatibility)
    price_per_hour DOUBLE DEFAULT 0.0,
    weekend_price DOUBLE DEFAULT 0.0,
    
    branch_id BIGINT,
    supervisor_id BIGINT,
    
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id) ON DELETE CASCADE,
    FOREIGN KEY (supervisor_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 4. Price Matrix Table (Specific Pricing per Slot)
CREATE TABLE price_matrix (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    day_of_week VARCHAR(20), -- MONDAY, TUESDAY...
    start_time TIME,
    price DOUBLE,
    field_id BIGINT,
    
    FOREIGN KEY (field_id) REFERENCES fields(field_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. Bookings Table
CREATE TABLE bookings (
    booking_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date DATE,
    start_time TIME,
    end_time TIME,
    status VARCHAR(50), -- PENDING, APPROVED, REJECTED
    price DOUBLE, -- The final price calculated at booking time
    
    -- Optional Services
    hire_photographer BOOLEAN DEFAULT FALSE,
    rent_jersey BOOLEAN DEFAULT FALSE,
    
    user_id BIGINT,
    field_id BIGINT,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (field_id) REFERENCES fields(field_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. Tasks Table (Staff Assignments)
CREATE TABLE tasks (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(255),
    status VARCHAR(50), -- PENDING, COMPLETED
    due_date DATE,
    proof_image_url VARCHAR(255),
    task_image_url VARCHAR(255),
    user_id BIGINT,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 7. Problem Reports Table
CREATE TABLE problem_reports (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(255),
    image_url VARCHAR(255),
    status VARCHAR(50), -- REPORTED, MAINTENANCE, FIXED
    timestamp DATETIME,
    notes VARCHAR(255),
    user_id BIGINT,
    branch_id BIGINT,
    field_id BIGINT,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id) ON DELETE SET NULL,
    FOREIGN KEY (field_id) REFERENCES fields(field_id) ON DELETE SET NULL
) ENGINE=InnoDB;
ALTER TABLE fields DROP INDEX UK_r55s3oeu0mfd7fjgfvhvd488s;
-- =======================================================
-- SEED DATA (Optional - To get you started)
-- =======================================================

-- Default Admin
INSERT INTO users (username, email, password, role, image_url, position, phone_number, age) 
VALUES ('Super Admin', 'admin@field.com', 'admin123', 'ADMIN', '/img/undraw_profile.svg', 'CEO', '012-3456789', 40);