-- Disable foreign key checks to allow circular references during creation
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------
-- Database `football_db`
-- -----------------------------------------------------
CREATE DATABASE IF NOT EXISTS `football_db`;
USE `football_db`;

-- -----------------------------------------------------
-- Table `users`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(255) NULL,
  `email` VARCHAR(255) NULL UNIQUE,
  `password` VARCHAR(255) NULL,
  `role` VARCHAR(50) NULL,
  `image_url` VARCHAR(255) NULL,
  `position` VARCHAR(100) NULL,
  `phone_number` VARCHAR(20) NULL,
  `staff_id` VARCHAR(50) NULL, -- NEW: Staff ID
  `age` INT NULL,
  `visit_count` INT DEFAULT 0,
  `reset_token` VARCHAR(255) NULL,
  `reset_token_expiry` DATETIME NULL,
  `is_vip` BOOLEAN DEFAULT FALSE, -- NEW
  `vip_expiry_date` DATETIME NULL, -- NEW
  `branch_id` BIGINT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_users_branch`
    FOREIGN KEY (`branch_id`)
    REFERENCES `branches` (`branch_id`)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------
-- Table `branches`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `branches`;
CREATE TABLE `branches` (
  `branch_id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) NULL,
  `location` VARCHAR(255) NULL,
  `contact_number` VARCHAR(50) NULL,
  `image_url` VARCHAR(255) NULL,
  `latitude` DOUBLE NULL,
  `longitude` DOUBLE NULL,
  `manager_id` BIGINT NULL,
  PRIMARY KEY (`branch_id`),
  CONSTRAINT `fk_branches_manager`
    FOREIGN KEY (`manager_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------
-- Table `fields`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `fields`;
CREATE TABLE `fields` (
  `field_id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) NULL,
  `type` VARCHAR(50) NULL,
  `status` VARCHAR(50) NULL,
  `size` VARCHAR(50) NULL,
  `image_url` VARCHAR(255) NULL,
  `price_per_hour` DOUBLE NULL,
  `weekend_price` DOUBLE NULL,
  `branch_id` BIGINT NULL,
  `supervisor_id` BIGINT NULL,
  PRIMARY KEY (`field_id`),
  CONSTRAINT `fk_fields_branch`
    FOREIGN KEY (`branch_id`)
    REFERENCES `branches` (`branch_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_fields_supervisor`
    FOREIGN KEY (`supervisor_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------
-- Table `bookings`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `bookings`;
CREATE TABLE `bookings` (
  `booking_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NULL,
  `field_id` BIGINT NULL,
  `date` DATE NULL,
  `start_time` TIME NULL,
  `end_time` TIME NULL,
  `status` VARCHAR(50) NULL,
  `price` DOUBLE NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `hire_photographer` BOOLEAN DEFAULT FALSE,
  `rent_jersey` BOOLEAN DEFAULT FALSE,
  `payment_status` VARCHAR(50) NULL, -- NEW
  `result` VARCHAR(255) NULL,
  PRIMARY KEY (`booking_id`),
  CONSTRAINT `fk_bookings_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_bookings_field`
    FOREIGN KEY (`field_id`)
    REFERENCES `fields` (`field_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------
-- Table `problem_reports`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `problem_reports`;
CREATE TABLE `problem_reports` (
  `report_id` BIGINT NOT NULL AUTO_INCREMENT,
  `description` TEXT NULL,
  `image_url` VARCHAR(255) NULL,
  `status` VARCHAR(50) NULL,
  `timestamp` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `notes` TEXT NULL,
  `user_id` BIGINT NULL,
  `branch_id` BIGINT NULL, -- NEW
  `field_id` BIGINT NULL, -- NEW
  PRIMARY KEY (`report_id`),
  CONSTRAINT `fk_reports_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE SET NULL
    ON UPDATE CASCADE,
  CONSTRAINT `fk_reports_branch`
    FOREIGN KEY (`branch_id`)
    REFERENCES `branches` (`branch_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_reports_field`
    FOREIGN KEY (`field_id`)
    REFERENCES `fields` (`field_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------
-- Table `tasks`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tasks`;
CREATE TABLE `tasks` (
  `task_id` BIGINT NOT NULL AUTO_INCREMENT,
  `description` TEXT NULL,
  `status` VARCHAR(50) NULL,
  `due_date` DATETIME NULL, -- Changed from DATE to DATETIME
  `completed_at` DATETIME NULL, -- NEW
  `creation_date` DATETIME DEFAULT CURRENT_TIMESTAMP, -- NEW
  `proof_image_url` VARCHAR(255) NULL,
  `task_image_url` VARCHAR(255) NULL,
  `completion_note` TEXT NULL, -- NEW
  `user_id` BIGINT NULL,
  `report_id` BIGINT NULL, -- NEW
  PRIMARY KEY (`task_id`),
  CONSTRAINT `fk_tasks_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_tasks_report`
    FOREIGN KEY (`report_id`)
    REFERENCES `problem_reports` (`report_id`)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------
-- Table `price_matrix`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `price_matrix`;
CREATE TABLE `price_matrix` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `field_id` BIGINT NULL,
  `day_of_week` VARCHAR(20) NULL,
  `start_time` TIME NULL,
  `price` DOUBLE NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_pricematrix_field`
    FOREIGN KEY (`field_id`)
    REFERENCES `fields` (`field_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------
-- Optional: Insert Initial Manager User
-- -----------------------------------------------------
-- Password is 'manager123'
INSERT INTO `users` (`username`, `email`, `password`, `role`) VALUES 
('manager', 'manager@example.com', '$2a$10$Dow1.s/lB.wX5.1.1.1.1.1.1.1.1.1.1', 'MANAGER');
