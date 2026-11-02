package com.traffic.traffic_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity recording system runtime events, component errors, and safety interventions.
 */
@Entity
@Table(name = "system_logs", indexes = {
        @Index(name = "idx_log_level", columnList = "log_level"),
        @Index(name = "idx_log_comp_time", columnList = "component, logged_at")
})
public class SystemLogRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_level", nullable = false, length = 50)
    private String logLevel; // INFO, WARN, ERROR, CRITICAL

    @Column(nullable = false, length = 100)
    private String component; // TRAFFIC_FSM, CAMERA_VISION, AUDIO_SIREN, DUAL_VERIFIER

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt = LocalDateTime.now();

    public SystemLogRecord() {}

    public SystemLogRecord(String logLevel, String component, String message) {
        this.logLevel = logLevel;
        this.component = component;
        this.message = message;
        this.loggedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getLoggedAt() { return loggedAt; }
    public void setLoggedAt(LocalDateTime loggedAt) { this.loggedAt = loggedAt; }
}
