import urllib.request
import urllib.parse
import json
import base64
import time
import os

BASE_URL = "http://localhost:8080/api"
SAMPLES_DIR = r"c:\Users\shri\Downloads\traffic-routing-prototype\sample_test_files"

def test_upload_flow():
    # 1. Reset
    print("[1] Resetting emergency...")
    req = urllib.request.Request(f"{BASE_URL}/emergency/reset", data=b"", method="POST")
    with urllib.request.urlopen(req) as resp:
        print("Reset resp:", resp.read().decode("utf-8"))

    time.sleep(0.5)

    # 2. Upload Camera frame
    print("[2] Sending camera detect-frame on NORTH...")
    with open(os.path.join(SAMPLES_DIR, "ambulance_positive_01.jpg"), "rb") as f:
        img_b64 = "data:image/jpeg;base64," + base64.b64encode(f.read()).decode("utf-8")
    req = urllib.request.Request(f"{BASE_URL}/camera/detect-frame?road=NORTH", data=img_b64.encode("utf-8"), headers={"Content-Type": "text/plain"})
    with urllib.request.urlopen(req) as resp:
        cam_res = json.loads(resp.read().decode("utf-8"))
        print("Camera detect result:", cam_res)

    # 3. Wait 500 ms (well within 3000 ms)
    time.sleep(0.5)

    # 4. Upload Audio chunk
    print("[3] Sending audio detect-chunk on NORTH (500 ms delta)...")
    with open(os.path.join(SAMPLES_DIR, "siren_positive_01.wav"), "rb") as f:
        wav_bytes = f.read()
    pcm_b64 = base64.b64encode(wav_bytes[44:]).decode("utf-8")
    req = urllib.request.Request(f"{BASE_URL}/audio/detect-chunk?road=NORTH", data=pcm_b64.encode("utf-8"), headers={"Content-Type": "text/plain"})
    with urllib.request.urlopen(req) as resp:
        aud_res = json.loads(resp.read().decode("utf-8"))
        print("Audio detect result:", aud_res)

    # 5. Check verifications
    req = urllib.request.Request(f"{BASE_URL}/emergency/verifications")
    with urllib.request.urlopen(req) as resp:
        verifs = json.loads(resp.read().decode("utf-8"))
        print(f"Total verifications recorded: {len(verifs)}")
        if verifs:
            print("Latest verification:", verifs[0])

    # 6. Check traffic state for up to 4 seconds to observe phase progression
    print("\n[4] Observing traffic state progression...")
    for step in range(8):
        req = urllib.request.Request(f"{BASE_URL}/traffic/state")
        with urllib.request.urlopen(req) as resp:
            s = json.loads(resp.read().decode("utf-8"))
            print(f"  T+{step*0.5:.1f}s: phaseType={s['phaseType']}, activeRoad={s['activeRoad']}, emergencyActive={s['emergencyActive']}, signals={s['signals']}")
        time.sleep(0.5)

if __name__ == "__main__":
    test_upload_flow()

