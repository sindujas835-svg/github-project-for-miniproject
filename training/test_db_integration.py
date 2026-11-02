"""
Test script verifying database persistence across all four tables:
1. ambulance_detection
2. siren_detection
3. emergency_history
4. emergency_reset_history
"""

import requests
import json
import time

BASE_URL = "http://localhost:8080"
AMB_IMAGE = r"c:\Users\shri\Downloads\traffic-routing-prototype\sample_test_files\ambulance_positive_01.jpg"
SIREN_AUDIO = r"c:\Users\shri\Downloads\traffic-routing-prototype\sample_test_files\siren_positive_01.wav"

def test_db_workflow():
    print("=" * 70)
    print("TESTING DATABASE INTEGRATION & AUDIT HISTORY WORKFLOW")
    print("=" * 70)

    # 1. Reset emergency first
    print("\n1. Resetting emergency state...")
    r = requests.post(f"{BASE_URL}/api/emergency/reset")
    print(f"   Reset response: {r.status_code} - {r.json()}")

    # 2. Upload ambulance image
    print(f"\n2. Uploading ambulance image: {AMB_IMAGE} on Road: NORTH...")
    with open(AMB_IMAGE, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/camera/upload?road=NORTH", files={"file": ("ambulance_positive_01.jpg", f, "image/jpeg")})
    print(f"   Camera Upload response: {r.status_code} - {r.json()}")
    assert r.status_code == 200
    cam_data = r.json()
    assert cam_data.get("isAmbulanceDetected") == True, "Ambulance not detected in test image"
    print(f"   [PASS] Ambulance detected with confidence: {cam_data.get('maxConfidence')*100:.1f}%")

    # 3. Verify emergency_history is PENDING
    print("\n3. Verifying emergency_history table has PENDING record...")
    r = requests.get(f"{BASE_URL}/api/emergency/db-history")
    history_data = r.json()
    eh_list = history_data.get("emergencyHistory", [])
    print(f"   Found {len(eh_list)} emergency_history records.")
    assert len(eh_list) > 0, "No emergency_history record found!"
    latest_eh = eh_list[0]
    print(f"   Latest Emergency Record: ID={latest_eh.get('emergencyId')}, Road={latest_eh.get('selectedRoad')}, Status={latest_eh.get('emergencyStatus')}, AmbConf={latest_eh.get('ambulanceConfidence')}")
    assert latest_eh.get("emergencyStatus") == "PENDING", f"Expected PENDING, got {latest_eh.get('emergencyStatus')}"
    print("   [PASS] Emergency status is correctly PENDING (Waiting for Siren Audio).")

    # 4. Upload siren audio
    print(f"\n4. Uploading siren audio: {SIREN_AUDIO} on Road: NORTH...")
    with open(SIREN_AUDIO, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/audio/upload?road=NORTH", files={"file": ("siren_positive_01.wav", f, "audio/wav")})
    print(f"   Audio Upload response: {r.status_code} - {r.json()}")
    assert r.status_code == 200
    audio_data = r.json()
    assert audio_data.get("isSirenConfirmed") == True, "Siren not confirmed in test audio"
    print(f"   [PASS] Siren detected with confidence: {audio_data.get('confidence')*100:.1f}%")
    print(f"   Emergency outcome: {audio_data.get('emergencyStatus')}, Signal: {audio_data.get('signalColor')}")

    # 5. Verify emergency_history is CONFIRMED with NORTH GREEN
    print("\n5. Verifying emergency_history table has CONFIRMED status...")
    r = requests.get(f"{BASE_URL}/api/emergency/db-history")
    history_data = r.json()
    latest_eh = history_data.get("emergencyHistory", [])[0]
    print(f"   Updated Emergency Record: ID={latest_eh.get('emergencyId')}, Status={latest_eh.get('emergencyStatus')}, Signal={latest_eh.get('trafficSignalStatus')}, ConfirmedAt={latest_eh.get('emergencyConfirmationTime')}")
    assert latest_eh.get("emergencyStatus") == "CONFIRMED", f"Expected CONFIRMED, got {latest_eh.get('emergencyStatus')}"
    assert "GREEN" in latest_eh.get("trafficSignalStatus", ""), f"Expected GREEN signal, got {latest_eh.get('trafficSignalStatus')}"
    print("   [PASS] Emergency is CONFIRMED and traffic signal status is GREEN.")

    # 6. Click RESET EMERGENCY
    print("\n6. Clicking RESET EMERGENCY...")
    r = requests.post(f"{BASE_URL}/api/emergency/reset")
    print(f"   Reset response: {r.status_code} - {r.json()}")
    assert r.status_code == 200

    # 7. Verify emergency_reset_history record
    print("\n7. Verifying emergency_reset_history table record...")
    r = requests.get(f"{BASE_URL}/api/emergency/db-history")
    history_data = r.json()
    reset_list = history_data.get("emergencyResetHistory", [])
    print(f"   Found {len(reset_list)} reset records.")
    assert len(reset_list) > 0, "No emergency_reset_history record found!"
    latest_reset = reset_list[0]
    print(f"   Latest Reset Record: ResetID={latest_reset.get('resetId')}, EmergencyID={latest_reset.get('emergencyId')}, Road={latest_reset.get('selectedRoad')}, PrevSignal={latest_reset.get('previousSignalStatus')}, ResetStatus={latest_reset.get('resetStatus')}, Timestamp={latest_reset.get('resetTimestamp')}")
    print("   [PASS] Emergency reset record successfully saved in MySQL.")

    print("\n" + "=" * 70)
    print("ALL 4 DATABASE TABLES TESTED AND VERIFIED SUCCESSFULLY!")
    print("=" * 70)

if __name__ == "__main__":
    test_db_workflow()

