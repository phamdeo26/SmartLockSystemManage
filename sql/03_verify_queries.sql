-- Smart Lock System Management - 03: verification queries (read-only)
USE SmartLockDB;
GO
-- 1. No table may be empty
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'locks', COUNT(*) FROM locks
UNION ALL SELECT 'lock_permissions', COUNT(*) FROM lock_permissions
UNION ALL SELECT 'credentials', COUNT(*) FROM credentials
UNION ALL SELECT 'access_logs', COUNT(*) FROM access_logs;

-- 2. Foreign keys declared (expected: 8)
SELECT COUNT(*) AS foreign_key_count FROM sys.foreign_keys;

-- 3. Passwords must be hashes, never plain text (expected: 0 rows)
SELECT username FROM users WHERE LEN(password_hash) <> 64 OR password_hash IN ('Admin@123', 'User@123');

-- 4. Each role has at least one account
SELECT role, COUNT(*) AS accounts FROM users GROUP BY role;

-- 5. Credentials per type and status
SELECT credential_type, status, COUNT(*) AS total FROM credentials GROUP BY credential_type, status ORDER BY credential_type, status;

-- 6. Rows that can open a door by PIN / card / fingerprint right now (every row must say APPROVED)
SELECT c.credential_id, u.username, l.lock_name, c.credential_type, c.status
FROM credentials c JOIN users u ON u.user_id = c.user_id JOIN locks l ON l.lock_id = c.lock_id
WHERE c.status = 'APPROVED';

-- 7. Every decided credential names the admin who decided it (expected: 0 rows)
SELECT c.credential_id FROM credentials c LEFT JOIN users d ON d.user_id = c.decided_by AND d.role = 'ADMIN'
WHERE c.status <> 'PENDING' AND d.user_id IS NULL;

-- 8. The log cannot be changed. Run this by hand: it must FAIL with "access_logs is append-only"
-- DELETE FROM access_logs;
-- UPDATE access_logs SET result = 'SUCCESS';
GO
