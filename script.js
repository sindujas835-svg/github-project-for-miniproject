"use strict";

const BACKEND_BASE = "http://localhost:8080";
const API_URL = `${BACKEND_BASE}/api`;

// -------------------------------------------------------------
// 1. DATA STRUCTURE: IN-MEMORY EMERGENCY PRIORITY QUEUE (FALLBACK)
// -------------------------------------------------------------
class EmergencyPriorityQueue {
    constructor() {
        this.items = [];
        this.counter = 1;
    }

    enqueue(item) {
        item.order = this.counter++;
        this.items.push(item);
    }

    dequeue() {
        if (this.items.length === 0) return null;
        this.items.sort((a, b) => a.priority - b.priority || a.order - b.order);
        return this.items.shift();
    }

    getAll() {
        return [...this.items].sort((a, b) => a.priority - b.priority || a.order - b.order);
    }

    size() {
        return this.items.length;
    }

    clear() {
        this.items = [];
        this.counter = 1;
    }
}

const localQueue = new EmergencyPriorityQueue();
const localHistory = [];

// -------------------------------------------------------------
// 2. STATE MACHINE CLIENT TELEMETRY & POLLER
// -------------------------------------------------------------
let backendOnline = false;
let currentTrafficState = {
    activeRoad: "NORTH",
    nextRoad: "SOUTH",
    phaseType: "ALL_RED_IDLE",
    secondsRemaining: 0,
    signals: {
        NORTH: "RED",
        SOUTH: "RED",
        EAST: "RED",
        WEST: "RED"
    },
    emergencyActive: false,
    emergencyVehicleId: null,
    cycleNumber: 1,
    paused: false
};

// Local fallback state machine if backend is starting or offline
function localFallbackTick() {
    if (backendOnline) return;

    if (currentTrafficState.paused) return;

    // Strict requirement: Intersection remains in ALL_RED_IDLE or EMERGENCY_GREEN without automatic green cycling
    if (currentTrafficState.emergencyActive || currentTrafficState.phaseType === "ALL_RED_IDLE") {
        currentTrafficState.secondsRemaining = 0;
        renderTrafficSignals(currentTrafficState);
        return;
    }

    currentTrafficState.secondsRemaining--;

    if (currentTrafficState.secondsRemaining <= 0) {
        const order = ["NORTH", "SOUTH", "EAST", "WEST"];
        const curIdx = order.indexOf(currentTrafficState.activeRoad);

        if (currentTrafficState.phaseType === "GREEN_PHASE") {
            currentTrafficState.phaseType = "YELLOW_PHASE";
            currentTrafficState.signals[currentTrafficState.activeRoad] = "YELLOW";
            currentTrafficState.secondsRemaining = 3;
        } else if (currentTrafficState.phaseType === "YELLOW_PHASE") {
            currentTrafficState.phaseType = "ALL_RED_CLEARANCE";
            currentTrafficState.signals[currentTrafficState.activeRoad] = "RED";
            currentTrafficState.secondsRemaining = 2;
        } else if (currentTrafficState.phaseType === "ALL_RED_CLEARANCE") {
            const nextIdx = (curIdx + 1) % 4;
            currentTrafficState.activeRoad = order[nextIdx];
            currentTrafficState.nextRoad = order[(nextIdx + 1) % 4];
            currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
            currentTrafficState.signals[currentTrafficState.activeRoad] = "GREEN";
            currentTrafficState.phaseType = "GREEN_PHASE";
            currentTrafficState.secondsRemaining = 8;
            if (currentTrafficState.activeRoad === "NORTH") {
                currentTrafficState.cycleNumber++;
            }
        }
    }

    renderTrafficSignals(currentTrafficState);
}

async function fetchBackendTrafficState() {
    try {
        const response = await fetch(`${API_URL}/traffic/state`, { cache: "no-store" });
        if (response.ok) {
            const data = await response.json();
            currentTrafficState = data;
            if (!backendOnline) {
                backendOnline = true;
                const badge = document.getElementById("backendBadge");
                badge.textContent = "Backend: Connected (Live Java State Machine)";
                badge.className = "badge badge-safe";
            }
            renderTrafficSignals(currentTrafficState);
        } else {
            throw new Error(`HTTP ${response.status}`);
        }
    } catch (e) {
        if (backendOnline) {
            backendOnline = false;
            const badge = document.getElementById("backendBadge");
            badge.textContent = "Backend: Offline (Client Fallback Active)";
            badge.className = "badge badge-warning";
        }
        localFallbackTick();
    }
}

// -------------------------------------------------------------
// 3. RENDER TRAFFIC SIGNALS (CARDS + SVG GRAPHICS)
// -------------------------------------------------------------
function renderTrafficSignals(state) {
    const activeRoadElem = document.getElementById("activeRoadDisplay");
    const activePhaseElem = document.getElementById("activePhaseDisplay");
    const countdownElem = document.getElementById("countdownDisplay");
    const cycleElem = document.getElementById("cycleDisplay");

    if (activeRoadElem) activeRoadElem.textContent = state.activeRoad || "--";
    if (activePhaseElem) {
        activePhaseElem.textContent = state.phaseType ? state.phaseType.replaceAll("_", " ") : "--";
        activePhaseElem.className = "value " + (
            state.phaseType === "GREEN_PHASE" || state.phaseType === "EMERGENCY_GREEN" ? "phase-green" :
                state.phaseType === "YELLOW_PHASE" || state.phaseType === "EMERGENCY_YELLOW" ? "phase-yellow" : "phase-red"
        );
    }
    if (countdownElem) {
        countdownElem.textContent = String(Math.max(0, state.secondsRemaining)).padStart(2, "0") + "s";
    }
    if (cycleElem) cycleElem.textContent = state.cycleNumber;

    // Update pause/resume button label
    const pauseBtn = document.getElementById("pauseResumeBtn");
    if (pauseBtn) {
        pauseBtn.innerHTML = state.paused ? "&#9658; Resume Cycle" : "&#10074;&#10074; Pause Cycle";
    }

    // Update individual approach cards
    ["North", "South", "East", "West"].forEach(dir => {
        const roadKey = dir.toUpperCase();
        const color = state.signals ? state.signals[roadKey] : "RED";

        const light = document.getElementById("light" + dir);
        const label = document.getElementById("state" + dir);
        const card = document.getElementById("card" + dir);
        const badge = document.getElementById("badge" + dir);

        if (light) {
            light.querySelectorAll("span").forEach(s => {
                s.classList.remove("on");
                s.classList.remove("active");
            });
            if (color === "RED") {
                light.querySelector(".red")?.classList.add("on", "active");
            } else if (color === "YELLOW") {
                light.querySelector(".yellow")?.classList.add("on", "active");
            } else if (color === "GREEN") {
                light.querySelector(".green")?.classList.add("on", "active");
            }
        }

        if (label) {
            label.textContent = color.toUpperCase();
            label.className = `signal-state-label ${color === "GREEN" ? "text-safe" : color === "YELLOW" ? "text-warning" : "text-danger"}`;
            label.style.color = color === "GREEN" ? "var(--accent-green)" :
                color === "YELLOW" ? "var(--accent-yellow)" : "var(--accent-red)";
        }

        if (card) {
            card.classList.toggle("active-card", color === "GREEN");
        }

        if (badge) {
            badge.textContent = color === "GREEN" ? "Priority Green" : "Holding";
            badge.className = "badge " + (color === "GREEN" ? "badge-safe" : "badge-muted");
        }
    });

    // Update SVG Traffic Light Graphics
    updateSvgOptic("North", state.signals?.NORTH);
    updateSvgOptic("South", state.signals?.SOUTH);
    updateSvgOptic("East", state.signals?.EAST);
    updateSvgOptic("West", state.signals?.WEST);

    // Update ambulance representation visibility and road approach positioning
    const ambSvg = document.getElementById("svgAmbulance");
    if (ambSvg) {
        if (state.emergencyActive) {
            ambSvg.style.display = "block";
            const targetRoad = state.activeRoad || "NORTH";

            // Position ambulance on the corresponding approach arm
            if (state.phaseType === "EMERGENCY_GREEN") {
                // Crossing center of intersection
                ambSvg.setAttribute("transform", "translate(300, 300)");
            } else if (targetRoad === "NORTH") {
                ambSvg.setAttribute("transform", "translate(265, 120)");
            } else if (targetRoad === "SOUTH") {
                ambSvg.setAttribute("transform", "translate(335, 480) rotate(180)");
            } else if (targetRoad === "EAST") {
                ambSvg.setAttribute("transform", "translate(480, 265) rotate(90)");
            } else if (targetRoad === "WEST") {
                ambSvg.setAttribute("transform", "translate(120, 335) rotate(270)");
            }
        } else {
            ambSvg.style.display = "none";
        }
    }

    // Update status notices and badges
    const cycleNotice = document.getElementById("cycleStatusNotice");
    if (cycleNotice) {
        if (state.emergencyActive) {
            cycleNotice.innerHTML = `&#128657; <strong>EMERGENCY PREEMPTION ACTIVE:</strong> Road <strong>${state.activeRoad}</strong> granted priority for <strong>${state.emergencyVehicleId || "Ambulance"}</strong>. Phase: <em>${state.phaseType ? state.phaseType.replaceAll("_", " ") : ""}</em>.`;
            cycleNotice.style.borderColor = "var(--accent-red)";
            cycleNotice.style.color = "var(--accent-red)";
        } else if (state.phaseType === "ALL_RED_IDLE") {
            cycleNotice.innerHTML = `&#9888; <strong>SAFE ALL-RED IDLE:</strong> All signals RED. Standing by for verified emergency approach.`;
            cycleNotice.style.borderColor = "rgba(56, 139, 253, 0.3)";
            cycleNotice.style.color = "var(--accent-blue)";
        } else {
            cycleNotice.innerHTML = `Active traffic phase: <strong>${state.phaseType ? state.phaseType.replaceAll("_", " ") : "ALL RED"}</strong> on road <strong>${state.activeRoad}</strong>.`;
            cycleNotice.style.borderColor = "rgba(56, 139, 253, 0.3)";
            cycleNotice.style.color = "var(--accent-blue)";
        }
    }
}

