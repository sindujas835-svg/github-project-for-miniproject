package com.traffic.traffic_backend.detection.camera;

import com.traffic.traffic_backend.traffic.Road;

/**
 * Representation of a vehicle detected by the computer vision pipeline.
 *
 * Designed for CS students:
 * When an object detection model (such as YOLO or RF-DETR) processes a video frame,
 * it returns bounding boxes defined by coordinates (x, y, width, height), a class name
 * (e.g. "ambulance", "car"), and a prediction confidence score between 0.0 and 1.0.
 *
 * To use this in traffic engineering, we calculate the vehicle's geometric centroid (center point)
 * and check which approach road's Region of Interest (ROI) contains that centroid.
 */
public class DetectedVehicle {

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
    private boolean simulated;

    public DetectedVehicle(int trackId, String label, float confidence,
                           int x, int y, int width, int height, Road road, boolean simulated) {
        this.trackId = trackId;
        this.label = label != null ? label.toLowerCase() : "unknown";
        this.confidence = confidence;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.centerX = x + (width / 2.0);
        this.centerY = y + (height / 2.0);
        this.road = road;
        this.simulated = simulated;
    }

    /**
     * Checks if this detection represents an emergency ambulance.
     */
    public boolean isAmbulance() {
        return "ambulance".equalsIgnoreCase(this.label) || "emergency".equalsIgnoreCase(this.label);
    }

    public int getTrackId() { return trackId; }
    public void setTrackId(int trackId) { this.trackId = trackId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public int getX() { return x; }
    public void setX(int x) {
        this.x = x;
        this.centerX = x + (width / 2.0);
    }

    public int getY() { return y; }
    public void setY(int y) {
        this.y = y;
        this.centerY = y + (height / 2.0);
    }

    public int getWidth() { return width; }
    public void setWidth(int width) {
        this.width = width;
        this.centerX = x + (width / 2.0);
    }

    public int getHeight() { return height; }
    public void setHeight(int height) {
        this.height = height;
        this.centerY = y + (height / 2.0);
    }

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }

    public Road getRoad() { return road; }
    public void setRoad(Road road) { this.road = road; }

    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }

    @Override
    public String toString() {
        return "DetectedVehicle{" +
                "trackId=" + trackId +
                ", label='" + label + '\'' +
                ", conf=" + String.format("%.2f", confidence) +
                ", bbox=[" + x + "," + y + "," + width + "," + height + "]" +
                ", road=" + road +
                ", sim=" + simulated +
                '}';
    }
}
