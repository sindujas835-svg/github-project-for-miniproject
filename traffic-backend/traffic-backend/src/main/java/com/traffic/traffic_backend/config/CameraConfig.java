package com.traffic.traffic_backend.config;

import com.traffic.traffic_backend.detection.camera.RoadRoi;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration properties for the live camera vision system.
 * Configured via application.properties or default presets.
 */
@Configuration
@ConfigurationProperties(prefix = "traffic.camera")
public class CameraConfig {

    private boolean enabled = true;
    private int deviceIndex = 0;
    private String modelPath = "models/vision/yolov8n_ambulance.onnx";
    private float confidenceThreshold = 0.70f;
    private float iouThreshold = 0.45f;
    private int inputSize = 640;
    private int fps = 10;
    private boolean simulationFallbackEnabled = true;

    // Geometric Regions of Interest (ROI) for each approaching road on the camera view
    // Normalized coordinates [0.0 - 1.0] representing:
    // NORTH: Approaching from Top
    // SOUTH: Approaching from Bottom
    // EAST: Approaching from Right
    // WEST: Approaching from Left
    private final Map<Road, RoadRoi> rois = new EnumMap<>(Road.class);

    public CameraConfig() {
        // Initialize default intersection quadrant ROIs (calibrated for standard intersection cameras)
        rois.put(Road.NORTH, new RoadRoi(Road.NORTH, 0.25, 0.00, 0.75, 0.40));
        rois.put(Road.SOUTH, new RoadRoi(Road.SOUTH, 0.25, 0.60, 0.75, 1.00));
        rois.put(Road.WEST,  new RoadRoi(Road.WEST,  0.00, 0.25, 0.40, 0.75));
        rois.put(Road.EAST,  new RoadRoi(Road.EAST,  0.60, 0.25, 1.00, 0.75));
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getDeviceIndex() { return deviceIndex; }
    public void setDeviceIndex(int deviceIndex) { this.deviceIndex = deviceIndex; }

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }

    public float getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(float confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public float getIouThreshold() { return iouThreshold; }
    public void setIouThreshold(float iouThreshold) { this.iouThreshold = iouThreshold; }

    public int getInputSize() { return inputSize; }
    public void setInputSize(int inputSize) { this.inputSize = inputSize; }

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }

    public boolean isSimulationFallbackEnabled() { return simulationFallbackEnabled; }
    public void setSimulationFallbackEnabled(boolean simulationFallbackEnabled) { this.simulationFallbackEnabled = simulationFallbackEnabled; }

    public Map<Road, RoadRoi> getRois() { return rois; }

    public RoadRoi getRoi(Road road) {
        return rois.get(road);
    }

    public void setRoi(Road road, double normX1, double normY1, double normX2, double normY2) {
        rois.put(road, new RoadRoi(road, normX1, normY1, normX2, normY2));
    }
}
