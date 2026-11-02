package com.traffic.traffic_backend.dto;

import com.traffic.traffic_backend.traffic.Road;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for acoustic siren detection events transmitted over WebSocket and REST.
 */
public class AudioDetectionDto {

    private String micId;
    private Road road;
    private String soundClass;
    private float confidence;
    private float frequencyHz;
    private float decibelLevel;
    private int consecutiveHits;
    private boolean isSirenConfirmed;
    private boolean isSimulated;
    private LocalDateTime timestamp;

    public AudioDetectionDto() {}

    public AudioDetectionDto(String micId, Road road, String soundClass, float confidence,
                             float frequencyHz, float decibelLevel, int consecutiveHits,
                             boolean isSirenConfirmed, boolean isSimulated) {
        this.micId = micId;
        this.road = road;
        this.soundClass = soundClass;
        this.confidence = confidence;
        this.frequencyHz = frequencyHz;
        this.decibelLevel = decibelLevel;
        this.consecutiveHits = consecutiveHits;
        this.isSirenConfirmed = isSirenConfirmed;
        this.isSimulated = isSimulated;
        this.timestamp = LocalDateTime.now();
    }

    public String getMicId() { return micId; }
    public void setMicId(String micId) { this.micId = micId; }

    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }

    public String getSoundClass() { return soundClass; }
    public void setSoundClass(String soundClass) { this.soundClass = soundClass; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public float getFrequencyHz() { return frequencyHz; }
    public void setFrequencyHz(float frequencyHz) { this.frequencyHz = frequencyHz; }

    public float getDecibelLevel() { return decibelLevel; }
    public void setDecibelLevel(float decibelLevel) { this.decibelLevel = decibelLevel; }

    public int getConsecutiveHits() { return consecutiveHits; }
    public void setConsecutiveHits(int consecutiveHits) { this.consecutiveHits = consecutiveHits; }

    public boolean isSirenConfirmed() { return isSirenConfirmed; }
    public void setSirenConfirmed(boolean sirenConfirmed) { isSirenConfirmed = sirenConfirmed; }

    public boolean isSimulated() { return isSimulated; }
    public void setSimulated(boolean simulated) { isSimulated = simulated; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
