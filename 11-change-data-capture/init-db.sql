CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    in_stock BOOLEAN DEFAULT true,
    updated_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO products (name, category, price) VALUES
    ('4K Monitor', 'Electronics', 599.99),
    ('Wireless Keyboard', 'Electronics', 79.99),
    ('Ergonomic Chair', 'Furniture', 449.99),
    ('Standing Desk', 'Furniture', 699.99),
    ('Desk Lamp', 'Accessories', 49.99);
