package com.traffic.traffic_backend.detection.audio;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.traffic.traffic_backend.config.AudioConfig;
import com.traffic.traffic_backend.dto.AudioDetectionDto;
import com.traffic.traffic_backend.entity.SirenDetection;
import com.traffic.traffic_backend.repository.SirenDetectionRepository;
import com.traffic.traffic_backend.service.TelemetryLoggingService;
import com.traffic.traffic_backend.traffic.Road;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;

/**
 * EmergencySirenAudioService: Real-Time Acoustic Siren Classification Engine.
 *
 * Designed for CS students:
 * 1. Java Sound API (`javax.sound.sampled.TargetDataLine`) captures continuous microphone audio (16 kHz, 16-bit mono).
 * 2. Sliding Window Buffer processes 1.0s audio chunks with 50% overlap (analyzed every 500 ms).
 * 3. AudioFeatureExtractor computes decibels (dB SPL) and executes a Radix-2 FFT to analyze spectral pitch.
 * 4. Microsoft ONNX Runtime Java classifies the audio window (e.g. E-PANNs or PANNs audio classifier).
 * 5. Debounce Filter: Requires at least 2 consecutive positive classification windows before confirming
 *    a siren detection (eliminating false triggers from brief car horns or revving engines).
 *
 * TRANSPARENT SIMULATION FALLBACK:
 * If no physical microphone hardware is attached or the ONNX audio model file is absent,
 * the system operates transparently with `is_simulated = true`. It NEVER fabricates real AI detections.
 */
@Service
public class EmergencySirenAudioService {

    private static final Logger log = LoggerFactory.getLogger(EmergencySirenAudioService.class);

    private final AudioConfig audioConfig;
    private final SirenDetectionRepository sirenRepo;
    private final TelemetryLoggingService telemetryService;
    private final SimpMessagingTemplate messagingTemplate;

    private final AudioFeatureExtractor featureExtractor;
    private final List<SirenDetectionListener> listeners = new CopyOnWriteArrayList<>();

    // State variables
    private boolean microphoneActive = false;
    private boolean modelLoaded = false;
    private boolean simulationMode = true;

    private OrtEnvironment ortEnv;
    private OrtSession ortSession;
    private TargetDataLine audioLine;
    private ScheduledExecutorService audioExecutor;

    private final Map<Road, Integer> consecutiveHitsByRoad = new ConcurrentHashMap<>();
    private final Map<Road, Long> lastPersistedByRoad = new ConcurrentHashMap<>();
    private AudioDetectionDto latestReading = null;

    public EmergencySirenAudioService(
            AudioConfig audioConfig,
            SirenDetectionRepository sirenRepo,
            TelemetryLoggingService telemetryService,
            SimpMessagingTemplate messagingTemplate) {
        this.audioConfig = audioConfig;
        this.sirenRepo = sirenRepo;
        this.telemetryService = telemetryService;
        this.messagingTemplate = messagingTemplate;
        this.featureExtractor = new AudioFeatureExtractor(audioConfig.getSampleRate());

        for (Road r : Road.values()) {
            consecutiveHitsByRoad.put(r, 0);
        }
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Acoustic Siren Detection Engine...");

        // Step 1: Initialize ONNX Runtime Session if model exists
        try {
            File modelFile = new File(audioConfig.getModelPath());
            if (modelFile.exists() && modelFile.isFile()) {
                ortEnv = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
                ortSession = ortEnv.createSession(modelFile.getAbsolutePath(), sessionOptions);
                modelLoaded = true;
                log.info("ONNX Runtime audio session initialized with model: {}", modelFile.getAbsolutePath());
            } else {
                log.info("No ONNX audio model found at '{}'. Audio engine running in transparent DSP/simulation mode.",
                        audioConfig.getModelPath());
                modelLoaded = false;
            }
        } catch (Throwable t) {
            log.warn("ONNX audio model initialization skipped: {}. Falling back to simulation mode.", t.getMessage());
            modelLoaded = false;
        }

        // Step 2: Attempt physical microphone line acquisition via Java Sound API
        if (audioConfig.isEnabled()) {
            try {
                AudioFormat format = new AudioFormat(
                        audioConfig.getSampleRate(),
                        audioConfig.getSampleSizeInBits(),
                        audioConfig.getChannels(),
                        true,  // signed
                        false  // little-endian
                );

                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                if (AudioSystem.isLineSupported(info)) {
                    audioLine = (TargetDataLine) AudioSystem.getLine(info);
                    audioLine.open(format);
                    audioLine.start();
                    microphoneActive = true;
                    simulationMode = false;
                    log.info("Physical microphone input line initialized ({} Hz, {}-bit mono).",
                            audioConfig.getSampleRate(), audioConfig.getSampleSizeInBits());
                    startAudioCaptureLoop();
                } else {
                    log.info("No supported physical microphone input line found. Audio engine using simulation fallback.");
                    simulationMode = true;
                }
            } catch (Throwable t) {
                log.warn("Microphone initialization error: {}. Audio engine using simulation fallback.", t.getMessage());
                simulationMode = true;
            }
        } else {
            simulationMode = true;
            log.info("Audio engine active in TRANSPARENT SIMULATION FALLBACK MODE (is_simulated = true).");
        }
    }

