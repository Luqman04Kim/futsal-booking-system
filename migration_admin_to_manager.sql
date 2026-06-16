-- Migration Script: Update ADMIN role to MANAGER
-- This script updates all existing admin users to the new manager role

USE `football_db`;

-- Update all users with ADMIN role to MANAGER role
UPDATE `users` 
SET `role` = 'MANAGER' 
WHERE `role` = 'ADMIN';

-- Verify the changes
SELECT user_id, username, email, role 
FROM `users` 
WHERE `role` = 'MANAGER';
