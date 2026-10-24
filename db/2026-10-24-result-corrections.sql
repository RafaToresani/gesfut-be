-- Revision critica #5: auditoria de correcciones de resultado
CREATE TABLE IF NOT EXISTS result_corrections (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  match_id BIGINT NULL,
  user_email VARCHAR(255) NULL,
  corrected_at DATETIME(6) NULL,
  old_result TEXT NULL,
  new_result TEXT NULL,
  KEY idx_result_corrections_match (match_id)
);
