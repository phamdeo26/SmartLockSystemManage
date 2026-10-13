/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.SmartLock;
import dto.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import util.DBUtils;
import util.PageResult;

/**
 *
 * @author acer
 */


public class LockDao {

    /** ORDER BY cannot use "?", so the sort column is checked against this fixed list. */
    private static final Set<String> SORT_COLUMNS = new HashSet<>(Arrays.asList("lock_name", "location", "status", "created_at"));
    private static final String COLUMNS = "lock_id, lock_name, location, status, created_at";

    private SmartLock map(ResultSet rs) throws SQLException {
        SmartLock l = new SmartLock();
        l.setLockId(rs.getInt("lock_id"));
        l.setLockName(rs.getString("lock_name"));
        l.setLocation(rs.getString("location"));
        l.setStatus(rs.getString("status"));
        l.setCreatedAt(rs.getTimestamp("created_at"));
        return l;
    }

    public PageResult<SmartLock> search(String keyword, String status, String sort, boolean asc, int page, int size)
            throws SQLException {
        String sortColumn = SORT_COLUMNS.contains(sort) ? sort : "lock_name";
        String where = " WHERE is_active = 1 AND (lock_name LIKE ? OR location LIKE ?) AND (? = '' OR status = ?)";
        String like = "%" + keyword + "%";
        try (Connection c = DBUtils.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM locks" + where)) {
                bindFilter(ps, like, status);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            int currentPage = PageResult.clampPage(page, total, size);
            String sql = "SELECT " + COLUMNS + " FROM locks" + where + " ORDER BY " + sortColumn + (asc ? " ASC" : " DESC")
                    + ", lock_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<SmartLock> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bindFilter(ps, like, status);
                ps.setInt(5, (currentPage - 1) * size);
                ps.setInt(6, size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(map(rs));
                    }
                }
            }
            return new PageResult<>(items, currentPage, size, total);
        }
    }

    private void bindFilter(PreparedStatement ps, String like, String status) throws SQLException {
        ps.setString(1, like);
        ps.setString(2, like);
        ps.setString(3, status);
        ps.setString(4, status);
    }

    public List<SmartLock> listAll() throws SQLException {
        List<SmartLock> items = new ArrayList<>();
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM locks WHERE is_active = 1 ORDER BY lock_name");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(map(rs));
            }
        }
        return items;
    }

    /** Locks this user holds a valid access right for (used by the PIN request form). */
    public List<SmartLock> listForUser(int userId) throws SQLException {
        List<SmartLock> items = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM locks l WHERE is_active = 1 AND EXISTS (SELECT 1 FROM lock_permissions p "
                + "WHERE p.lock_id = l.lock_id AND p.user_id = ? AND p.valid_from <= SYSDATETIME() "
                + "AND (p.valid_to IS NULL OR p.valid_to >= SYSDATETIME())) ORDER BY lock_name";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        }
        return items;
    }

    public SmartLock findById(int lockId) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM locks WHERE lock_id = ? AND is_active = 1")) {
            ps.setInt(1, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public void insert(SmartLock lock) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO locks (lock_name, location) VALUES (?, ?)")) {
            ps.setString(1, lock.getLockName());
            ps.setString(2, lock.getLocation());
            ps.executeUpdate();
        }
    }

    public boolean update(SmartLock lock) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE locks SET lock_name = ?, location = ? WHERE lock_id = ? AND is_active = 1")) {
            ps.setString(1, lock.getLockName());
            ps.setString(2, lock.getLocation());
            ps.setInt(3, lock.getLockId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Soft delete: the row stays so old log lines keep their foreign key. */
    public boolean delete(int lockId) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE locks SET is_active = 0 WHERE lock_id = ? AND is_active = 1")) {
            ps.setInt(1, lockId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Returns {total locks, unlocked locks}. */
    public int[] stats() throws SQLException {
        String sql = "SELECT COUNT(*), SUM(CASE WHEN status = 'UNLOCKED' THEN 1 ELSE 0 END) FROM locks WHERE is_active = 1";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new int[]{rs.getInt(1), rs.getInt(2)};
        }
    }

    /**
     * "Open remotely" from the web. Needs a valid access right (admins always have one).
     * Marks the request for the ESP32 and writes the log line in ONE transaction.
     * Returns SUCCESS, DENIED or NOT_FOUND.
     */
    public String remoteOpen(int lockId, User user) throws SQLException {
        try (Connection c = DBUtils.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM locks WHERE lock_id = ? AND is_active = 1")) {
                    ps.setInt(1, lockId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return "NOT_FOUND";
                        }
                    }
                }
                if (!user.isAdmin() && !AccessRightDAO.hasRight(c, user.getUserId(), lockId)) {
                    AccessLogDAO.insert(c, lockId, user.getUserId(), null, "WEB", "DENIED", "No valid access right");
                    c.commit();
                    return "DENIED";
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE locks SET status = 'UNLOCKED', open_requested_at = SYSDATETIME() WHERE lock_id = ?")) {
                    ps.setInt(1, lockId);
                    ps.executeUpdate();
                }
                AccessLogDAO.insert(c, lockId, user.getUserId(), null, "WEB", "SUCCESS", "Remote unlock from web");
                c.commit();
                return "SUCCESS";
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Called by the ESP32. True only once per web request, and only if the request is at most 30 seconds old,
     * so a request made while the device was offline can never open the door later.
     */
    public boolean consumeOpenRequest(int lockId) throws SQLException {
        String sql = "UPDATE locks SET open_requested_at = NULL "
                + "OUTPUT CASE WHEN deleted.open_requested_at >= DATEADD(SECOND, -30, SYSDATETIME()) THEN 1 ELSE 0 END "
                + "WHERE lock_id = ? AND is_active = 1 AND open_requested_at IS NOT NULL";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    /** The ESP32 reports LOCKED again after the door closes. */
    public boolean setStatus(int lockId, String status) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE locks SET status = ? WHERE lock_id = ? AND is_active = 1")) {
            ps.setString(1, status);
            ps.setInt(2, lockId);
            return ps.executeUpdate() > 0;
        }
    }
}
