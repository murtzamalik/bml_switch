-- V002 seed data (sandbox)
-- client secret plaintext: change_me_sandbox_secret
-- user password plaintext: Sandbox@123
-- BCrypt $2a$ compatible hashes generated for seed; DataSeeder may refresh on boot if needed

INSERT INTO api_clients (id, client_code, client_name, client_secret_hash, status, allowed_scopes) VALUES
('11111111-1111-1111-1111-111111111111', 'appinsnap-sandbox', 'AppInSnap Sandbox',
 '$2a$12$8CWH7TK.ThV1zzPb4cWQ9enx.if9JV1m1RezVtI2CWb5.XN3kB0TC', 'ACTIVE', 'onboarding,inquiry,transfer,statement,lookups');

INSERT INTO customers (id, cnic, username, password_hash, full_name, mobile, email, status) VALUES
('22222222-2222-2222-2222-222222222222', '4210112345678', 'ali.khan',
 '$2a$12$JESwha5jYyJRpMNhV4c1BennjdxBKWtcU9SvMjvbTzErloT1bSbYC',
 'ALI KHAN', '03001234567', 'ali.khan@example.com', 'ACTIVE');

INSERT INTO accounts (id, customer_id, account_number, iban, account_title, account_type, product_code, branch_code, currency, status) VALUES
('33333333-3333-3333-3333-333333333331', '22222222-2222-2222-2222-222222222222', '0345001234567', 'PK00BMAL0000000345001234567', 'ALI KHAN', 'CURRENT', 'ASAAN_DIGITAL', '001', 'PKR', 'ACTIVE'),
('33333333-3333-3333-3333-333333333332', '22222222-2222-2222-2222-222222222222', '0345001234568', 'PK00BMAL0000000345001234568', 'ALI KHAN', 'CURRENT', 'ASAAN_DIGITAL', '001', 'PKR', 'ACTIVE'),
('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', '0345009999999', 'PK00BMAL0000000345009999999', 'ALI KHAN ZERO', 'CURRENT', 'ASAAN_DIGITAL', '001', 'PKR', 'ACTIVE');

INSERT INTO ledger_balances (id, account_id, available_balance, ledger_balance, currency) VALUES
('44444444-4444-4444-4444-444444444441', '33333333-3333-3333-3333-333333333331', 150000.0000, 150000.0000, 'PKR'),
('44444444-4444-4444-4444-444444444442', '33333333-3333-3333-3333-333333333332', 50000.0000, 50000.0000, 'PKR'),
('44444444-4444-4444-4444-444444444443', '33333333-3333-3333-3333-333333333333', 0.0000, 0.0000, 'PKR');

INSERT INTO ref_banks (id, imd, bank_code, short_name, legal_name, iban_bank_code, supports_ibft, is_active, sort_order) VALUES
('b0000001-0000-0000-0000-000000000001', '601001', 'HBL', 'HBL', 'Habib Bank Limited', 'HABP', 1, 1, 10),
('b0000001-0000-0000-0000-000000000002', '601002', 'UBL', 'UBL', 'United Bank Limited', 'UNIL', 1, 1, 20),
('b0000001-0000-0000-0000-000000000003', '601003', 'MCB', 'MCB', 'MCB Bank Limited', 'MUCB', 1, 1, 30),
('b0000001-0000-0000-0000-000000000004', '601004', 'MEZN', 'Meezan', 'Meezan Bank Limited', 'MEZN', 1, 1, 40),
('b0000001-0000-0000-0000-000000000005', '601005', 'BIPL', 'BankIslami', 'BankIslami Pakistan Limited', 'BIPL', 1, 1, 50),
('b0000001-0000-0000-0000-000000000006', '601006', 'ABL', 'Allied', 'Allied Bank Limited', 'ABPA', 1, 1, 60),
('b0000001-0000-0000-0000-000000000007', '601007', 'AKBL', 'Askari', 'Askari Bank Limited', 'ASCM', 1, 1, 70),
('b0000001-0000-0000-0000-000000000008', '601008', 'BAFL', 'Alfalah', 'Bank Alfalah Limited', 'ALFH', 1, 1, 80),
('b0000001-0000-0000-0000-000000000009', '601009', 'JSBL', 'JS Bank', 'JS Bank Limited', 'JSBL', 1, 1, 90),
('b0000001-0000-0000-0000-000000000010', '601010', 'SCBL', 'SCB', 'Standard Chartered Bank (Pakistan)', 'SCBL', 1, 1, 100),
('b0000001-0000-0000-0000-000000000011', '601011', 'NBP', 'NBP', 'National Bank of Pakistan', 'NBPA', 1, 1, 110),
('b0000001-0000-0000-0000-000000000012', '601012', 'BOP', 'BOP', 'Bank of Punjab', 'BPUN', 1, 1, 120),
('b0000001-0000-0000-0000-000000000013', '601013', 'FABL', 'Faysal', 'Faysal Bank Limited', 'FAYS', 1, 1, 130),
('b0000001-0000-0000-0000-000000000014', '601014', 'DIB', 'DIB', 'Dubai Islamic Bank Pakistan', 'DUIB', 1, 1, 140),
('b0000001-0000-0000-0000-000000000015', '601015', 'BAHL', 'BAHL', 'Bank Al Habib Limited', 'BAHL', 1, 1, 150),
('b0000001-0000-0000-0000-000000000016', '601016', 'SNBL', 'Soneri', 'Soneri Bank Limited', 'SONE', 1, 1, 160),
('b0000001-0000-0000-0000-000000000017', '601017', 'SILK', 'Silk', 'Silk Bank Limited', 'SAUD', 1, 1, 170),
('b0000001-0000-0000-0000-000000000018', '627000', 'BMAL', 'BML', 'Bank Al Murqarmah', 'BMAL', 1, 1, 5);

