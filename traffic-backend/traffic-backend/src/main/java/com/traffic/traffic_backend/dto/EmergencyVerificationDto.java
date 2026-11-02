package com.traffic.traffic_backend.dto;

import com.traffic.traffic_backend.traffic.Road;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for dual-sensor verification outcomes transmitted over WebSocket and REST.
 */
public class EmergencyVerificationDto {

    private Long id;
    private Road road;
    private Long ambulanceDetectionId;
    private Long sirenDetectionId;
    private String vehicleTrackId;
    private Float cameraConfidence;
    private Float audioConfidence;
    private Long timeDeltaMs;
    private boolean verified;
    private String rejectionReason;
    private boolean isSimulated;
    private LocalDateTime verifiedAt;

    public EmergencyVerificationDto() {}

    public EmergencyVerificationDto(Long id, Road road, Long ambulanceDetectionId, Long sirenDetectionId,
                                  String vehicleTrackId, Float cameraConfidence, Float audioConfidence,
                                  Long timeDeltaMs, boolean verified, String rejectionReason,
                                  boolean isSimulated, LocalDateTime verifiedAt) {
        this.id = id;
        this.road = road;
        this.ambulanceDetectionId = ambulanceDetectionId;
        this.sirenDetectionId = sirenDetectionId;
        this.vehicleTrackId = vehicleTrackId;
        this.cameraConfidence = cameraConfidence;
        this.audioConfidence = audioConfidence;
        this.timeDeltaMs = timeDeltaMs;
        this.verified = verified;
        this.rejectionReason = rejectionReason;
        this.isSimulated = isSimulated;
        this.verifiedAt = verifiedAt != null ? verifiedAt : LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }

    public Long getAmbulanceDetectionId() { return ambulanceDetectionId; }
    public void setAmbulanceDetectionId(Long ambulanceDetectionId) { this.ambulanceDetectionId = ambulanceDetectionId; }

    public Long getSirenDetectionId() { return sirenDetectionId; }
    public void setSirenDetectionId(Long sirenDetectionId) { this.sirenDetectionId = sirenDetectionId; }

    public String getVehicleTrackId() { return vehicleTrackId; }
    public void setVehicleTrackId(String vehicleTrackId) { this.vehicleTrackId = vehicleTrackId; }

    public Float getCameraConfidence() { return cameraConfidence; }
    public void setCameraConfidence(Float cameraConfidence) { this.cameraConfidence = cameraConfidence; }

    public Float getAudioConfidence() { return audioConfidence; }
    public void setAudioConfidence(Float audioConfidence) { this.audioConfidence = audioConfidence; }

    public Long getTimeDeltaMs() { return timeDeltaMs; }
    public void setTimeDeltaMs(Long timeDeltaMs) { this.timeDeltaMs = timeDeltaMs; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public boolean isSimulated() { return isSimulated; }
    public void setSimulated(boolean simulated) { isSimulated = simulated; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}
