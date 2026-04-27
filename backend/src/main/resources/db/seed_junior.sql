INSERT INTO customers (id, email, password_hash, first_name, last_name, date_of_birth, role, parent_id)
VALUES (uuid_generate_v4(), 'junior@example.com', '$2a$10$EG2ER3IcpTvwit6Ztl4gv.SXaZEyoDBZFVqDhcE3L.Pr328jQ35bm', 'Kamil', 'Kowalski', '2015-01-01', 'JUNIOR', '8ef5d43f-4cb7-4a5a-8dd7-4a06be6c197c');

INSERT INTO accounts (customer_id, iban, account_type, balance, available_balance, parent_account_id)
SELECT id, 'DE89370400440000300003', 'JUNIOR', 100.00, 100.00, '0cec69c7-b85d-401e-85b4-3374f319a8c0'
FROM customers WHERE email = 'junior@example.com';
