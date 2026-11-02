import urllib.request
import urllib.parse
import json
import base64
import os

BASE_URL = "http://localhost:8080/api"
SAMPLES_DIR = r"c:\Users\shri\Downloads\traffic-routing-prototype\sample_test_files"

def test_camera_detection(filename, expected_detected):
    filepath = os.path.join(SAMPLES_DIR, filename)
    with open(filepath, "rb") as f:
        data = f.read()
    b64_img = "data:image/jpeg;base64," + base64.b64encode(data).decode("utf-8")
    
    url = f"{BASE_URL}/camera/detect-frame?road=NORTH"
    req = urllib.request.Request(url, data=b64_img.encode("utf-8"), headers={"Content-Type": "text/plain"})
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        print(f"[Camera Test] {filename} -> isAmbulanceDetected: {res.get('isAmbulanceDetected')}, maxConfidence: {res.get('maxConfidence')}")
        assert res.get('isAmbulanceDetected') == expected_detected, f"Expected {expected_detected} but got {res.get('isAmbulanceDetected')}"
        if expected_detected:
            assert res.get('maxConfidence') >= 0.70, f"Confidence {res.get('maxConfidence')} < 0.70"

def test_audio_detection(filename, expected_siren):
    filepath = os.path.join(SAMPLES_DIR, filename)
    with open(filepath, "rb") as f:
        wav_bytes = f.read()
    
    # Read raw PCM from WAV (skip 44-byte header)
    pcm_data = wav_bytes[44:]
    b64_pcm = base64.b64encode(pcm_data).decode("utf-8")
    
    url = f"{BASE_URL}/audio/detect-chunk?road=NORTH"
    req = urllib.request.Request(url, data=b64_pcm.encode("utf-8"), headers={"Content-Type": "text/plain"})
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        print(f"[Audio Test] {filename} -> isSirenConfirmed: {res.get('isSirenConfirmed')}, soundClass: {res.get('soundClass')}, confidence: {res.get('confidence')}")
        assert res.get('isSirenConfirmed') == expected_siren, f"Expected isSirenConfirmed={expected_siren} but got {res.get('isSirenConfirmed')}"
        if expected_siren:
            assert res.get('confidence') >= 0.70, f"Confidence {res.get('confidence')} < 0.70"

if __name__ == "__main__":
    print("Testing Ambulance Image (Positive)...")
    test_camera_detection("ambulance_positive_01.jpg", True)
    
    print("Testing Civilian Car Image (Negative)...")
    test_camera_detection("civilian_car_negative_01.jpg", False)
    
    print("Testing Siren Audio (Positive)...")
    test_audio_detection("siren_positive_01.wav", True)
    
    print("Testing Car Horn Audio (Negative)...")
    test_audio_detection("car_horn_negative_01.wav", False)
    
    print("Testing Traffic Noise Audio (Negative)...")
    test_audio_detection("traffic_noise_negative_01.wav", False)
    
    print("\nALL SAMPLE FILE INFERENCE TESTS PASSED 100%!")