function updateSvgOptic(dir, color) {
    const redCircle = document.getElementById(`svg${dir}Red`);
    const yellowCircle = document.getElementById(`svg${dir}Yellow`);
    const greenCircle = document.getElementById(`svg${dir}Green`);

    if (!redCircle || !yellowCircle || !greenCircle) return;

    // Reset all to dim off state
    redCircle.setAttribute("fill", "#381515");
    redCircle.removeAttribute("filter");
    yellowCircle.setAttribute("fill", "#3a3315");
    yellowCircle.removeAttribute("filter");
    greenCircle.setAttribute("fill", "#153820");
    greenCircle.removeAttribute("filter");

    if (color === "RED") {
        redCircle.setAttribute("fill", "#ff4d4d");
        redCircle.setAttribute("filter", "url(#glowRed)");
    } else if (color === "YELLOW") {
        yellowCircle.setAttribute("fill", "#ffea00");
        yellowCircle.setAttribute("filter", "url(#glowYellow)");
    } else if (color === "GREEN") {
        greenCircle.setAttribute("fill", "#2ecc71");
        greenCircle.setAttribute("filter", "url(#glowGreen)");
    }
}

// -------------------------------------------------------------
// 4. MOVING VEHICLE PHYSICS SIMULATION (SVG ROADWAY)
// -------------------------------------------------------------
class VehicleSimulation {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        if (!this.container) return;
        this.vehicles = [
            // North approach (heading South): lane x=265, stop y=190
            { id: "vN1", dir: "NORTH", x: 265, y: 50, speed: 1.6, color: "#3498db", width: 22, height: 38 },
            // South approach (heading North): lane x=335, stop y=410
            { id: "vS1", dir: "SOUTH", x: 335, y: 550, speed: 1.6, color: "#9b59b6", width: 22, height: 38 },
            // West approach (heading East): lane y=335, stop x=190
            { id: "vW1", dir: "WEST", x: 50, y: 335, speed: 1.6, color: "#e67e22", width: 38, height: 22 },
            // East approach (heading West): lane y=265, stop x=410
            { id: "vE1", dir: "EAST", x: 550, y: 265, speed: 1.6, color: "#1abc9c", width: 38, height: 22 }
        ];
        this.initDom();
        this.animate = this.animate.bind(this);
        requestAnimationFrame(this.animate);
    }

    initDom() {
        if (!this.container) return;
        this.container.innerHTML = "";
        this.vehicles.forEach(v => {
            const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
            rect.setAttribute("id", v.id);
            rect.setAttribute("width", v.width);
            rect.setAttribute("height", v.height);
            rect.setAttribute("rx", "4");
            rect.setAttribute("fill", v.color);
            rect.setAttribute("stroke", "#ffffff");
            rect.setAttribute("stroke-width", "1");
            this.container.appendChild(rect);
        });
    }

    animate() {
        if (!this.container) return;
        const signals = currentTrafficState.signals || {};

        this.vehicles.forEach(v => {
            const isGreen = signals[v.dir] === "GREEN";

            if (v.dir === "NORTH") {
                // Moving Down (y increases). Stop line at y=180
                const atStop = v.y >= 170 && v.y <= 185;
                if (isGreen || !atStop) {
                    v.y += v.speed;
                }
                if (v.y > 640) v.y = -40; // Recycle vehicle
                const elem = document.getElementById(v.id);
                if (elem) {
                    elem.setAttribute("x", v.x - v.width / 2);
                    elem.setAttribute("y", v.y - v.height / 2);
                }
            } else if (v.dir === "SOUTH") {
                // Moving Up (y decreases). Stop line at y=420
                const atStop = v.y <= 430 && v.y >= 415;
                if (isGreen || !atStop) {
                    v.y -= v.speed;
                }
                if (v.y < -40) v.y = 640;
                const elem = document.getElementById(v.id);
                if (elem) {
                    elem.setAttribute("x", v.x - v.width / 2);
                    elem.setAttribute("y", v.y - v.height / 2);
                }
            } else if (v.dir === "WEST") {
                // Moving Right (x increases). Stop line at x=180
                const atStop = v.x >= 170 && v.x <= 185;
                if (isGreen || !atStop) {
                    v.x += v.speed;
                }
                if (v.x > 640) v.x = -40;
                const elem = document.getElementById(v.id);
                if (elem) {
                    elem.setAttribute("x", v.x - v.width / 2);
                    elem.setAttribute("y", v.y - v.height / 2);
                }
            } else if (v.dir === "EAST") {
                // Moving Left (x decreases). Stop line at x=420
                const atStop = v.x <= 430 && v.x >= 415;
                if (isGreen || !atStop) {
                    v.x -= v.speed;
                }
                if (v.x < -40) v.x = 640;
                const elem = document.getElementById(v.id);
                if (elem) {
                    elem.setAttribute("x", v.x - v.width / 2);
                    elem.setAttribute("y", v.y - v.height / 2);
                }
            }
        });

        requestAnimationFrame(this.animate);
    }
}

// -------------------------------------------------------------
// 5. BUTTON CONTROLS & API ACTIONS
// -------------------------------------------------------------
async function togglePause() {
    try {
        const endpoint = currentTrafficState.paused ? "/api/traffic/resume" : "/api/traffic/pause";
        await fetch(`${BACKEND_BASE}${endpoint}`, { method: "POST" });
        currentTrafficState.paused = !currentTrafficState.paused;
    } catch (e) {
        currentTrafficState.paused = !currentTrafficState.paused;
    }
    renderTrafficSignals(currentTrafficState);
}

async function resetTrafficCycle() {
    try {
        await fetch(`${BACKEND_BASE}/api/traffic/reset`, { method: "POST" });
    } catch (e) {
        currentTrafficState.activeRoad = "NORTH";
        currentTrafficState.nextRoad = "SOUTH";
        currentTrafficState.phaseType = "GREEN_PHASE";
        currentTrafficState.secondsRemaining = 8;
        currentTrafficState.signals = { NORTH: "GREEN", SOUTH: "RED", EAST: "RED", WEST: "RED" };
        currentTrafficState.emergencyActive = false;
    }
    fetchBackendTrafficState();
}

async function testEmergencyDispatch() {
    const roadSelect = document.getElementById("emergencyRoadSelect");
    const road = roadSelect ? roadSelect.value : "NORTH";
    const vehicleId = "AMB-" + road.charAt(0) + "-" + Math.floor(100 + Math.random() * 900);

    try {
        await fetch(`${BACKEND_BASE}/api/traffic/test-emergency?road=${road}&vehicleId=${vehicleId}&priority=1`, { method: "POST" });
        fetchBackendTrafficState();
    } catch (e) {
        alert("Backend not reachable. Start Spring Boot to execute live emergency preemption.");
    }
}

async function notifyAmbulancePassed() {
    const vehicleId = currentTrafficState.emergencyVehicleId || "AMB-DEMO-01";
    try {
        await fetch(`${BACKEND_BASE}/api/traffic/test-passed?vehicleId=${vehicleId}`, { method: "POST" });
        fetchBackendTrafficState();
    } catch (e) {
        alert("Ambulance passed exit notification dispatched.");
    }
}

async function releaseEmergency() {
    try {
        await fetch(`${BACKEND_BASE}/api/traffic/test-release`, { method: "POST" });
        fetchBackendTrafficState();
    } catch (e) {
        alert("Emergency released.");
    }
}

async function updateTimings() {
    const green = parseInt(document.getElementById("cfgGreen").value, 10);
    const yellow = parseInt(document.getElementById("cfgYellow").value, 10);
    const allRed = parseInt(document.getElementById("cfgAllRed").value, 10);

    const msg = document.getElementById("timingMsg");

    try {
        const response = await fetch(`${BACKEND_BASE}/api/traffic/config`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                greenIntervalSeconds: green,
                yellowIntervalSeconds: yellow,
                allRedClearanceSeconds: allRed
            })
        });

        if (response.ok) {
            msg.textContent = "Backend timings updated successfully!";
            msg.style.color = "var(--accent-green)";
        } else {
            const err = await response.json();
            msg.textContent = "Error: " + (err.error || "Failed to update");
            msg.style.color = "var(--accent-red)";
        }
    } catch (e) {
        msg.textContent = "Backend offline. Values saved locally.";
        msg.style.color = "var(--accent-yellow)";
    }
}

// -------------------------------------------------------------
// 6. NAVIGATION & TAB SWITCHING
// -------------------------------------------------------------
function showPage(pageId) {
    document.querySelectorAll(".page").forEach(page => {
        page.classList.toggle("active", page.id === pageId);
    });

    document.querySelectorAll(".nav-btn").forEach(button => {
        button.classList.toggle("active", button.dataset.page === pageId);
    });

    if (pageId === "requests") renderQueueTable();
}

document.querySelectorAll(".nav-btn").forEach(button => {
    button.addEventListener("click", () => {
        showPage(button.dataset.page);
    });
});

// -------------------------------------------------------------
// 7. EMERGENCY QUEUE TAB LOGIC
// -------------------------------------------------------------
function addEmergencyRequest() {
    const idInput = document.getElementById("ambulanceId");
    const id = idInput.value.trim();
    const direction = document.getElementById("direction").value;
    const priority = Number(document.getElementById("priority").value);

    if (!id) {
        alert("Please enter an ambulance identifier.");
        idInput.focus();
        return;
    }

    localQueue.enqueue({
        ambulanceId: id,
        direction,
        priority
    });

    idInput.value = "";
    renderQueueTable();
}

async function processHighestPriority() {
    const queueRes = await fetch(`${API_URL}/emergency/queue`, { cache: "no-store" });
    if (queueRes.ok) {
        const queueItems = await queueRes.json();
        const queuedOnly = queueItems.filter(i => i.status === "QUEUED");
        if (queuedOnly.length > 0) {
            const next = queuedOnly[0];
            await fetch(`${API_URL}/emergency/trigger?road=${next.road}&vehicleId=${next.vehicleId}&priorityLevel=${next.priorityLevel}&includeCamera=true&includeSiren=true&deltaMs=100`, { method: "POST" });
            fetchPerceptionTelemetry();
            showPage("dashboard");
            return;
        }
    }

    const item = localQueue.dequeue();
    if (!item) {
        alert("No emergency vehicles waiting in queue.");
        return;
    }

    renderQueueTable();
    showPage("dashboard");
    await fetch(`${API_URL}/emergency/trigger?road=${item.direction}&vehicleId=${item.ambulanceId}&priorityLevel=${item.priority}&includeCamera=true&includeSiren=true&deltaMs=100`, { method: "POST" });
    fetchBackendTrafficState();
}

function clearQueue() {
    localQueue.clear();
    renderQueueTable();
}

