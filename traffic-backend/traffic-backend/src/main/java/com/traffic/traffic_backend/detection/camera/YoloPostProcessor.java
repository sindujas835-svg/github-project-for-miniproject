package com.traffic.traffic_backend.detection.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * YoloPostProcessor: Pure Java Post-Processing for YOLOv8 ONNX Output Tensors.
 *
 * EXPLANATION FOR CS STUDENTS:
 * An object detection model does not output a ready-to-display Java object with labels and boxes.
 * Instead, it returns a 3D float tensor (array of numbers) representing raw grid predictions.
 *
 * For YOLOv8, an input image of 640x640 produces an output tensor with shape:
 * [1, 4 + C, 8400]
 * where:
 * - 8400: Number of candidate anchor boxes generated across 3 feature map scales (80x80, 40x40, 20x20).
 * - 4: Bounding box parameters: [center_x, center_y, width, height]
 * - C: Class prediction probabilities (e.g., 0 = ambulance, 1 = car, 2 = bus, 3 = truck).
 *
 * This class performs three vital mathematical operations:
 * 1. TENSOR DECODING: Extracts (cx, cy, w, h) and class probabilities for each candidate box.
 * 2. CONFIDENCE FILTERING: Discards all predictions below the confidence threshold (e.g. 0.70).
 * 3. NON-MAXIMUM SUPPRESSION (NMS): Eliminates multiple overlapping boxes detecting the same vehicle
 *    by calculating Intersection-over-Union (IoU).
 */
