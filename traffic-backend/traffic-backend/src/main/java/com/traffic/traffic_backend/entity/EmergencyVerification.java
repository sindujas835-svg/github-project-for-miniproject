package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing the dual-sensor verification decision of EmergencyVerificationService.
 */
@Entity
@Table(name = "emergency_verifications", indexes = {
        @Index(name = "idx_verif_road_time", columnList = "road, verified_at"),
        @Index(name = "idx_verif_result", columnList = "verified")
})
public class EmergencyVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Road road;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ambulance_detection_id")
    private AmbulanceDetection ambulanceDetection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "siren_detection_id")
    private SirenDetection sirenDetection;

    @Column(name = "time_delta_ms")
    private Long timeDeltaMs;

    @Column(nullable = false)
    private Boolean verified;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private LocalDateTime verifiedAt = LocalDateTime.now();

    public EmergencyVerification() {}

    public EmergencyVerification(Road road, AmbulanceDetection ambulanceDetection, SirenDetection sirenDetection,
                                 Long timeDeltaMs, Boolean verified, String rejectionReason) {
        this.road = road;
        this.ambulanceDetection = ambulanceDetection;
        this.sirenDetection = sirenDetection;
        this.timeDeltaMs = timeDeltaMs;
        this.verified = verified;
        this.rejectionReason = rejectionReason;
        this.verifiedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }
    public AmbulanceDetection getAmbulanceDetection() { return ambulanceDetection; }
    public void setAmbulanceDetection(AmbulanceDetection ambulanceDetection) { this.ambulanceDetection = ambulanceDetection; }
    public SirenDetection getSirenDetection() { return sirenDetection; }
    public void setSirenDetection(SirenDetection sirenDetection) { this.sirenDetection = sirenDetection; }
    public Long getTimeDeltaMs() { return timeDeltaMs; }
    public void setTimeDeltaMs(Long timeDeltaMs) { this.timeDeltaMs = timeDeltaMs; }
    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}
