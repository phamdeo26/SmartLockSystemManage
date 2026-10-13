/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

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
import util.PasswordUtil;

/**
 *
 * @author User
 */


public class UserDAO {

    private static final Set<String> SORT_COLUMNS = new HashSet<>(
            Arrays.asList("username", "full_name", "role", "created_at"));
    private static final String COLUMNS = "user_id, username, full_name, role, is_active, created_at";

    private User map(ResultSet rs) throws SQLException {
        User u = new User(rs.getInt("user_id"), rs.getString("username"), rs.getString("full_name"), rs.getString("role"));
        u.setActive(rs.getBoolean("is_active"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        return u;
    }

    /** Returns the user when the username and password are correct, otherwise null. */
    public User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT " + COLUMNS + ", password_hash, salt FROM users WHERE username = ? AND is_active = 1";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && PasswordUtil.matches(rs.getString("salt"), password, rs.getString("password_hash"))) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public boolean existsUsername(String username) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insert(User user, String password) throws SQLException {
        String salt = PasswordUtil.newSalt();
        String sql = "INSERT INTO users (username, password_hash, salt, full_name, role, is_active) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, PasswordUtil.hash(salt, password));
            ps.setString(3, salt);
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getRole());
            ps.setBoolean(6, user.isActive());
            ps.executeUpdate();
        }
    }

    public User findById(int userId) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Updates name, role and status. A non-empty newPassword also resets the password. */
    public boolean update(User user, String newPassword) throws SQLException {
        boolean resetPassword = newPassword != null && !newPassword.isEmpty();
        String sql = "UPDATE users SET full_name = ?, role = ?, is_active = ?"
                + (resetPassword ? ", password_hash = ?, salt = ?" : "") + " WHERE user_id = ?";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, user.getFullName());
            ps.setString(i++, user.getRole());
            ps.setBoolean(i++, user.isActive());
            if (resetPassword) {
                String salt = PasswordUtil.newSalt();
                ps.setString(i++, PasswordUtil.hash(salt, newPassword));
                ps.setString(i++, salt);
            }
            ps.setInt(i, user.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Soft delete: the account is disabled, old access logs keep their foreign key. */
    public boolean disable(int userId) throws SQLException {
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE users SET is_active = 0 WHERE user_id = ? AND is_active = 1")) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<User> listActive() throws SQLException {
        List<User> items = new ArrayList<>();
        try (Connection c = DBUtils.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE is_active = 1 ORDER BY username");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(map(rs));
            }
        }
        return items;
    }

    public PageResult<User> search(String keyword, String role, String sort, boolean asc, int page, int size)
            throws SQLException {
        String sortColumn = SORT_COLUMNS.contains(sort) ? sort : "username";
        String where = " WHERE (username LIKE ? OR full_name LIKE ?) AND (? = '' OR role = ?)";
        String like = "%" + keyword + "%";
        try (Connection c = DBUtils.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users" + where)) {
                bind(ps, like, role);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            int currentPage = PageResult.clampPage(page, total, size);
            String sql = "SELECT " + COLUMNS + " FROM users" + where + " ORDER BY " + sortColumn
                    + (asc ? " ASC" : " DESC") + ", user_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<User> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, like, role);
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

    private void bind(PreparedStatement ps, String like, String role) throws SQLException {
        ps.setString(1, like);
        ps.setString(2, like);
        ps.setString(3, role);
        ps.setString(4, role);
    }
}
