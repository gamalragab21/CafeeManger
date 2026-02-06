-- Seed Data for Development
-- This file is automatically run when PostgreSQL container starts

-- Create a demo vendor
INSERT INTO vendors (id, name, logo_url, address, contact_phone, wallet_phone, created_at, updated_at)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'Demo Cafe',
    NULL,
    '123 Main Street, Downtown',
    '+1234567890',
    '+1234567890',
    NOW(),
    NOW()
) ON CONFLICT DO NOTHING;

-- Create demo users (password is "password123" hashed with BCrypt)
-- Hash: $2a$12$LQv3c1yqBo9SkvXS7QTJPOoZkNYzMHFb9BqZfwxDLkMxWDS5bC5Ey
INSERT INTO users (id, vendor_id, role, name, phone, email, password_hash, active, created_at, updated_at)
VALUES
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'MANAGER', 'Demo Manager', '+1111111111', 'manager@demo.com',
     '$2a$12$LQv3c1yqBo9SkvXS7QTJPOoZkNYzMHFb9BqZfwxDLkMxWDS5bC5Ey', true, NOW(), NOW()),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'CASHIER', 'Demo Cashier', '+2222222222', 'cashier@demo.com',
     '$2a$12$LQv3c1yqBo9SkvXS7QTJPOoZkNYzMHFb9BqZfwxDLkMxWDS5bC5Ey', true, NOW(), NOW()),
    ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'DELIVERY', 'Demo Delivery', '+3333333333', 'delivery@demo.com',
     '$2a$12$LQv3c1yqBo9SkvXS7QTJPOoZkNYzMHFb9BqZfwxDLkMxWDS5bC5Ey', true, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Create demo categories
INSERT INTO categories (id, vendor_id, name, display_order, created_at, updated_at)
VALUES
    ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'Burgers', 1, NOW(), NOW()),
    ('f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'Drinks', 2, NOW(), NOW()),
    ('a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'Desserts', 3, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Create demo items
INSERT INTO items (id, vendor_id, category_id, name, description, price, available, created_at, updated_at)
VALUES
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Classic Burger', 'Beef patty with lettuce and tomato', 8.99, true, NOW(), NOW()),
    ('c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Cheese Burger', 'Classic with extra cheese', 10.99, true, NOW(), NOW()),
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Cola', 'Refreshing cola drink', 2.99, true, NOW(), NOW()),
    ('e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Fresh Juice', 'Orange juice', 4.99, true, NOW(), NOW()),
    ('f1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Chocolate Cake', 'Rich chocolate layer cake', 6.99, true, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Create demo tables
INSERT INTO restaurant_tables (id, vendor_id, number, capacity, status, created_at, updated_at)
VALUES
    ('a2eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'T1', 4, 'AVAILABLE', NOW(), NOW()),
    ('b2eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'T2', 2, 'AVAILABLE', NOW(), NOW()),
    ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'T3', 6, 'AVAILABLE', NOW(), NOW()),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     'T4', 4, 'AVAILABLE', NOW(), NOW())
ON CONFLICT DO NOTHING;
