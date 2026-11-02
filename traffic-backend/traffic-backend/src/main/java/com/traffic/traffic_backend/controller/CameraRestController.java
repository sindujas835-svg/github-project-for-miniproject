package com.traffic.traffic_backend.controller;

import com.traffic.traffic_backend.config.CameraConfig;
import com.traffic.traffic_backend.detection.camera.AmbulanceVisionService;
import com.traffic.traffic_backend.detection.camera.DetectedVehicle;
import com.traffic.traffic_backend.detection.camera.RoadRoi;
import com.traffic.traffic_backend.dto.CameraDetectionDto;
import com.traffic.traffic_backend.entity.AmbulanceDetection;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller exposing camera status, live detections, ROI geometry,
 * and transparent simulation trigger endpoints.
 */
@RestController
@RequestMapping("/api/camera")
@CrossOrigin(origins = "*")
public class CameraRestController {

    private final AmbulanceVisionService visionService;
    private final CameraConfig cameraConfig;
    private final com.traffic.traffic_backend.service.EmergencyDatabaseService databaseService;
    private final com.traffic.traffic_backend.repository.AmbulanceDetectionRepository ambulanceRepo;

    public CameraRestController(
            AmbulanceVisionService visionService,
            CameraConfig cameraConfig,
            com.traffic.traffic_backend.service.EmergencyDatabaseService databaseService,
            com.traffic.traffic_backend.repository.AmbulanceDetectionRepository ambulanceRepo) {
        this.visionService = visionService;
        this.cameraConfig = cameraConfig;
        this.databaseService = databaseService;
        this.ambulanceRepo = ambulanceRepo;
    }