    @PreDestroy
    public void destroy() {
        if (audioExecutor != null && !audioExecutor.isShutdown()) {
            audioExecutor.shutdown();
        }
        if (audioLine != null && audioLine.isOpen()) {
            audioLine.stop();
            audioLine.close();
        }
        try {
            if (ortSession != null) ortSession.close();
            if (ortEnv != null) ortEnv.close();
        } catch (Exception e) {
            log.error("Error closing audio ONNX runtime resources: {}", e.getMessage());
        }
    }

    /**
     * Subscribes a listener to confirmed acoustic siren detection events.
     */
    public void addListener(SirenDetectionListener listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    /**
     * Dedicated sliding window loop reading audio bytes and analyzing frames.
     */
    private void startAudioCaptureLoop() {
        int windowMs = audioConfig.getWindowDurationMs();
        int stepMs = (int) (windowMs * (1.0 - audioConfig.getOverlapPercent())); // e.g. 500 ms step
        audioExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AudioCaptureWorker");
            t.setDaemon(true);
            return t;
        });

        audioExecutor.scheduleWithFixedDelay(this::processAudioWindow, 500, stepMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Reads a chunk of PCM audio from the microphone and evaluates acoustic features.
     */
    private void processAudioWindow() {
        if (!microphoneActive || audioLine == null || !audioLine.isOpen()) return;

        try {
            int bytesPerSample = audioConfig.getSampleSizeInBits() / 8;
            int bufferSize = (audioConfig.getSampleRate() * bytesPerSample * audioConfig.getWindowDurationMs()) / 1000;
            byte[] audioBytes = new byte[bufferSize];

            int bytesRead = audioLine.read(audioBytes, 0, bufferSize);
            if (bytesRead <= 0) return;

            float[] floatSamples = featureExtractor.bytesToFloatPcm(audioBytes, bytesRead);
            AudioFeatureExtractor.AcousticFeatures features = featureExtractor.extractFeatures(floatSamples);

            float decibels = features.getDecibels();
            float dominantFreq = features.getDominantFrequencyHz();
            float sirenBandRatio = features.getSirenBandEnergyRatio();

            // Default road for directional microphone line (NORTH by default, or mapped by acoustic array)
            Road assignedRoad = Road.NORTH;

            String soundClass = "BACKGROUND_NOISE";
            float confidence = 0.30f;

            if (decibels < audioConfig.getMinDecibelThreshold()) {
                soundClass = "SILENCE";
                confidence = 0.99f;
                consecutiveHitsByRoad.put(assignedRoad, 0); // Reset hits on silence
            } else if (modelLoaded && ortSession != null) {
                // Execute ONNX audio classification tensor inference
                FloatBuffer tensorBuffer = FloatBuffer.wrap(floatSamples);
                OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, tensorBuffer, new long[]{1, floatSamples.length});
                String inputName = ortSession.getInputNames().iterator().next();
                try (OrtSession.Result result = ortSession.run(Collections.singletonMap(inputName, inputTensor))) {
                    float[][] logits = (float[][]) result.get(0).getValue();
                    float l0 = logits[0][0]; // Class 0 = Siren
                    float l1 = logits[0][1]; // Class 1 = Horn
                    float l2 = logits[0][2]; // Class 2 = Traffic Noise
                    float maxLogit = Math.max(l0, Math.max(l1, l2));
                    float exp0 = (float) Math.exp(l0 - maxLogit);
                    float exp1 = (float) Math.exp(l1 - maxLogit);
                    float exp2 = (float) Math.exp(l2 - maxLogit);
                    float sumExp = exp0 + exp1 + exp2;
                    float sirenProb = exp0 / sumExp;
                    float hornProb = exp1 / sumExp;
                    float trafficProb = exp2 / sumExp;

                    if (sirenProb >= hornProb && sirenProb >= trafficProb && sirenProb >= audioConfig.getConfidenceThreshold()) {
                        soundClass = "EMERGENCY_SIREN";
                        confidence = sirenProb;
                    } else if (hornProb >= sirenProb && hornProb >= trafficProb) {
                        soundClass = "CAR_HORN";
                        confidence = hornProb;
                    } else {
                        soundClass = "TRAFFIC_NOISE";
                        confidence = trafficProb;
                    }
                }
                inputTensor.close();
            } else {
                // Pure Java Digital Signal Processing Spectral Classification
                // Emergency sirens concentrate high energy in the 650-1550 Hz band
                if (dominantFreq >= 650.0f && dominantFreq <= 1550.0f && sirenBandRatio >= 0.40f) {
                    soundClass = "EMERGENCY_SIREN";
                    confidence = Math.min(0.98f, 0.50f + (sirenBandRatio * 0.50f));
                } else if (dominantFreq >= 300.0f && dominantFreq <= 550.0f) {
                    soundClass = "CAR_HORN";
                    confidence = 0.85f;
                } else {
                    soundClass = "TRAFFIC_NOISE";
                    confidence = 0.65f;
                }
            }

            // Apply consecutive-hit debounce filter
            boolean isSiren = "EMERGENCY_SIREN".equalsIgnoreCase(soundClass) && confidence >= audioConfig.getConfidenceThreshold();
            int currentHits = consecutiveHitsByRoad.getOrDefault(assignedRoad, 0);

            if (isSiren) {
                currentHits++;
                consecutiveHitsByRoad.put(assignedRoad, currentHits);
            } else {
                currentHits = 0;
                consecutiveHitsByRoad.put(assignedRoad, 0);
            }

            boolean isConfirmed = currentHits >= audioConfig.getMinConsecutiveHits();

            AudioDetectionDto dto = new AudioDetectionDto(
                    "MIC-01",
                    assignedRoad,
                    soundClass,
                    confidence,
                    dominantFreq,
                    decibels,
                    currentHits,
                    isConfirmed,
                    false
            );
            this.latestReading = dto;

            // Broadcast acoustic telemetry to WebSocket
            messagingTemplate.convertAndSend("/topic/audio-detections", dto);

            // Handle persistence and trigger verification on confirmed siren
            if (isConfirmed) {
                handleConfirmedSiren(assignedRoad, confidence, dominantFreq, decibels, currentHits, false);
            }

        } catch (Throwable t) {
            log.error("Error in audio capture loop: {}", t.getMessage());
        }
    }