// -------------------------------------------------------------
// 7. PERCEPTION & DUAL-VERIFICATION TELEMETRY POLLER
// -------------------------------------------------------------
async function fetchPerceptionTelemetry() {
    if (!backendOnline) return;

    try {
        // Fetch Camera Perceptions
        const camRes = await fetch(`${API_URL}/camera/status`, { cache: "no-store" });
        if (camRes.ok) {
            const camData = await camRes.json();
            const camBadge = document.getElementById("camStatusBadge");
            if (camBadge) {
                if (!camData.modelLoaded) {
                    camBadge.textContent = "MODEL UNAVAILABLE";
                    camBadge.className = "sensor-status badge-danger";
                } else {
                    camBadge.textContent = camData.cameraActive ? "Webcam Line Active" : "Camera Idle";
                    camBadge.className = camData.cameraActive ? "sensor-status badge-safe" : "sensor-status badge-muted";
                }
            }
        }

        const camDetRes = await fetch(`${API_URL}/camera/detections`, { cache: "no-store" });
        if (camDetRes.ok) {
            const detections = await camDetRes.json();
            const bbox = document.getElementById("visionBoundingBox");

            if (detections.length > 0) {
                const det = detections[0];
                if (det.isAmbulance && det.confidence >= 0.70) {
                    perceptionTelemetry.camera.detected = true;
                    perceptionTelemetry.camera.confidence = det.confidence;
                    perceptionTelemetry.camera.road = det.road || getActiveRoad();
                    perceptionTelemetry.camera.timestampMs = Date.now();
                    if (bbox) bbox.style.display = "block";
                }
            } else if (!currentTrafficState.emergencyActive && (Date.now() - perceptionTelemetry.camera.timestampMs) > 6000) {
                perceptionTelemetry.camera.detected = false;
                if (bbox) bbox.style.display = "none";
            }
        }

        // Fetch Audio Perceptions
        const audioRes = await fetch(`${API_URL}/audio/reading`, { cache: "no-store" });
        if (audioRes.ok) {
            const audioData = await audioRes.json();
            const waveBars = document.getElementById("waveBars");

            if (audioData.isSirenConfirmed && audioData.confidence >= 0.70) {
                perceptionTelemetry.audio.detected = true;
                perceptionTelemetry.audio.confidence = audioData.confidence;
                perceptionTelemetry.audio.road = audioData.road || getActiveRoad();
                perceptionTelemetry.audio.timestampMs = Date.now();
                if (waveBars) waveBars.classList.add("active", "siren");
            } else if (!currentTrafficState.emergencyActive && (Date.now() - perceptionTelemetry.audio.timestampMs) > 6000) {
                perceptionTelemetry.audio.detected = false;
                if (waveBars) waveBars.classList.remove("siren");
            }
        }

        // Fetch Dual Verifications
        const verifRes = await fetch(`${API_URL}/emergency/verifications`, { cache: "no-store" });
        if (verifRes.ok) {
            const verifs = await verifRes.json();
            const fusionRes = document.getElementById("fusionResult");
            const emergStat = document.getElementById("emergencyStatus");
            const fusionSub = document.getElementById("fusionSub");

            if (verifs && verifs.length > 0) {
                const latest = verifs[0];
                if (fusionRes) {
                    if (latest.verified) {
                        fusionRes.innerHTML = `<span style="color: var(--accent-green);">&#10004; DUAL CONFIRMED on ${latest.road}! &Delta;t: ${latest.timeDeltaMs}ms &bull; Vision & Audio Match.</span>`;
                    } else {
                        fusionRes.innerHTML = `<span style="color: var(--accent-red);">&#9888; REJECTED on ${latest.road}: ${latest.rejectionReason}</span>`;
                    }
                }

                if (emergStat) {
                    emergStat.textContent = currentTrafficState.emergencyActive ? "PREEMPTION ACTIVE" : (latest.verified ? "Verified" : "Idle");
                    emergStat.style.color = currentTrafficState.emergencyActive ? "var(--accent-red)" : (latest.verified ? "var(--accent-green)" : "var(--accent-yellow)");
                }

                if (fusionSub) {
                    fusionSub.textContent = latest.verified ? `Active Road: ${latest.road}` : `Rule: Both Sensors Same Road`;
                }
            }
        }

        // Fetch Emergency Priority Queue
        const queueRes = await fetch(`${API_URL}/emergency/queue`, { cache: "no-store" });
        if (queueRes.ok) {
            const queueItems = await queueRes.json();
            renderQueueFromBackend(queueItems);
        }

        // Fetch Telemetry Signal Logs
        const telemRes = await fetch(`${API_URL}/telemetry/signals?limit=10`, { cache: "no-store" });
        if (telemRes.ok) {
            const signals = await telemRes.json();
            renderHistoryFromBackend(signals);
        }

    } catch (e) {
        console.debug("Perception poller notice:", e.message);
    }
}

function renderQueueFromBackend(items) {
    const table = document.getElementById("queueTable");
    const pendingCount = document.getElementById("pendingCount");

    if (pendingCount) pendingCount.textContent = items.length;
    if (!table) return;

    if (!items || items.length === 0) {
        table.innerHTML = `<tr><td colspan="5" class="empty">Emergency queue is empty.</td></tr>`;
        return;
    }

    table.innerHTML = items.map(item => `
        <tr>
            <td><strong>${item.vehicleId}</strong></td>
            <td><span class="badge">${item.road}</span></td>
            <td>${item.priorityLevel === 1 ? '<span class="badge badge-danger">1 - Critical</span>' :
            item.priorityLevel === 2 ? '<span class="badge badge-warning">2 - High</span>' : '<span class="badge">3 - Normal</span>'}</td>
            <td>#${item.arrivalOrder}</td>
            <td><span class="badge ${item.status === 'DISPATCHED' ? 'badge-danger' : 'badge-safe'}">${item.status}</span></td>
        </tr>
    `).join("");
}

function renderHistoryFromBackend(signals) {
    const table = document.getElementById("recentHistory");
    const allTable = document.getElementById("allHistory");

    if (table && signals && signals.length > 0) {
        table.innerHTML = signals.slice(0, 8).map(s => {
            const timeStr = (s.recordedAt || s.timestamp);
            const isEmerg = (s.isEmergencyPreemption ?? s.emergencyPreemptionActive) || false;
            return `
            <tr>
                <td>#${s.id}</td>
                <td>${timeStr ? (timeStr.includes('T') ? timeStr.substring(11, 19) : timeStr.substring(0, 8)) : '--'}</td>
                <td><span class="badge ${isEmerg ? 'badge-danger' : 'badge-safe'}">${isEmerg ? 'EMERGENCY' : 'CYCLE'}</span></td>
                <td><strong>${s.road}</strong></td>
                <td><span class="badge" style="color: ${s.signalColor === 'GREEN' ? '#2ecc71' : s.signalColor === 'YELLOW' ? '#f1c40f' : '#e74c3c'}">${s.signalColor}</span></td>
                <td>${isEmerg ? '<span class="badge badge-danger">YES</span>' : '<span class="badge badge-muted">NO</span>'}</td>
            </tr>
            `;
        }).join("");
    }

    if (allTable && signals && signals.length > 0) {
        allTable.innerHTML = signals.map(s => {
            const timeStr = (s.recordedAt || s.timestamp);
            const isEmerg = (s.isEmergencyPreemption ?? s.emergencyPreemptionActive) || false;
            return `
            <tr>
                <td>${s.id}</td>
                <td>${timeStr ? timeStr.replace('T', ' ').substring(0, 19) : '--'}</td>
                <td>${s.phaseType || (isEmerg ? 'EMERGENCY_GREEN' : 'Normal Cycle')}</td>
                <td><span class="badge">${s.road}</span></td>
                <td><span class="badge">${isEmerg ? 'CONFIRMED' : 'NONE'}</span></td>
                <td><span class="badge ${s.signalColor === 'GREEN' ? 'badge-safe' : 'badge-muted'}">${s.signalColor} Phase</span></td>
            </tr>
            `;
        }).join("");
    }
}