    /**
     * Returns operational status of the computer vision engine.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCameraStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("openCvLoaded", visionService.isOpenCvLoaded());
        status.put("modelLoaded", visionService.isModelLoaded());
        status.put("cameraActive", visionService.isCameraActive());
        status.put("simulationMode", visionService.isSimulationMode());
        status.put("deviceIndex", cameraConfig.getDeviceIndex());
        status.put("fps", cameraConfig.getFps());
        status.put("modelPath", cameraConfig.getModelPath());
        status.put("confidenceThreshold", cameraConfig.getConfidenceThreshold());
        status.put("iouThreshold", cameraConfig.getIouThreshold());
        status.put("inputSize", cameraConfig.getInputSize());
        status.put("activeDetectionsCount", visionService.getLatestDetections().size());

        String source;
        if (visionService.isCameraActive() && visionService.isModelLoaded()) {
            source = "PHYSICAL_CAMERA_ONNX_INFERENCE";
        } else if (!visionService.isModelLoaded()) {
            source = "MODEL_UNAVAILABLE";
        } else {
            source = "CAMERA_STANDBY";
        }
        status.put("detectionSource", source);
        return ResponseEntity.ok(status);
    }

    /**
     * Returns list of currently tracked objects in the camera frame.
     */
    @GetMapping("/detections")
    public ResponseEntity<List<CameraDetectionDto>> getDetections() {
        List<CameraDetectionDto> dtos = new ArrayList<>();
        for (DetectedVehicle v : visionService.getLatestDetections()) {
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
                    v.isSimulated() || visionService.isSimulationMode()
            ));
        }
        return ResponseEntity.ok(dtos);
    }

    /**
     * Injects a transparently simulated ambulance detection on the specified road approach.
     * All output database records and UI telemetry will have `is_simulated = true`.
     */
    @PostMapping("/simulate")
    public ResponseEntity<AmbulanceDetection> simulateDetection(
            @RequestParam Road road,
            @RequestParam(required = false, defaultValue = "0.92") float confidence,
            @RequestParam(required = false, defaultValue = "260") int x,
            @RequestParam(required = false, defaultValue = "180") int y,
            @RequestParam(required = false, defaultValue = "120") int width,
            @RequestParam(required = false, defaultValue = "80") int height) {

        AmbulanceDetection detection = visionService.simulateAmbulanceDetection(road, confidence, x, y, width, height);
        return ResponseEntity.ok(detection);
    }

    /**
     * Upload an ambulance image file and run inference using the trained ONNX model.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadAmbulanceImage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "road", required = false, defaultValue = "NORTH") Road road) {

        Map<String, Object> response = new LinkedHashMap<>();
        if (file == null || file.isEmpty()) {
            response.put("error", "No file uploaded");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            byte[] bytes = file.getBytes();
            List<DetectedVehicle> detections = visionService.processImageBytes(bytes, road);

            response.put("filename", file.getOriginalFilename());
            response.put("fileSizeBytes", bytes.length);
            response.put("road", road);
            response.put("detectionsCount", detections.size());
            response.put("detections", detections);
            response.put("modelUsed", visionService.isModelLoaded() ? "Trained YOLOv8 ONNX" : "Fallback Engine");

            boolean hasAmbulance = detections.stream().anyMatch(DetectedVehicle::isAmbulance);
            response.put("isAmbulanceDetected", hasAmbulance);
            float conf = 0.0f;
            if (hasAmbulance) {
                conf = (float) detections.stream().filter(DetectedVehicle::isAmbulance).mapToDouble(DetectedVehicle::getConfidence).max().orElse(0.0);
                response.put("maxConfidence", conf);
                response.put("status", "WAITING_FOR_SIREN_AUDIO");
            } else {
                response.put("maxConfidence", 0.0f);
                response.put("status", "NO_AMBULANCE_FOUND");
            }

            // Save actual detection to database
            AmbulanceDetection ambEntity = new AmbulanceDetection(
                    "CAM-UPLOAD-01",
                    road,
                    "AMB-UP-" + (System.currentTimeMillis() % 10000),
                    conf,
                    0, 0, 0, 0,
                    false
            );
            ambEntity.setImageFilename(file.getOriginalFilename());
            ambEntity.setAmbulanceDetected(hasAmbulance ? "YES" : "NO");
            ambEntity = ambulanceRepo.save(ambEntity);

            databaseService.recordAmbulanceDetection(file.getOriginalFilename(), road, hasAmbulance, conf, ambEntity.getId());
            response.put("databaseRecordId", ambEntity.getId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Process live webcam frame snapshot sent from browser (Base64 JPEG).
     */
    @PostMapping(value = "/detect-frame", consumes = "text/plain")
    public ResponseEntity<Map<String, Object>> detectWebcamFrame(
            @RequestBody String base64Frame,
            @RequestParam(value = "road", required = false, defaultValue = "NORTH") Road road) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            String data = base64Frame;
            if (data.contains(",")) {
                data = data.split(",")[1];
            }
            byte[] imageBytes = Base64.getDecoder().decode(data);
            List<DetectedVehicle> detections = visionService.processImageBytes(imageBytes, road);

            response.put("detectionsCount", detections.size());
            response.put("detections", detections);
            boolean hasAmbulance = detections.stream().anyMatch(DetectedVehicle::isAmbulance);
            response.put("isAmbulanceDetected", hasAmbulance);
            response.put("road", road);
            if (hasAmbulance) {
                float conf = (float) detections.stream().filter(DetectedVehicle::isAmbulance).mapToDouble(DetectedVehicle::getConfidence).max().orElse(0.0);
                response.put("maxConfidence", conf);
                response.put("predictedClass", "ambulance");
                response.put("status", "WAITING_FOR_SIREN_AUDIO");

                AmbulanceDetection ambEntity = new AmbulanceDetection(
                        "CAM-LIVE-01",
                        road,
                        "AMB-LIVE-" + (System.currentTimeMillis() % 10000),
                        conf,
                        0, 0, 0, 0,
                        false
                );
                ambEntity.setImageFilename("live_webcam_frame.jpg");
                ambEntity.setAmbulanceDetected("YES");
                ambEntity = ambulanceRepo.save(ambEntity);

                databaseService.recordAmbulanceDetection("live_webcam_frame.jpg", road, true, conf, ambEntity.getId());
                response.put("databaseRecordId", ambEntity.getId());
            } else {
                response.put("maxConfidence", 0.0f);
                response.put("predictedClass", "none");
                response.put("status", "NO_AMBULANCE_FOUND");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Returns the geometric Regions of Interest (ROI) configured for each approaching road.
     */
    @GetMapping("/rois")
    public ResponseEntity<Map<Road, RoadRoi>> getRois() {
        return ResponseEntity.ok(cameraConfig.getRois());
    }
}