INSERT INTO ref_purpose_codes (id, code, description, applies_to) VALUES
('p0000001-0000-0000-0000-000000000001', 'FAM', 'Family Support', 'IFT,IBFT'),
('p0000001-0000-0000-0000-000000000002', 'SAL', 'Salary', 'IFT,IBFT'),
('p0000001-0000-0000-0000-000000000003', 'GDS', 'Goods Payment', 'IFT,IBFT'),
('p0000001-0000-0000-0000-000000000004', 'EDU', 'Education', 'IFT,IBFT'),
('p0000001-0000-0000-0000-000000000005', 'MED', 'Medical', 'IFT,IBFT');

INSERT INTO ref_purpose_of_account (id, code, description) VALUES
('a0000001-0000-0000-0000-000000000001', 'SAV', 'Savings / Personal'),
('a0000001-0000-0000-0000-000000000002', 'SAL', 'Salary Credit'),
('a0000001-0000-0000-0000-000000000003', 'BUS', 'Business');

INSERT INTO ref_occupations (id, code, description) VALUES
('o0000001-0000-0000-0000-000000000001', 'EMP', 'Salaried Employee'),
('o0000001-0000-0000-0000-000000000002', 'SLF', 'Self Employed'),
('o0000001-0000-0000-0000-000000000003', 'STU', 'Student'),
('o0000001-0000-0000-0000-000000000004', 'HOM', 'Homemaker');

INSERT INTO ref_response_codes (id, code, message_en, severity) VALUES
('r0000001-0000-0000-0000-000000000001', '00', 'Success', 'INFO'),
('r0000001-0000-0000-0000-000000000002', '12', 'Invalid or closed account', 'ERROR'),
('r0000001-0000-0000-0000-000000000003', '14', 'Account not found', 'ERROR'),
('r0000001-0000-0000-0000-000000000004', '51', 'Insufficient funds', 'ERROR'),
('r0000001-0000-0000-0000-000000000005', '61', 'Limit exceeded', 'ERROR'),
('r0000001-0000-0000-0000-000000000006', '75', 'LOCKOUT', 'ERROR'),
('r0000001-0000-0000-0000-000000000007', '76', 'Invalid or inactive IMD', 'ERROR'),
('r0000001-0000-0000-0000-000000000008', '77', 'KYC incomplete', 'ERROR'),
('r0000001-0000-0000-0000-000000000009', '78', 'OTP required or invalid', 'ERROR'),
('r0000001-0000-0000-0000-000000000010', '79', 'Manual review pending', 'WARN'),
('r0000001-0000-0000-0000-000000000011', '91', 'SESSION_EXPIRED', 'ERROR'),
('r0000001-0000-0000-0000-000000000012', '96', 'System fault', 'ERROR'),
('r0000001-0000-0000-0000-000000000013', '99', 'Upstream unavailable', 'ERROR');