// -------------------------------------------------------------
// 8. SCENARIO TEST EXECUTION SUITE
// -------------------------------------------------------------
async function executeScenario(scenarioId) {
    showPage("monitoring");
    const fusionRes = document.getElementById("fusionResult");

    switch (scenarioId) {
        case "SAME_ROAD":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 1: Same-Road Dual Confirmation (North)...</span>`;
            announceVoice("EMERGENCY_CONFIRMED", "Emergency confirmed. Ambulance approaching from NORTH.");
            await fetch(`${API_URL}/emergency/trigger?road=NORTH&vehicleId=AMB-NORTH-101&priorityLevel=1&includeCamera=true&includeSiren=true&deltaMs=200`, { method: "POST" });
            break;

        case "ROAD_MISMATCH":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 2: Road Mismatch (Camera North + Siren East)...</span>`;
            announceVoice("ROAD_MISMATCH", "Emergency verification rejected. Road mismatch.");
            await fetch(`${API_URL}/emergency/trigger?road=NORTH&sirenRoad=EAST&vehicleId=AMB-MISMATCH-1&includeCamera=true&includeSiren=true&deltaMs=150`, { method: "POST" });
            break;

        case "CAM_ONLY":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 3: Camera Only (Silent Approach)...</span>`;
            announceVoice("CAMERA_AMBULANCE", "Ambulance detected.");
            await fetch(`${API_URL}/emergency/trigger?road=NORTH&vehicleId=AMB-SILENT-1&includeCamera=true&includeSiren=false`, { method: "POST" });
            break;

        case "AUDIO_ONLY":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 4: Siren Only (Ghost Sound)...</span>`;
            announceVoice("MIC_SIREN", "Ambulance siren detected.");
            await fetch(`${API_URL}/emergency/trigger?road=SOUTH&vehicleId=AMB-GHOST-1&includeCamera=false&includeSiren=true`, { method: "POST" });
            break;

        case "TIME_EXCEED":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 5: Time Window Exceeded (&Delta;t = 4.5s &gt; 3.0s)...</span>`;
            await fetch(`${API_URL}/emergency/trigger?road=NORTH&vehicleId=AMB-DELAYED-1&includeCamera=true&includeSiren=true&deltaMs=4200`, { method: "POST" });
            break;

        case "LOW_CONF":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 6: Low Sensor Confidence (0.52 &lt; 0.70)...</span>`;
            await fetch(`${API_URL}/emergency/trigger?road=NORTH&vehicleId=AMB-LOW-1&includeCamera=true&includeSiren=true&cameraConfidence=0.52&audioConfidence=0.55&deltaMs=100`, { method: "POST" });
            break;

        case "MULTI_QUEUE":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 7: Multi-Ambulance Priority Queue (Critical vs High)...</span>`;
            announceVoice("EMERGENCY_CONFIRMED", "Emergency confirmed. Ambulance approaching from NORTH.");
            // Trigger Critical Priority 1 on North
            await fetch(`${API_URL}/emergency/trigger?road=NORTH&vehicleId=AMB-CRITICAL-1&priorityLevel=1&includeCamera=true&includeSiren=true&deltaMs=100`, { method: "POST" });
            setTimeout(async () => {
                // Enqueue High Priority 2 on East
                await fetch(`${API_URL}/emergency/trigger?road=EAST&vehicleId=AMB-HIGH-2&priorityLevel=2&includeCamera=true&includeSiren=true&deltaMs=100`, { method: "POST" });
            }, 800);
            break;

        case "CLEAR_EXIT":
            if (fusionRes) fusionRes.innerHTML = `<span style="color: var(--accent-blue);">Executing Scenario 8: Ambulance Cleared Intersection Exit...</span>`;
            announceVoice("EMERGENCY_RESET", "Emergency ended. All signals are red.");
            await fetch(`${API_URL}/emergency/pass?vehicleId=AMB-NORTH-101`, { method: "POST" });
            break;
    }

    setTimeout(() => {
        fetchBackendTrafficState();
        fetchPerceptionTelemetry();
    }, 600);
}

// =============================================================
// 8B. BROWSER WEB SPEECH API & LIVE PERCEPTION STATE ENGINE
// =============================================================
let lastVoiceEvent = null;
let lastVoiceTime = 0;
let voiceClearTimer = null;

function announceVoice(eventType, message) {
    if (!('speechSynthesis' in window)) {
        console.warn("Web Speech API not supported in this browser.");
        return;
    }

    const now = Date.now();
    // Deduplication: Avoid repeating the exact same announcement continuously
    if (lastVoiceEvent === eventType && (now - lastVoiceTime) < 5000) {
        return;
    }

    lastVoiceEvent = eventType;
    lastVoiceTime = now;

    try {
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(message);
        utterance.rate = 1.0;
        utterance.pitch = 1.0;
        utterance.volume = 1.0;
        window.speechSynthesis.speak(utterance);
        console.log(`[Voice Announcement] ${eventType}: "${message}"`);

        const voiceText = document.getElementById("voiceStatusText");
        if (voiceText) {
            voiceText.textContent = `Announced: "${message}"`;
            setTimeout(() => {
                if (voiceText) voiceText.textContent = "Active";
            }, 4000);
        }
    } catch (err) {
        console.error("SpeechSynthesis error:", err);
    }
}

// Perception & Verification In-Memory State Tracker
const perceptionTelemetry = {
    camera: {
        detected: false,
        confidence: 0.0,
        predictedClass: "none",
        road: "NORTH",
        timestamp: null,
        timestampMs: 0
    },
    audio: {
        detected: false,
        confidence: 0.0,
        soundClass: "none",
        road: "NORTH",
        timestamp: null,
        timestampMs: 0
    },
    verificationStatus: "ALL_RED_IDLE",
    lastConfirmedRoad: null
};

function getActiveRoad() {
    return document.getElementById("approachRoadSelect")?.value ||
        document.getElementById("emergencyRoadSelect")?.value ||
        perceptionTelemetry.camera.road ||
        "NORTH";
}

function updateDualVerificationMonitor() {
    const banner = document.getElementById("mainStatusBanner");
    const camStatusBox = document.getElementById("cameraStatusDisplay");
    const sirenStatusBox = document.getElementById("sirenStatusDisplay");

    const cam = perceptionTelemetry.camera;
    const aud = perceptionTelemetry.audio;
    const now = Date.now();

    // Fresh validity window: 6 seconds
    const camFresh = cam.detected && (now - cam.timestampMs) < 6000;
    const audFresh = aud.detected && (now - aud.timestampMs) < 6000;

    // 1. Panel A: Camera Detection Status
    if (camStatusBox) {
        if (camFresh) {
            camStatusBox.textContent = "AMBULANCE DETECTED";
            camStatusBox.className = "sensor-status-box status-detected";
        } else {
            camStatusBox.textContent = "NO AMBULANCE DETECTED";
            camStatusBox.className = "sensor-status-box status-idle";
        }
    }

    // 2. Panel B: Siren Detection Status
    if (sirenStatusBox) {
        if (audFresh) {
            sirenStatusBox.textContent = "SIREN DETECTED";
            sirenStatusBox.className = "sensor-status-box status-siren";
        } else {
            sirenStatusBox.textContent = "NO SIREN DETECTED";
            sirenStatusBox.className = "sensor-status-box status-idle";
        }
    }

    // 3. System Status Banner & Strict Dual Verification Signal Control
    let statusText = "ALL_RED_IDLE";
    let bannerClass = "banner-idle";

    if (currentTrafficState.emergencyActive) {
        statusText = "EMERGENCY CONFIRMED!";
        bannerClass = "banner-confirmed";
        const target = currentTrafficState.activeRoad || "NORTH";
        currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
        currentTrafficState.signals[target] = "GREEN";
        renderTrafficSignals(currentTrafficState);
    } else if (camFresh && audFresh) {
        const deltaMs = Math.abs(cam.timestampMs - aud.timestampMs);

        if (cam.road !== aud.road) {
            // Road mismatch rejection
            statusText = "VERIFICATION REJECTED – ROAD MISMATCH";
            bannerClass = "banner-rejected";
            currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
            currentTrafficState.phaseType = "ALL_RED_IDLE";
            currentTrafficState.emergencyActive = false;
            renderTrafficSignals(currentTrafficState);
            announceVoice("ROAD_MISMATCH", "Emergency verification rejected. Road mismatch.");
        } else if (deltaMs > 3000) {
            // Time window exceeded rejection
            statusText = "VERIFICATION REJECTED";
            bannerClass = "banner-rejected";
            currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
            currentTrafficState.phaseType = "ALL_RED_IDLE";
            currentTrafficState.emergencyActive = false;
            renderTrafficSignals(currentTrafficState);
        } else if (cam.confidence < 0.70 || aud.confidence < 0.70) {
            // Low confidence rejection
            statusText = "VERIFICATION REJECTED";
            bannerClass = "banner-rejected";
            currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
            currentTrafficState.phaseType = "ALL_RED_IDLE";
            currentTrafficState.emergencyActive = false;
            renderTrafficSignals(currentTrafficState);
        } else {
            // ALL 4 CONDITIONS SATISFIED:
            // 1. Camera confirms ambulance (>= 0.70)
            // 2. Siren confirms siren (>= 0.70)
            // 3. Both on same road
            // 4. Both within 3000ms
            statusText = "EMERGENCY CONFIRMED!";
            bannerClass = "banner-confirmed";

            const targetRoad = cam.road;
            currentTrafficState.activeRoad = targetRoad;
            currentTrafficState.phaseType = "EMERGENCY_GREEN";
            currentTrafficState.emergencyActive = true;
            currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
            currentTrafficState.signals[targetRoad] = "GREEN";
            renderTrafficSignals(currentTrafficState);

            // Voice announcement strictly on emergency confirmation
            announceVoice("EMERGENCY_CONFIRMED", `Emergency confirmed. Ambulance approaching from ${targetRoad}.`);

            if (backendOnline && !currentTrafficState.dispatched) {
                currentTrafficState.dispatched = true;
                fetch(`${API_URL}/emergency/trigger?road=${targetRoad}&vehicleId=AMB-${targetRoad}-01&priorityLevel=1&includeCamera=true&includeSiren=true&deltaMs=${deltaMs}`, { method: "POST" })
                    .catch(e => console.debug("Trigger post notice:", e.message));
            }
        }
    } else if (camFresh && !audFresh) {
        // Camera only -> Hold signals RED, No voice
        statusText = "AMBULANCE DETECTED – WAITING FOR SIREN";
        bannerClass = "banner-detected";
        currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
        currentTrafficState.phaseType = "ALL_RED_IDLE";
        currentTrafficState.emergencyActive = false;
        renderTrafficSignals(currentTrafficState);
    } else if (audFresh && !camFresh) {
        // Siren only -> Hold signals RED, No voice
        statusText = "SIREN DETECTED – WAITING FOR AMBULANCE";
        bannerClass = "banner-waiting";
        currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
        currentTrafficState.phaseType = "ALL_RED_IDLE";
        currentTrafficState.emergencyActive = false;
        renderTrafficSignals(currentTrafficState);
    } else {
        // Idle state -> ALL_RED_IDLE
        statusText = "ALL_RED_IDLE";
        bannerClass = "banner-idle";
        currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };
        currentTrafficState.phaseType = "ALL_RED_IDLE";
        renderTrafficSignals(currentTrafficState);
    }

    if (banner) {
        banner.textContent = statusText;
        banner.className = `system-status-banner ${bannerClass}`;
    }
}

// -------------------------------------------------------------
// 8C. LIVE BROWSER WEBCAM & MICROPHONE INTEGRATION
// -------------------------------------------------------------
let webcamStream = null;
let webcamTimer = null;
let audioContext = null;
let micStream = null;
let scriptProcessor = null;

async function startCamera() {
    const video = document.getElementById("webcamVideo");
    const canvas = document.getElementById("webcamCanvas");
    const fallback = document.getElementById("cameraFallback");
    const statusBadge = document.getElementById("camStatusBadge");
    const uploadStatus = document.getElementById("imageUploadStatus");
    const startCamBtn = document.getElementById("startCameraBtn") || document.getElementById("dashStartCamBtn");
    const stopCamBtn = document.getElementById("stopCameraBtn") || document.getElementById("dashStopCamBtn");

    try {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            alert("Webcam access not supported on this browser.");
            return;
        }

        webcamStream = await navigator.mediaDevices.getUserMedia({
            video: { width: { ideal: 640 }, height: { ideal: 480 } }
        });

        if (video) {
            video.srcObject = webcamStream;
            video.style.display = "block";
            await video.play();
        }

        if (fallback) fallback.style.display = "none";
        if (canvas) canvas.style.display = "none";

        if (statusBadge) {
            statusBadge.textContent = "Live Webcam Active";
            statusBadge.className = "badge badge-safe";
        }
        if (uploadStatus) uploadStatus.textContent = "Webcam active. Sampling frames for trained YOLOv8 ONNX inference...";
        if (startCamBtn) startCamBtn.style.display = "none";
        if (stopCamBtn) stopCamBtn.style.display = "inline-flex";

        if (webcamTimer) clearInterval(webcamTimer);
        webcamTimer = setInterval(captureAndSendWebcamFrame, 1200);
    } catch (err) {
        console.error("Camera permission error:", err);
        if (uploadStatus) uploadStatus.textContent = `Camera error: ${err.message}. Ensure camera permissions are granted.`;
    }
}

function stopCamera() {
    if (webcamTimer) {
        clearInterval(webcamTimer);
        webcamTimer = null;
    }
    if (webcamStream) {
        webcamStream.getTracks().forEach(track => track.stop());
        webcamStream = null;
    }
    const video = document.getElementById("webcamVideo");
    const canvas = document.getElementById("webcamCanvas");
    const fallback = document.getElementById("cameraFallback");
    const bbox = document.getElementById("visionBoundingBox");
    const statusBadge = document.getElementById("camStatusBadge");
    const uploadStatus = document.getElementById("imageUploadStatus");
    const startCamBtn = document.getElementById("startCameraBtn") || document.getElementById("dashStartCamBtn");
    const stopCamBtn = document.getElementById("stopCameraBtn") || document.getElementById("dashStopCamBtn");

    if (video) {
        video.pause();
        video.srcObject = null;
        video.style.display = "none";
    }
    if (canvas) {
        const ctx = canvas.getContext("2d");
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        canvas.style.display = "none";
    }
    if (bbox) bbox.style.display = "none";
    if (fallback) fallback.style.display = "flex";

    if (statusBadge) {
        statusBadge.textContent = "Camera Idle";
        statusBadge.className = "badge";
    }
    if (uploadStatus) uploadStatus.textContent = "Webcam stopped.";
    if (startCamBtn) startCamBtn.style.display = "inline-flex";
    if (stopCamBtn) stopCamBtn.style.display = "none";

    perceptionTelemetry.camera.detected = false;
    updateDualVerificationMonitor();
}

async function captureAndSendWebcamFrame() {
    const video = document.getElementById("webcamVideo");
    const canvas = document.getElementById("webcamCanvas");
    const fallback = document.getElementById("cameraFallback");
    if (!video || !canvas || video.readyState !== 4) return;

    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext("2d");
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    const base64Jpeg = canvas.toDataURL("image/jpeg", 0.8);
    const selectedRoad = getActiveRoad();

    try {
        const response = await fetch(`${API_URL}/camera/detect-frame?road=${selectedRoad}`, {
            method: "POST",
            headers: { "Content-Type": "text/plain" },
            body: base64Jpeg
        });
        if (response.ok) {
            const data = await response.json();
            const uploadStatus = document.getElementById("imageUploadStatus");
            const bbox = document.getElementById("visionBoundingBox");

            if (data.isAmbulanceDetected && data.maxConfidence >= 0.70) {
                perceptionTelemetry.camera.detected = true;
                perceptionTelemetry.camera.confidence = data.maxConfidence;
                perceptionTelemetry.camera.predictedClass = "ambulance";
                perceptionTelemetry.camera.road = selectedRoad;
                perceptionTelemetry.camera.timestamp = new Date().toLocaleTimeString();
                perceptionTelemetry.camera.timestampMs = Date.now();

                // Draw bounding box on canvas
                canvas.style.display = "block";
                video.style.display = "none";
                if (fallback) fallback.style.display = "none";

                if (data.detections && data.detections.length > 0) {
                    data.detections.forEach(det => {
                        if (det.isAmbulance || det.label === "ambulance") {
                            ctx.strokeStyle = "#2ecc71";
                            ctx.lineWidth = 4;
                            ctx.strokeRect(det.x, det.y, det.width, det.height);
                            ctx.fillStyle = "rgba(46, 204, 113, 0.9)";
                            ctx.fillRect(det.x, Math.max(0, det.y - 24), 220, 24);
                            ctx.fillStyle = "#0d1117";
                            ctx.font = "bold 13px 'JetBrains Mono', monospace";
                            ctx.fillText(`AMBULANCE ${(det.confidence * 100).toFixed(0)}% [${selectedRoad}]`, det.x + 4, Math.max(16, det.y - 6));
                        }
                    });
                }

                if (uploadStatus) uploadStatus.innerHTML = `<span style="color:var(--accent-green);">&#10004; Trained Model: AMBULANCE DETECTED (${(data.maxConfidence * 100).toFixed(1)}%) on ${selectedRoad}</span>`;
                if (bbox) {
                    bbox.style.display = "block";
                    bbox.querySelector(".overlay-label").textContent = `AMBULANCE (${(data.maxConfidence * 100).toFixed(0)}%) [${selectedRoad}]`;
                }
            } else {
                perceptionTelemetry.camera.detected = false;
                perceptionTelemetry.camera.confidence = data.maxConfidence || 0.0;
                perceptionTelemetry.camera.predictedClass = "none";
                perceptionTelemetry.camera.road = selectedRoad;
                perceptionTelemetry.camera.timestamp = new Date().toLocaleTimeString();
                perceptionTelemetry.camera.timestampMs = Date.now();

                video.style.display = "block";
                canvas.style.display = "none";
                if (uploadStatus) uploadStatus.textContent = `Live Model: No ambulance detected on approach ${selectedRoad}.`;
                if (bbox) bbox.style.display = "none";
            }

            updateDualVerificationMonitor();
        }
    } catch (e) {
        console.debug("Webcam frame post notice:", e.message);
    }
}

