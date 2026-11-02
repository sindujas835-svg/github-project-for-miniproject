import urllib.request
import urllib.parse
import json
import time

BASE_URL = "http://localhost:8080/api"

def get_state():
    req = urllib.request.Request(f"{BASE_URL}/traffic/state")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def reset_emergency():
    req = urllib.request.Request(f"{BASE_URL}/emergency/reset", data=b"", method="POST")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def trigger_emergency(road, siren_road=None, delta_ms=100, include_camera=True, include_siren=True):
    params = {
        "road": road,
        "includeCamera": str(include_camera).lower(),
        "includeSiren": str(include_siren).lower(),
        "deltaMs": str(delta_ms)
    }
    if siren_road:
        params["sirenRoad"] = siren_road
    query = urllib.parse.urlencode(params)
    url = f"{BASE_URL}/emergency/trigger?{query}"
    req = urllib.request.Request(url, data=b"", method="POST")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

if __name__ == "__main__":
    print("Step 1: Resetting intersection to idle...")
    reset_emergency()
    time.sleep(0.5)
    s = get_state()
    print(f"State: phaseType={s['phaseType']}, signals={s['signals']}")
    assert s['phaseType'] == "ALL_RED_IDLE"
    assert all(sig == "RED" for sig in s['signals'].values()), "All signals must be RED"

    print("\nStep 2: Testing Camera Only (Ambulance detected on NORTH, No Siren)...")
    trigger_emergency(road="NORTH", include_camera=True, include_siren=False)
    time.sleep(0.5)
    s = get_state()
    print(f"State: phaseType={s['phaseType']}, emergencyActive={s['emergencyActive']}, signals={s['signals']}")
    assert s['emergencyActive'] is False, "Camera alone must NOT activate emergency green"
    assert all(sig == "RED" for sig in s['signals'].values()), "All signals must remain RED on camera alone"

    print("\nStep 3: Testing Siren Only (Siren detected on NORTH, No Camera)...")
    reset_emergency()
    trigger_emergency(road="NORTH", include_camera=False, include_siren=True)
    time.sleep(0.5)
    s = get_state()
    print(f"State: phaseType={s['phaseType']}, emergencyActive={s['emergencyActive']}, signals={s['signals']}")
    assert s['emergencyActive'] is False, "Siren alone must NOT activate emergency green"
    assert all(sig == "RED" for sig in s['signals'].values()), "All signals must remain RED on siren alone"

    print("\nStep 4: Testing Road Mismatch (Camera NORTH, Siren EAST)...")
    reset_emergency()
    trigger_emergency(road="NORTH", siren_road="EAST", include_camera=True, include_siren=True)
    time.sleep(0.5)
    s = get_state()
    print(f"State: phaseType={s['phaseType']}, emergencyActive={s['emergencyActive']}, signals={s['signals']}")
    assert s['emergencyActive'] is False, "Road mismatch must NOT activate emergency green"
    assert all(sig == "RED" for sig in s['signals'].values()), "All signals must remain RED on road mismatch"

    print("\nStep 5: Testing Dual Confirmation on Same Road (Camera NORTH, Siren NORTH within 3000ms)...")
    reset_emergency()
    trigger_emergency(road="NORTH", siren_road="NORTH", delta_ms=150, include_camera=True, include_siren=True)
    # Wait for safe all-red pre-clearance transition into EMERGENCY_GREEN
    s = get_state()
    for _ in range(6):
        time.sleep(0.5)
        s = get_state()
        print(f"State: phaseType={s['phaseType']}, activeRoad={s['activeRoad']}, emergencyActive={s['emergencyActive']}, signals={s['signals']}")
        if s['emergencyActive'] and s['phaseType'] == "EMERGENCY_GREEN":
            break
    assert s['emergencyActive'] is True, "Dual confirmation MUST activate emergency priority"
    assert s['signals']['NORTH'] == "GREEN", "NORTH must be GREEN"
    assert s['signals']['SOUTH'] == "RED", "SOUTH must be RED"
    assert s['signals']['EAST'] == "RED", "EAST must be RED"
    assert s['signals']['WEST'] == "RED", "WEST must be RED"

    print("\nStep 6: Testing Reset Emergency Button action...")
    reset_emergency()
    time.sleep(0.5)
    s = get_state()
    print(f"State: phaseType={s['phaseType']}, emergencyActive={s['emergencyActive']}, signals={s['signals']}")
    assert s['phaseType'] == "ALL_RED_IDLE"
    assert s['emergencyActive'] is False
    assert all(sig == "RED" for sig in s['signals'].values()), "All signals must return to RED on reset"

    print("\nALL END-TO-END DUAL-VERIFICATION LOGIC SCENARIOS PASSED 100%!")

