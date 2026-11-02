package com.traffic.traffic_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the live acoustic siren detection pipeline.
 */
@Configuration
@ConfigurationProperties(prefix = "traffic.audio")
public class AudioConfig {

    private boolean enabled = true;
    private int sampleRate = 16000; // Standard 16 kHz for speech and audio classification
    private int sampleSizeInBits = 16; // 16-bit PCM
    private int channels = 1; // Mono audio
    private int windowDurationMs = 1000; // 1.0 second analysis window
    private double overlapPercent = 0.50; // 50% sliding window overlap (500 ms step)
    private int minConsecutiveHits = 2; // Debounce filter: requires 2 consecutive siren hits
    private float confidenceThreshold = 0.70f;
    private float minDecibelThreshold = 45.0f; // Silence suppression threshold in dB
    private String modelPath = "models/audio/siren_classifier.onnx";
    private boolean simulationFallbackEnabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getSampleRate() { return sampleRate; }
    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }

    public int getSampleSizeInBits() { return sampleSizeInBits; }
    public void setSampleSizeInBits(int sampleSizeInBits) { this.sampleSizeInBits = sampleSizeInBits; }

    public int getChannels() { return channels; }
    public void setChannels(int channels) { this.channels = channels; }

    public int getWindowDurationMs() { return windowDurationMs; }
    public void setWindowDurationMs(int windowDurationMs) { this.windowDurationMs = windowDurationMs; }

    public double getOverlapPercent() { return overlapPercent; }
    public void setOverlapPercent(double overlapPercent) { this.overlapPercent = overlapPercent; }

    public int getMinConsecutiveHits() { return minConsecutiveHits; }
    public void setMinConsecutiveHits(int minConsecutiveHits) { this.minConsecutiveHits = minConsecutiveHits; }

    public float getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(float confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public float getMinDecibelThreshold() { return minDecibelThreshold; }
    public void setMinDecibelThreshold(float minDecibelThreshold) { this.minDecibelThreshold = minDecibelThreshold; }

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }

    public boolean isSimulationFallbackEnabled() { return simulationFallbackEnabled; }
    public void setSimulationFallbackEnabled(boolean simulationFallbackEnabled) { this.simulationFallbackEnabled = simulationFallbackEnabled; }
}