function resampleFloat32(input, fromRate, toRate) {
    if (fromRate === toRate) return input;
    const ratio = fromRate / toRate;
    const outLength = Math.round(input.length / ratio);
    const output = new Float32Array(outLength);
    for (let i = 0; i < outLength; i++) {
        const srcIdx = i * ratio;
        const idx0 = Math.floor(srcIdx);
        const idx1 = Math.min(idx0 + 1, input.length - 1);
        const frac = srcIdx - idx0;
        output[i] = input[idx0] * (1 - frac) + input[idx1] * frac;
    }
    return output;
}

async function startMicrophone() {
    const statusBadge = document.getElementById("audioStatusBadge");
    const waveBars = document.getElementById("waveBars");
    const audioText = document.getElementById("audioPreviewText");
    const uploadStatus = document.getElementById("audioUploadStatus");
    const fallback = document.getElementById("sirenFallback");
    const startMicBtn = document.getElementById("startMicBtn") || document.getElementById("dashStartMicBtn");
    const stopMicBtn = document.getElementById("stopMicBtn") || document.getElementById("dashStopMicBtn");

    try {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            if (statusBadge) {
                statusBadge.textContent = "MICROPHONE ERROR";
                statusBadge.className = "badge badge-danger";
            }
            alert("Microphone access not supported on this browser.");
            return;
        }

        micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        if (statusBadge) {
            statusBadge.textContent = "MICROPHONE CONNECTED";
            statusBadge.className = "badge badge-safe";
        }

        audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const source = audioContext.createMediaStreamSource(micStream);
        const nativeSampleRate = audioContext.sampleRate;

        scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1);
        let accumulatedRawSamples = [];
        const requiredRawSamples = Math.round(nativeSampleRate * 1.0); // 1 full second

        if (statusBadge) {
            statusBadge.textContent = "LISTENING";
            statusBadge.className = "badge badge-safe";
        }
        if (audioText) audioText.textContent = `Microphone active (${nativeSampleRate} Hz -> 16000 Hz). Continuous inference listening...`;
        if (uploadStatus) uploadStatus.textContent = "Microphone listening for emergency siren audio...";

        scriptProcessor.onaudioprocess = async (e) => {
            const inputData = e.inputBuffer.getChannelData(0);
            for (let i = 0; i < inputData.length; i++) {
                accumulatedRawSamples.push(inputData[i]);
            }

            if (accumulatedRawSamples.length >= requiredRawSamples) {
                const rawBuffer = new Float32Array(accumulatedRawSamples.slice(0, requiredRawSamples));
                const step = Math.round(requiredRawSamples * 0.5);
                accumulatedRawSamples = accumulatedRawSamples.slice(step);

                // Resample from native rate down to exactly 16000 Hz mono
                const resampled = resampleFloat32(rawBuffer, nativeSampleRate, 16000);
                const chunkLength = Math.min(16000, resampled.length);

                // RMS calculation
                let sumSq = 0;
                for (let i = 0; i < chunkLength; i++) sumSq += resampled[i] * resampled[i];
                const rms = Math.sqrt(sumSq / chunkLength);

                const pcmBytes = new Int16Array(16000);
                for (let i = 0; i < 16000; i++) {
                    const sample = i < chunkLength ? resampled[i] : 0.0;
                    const clamped = Math.max(-1.0, Math.min(1.0, sample));
                    pcmBytes[i] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7FFF;
                }

                let binaryStr = "";
                const uint8 = new Uint8Array(pcmBytes.buffer);
                const len = uint8.length;
                for (let i = 0; i < len; i += 1024) {
                    binaryStr += String.fromCharCode.apply(null, uint8.subarray(i, Math.min(i + 1024, len)));
                }
                const base64Pcm = btoa(binaryStr);

                const selectedRoad = getActiveRoad();
                try {
                    const res = await fetch(`${API_URL}/audio/detect-chunk?road=${selectedRoad}`, {
                        method: "POST",
                        headers: { "Content-Type": "text/plain" },
                        body: base64Pcm
                    });
                    if (res.ok) {
                        const data = await res.json();
                        if (data.isSirenConfirmed && data.confidence >= 0.70) {
                            perceptionTelemetry.audio.detected = true;
                            perceptionTelemetry.audio.confidence = data.confidence;
                            perceptionTelemetry.audio.soundClass = "EMERGENCY_SIREN";
                            perceptionTelemetry.audio.road = selectedRoad;
                            perceptionTelemetry.audio.timestamp = new Date().toLocaleTimeString();
                            perceptionTelemetry.audio.timestampMs = Date.now();

                            if (statusBadge) {
                                statusBadge.textContent = "SIREN DETECTED";
                                statusBadge.className = "badge badge-danger";
                            }
                            if (uploadStatus) {
                                uploadStatus.innerHTML = `<span style="color:var(--accent-red);">&#128680; Live Mic: AMBULANCE SIREN DETECTED (${(data.confidence * 100).toFixed(1)}%) on ${selectedRoad}!</span>`;
                            }
                            if (waveBars) waveBars.classList.add("active", "siren");
                        } else {
                            perceptionTelemetry.audio.detected = false;
                            perceptionTelemetry.audio.confidence = data.confidence || 0.0;
                            perceptionTelemetry.audio.soundClass = data.soundClass || "TRAFFIC_NOISE";
                            perceptionTelemetry.audio.road = selectedRoad;
                            perceptionTelemetry.audio.timestamp = new Date().toLocaleTimeString();
                            perceptionTelemetry.audio.timestampMs = Date.now();

                            if (statusBadge) {
                                statusBadge.textContent = rms < 0.008 ? "LISTENING" : "NO SIREN DETECTED";
                                statusBadge.className = "badge badge-safe";
                            }
                            if (uploadStatus) {
                                uploadStatus.textContent = rms < 0.008 ? "LISTENING (Ambient Sound)" : `Live Mic: NO SIREN DETECTED (${data.soundClass || 'Normal traffic'} - ${(data.confidence * 100).toFixed(1)}%)`;
                            }
                            if (waveBars) waveBars.classList.remove("siren");
                        }

                        updateDualVerificationMonitor();
                    }
                } catch (err) {
                    console.debug("Audio chunk post notice:", err.message);
                }
            }
        };

        source.connect(scriptProcessor);
        scriptProcessor.connect(audioContext.destination);

        if (fallback) fallback.style.display = "none";
        if (waveBars) waveBars.classList.add("active");
        if (startMicBtn) startMicBtn.style.display = "none";
        if (stopMicBtn) stopMicBtn.style.display = "inline-flex";
    } catch (err) {
        console.error("Microphone access error:", err);
        if (statusBadge) {
            statusBadge.textContent = "MICROPHONE ERROR";
            statusBadge.className = "badge badge-danger";
        }
        if (uploadStatus) uploadStatus.textContent = `MICROPHONE ERROR: ${err.message}.`;
    }
}

