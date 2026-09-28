-- V001 core + ref schema
CREATE TABLE api_clients (
  id CHAR(36) PRIMARY KEY,
  client_code VARCHAR(64) NOT NULL,
  client_name VARCHAR(128) NOT NULL,
  client_secret_hash VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  allowed_scopes VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uq_api_clients_client_code (client_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
  id CHAR(36) PRIMARY KEY,
  api_client_id CHAR(36) NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  jti VARCHAR(64) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  replaced_by_id CHAR(36) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_refresh_tokens_token_hash (token_hash),
  KEY idx_refresh_client (api_client_id),
  CONSTRAINT fk_refresh_client FOREIGN KEY (api_client_id) REFERENCES api_clients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customers (
  id CHAR(36) PRIMARY KEY,
  cnic VARCHAR(13) NOT NULL,
  username VARCHAR(64) NULL,
  password_hash VARCHAR(100) NULL,
  full_name VARCHAR(200) NOT NULL,
  mobile VARCHAR(20) NULL,
  email VARCHAR(200) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  str_token_jti VARCHAR(64) NULL,
  str_token_expires_at DATETIME(3) NULL,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uq_customers_cnic (cnic),
  UNIQUE KEY uq_customers_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE accounts (
  id CHAR(36) PRIMARY KEY,
  customer_id CHAR(36) NOT NULL,
  account_number VARCHAR(32) NOT NULL,
  iban VARCHAR(34) NOT NULL,
  account_title VARCHAR(200) NOT NULL,
  account_type VARCHAR(32) NOT NULL DEFAULT 'CURRENT',
  product_code VARCHAR(32) NOT NULL DEFAULT 'ASAAN_DIGITAL',
  branch_code VARCHAR(16) NOT NULL DEFAULT '001',
  currency CHAR(3) NOT NULL DEFAULT 'PKR',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uq_accounts_number (account_number),
  UNIQUE KEY uq_accounts_iban (iban),
  KEY idx_accounts_customer (customer_id),
  CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ledger_balances (
  id CHAR(36) PRIMARY KEY,
  account_id CHAR(36) NOT NULL,
  available_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
  ledger_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
  currency CHAR(3) NOT NULL DEFAULT 'PKR',
  as_of DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_ledger_account (account_id),
  CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transactions (
  id CHAR(36) PRIMARY KEY,
  account_id CHAR(36) NOT NULL,
  counterparty_account VARCHAR(64) NULL,
  counterparty_iban VARCHAR(34) NULL,
  to_imd VARCHAR(16) NULL,
  direction VARCHAR(8) NOT NULL,
  txn_type VARCHAR(16) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'PKR',
  fee DECIMAL(19,4) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  stan VARCHAR(32) NULL,
  int_ref_num VARCHAR(32) NULL,
  transaction_id VARCHAR(64) NULL,
  purpose_of_payment VARCHAR(32) NULL,
  narration VARCHAR(255) NULL,
  correlation_id VARCHAR(64) NULL,
  idempotency_key VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_txn_account (account_id),
  KEY idx_txn_stan (stan),
  KEY idx_txn_txid (transaction_id),
  KEY idx_txn_idem (idempotency_key),
  CONSTRAINT fk_txn_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE statement_entries (
  id CHAR(36) PRIMARY KEY,
  account_id CHAR(36) NOT NULL,
  transaction_id CHAR(36) NULL,
  booking_date DATE NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'PKR',
  direction VARCHAR(8) NOT NULL,
  running_balance DECIMAL(19,4) NOT NULL,
  narrative VARCHAR(255) NOT NULL,
  stan VARCHAR(32) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_stmt_account_date (account_id, booking_date),
  CONSTRAINT fk_stmt_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE onboarding_applications (
  id CHAR(36) PRIMARY KEY,
  cnic VARCHAR(13) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'STARTED',
  step_id INT NOT NULL DEFAULT 1,
  status_id INT NOT NULL DEFAULT 1,
  customer_id CHAR(36) NULL,
  payload_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_onb_cnic (cnic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idempotency_keys (
  id CHAR(36) PRIMARY KEY,
  api_client_id CHAR(36) NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash VARCHAR(128) NULL,
  response_body MEDIUMTEXT NULL,
  http_status INT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_idem_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE outbox_events (
  id CHAR(36) PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'NEW',
  correlation_id VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  published_at DATETIME(3) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  KEY idx_outbox_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
  id CHAR(36) PRIMARY KEY,
  actor_type VARCHAR(32) NULL,
  actor_id VARCHAR(64) NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NULL,
  resource_id VARCHAR(64) NULL,
  correlation_id VARCHAR(64) NULL,
  outcome VARCHAR(32) NULL,
  detail JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_audit_created (created_at),
  KEY idx_audit_corr (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE otp_challenges (
  id CHAR(36) PRIMARY KEY,
  customer_id CHAR(36) NULL,
  otp_reference VARCHAR(64) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  verified TINYINT(1) NOT NULL DEFAULT 0,
  otp_ticket VARCHAR(64) NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_otp_ref (otp_reference),
  KEY idx_otp_ticket (otp_ticket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_banks (
  id CHAR(36) PRIMARY KEY,
  imd VARCHAR(16) NOT NULL,
  bank_code VARCHAR(16) NOT NULL,
  short_name VARCHAR(64) NOT NULL,
  legal_name VARCHAR(200) NOT NULL,
  iban_bank_code VARCHAR(8) NULL,
  supports_ibft TINYINT(1) NOT NULL DEFAULT 1,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  logo_url VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 100,
  version INT NOT NULL DEFAULT 1,
  as_of DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uq_ref_banks_imd (imd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_purpose_codes (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  description VARCHAR(200) NOT NULL,
  applies_to VARCHAR(64) NOT NULL DEFAULT 'IFT,IBFT',
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_purpose_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_purpose_of_account (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  description VARCHAR(200) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_poa_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_occupations (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  description VARCHAR(200) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_occ_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_response_codes (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(16) NOT NULL,
  message_en VARCHAR(255) NOT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'ERROR',
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_resp_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_account_types (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  label VARCHAR(128) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_acct_type (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_provinces (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(8) NOT NULL,
  name_en VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 100,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_prov_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_id_types (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(16) NOT NULL,
  label VARCHAR(64) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_id_type (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_finger_indexes (
  id CHAR(36) PRIMARY KEY,
  index_code VARCHAR(8) NOT NULL,
  label VARCHAR(64) NOT NULL,
  hand VARCHAR(8) NOT NULL,
  sort_order INT NOT NULL DEFAULT 100,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_finger (index_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_onboarding_steps (
  id CHAR(36) PRIMARY KEY,
  step_id INT NOT NULL,
  label VARCHAR(128) NOT NULL,
  ordinal INT NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_onb_step (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_branches (
  id CHAR(36) PRIMARY KEY,
  branch_code VARCHAR(16) NOT NULL,
  name VARCHAR(128) NOT NULL,
  city VARCHAR(64) NULL,
  province_code VARCHAR(8) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_branch (branch_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_currencies (
  id CHAR(36) PRIMARY KEY,
  iso_code CHAR(3) NOT NULL,
  numeric_code CHAR(3) NOT NULL,
  decimals INT NOT NULL DEFAULT 2,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_currency (iso_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ref_app_config (
  id CHAR(36) PRIMARY KEY,
  config_key VARCHAR(64) NOT NULL,
  config_value VARCHAR(512) NOT NULL,
  value_type VARCHAR(16) NOT NULL DEFAULT 'STRING',
  is_public TINYINT(1) NOT NULL DEFAULT 1,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_app_cfg (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
