package org.traccar.api.security;


import org.traccar.storage.StorageName;

import java.util.Date;

@StorageName("LoginHistory")
public class LoginHistory {

    private Long id;
    private String loginTime;
    private Long userId;
    private String ipAddress;
    private String userAgent;
    private String loginStatus;
    private String failureReason;

    // Constructors
    public LoginHistory() {}

    public LoginHistory(String loginTime, Long userId, String ipAddress, String userAgent, String loginStatus, String failureReason) {
        this.loginTime = loginTime;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.loginStatus = loginStatus;
        this.failureReason = failureReason;
    }

    // Getters and Setters


    public String getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }


    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getLoginStatus() {
        return loginStatus;
    }

    public void setLoginStatus(String loginStatus) {
        this.loginStatus = loginStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    // Optionally, override toString(), equals(), and hashCode() methods if needed.

    @Override
    public String toString() {
        return "LoginHistory{" +
                "id=" + id +
                ", userId=" + userId +
                ", ipAddress='" + ipAddress + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", loginStatus='" + loginStatus + '\'' +
                ", failureReason='" + failureReason + '\'' +
                '}';
    }
}
