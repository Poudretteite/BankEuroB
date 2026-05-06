-- ================================================================
-- BankEuroB – V5: Dodanie blik_pin do tabeli customers
-- ================================================================

ALTER TABLE customers ADD COLUMN blik_pin VARCHAR(4);
