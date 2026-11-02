package com.traffic.traffic_backend.detection.camera;

import com.traffic.traffic_backend.traffic.Road;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CentroidTrackerTest {

    private CentroidTracker tracker;

    @BeforeEach
    void setUp() {
        // maxDisappeared = 3 frames, maxDistance = 100 pixels
        tracker = new CentroidTracker(3, 100.0);
    }

    @Test
    void testRegisterNewDetections() {
        DetectedVehicle v1 = new DetectedVehicle(-1, "ambulance", 0.95f, 100, 100, 50, 50, Road.NORTH, false);
        DetectedVehicle v2 = new DetectedVehicle(-1, "car", 0.88f, 300, 300, 40, 40, Road.SOUTH, false);

        List<DetectedVehicle> tracked = tracker.update(List.of(v1, v2));

        assertEquals(2, tracked.size());
        assertEquals(1, tracked.get(0).getTrackId());
        assertEquals(2, tracked.get(1).getTrackId());
    }

    @Test
    void testTrackContinuityAcrossFrames() {
        // Frame 1: Vehicle at (100, 100)
        DetectedVehicle frame1 = new DetectedVehicle(-1, "ambulance", 0.95f, 100, 100, 50, 50, Road.NORTH, false);
        List<DetectedVehicle> tracked1 = tracker.update(List.of(frame1));
        int assignedId = tracked1.get(0).getTrackId();

        // Frame 2: Vehicle moved slightly to (110, 105) (distance = ~11 px < 100 px threshold)
        DetectedVehicle frame2 = new DetectedVehicle(-1, "ambulance", 0.94f, 110, 105, 50, 50, Road.NORTH, false);
        List<DetectedVehicle> tracked2 = tracker.update(List.of(frame2));

        assertEquals(1, tracked2.size());
        assertEquals(assignedId, tracked2.get(0).getTrackId(), "Track ID must remain continuous across frames");
        assertEquals(110, tracked2.get(0).getX());
    }

    @Test
    void testDeregistrationAfterMaxDisappeared() {
        DetectedVehicle v = new DetectedVehicle(-1, "ambulance", 0.95f, 100, 100, 50, 50, Road.NORTH, false);
        tracker.update(List.of(v));
        assertEquals(1, tracker.getActiveVehicles().size());

        // Frame 2: Empty (disappeared = 1)
        tracker.update(List.of());
        assertEquals(1, tracker.getActiveVehicles().size());

        // Frame 3: Empty (disappeared = 2)
        tracker.update(List.of());
        assertEquals(1, tracker.getActiveVehicles().size());

        // Frame 4: Empty (disappeared = 3)
        tracker.update(List.of());
        assertEquals(1, tracker.getActiveVehicles().size());

        // Frame 5: Empty (disappeared = 4 > maxDisappeared: deregistered)
        tracker.update(List.of());
        assertEquals(0, tracker.getActiveVehicles().size(), "Vehicle must be deregistered after exceeding maxDisappeared");
    }
}
