# AI-Powered Intelligent Traffic Management & Emergency Vehicle Routing System

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-green.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![OpenCV](https://img.shields.io/badge/OpenCV-Java%204.9.0-red.svg)](https://opencv.org/)
[![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-1.19.0-purple.svg)](https://onnxruntime.ai/)
[![Tests](https://img.shields.io/badge/Tests-46%20Passing-brightgreen.svg)]()

> A robust, real-time intelligent traffic intersection and emergency vehicle priority preemption system built with a **Java-first architecture**. It combines computer vision (OpenCV + ONNX) and acoustic signal processing (FFT + ONNX) to verify ambulances and grant safe green corridors without risking collisions.

---

## 1. Project Objective & Core Problem

Traditional traffic lights operate on rigid static timers or simple loop detectors. Emergency vehicles (ambulances, fire trucks) frequently get stuck behind red signals, causing critical delays. Conversely, naive priority systems that trigger green lights upon hearing any sound can be tricked by car horns, or worse, can trigger simultaneous green lights and cause fatal intersection collisions.

### The Solution: Same-Road Dual-Sensor Fusion
This system strictly enforces that emergency vehicle priority is granted **ONLY** when both:
1. **Live Camera-Based Ambulance Detection** (Vision)
2. **Live Microphone-Based Siren Detection** (Acoustics)

are confirmed on the **EXACT SAME ROAD** (e.g. NORTH) within a configurable time window ($\Delta t \le 3.0\text{ seconds}$).

```
+---------------------------------------------------------------------------------+
|                       SAME-ROAD DUAL CONFIRMATION MATRIX                        |
+--------------------------+--------------------------+---------------------------+
| Vision Sensor (Camera)   | Acoustic Sensor (Siren)  | System Decision & Action  |
+--------------------------+--------------------------+---------------------------+
| Ambulance on NORTH       | Siren on NORTH (<= 3s)   | -> PRIORITY GRANTED       |
| Ambulance on NORTH       | No Siren Detected        | -> REJECT (No Priority)   |
| No Ambulance Detected    | Siren on NORTH           | -> REJECT (No Priority)   |
| Ambulance on NORTH       | Siren on EAST (Mismatch) | -> REJECT (Road Mismatch) |
| Ambulance on NORTH       | Siren on NORTH (> 3s)    | -> REJECT (Window Exceed) |
| Confidence < 70%         | Confidence >= 70%        | -> REJECT (Low Conf)      |
+--------------------------+--------------------------+---------------------------+
```

---

## 2. High-Level Architecture

The system consists of a Spring Boot Java backend, a MySQL persistence layer, native AI/DSP inference engines, and a real-time reactive HTML5/CSS3/JavaScript dashboard.

```
                           +--------------------------------------+
                           |   INTERSECTION SURVEILLANCE & AUDIO  |
                           |  [Webcam / RTSP]    [Directional Mic]|
                           +------------------+-------------------+
                                              |
                     +------------------------+------------------------+
                     |                                                 |
                     v                                                 v
         +-----------------------+                         +-----------------------+
         |  AmbulanceVisionService|                         |EmergencySirenAudioSvc |
         |  - OpenCV Frame Capture|                         | - Java Sound Line     |
         |  - 640x640 Preprocess |                         | - Sliding Window (1s) |
         |  - ONNX Runtime Inference                       | - Radix-2 FFT Pitch   |
         |  - YoloPostProcessor  |                         | - 650-1550Hz Energy   |
         |  - CentroidTracker    |                         | - Debounce Filter (2x)|
         |  - RoadRoi Spatial Map|                         +-----------+-----------+
         +-----------+-----------+                                     |
                     |                                                 |
                     +------------------------+------------------------+
                                              |
                                              v
                           +--------------------------------------+
                           |     EmergencyVerificationService     |
                           |   (Strict Same-Road Dual Matcher)    |
                           +------------------+-------------------+
                                              |
                                              v
                           +--------------------------------------+
                           |        EmergencyQueueService         |
                           |  (PriorityQueue: Critical > Normal)  |
                           +------------------+-------------------+
                                              |
                                              v
                           +--------------------------------------+
                           |        TrafficSignalController       |
                           |  - Non-blocking 1Hz Scheduler        |
                           |  - Conflicting Green Safe Decel      |
                           |  - TrafficSafetyGuard Interlock      |
                           |  - WebSocket STOMP Broadcast         |
                           +------------------+-------------------+
                                              |
                     +------------------------+------------------------+
                     |                                                 |
                     v                                                 v
         +-----------------------+                         +-----------------------+
         |      MySQL Database   |                         |   Frontend Dashboard  |
         |  - ambulance_detections                         |  - SVG 4-Way Road Map |
         |  - siren_detections   |                         |  - Vehicle Physics Sim|
         |  - emergency_queue    |                         |  - Live Perception UI |
         |  - traffic_history    |                         |  - Scenario Test Suite|
         +-----------------------+                         +-----------------------+
```

---

## 3. Engineering Concepts Explained for Students

### A. Non-Blocking Traffic State Machine (`TrafficSignalController.java`)
Traffic signals must never use `Thread.sleep()` because sleeping freezes the entire server, preventing camera processing, API requests, and emergency preemption. Instead, a Java `ScheduledExecutorService` ticks once per second (1 Hz), decrementing `secondsRemaining` and updating an atomic state machine:
- **Normal Cycle**: North (Green 8s, Yellow 3s, All-Red 2s) $\to$ South $\to$ East $\to$ West.
- **Preemption Protocol**:
  1. If conflicting road is Green $\to$ Transition conflicting road to `EMERGENCY_YELLOW` (2s).
  2. Transition all roads to `EMERGENCY_PRE_CLEARANCE` (2s All-Red) to completely flush vehicles out of the junction.
  3. Grant `EMERGENCY_GREEN` (max 15s) to the ambulance's road.
  4. Upon ambulance crossing the exit line $\to$ Transition to `EMERGENCY_POST_CLEARANCE` (2s All-Red).
  5. Safely resume normal sequential cycle from the interrupted road's successor.

### B. Strict Safety Interlock Guard (`TrafficSafetyGuard.java`)
Before any signal change is broadcast or applied, the `TrafficSafetyGuard` evaluates an unbreachable hardware-level invariant:
$$\sum_{r \in \{\text{N, S, E, W}\}} [\text{signal}(r) = \text{GREEN}] \le 1$$
If a concurrency race ever attempted to activate two conflicting greens simultaneously, the guard immediately trips a fail-safe override, forcing all 4 approaches to **ALL-RED** and sounding an emergency alarm.

### C. Multi-Object Centroid Tracking (`CentroidTracker.java`)
Raw object detectors are stateless: they detect boxes frame-by-frame with zero memory. If an ambulance stays in view for 50 frames, a raw detector produces 50 isolated detections.
`CentroidTracker` solves this:
1. Computes box center points: $(c_x = x + w/2, c_y = y + h/2)$.
2. Computes the Euclidean distance matrix:
   $$d = \sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}$$
3. Performs minimum distance greedy matching below 120 pixels.
4. Preserves a persistent `trackId` across consecutive frames, preventing duplicate database records.

### D. Digital Signal Processing & FFT Pitch Extraction (`AudioFeatureExtractor.java`)
An acoustic microphone samples air pressure at 16,000 Hz. The audio feature extractor runs:
1. **Root-Mean-Square (RMS) Energy & Decibels**:
   $$\text{RMS} = \sqrt{\frac{1}{N} \sum_{i=0}^{N-1} x[i]^2}, \quad \text{dB} = 20 \log_{10}(\text{RMS} + 10^{-6}) + 90$$
2. **Hann Windowing**: $w[n] = 0.5 - 0.5 \cos(\frac{2\pi n}{N-1})$ to prevent edge discontinuity artifacts.
3. **Cooley-Tukey Radix-2 FFT**: Converts 2048 samples from time domain to frequency domain in $O(N \log N)$ time.
4. **Siren Band Discrimination**: Emergency sirens oscillate between 650 Hz and 1550 Hz, whereas car horns are around 400 Hz and road noise is below 300 Hz.

### E. Multi-Ambulance Priority Queue (`EmergencyQueueService.java`)
When multiple ambulances approach from different roads:
- Priority 1 = Critical (Cardiac arrest, trauma)
- Priority 2 = High (Emergency transfer)
- Priority 3 = Normal (Routine transit)
Queued vehicles are ordered by `priorityLevel ASC, arrivalOrder ASC`. The first ambulance clears completely through All-Red clearance before the second ambulance receives priority.

---

## 4. Project Structure

```
traffic-routing-prototype/
├── index.html                   # Interactive 4-Way SVG Dashboard & Scenario UI
├── style.css                    # Modern dark-mode styling with LED optics & animations
├── script.js                    # Client poller, SVG vehicle physics, and scenario dispatcher
├── database/
│   └── schema.sql               # Complete MySQL DDL (7 tables, foreign keys, indexes)
├── traffic-backend/
│   └── traffic-backend/
│       ├── .mvn/
│       │   └── maven.config     # Configured for D:\maven-repo (disk space preservation)
│       ├── pom.xml              # Spring Boot, ONNX Runtime, OpenCV, MySQL, WebSocket
│       └── src/
│           ├── main/
│           │   ├── java/com/traffic/traffic_backend/
│           │   │   ├── config/      # CameraConfig, AudioConfig, WebSocketConfig
│           │   │   ├── controller/  # Traffic, Camera, Audio, Emergency, Telemetry REST APIs
│           │   │   ├── detection/
│           │   │   │   ├── audio/   # EmergencySirenAudioService, AudioFeatureExtractor
│           │   │   │   └── camera/  # AmbulanceVisionService, CentroidTracker, YoloPostProcessor, RoadRoi
│           │   │   ├── dto/         # CameraDetectionDto, AudioDetectionDto, EmergencyVerificationDto
│           │   │   ├── entity/      # 9 JPA Entities (AmbulanceDetection, SirenDetection, etc.)
│           │   │   ├── repository/  # 9 Spring Data JPA Repositories
│           │   │   ├── service/     # EmergencyVerificationService, EmergencyQueueService, Telemetry
│           │   │   └── traffic/     # TrafficSignalController, TrafficSafetyGuard, Road, SignalColor
│           │   └── resources/
│           │       └── application.properties # MySQL datasource, port 8080, logging
│           └── test/
│               └── java/com/traffic/traffic_backend/
│                   ├── SystemVerificationIntegrationTest.java # All 15 Evaluation Scenarios
│                   ├── detection/camera/CentroidTrackerTest.java, YoloPostProcessorTest.java
│                   ├── detection/audio/AudioFeatureExtractorTest.java
│                   ├── service/EmergencyVerificationServiceTest.java, EmergencyQueueServiceTest.java
│                   └── traffic/TrafficSignalControllerTest.java, TrafficSafetyGuardTest.java
```

---

## 5. Summary of Automated Verification

All 46 automated unit and integration tests execute cleanly with zero failures:

| Test Suite | Tests Run | Result | Focus |
|---|:---:|:---:|---|
| `TrafficSignalControllerTest` | 8 | PASSED | Preemption state transitions, yellow safety, grace period |
| `TrafficSafetyGuardTest` | 4 | PASSED | Invariant enforcement, zero conflicting greens |
| `CentroidTrackerTest` | 3 | PASSED | Multi-object tracking, continuity, max disappeared frames |
| `YoloPostProcessorTest` | 4 | PASSED | NMS suppression, IoU calculation, RoadRoi boundary check |
| `AudioFeatureExtractorTest` | 3 | PASSED | 1000 Hz siren detection, 200 Hz engine noise rejection |
| `EmergencyVerificationServiceTest` | 4 | PASSED | Same-road confirmation, road mismatch, low confidence |
| `EmergencyQueueServiceTest` | 3 | PASSED | Immediate dispatch, queueing behind active, FIFO order |
| `TelemetryLoggingServiceTest` | 1 | PASSED | JPA persistence and retrieval |
| `TrafficBackendApplicationTests` | 1 | PASSED | Spring Boot context load, MySQL schema verification |
| `SystemVerificationIntegrationTest` | 15 | PASSED | **All 15 complete end-to-end evaluation scenarios** |
| **Total** | **46** | **100% Passed** | **Zero Failures, Zero Errors** |
