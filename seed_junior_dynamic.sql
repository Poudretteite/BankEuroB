DO $$
DECLARE
    v_anna_id UUID;
    v_anna_account_id UUID;
    v_junior_id UUID;
BEGIN
    SELECT id INTO v_anna_id FROM customers WHERE email = 'anna.kowalski@example.com';
    SELECT id INTO v_anna_account_id FROM accounts WHERE customer_id = v_anna_id LIMIT 1;
    
    -- Check if junior exists
    SELECT id INTO v_junior_id FROM customers WHERE email = 'junior@example.com';
    
    IF v_junior_id IS NULL THEN
        -- Insert customer
        INSERT INTO customers (id, email, password_hash, first_name, last_name, date_of_birth, role, parent_id, address_country)
        VALUES (uuid_generate_v4(), 'junior@example.com', '$2a$10$EG2ER3IcpTvwit6Ztl4gv.SXaZEyoDBZFVqDhcE3L.Pr328jQ35bm', 'Kamil', 'Kowalski', '2015-01-01', 'JUNIOR', v_anna_id, 'DE')
        RETURNING id INTO v_junior_id;
        
        -- Insert account
        INSERT INTO accounts (customer_id, iban, account_type, balance, available_balance, parent_account_id, currency, daily_limit)
        VALUES (v_junior_id, 'DE89370400440000300003', 'JUNIOR', 150.00, 150.00, v_anna_account_id, 'EUR', 50.00);
    END IF;
END $$;
