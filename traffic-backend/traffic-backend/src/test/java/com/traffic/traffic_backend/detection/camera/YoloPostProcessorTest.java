package com.traffic.traffic_backend.detection.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YoloPostProcessorTest {

    @Test
    void testCalculateIoUExactMatch() {
        DetectedVehicle boxA = new DetectedVehicle(1, "car", 0.9f, 10, 10, 50, 50, null, false);
        DetectedVehicle boxB = new DetectedVehicle(2, "car", 0.9f, 10, 10, 50, 50, null, false);

        double iou = YoloPostProcessor.calculateIoU(boxA, boxB);
        assertEquals(1.0, iou, 0.0001, "Identical boxes must have IoU = 1.0");
    }

    @Test
    void testCalculateIoUNoOverlap() {
        DetectedVehicle boxA = new DetectedVehicle(1, "car", 0.9f, 0, 0, 10, 10, null, false);
        DetectedVehicle boxB = new DetectedVehicle(2, "car", 0.9f, 50, 50, 10, 10, null, false);

        double iou = YoloPostProcessor.calculateIoU(boxA, boxB);
        assertEquals(0.0, iou, 0.0001, "Disjoint boxes must have IoU = 0.0");
    }

    @Test
    void testNmsSuppression() {
        YoloPostProcessor processor = new YoloPostProcessor(new String[]{"ambulance", "car"}, 0.50f, 0.40f);

        // Two overlapping detections of the same ambulance
        DetectedVehicle highConf = new DetectedVehicle(1, "ambulance", 0.95f, 100, 100, 60, 60, null, false);
        DetectedVehicle lowConf = new DetectedVehicle(2, "ambulance", 0.70f, 105, 105, 60, 60, null, false);

        List<DetectedVehicle> result = processor.applyNms(List.of(highConf, lowConf), 0.40f);

        assertEquals(1, result.size(), "NMS must suppress the lower confidence duplicate box");
        assertEquals(0.95f, result.get(0).getConfidence(), 0.001);
    }

    @Test
    void testRoadRoiMapping() {
        RoadRoi northRoi = new RoadRoi(com.traffic.traffic_backend.traffic.Road.NORTH, 0.25, 0.0, 0.75, 0.40);

        // Point inside North ROI (e.g. x = 0.5, y = 0.2)
        assertTrue(northRoi.containsNormalizedPoint(0.5, 0.2));

        // Point outside North ROI (e.g. x = 0.5, y = 0.8)
        assertFalse(northRoi.containsNormalizedPoint(0.5, 0.8));

        // Pixel coordinates with 1000x1000 frame
        assertTrue(northRoi.containsPixelPoint(500, 200, 1000, 1000));
        assertFalse(northRoi.containsPixelPoint(500, 800, 1000, 1000));
    }
}
