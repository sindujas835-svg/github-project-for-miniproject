package com.traffic.traffic_backend.controller;

import com.traffic.traffic_backend.config.AudioConfig;
import com.traffic.traffic_backend.detection.audio.EmergencySirenAudioService;
import com.traffic.traffic_backend.dto.AudioDetectionDto;
import com.traffic.traffic_backend.entity.SirenDetection;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST Controller exposing microphone status, live acoustic readings,
 * and transparent simulation trigger endpoints.
 */
@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = "*")
public class AudioRestController {

    private final EmergencySirenAudioService audioService;
    private final AudioConfig audioConfig;
    private final com.traffic.traffic_backend.service.EmergencyDatabaseService databaseService;
    private final com.traffic.traffic_backend.repository.SirenDetectionRepository sirenRepo;
    private final com.traffic.traffic_backend.traffic.TrafficSignalController signalController;

    public AudioRestController(
            EmergencySirenAudioService audioService,
            AudioConfig audioConfig,
            com.traffic.traffic_backend.service.EmergencyDatabaseService databaseService,
            com.traffic.traffic_backend.repository.SirenDetectionRepository sirenRepo,
            com.traffic.traffic_backend.traffic.TrafficSignalController signalController) {
        this.audioService = audioService;
        this.audioConfig = audioConfig;
        this.databaseService = databaseService;
        this.sirenRepo = sirenRepo;
        this.signalController = signalController;
    }

    /**
     * Returns operational status of the acoustic siren classification engine.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAudioStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("microphoneActive", audioService.isMicrophoneActive());
        status.put("modelLoaded", audioService.isModelLoaded());
        status.put("simulationMode", audioService.isSimulationMode());
        status.put("sampleRate", audioConfig.getSampleRate());
        status.put("sampleSizeInBits", audioConfig.getSampleSizeInBits());
        status.put("channels", audioConfig.getChannels());
        status.put("windowDurationMs", audioConfig.getWindowDurationMs());
        status.put("overlapPercent", audioConfig.getOverlapPercent());
        status.put("minConsecutiveHits", audioConfig.getMinConsecutiveHits());
        status.put("confidenceThreshold", audioConfig.getConfidenceThreshold());
        status.put("minDecibelThreshold", audioConfig.getMinDecibelThreshold());
        status.put("modelPath", audioConfig.getModelPath());

        String source;
        if (audioService.isMicrophoneActive() && audioService.isModelLoaded()) {
            source = "PHYSICAL_MICROPHONE_ONNX_INFERENCE";
        } else if (!audioService.isModelLoaded()) {
            source = "MODEL_UNAVAILABLE";
        } else {
            source = "MICROPHONE_STANDBY";
        }
        status.put("detectionSource", source);
        return ResponseEntity.ok(status);
    }

    /**
     * Returns the latest acoustic reading.
     */
    @GetMapping("/reading")
    public ResponseEntity<AudioDetectionDto> getLatestReading() {
        AudioDetectionDto reading = audioService.getLatestReading();
        if (reading == null) {
            reading = new AudioDetectionDto(
                    "MIC-01",
                    Road.NORTH,
                    "SILENCE",
                    0.99f,
                    0.0f,
                    32.0f,
                    0,
                    false,
                    audioService.isSimulationMode()
            );
        }
        return ResponseEntity.ok(reading);
    }

    /**
     * Injects a transparently simulated acoustic siren detection on the specified road approach.
     * All output database records and UI telemetry will have `is_simulated = true`.
     */
    @PostMapping("/simulate")
    public ResponseEntity<SirenDetection> simulateSiren(
            @RequestParam Road road,
            @RequestParam(required = false, defaultValue = "0.94") float confidence,
            @RequestParam(required = false, defaultValue = "950.0") float frequencyHz,
            @RequestParam(required = false, defaultValue = "88.0") float decibels,
            @RequestParam(required = false, defaultValue = "2") int consecutiveHits) {

        SirenDetection detection = audioService.simulateSirenDetection(road, confidence, frequencyHz, decibels, consecutiveHits);
        return ResponseEntity.ok(detection);
    }

