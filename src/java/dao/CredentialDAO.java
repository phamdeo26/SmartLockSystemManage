/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;
import dto.AccessCredential;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import util.DBUtils;
import util.PageResult;
import util.PasswordUtil;
/**
 *
 * @author User
 */
public class CredentialDAO {

    /** SQL Server error numbers for a duplicate key / unique index violation. */
    private static final int DUPLICATE_KEY_1 = 2601;
    private static final int DUPLICATE_KEY_2 = 2627;

    /**
     * A normal user asks for a PIN. The row is ALWAYS saved as PENDING.
     * Returns OK, NO_LOCK, NO_RIGHT (user has no valid access right for the lock) or DUPLICATE.
     */
    public String requestPin(int userId, int lockId, String pin) throws SQLException {
        try (Connection c = DBUtils.getConnection()) {
            if (!lockExists(c, lockId)) {
                return "NO_LOCK";
            }
            if (!AccessRightDAO.hasRight(c, userId, lockId)) {
                return "NO_RIGHT";
            }
            return insert(c, userId, lockId, "PIN", pin, null, "PENDING", null);
        }
    }

    /**
     * An admin registers a PIN, fingerprint or card for a user. It is saved as APPROVED and records the admin.
     * Returns OK, NO_LOCK, NO_USER or DUPLICATE.
     */
    public String enroll(int adminId, int userId, int lockId, String type, String value, String label) throws SQLException {
        try (Connection c = DBUtils.getConnection()) {
            if (!lockExists(c, lockId)) {
                return "NO_LOCK";
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE user_id = ? AND is_active = 1")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "NO_USER";
                    }
                }
            }
            return insert(c, userId, lockId, type, value, label, "APPROVED", adminId);
        }
    }

    private boolean lockExists(Connection c, int lockId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM locks WHERE lock_id = ? AND is_active = 1")) {
            ps.setInt(1, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String insert(Connection c, int userId, int lockId, String type, String value, String label, String status, Integer decidedBy)
            throws SQLException {
        String sql = "INSERT INTO credentials (user_id, lock_id, credential_type, secret_hash, label, status, decided_by, decided_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, " + (decidedBy == null ? "NULL" : "SYSDATETIME()") + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, lockId);
            ps.setString(3, type);
            ps.setString(4, PasswordUtil.credentialHash(type, lockId, value));
            ps.setString(5, label == null || label.isEmpty() ? null : label);
            ps.setString(6, status);
            if (decidedBy == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, decidedBy);
            }
            ps.executeUpdate();
            return "OK";
        } catch (SQLException e) {
            if (e.getErrorCode() == DUPLICATE_KEY_1 || e.getErrorCode() == DUPLICATE_KEY_2) {
                return "DUPLICATE";
            }
            throw e;
        }
    }

    /** userId = 0 lists every credential (admin); type = "" and status = "" mean no filter. */
    public PageResult<AccessCredential> search(int userId, String type, String status, String keyword, int page, int size)
            throws SQLException {
        String from = " FROM credentials c JOIN users u ON u.user_id = c.user_id JOIN locks l ON l.lock_id = c.lock_id"
                + " LEFT JOIN users d ON d.user_id = c.decided_by"
                + " WHERE (? = 0 OR c.user_id = ?) AND (? = '' OR c.credential_type = ?) AND (? = '' OR c.status = ?)"
                + " AND (u.username LIKE ? OR l.lock_name LIKE ? OR c.label LIKE ?)";
        String like = "%" + keyword + "%";
        try (Connection c = DBUtils.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*)" + from)) {
                bind(ps, userId, type, status, like);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            int currentPage = PageResult.clampPage(page, total, size);
            String sql = "SELECT c.credential_id, u.user_id, u.username, l.lock_id, l.lock_name, c.credential_type, c.label, c.status,"
                    + " c.requested_at, d.username, c.decided_at" + from
                    + " ORDER BY c.requested_at DESC, c.credential_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<AccessCredential> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, userId, type, status, like);
                ps.setInt(10, (currentPage - 1) * size);
                ps.setInt(11, size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AccessCredential a = new AccessCredential();
                        a.setCredentialId(rs.getInt(1));
                        a.setUserId(rs.getInt(2));
                        a.setUsername(rs.getString(3));
                        a.setLockId(rs.getInt(4));
                        a.setLockName(rs.getString(5));
                        a.setType(rs.getString(6));
                        a.setLabel(rs.getString(7));
                        a.setStatus(rs.getString(8));
                        a.setRequestedAt(rs.getTimestamp(9));
                        a.setDecidedBy(rs.getString(10));
                        a.setDecidedAt(rs.getTimestamp(11));
                        items.add(a);
                    }
                }
            }
            return new PageResult<>(items, currentPage, size, total);
        }
    }

    private void bind(PreparedStatement ps, int userId, String type, String status, String like) throws SQLException {
        ps.setInt(1, userId);
        ps.setInt(2, userId);
        ps.setString(3, type);
        ps.setString(4, type);
        ps.setString(5, status);
        ps.setString(6, status);
        ps.setString(7, like);
        ps.setString(8, like);
        ps.setString(9, like);
    }

    public int countPending() throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM credentials WHERE status = 'PENDING'");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Admin decision. The change only happens when the row is currently in fromStatus, so
     * approve/reject work on PENDING rows only and revoke works on APPROVED rows only.
     */
    public boolean decide(int credentialId, int adminId, String fromStatus, String toStatus) throws SQLException {
        String sql = "UPDATE credentials SET status = ?, decided_by = ?, decided_at = SYSDATETIME() WHERE credential_id = ? AND status = ?";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setInt(2, adminId);
            ps.setInt(3, credentialId);
            ps.setString(4, fromStatus);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Called by the ESP32 for a PIN, a fingerprint or a card. This is the ONLY place where a credential can open a door,
     * and it only accepts rows whose status is APPROVED. Every attempt is written to the entry log.
     * Returns SUCCESS, DENIED or UNKNOWN (lock does not exist, nothing logged). value must already be normalized.
     */
    public String verify(int lockId, String type, String value) throws SQLException {
        String find = "SELECT TOP 1 c.credential_id, c.user_id, c.status, u.role, u.is_active, l.is_active "
                + "FROM credentials c JOIN users u ON u.user_id = c.user_id JOIN locks l ON l.lock_id = c.lock_id "
                + "WHERE c.lock_id = ? AND c.credential_type = ? AND c.secret_hash = ? "
                + "ORDER BY CASE WHEN c.status = 'APPROVED' THEN 0 ELSE 1 END, c.credential_id DESC";
        try (Connection c = DBUtils.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM locks WHERE lock_id = ?")) {
                    ps.setInt(1, lockId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return "UNKNOWN";
                        }
                    }
                }
                Integer credentialId = null;
                Integer ownerId = null;
                String result;
                String detail = null;
                try (PreparedStatement ps = c.prepareStatement(find)) {
                    ps.setInt(1, lockId);
                    ps.setString(2, type);
                    ps.setString(3, PasswordUtil.credentialHash(type, lockId, value));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            result = "DENIED";
                            detail = "Unknown " + type.toLowerCase();
                        } else {
                            credentialId = rs.getInt(1);
                            ownerId = rs.getInt(2);
                            String status = rs.getString(3);
                            boolean ownerIsAdmin = "ADMIN".equals(rs.getString(4));
                            boolean ownerActive = rs.getBoolean(5);
                            boolean lockActive = rs.getBoolean(6);
                            if (!"APPROVED".equals(status)) {
                                result = "DENIED";
                                detail = type + " is " + status.toLowerCase();
                            } else if (!ownerActive) {
                                result = "DENIED";
                                detail = "User is disabled";
                            } else if (!lockActive) {
                                result = "DENIED";
                                detail = "Lock is disabled";
                            } else if (!ownerIsAdmin && !AccessRightDAO.hasRight(c, ownerId, lockId)) {
                                result = "DENIED";
                                detail = "No valid access right";
                            } else {
                                result = "SUCCESS";
                            }
                        }
                    }
                }
                if ("SUCCESS".equals(result)) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE locks SET status = 'UNLOCKED' WHERE lock_id = ?")) {
                        ps.setInt(1, lockId);
                        ps.executeUpdate();
                    }
                }
                AccessLogDAO.insert(c, lockId, ownerId, credentialId, type, result, detail);
                c.commit();
                return result;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
