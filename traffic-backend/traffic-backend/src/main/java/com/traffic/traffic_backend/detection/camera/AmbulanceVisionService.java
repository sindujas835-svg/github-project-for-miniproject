package com.traffic.traffic_backend.detection.camera;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.traffic.traffic_backend.config.CameraConfig;
import com.traffic.traffic_backend.dto.CameraDetectionDto;
import com.traffic.traffic_backend.entity.AmbulanceDetection;
import com.traffic.traffic_backend.repository.AmbulanceDetectionRepository;
import com.traffic.traffic_backend.service.TelemetryLoggingService;
import com.traffic.traffic_backend.traffic.Road;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;

/**
 * AmbulanceVisionService: Real-Time Computer Vision and Object Detection Engine.
 *
 * Designed for CS students:
 * 1. OpenCV Java captures live frames from a connected webcam or RTSP camera stream.
 * 2. Frames are resized to 640x640, normalized, and converted into NCHW format float tensors.
 * 3. Microsoft ONNX Runtime Java executes high-speed deep learning inference (e.g. YOLOv8 or RF-DETR).
 * 4. YoloPostProcessor applies confidence filtering and Non-Maximum Suppression (NMS).
 * 5. CentroidTracker maintains smooth, persistent multi-object tracking across frames.
 * 6. RoadRoi maps bounding box centroids to approaching intersection roads (NORTH, SOUTH, EAST, WEST).
 *
 * TRANSPARENT SIMULATION FALLBACK:
 * If no physical webcam is plugged in or the ONNX model file is missing, the service does NOT crash.
 * Instead, it enters a transparent, well-documented simulation mode where `is_simulated = true`.
 * It NEVER fakes real AI detection results as genuine camera output.
 */
@Service
public class AmbulanceVisionService {

    private static final Logger log = LoggerFactory.getLogger(AmbulanceVisionService.class);

    private final CameraConfig cameraConfig;
    private final AmbulanceDetectionRepository ambulanceRepo;
    private final TelemetryLoggingService telemetryService;
    private final SimpMessagingTemplate messagingTemplate;

    private final CentroidTracker centroidTracker = new CentroidTracker(15, 120.0);
    private final YoloPostProcessor postProcessor;
    private final List<AmbulanceDetectionListener> listeners = new CopyOnWriteArrayList<>();

    // State tracking
    private boolean openCvLoaded = false;
    private boolean modelLoaded = false;
    private boolean cameraActive = false;
    private boolean simulationMode = true;

    private OrtEnvironment ortEnv;
    private OrtSession ortSession;
    private VideoCapture videoCapture;
    private ScheduledExecutorService frameExecutor;

    private final List<DetectedVehicle> latestDetections = new CopyOnWriteArrayList<>();
    private final Map<Integer, Long> lastPersistedByTrack = new ConcurrentHashMap<>();

    public AmbulanceVisionService(
            CameraConfig cameraConfig,
            AmbulanceDetectionRepository ambulanceRepo,
            TelemetryLoggingService telemetryService,
            SimpMessagingTemplate messagingTemplate) {
        this.cameraConfig = cameraConfig;
        this.ambulanceRepo = ambulanceRepo;
        this.telemetryService = telemetryService;
        this.messagingTemplate = messagingTemplate;
        this.postProcessor = new YoloPostProcessor(
                null,
                cameraConfig.getConfidenceThreshold(),
                cameraConfig.getIouThreshold()
        );
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Computer Vision & Ambulance Detection Engine...");

        // Step 1: Initialize OpenCV native libraries
        try {
            nu.pattern.OpenCV.loadLocally();
            openCvLoaded = true;
            log.info("OpenCV 4.9 native binaries successfully loaded.");
        } catch (Throwable t) {
            log.warn("OpenCV native library could not be loaded: {}. Running in headless vision mode.", t.getMessage());
            openCvLoaded = false;
        }

        // Step 2: Initialize Microsoft ONNX Runtime Session if model exists
        try {
            File modelFile = new File(cameraConfig.getModelPath());
            if (modelFile.exists() && modelFile.isFile()) {
                ortEnv = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
                ortSession = ortEnv.createSession(modelFile.getAbsolutePath(), sessionOptions);
                modelLoaded = true;
                log.info("ONNX Runtime session initialized with model: {}", modelFile.getAbsolutePath());
            } else {
                log.info("No ONNX vision model found at '{}'. Vision engine ready with transparent simulation fallback.",
                        cameraConfig.getModelPath());
                modelLoaded = false;
            }
        } catch (Throwable t) {
            log.warn("ONNX Runtime initialization skipped: {}. Falling back to simulation mode.", t.getMessage());
            modelLoaded = false;
        }

        // Step 3: Check camera hardware across multiple backends (DirectShow, MSMF, Default)
        if (openCvLoaded && cameraConfig.isEnabled()) {
            videoCapture = attemptOpenPhysicalCamera(cameraConfig.getDeviceIndex());
            if (videoCapture != null && videoCapture.isOpened()) {
                cameraActive = true;
                simulationMode = false;
                log.info("Physical camera successfully opened and verified! Starting live video capture loop.");
                startFrameProcessingLoop();
            } else {
                cameraActive = false;
                simulationMode = true;
                log.info("Physical camera could not be opened on available backends. Vision engine operating in TRANSPARENT SIMULATION FALLBACK MODE (is_simulated = true).");
            }
        } else {
            simulationMode = true;
            log.info("Vision engine active in TRANSPARENT SIMULATION FALLBACK MODE (is_simulated = true).");
        }
    }

