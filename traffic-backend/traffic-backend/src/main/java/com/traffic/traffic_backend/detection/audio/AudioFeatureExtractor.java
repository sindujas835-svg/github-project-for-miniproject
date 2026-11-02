package com.traffic.traffic_backend.detection.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AudioFeatureExtractor: Pure Java Digital Signal Processing (DSP) for acoustic siren analysis.
 *
 * EXPLANATION FOR CS STUDENTS:
 * Sound recorded by a microphone is an analog continuous wave converted by an ADC
 * (Analog-to-Digital Converter) into discrete digital numbers (e.g. 16,000 samples per second).
 *
 * To classify if an approaching vehicle is an emergency siren:
 * 1. RMS AMPLITUDE & DECIBEL:
 *    Calculates the Root-Mean-Square energy of the audio window and converts it to decibels (dB SPL).
 *    If the signal is quiet (below 45 dB), it's background noise or silence.
 *
 * 2. FAST FOURIER TRANSFORM (FFT) & HANN WINDOWING:
 *    Converts the audio from the time domain (amplitude vs time) into the frequency domain
 *    (amplitude vs frequency pitch).
 *    Hann windowing w[n] = 0.5 - 0.5 * cos(2*pi*n / (N-1)) smooths the window boundaries to prevent
 *    spectral leakage.
 *
 * 3. SPECTRAL SIREN DISCRIMINATION:
 *    - Real emergency sirens (Wail, Yelp, Hi-Lo) sweep between 650 Hz and 1500 Hz.
 *    - Car horns are generally lower dual-tones between 350 Hz and 500 Hz.
 *    - Engine rumble and road tire friction concentrate predominantly below 350 Hz.
 *    By measuring the ratio of acoustic energy in the 650-1550 Hz band compared to total energy,
 *    we obtain an acoustic siren confidence score!
 */
public class AudioFeatureExtractor {

    private static final Logger log = LoggerFactory.getLogger(AudioFeatureExtractor.class);

    private final int sampleRate;

    public static class AcousticFeatures {
        private final float decibels;
        private final float dominantFrequencyHz;
        private final float sirenBandEnergyRatio; // Ratio of energy in [650 - 1550 Hz]

        public AcousticFeatures(float decibels, float dominantFrequencyHz, float sirenBandEnergyRatio) {
            this.decibels = decibels;
            this.dominantFrequencyHz = dominantFrequencyHz;
            this.sirenBandEnergyRatio = sirenBandEnergyRatio;
        }

        public float getDecibels() { return decibels; }
        public float getDominantFrequencyHz() { return dominantFrequencyHz; }
        public float getSirenBandEnergyRatio() { return sirenBandEnergyRatio; }
    }

    public AudioFeatureExtractor(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public AudioFeatureExtractor() {
        this(16000);
    }

    /**
     * Converts raw 16-bit signed PCM byte array (little-endian) into normalized [-1.0, 1.0] float array.
     */
    public float[] bytesToFloatPcm(byte[] byteData, int length) {
        int numSamples = length / 2;
        float[] floatPcm = new float[numSamples];
        for (int i = 0; i < numSamples; i++) {
            int low = byteData[i * 2] & 0xFF;
            int high = byteData[i * 2 + 1]; // Signed byte
            short val = (short) ((high << 8) | low);
            floatPcm[i] = val / 32768.0f;
        }
        return floatPcm;
    }

    /**
     * Computes acoustic features (decibels, dominant frequency, siren band ratio) from a PCM window.
     */
    public AcousticFeatures extractFeatures(float[] samples) {
        if (samples == null || samples.length == 0) {
            return new AcousticFeatures(0.0f, 0.0f, 0.0f);
        }

        // 1. Calculate RMS and Decibels
        double sumSquare = 0.0;
        for (float s : samples) {
            sumSquare += (s * s);
        }
        double rms = Math.sqrt(sumSquare / samples.length);
        // Calibrate so 0.001 RMS is ~30 dB, 0.1 RMS is ~70 dB, 0.5+ RMS is ~90+ dB
        float decibels = (float) Math.max(0.0, 20.0 * Math.log10(rms + 1e-6) + 90.0);

        // 2. Perform FFT to analyze frequency spectrum
        int fftSize = 2048; // Nearest power-of-two window for high frequency resolution
        if (fftSize > samples.length) {
            fftSize = Integer.highestOneBit(samples.length);
        }

        double[] real = new double[fftSize];
        double[] imag = new double[fftSize];

        // Apply Hann window to reduce edge distortion
        for (int i = 0; i < fftSize; i++) {
            double hann = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (fftSize - 1)));
            real[i] = samples[i] * hann;
            imag[i] = 0.0;
        }

        fftRadix2(real, imag);

        // 3. Compute magnitude spectrum for positive frequencies
        int halfFft = fftSize / 2;
        double maxMagnitude = 0.0;
        int peakBin = 0;
        double totalEnergy = 0.0;
        double sirenBandEnergy = 0.0;

        double hzPerBin = (double) sampleRate / fftSize;

        for (int k = 1; k < halfFft; k++) { // Skip DC component (k=0)
            double mag = Math.sqrt(real[k] * real[k] + imag[k] * imag[k]);
            double freq = k * hzPerBin;

            totalEnergy += (mag * mag);

            // Siren acoustic frequency band: 650 Hz to 1550 Hz
            if (freq >= 650.0 && freq <= 1550.0) {
                sirenBandEnergy += (mag * mag);
            }

            if (mag > maxMagnitude) {
                maxMagnitude = mag;
                peakBin = k;
            }
        }

        float dominantFrequencyHz = (float) (peakBin * hzPerBin);
        float sirenBandRatio = (float) (totalEnergy > 0.0 ? (sirenBandEnergy / totalEnergy) : 0.0);

        return new AcousticFeatures(decibels, dominantFrequencyHz, sirenBandRatio);
    }

    /**
     * In-place Cooley-Tukey Radix-2 Decimation-in-Time Fast Fourier Transform.
     * Computes Discrete Fourier Transform in O(N log N) time complexity.
     */
    private void fftRadix2(double[] real, double[] imag) {
        int n = real.length;
        if (Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("FFT size must be a power of 2");
        }

        // Bit-reversal permutation
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                double tempR = real[i];
                real[i] = real[j];
                real[j] = tempR;

                double tempI = imag[i];
                imag[i] = imag[j];
                imag[j] = tempI;
            }
            int k = n / 2;
            while (k <= j) {
                j -= k;
                k /= 2;
            }
            j += k;
        }

        // Cooley-Tukey butterfly computations
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            double wStepR = Math.cos(angle);
            double wStepI = Math.sin(angle);

            for (int i = 0; i < n; i += len) {
                double wR = 1.0;
                double wI = 0.0;
                for (int m = 0; m < len / 2; m++) {
                    double uR = real[i + m];
                    double uI = imag[i + m];

                    double vR = real[i + m + len / 2] * wR - imag[i + m + len / 2] * wI;
                    double vI = real[i + m + len / 2] * wI + imag[i + m + len / 2] * wR;

                    real[i + m] = uR + vR;
                    imag[i + m] = uI + vI;

                    real[i + m + len / 2] = uR - vR;
                    imag[i + m + len / 2] = uI - vI;

                    double nextWR = wR * wStepR - wI * wStepI;
                    wI = wR * wStepI + wI * wStepR;
                    wR = nextWR;
                }
            }
        }
    }
}
