package com.traffic.traffic_backend.detection.camera;

import com.traffic.traffic_backend.traffic.Road;

/**
 * Region of Interest (ROI) mapping a spatial zone of the camera frame to a specific approaching Road.
 *
 * NOTE: An object detection neural network outputs bounding boxes [x, y, w, h] and class labels;
 * it CANNOT determine which road an ambulance belongs to without geometric camera calibration
 * or spatial region-of-interest (ROI) mapping.
 */
public class RoadRoi {

    private final Road road;
    private final double normX1;
    private final double normY1;
    private final double normX2;
    private final double normY2;

    /**
     * @param road The road approach (NORTH, SOUTH, EAST, WEST).
     * @param normX1 Normalized top-left X [0.0 - 1.0].
     * @param normY1 Normalized top-left Y [0.0 - 1.0].
     * @param normX2 Normalized bottom-right X [0.0 - 1.0].
     * @param normY2 Normalized bottom-right Y [0.0 - 1.0].
     */
    public RoadRoi(Road road, double normX1, double normY1, double normX2, double normY2) {
        this.road = road;
        this.normX1 = normX1;
        this.normY1 = normY1;
        this.normX2 = normX2;
        this.normY2 = normY2;
    }

    /**
     * Checks if a point (normalized 0.0 - 1.0) falls within this road's ROI.
     */
    public boolean containsNormalizedPoint(double x, double y) {
        return x >= normX1 && x <= normX2 && y >= normY1 && y <= normY2;
    }

    /**
     * Checks if pixel coordinates fall within this road's ROI given frame dimensions.
     */
    public boolean containsPixelPoint(double px, double py, int frameWidth, int frameHeight) {
        if (frameWidth <= 0 || frameHeight <= 0) return false;
        double nx = px / (double) frameWidth;
        double ny = py / (double) frameHeight;
        return containsNormalizedPoint(nx, ny);
    }

    public Road getRoad() { return road; }
    public double getNormX1() { return normX1; }
    public double getNormY1() { return normY1; }
    public double getNormX2() { return normX2; }
    public double getNormY2() { return normY2; }
}