function stopMicrophone() {
    if (scriptProcessor) {
        scriptProcessor.disconnect();
        scriptProcessor = null;
    }
    if (audioContext) {
        audioContext.close();
        audioContext = null;
    }
    if (micStream) {
        micStream.getTracks().forEach(t => t.stop());
        micStream = null;
    }
    const statusBadge = document.getElementById("audioStatusBadge");
    const waveBars = document.getElementById("waveBars");
    const audioText = document.getElementById("audioPreviewText");
    const uploadStatus = document.getElementById("audioUploadStatus");
    const fallback = document.getElementById("sirenFallback");
    const startMicBtn = document.getElementById("startMicBtn") || document.getElementById("dashStartMicBtn");
    const stopMicBtn = document.getElementById("stopMicBtn") || document.getElementById("dashStopMicBtn");

    if (waveBars) waveBars.classList.remove("active", "siren");
    if (fallback) fallback.style.display = "flex";
    if (statusBadge) {
        statusBadge.textContent = "Microphone Idle";
        statusBadge.className = "badge";
    }
    if (audioText) audioText.textContent = "Microphone stopped. Ready for live capture or file upload.";
    if (uploadStatus) uploadStatus.textContent = "Microphone stopped.";
    if (startMicBtn) startMicBtn.style.display = "inline-flex";
    if (stopMicBtn) stopMicBtn.style.display = "none";

    perceptionTelemetry.audio.detected = false;
    updateDualVerificationMonitor();
}

async function uploadAmbulanceImage(file) {
    if (!file) return;
    const road = getActiveRoad();
    const statusDiv = document.getElementById("imageUploadStatus");
    const canvas = document.getElementById("webcamCanvas");
    const video = document.getElementById("webcamVideo");
    const fallback = document.getElementById("cameraFallback");
    const bbox = document.getElementById("visionBoundingBox");

    if (statusDiv) statusDiv.textContent = `Analyzing ${file.name} with trained YOLOv8 ONNX model...`;

    if (video) video.style.display = "none";

    const img = new Image();
    img.onload = async () => {
        if (canvas) {
            canvas.width = img.width || 640;
            canvas.height = img.height || 480;
            const ctx = canvas.getContext("2d");
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            canvas.style.display = "block";
            if (fallback) fallback.style.display = "none";

            const base64Jpeg = canvas.toDataURL("image/jpeg", 0.85);

            try {
                const response = await fetch(`${API_URL}/camera/detect-frame?road=${road}`, {
                    method: "POST",
                    headers: { "Content-Type": "text/plain" },
                    body: base64Jpeg
                });

                if (response.ok) {
                    const data = await response.json();
                    if (data.isAmbulanceDetected && data.maxConfidence >= 0.70) {
                        perceptionTelemetry.camera.detected = true;
                        perceptionTelemetry.camera.confidence = data.maxConfidence;
                        perceptionTelemetry.camera.predictedClass = "ambulance";
                        perceptionTelemetry.camera.road = road;
                        perceptionTelemetry.camera.timestamp = new Date().toLocaleTimeString();
                        perceptionTelemetry.camera.timestampMs = Date.now();

                        // Draw bounding box on canvas
                        if (data.detections && data.detections.length > 0) {
                            data.detections.forEach(det => {
                                if (det.isAmbulance || det.label === "ambulance") {
                                    ctx.strokeStyle = "#2ecc71";
                                    ctx.lineWidth = 4;
                                    ctx.strokeRect(det.x, det.y, det.width, det.height);
                                    ctx.fillStyle = "rgba(46, 204, 113, 0.9)";
                                    ctx.fillRect(det.x, Math.max(0, det.y - 24), 220, 24);
                                    ctx.fillStyle = "#0d1117";
                                    ctx.font = "bold 13px 'JetBrains Mono', monospace";
                                    ctx.fillText(`AMBULANCE ${(det.confidence * 100).toFixed(0)}% [${road}]`, det.x + 4, Math.max(16, det.y - 6));
                                }
                            });
                        }

                        if (statusDiv) statusDiv.innerHTML = `<span style="color:var(--accent-green);">&#10004; AMBULANCE DETECTED (${(data.maxConfidence * 100).toFixed(1)}%) on approach ${road}!</span>`;
                        if (bbox) {
                            bbox.style.display = "block";
                            bbox.querySelector(".overlay-label").textContent = `AMBULANCE (${(data.maxConfidence * 100).toFixed(0)}%) [${road}]`;
                        }
                    } else {
                        perceptionTelemetry.camera.detected = false;
                        perceptionTelemetry.camera.confidence = data.maxConfidence || 0.0;
                        perceptionTelemetry.camera.predictedClass = "none";
                        perceptionTelemetry.camera.road = road;
                        perceptionTelemetry.camera.timestamp = new Date().toLocaleTimeString();
                        perceptionTelemetry.camera.timestampMs = Date.now();

                        if (statusDiv) statusDiv.innerHTML = `<span style="color:var(--text-secondary);">&#9888; NO AMBULANCE DETECTED in ${file.name} (Civilian car / background).</span>`;
                        if (bbox) bbox.style.display = "none";
                    }

                    updateDualVerificationMonitor();
                } else {
                    // Fallback to multipart
                    const formData = new FormData();
                    formData.append("file", file);
                    formData.append("road", road);
                    const res2 = await fetch(`${API_URL}/camera/upload`, { method: "POST", body: formData });
                    if (res2.ok) {
                        const data = await res2.json();
                        if (data.isAmbulanceDetected && data.maxConfidence >= 0.70) {
                            perceptionTelemetry.camera.detected = true;
                            perceptionTelemetry.camera.confidence = data.maxConfidence;
                            perceptionTelemetry.camera.road = road;
                            perceptionTelemetry.camera.timestampMs = Date.now();
                        } else {
                            perceptionTelemetry.camera.detected = false;
                        }
                        updateDualVerificationMonitor();
                    }
                }
            } catch (err) {
                console.error("Camera detect error:", err);
                if (statusDiv) statusDiv.textContent = `Detection notice: ${err.message}`;
            }
        }
    };
    img.src = URL.createObjectURL(file);
}

