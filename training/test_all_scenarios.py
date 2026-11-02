"""
Comprehensive End-to-End Validation Script for Emergency Traffic Management System.
Tests all 14 scenarios across Vision, Audio, Dual-Verification, and Signal Control.
"""

import os
import base64
import requests
import json
import time

BACKEND_BASE = "http://localhost:8080"
SAMPLE_DIR = r"c:\Users\shri\Downloads\traffic-routing-prototype\sample_test_files"

def run_tests():
    print("=" * 70)
    print("STARTING FULL END-TO-END SYSTEM VALIDATION SUITE")
    print("=" * 70)
    results = {}

    # Reset backend first
    try:
        r = requests.post(f"{BACKEND_BASE}/api/emergency/reset")
        print(f"Backend reset to idle: {r.status_code}")
    except Exception as e:
        print(f"Backend connection error: {e}")
        return

    # ---------------------------------------------------------
    # TEST 1: Vision Multi-Class Discrimination (Bug 2)
    # ---------------------------------------------------------
    vision_tests = [
        ("ambulance_positive_01.jpg", "ambulance", True),
        ("person_negative_01.jpg", "person", False),
        ("civilian_car_negative_01.jpg", "car", False),
        ("bus_negative_01.jpg", "bus", False),
        ("truck_negative_01.jpg", "truck", False),
        ("motorcycle_negative_01.jpg", "motorcycle", False),
    ]

    print("\n--- TEST GROUP A: COMPUTER VISION OBJECT CLASSIFICATION ---")
    for fname, expected_class, should_be_ambulance in vision_tests:
        fpath = os.path.join(SAMPLE_DIR, fname)
        if not os.path.exists(fpath):
            print(f"  [SKIP] File not found: {fname}")
            continue

        with open(fpath, "rb") as f:
            b64_data = "data:image/jpeg;base64," + base64.b64encode(f.read()).decode("utf-8")

        res = requests.post(f"{BACKEND_BASE}/api/camera/detect-frame?road=NORTH", data=b64_data, headers={"Content-Type": "text/plain"})
        if res.status_code == 200:
            data = res.json()
            is_amb = data.get("isAmbulanceDetected", False)
            conf = data.get("maxConfidence", 0.0)
            detections = data.get("detections", [])
            labels = [d.get("label") for d in detections]

            passed = (is_amb == should_be_ambulance)
            status_str = "PASS" if passed else "FAIL"
            print(f"  [{status_str}] File: {fname:25s} | Expected: {expected_class:10s} (Amb={should_be_ambulance}) -> Detected: {labels} (Amb={is_amb}, Conf={conf*100:.1f}%)")
            results[f"Vision_{expected_class}"] = passed
        else:
            print(f"  [FAIL] HTTP Error: {res.status_code} for {fname}")
            results[f"Vision_{expected_class}"] = False

    # ---------------------------------------------------------
    # TEST 2: Acoustic Siren Classification (Bug 3)
    # ---------------------------------------------------------
    print("\n--- TEST GROUP B: ACOUSTIC SIREN AUDIO CLASSIFICATION ---")
    audio_tests = [
        ("siren_positive_01.wav", "EMERGENCY_SIREN", True),
        ("car_horn_negative_01.wav", "CAR_HORN", False),
        ("traffic_noise_negative_01.wav", "TRAFFIC_NOISE", False),
    ]

    for fname, expected_sound, should_be_siren in audio_tests:
        fpath = os.path.join(SAMPLE_DIR, fname)
        if not os.path.exists(fpath):
            print(f"  [SKIP] File not found: {fname}")
            continue

        with open(fpath, "rb") as f:
            wav_bytes = f.read()

        # Send raw 16-bit PCM bytes skipped 44-byte WAV header or full WAV to detect-chunk
        b64_data = base64.b64encode(wav_bytes).decode("utf-8")
        res = requests.post(f"{BACKEND_BASE}/api/audio/detect-chunk?road=NORTH", data=b64_data, headers={"Content-Type": "text/plain"})

        if res.status_code == 200:
            data = res.json()
            is_siren = data.get("isSirenConfirmed", False)
            sound_class = data.get("soundClass", "")
            conf = data.get("confidence", 0.0)

            passed = (is_siren == should_be_siren)
            status_str = "PASS" if passed else "FAIL"
            print(f"  [{status_str}] Audio: {fname:25s} | Expected: {expected_sound:15s} (Siren={should_be_siren}) -> Detected: {sound_class} (Siren={is_siren}, Conf={conf*100:.1f}%)")
            results[f"Audio_{expected_sound}"] = passed
        else:
            print(f"  [FAIL] HTTP Error: {res.status_code} for {fname}")
            results[f"Audio_{expected_sound}"] = False

    # ---------------------------------------------------------
    # TEST 3: Strict Dual Verification & Signal Control (Bug 1)
    # ---------------------------------------------------------
    print("\n--- TEST GROUP C: STRICT DUAL VERIFICATION & TRAFFIC SIGNAL CONTROL ---")

    # Scenario 1: Camera Only -> All RED
    requests.post(f"{BACKEND_BASE}/api/emergency/reset")
    time.sleep(0.5)
    with open(os.path.join(SAMPLE_DIR, "ambulance_positive_01.jpg"), "rb") as f:
        b64_amb = "data:image/jpeg;base64," + base64.b64encode(f.read()).decode("utf-8")
    requests.post(f"{BACKEND_BASE}/api/camera/detect-frame?road=NORTH", data=b64_amb, headers={"Content-Type": "text/plain"})
    time.sleep(1.0)
    st = requests.get(f"{BACKEND_BASE}/api/traffic/state").json()
    signals = st.get("signals", {})
    all_red = all(v == "RED" for v in signals.values())
    print(f"  [{'PASS' if all_red else 'FAIL'}] Scenario: Camera Only -> All Signals RED: {signals} (EmergencyActive={st.get('emergencyActive')})")
    results["Single_Camera_All_Red"] = all_red

    # Scenario 2: Siren Only -> All RED
    requests.post(f"{BACKEND_BASE}/api/emergency/reset")
    time.sleep(0.5)
    with open(os.path.join(SAMPLE_DIR, "siren_positive_01.wav"), "rb") as f:
        b64_siren = base64.b64encode(f.read()).decode("utf-8")
    requests.post(f"{BACKEND_BASE}/api/audio/detect-chunk?road=NORTH", data=b64_siren, headers={"Content-Type": "text/plain"})
    time.sleep(1.0)
    st = requests.get(f"{BACKEND_BASE}/api/traffic/state").json()
    signals = st.get("signals", {})
    all_red = all(v == "RED" for v in signals.values())
    print(f"  [{'PASS' if all_red else 'FAIL'}] Scenario: Siren Only -> All Signals RED: {signals} (EmergencyActive={st.get('emergencyActive')})")
    results["Single_Siren_All_Red"] = all_red

    # Scenario 3: Road Mismatch (Camera NORTH, Siren EAST) -> Rejected, All RED
    requests.post(f"{BACKEND_BASE}/api/emergency/reset")
    time.sleep(0.5)
    requests.post(f"{BACKEND_BASE}/api/camera/detect-frame?road=NORTH", data=b64_amb, headers={"Content-Type": "text/plain"})
    requests.post(f"{BACKEND_BASE}/api/audio/detect-chunk?road=EAST", data=b64_siren, headers={"Content-Type": "text/plain"})
    time.sleep(1.0)
    st = requests.get(f"{BACKEND_BASE}/api/traffic/state").json()
    signals = st.get("signals", {})
    all_red = all(v == "RED" for v in signals.values())
    print(f"  [{'PASS' if all_red else 'FAIL'}] Scenario: Road Mismatch (NORTH vs EAST) -> Verification Rejected, All RED: {signals}")
    results["Road_Mismatch_Rejected"] = all_red

    # Scenario 4: Strict Dual Confirmation (Camera NORTH + Siren NORTH within 3000ms) -> NORTH GREEN, Others RED
    requests.post(f"{BACKEND_BASE}/api/emergency/reset")
    time.sleep(0.5)
    requests.post(f"{BACKEND_BASE}/api/camera/detect-frame?road=NORTH", data=b64_amb, headers={"Content-Type": "text/plain"})
    requests.post(f"{BACKEND_BASE}/api/audio/detect-chunk?road=NORTH", data=b64_siren, headers={"Content-Type": "text/plain"})
    time.sleep(1.0)
    st = requests.get(f"{BACKEND_BASE}/api/traffic/state").json()
    signals = st.get("signals", {})
    north_green = signals.get("NORTH") == "GREEN" and signals.get("SOUTH") == "RED" and signals.get("EAST") == "RED" and signals.get("WEST") == "RED"
    print(f"  [{'PASS' if north_green else 'FAIL'}] Scenario: Dual Confirmed on NORTH -> NORTH GREEN, Others RED: {signals} (Phase={st.get('phaseType')})")
    results["Dual_Confirmed_North_Green"] = north_green

    # Scenario 5: Emergency Reset -> ALL_RED_IDLE
    reset_res = requests.post(f"{BACKEND_BASE}/api/emergency/reset").json()
    time.sleep(0.5)
    st = requests.get(f"{BACKEND_BASE}/api/traffic/state").json()
    signals = st.get("signals", {})
    idle_all_red = st.get("phaseType") == "ALL_RED_IDLE" and all(v == "RED" for v in signals.values())
    print(f"  [{'PASS' if idle_all_red else 'FAIL'}] Scenario: Reset Emergency -> System ALL_RED_IDLE, All RED: {signals}")
    results["Emergency_Reset_Idle"] = idle_all_red

    print("\n" + "=" * 70)
    all_passed = all(results.values())
    print(f"TEST SUMMARY: {sum(results.values())}/{len(results)} PASSED | ALL SUITE PASSED: {all_passed}")
    print("=" * 70)

if __name__ == "__main__":
    run_tests()