INSERT INTO ref_account_types (id, code, label) VALUES
('t0000001-0000-0000-0000-000000000001', 'CURRENT', 'Current Account'),
('t0000001-0000-0000-0000-000000000002', 'SAVINGS', 'Savings Account'),
('t0000001-0000-0000-0000-000000000003', 'ASAAN_DIGITAL', 'Asaan Digital Account');

INSERT INTO ref_provinces (id, code, name_en, sort_order) VALUES
('v0000001-0000-0000-0000-000000000001', 'SD', 'Sindh', 10),
('v0000001-0000-0000-0000-000000000002', 'PB', 'Punjab', 20),
('v0000001-0000-0000-0000-000000000003', 'KP', 'Khyber Pakhtunkhwa', 30),
('v0000001-0000-0000-0000-000000000004', 'BL', 'Balochistan', 40),
('v0000001-0000-0000-0000-000000000005', 'IS', 'Islamabad', 50);

INSERT INTO ref_id_types (id, code, label) VALUES
('i0000001-0000-0000-0000-000000000001', 'CNIC', 'CNIC'),
('i0000001-0000-0000-0000-000000000002', 'NTN', 'NTN'),
('i0000001-0000-0000-0000-000000000003', 'PASSPORT', 'Passport');

INSERT INTO ref_finger_indexes (id, index_code, label, hand, sort_order) VALUES
('f0000001-0000-0000-0000-000000000001', '1', 'Right Thumb', 'RIGHT', 1),
('f0000001-0000-0000-0000-000000000002', '2', 'Right Index', 'RIGHT', 2),
('f0000001-0000-0000-0000-000000000003', '6', 'Left Thumb', 'LEFT', 6),
('f0000001-0000-0000-0000-000000000004', '7', 'Left Index', 'LEFT', 7);

INSERT INTO ref_onboarding_steps (id, step_id, label, ordinal) VALUES
('s0000001-0000-0000-0000-000000000001', 1, 'STARTED', 1),
('s0000001-0000-0000-0000-000000000002', 2, 'DOCS_UPLOADED', 2),
('s0000001-0000-0000-0000-000000000003', 3, 'CNIC_VALIDATED', 3),
('s0000001-0000-0000-0000-000000000004', 4, 'LIVELINESS_OK', 4),
('s0000001-0000-0000-0000-000000000005', 5, 'BIOMETRIC_OK', 5),
('s0000001-0000-0000-0000-000000000006', 6, 'ACCOUNT_OPENED', 6),
('s0000001-0000-0000-0000-000000000007', 7, 'REGISTERED', 7);

INSERT INTO ref_branches (id, branch_code, name, city, province_code) VALUES
('h0000001-0000-0000-0000-000000000001', '001', 'Main Branch Karachi', 'Karachi', 'SD');

INSERT INTO ref_currencies (id, iso_code, numeric_code, decimals) VALUES
('c0000001-0000-0000-0000-000000000001', 'PKR', '586', 2);

INSERT INTO ref_app_config (id, config_key, config_value, value_type, is_public) VALUES
('g0000001-0000-0000-0000-000000000001', 'currencyDefault', 'PKR', 'STRING', 1),
('g0000001-0000-0000-0000-000000000002', 'miniStatementCount', '10', 'INT', 1),
('g0000001-0000-0000-0000-000000000003', 'statementMaxDays', '90', 'INT', 1),
('g0000001-0000-0000-0000-000000000004', 'asaanMaxBalanceDisplay', '1000000', 'STRING', 1),
('g0000001-0000-0000-0000-000000000005', 'asaanDailyDebitLimitDisplay', '200000', 'STRING', 1),
('g0000001-0000-0000-0000-000000000006', 'ibftEnabled', 'true', 'BOOL', 1),
('g0000001-0000-0000-0000-000000000007', 'iftEnabled', 'true', 'BOOL', 1),
('g0000001-0000-0000-0000-000000000008', 'mockOtp', 'true', 'BOOL', 1),
('g0000001-0000-0000-0000-000000000009', 'lookupsVersion', '3', 'STRING', 1),
('g0000001-0000-0000-0000-000000000010', 'mockImal', 'true', 'BOOL', 1);