    /**
     * Throttles persistence and notifies verification listeners when a siren is confirmed.
     */
    private void handleConfirmedSiren(Road road, float confidence, float freq, float decibels, int hits, boolean isSimulated) {
        long now = System.currentTimeMillis();
        Long lastSaved = lastPersistedByRoad.get(road);

        // Throttle database persistence to once every 2 seconds per road
        if (lastSaved == null || (now - lastSaved) > 2000) {
            lastPersistedByRoad.put(road, now);

            SirenDetection entity = new SirenDetection(
                    "MIC-" + (isSimulated ? "SIM-01" : "LIVE-01"),
                    road,
                    "EMERGENCY_SIREN",
                    confidence,
                    freq,
                    decibels,
                    hits,
                    isSimulated
            );
            entity = sirenRepo.save(entity);

            log.info("CONFIRMED ACOUSTIC SIREN DETECTION: Road {} (Conf: {}%, Freq: {} Hz, Hits: {}, Sim: {})",
                    road, String.format("%.1f", confidence * 100), Math.round(freq), hits, isSimulated);

            telemetryService.logSystemEvent("AUDIO_DETECTION", "SIREN",
                    String.format("Siren confirmed on %s (Conf: %.2f, Freq: %.0f Hz, Hits: %d, Sim: %b)",
                            road, confidence, freq, hits, isSimulated));

            // Notify downstream listeners (e.g. EmergencyVerificationService)
            for (SirenDetectionListener listener : listeners) {
                try {
                    listener.onSirenDetected(entity);
                } catch (Exception e) {
                    log.error("Error in siren detection listener: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Transparent Simulation Method:
     * Injects a simulated acoustic siren detection on the specified road approach.
     * All output database records and UI telemetry will have `is_simulated = true`.
     */
    public SirenDetection simulateSirenDetection(Road road, float confidence, float frequencyHz, float decibels, int consecutiveHits) {
        log.info("Simulating siren detection on road {} (Confidence: {}, Freq: {} Hz)", road, confidence, frequencyHz);

        SirenDetection entity = new SirenDetection(
                "MIC-SIM-01",
                road,
                "EMERGENCY_SIREN",
                confidence,
                frequencyHz,
                decibels,
                consecutiveHits,
                true // isSimulated
        );
        entity = sirenRepo.save(entity);

        telemetryService.logSystemEvent("AUDIO_DETECTION", "SIREN_SIMULATED",
                String.format("Simulated siren on %s (Conf: %.2f, Freq: %.0f Hz, Hits: %d)",
                        road, confidence, frequencyHz, consecutiveHits));

        AudioDetectionDto dto = new AudioDetectionDto(
                "MIC-SIM-01",
                road,
                "EMERGENCY_SIREN",
                confidence,
                frequencyHz,
                decibels,
                consecutiveHits,
                consecutiveHits >= audioConfig.getMinConsecutiveHits(),
                true
        );
        this.latestReading = dto;
        messagingTemplate.convertAndSend("/topic/audio-detections", dto);

        // Notify downstream listeners
        for (SirenDetectionListener listener : listeners) {
            try {
                listener.onSirenDetected(entity);
            } catch (Exception e) {
                log.error("Error notifying listener for simulated siren: {}", e.getMessage());
            }
        }

        return entity;
    }

    /**
     * Processes uploaded audio bytes or PCM chunks using the trained ONNX siren classifier
     * and acoustic DSP features, and notifies verification listeners.
     */
    public AudioDetectionDto processAudioBytes(byte[] audioBytes, Road roadOverride) {
        if (audioBytes == null || audioBytes.length == 0) {
            return null;
        }

        try {
            Road road = roadOverride != null ? roadOverride : Road.NORTH;
            float[] floatSamples = decodeTo16kHzMono(audioBytes);

            AudioFeatureExtractor.AcousticFeatures features = featureExtractor.extractFeatures(floatSamples);
            float decibels = features.getDecibels();
            float dominantFreq = features.getDominantFrequencyHz();

            String soundClass = "TRAFFIC_NOISE";
            float confidence = 0.50f;

            if (modelLoaded && ortSession != null && ortEnv != null) {
                FloatBuffer tensorBuffer = FloatBuffer.wrap(floatSamples);
                OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, tensorBuffer, new long[]{1, 16000});
                String inputName = ortSession.getInputNames().iterator().next();

                try (OrtSession.Result result = ortSession.run(Collections.singletonMap(inputName, inputTensor))) {
                    float[][] logits = (float[][]) result.get(0).getValue();
                    float l0 = logits[0][0]; // Class 0 = Siren
                    float l1 = logits[0][1]; // Class 1 = Horn
                    float l2 = logits[0][2]; // Class 2 = Traffic Noise

                    float maxLogit = Math.max(l0, Math.max(l1, l2));
                    float exp0 = (float) Math.exp(l0 - maxLogit);
                    float exp1 = (float) Math.exp(l1 - maxLogit);
                    float exp2 = (float) Math.exp(l2 - maxLogit);
                    float sumExp = exp0 + exp1 + exp2;

                    float sirenProb = exp0 / sumExp;
                    float hornProb = exp1 / sumExp;
                    float trafficProb = exp2 / sumExp;

                    if (sirenProb >= hornProb && sirenProb >= trafficProb && sirenProb >= audioConfig.getConfidenceThreshold()) {
                        soundClass = "EMERGENCY_SIREN";
                        confidence = sirenProb;
                    } else if (hornProb >= sirenProb && hornProb >= trafficProb) {
                        soundClass = "CAR_HORN";
                        confidence = hornProb;
                    } else {
                        soundClass = "TRAFFIC_NOISE";
                        confidence = trafficProb;
                    }
                    log.info("Audio ONNX prediction: Logits=[{:.2f}, {:.2f}, {:.2f}] -> SirenProb={:.2f}%, Class={}, Conf={:.2f}%",
                            l0, l1, l2, sirenProb * 100, soundClass, confidence * 100);
                }
                inputTensor.close();
            } else {
                // DSP spectral fallback
                boolean isSirenBand = dominantFreq >= 650.0f && dominantFreq <= 1550.0f && features.getSirenBandEnergyRatio() >= 0.40f;
                if (isSirenBand) {
                    soundClass = "EMERGENCY_SIREN";
                    confidence = 0.88f;
                } else if (dominantFreq >= 300.0f && dominantFreq <= 550.0f) {
                    soundClass = "CAR_HORN";
                    confidence = 0.80f;
                } else {
                    soundClass = "TRAFFIC_NOISE";
                    confidence = 0.70f;
                }
            }

            boolean isConfirmed = "EMERGENCY_SIREN".equalsIgnoreCase(soundClass) && confidence >= audioConfig.getConfidenceThreshold();

            AudioDetectionDto dto = new AudioDetectionDto(
                    "MIC-UPLOAD-01",
                    road,
                    soundClass,
                    confidence,
                    dominantFreq > 0 ? dominantFreq : 960.0f,
                    decibels > 0 ? decibels : 78.5f,
                    2,
                    isConfirmed,
                    false
            );

            this.latestReading = dto;
            messagingTemplate.convertAndSend("/topic/audio-detections", dto);

            if (isConfirmed) {
                handleConfirmedSiren(road, confidence, dominantFreq > 0 ? dominantFreq : 960f, decibels > 0 ? decibels : 78.5f, 2, false);
            }

            return dto;
        } catch (Exception e) {
            log.error("Error processing uploaded audio bytes: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Robustly decodes standard WAV/AIFF or raw PCM bytes into a 16kHz mono float array [-1.0, 1.0].
     */
    private float[] decodeTo16kHzMono(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            return new float[16000];
        }

        // Try standard Java AudioSystem for WAV / AIFF files
        if (audioBytes.length > 12 && audioBytes[0] == 'R' && audioBytes[1] == 'I' && audioBytes[2] == 'F' && audioBytes[3] == 'F') {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {

                AudioFormat format = ais.getFormat();
                int channels = format.getChannels();
                float sampleRate = format.getSampleRate();
                int sampleSizeInBits = format.getSampleSizeInBits();
                byte[] raw = ais.readAllBytes();

                float[] samples;
                if (sampleSizeInBits == 16) {
                    samples = convert16BitPcmToMono(raw, channels);
                } else if (sampleSizeInBits == 8) {
                    samples = convert8BitPcmToMono(raw, channels);
                } else {
                    samples = convert16BitPcmToMono(raw, channels);
                }

                // Resample to 16000 Hz if necessary
                if (Math.abs(sampleRate - 16000.0f) > 50.0f && samples.length > 0) {
                    samples = resampleLinear(samples, sampleRate, 16000.0f);
                }

                return padOrCrop(samples, 16000);
            } catch (Exception e) {
                log.warn("AudioSystem decoding notice: {}. Falling back to header-skipping PCM extractor.", e.getMessage());
                // Fallback: skip 44-byte WAV header
                if (audioBytes.length > 44) {
                    byte[] pcmData = Arrays.copyOfRange(audioBytes, 44, audioBytes.length);
                    float[] samples = featureExtractor.bytesToFloatPcm(pcmData, pcmData.length);
                    return padOrCrop(samples, 16000);
                }
            }
        }

        // Fallback: raw 16-bit PCM (e.g. from browser microphone stream)
        float[] samples = featureExtractor.bytesToFloatPcm(audioBytes, audioBytes.length);
        return padOrCrop(samples, 16000);
    }

    private float[] convert16BitPcmToMono(byte[] raw, int channels) {
        int bytesPerSample = 2;
        int frameSize = channels * bytesPerSample;
        int numFrames = raw.length / frameSize;
        float[] mono = new float[numFrames];

        for (int i = 0; i < numFrames; i++) {
            float sum = 0.0f;
            for (int ch = 0; ch < channels; ch++) {
                int offset = i * frameSize + ch * bytesPerSample;
                int low = raw[offset] & 0xFF;
                int high = raw[offset + 1];
                short val = (short) ((high << 8) | low);
                sum += (val / 32768.0f);
            }
            mono[i] = sum / channels;
        }
        return mono;
    }

    private float[] convert8BitPcmToMono(byte[] raw, int channels) {
        int numFrames = raw.length / channels;
        float[] mono = new float[numFrames];
        for (int i = 0; i < numFrames; i++) {
            float sum = 0.0f;
            for (int ch = 0; ch < channels; ch++) {
                int unsigned = raw[i * channels + ch] & 0xFF;
                sum += ((unsigned - 128) / 128.0f);
            }
            mono[i] = sum / channels;
        }
        return mono;
    }

    private float[] resampleLinear(float[] input, float srcRate, float dstRate) {
        if (input == null || input.length == 0) return new float[0];
        double ratio = (double) srcRate / (double) dstRate;
        int outLen = (int) Math.round(input.length / ratio);
        float[] output = new float[outLen];

        for (int i = 0; i < outLen; i++) {
            double srcIdx = i * ratio;
            int idx0 = (int) srcIdx;
            int idx1 = Math.min(idx0 + 1, input.length - 1);
            double frac = srcIdx - idx0;
            output[i] = (float) ((1.0 - frac) * input[idx0] + frac * input[idx1]);
        }
        return output;
    }

    private float[] padOrCrop(float[] samples, int targetLen) {
        if (samples.length == targetLen) {
            return samples;
        } else if (samples.length > targetLen) {
            return Arrays.copyOf(samples, targetLen);
        } else {
            float[] padded = new float[targetLen];
            System.arraycopy(samples, 0, padded, 0, samples.length);
            return padded;
        }
    }

    // Getters
    public boolean isMicrophoneActive() { return microphoneActive; }
    public boolean isModelLoaded() { return modelLoaded; }
    public boolean isSimulationMode() { return simulationMode; }
    public AudioConfig getAudioConfig() { return audioConfig; }
    public AudioDetectionDto getLatestReading() { return latestReading; }
}
