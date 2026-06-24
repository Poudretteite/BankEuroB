ALTER TABLE transactions ADD COLUMN IF NOT EXISTS aml_status VARCHAR(30);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS aml_explanation TEXT;
