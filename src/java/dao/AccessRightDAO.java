/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;
import dto.AccessRight;
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
/**
 *
 * @author acer
 */
public class AccessRightDAO {

    /** True when the user holds a right for the lock that is valid right now. Used for web, PIN, card and fingerprint. */
    public static boolean hasRight(Connection c, int userId, int lockId) throws SQLException {
        String sql = "SELECT 1 FROM lock_permissions WHERE user_id = ? AND lock_id = ? "
                + "AND valid_from <= SYSDATETIME() AND (valid_to IS NULL OR valid_to >= SYSDATETIME())";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public PageResult<AccessRight> search(String keyword, int page, int size) throws SQLException {
        String from = " FROM lock_permissions p JOIN users u ON u.user_id = p.user_id JOIN locks l ON l.lock_id = p.lock_id"
                + " WHERE l.is_active = 1 AND (u.username LIKE ? OR u.full_name LIKE ? OR l.lock_name LIKE ?)";
        String like = "%" + keyword + "%";
        try (Connection c = DBUtils.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*)" + from)) {
                bind(ps, like);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            int currentPage = PageResult.clampPage(page, total, size);
            String sql = "SELECT p.permission_id, u.user_id, u.username, l.lock_id, l.lock_name, p.valid_from, p.valid_to,"
                    + " CASE WHEN p.valid_from > SYSDATETIME() THEN 'NOT_STARTED'"
                    + " WHEN p.valid_to IS NOT NULL AND p.valid_to < SYSDATETIME() THEN 'EXPIRED' ELSE 'ACTIVE' END"
                    + from + " ORDER BY p.valid_from DESC, p.permission_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<AccessRight> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, like);
                ps.setInt(4, (currentPage - 1) * size);
                ps.setInt(5, size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AccessRight a = new AccessRight();
                        a.setRightId(rs.getInt(1));
                        a.setUserId(rs.getInt(2));
                        a.setUsername(rs.getString(3));
                        a.setLockId(rs.getInt(4));
                        a.setLockName(rs.getString(5));
                        a.setValidFrom(rs.getTimestamp(6));
                        a.setValidTo(rs.getTimestamp(7));
                        a.setStatus(rs.getString(8));
                        items.add(a);
                    }
                }
            }
            return new PageResult<>(items, currentPage, size, total);
        }
    }

    private void bind(PreparedStatement ps, String like) throws SQLException {
        ps.setString(1, like);
        ps.setString(2, like);
        ps.setString(3, like);
    }

    /** validTo may be null (no expiry). */
    public void insert(int userId, int lockId, Timestamp validFrom, Timestamp validTo) throws SQLException {
        String sql = "INSERT INTO lock_permissions (user_id, lock_id, valid_from, valid_to) VALUES (?, ?, ?, ?)";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, lockId);
            ps.setTimestamp(3, validFrom);
            if (validTo == null) {
                ps.setNull(4, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(4, validTo);
            }
            ps.executeUpdate();
        }
    }

    public boolean delete(int rightId) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM lock_permissions WHERE permission_id = ?")) {
            ps.setInt(1, rightId);
            return ps.executeUpdate() > 0;
        }
    }
}
