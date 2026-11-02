package com.traffic.traffic_backend.traffic;

/**
 * Represents the distinct operational phases of the intersection state machine.
 */
public enum TrafficPhaseType {
    // System idle state: all signals RED, waiting for verified detection
    ALL_RED_IDLE,

    // Normal cycle phases
    GREEN_PHASE,
    YELLOW_PHASE,
    ALL_RED_CLEARANCE,

    // Emergency preemption phases
    EMERGENCY_YELLOW,            // Safely slowing down active conflicting traffic
    EMERGENCY_PRE_CLEARANCE,     // All-Red clearance flushing the intersection before ambulance green
    EMERGENCY_GREEN,             // Green granted exclusively to the emergency vehicle's approach
    EMERGENCY_POST_CLEARANCE     // All-Red clearance after ambulance departs
}
