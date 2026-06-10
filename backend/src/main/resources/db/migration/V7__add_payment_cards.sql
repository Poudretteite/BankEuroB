CREATE TABLE payment_cards (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    card_token VARCHAR(255) NOT NULL UNIQUE,
    card_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_payment_card_account
        FOREIGN KEY(account_id) 
        REFERENCES accounts(id)
        ON DELETE CASCADE
);
