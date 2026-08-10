CREATE TABLE IF NOT EXISTS admin_totp_credentials (
  subject_id VARCHAR(128) NOT NULL,
  encrypted_secret TEXT NOT NULL,
  nonce VARBINARY(32) NOT NULL,
  key_version VARCHAR(64) NOT NULL,
  algorithm VARCHAR(32) NOT NULL,
  digits INT NOT NULL,
  period_seconds INT NOT NULL,
  last_accepted_timestep BIGINT NULL,
  credential_version BIGINT NOT NULL DEFAULT 1,
  enabled_at DATETIME(6) NOT NULL,
  PRIMARY KEY (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_auth_challenges (
  challenge_hash CHAR(64) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  session_hash CHAR(64) NULL,
  password_authenticated_at DATETIME(6) NULL,
  encrypted_secret TEXT NULL,
  nonce VARBINARY(32) NULL,
  key_version VARCHAR(64) NULL,
  expires_at DATETIME(6) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (challenge_hash),
  KEY idx_admin_auth_challenge_subject (subject_id, purpose, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_recovery_codes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  subject_id VARCHAR(128) NOT NULL,
  code_hash VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  replaced_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_admin_recovery_subject (subject_id, used_at, replaced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_sessions (
  session_hash CHAR(64) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  environment VARCHAR(32) NOT NULL,
  auth_mode VARCHAR(32) NOT NULL,
  assurance VARCHAR(32) NOT NULL,
  password_authenticated_at DATETIME(6) NULL,
  totp_authenticated_at DATETIME(6) NULL,
  credential_version BIGINT NOT NULL,
  password_credential_hash CHAR(64) NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  idle_expires_at DATETIME(6) NOT NULL,
  last_seen_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  PRIMARY KEY (session_hash),
  KEY idx_admin_sessions_subject (subject_id, revoked_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_operation_challenges (
  challenge_hash CHAR(64) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  session_hash CHAR(64) NOT NULL,
  action_key VARCHAR(255) NOT NULL,
  target_id VARCHAR(255) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (challenge_hash),
  KEY idx_admin_operation_challenge_session (session_hash, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_operation_proofs (
  proof_hash CHAR(64) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  session_hash CHAR(64) NOT NULL,
  action_key VARCHAR(255) NOT NULL,
  target_id VARCHAR(255) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (proof_hash),
  KEY idx_admin_operation_proof_session (session_hash, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_auth_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_type VARCHAR(64) NOT NULL,
  subject_id VARCHAR(128) NULL,
  session_hash CHAR(64) NULL,
  source VARCHAR(128) NULL,
  result VARCHAR(32) NOT NULL,
  reason_code VARCHAR(128) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_admin_auth_audit_subject (subject_id, created_at),
  KEY idx_admin_auth_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