    /**
     * Attempts to open the physical camera across multiple Windows backends:
     * DirectShow (CAP_DSHOW = 700), Media Foundation (CAP_MSMF = 1400), and Default (CAP_ANY = 0).
     */
    private VideoCapture attemptOpenPhysicalCamera(int preferredIndex) {
        int[] backends = new int[]{Videoio.CAP_DSHOW, Videoio.CAP_MSMF, Videoio.CAP_ANY};
        int[] indices = new int[]{preferredIndex, 0, 1};

        for (int idx : indices) {
            for (int backend : backends) {
                try {
                    VideoCapture cap = new VideoCapture(idx, backend);
                    if (cap.isOpened()) {
                        Mat testFrame = new Mat();
                        if (cap.read(testFrame) && !testFrame.empty()) {
                            log.info("Physical camera frame read confirmed: deviceIndex={}, backend={}, resolution={}x{}",
                                    idx, backend, testFrame.cols(), testFrame.rows());
                            testFrame.release();
                            return cap;
                        }
                        testFrame.release();
                    }
                    cap.release();
                } catch (Throwable t) {
                    log.debug("Probing camera idx {} backend {}: {}", idx, backend, t.getMessage());
                }
            }
        }
        return null;
    }

    @PreDestroy
    public void destroy() {
        if (frameExecutor != null && !frameExecutor.isShutdown()) {
            frameExecutor.shutdown();
        }
        if (videoCapture != null && videoCapture.isOpened()) {
            videoCapture.release();
        }
        try {
            if (ortSession != null) ortSession.close();
            if (ortEnv != null) ortEnv.close();
        } catch (Exception e) {
            log.error("Error closing ONNX runtime resources: {}", e.getMessage());
        }
    }

    /**
     * Subscribes a listener to confirmed ambulance detection events.
     */
    public void addListener(AmbulanceDetectionListener listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    /**
     * Dedicated loop capturing video frames at target FPS and running ONNX inference.
     */
    private void startFrameProcessingLoop() {
        int delayMs = 1000 / Math.max(1, cameraConfig.getFps());
        frameExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CameraVisionWorker");
            t.setDaemon(true);
            return t;
        });