async function uploadSirenAudio(file) {
    if (!file) return;
    const road = getActiveRoad();
    const statusDiv = document.getElementById("audioUploadStatus");
    const audioText = document.getElementById("audioPreviewText");
    const waveBars = document.getElementById("waveBars");
    const fallback = document.getElementById("sirenFallback");

    if (statusDiv) statusDiv.textContent = `Decoding and classifying ${file.name} with trained 1D-CNN ONNX model...`;

    try {
        const arrayBuffer = await file.arrayBuffer();

        // Browser Web Audio API natively decodes MP3, WAV, AAC, OGG cleanly
        const offlineAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
        let audioBuffer = null;
        try {
            audioBuffer = await offlineAudioCtx.decodeAudioData(arrayBuffer.slice(0));
        } catch (decErr) {
            console.warn("Client WebAudio decode failed, will try multipart upload:", decErr);
        }

        if (audioBuffer) {
            const numChannels = audioBuffer.numberOfChannels;
            const length = audioBuffer.length;
            const sampleRate = audioBuffer.sampleRate;

            // Downmix to mono
            const monoData = new Float32Array(length);
            for (let ch = 0; ch < numChannels; ch++) {
                const chData = audioBuffer.getChannelData(ch);
                for (let i = 0; i < length; i++) {
                    monoData[i] += chData[i] / numChannels;
                }
            }

            // Resample to 16,000 Hz if needed
            let samples16k;
            const targetRate = 16000;
            if (sampleRate === targetRate) {
                samples16k = monoData;
            } else {
                const ratio = sampleRate / targetRate;
                const targetLength = Math.round(length / ratio);
                samples16k = new Float32Array(targetLength);
                for (let i = 0; i < targetLength; i++) {
                    const srcIdx = i * ratio;
                    const idx0 = Math.floor(srcIdx);
                    const idx1 = Math.min(idx0 + 1, length - 1);
                    const frac = srcIdx - idx0;
                    samples16k[i] = (1.0 - frac) * monoData[idx0] + frac * monoData[idx1];
                }
            }

            // 1 second chunk (16000 samples)
            const chunk = new Float32Array(16000);
            for (let i = 0; i < Math.min(16000, samples16k.length); i++) {
                chunk[i] = samples16k[i];
            }

            // Convert to 16-bit PCM bytes
            const pcmBytes = new Int16Array(16000);
            for (let i = 0; i < 16000; i++) {
                const s = Math.max(-1, Math.min(1, chunk[i]));
                pcmBytes[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
            }

            const binaryStr = String.fromCharCode(...new Uint8Array(pcmBytes.buffer));
            const base64Pcm = btoa(binaryStr);

            const res = await fetch(`${API_URL}/audio/detect-chunk?road=${road}`, {
                method: "POST",
                headers: { "Content-Type": "text/plain" },
                body: base64Pcm
            });

            if (res.ok) {
                const data = await res.json();
                processAudioInferenceResult(data, file.name, road);
                return;
            }
        }

        // Multipart fallback
        const formData = new FormData();
        formData.append("file", file);
        formData.append("road", road);
        const response = await fetch(`${API_URL}/audio/upload`, {
            method: "POST",
            body: formData
        });

        if (response.ok) {
            const data = await response.json();
            processAudioInferenceResult(data, file.name, road);
        } else {
            throw new Error(`Server returned HTTP ${response.status}`);
        }
    } catch (e) {
        if (statusDiv) statusDiv.textContent = `Upload failed: ${e.message}`;
    }
}

function processAudioInferenceResult(data, fileName, road) {
    const statusDiv = document.getElementById("audioUploadStatus");
    const audioText = document.getElementById("audioPreviewText");
    const waveBars = document.getElementById("waveBars");
    const fallback = document.getElementById("sirenFallback");

    if (fallback) fallback.style.display = "none";

    if (data.isSirenConfirmed && data.confidence >= 0.70) {
        perceptionTelemetry.audio.detected = true;
        perceptionTelemetry.audio.confidence = data.confidence;
        perceptionTelemetry.audio.soundClass = "EMERGENCY_SIREN";
        perceptionTelemetry.audio.road = road;
        perceptionTelemetry.audio.timestamp = new Date().toLocaleTimeString();
        perceptionTelemetry.audio.timestampMs = Date.now();

        if (statusDiv) statusDiv.innerHTML = `<span style="color:var(--accent-red);">&#128680; AMBULANCE SIREN DETECTED (${(data.confidence * 100).toFixed(1)}%) on ${road}!</span>`;
        if (audioText) audioText.textContent = `Uploaded ${fileName}: EMERGENCY_SIREN (${(data.confidence * 100).toFixed(0)}%)`;
        if (waveBars) waveBars.classList.add("active", "siren");
    } else {
        perceptionTelemetry.audio.detected = false;
        perceptionTelemetry.audio.confidence = data.confidence || 0.0;
        perceptionTelemetry.audio.soundClass = data.soundClass || "TRAFFIC_NOISE";
        perceptionTelemetry.audio.road = road;
        perceptionTelemetry.audio.timestamp = new Date().toLocaleTimeString();
        perceptionTelemetry.audio.timestampMs = Date.now();

        if (statusDiv) statusDiv.innerHTML = `<span style="color:var(--text-secondary);">&#9888; NO SIREN DETECTED: ${data.soundClass || 'Normal traffic sound'} (${(data.confidence * 100).toFixed(1)}%).</span>`;
        if (audioText) audioText.textContent = `Uploaded ${fileName}: Non-siren sound (${data.soundClass || 'traffic/horn'})`;
        if (waveBars) waveBars.classList.remove("siren");
    }

    updateDualVerificationMonitor();
}

async function resetEmergency() {
    try {
        await fetch(`${API_URL}/emergency/reset`, { method: "POST" });
    } catch (e) {
        console.debug("Backend reset notice:", e.message);
    }

    currentTrafficState.phaseType = "ALL_RED_IDLE";
    currentTrafficState.emergencyActive = false;
    currentTrafficState.dispatched = false;
    currentTrafficState.secondsRemaining = 0;
    currentTrafficState.signals = { NORTH: "RED", SOUTH: "RED", EAST: "RED", WEST: "RED" };

    // Reset telemetry state
    perceptionTelemetry.camera.detected = false;
    perceptionTelemetry.camera.confidence = 0.0;
    perceptionTelemetry.camera.timestamp = null;
    perceptionTelemetry.camera.timestampMs = 0;

    perceptionTelemetry.audio.detected = false;
    perceptionTelemetry.audio.confidence = 0.0;
    perceptionTelemetry.audio.timestamp = null;
    perceptionTelemetry.audio.timestampMs = 0;

    // Reset status displays
    const camStatus = document.getElementById("cameraStatusDisplay");
    if (camStatus) {
        camStatus.textContent = "NO AMBULANCE DETECTED";
        camStatus.className = "sensor-status-box status-idle";
    }
    const sirenStatus = document.getElementById("sirenStatusDisplay");
    if (sirenStatus) {
        sirenStatus.textContent = "NO SIREN DETECTED";
        sirenStatus.className = "sensor-status-box status-idle";
    }
    const banner = document.getElementById("mainStatusBanner");
    if (banner) {
        banner.textContent = "ALL_RED_IDLE";
        banner.className = "system-status-banner banner-idle";
    }

    const bbox = document.getElementById("visionBoundingBox");
    if (bbox) bbox.style.display = "none";
    const waveBars = document.getElementById("waveBars");
    if (waveBars) waveBars.classList.remove("active", "siren");

    renderTrafficSignals(currentTrafficState);

    lastVoiceEvent = null;
    announceVoice("EMERGENCY_RESET", "Emergency ended. All signals are red.");
    fetchBackendTrafficState();
}

// -------------------------------------------------------------
// 9. EVENT LISTENERS & INITIALIZATION
// -------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
    // Road Approach Selector Sync
    document.getElementById("approachRoadSelect")?.addEventListener("change", (e) => {
        const val = e.target.value;
        if (!perceptionTelemetry.camera.detected) {
            perceptionTelemetry.camera.road = val;
        }
        if (!perceptionTelemetry.audio.detected) {
            perceptionTelemetry.audio.road = val;
        }
        updateDualVerificationMonitor();
    });

    // Traffic Controls
    document.getElementById("pauseResumeBtn")?.addEventListener("click", togglePause);
    document.getElementById("resetCycleBtn")?.addEventListener("click", resetTrafficCycle);
    document.getElementById("resetEmergencyBtn")?.addEventListener("click", resetEmergency);
    document.getElementById("dashResetBtn")?.addEventListener("click", resetEmergency);
    document.getElementById("testEmergencyBtn")?.addEventListener("click", testEmergencyDispatch);
    document.getElementById("notifyPassedBtn")?.addEventListener("click", notifyAmbulancePassed);
    document.getElementById("releaseEmergencyBtn")?.addEventListener("click", releaseEmergency);
    document.getElementById("saveTimingsBtn")?.addEventListener("click", updateTimings);

    // Voice Test Button
    document.getElementById("testVoiceBtn")?.addEventListener("click", () => {
        lastVoiceEvent = null;
        announceVoice("VOICE_TEST", "Voice announcements active. System ready for ambulance and siren detection.");
    });

    // Dashboard Quick Camera & Microphone Controls
    document.getElementById("dashStartCamBtn")?.addEventListener("click", startCamera);
    document.getElementById("dashStopCamBtn")?.addEventListener("click", stopCamera);
    document.getElementById("dashStartMicBtn")?.addEventListener("click", startMicrophone);
    document.getElementById("dashStopMicBtn")?.addEventListener("click", stopMicrophone);

    // Live Monitoring Page Controls
    document.getElementById("startCameraBtn")?.addEventListener("click", startCamera);
    document.getElementById("stopCameraBtn")?.addEventListener("click", stopCamera);
    document.getElementById("uploadAmbulanceBtn")?.addEventListener("click", () => {
        document.getElementById("ambulanceFileInput")?.click();
    });
    document.getElementById("ambulanceFileInput")?.addEventListener("change", (e) => {
        if (e.target.files && e.target.files[0]) {
            uploadAmbulanceImage(e.target.files[0]);
        }
    });

    document.getElementById("startMicBtn")?.addEventListener("click", startMicrophone);
    document.getElementById("stopMicBtn")?.addEventListener("click", stopMicrophone);
    document.getElementById("uploadSirenBtn")?.addEventListener("click", () => {
        document.getElementById("sirenFileInput")?.click();
    });
    document.getElementById("sirenFileInput")?.addEventListener("change", (e) => {
        if (e.target.files && e.target.files[0]) {
            uploadSirenAudio(e.target.files[0]);
        }
    });

    // Manual Emergency Form
    document.getElementById("addRequestBtn")?.addEventListener("click", addEmergencyRequest);
    document.getElementById("processQueueBtn")?.addEventListener("click", processHighestPriority);
    document.getElementById("clearQueueBtn")?.addEventListener("click", clearQueue);

    // Scenario Test Buttons
    document.getElementById("scenSameRoadBtn")?.addEventListener("click", () => executeScenario("SAME_ROAD"));
    document.getElementById("scenMismatchBtn")?.addEventListener("click", () => executeScenario("ROAD_MISMATCH"));
    document.getElementById("scenCamOnlyBtn")?.addEventListener("click", () => executeScenario("CAM_ONLY"));
    document.getElementById("scenAudioOnlyBtn")?.addEventListener("click", () => executeScenario("AUDIO_ONLY"));
    document.getElementById("scenTimeExceedBtn")?.addEventListener("click", () => executeScenario("TIME_EXCEED"));
    document.getElementById("scenLowConfBtn")?.addEventListener("click", () => executeScenario("LOW_CONF"));
    document.getElementById("scenMultiQueueBtn")?.addEventListener("click", () => executeScenario("MULTI_QUEUE"));
    document.getElementById("scenClearExitBtn")?.addEventListener("click", () => executeScenario("CLEAR_EXIT"));

    // History Refresh
    document.getElementById("refreshHistoryBtn")?.addEventListener("click", fetchPerceptionTelemetry);
    document.getElementById("refreshDbHistoryBtn")?.addEventListener("click", fetchDatabaseHistory);

    // Initialize vehicle animation on SVG
    new VehicleSimulation("vehiclesContainer");

    // Initialize Telemetry Monitor Display
    updateDualVerificationMonitor();

    // Start background pollers
    fetchBackendTrafficState();
    fetchPerceptionTelemetry();
    fetchDatabaseHistory();

    setInterval(fetchBackendTrafficState, 1000);
    setInterval(fetchPerceptionTelemetry, 1500);
    setInterval(fetchDatabaseHistory, 2500);
    setInterval(updateDualVerificationMonitor, 1000);
});

