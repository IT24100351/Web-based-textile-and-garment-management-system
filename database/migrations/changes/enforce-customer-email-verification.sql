--liquibase formatted sql

--changeset tgms:enforce-customer-email-verification logicalFilePath:db/changelog/changes/enforce-customer-email-verification.sql
--comment: Require explicit email verification for new accounts and reset existing customer verification status.
ALTER TABLE users
    ALTER COLUMN email_verified_at DROP DEFAULT;

UPDATE users
SET email_verified_at = NULL
WHERE role = 'CUSTOMER'
  AND email_verified_at = created_at
  AND NOT EXISTS (
      SELECT 1
      FROM email_verification_codes
      WHERE email_verification_codes.user_id = users.id
  );

--rollback ALTER TABLE users ALTER COLUMN email_verified_at SET DEFAULT CURRENT_TIMESTAMP(6);
