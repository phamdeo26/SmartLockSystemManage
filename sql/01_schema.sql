-- Smart Lock System Management - 01: schema
-- Run order: 01_schema.sql -> 02_sample_data.sql -> 03_verify_queries.sql
-- This script drops and recreates the whole database so it can be re-run from scratch.
IF DB_ID('SmartLockDB') IS NOT NULL
BEGIN
    ALTER DATABASE SmartLockDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE SmartLockDB;
END
GO
CREATE DATABASE SmartLockDB;
GO
USE SmartLockDB;
GO

-- Tables are created top to bottom: every foreign key points to a table created earlier.

CREATE TABLE users (
    user_id        INT IDENTITY(1,1) CONSTRAINT pk_users PRIMARY KEY,
    username       VARCHAR(50)   NOT NULL CONSTRAINT uq_users_username UNIQUE,
    password_hash  CHAR(64)      NOT NULL,                -- SHA-256 hex of (salt + password)
    salt           VARCHAR(16)   NOT NULL,
    full_name      NVARCHAR(100) NOT NULL,
    role           VARCHAR(10)   NOT NULL CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER')),
    is_active      BIT           NOT NULL CONSTRAINT df_users_active DEFAULT 1,
    created_at     DATETIME2     NOT NULL CONSTRAINT df_users_created DEFAULT SYSDATETIME()
);

CREATE TABLE locks (
    lock_id            INT IDENTITY(1,1) CONSTRAINT pk_locks PRIMARY KEY,
    lock_name          NVARCHAR(100) NOT NULL,
    location           NVARCHAR(200) NULL,
    status             VARCHAR(10)   NOT NULL CONSTRAINT df_locks_status DEFAULT 'LOCKED'
                       CONSTRAINT ck_locks_status CHECK (status IN ('LOCKED', 'UNLOCKED')),
    open_requested_at  DATETIME2     NULL,                -- set by "open remotely" on the web, read once by the ESP32
    is_active          BIT           NOT NULL CONSTRAINT df_locks_active DEFAULT 1,
    created_at         DATETIME2     NOT NULL CONSTRAINT df_locks_created DEFAULT SYSDATETIME()
);

-- Access right: which user may use which lock, and during which time window (applies to every way of opening).
CREATE TABLE lock_permissions (
    permission_id  INT IDENTITY(1,1) CONSTRAINT pk_lock_permissions PRIMARY KEY,
    user_id        INT       NOT NULL CONSTRAINT fk_permissions_user REFERENCES users(user_id),
    lock_id        INT       NOT NULL CONSTRAINT fk_permissions_lock REFERENCES locks(lock_id),
    valid_from     DATETIME2 NOT NULL CONSTRAINT df_permissions_from DEFAULT SYSDATETIME(),
    valid_to       DATETIME2 NULL                          -- NULL = no expiry
);

-- What a person presents at the door: a PIN, a fingerprint (sensor slot id) or a card (UID).
-- Only a row with status = 'APPROVED' can open a door. The secret is stored as a hash:
-- secret_hash = SHA-256(credential_type + ':' + lock_id + ':' + value), so no real PIN is ever stored.
CREATE TABLE credentials (
    credential_id    INT IDENTITY(1,1) CONSTRAINT pk_credentials PRIMARY KEY,
    user_id          INT         NOT NULL CONSTRAINT fk_credentials_user REFERENCES users(user_id),
    lock_id          INT         NOT NULL CONSTRAINT fk_credentials_lock REFERENCES locks(lock_id),
    credential_type  VARCHAR(12) NOT NULL CONSTRAINT ck_credentials_type CHECK (credential_type IN ('PIN', 'FINGERPRINT', 'CARD')),
    secret_hash      CHAR(64)    NOT NULL,
    label            NVARCHAR(50) NULL,                     -- free text such as "Blue card" or "Right index finger"
    status           VARCHAR(10) NOT NULL CONSTRAINT df_credentials_status DEFAULT 'PENDING'
                     CONSTRAINT ck_credentials_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED')),
    requested_at     DATETIME2   NOT NULL CONSTRAINT df_credentials_requested DEFAULT SYSDATETIME(),
    decided_by       INT         NULL CONSTRAINT fk_credentials_decider REFERENCES users(user_id),
    decided_at       DATETIME2   NULL,
    -- a decision (approve / reject / revoke) always records who decided and when; a pending row has neither
    CONSTRAINT ck_credentials_decision CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
     OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);
-- The same secret cannot be pending/approved twice on one lock, so a PIN / card / finger always identifies one owner.
CREATE UNIQUE INDEX ux_credentials_active ON credentials(lock_id, credential_type, secret_hash) WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX ix_credentials_user ON credentials(user_id, requested_at DESC);

-- Entry log: who opened which door, when, how, and whether it worked.
-- user_id and credential_id are NULL when somebody tried an unknown PIN / card / fingerprint.
CREATE TABLE access_logs (
    log_id         BIGINT IDENTITY(1,1) CONSTRAINT pk_access_logs PRIMARY KEY,
    lock_id        INT          NOT NULL CONSTRAINT fk_logs_lock REFERENCES locks(lock_id),
    user_id        INT          NULL CONSTRAINT fk_logs_user REFERENCES users(user_id),
    credential_id  INT          NULL CONSTRAINT fk_logs_credential REFERENCES credentials(credential_id),
    method         VARCHAR(12)  NOT NULL CONSTRAINT ck_logs_method CHECK (method IN ('WEB', 'PIN', 'FINGERPRINT', 'CARD')),
    result         VARCHAR(10)  NOT NULL CONSTRAINT ck_logs_result CHECK (result IN ('SUCCESS', 'DENIED')),
    detail         NVARCHAR(100) NULL,                      -- reason, e.g. "PIN is pending"; never contains a secret
    log_time       DATETIME2    NOT NULL CONSTRAINT df_logs_time DEFAULT SYSDATETIME()
);
CREATE INDEX ix_access_logs_time ON access_logs(log_time DESC);
GO

-- The log is append-only: nobody (application, admin screen or a hand-written query) can change or delete a row.
CREATE TRIGGER tr_access_logs_append_only ON access_logs INSTEAD OF UPDATE, DELETE
AS
BEGIN
    RAISERROR('access_logs is append-only: rows cannot be updated or deleted.', 16, 1);
END
GO
