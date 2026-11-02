package com.traffic.traffic_backend.detection.audio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioFeatureExtractorTest {

    private AudioFeatureExtractor extractor;
    private final int sampleRate = 16000;

    @BeforeEach
    void setUp() {
        extractor = new AudioFeatureExtractor(sampleRate);
    }

    @Test
    void testSyntheticSirenFrequencyDetection() {
        // Generate a synthetic 1000 Hz sine wave for 1.0 second (typical siren tone)
        int numSamples = sampleRate; // 1 second
        float[] samples = new float[numSamples];
        double freq = 1000.0; // 1000 Hz

        for (int i = 0; i < numSamples; i++) {
            samples[i] = (float) (0.8 * Math.sin(2.0 * Math.PI * freq * i / sampleRate));
        }

        AudioFeatureExtractor.AcousticFeatures features = extractor.extractFeatures(samples);

        // Dominant frequency should be close to 1000 Hz (within FFT bin resolution ~7.8 Hz)
        assertEquals(1000.0f, features.getDominantFrequencyHz(), 15.0f, "Dominant frequency must match 1000 Hz siren pitch");

        // High decibel level (> 80 dB)
        assertTrue(features.getDecibels() > 80.0f, "High amplitude sine wave should have decibels > 80 dB");

        // Siren band energy ratio should be high (> 0.70)
        assertTrue(features.getSirenBandEnergyRatio() > 0.70f, "1000 Hz tone must fall predominantly within siren band");
    }

    @Test
    void testLowFrequencyEngineNoiseRejection() {
        // Generate a synthetic 200 Hz sine wave (engine rumble)
        int numSamples = sampleRate;
        float[] samples = new float[numSamples];
        double freq = 200.0;

        for (int i = 0; i < numSamples; i++) {
            samples[i] = (float) (0.8 * Math.sin(2.0 * Math.PI * freq * i / sampleRate));
        }

        AudioFeatureExtractor.AcousticFeatures features = extractor.extractFeatures(samples);

        // Dominant frequency should be close to 200 Hz
        assertEquals(200.0f, features.getDominantFrequencyHz(), 15.0f);

        // Siren band energy ratio (650-1550 Hz) should be near 0 for 200 Hz rumble
        assertTrue(features.getSirenBandEnergyRatio() < 0.10f, "200 Hz tone must have low siren band energy ratio");
    }

    @Test
    void testPcmByteDecoding() {
        // Create 2 samples: 0 and full scale positive (32767)
        byte[] bytes = new byte[4];
        // Sample 0: 0
        bytes[0] = 0x00;
        bytes[1] = 0x00;
        // Sample 1: 32767 (0x7FFF in little-endian is 0xFF, 0x7F)
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0x7F;

        float[] pcm = extractor.bytesToFloatPcm(bytes, bytes.length);

        assertEquals(2, pcm.length);
        assertEquals(0.0f, pcm[0], 0.001f);
        assertEquals(1.0f, pcm[1], 0.001f);
    }
}
