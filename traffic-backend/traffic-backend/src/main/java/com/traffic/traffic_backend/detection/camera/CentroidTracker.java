package com.traffic.traffic_backend.detection.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * CentroidTracker: Multi-Object Centroid Tracking in pure Java.
 *
 * EXPLANATION FOR CS STUDENTS:
 * Raw object detectors (like YOLO or RF-DETR) are "stateless": they detect objects
 * frame-by-frame with no memory of what was detected in the previous frame.
 * If an ambulance stays in view for 50 frames (5 seconds), a stateless detector
 * would report 50 separate detections, potentially creating 50 database records or trigger alerts!
 *
 * The Centroid Tracker solves this by:
 * 1. Computing the center point (centroid) of every detected bounding box: (cx = x + w/2, cy = y + h/2).
 * 2. Calculating the Euclidean distance between all existing centroids and new detections:
 *    distance = sqrt((x2 - x1)^2 + (y2 - y1)^2)
 * 3. Pairing each existing track to its closest detection using minimum distance greedy matching.
 * 4. Preserving a persistent `trackId` across frames.
 * 5. Maintaining a "disappeared" counter: if an object is temporarily occluded or missed
 *    for a few frames, its trackId is kept until `maxDisappeared` frames have elapsed.
 */
public class CentroidTracker {

    private static final Logger log = LoggerFactory.getLogger(CentroidTracker.class);

    private final int maxDisappeared;
    private final double maxDistance;

    private int nextTrackId = 1;
    private final Map<Integer, TrackedObject> objects = new LinkedHashMap<>();
    private final Map<Integer, Integer> disappeared = new HashMap<>();

    public static class TrackedObject {
        private final int trackId;
        private DetectedVehicle vehicle;
        private double centroidX;
        private double centroidY;

        public TrackedObject(int trackId, DetectedVehicle vehicle) {
            this.trackId = trackId;
            this.vehicle = vehicle;
            this.centroidX = vehicle.getCenterX();
            this.centroidY = vehicle.getCenterY();
        }

        public int getTrackId() { return trackId; }
        public DetectedVehicle getVehicle() { return vehicle; }
        public void setVehicle(DetectedVehicle vehicle) {
            this.vehicle = vehicle;
            this.centroidX = vehicle.getCenterX();
            this.centroidY = vehicle.getCenterY();
        }
        public double getCentroidX() { return centroidX; }
        public double getCentroidY() { return centroidY; }
    }

    /**
     * @param maxDisappeared Number of consecutive frames an object can be missing before deregistration.
     * @param maxDistance Maximum Euclidean distance (in pixels) to consider matching the same object.
     */
    public CentroidTracker(int maxDisappeared, double maxDistance) {
        this.maxDisappeared = maxDisappeared;
        this.maxDistance = maxDistance;
    }

    public CentroidTracker() {
        this(15, 120.0); // Default: 15 frames grace period, 120px max movement per frame
    }

    /**
     * Updates the tracker with the list of raw detections from the current frame.
     *
     * @param rawDetections Bounding boxes detected in the current frame.
     * @return List of tracked vehicles with persistent track IDs.
     */
    public synchronized List<DetectedVehicle> update(List<DetectedVehicle> rawDetections) {
        // Case 1: No detections in current frame
        if (rawDetections == null || rawDetections.isEmpty()) {
            List<Integer> toRemove = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : disappeared.entrySet()) {
                int id = entry.getKey();
                int count = entry.getValue() + 1;
                disappeared.put(id, count);
                if (count > maxDisappeared) {
                    toRemove.add(id);
                }
            }
            for (Integer id : toRemove) {
                deregister(id);
            }
            return getActiveVehicles();
        }

        // Case 2: No existing tracked objects - register all new detections
        if (objects.isEmpty()) {
            for (DetectedVehicle detection : rawDetections) {
                register(detection);
            }
            return getActiveVehicles();
        }

