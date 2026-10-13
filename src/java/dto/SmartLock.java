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
public class SmartLock {

    private int lockId;
    private String lockName;
    private String location;
    private String status = "LOCKED";
    private Timestamp createdAt;

    public SmartLock() {
    }

    public int getLockId() { return lockId; }
    public void setLockId(int lockId) { this.lockId = lockId; }
    public String getLockName() { return lockName; }
    public void setLockName(String lockName) { this.lockName = lockName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
