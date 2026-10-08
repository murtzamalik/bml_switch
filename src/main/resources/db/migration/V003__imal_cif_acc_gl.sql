-- iMal CIF / GL fields for account-open orchestration mock
-- Called by: Spring Flyway on startup (spring.flyway.locations=classpath:db/migration)
-- Used by: MockImalAdapter createRetailCif / createGeneralAccount / listAccounts*
ALTER TABLE customers
  ADD COLUMN cif_no VARCHAR(32) NULL AFTER cnic,
  ADD UNIQUE KEY uq_customers_cif_no (cif_no);

ALTER TABLE accounts
  ADD COLUMN acc_gl VARCHAR(32) NULL AFTER product_code,
  ADD COLUMN cif_no VARCHAR(32) NULL AFTER acc_gl;

UPDATE customers SET cif_no = '9052000' WHERE cnic = '4210112345678' AND cif_no IS NULL;
UPDATE accounts SET acc_gl = '203153', cif_no = '9052000'
  WHERE customer_id = '22222222-2222-2222-2222-222222222222' AND acc_gl IS NULL;
