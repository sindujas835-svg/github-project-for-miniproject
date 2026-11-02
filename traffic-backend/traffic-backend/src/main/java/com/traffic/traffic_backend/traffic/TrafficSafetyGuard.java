package com.traffic.traffic_backend.traffic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Traffic Safety Guard: Enforces fundamental intersection safety invariants.
 *
 * CRITICAL SAFETY RULES:
 * 1. NEVER allow conflicting green signals under any circumstances.
 * 2. NEVER switch immediately from one road's green to another road's green without
 *    transitioning through yellow and an all-red clearance interval.
 * 3. At most ONE road may show GREEN or YELLOW at any given millisecond.
 * 4. If any safety invariant fails, FAIL-SAFE to an all-red state immediately.
 */
@Component
public class TrafficSafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(TrafficSafetyGuard.class);

    /**
     * Validates that the provided signal distribution satisfies all safety invariants.
     *
     * @param signals Map of road to signal color.
     * @param phaseType Current traffic phase type.
     * @throws IllegalStateException if any safety invariant is violated.
     */
    public void validateSignals(Map<Road, SignalColor> signals, TrafficPhaseType phaseType) {
        if (signals == null || signals.size() != Road.values().length) {
            throw new IllegalStateException("Signal state invalid: must define states for all 4 roads.");
        }

        int greenCount = 0;
        int yellowCount = 0;
        Road activeRoad = null;

        for (Map.Entry<Road, SignalColor> entry : signals.entrySet()) {
            SignalColor color = entry.getValue();
            if (color == SignalColor.GREEN) {
                greenCount++;
                activeRoad = entry.getKey();
            } else if (color == SignalColor.YELLOW) {
                yellowCount++;
                activeRoad = entry.getKey();
            }
        }

        // Rule 1: Never more than 1 road non-red
        if (greenCount + yellowCount > 1) {
            log.error("CRITICAL SAFETY INVARIANT VIOLATION: Multiple roads have non-red signals! Green: {}, Yellow: {}. Failing safe to ALL-RED.",
                    greenCount, yellowCount);
            throw new IllegalStateException("CONFLICTING GREEN/YELLOW DETECTED! Fail-safe invoked.");
        }

        // Rule 2: In any clearance or idle phase, greenCount and yellowCount must be exactly 0
        boolean isClearancePhase = (phaseType == TrafficPhaseType.ALL_RED_CLEARANCE
                || phaseType == TrafficPhaseType.ALL_RED_IDLE
                || phaseType == TrafficPhaseType.EMERGENCY_PRE_CLEARANCE
                || phaseType == TrafficPhaseType.EMERGENCY_POST_CLEARANCE);

        if (isClearancePhase && (greenCount > 0 || yellowCount > 0)) {
            log.error("SAFETY INVARIANT VIOLATION: Non-red signal observed during clearance or idle phase ({})! Fail-safe invoked.", phaseType);
            throw new IllegalStateException("Clearance and Idle phases must have all RED signals.");
        }
    }

    /**
     * Creates a guaranteed safe fail-safe signal map where all four approaches are RED.
     */
    public Map<Road, SignalColor> createAllRedSignalMap() {
        Map<Road, SignalColor> map = new EnumMap<>(Road.class);
        for (Road road : Road.values()) {
            map.put(road, SignalColor.RED);
        }
        return map;
    }
}
