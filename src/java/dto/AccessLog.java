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
public class AccessLog {

    private long logId;
    private String lockName;
    private String username;
    private String method;
    private String result;
    private String detail;
    private Timestamp logTime;

    public AccessLog(long logId, String lockName, String username, String method, String result, String detail, Timestamp logTime) {
        this.logId = logId;
        this.lockName = lockName;
        this.username = username;
        this.method = method;
        this.result = result;
        this.detail = detail;
        this.logTime = logTime;
    }

    public long getLogId() { return logId; }
    public String getLockName() { return lockName; }
    public String getUsername() { return username; }
    public String getMethod() { return method; }
    public String getResult() { return result; }
    public String getDetail() { return detail; }
    public Timestamp getLogTime() { return logTime; }
}
