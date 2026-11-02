package com.traffic.traffic_backend.entity;

import com.traffic.traffic_backend.traffic.Road;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing an acoustic microphone siren detection event.
 */
@Entity
@Table(name = "siren_detections", indexes = {
        @Index(name = "idx_siren_road_time", columnList = "road, detected_at")
})
public class SirenDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mic_id", nullable = false, length = 50)
    private String micId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Road road;

    @Column(name = "sound_class", nullable = false, length = 50)
    private String soundClass = "EMERGENCY_SIREN";

    @Column(nullable = false)
    private Float confidence;

    @Column(name = "frequency_hz")
    private Float frequencyHz;

    @Column(name = "decibel_level")
    private Float decibelLevel;

    @Column(name = "consecutive_hits", nullable = false)
    private Integer consecutiveHits = 1;

    @Column(name = "is_simulated", nullable = false)
    private Boolean isSimulated = false;

    @Column(name = "audio_filename", length = 255)
    private String audioFilename = "live_microphone.wav";

    @Column(name = "siren_detected", length = 10)
    private String sirenDetected = "YES";

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt = LocalDateTime.now();

    public SirenDetection() {}

    public SirenDetection(String micId, Road road, String soundClass, Float confidence,
                          Float frequencyHz, Float decibelLevel, Integer consecutiveHits, Boolean isSimulated) {
        this.micId = micId;
        this.road = road;
        this.soundClass = soundClass;
        this.confidence = confidence;
        this.frequencyHz = frequencyHz;
        this.decibelLevel = decibelLevel;
        this.consecutiveHits = consecutiveHits;
        this.isSimulated = isSimulated;
        this.detectedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getMicId() { return micId; }
    public void setMicId(String micId) { this.micId = micId; }
    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }
    public String getSoundClass() { return soundClass; }
    public void setSoundClass(String soundClass) { this.soundClass = soundClass; }
    public Float getConfidence() { return confidence; }
    public void setConfidence(Float confidence) { this.confidence = confidence; }
    public Float getFrequencyHz() { return frequencyHz; }
    public void setFrequencyHz(Float frequencyHz) { this.frequencyHz = frequencyHz; }
    public Float getDecibelLevel() { return decibelLevel; }
    public void setDecibelLevel(Float decibelLevel) { this.decibelLevel = decibelLevel; }
    public Integer getConsecutiveHits() { return consecutiveHits; }
    public void setConsecutiveHits(Integer consecutiveHits) { this.consecutiveHits = consecutiveHits; }
    public Boolean getIsSimulated() { return isSimulated; }
    public void setIsSimulated(Boolean isSimulated) { this.isSimulated = isSimulated; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    public String getAudioFilename() { return audioFilename; }
    public void setAudioFilename(String audioFilename) { this.audioFilename = audioFilename; }
    public String getSirenDetected() { return sirenDetected; }
    public void setSirenDetected(String sirenDetected) { this.sirenDetected = sirenDetected; }
}
