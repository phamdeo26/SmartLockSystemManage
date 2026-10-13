-- Smart Lock System Management - 02: sample data
USE SmartLockDB;
GO

-- Accounts. Password hash = SHA-256(salt + password), computed here so no plain text is stored.
--   admin / Admin@123   (ADMIN)
--   alice / User@123    (USER)
--   bob   / User@123    (USER)
INSERT INTO users (username, password_hash, salt, full_name, role) VALUES
('admin', LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'a1b2c3d4' + 'Admin@123'), 2)), 'a1b2c3d4', N'System Administrator', 'ADMIN'),
('alice', LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'e5f6a7b8' + 'User@123'),  2)), 'e5f6a7b8', N'Alice Johnson',       'USER'),
('bob',   LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'c9d0e1f2' + 'User@123'),  2)), 'c9d0e1f2', N'Bob Smith',           'USER');

INSERT INTO locks (lock_name, location, status) VALUES
(N'Main Entrance',   N'Building A - Floor 1',  'LOCKED'),
(N'Server Room',     N'Building A - Floor 2',  'LOCKED'),
(N'Storage Room',    N'Building A - Basement', 'UNLOCKED'),
(N'Meeting Room A',  N'Building B - Floor 1',  'LOCKED'),
(N'Parking Gate',    N'Outdoor',               'LOCKED'),
(N'Roof Access',     N'Building B - Roof',     'LOCKED');

-- 50 extra locks so list screens have enough rows to test paging and sorting
;WITH n AS (SELECT TOP (50) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS i FROM sys.all_objects)
INSERT INTO locks (lock_name, location, status)
SELECT CONCAT(N'Office Door ', RIGHT(CONCAT('00', i), 2)),
       CONCAT(N'Building C - Floor ', (i % 5) + 1),
       CASE WHEN i % 7 = 0 THEN 'UNLOCKED' ELSE 'LOCKED' END
FROM n;

INSERT INTO lock_permissions (user_id, lock_id, valid_from, valid_to) VALUES
(2, 1, DATEADD(DAY, -30, SYSDATETIME()), NULL),
(2, 3, DATEADD(DAY, -30, SYSDATETIME()), NULL),
(2, 4, DATEADD(DAY, -30, SYSDATETIME()), NULL),
(3, 1, DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, 30, SYSDATETIME())),
(3, 5, DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, 30, SYSDATETIME()));

-- Credentials. Demo values (what you type on the keypad / present at the door):
--   1 alice PIN 135790 on lock 1 APPROVED     2 alice PIN 246810 on lock 3 PENDING
--   3 bob   PIN 112233 on lock 1 REJECTED     4 bob   PIN 445566 on lock 5 REVOKED
--   5 alice fingerprint slot 1 on lock 1 APPROVED     6 bob card A1B2C3D4 on lock 1 APPROVED
--   7 alice card 0F1E2D3C on lock 3 APPROVED
INSERT INTO credentials (user_id, lock_id, credential_type, secret_hash, label, status, requested_at, decided_by, decided_at) VALUES
(2, 1, 'PIN',         LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'PIN:1:135790'), 2)),       NULL,                  'APPROVED', DATEADD(DAY, -3, SYSDATETIME()),  1,    DATEADD(DAY, -3, SYSDATETIME())),
(2, 3, 'PIN',         LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'PIN:3:246810'), 2)),       NULL,                  'PENDING',  DATEADD(HOUR, -2, SYSDATETIME()), NULL, NULL),
(3, 1, 'PIN',         LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'PIN:1:112233'), 2)),       NULL,                  'REJECTED', DATEADD(DAY, -2, SYSDATETIME()),  1,    DATEADD(DAY, -2, SYSDATETIME())),
(3, 5, 'PIN',         LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'PIN:5:445566'), 2)),       NULL,                  'REVOKED',  DATEADD(DAY, -5, SYSDATETIME()),  1,    DATEADD(DAY, -1, SYSDATETIME())),
(2, 1, 'FINGERPRINT', LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'FINGERPRINT:1:1'), 2)),    N'Right index finger', 'APPROVED', DATEADD(DAY, -6, SYSDATETIME()),  1,    DATEADD(DAY, -6, SYSDATETIME())),
(3, 1, 'CARD',        LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'CARD:1:A1B2C3D4'), 2)),    N'Blue card',          'APPROVED', DATEADD(DAY, -6, SYSDATETIME()),  1,    DATEADD(DAY, -6, SYSDATETIME())),
(2, 3, 'CARD',        LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', 'CARD:3:0F1E2D3C'), 2)),    N'Office card',        'APPROVED', DATEADD(DAY, -4, SYSDATETIME()),  1,    DATEADD(DAY, -4, SYSDATETIME()));

INSERT INTO access_logs (lock_id, user_id, credential_id, method, result, detail, log_time) VALUES
(1, 2, 1,    'PIN',         'SUCCESS', NULL,                      DATEADD(HOUR, -30, SYSDATETIME())),
(1, 2, 5,    'FINGERPRINT', 'SUCCESS', NULL,                      DATEADD(HOUR, -26, SYSDATETIME())),
(3, 2, 7,    'CARD',        'SUCCESS', NULL,                      DATEADD(HOUR, -22, SYSDATETIME())),
(1, 3, 3,    'PIN',         'DENIED',  N'PIN is rejected',        DATEADD(HOUR, -20, SYSDATETIME())),
(5, 3, 4,    'PIN',         'DENIED',  N'PIN is revoked',         DATEADD(HOUR, -18, SYSDATETIME())),
(1, NULL, NULL, 'CARD',     'DENIED',  N'Unknown card',           DATEADD(HOUR, -15, SYSDATETIME())),
(1, 3, 6,    'CARD',        'SUCCESS', NULL,                      DATEADD(HOUR, -12, SYSDATETIME())),
(4, 2, NULL, 'WEB',         'SUCCESS', N'Remote unlock from web', DATEADD(HOUR, -5,  SYSDATETIME())),
(2, 3, NULL, 'WEB',         'DENIED',  N'No valid access right',  DATEADD(HOUR, -4,  SYSDATETIME())),
(2, 1, NULL, 'WEB',         'SUCCESS', N'Remote unlock from web', DATEADD(HOUR, -3,  SYSDATETIME()));
GO
