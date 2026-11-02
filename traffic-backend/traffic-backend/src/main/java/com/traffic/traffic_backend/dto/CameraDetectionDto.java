package com.traffic.traffic_backend.dto;

import com.traffic.traffic_backend.traffic.Road;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for camera detection events transmitted over WebSocket and REST.
 */
public class CameraDetectionDto {

    private int trackId;
    private String label;
    private float confidence;
    private int x;
    private int y;
    private int width;
    private int height;
    private double centerX;
    private double centerY;
    private Road road;
    private boolean isAmbulance;
    private boolean isSimulated;
    private LocalDateTime timestamp;

    public CameraDetectionDto() {}

    public CameraDetectionDto(int trackId, String label, float confidence, int x, int y,
                              int width, int height, double centerX, double centerY,
                              Road road, boolean isAmbulance, boolean isSimulated) {
        this.trackId = trackId;
        this.label = label;
        this.confidence = confidence;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.centerX = centerX;
        this.centerY = centerY;
        this.road = road;
        this.isAmbulance = isAmbulance;
        this.isSimulated = isSimulated;
        this.timestamp = LocalDateTime.now();
    }

    public int getTrackId() { return trackId; }
    public void setTrackId(int trackId) { this.trackId = trackId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public double getCenterX() { return centerX; }
    public void setCenterX(double centerX) { this.centerX = centerX; }

    public double getCenterY() { return centerY; }
    public void setCenterY(double centerY) { this.centerY = centerY; }

    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }

    public boolean isAmbulance() { return isAmbulance; }
    public void setAmbulance(boolean ambulance) { isAmbulance = ambulance; }

    public boolean isSimulated() { return isSimulated; }
    public void setSimulated(boolean simulated) { isSimulated = simulated; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
