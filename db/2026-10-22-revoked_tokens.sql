CREATE TABLE IF NOT EXISTS revoked_tokens (
  token_hash VARCHAR(64) NOT NULL PRIMARY KEY,
  expires_at BIGINT NOT NULL,
  INDEX idx_revoked_tokens_expires (expires_at)
) ENGINE=InnoDB;
