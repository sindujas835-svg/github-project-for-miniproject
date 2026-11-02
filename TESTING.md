# Complete System Testing & Scenario Verification Guide

This document provides instructions on how to test and verify all **15 Core Evaluation Scenarios** across the system using:
1. **Automated JUnit 5 Test Suite**
2. **Interactive Frontend Dashboard**
3. **REST API curl Commands**

---

## 1. Running the Automated Scenario Suite

Run the master test class from PowerShell:
```powershell
cd C:\Users\shri\Downloads\traffic-routing-prototype\traffic-backend\traffic-backend
.\mvnw.cmd test -Dtest="SystemVerificationIntegrationTest"
```

All 15 scenarios will execute and validate their respective safety invariants:
```
[INFO] Running com.traffic.traffic_backend.SystemVerificationIntegrationTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- in com.traffic.traffic_backend.SystemVerificationIntegrationTest
[INFO] BUILD SUCCESS
```

---

## 2. Interactive Verification via Frontend Dashboard

Open `index.html` in your browser and click the **Live Monitoring** tab in the sidebar navigation.
Scroll to the **Evaluation Scenario Test Suite** panel, where you will find dedicated buttons for each scenario:

### Scenario 1: Same-Road Dual Confirmation
- **Action:** Click `[Trigger Dual Match]`
- **Expectation:**
  - Both Camera Vision and Siren Audio trigger on `NORTH` within 200 ms.
  - The decision card glows green: `✔ DUAL CONFIRMED on NORTH! Δt: 200ms`.
  - Conflicting road safely transitions to Yellow $\to$ All-Red $\to$ `NORTH` receives Green preemption.
  - SVG map shows ambulance vehicle entering and crossing intersection.

### Scenario 2: Road Mismatch Anomaly
- **Action:** Click `[Test Road Mismatch]`
- **Expectation:**
  - Camera detects ambulance on `NORTH`, but acoustic microphone detects siren on `EAST`.
  - The decision card displays a red warning: `⚠ REJECTED on NORTH: ROAD MISMATCH: Siren on EAST, but Vision on NORTH`.
  - Normal traffic cycling continues uninterrupted. **Zero preemption is granted**.

### Scenario 3: Camera Only (Silent Vehicle)
- **Action:** Click `[Test Camera Only]`
- **Expectation:**
  - Camera sees ambulance on `NORTH`, but no siren is heard.
  - The decision card states: `Awaiting acoustic siren confirmation on Road NORTH`.
  - No emergency priority is granted.

### Scenario 4: Siren Only (Ghost Sound)
- **Action:** Click `[Test Siren Only]`
- **Expectation:**
  - Microphone detects siren on `SOUTH`, but no vehicle is visible in the camera ROI.
  - The decision card states: `Awaiting camera vision confirmation on Road SOUTH`.
  - No emergency priority is granted.

### Scenario 5: Time Window Exceeded
- **Action:** Click `[Test Window Exceeded]`
- **Expectation:**
  - Camera detects at $t=0$, Siren arrives at $t=4.2\text{s}$ ($\Delta t > 3.0\text{s}$).
  - Decision card displays: `⚠ REJECTED on NORTH: Time window exceeded: Δt=4200 ms > 3000 ms threshold`.

### Scenario 6: Low Sensor Confidence
- **Action:** Click `[Test Low Confidence]`
- **Expectation:**
  - Detection confidence is 52%, below the 70% threshold.
  - Decision card displays: `⚠ REJECTED: Insufficient sensor confidence: Vision=0.52, Audio=0.55 (Min required: 0.70)`.

### Scenario 7: Multi-Ambulance Priority Queue
- **Action:** Click `[Test Priority Queue]`
- **Expectation:**
  - Critical Ambulance `AMB-CRITICAL-1` (Priority 1) arrives on `NORTH`.
  - High Ambulance `AMB-HIGH-2` (Priority 2) arrives on `EAST`.
  - `AMB-CRITICAL-1` is dispatched first; `AMB-HIGH-2` is safely enqueued with status `QUEUED`.
  - View the **Emergency Queue** tab to see real-time priority rankings.

### Scenario 8: Ambulance Cleared Exit Line
- **Action:** Click `[Mark Cleared Exit]`
- **Expectation:**
  - Exit sensor notifies that the active ambulance has cleared the junction.
  - Active priority green ends immediately without waiting for max timeout.
  - All signals turn RED for safe All-Red post-clearance (2s).
  - If a queued ambulance was waiting, it is dispatched next; otherwise, normal sequential cycling resumes.

---

## 3. REST API Testing via PowerShell / curl

You can also test the endpoints programmatically:

### 1. Check Intersection State
```powershell
curl http://localhost:8080/api/traffic/state
```

### 2. Check Dual Verification Engine Status
```powershell
curl http://localhost:8080/api/emergency/status
```

### 3. Trigger Same-Road Dual Confirmation (North)
```powershell
curl -X POST "http://localhost:8080/api/emergency/trigger?road=NORTH&vehicleId=AMB-101&priorityLevel=1&includeCamera=true&includeSiren=true&deltaMs=150"
```

### 4. Trigger Road Mismatch (Camera North + Siren East)
```powershell
curl -X POST "http://localhost:8080/api/emergency/trigger?road=NORTH&sirenRoad=EAST&vehicleId=AMB-FAIL&includeCamera=true&includeSiren=true&deltaMs=100"
```

### 5. Mark Ambulance Passed Exit Line
```powershell
curl -X POST "http://localhost:8080/api/emergency/pass?vehicleId=AMB-101"
```

### 6. View Historical Event Log from MySQL
```powershell
curl "http://localhost:8080/api/telemetry/events?limit=10"
```
