/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.AccessLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import util.DBUtils;
import util.PageResult;

/**
 *
 * @author User
 */
public class AccessLogDAO {

    /** Adds one line inside the caller's transaction. userId and credentialId may be null. */
    public static void insert(Connection c, int lockId, Integer userId, Integer credentialId, String method, String result, String detail)
            throws SQLException {
        String sql = "INSERT INTO access_logs (lock_id, user_id, credential_id, method, result, detail) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, lockId);
            if (userId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, userId);
            }
            if (credentialId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, credentialId);
            }
            ps.setString(4, method);
            ps.setString(5, result);
            ps.setString(6, detail);
            ps.executeUpdate();
        }
    }

    /** userId = 0 returns everybody's rows (admin); method = "" and result = "" mean no filter. */
    public PageResult<AccessLog> search(int userId, String method, String result, int page, int size) throws SQLException {
        String from = " FROM access_logs a JOIN locks l ON l.lock_id = a.lock_id LEFT JOIN users u ON u.user_id = a.user_id"
                + " WHERE (? = 0 OR a.user_id = ?) AND (? = '' OR a.method = ?) AND (? = '' OR a.result = ?)";
        try (Connection c = DBUtils.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*)" + from)) {
                bind(ps, userId, method, result);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            int currentPage = PageResult.clampPage(page, total, size);
            String sql = "SELECT a.log_id, l.lock_name, u.username, a.method, a.result, a.detail, a.log_time" + from
                    + " ORDER BY a.log_time DESC, a.log_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<AccessLog> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, userId, method, result);
                ps.setInt(7, (currentPage - 1) * size);
                ps.setInt(8, size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new AccessLog(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getString(5), rs.getString(6), rs.getTimestamp(7)));
                    }
                }
            }
            return new PageResult<>(items, currentPage, size, total);
        }
    }

    private void bind(PreparedStatement ps, int userId, String method, String result) throws SQLException {
        ps.setInt(1, userId);
        ps.setInt(2, userId);
        ps.setString(3, method);
        ps.setString(4, method);
        ps.setString(5, result);
        ps.setString(6, result);
    }

    public int countDeniedLast24Hours() throws SQLException {
        String sql = "SELECT COUNT(*) FROM access_logs WHERE result = 'DENIED' AND log_time >= DATEADD(HOUR, -24, SYSDATETIME())";
        try (Connection c = DBUtils.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}