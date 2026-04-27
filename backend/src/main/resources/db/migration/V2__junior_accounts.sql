-- ================================================================
-- BankEuroB – V2: Konta Junior
-- Dodanie relacji rodzic-dziecko w customers
-- Dodanie tabeli login_attempts (2FA akceptacja przez rodzica)
-- ================================================================

-- Kolumna parent_id w customers (NULL dla dorosłych, wskazuje na rodzica dla JUNIOR)
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES customers(id);

CREATE INDEX IF NOT EXISTS idx_customers_parent_id ON customers(parent_id);

-- ================================================================
-- LOGIN_ATTEMPTS – próby logowania kont JUNIOR wymagające akceptacji rodzica
-- ================================================================
CREATE TABLE IF NOT EXISTS login_attempts (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    status      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                -- PENDING, APPROVED, REJECTED, CONSUMED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_customer_id ON login_attempts(customer_id);
CREATE INDEX IF NOT EXISTS idx_login_attempts_status ON login_attempts(status);
