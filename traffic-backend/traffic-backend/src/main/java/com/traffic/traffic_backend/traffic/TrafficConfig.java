package com.traffic.traffic_backend.traffic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Traffic signal cycle timing configuration.
 *
 * NOTE: All timing values defined here are prototype simulation settings
 * designed for educational demonstration and testing, NOT universal real-world
 * traffic engineering standards (which vary based on roadway design speeds,
 * intersection curb-to-curb width, perception-reaction times, and MUTCD guidelines).
 */
@Component
@ConfigurationProperties(prefix = "traffic.timing")
public class TrafficConfig {

    /**
     * Duration of the green light phase in seconds.
     * Prototype simulation default: 8 seconds.
     */
    private int greenIntervalSeconds = 8;

    /**
     * Duration of the yellow light transition phase in seconds.
     * Prototype simulation default: 3 seconds.
     */
    private int yellowIntervalSeconds = 3;

    /**
     * Duration of the all-red clearance interval in seconds where all 4 approaches remain RED.
     * This ensures the intersection is completely vacated before any conflicting movement is allowed.
     * Prototype simulation default: 2 seconds.
     */
    private int allRedClearanceSeconds = 2;

    /**
     * Yellow clearance duration when preempting an active conflicting green signal.
     * Prototype simulation default: 3 seconds.
     */
    private int emergencyYellowSeconds = 3;

    /**
     * All-red clearance buffer prior to activating the emergency vehicle's green light.
     * Prototype simulation default: 2 seconds.
     */
    private int emergencyPreClearanceSeconds = 2;

    /**
     * Maximum safety duration for emergency green in case vehicle tracking is lost.
     * Prevents intersection gridlock if a vehicle turns away or stalls.
     * Prototype simulation default: 20 seconds.
     */
    private int emergencyMaxGreenSeconds = 20;

    /**
     * Grace period in seconds to tolerate temporary camera detection loss
     * before assuming the emergency vehicle has cleared the intersection.
     * Prototype simulation default: 15 seconds.
     */
    private int emergencyGracePeriodSeconds = 15;

    /**
     * All-red clearance buffer after the emergency vehicle passes before resuming normal traffic.
     * Prototype simulation default: 2 seconds.
     */
    private int emergencyPostClearanceSeconds = 2;

    public TrafficConfig() {}

    public TrafficConfig(int greenIntervalSeconds, int yellowIntervalSeconds, int allRedClearanceSeconds) {
        setGreenIntervalSeconds(greenIntervalSeconds);
        setYellowIntervalSeconds(yellowIntervalSeconds);
        setAllRedClearanceSeconds(allRedClearanceSeconds);
    }

    public int getGreenIntervalSeconds() {
        return greenIntervalSeconds;
    }

    public void setGreenIntervalSeconds(int greenIntervalSeconds) {
        if (greenIntervalSeconds < 3) {
            throw new IllegalArgumentException("Green interval must be at least 3 seconds for safe simulation.");
        }
        this.greenIntervalSeconds = greenIntervalSeconds;
    }

    public int getYellowIntervalSeconds() {
        return yellowIntervalSeconds;
    }

    public void setYellowIntervalSeconds(int yellowIntervalSeconds) {
        if (yellowIntervalSeconds < 2) {
            throw new IllegalArgumentException("Yellow interval must be at least 2 seconds for safe vehicle stopping.");
        }
        this.yellowIntervalSeconds = yellowIntervalSeconds;
    }

    public int getAllRedClearanceSeconds() {
        return allRedClearanceSeconds;
    }

    public void setAllRedClearanceSeconds(int allRedClearanceSeconds) {
        if (allRedClearanceSeconds < 1) {
            throw new IllegalArgumentException("All-red clearance interval must be at least 1 second.");
        }
        this.allRedClearanceSeconds = allRedClearanceSeconds;
    }

    public int getEmergencyYellowSeconds() {
        return emergencyYellowSeconds;
    }

    public void setEmergencyYellowSeconds(int emergencyYellowSeconds) {
        if (emergencyYellowSeconds < 2) {
            throw new IllegalArgumentException("Emergency yellow interval must be at least 2 seconds.");
        }
        this.emergencyYellowSeconds = emergencyYellowSeconds;
    }

    public int getEmergencyPreClearanceSeconds() {
        return emergencyPreClearanceSeconds;
    }

    public void setEmergencyPreClearanceSeconds(int emergencyPreClearanceSeconds) {
        if (emergencyPreClearanceSeconds < 1) {
            throw new IllegalArgumentException("Emergency pre-clearance must be at least 1 second.");
        }
        this.emergencyPreClearanceSeconds = emergencyPreClearanceSeconds;
    }

    public int getEmergencyMaxGreenSeconds() {
        return emergencyMaxGreenSeconds;
    }

    public void setEmergencyMaxGreenSeconds(int emergencyMaxGreenSeconds) {
        if (emergencyMaxGreenSeconds < 5) {
            throw new IllegalArgumentException("Emergency max green must be at least 5 seconds.");
        }
        this.emergencyMaxGreenSeconds = emergencyMaxGreenSeconds;
    }

    public int getEmergencyGracePeriodSeconds() {
        return emergencyGracePeriodSeconds;
    }

    public void setEmergencyGracePeriodSeconds(int emergencyGracePeriodSeconds) {
        if (emergencyGracePeriodSeconds < 1) {
            throw new IllegalArgumentException("Grace period must be at least 1 second.");
        }
        this.emergencyGracePeriodSeconds = emergencyGracePeriodSeconds;
    }

    public int getEmergencyPostClearanceSeconds() {
        return emergencyPostClearanceSeconds;
    }

    public void setEmergencyPostClearanceSeconds(int emergencyPostClearanceSeconds) {
        if (emergencyPostClearanceSeconds < 1) {
            throw new IllegalArgumentException("Emergency post-clearance must be at least 1 second.");
        }
        this.emergencyPostClearanceSeconds = emergencyPostClearanceSeconds;
    }

    public int getTotalCycleDuration() {
        // 4 approaches * (green + yellow + allRed)
        return 4 * (greenIntervalSeconds + yellowIntervalSeconds + allRedClearanceSeconds);
    }
}

