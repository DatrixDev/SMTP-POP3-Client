-- MySQL 8+; local metadata only, not a replacement for SMTP/POP3 servers.
CREATE DATABASE IF NOT EXISTS smtp_pop3_client CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smtp_pop3_client;

CREATE TABLE IF NOT EXISTS mail_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(320) NOT NULL UNIQUE,
  smtp_host VARCHAR(255) NOT NULL,
  smtp_port INT NOT NULL DEFAULT 587,
  pop3_host VARCHAR(255) NOT NULL,
  pop3_port INT NOT NULL DEFAULT 995,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mail_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  direction ENUM('SENT','RECEIVED') NOT NULL,
  message_id VARCHAR(512),
  sender VARCHAR(320),
  recipients TEXT,
  subject VARCHAR(998),
  body MEDIUMTEXT,
  sent_at DATETIME NULL,
  fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_mail_account FOREIGN KEY (account_id) REFERENCES mail_accounts(id) ON DELETE CASCADE,
  INDEX idx_account_direction (account_id, direction),
  INDEX idx_message_id (message_id(191))
);
-- Do not store email passwords or application passwords in this database.

-- Drafts are stored separately because mail_messages.direction only covers sent/received.
CREATE TABLE IF NOT EXISTS mail_drafts (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  account_id BIGINT NOT NULL,
  recipients TEXT,
  subject VARCHAR(998),
  body MEDIUMTEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_draft_account (account_id),
  CONSTRAINT fk_draft_account FOREIGN KEY (account_id) REFERENCES mail_accounts(id) ON DELETE CASCADE
);
