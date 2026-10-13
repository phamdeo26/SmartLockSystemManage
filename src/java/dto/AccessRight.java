/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

import java.sql.Timestamp;

/**
 *
 * @author acer
 */
public class AccessRight {

    private int rightId;
    private int userId;
    private String username;
    private int lockId;
    private String lockName;
    private Timestamp validFrom;
    private Timestamp validTo;
    private String status;

    public AccessRight() {
    }

    public int getRightId() { return rightId; }
    public void setRightId(int rightId) { this.rightId = rightId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getLockId() { return lockId; }
    public void setLockId(int lockId) { this.lockId = lockId; }
    public String getLockName() { return lockName; }
    public void setLockName(String lockName) { this.lockName = lockName; }
    public Timestamp getValidFrom() { return validFrom; }
    public void setValidFrom(Timestamp validFrom) { this.validFrom = validFrom; }
    public Timestamp getValidTo() { return validTo; }
    public void setValidTo(Timestamp validTo) { this.validTo = validTo; }
    /** ACTIVE, EXPIRED or NOT_STARTED. */
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
