ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_balance_non_negative;
ALTER TABLE accounts ADD CONSTRAINT chk_balance_within_limit CHECK (balance + overdraft_limit >= 0);
ALTER TABLE accounts ADD CONSTRAINT chk_avail_balance_within_limit CHECK (available_balance + overdraft_limit >= 0);
