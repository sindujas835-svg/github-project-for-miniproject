"""
Acoustic Siren Dataset Generator & Organizer
Generates 16kHz 16-bit PCM WAV audio files for Emergency Siren Detection:
  - Positive Class (SIREN):
    - Wail pattern: sinusoidal pitch modulation between 650 Hz and 1300 Hz
    - Yelp pattern: rapid pitch modulation between 700 Hz and 1550 Hz
    - Hi-Lo pattern: alternating dual-tone European/US emergency signal (e.g. 700Hz and 950Hz)
    - Harmonic overtones (2nd, 3rd harmonics) with variable environmental noise and distances
  - Negative Class (NEGATIVE):
    - Car horns: dual-tone chord (380 Hz + 460 Hz)
    - Traffic noise: low-frequency engine rumble, tire friction, pink noise
    - Silence / ambient city background
"""

import os
import random
import numpy as np
import soundfile as sf

SAMPLE_RATE = 16000
DURATION_SEC = 1.0
NUM_SAMPLES = int(SAMPLE_RATE * DURATION_SEC) # 16000 samples

def generate_wail_siren(distance="medium"):
    t = np.linspace(0, DURATION_SEC, NUM_SAMPLES, endpoint=False)
    # Modulation frequency ~0.25 Hz
    mod_freq = random.uniform(0.2, 0.3)
    carrier = random.uniform(850, 1000)
    deviation = random.uniform(250, 350)
    
    # Instantaneous frequency
    phase = 2 * np.pi * carrier * t - (deviation / mod_freq) * np.cos(2 * np.pi * mod_freq * t + random.uniform(0, 2*np.pi))
    audio = 0.6 * np.sin(phase) + 0.25 * np.sin(2 * phase) + 0.1 * np.sin(3 * phase)
    
    # Add ambient noise
    noise = np.random.randn(NUM_SAMPLES) * random.uniform(0.01, 0.05)
    audio += noise
    
    # Scale by distance
    scale = 0.8 if distance == "near" else (0.5 if distance == "medium" else 0.25)
    return np.clip(audio * scale, -1.0, 1.0).astype(np.float32)

def generate_yelp_siren(distance="medium"):
    t = np.linspace(0, DURATION_SEC, NUM_SAMPLES, endpoint=False)
    # Rapid modulation ~2.5 - 3.5 Hz
    mod_freq = random.uniform(2.5, 3.5)
    carrier = random.uniform(1050, 1200)
    deviation = random.uniform(300, 400)
    
    phase = 2 * np.pi * carrier * t - (deviation / mod_freq) * np.cos(2 * np.pi * mod_freq * t + random.uniform(0, 2*np.pi))
    audio = 0.65 * np.sin(phase) + 0.20 * np.sin(2 * phase) + 0.08 * np.sin(3 * phase)
    
    noise = np.random.randn(NUM_SAMPLES) * random.uniform(0.01, 0.05)
    audio += noise
    
    scale = 0.8 if distance == "near" else (0.5 if distance == "medium" else 0.25)
    return np.clip(audio * scale, -1.0, 1.0).astype(np.float32)

def generate_hilo_siren(distance="medium"):
    t = np.linspace(0, DURATION_SEC, NUM_SAMPLES, endpoint=False)
    # Alternates between f1 and f2 every 0.3 - 0.5s
    f1, f2 = random.choice([(700, 950), (650, 1200), (750, 1000)])
    switch_period = random.uniform(0.35, 0.50)
    freq_track = np.where((t % (2 * switch_period)) < switch_period, f1, f2)
    phase = 2 * np.pi * np.cumsum(freq_track) / SAMPLE_RATE
    audio = 0.7 * np.sin(phase) + 0.2 * np.sin(2 * phase)
    
    noise = np.random.randn(NUM_SAMPLES) * random.uniform(0.01, 0.05)
    audio += noise
    scale = 0.8 if distance == "near" else (0.5 if distance == "medium" else 0.25)
    return np.clip(audio * scale, -1.0, 1.0).astype(np.float32)

def generate_car_horn():
    t = np.linspace(0, DURATION_SEC, NUM_SAMPLES, endpoint=False)
    f1 = random.uniform(370, 420)
    f2 = random.uniform(460, 520)
    audio = 0.5 * np.sin(2 * np.pi * f1 * t) + 0.4 * np.sin(2 * np.pi * f2 * t)
    # Add brief envelope (horn bursts)
    envelope = np.ones_like(t)
    if random.random() > 0.5:
        # Dual beep
        envelope[int(NUM_SAMPLES * 0.4):int(NUM_SAMPLES * 0.6)] = 0.05
    noise = np.random.randn(NUM_SAMPLES) * 0.02
    return np.clip((audio * envelope + noise) * 0.6, -1.0, 1.0).astype(np.float32)

def generate_traffic_noise():
    # Filtered low-frequency pink/brown noise
    noise = np.cumsum(np.random.randn(NUM_SAMPLES))
    noise = noise - np.mean(noise)
    max_val = np.max(np.abs(noise)) + 1e-6
    normalized = noise / max_val * random.uniform(0.2, 0.45)
    return normalized.astype(np.float32)

def generate_split(base_dir, split_name, siren_count=60, negative_count=60):
    siren_dir = os.path.join(base_dir, split_name, "siren")
    neg_dir = os.path.join(base_dir, split_name, "negative")
    os.makedirs(siren_dir, exist_ok=True)
    os.makedirs(neg_dir, exist_ok=True)

    print(f"Generating {split_name} acoustic split: {siren_count} sirens, {negative_count} negatives...")

    # Sirens
    for i in range(siren_count):
        dist = random.choice(["near", "medium", "far"])
        pattern = i % 3
        if pattern == 0:
            audio = generate_wail_siren(distance=dist)
        elif pattern == 1:
            audio = generate_yelp_siren(distance=dist)
        else:
            audio = generate_hilo_siren(distance=dist)
        
        path = os.path.join(siren_dir, f"siren_{split_name}_{i:04d}.wav")
        sf.write(path, audio, SAMPLE_RATE, subtype='PCM_16')

    # Negatives
    for i in range(negative_count):
        if i % 2 == 0:
            audio = generate_car_horn()
        else:
            audio = generate_traffic_noise()
        
        path = os.path.join(neg_dir, f"neg_{split_name}_{i:04d}.wav")
        sf.write(path, audio, SAMPLE_RATE, subtype='PCM_16')

def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "dataset"))
    random.seed(42)
    np.random.seed(42)

    # Train: 90 sirens, 90 negatives = 180 clips
    generate_split(base_dir, "train", siren_count=90, negative_count=90)
    # Val: 30 sirens, 30 negatives = 60 clips
    generate_split(base_dir, "val", siren_count=30, negative_count=30)
    # Test: 25 sirens, 25 negatives = 50 clips
    generate_split(base_dir, "test", siren_count=25, negative_count=25)

    print("[SUCCESS] Complete Siren Acoustic Dataset generated and structured!")

if __name__ == "__main__":
    main()