    /**
     * Upload a siren WAV audio clip and classify it using the trained ONNX model and FFT DSP.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadSirenAudio(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "road", required = false, defaultValue = "NORTH") Road road) {

        Map<String, Object> response = new LinkedHashMap<>();
        if (file == null || file.isEmpty()) {
            response.put("error", "No audio file uploaded");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            byte[] bytes = file.getBytes();
            AudioDetectionDto result = audioService.processAudioBytes(bytes, road);

            if (result != null) {
                response.put("filename", file.getOriginalFilename());
                response.put("fileSizeBytes", bytes.length);
                response.put("road", road);
                response.put("soundClass", result.getSoundClass());
                response.put("confidence", result.getConfidence());
                response.put("dominantFrequencyHz", result.getFrequencyHz());
                response.put("decibels", result.getDecibelLevel());
                response.put("isSirenConfirmed", result.isSirenConfirmed());
                response.put("modelUsed", audioService.isModelLoaded() ? "Trained 1D-CNN ONNX" : "DSP Spectral Engine");

                // Save to siren_detections table
                SirenDetection sirenEntity = new SirenDetection(
                        "MIC-UPLOAD-01",
                        road,
                        result.getSoundClass(),
                        result.getConfidence(),
                        result.getFrequencyHz(),
                        result.getDecibelLevel(),
                        2,
                        false
                );
                sirenEntity.setAudioFilename(file.getOriginalFilename());
                sirenEntity.setSirenDetected(result.isSirenConfirmed() ? "YES" : "NO");
                sirenEntity = sirenRepo.save(sirenEntity);
                response.put("databaseRecordId", sirenEntity.getId());

                // Resolve with pending emergency_history record
                com.traffic.traffic_backend.entity.EmergencyHistory resolved = databaseService.resolvePendingEmergency(
                        sirenEntity.getId(),
                        road,
                        result.isSirenConfirmed(),
                        result.getConfidence()
                );

                if (resolved != null) {
                    response.put("emergencyStatus", resolved.getEmergencyStatus());
                    response.put("emergencyId", resolved.getEmergencyId());
                    if ("CONFIRMED".equals(resolved.getEmergencyStatus())) {
                        signalController.requestEmergencyPreemption(road, "AMB-" + road, 1);
                        response.put("status", "EMERGENCY_CONFIRMED");
                        response.put("signalColor", road + " GREEN");
                    } else {
                        response.put("status", "EMERGENCY_REJECTED");
                        response.put("signalColor", "ALL_RED");
                    }
                } else {
                    response.put("status", result.isSirenConfirmed() ? "SIREN_DETECTED" : "NO_SIREN_DETECTED");
                }
            } else {
                response.put("status", "AUDIO_PROCESSING_ERROR");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Process live microphone chunk streamed from browser (Base64 PCM).
     */
    @PostMapping(value = "/detect-chunk", consumes = "text/plain")
    public ResponseEntity<Map<String, Object>> detectAudioChunk(
            @RequestBody String base64Chunk,
            @RequestParam(value = "road", required = false, defaultValue = "NORTH") Road road) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            String data = base64Chunk;
            if (data.contains(",")) {
                data = data.split(",")[1];
            }
            byte[] pcmBytes = java.util.Base64.getDecoder().decode(data);
            AudioDetectionDto result = audioService.processAudioBytes(pcmBytes, road);

            if (result != null) {
                response.put("soundClass", result.getSoundClass());
                response.put("confidence", result.getConfidence());
                response.put("isSirenConfirmed", result.isSirenConfirmed());
                response.put("frequencyHz", result.getFrequencyHz());
                response.put("road", road);

                if (result.isSirenConfirmed()) {
                    SirenDetection sirenEntity = new SirenDetection(
                            "MIC-LIVE-01",
                            road,
                            result.getSoundClass(),
                            result.getConfidence(),
                            result.getFrequencyHz(),
                            result.getDecibelLevel(),
                            2,
                            false
                    );
                    sirenEntity.setAudioFilename("live_microphone_stream.wav");
                    sirenEntity.setSirenDetected("YES");
                    sirenEntity = sirenRepo.save(sirenEntity);
                    response.put("databaseRecordId", sirenEntity.getId());

                    com.traffic.traffic_backend.entity.EmergencyHistory resolved = databaseService.resolvePendingEmergency(
                            sirenEntity.getId(),
                            road,
                            true,
                            result.getConfidence()
                    );

                    if (resolved != null && "CONFIRMED".equals(resolved.getEmergencyStatus())) {
                        signalController.requestEmergencyPreemption(road, "AMB-" + road, 1);
                        response.put("status", "EMERGENCY_CONFIRMED");
                        response.put("signalColor", road + " GREEN");
                    } else if (resolved != null) {
                        response.put("status", "EMERGENCY_REJECTED");
                        response.put("signalColor", "ALL_RED");
                    } else {
                        response.put("status", "SIREN_DETECTED");
                    }
                } else {
                    response.put("status", "NO_SIREN_DETECTED");
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
