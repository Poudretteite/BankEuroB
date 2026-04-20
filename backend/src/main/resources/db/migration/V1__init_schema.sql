-- ================================================================
-- BankEuroB – V1: Inicjalizacja schematu bazy danych
-- ================================================================

-- Rozszerzenie UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ================================================================
-- CUSTOMERS – klienci banku
-- ================================================================
CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    date_of_birth   DATE NOT NULL,
    pesel           VARCHAR(11),                    -- opcjonalnie dla polskich klientów
    address_street  VARCHAR(255),
    address_city    VARCHAR(100),
    address_country VARCHAR(2) NOT NULL DEFAULT 'DE', -- kod ISO 3166-1 alpha-2
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    role            VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER', -- CUSTOMER, ADMIN, EMPLOYEE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_email ON customers(email);

-- ================================================================
-- ACCOUNTS – konta bankowe
-- ================================================================
CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id     UUID NOT NULL REFERENCES customers(id),
    iban            VARCHAR(34) NOT NULL UNIQUE,     -- np. DE89370400440532013000
    bic             VARCHAR(11) NOT NULL DEFAULT 'BKEUDEBBXXX', -- BIC banku
    account_type    VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
                                                     -- STANDARD, SAVINGS, JUNIOR
    currency        VARCHAR(3) NOT NULL DEFAULT 'EUR',
    balance         DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000, -- saldo po blokadach
    parent_account_id UUID REFERENCES accounts(id),  -- dla kont JUNIOR
    daily_limit     DECIMAL(19,4),                   -- dzienny limit przelewów
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_junior_has_parent CHECK (
        account_type != 'JUNIOR' OR parent_account_id IS NOT NULL
    )
);

CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);
CREATE INDEX idx_accounts_iban ON accounts(iban);

-- ================================================================
-- TRANSACTIONS – historia wszystkich transakcji
-- ================================================================
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number    VARCHAR(50) NOT NULL UNIQUE, -- unikalny numer referencyjny
    transaction_type    VARCHAR(30) NOT NULL,
                        -- INTERNAL, SEPA_SCT, SEPA_INSTANT, RTGS_TARGET2,
                        -- SWIFT, CARD_PAYMENT, BLIK
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                        -- PENDING, PENDING_PARENT_APPROVAL, PROCESSING,
                        -- COMPLETED, FAILED, REJECTED, BLOCKED_AML, REVERSED

    -- Nadawca
    sender_account_id   UUID REFERENCES accounts(id),
    sender_iban         VARCHAR(34) NOT NULL,
    sender_name         VARCHAR(255),
    sender_bic          VARCHAR(11),

    -- Odbiorca
    receiver_iban       VARCHAR(34) NOT NULL,
    receiver_name       VARCHAR(255),
    receiver_bic        VARCHAR(11),
    receiver_bank_name  VARCHAR(255),

    -- Kwota
    amount              DECIMAL(19,4) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'EUR',
    -- Dla płatności walutowych:
    original_amount     DECIMAL(19,4),
    original_currency   VARCHAR(3),
    exchange_rate       DECIMAL(10,6),

    title               VARCHAR(140),               -- tytuł przelewu
    description         TEXT,

    -- Daty
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processing_at       TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    value_date          DATE,                        -- data waluty (D+1 dla SEPA)

    -- Metadane integracji
    external_message_id VARCHAR(100),               -- ID nadane przez system zewnętrzny
    aml_status          VARCHAR(20),                -- APPROVED, FLAGGED, BLOCKED
    aml_reason          TEXT,
    aml_reviewed_at     TIMESTAMPTZ,
    aml_reviewer_id     UUID REFERENCES customers(id),

    -- Wyjaśnienie klienta (AML)
    customer_explanation TEXT,
    explanation_submitted_at TIMESTAMPTZ,

    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_sender_account ON transactions(sender_account_id);
CREATE INDEX idx_transactions_sender_iban ON transactions(sender_iban);
CREATE INDEX idx_transactions_receiver_iban ON transactions(receiver_iban);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);

-- ================================================================
-- CARDS – karty płatnicze
-- ================================================================
CREATE TABLE cards (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id          UUID NOT NULL REFERENCES accounts(id),
    card_number_hash    VARCHAR(255) NOT NULL,       -- zahashowany numer karty
    card_number_masked  VARCHAR(19) NOT NULL,        -- np. **** **** **** 1234
    card_holder_name    VARCHAR(100) NOT NULL,
    expiry_month        SMALLINT NOT NULL,
    expiry_year         SMALLINT NOT NULL,
    cvv_hash            VARCHAR(255) NOT NULL,
    card_type           VARCHAR(20) NOT NULL DEFAULT 'DEBIT',
                                                    -- DEBIT, PREPAID
    card_network        VARCHAR(20) NOT NULL DEFAULT 'VISA',
                                                    -- VISA, MASTERCARD
    currency            VARCHAR(3) NOT NULL DEFAULT 'EUR',
    daily_limit         DECIMAL(19,4) NOT NULL DEFAULT 1000.00,
    single_transaction_limit DECIMAL(19,4) NOT NULL DEFAULT 500.00,
    monthly_limit       DECIMAL(19,4) NOT NULL DEFAULT 5000.00,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    is_blocked          BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_reason      VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cards_account_id ON cards(account_id);

-- ================================================================
-- BLIK_CODES – jednorazowe kody BLIK
-- ================================================================
CREATE TABLE blik_codes (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id  UUID NOT NULL REFERENCES accounts(id),
    code        VARCHAR(6) NOT NULL,               -- 6-cyfrowy kod
    expires_at  TIMESTAMPTZ NOT NULL,              -- ważny 2 minuty
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blik_codes_account_id ON blik_codes(account_id);
CREATE INDEX idx_blik_codes_code ON blik_codes(code);

-- ================================================================
-- NOTIFICATIONS – powiadomienia dla klientów
-- ================================================================
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    type        VARCHAR(50) NOT NULL,
                -- TRANSFER_PENDING_APPROVAL, TRANSFER_COMPLETED,
                -- TRANSFER_FAILED, AML_FLAGGED, AML_BLOCKED
    title       VARCHAR(255) NOT NULL,
    message     TEXT NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    reference_id UUID,                            -- np. ID transakcji
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_customer_id ON notifications(customer_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);

-- ================================================================
-- AUDIT_LOG – log zmian (dla AML i compliance)
-- ================================================================
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,             -- TRANSACTION, ACCOUNT, CUSTOMER
    entity_id   UUID NOT NULL,
    action      VARCHAR(50) NOT NULL,             -- CREATE, UPDATE, STATUS_CHANGE
    old_value   JSONB,
    new_value   JSONB,
    performed_by UUID REFERENCES customers(id),
    performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address  INET
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_performed_at ON audit_log(performed_at DESC);
