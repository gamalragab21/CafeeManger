-- Seed Data for Development (Docker PostgreSQL)
-- This file is automatically run when PostgreSQL container starts
-- All accounts use password: password123
-- BCrypt Hash: $2a$12$LQv3c1yqBo9SkvXS7QTJPOoZkNYzMHFb9BqZfwxDLkMxWDS5bC5Ey

-- NOTE: The programmatic seed (DatabaseConfig.seedIfEmpty) will handle full test data
-- This file is kept as a minimal fallback for Docker init only

-- The full test data is seeded programmatically on first startup and includes:
-- 1. مطعم الشام (RESTAURANT) - Manager: +2010101001, Cashier: +2010101002, Delivery: +2010101003
-- 2. صيدلية الشفاء (PHARMACY) - Manager: +2020202001, Cashier: +2020202002, Delivery: +2020202003
-- 3. كافيه لافندر (CAFE) - Manager: +2030303001, Cashier: +2030303002, Delivery: +2030303003
-- 4. مخبز السنابل (BAKERY) - Manager: +2040404001, Cashier: +2040404002, Delivery: +2040404003
-- 5. سوبر ماركت الخير (SUPERMARKET) - Manager: +2050505001, Cashier: +2050505002, Delivery: +2050505003
-- 6. محل لعب أطفال توي لاند (RETAIL) - Manager: +2060606001, Cashier: +2060606002, Delivery: +2060606003
--
-- Each vendor includes: categories, menu items, customers, stock, and workers
-- All passwords: password123