async function fetchDatabaseHistory() {
    try {
        const res = await fetch("http://localhost:8080/api/emergency/db-history");
        if (!res.ok) return;
        const data = await res.json();

        const ambList = data.ambulanceDetections || [];
        const sirenList = data.sirenDetections || [];
        const emergList = data.emergencyHistory || [];
        const resetList = data.emergencyResetHistory || [];

        // Update counts
        const elAmbCount = document.getElementById("dbCountAmb");
        const elSirenCount = document.getElementById("dbCountSiren");
        const elEmergCount = document.getElementById("dbCountEmergency");
        const elResetCount = document.getElementById("dbCountResets");

        if (elAmbCount) elAmbCount.textContent = ambList.length;
        if (elSirenCount) elSirenCount.textContent = sirenList.length;
        if (elEmergCount) elEmergCount.textContent = emergList.length;
        if (elResetCount) elResetCount.textContent = resetList.length;

        // Render Table 1: Ambulance Detections
        const tbAmb = document.getElementById("dbTableAmbulance");
        if (tbAmb) {
            if (ambList.length === 0) {
                tbAmb.innerHTML = '<tr><td colspan="6" class="empty">No ambulance detections recorded in MySQL.</td></tr>';
            } else {
                tbAmb.innerHTML = ambList.slice(0, 15).map(r => `
                    <tr>
                        <td><strong>#${r.id}</strong></td>
                        <td><code>${r.imageFilename || 'webcam_stream.jpg'}</code></td>
                        <td><span class="badge ${r.ambulanceDetected === 'YES' ? 'badge-success' : 'badge-muted'}">${r.ambulanceDetected || (r.ambulance ? 'YES' : 'NO')}</span></td>
                        <td><strong>${(r.confidence * 100).toFixed(1)}%</strong></td>
                        <td><span class="road-tag">${r.road}</span></td>
                        <td><small>${formatDbTimestamp(r.detectedAt)}</small></td>
                    </tr>
                `).join('');
            }
        }

        // Render Table 2: Siren Detections
        const tbSiren = document.getElementById("dbTableSiren");
        if (tbSiren) {
            if (sirenList.length === 0) {
                tbSiren.innerHTML = '<tr><td colspan="6" class="empty">No siren detections recorded in MySQL.</td></tr>';
            } else {
                tbSiren.innerHTML = sirenList.slice(0, 15).map(r => `
                    <tr>
                        <td><strong>#${r.id}</strong></td>
                        <td><code>${r.audioFilename || 'live_microphone.wav'}</code></td>
                        <td><span class="badge ${r.sirenDetected === 'YES' ? 'badge-success' : 'badge-muted'}">${r.sirenDetected || 'YES'}</span></td>
                        <td><strong>${(r.confidence * 100).toFixed(1)}%</strong></td>
                        <td><span class="road-tag">${r.road}</span></td>
                        <td><small>${formatDbTimestamp(r.detectedAt)}</small></td>
                    </tr>
                `).join('');
            }
        }

        // Render Table 3: Emergency History
        const tbEmerg = document.getElementById("dbTableEmergency");
        if (tbEmerg) {
            if (emergList.length === 0) {
                tbEmerg.innerHTML = '<tr><td colspan="10" class="empty">No emergency history records in MySQL. Upload an ambulance image to begin.</td></tr>';
            } else {
                tbEmerg.innerHTML = emergList.slice(0, 15).map(r => {
                    let badgeClass = 'badge-muted';
                    if (r.emergencyStatus === 'CONFIRMED') badgeClass = 'badge-success';
                    else if (r.emergencyStatus === 'PENDING') badgeClass = 'badge-warning';
                    else if (r.emergencyStatus === 'REJECTED') badgeClass = 'badge-danger';

                    return `
                        <tr>
                            <td><strong>#EM-${r.emergencyId}</strong></td>
                            <td>${r.ambulanceDetectionId ? '#' + r.ambulanceDetectionId : '-'}</td>
                            <td>${r.sirenDetectionId ? '#' + r.sirenDetectionId : '-'}</td>
                            <td><span class="road-tag">${r.selectedRoad}</span></td>
                            <td>${r.ambulanceConfidence != null ? (r.ambulanceConfidence * 100).toFixed(1) + '%' : '-'}</td>
                            <td>${r.sirenConfidence != null ? (r.sirenConfidence * 100).toFixed(1) + '%' : '-'}</td>
                            <td><span class="badge ${badgeClass}">${r.emergencyStatus}</span></td>
                            <td><strong style="color: ${r.trafficSignalStatus && r.trafficSignalStatus.includes('GREEN') ? '#22c55e' : '#ef4444'}">${r.trafficSignalStatus || 'ALL_RED'}</strong></td>
                            <td><small>${formatDbTimestamp(r.emergencyStartTime)}</small></td>
                            <td><small>${r.emergencyConfirmationTime ? formatDbTimestamp(r.emergencyConfirmationTime) : '-'}</small></td>
                        </tr>
                    `;
                }).join('');
            }
        }

        // Render Table 4: Emergency Reset History
        const tbReset = document.getElementById("dbTableReset");
        if (tbReset) {
            if (resetList.length === 0) {
                tbReset.innerHTML = '<tr><td colspan="6" class="empty">No reset history recorded. Click RESET EMERGENCY to test.</td></tr>';
            } else {
                tbReset.innerHTML = resetList.slice(0, 15).map(r => `
                    <tr>
                        <td><strong>#RST-${r.resetId}</strong></td>
                        <td>${r.emergencyId ? '#EM-' + r.emergencyId : '-'}</td>
                        <td><span class="road-tag">${r.selectedRoad || 'NORTH'}</span></td>
                        <td><code>${r.previousSignalStatus || 'ALL_RED'}</code></td>
                        <td><span class="badge badge-success">${r.resetStatus || 'RESET_SUCCESS'}</span></td>
                        <td><small>${formatDbTimestamp(r.resetTimestamp)}</small></td>
                    </tr>
                `).join('');
            }
        }
    } catch (err) {
        console.debug("Error fetching database history:", err);
    }
}

function formatDbTimestamp(tsStr) {
    if (!tsStr) return '-';
    try {
        const d = new Date(tsStr);
        if (isNaN(d.getTime())) return tsStr;
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' ' + d.toLocaleDateString();
    } catch (e) {
        return tsStr;
    }
}


async function fetchDatabaseHistory() {
    try {
        const res = await fetch("http://localhost:8080/api/emergency/db-history");
        if (!res.ok) return;
        const data = await res.json();

        const ambList = data.ambulanceDetections || [];
        const sirenList = data.sirenDetections || [];
        const emergList = data.emergencyHistory || [];
        const resetList = data.emergencyResetHistory || [];

        // Update counts
        const elAmbCount = document.getElementById("dbCountAmb");
        const elSirenCount = document.getElementById("dbCountSiren");
        const elEmergCount = document.getElementById("dbCountEmergency");
        const elResetCount = document.getElementById("dbCountResets");

        if (elAmbCount) elAmbCount.textContent = ambList.length;
        if (elSirenCount) elSirenCount.textContent = sirenList.length;
        if (elEmergCount) elEmergCount.textContent = emergList.length;
        if (elResetCount) elResetCount.textContent = resetList.length;

        // Render Table 1: Ambulance Detections
        const tbAmb = document.getElementById("dbTableAmbulance");
        if (tbAmb) {
            if (ambList.length === 0) {
                tbAmb.innerHTML = '<tr><td colspan="6" class="empty">No ambulance detections recorded in MySQL.</td></tr>';
            } else {
                tbAmb.innerHTML = ambList.slice(0, 15).map(r => `
                    <tr>
                        <td><strong>#${r.id}</strong></td>
                        <td><code>${r.imageFilename || 'webcam_stream.jpg'}</code></td>
                        <td><span class="badge ${r.ambulanceDetected === 'YES' ? 'badge-success' : 'badge-muted'}">${r.ambulanceDetected || (r.ambulance ? 'YES' : 'NO')}</span></td>
                        <td><strong>${(r.confidence * 100).toFixed(1)}%</strong></td>
                        <td><span class="road-tag">${r.road}</span></td>
                        <td><small>${formatDbTimestamp(r.detectedAt)}</small></td>
                    </tr>
                `).join('');
            }
        }

        // Render Table 2: Siren Detections
        const tbSiren = document.getElementById("dbTableSiren");
        if (tbSiren) {
            if (sirenList.length === 0) {
                tbSiren.innerHTML = '<tr><td colspan="6" class="empty">No siren detections recorded in MySQL.</td></tr>';
            } else {
                tbSiren.innerHTML = sirenList.slice(0, 15).map(r => `
                    <tr>
                        <td><strong>#${r.id}</strong></td>
                        <td><code>${r.audioFilename || 'live_microphone.wav'}</code></td>
                        <td><span class="badge ${r.sirenDetected === 'YES' ? 'badge-success' : 'badge-muted'}">${r.sirenDetected || 'YES'}</span></td>
                        <td><strong>${(r.confidence * 100).toFixed(1)}%</strong></td>
                        <td><span class="road-tag">${r.road}</span></td>
                        <td><small>${formatDbTimestamp(r.detectedAt)}</small></td>
                    </tr>
                `).join('');
            }
        }

        // Render Table 3: Emergency History
        const tbEmerg = document.getElementById("dbTableEmergency");
        if (tbEmerg) {
            if (emergList.length === 0) {
                tbEmerg.innerHTML = '<tr><td colspan="10" class="empty">No emergency history records in MySQL. Upload an ambulance image to begin.</td></tr>';
            } else {
                tbEmerg.innerHTML = emergList.slice(0, 15).map(r => {
                    let badgeClass = 'badge-muted';
                    if (r.emergencyStatus === 'CONFIRMED') badgeClass = 'badge-success';
                    else if (r.emergencyStatus === 'PENDING') badgeClass = 'badge-warning';
                    else if (r.emergencyStatus === 'REJECTED') badgeClass = 'badge-danger';

                    return `
                        <tr>
                            <td><strong>#EM-${r.emergencyId}</strong></td>
                            <td>${r.ambulanceDetectionId ? '#' + r.ambulanceDetectionId : '-'}</td>
                            <td>${r.sirenDetectionId ? '#' + r.sirenDetectionId : '-'}</td>
                            <td><span class="road-tag">${r.selectedRoad}</span></td>
                            <td>${r.ambulanceConfidence != null ? (r.ambulanceConfidence * 100).toFixed(1) + '%' : '-'}</td>
                            <td>${r.sirenConfidence != null ? (r.sirenConfidence * 100).toFixed(1) + '%' : '-'}</td>
                            <td><span class="badge ${badgeClass}">${r.emergencyStatus}</span></td>
                            <td><strong style="color: ${r.trafficSignalStatus && r.trafficSignalStatus.includes('GREEN') ? '#22c55e' : '#ef4444'}">${r.trafficSignalStatus || 'ALL_RED'}</strong></td>
                            <td><small>${formatDbTimestamp(r.emergencyStartTime)}</small></td>
                            <td><small>${r.emergencyConfirmationTime ? formatDbTimestamp(r.emergencyConfirmationTime) : '-'}</small></td>
                        </tr>
                    `;
                }).join('');
            }
        }

        // Render Table 4: Emergency Reset History
        const tbReset = document.getElementById("dbTableReset");
        if (tbReset) {
            if (resetList.length === 0) {
                tbReset.innerHTML = '<tr><td colspan="6" class="empty">No reset history recorded. Click RESET EMERGENCY to test.</td></tr>';
            } else {
                tbReset.innerHTML = resetList.slice(0, 15).map(r => `
                    <tr>
                        <td><strong>#RST-${r.resetId}</strong></td>
                        <td>${r.emergencyId ? '#EM-' + r.emergencyId : '-'}</td>
                        <td><span class="road-tag">${r.selectedRoad || 'NORTH'}</span></td>
                        <td><code>${r.previousSignalStatus || 'ALL_RED'}</code></td>
                        <td><span class="badge badge-success">${r.resetStatus || 'RESET_SUCCESS'}</span></td>
                        <td><small>${formatDbTimestamp(r.resetTimestamp)}</small></td>
                    </tr>
                `).join('');
            }
        }
    } catch (err) {
        console.debug("Error fetching database history:", err);
    }
}

function formatDbTimestamp(tsStr) {
    if (!tsStr) return '-';
    try {
        const d = new Date(tsStr);
        if (isNaN(d.getTime())) return tsStr;
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' ' + d.toLocaleDateString();
    } catch (e) {
        return tsStr;
    }
}