        // Case 3: Match existing objects with incoming detections
        List<Integer> objectIds = new ArrayList<>(objects.keySet());
        List<TrackedObject> objectList = new ArrayList<>(objects.values());

        int numObjects = objectList.size();
        int numDetections = rawDetections.size();

        // Build Euclidean distance matrix: [numObjects][numDetections]
        double[][] distanceMatrix = new double[numObjects][numDetections];
        for (int i = 0; i < numObjects; i++) {
            TrackedObject obj = objectList.get(i);
            for (int j = 0; j < numDetections; j++) {
                DetectedVehicle det = rawDetections.get(j);
                double dx = obj.getCentroidX() - det.getCenterX();
                double dy = obj.getCentroidY() - det.getCenterY();
                distanceMatrix[i][j] = Math.sqrt(dx * dx + dy * dy);
            }
        }

        // Greedy matching: match smallest distance pairs first
        Set<Integer> usedObjects = new HashSet<>();
        Set<Integer> usedDetections = new HashSet<>();

        // Keep finding the minimum distance entry until no valid match is found
        while (usedObjects.size() < numObjects && usedDetections.size() < numDetections) {
            double minDistance = Double.MAX_VALUE;
            int bestObjIdx = -1;
            int bestDetIdx = -1;

            for (int i = 0; i < numObjects; i++) {
                if (usedObjects.contains(i)) continue;
                for (int j = 0; j < numDetections; j++) {
                    if (usedDetections.contains(j)) continue;
                    if (distanceMatrix[i][j] < minDistance) {
                        minDistance = distanceMatrix[i][j];
                        bestObjIdx = i;
                        bestDetIdx = j;
                    }
                }
            }

            // If the closest distance exceeds maxDistance threshold, stop matching
            if (minDistance > maxDistance || bestObjIdx == -1 || bestDetIdx == -1) {
                break;
            }

            // Pair object with detection
            usedObjects.add(bestObjIdx);
            usedDetections.add(bestDetIdx);

            int trackId = objectIds.get(bestObjIdx);
            DetectedVehicle matchedDet = rawDetections.get(bestDetIdx);
            matchedDet.setTrackId(trackId);

            TrackedObject obj = objects.get(trackId);
            obj.setVehicle(matchedDet);
            disappeared.put(trackId, 0); // Reset disappeared counter
        }

        // Increment disappeared counter for unmatched existing objects
        List<Integer> toDeregister = new ArrayList<>();
        for (int i = 0; i < numObjects; i++) {
            if (!usedObjects.contains(i)) {
                int trackId = objectIds.get(i);
                int count = disappeared.getOrDefault(trackId, 0) + 1;
                disappeared.put(trackId, count);
                if (count > maxDisappeared) {
                    toDeregister.add(trackId);
                }
            }
        }
        for (Integer id : toDeregister) {
            deregister(id);
        }

        // Register remaining unmatched detections as brand-new objects
        for (int j = 0; j < numDetections; j++) {
            if (!usedDetections.contains(j)) {
                register(rawDetections.get(j));
            }
        }

        return getActiveVehicles();
    }

    private void register(DetectedVehicle detection) {
        int trackId = nextTrackId++;
        detection.setTrackId(trackId);
        objects.put(trackId, new TrackedObject(trackId, detection));
        disappeared.put(trackId, 0);
        log.debug("Registered new track ID: {} ({})", trackId, detection.getLabel());
    }

    private void deregister(int trackId) {
        objects.remove(trackId);
        disappeared.remove(trackId);
        log.debug("Deregistered track ID: {} (exceeded max disappeared frames)", trackId);
    }

    public synchronized List<DetectedVehicle> getActiveVehicles() {
        List<DetectedVehicle> list = new ArrayList<>();
        for (TrackedObject obj : objects.values()) {
            list.add(obj.getVehicle());
        }
        return list;
    }

    public synchronized void reset() {
        objects.clear();
        disappeared.clear();
        nextTrackId = 1;
    }
}