        frameExecutor.scheduleWithFixedDelay(this::processSingleFrame, 500, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Processes a single camera frame through OpenCV preprocessing and ONNX inference.
     */
    private void processSingleFrame() {
        if (!cameraActive || videoCapture == null || !videoCapture.isOpened()) return;

        Mat frame = new Mat();
        try {
            if (!videoCapture.read(frame) || frame.empty()) {
                return;
            }

            int origWidth = frame.cols();
            int origHeight = frame.rows();
            int modelSize = cameraConfig.getInputSize();

            List<DetectedVehicle> rawDetections;

            if (modelLoaded && ortSession != null) {
                // Resize frame to 640x640 for model input
                Mat resized = new Mat();
                Imgproc.resize(frame, resized, new Size(modelSize, modelSize));

                // Convert BGR to RGB
                Mat rgb = new Mat();
                Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_BGR2RGB);

                // Prepare NCHW float tensor: shape [1, 3, 640, 640]
                FloatBuffer buffer = FloatBuffer.allocate(1 * 3 * modelSize * modelSize);
                byte[] pixelData = new byte[modelSize * modelSize * 3];
                rgb.get(0, 0, pixelData);

                int channelStride = modelSize * modelSize;
                for (int i = 0; i < modelSize * modelSize; i++) {
                    int r = pixelData[i * 3] & 0xFF;
                    int g = pixelData[i * 3 + 1] & 0xFF;
                    int b = pixelData[i * 3 + 2] & 0xFF;
                    // Normalized to [0.0, 1.0]
                    buffer.put(i, r / 255.0f);
                    buffer.put(channelStride + i, g / 255.0f);
                    buffer.put(channelStride * 2 + i, b / 255.0f);
                }

                OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, buffer, new long[]{1, 3, modelSize, modelSize});
                String inputName = ortSession.getInputNames().iterator().next();
                try (OrtSession.Result result = ortSession.run(Collections.singletonMap(inputName, inputTensor))) {
                    float[][][] outputArray = (float[][][]) result.get(0).getValue();
                    rawDetections = postProcessor.process(outputArray, origWidth, origHeight, modelSize);
                }
                inputTensor.close();
            } else {
                rawDetections = Collections.emptyList();
            }

            // Map each detected vehicle to an approaching Road via RoadRoi
            for (DetectedVehicle det : rawDetections) {
                Road mappedRoad = determineRoad(det.getCenterX(), det.getCenterY(), origWidth, origHeight);
                det.setRoad(mappedRoad);
            }

            // Update Centroid Tracker to preserve persistent track IDs
            List<DetectedVehicle> tracked = centroidTracker.update(rawDetections);

            this.latestDetections.clear();
            this.latestDetections.addAll(tracked);

            // Handle detections and trigger verification if ambulance detected
            handleTrackedVehicles(tracked, false);

            // Broadcast detections via WebSocket
            broadcastDetections(tracked);

        } catch (Throwable t) {
            log.error("Error processing camera frame: {}", t.getMessage());
        } finally {
            frame.release();
        }
    }

    /**
     * Determines which road approach contains the given pixel coordinate using configured ROIs.
     */
    public Road determineRoad(double pixelX, double pixelY, int frameWidth, int frameHeight) {
        for (Road road : Road.values()) {
            RoadRoi roi = cameraConfig.getRoi(road);
            if (roi != null && roi.containsPixelPoint(pixelX, pixelY, frameWidth, frameHeight)) {
                return road;
            }
        }
        return null;
    }

    /**
     * Evaluates tracked vehicles, persists confirmed ambulance detections, and dispatches events.
     */
    private void handleTrackedVehicles(List<DetectedVehicle> vehicles, boolean isSimulated) {
        long now = System.currentTimeMillis();

        for (DetectedVehicle vehicle : vehicles) {
            if (vehicle.isAmbulance() && vehicle.getRoad() != null &&
                    vehicle.getConfidence() >= cameraConfig.getConfidenceThreshold()) {

                // Throttle persistence per track ID to once every 2 seconds
                Long lastSaved = lastPersistedByTrack.get(vehicle.getTrackId());
                if (lastSaved == null || (now - lastSaved) > 2000) {
                    lastPersistedByTrack.put(vehicle.getTrackId(), now);

                    AmbulanceDetection entity = new AmbulanceDetection(
                            "CAM-" + (isSimulated ? "SIM-01" : "LIVE-01"),
                            vehicle.getRoad(),
                            "TRACK-" + vehicle.getTrackId(),
                            vehicle.getConfidence(),
                            vehicle.getX(),
                            vehicle.getY(),
                            vehicle.getWidth(),
                            vehicle.getHeight(),
                            isSimulated
                    );
                    ambulanceRepo.save(entity);

                    log.info("CONFIRMED AMBULANCE CAMERA DETECTION: Track {} on Road {} (Confidence: {}%, Simulated: {})",
                            vehicle.getTrackId(), vehicle.getRoad(),
                            String.format("%.1f", vehicle.getConfidence() * 100), isSimulated);

                    telemetryService.logSystemEvent("CAMERA_DETECTION", "AMBULANCE",
                            String.format("Ambulance detected on %s (Confidence: %.2f, Track: %d, Simulated: %b)",
                                    vehicle.getRoad(), vehicle.getConfidence(), vehicle.getTrackId(), isSimulated));

                    // Notify listeners (e.g. EmergencyVerificationService)
                    for (AmbulanceDetectionListener listener : listeners) {
                        try {
                            listener.onAmbulanceDetected(entity);
                        } catch (Exception e) {
                            log.error("Error in ambulance detection listener: {}", e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Transparent Simulation Method:
     * Injects a simulated ambulance detection on a specified road.
     * All database entries and broadcasts are clearly marked with `is_simulated = true`.
     */
    public AmbulanceDetection simulateAmbulanceDetection(Road road, float confidence, int x, int y, int width, int height) {
        log.info("Simulating ambulance detection on approach road {} (Confidence: {})", road, confidence);

        int trackId = (int) (1000 + (System.currentTimeMillis() % 9000));
        DetectedVehicle simVehicle = new DetectedVehicle(
                trackId,
                "ambulance",
                confidence,
                x, y, width, height,
                road,
                true // isSimulated
        );

        this.latestDetections.add(simVehicle);

        AmbulanceDetection entity = new AmbulanceDetection(
                "CAM-SIM-01",
                road,
                "TRACK-" + trackId,
                confidence,
                x, y, width, height,
                true // isSimulated
        );
        entity = ambulanceRepo.save(entity);

        telemetryService.logSystemEvent("CAMERA_DETECTION", "AMBULANCE_SIMULATED",
                String.format("Simulated ambulance on %s (Conf: %.2f, Track: %d)", road, confidence, trackId));

        broadcastDetections(List.of(simVehicle));

        // Notify downstream listeners
        for (AmbulanceDetectionListener listener : listeners) {
            try {
                listener.onAmbulanceDetected(entity);
            } catch (Exception e) {
                log.error("Error notifying listener for simulated detection: {}", e.getMessage());
            }
        }

        return entity;
    }

    /**
     * Runs actual model inference on an uploaded image or webcam frame bytes.
     * Uses the loaded ONNX model, decodes bounding boxes, maps road ROI, and dispatches events.
     */
    public List<DetectedVehicle> processImageBytes(byte[] imageBytes, Road roadOverride) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Collections.emptyList();
        }

        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes);
            java.awt.image.BufferedImage bImage = javax.imageio.ImageIO.read(bais);
            if (bImage == null) {
                log.warn("Could not decode image bytes via ImageIO.");
                return Collections.emptyList();
            }

            int origWidth = bImage.getWidth();
            int origHeight = bImage.getHeight();
            int modelSize = cameraConfig.getInputSize(); // 640

            java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(modelSize, modelSize, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = resized.createGraphics();
            g.drawImage(bImage, 0, 0, modelSize, modelSize, null);
            g.dispose();

            List<DetectedVehicle> rawDetections = new ArrayList<>();

            if (modelLoaded && ortSession != null && ortEnv != null) {
                FloatBuffer buffer = FloatBuffer.allocate(1 * 3 * modelSize * modelSize);
                int channelStride = modelSize * modelSize;

                for (int y = 0; y < modelSize; y++) {
                    for (int x = 0; x < modelSize; x++) {
                        int rgb = resized.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF;
                        int gCol = (rgb >> 8) & 0xFF;
                        int bCol = rgb & 0xFF;

                        int idx = y * modelSize + x;
                        buffer.put(idx, r / 255.0f);
                        buffer.put(channelStride + idx, gCol / 255.0f);
                        buffer.put(channelStride * 2 + idx, bCol / 255.0f);
                    }
                }

                OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, buffer, new long[]{1, 3, modelSize, modelSize});
                String inputName = ortSession.getInputNames().iterator().next();

                try (OrtSession.Result result = ortSession.run(Collections.singletonMap(inputName, inputTensor))) {
                    float[][][] outputArray = (float[][][]) result.get(0).getValue();
                    rawDetections = postProcessor.process(outputArray, origWidth, origHeight, modelSize);
                }
                inputTensor.close();
            } else {
                log.warn("Ambulance vision model not loaded or ONNX session unavailable. Returning no detections.");
                rawDetections = Collections.emptyList();
            }

            int seq = (int)(System.currentTimeMillis() % 100000);
            for (int i = 0; i < rawDetections.size(); i++) {
                DetectedVehicle det = rawDetections.get(i);
                Road road = roadOverride != null ? roadOverride : determineRoad(det.getCenterX(), det.getCenterY(), origWidth, origHeight);
                if (road == null) road = Road.NORTH;
                det.setRoad(road);
                if (det.getTrackId() <= 0) {
                    det.setTrackId(seq + i + 1);
                }
            }

            this.latestDetections.clear();
            this.latestDetections.addAll(rawDetections);
            handleTrackedVehicles(rawDetections, false);
            broadcastDetections(rawDetections);

            return rawDetections;
        } catch (Exception e) {
            log.error("Error processing uploaded image bytes: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Broadcasts current detections to WebSocket subscribers for live UI bounding boxes.
     */
    public void broadcastDetections(List<DetectedVehicle> vehicles) {
        List<CameraDetectionDto> dtos = new ArrayList<>();
        for (DetectedVehicle v : vehicles) {
            dtos.add(new CameraDetectionDto(
                    v.getTrackId(),
                    v.getLabel(),
                    v.getConfidence(),
                    v.getX(),
                    v.getY(),
                    v.getWidth(),
                    v.getHeight(),
                    v.getCenterX(),
                    v.getCenterY(),
                    v.getRoad(),
                    v.isAmbulance(),
                    v.isSimulated() || simulationMode
            ));
        }

        messagingTemplate.convertAndSend("/topic/camera-detections", dtos);
    }

    // Getters
    public boolean isOpenCvLoaded() { return openCvLoaded; }
    public boolean isModelLoaded() { return modelLoaded; }
    public boolean isCameraActive() { return cameraActive; }
    public boolean isSimulationMode() { return simulationMode; }
    public List<DetectedVehicle> getLatestDetections() { return new ArrayList<>(latestDetections); }
    public CameraConfig getCameraConfig() { return cameraConfig; }
}
