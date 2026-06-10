CREATE TABLE blik_transactions (
    id UUID PRIMARY KEY,
    klik_transaction_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(50) NOT NULL,
    customer_id UUID NOT NULL,
    account_id UUID,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    merchant_name VARCHAR(200),
    is_on_us BOOLEAN,
    zone VARCHAR(2),
    expiry_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_AUTHORIZATION',
    merchant_net NUMERIC(19, 4),
    klik_fee NUMERIC(19, 4),
    agent_fee NUMERIC(19, 4),
    reference_number VARCHAR(50),
    received_at TIMESTAMP WITH TIME ZONE,
    authorized_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_blik_tx_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_blik_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE UNIQUE INDEX idx_blik_tx_klik_id ON blik_transactions(klik_transaction_id);
CREATE INDEX idx_blik_tx_customer ON blik_transactions(customer_id);
CREATE INDEX idx_blik_tx_status ON blik_transactions(status);