public class YoloPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(YoloPostProcessor.class);

    // Default multi-class label mapping for traffic detection
    // Matches the custom trained multi-class vision model
    private static final String[] DEFAULT_LABELS = {
            "ambulance", "person", "car", "bus", "truck", "motorcycle"
    };

    private final String[] classLabels;
    private final float confidenceThreshold;
    private final float iouThreshold;

    public YoloPostProcessor(String[] classLabels, float confidenceThreshold, float iouThreshold) {
        this.classLabels = classLabels != null && classLabels.length > 0 ? classLabels : DEFAULT_LABELS;
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;
    }

    public YoloPostProcessor() {
        this(DEFAULT_LABELS, 0.70f, 0.45f);
    }

    /**
     * Decodes the raw 3D float array from ONNX Runtime into a list of filtered DetectedVehicle objects.
     *
     * @param rawOutput 3D float array from YOLOv8: shape [1][4 + C][8400] or [1][8400][4 + C].
     * @param originalWidth Width of the original input video frame (e.g., 1280 or 1920).
     * @param originalHeight Height of the original input video frame (e.g., 720 or 1080).
     * @param modelInputSize Input dimension of the model (e.g., 640).
     * @return List of verified detections after NMS suppression.
     */
    public List<DetectedVehicle> process(float[][][] rawOutput, int originalWidth, int originalHeight, int modelInputSize) {
        List<DetectedVehicle> candidates = new ArrayList<>();
        if (rawOutput == null || rawOutput.length == 0 || rawOutput[0].length == 0) {
            return candidates;
        }

        int dim1 = rawOutput[0].length;
        int dim2 = rawOutput[0][0].length;

        // YOLOv8 native shape is [1, 4 + C, 8400]
        boolean transposed = (dim1 < dim2);
        int numPredictions = transposed ? dim2 : dim1;
        int numFeatures = transposed ? dim1 : dim2;
        int numClasses = numFeatures - 4;

        double scaleX = (double) originalWidth / modelInputSize;
        double scaleY = (double) originalHeight / modelInputSize;

        for (int i = 0; i < numPredictions; i++) {
            // Extract bounding box [cx, cy, w, h]
            float cx = transposed ? rawOutput[0][0][i] : rawOutput[0][i][0];
            float cy = transposed ? rawOutput[0][1][i] : rawOutput[0][i][1];
            float w = transposed ? rawOutput[0][2][i] : rawOutput[0][i][2];
            float h = transposed ? rawOutput[0][3][i] : rawOutput[0][i][3];

            // Find class with highest probability
            float maxScore = 0.0f;
            int bestClassIdx = -1;

            for (int c = 0; c < numClasses; c++) {
                float score = transposed ? rawOutput[0][4 + c][i] : rawOutput[0][i][4 + c];
                if (score > maxScore) {
                    maxScore = score;
                    bestClassIdx = c;
                }
            }

            // Filter out low-confidence predictions
            if (maxScore >= confidenceThreshold && bestClassIdx >= 0) {
                // Convert (center_x, center_y, width, height) to (top_left_x, top_left_y, width, height)
                int pixelX = (int) Math.round((cx - (w / 2.0)) * scaleX);
                int pixelY = (int) Math.round((cy - (h / 2.0)) * scaleY);
                int pixelW = (int) Math.round(w * scaleX);
                int pixelH = (int) Math.round(h * scaleY);

                // Clip to frame boundary
                pixelX = Math.max(0, Math.min(pixelX, originalWidth - 1));
                pixelY = Math.max(0, Math.min(pixelY, originalHeight - 1));
                pixelW = Math.min(pixelW, originalWidth - pixelX);
                pixelH = Math.min(pixelH, originalHeight - pixelY);

                String label = (bestClassIdx < classLabels.length) ? classLabels[bestClassIdx] : "vehicle_" + bestClassIdx;
                log.info("RAW VISION DETECTION: Anchor #{} -> Class='{}' (Idx: {}), Confidence={}% [box: x={}, y={}, w={}, h={}]",
                        i, label, bestClassIdx, String.format("%.1f", maxScore * 100), pixelX, pixelY, pixelW, pixelH);

                candidates.add(new DetectedVehicle(
                        -1, // Track ID assigned later by CentroidTracker
                        label,
                        maxScore,
                        pixelX,
                        pixelY,
                        pixelW,
                        pixelH,
                        null, // Road mapped later by RoadRoi
                        false
                ));
            }
        }

        // Apply Non-Maximum Suppression (NMS) to eliminate duplicate overlapping boxes
        return applyNms(candidates, iouThreshold);
    }

    /**
     * Non-Maximum Suppression:
     * When a vehicle is detected, the model often produces multiple slightly shifted boxes around it.
     * NMS keeps only the highest-scoring box and deletes overlapping boxes whose IoU exceeds the threshold.
     */
    public List<DetectedVehicle> applyNms(List<DetectedVehicle> boxes, float iouThresh) {
        if (boxes.isEmpty()) return boxes;

        // Group detections by class label (we do NMS per class)
        Map<String, List<DetectedVehicle>> byClass = new HashMap<>();
        for (DetectedVehicle box : boxes) {
            byClass.computeIfAbsent(box.getLabel(), k -> new ArrayList<>()).add(box);
        }

        List<DetectedVehicle> finalDetections = new ArrayList<>();

        for (List<DetectedVehicle> classBoxes : byClass.values()) {
            // Sort boxes by confidence in descending order
            classBoxes.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));

            List<DetectedVehicle> selected = new ArrayList<>();
            while (!classBoxes.isEmpty()) {
                DetectedVehicle best = classBoxes.remove(0);
                selected.add(best);

                Iterator<DetectedVehicle> iter = classBoxes.iterator();
                while (iter.hasNext()) {
                    DetectedVehicle other = iter.next();
                    if (calculateIoU(best, other) > iouThresh) {
                        iter.remove(); // Suppress redundant overlapping box
                    }
                }
            }
            finalDetections.addAll(selected);
        }

        return finalDetections;
    }

    /**
     * Calculates the Intersection-over-Union (IoU) of two bounding boxes.
     * IoU = Area(Overlap) / Area(Union)
     */
    public static double calculateIoU(DetectedVehicle a, DetectedVehicle b) {
        int x1 = Math.max(a.getX(), b.getX());
        int y1 = Math.max(a.getY(), b.getY());
        int x2 = Math.min(a.getX() + a.getWidth(), b.getX() + b.getWidth());
        int y2 = Math.min(a.getY() + a.getHeight(), b.getY() + b.getHeight());

        int intersectionWidth = Math.max(0, x2 - x1);
        int intersectionHeight = Math.max(0, y2 - y1);
        double intersectionArea = (double) intersectionWidth * intersectionHeight;

        double areaA = (double) a.getWidth() * a.getHeight();
        double areaB = (double) b.getWidth() * b.getHeight();
        double unionArea = areaA + areaB - intersectionArea;

        if (unionArea <= 0.0) return 0.0;
        return intersectionArea / unionArea;
    }
}
