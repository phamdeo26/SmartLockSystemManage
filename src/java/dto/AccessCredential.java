/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;
import java.sql.Timestamp;
/**
 *
 * @author User
 */


/** A PIN, fingerprint or card that belongs to one user on one lock. The secret itself is never loaded. */
public class AccessCredential {

    private int credentialId;
    private int userId;
    private String username;
    private int lockId;
    private String lockName;
    private String type;
    private String label;
    private String status;
    private Timestamp requestedAt;
    private String decidedBy;
    private Timestamp decidedAt;

    public int getCredentialId() { return credentialId; }
    public void setCredentialId(int credentialId) { this.credentialId = credentialId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getLockId() { return lockId; }
    public void setLockId(int lockId) { this.lockId = lockId; }
    public String getLockName() { return lockName; }
    public void setLockName(String lockName) { this.lockName = lockName; }
    /** PIN, FINGERPRINT or CARD. */
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    /** PENDING, APPROVED, REJECTED or REVOKED. */
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Timestamp requestedAt) { this.requestedAt = requestedAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Timestamp getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Timestamp decidedAt) { this.decidedAt = decidedAt; }
}
