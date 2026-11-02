-- ============================================================================
-- AI-POWERED INTELLIGENT TRAFFIC MANAGEMENT AND EMERGENCY ROUTING SYSTEM
-- Database Schema: ambulance_db
-- Target Engine: MySQL 8.0+
-- Author: Traffic Management Engineering Team
-- ============================================================================

CREATE DATABASE IF NOT EXISTS ambulance_db;
USE ambulance_db;

-- ----------------------------------------------------------------------------
-- 1. Table: ambulance_detections
-- Stores vision detection inference telemetry from OpenCV/ONNX camera feeds.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ambulance_detections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id VARCHAR(50) NOT NULL,
    road ENUM('NORTH', 'SOUTH', 'EAST', 'WEST') NOT NULL,
    vehicle_track_id VARCHAR(50) NULL,
    confidence FLOAT NOT NULL,
    bbox_x INT NULL,
    bbox_y INT NULL,
    bbox_width INT NULL,
    bbox_height INT NULL,
    is_simulated BOOLEAN DEFAULT FALSE NOT NULL,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_amb_road_time (road, detected_at),
    INDEX idx_amb_track (vehicle_track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 2. Table: siren_detections
-- Stores acoustic inference telemetry from audio capture & ONNX classification.
-- NOTE: Raw audio is not permanently stored to preserve disk bandwidth.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS siren_detections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mic_id VARCHAR(50) NOT NULL,
    road ENUM('NORTH', 'SOUTH', 'EAST', 'WEST') NOT NULL,
    sound_class VARCHAR(50) DEFAULT 'EMERGENCY_SIREN' NOT NULL,
    confidence FLOAT NOT NULL,
    frequency_hz FLOAT NULL,
    decibel_level FLOAT NULL,
    consecutive_hits INT DEFAULT 1 NOT NULL,
    is_simulated BOOLEAN DEFAULT FALSE NOT NULL,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_siren_road_time (road, detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 3. Table: emergency_verifications
-- Records the decisions of the EmergencyVerificationService.
-- Enforces: Camera + Siren confirmed on SAME road within configurable delta-t.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS emergency_verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    road ENUM('NORTH', 'SOUTH', 'EAST', 'WEST') NOT NULL,
    ambulance_detection_id BIGINT NULL,
    siren_detection_id BIGINT NULL,
    time_delta_ms BIGINT NULL,
    verified BOOLEAN NOT NULL,
    rejection_reason VARCHAR(255) NULL,
    verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_verif_road_time (road, verified_at),
    INDEX idx_verif_result (verified),
    CONSTRAINT fk_verif_ambulance FOREIGN KEY (ambulance_detection_id)
        REFERENCES ambulance_detections(id) ON DELETE SET NULL,
    CONSTRAINT fk_verif_siren FOREIGN KEY (siren_detection_id)
        REFERENCES siren_detections(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 4. Table: emergency_events
-- Records lifecycle of an emergency traffic priority preemption.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS emergency_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    verification_id BIGINT NULL,
    vehicle_id VARCHAR(50) NOT NULL,
    road ENUM('NORTH', 'SOUTH', 'EAST', 'WEST') NOT NULL,
    event_state VARCHAR(50) NOT NULL,
    action_taken VARCHAR(255) NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cleared_at TIMESTAMP NULL,
    INDEX idx_event_road (road),
    INDEX idx_event_vehicle (vehicle_id),
    CONSTRAINT fk_event_verif FOREIGN KEY (verification_id)
        REFERENCES emergency_verifications(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 5. Table: emergency_queue
-- Persistent record of emergency vehicles queued for intersection clearance.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS emergency_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id VARCHAR(50) NOT NULL,
    road ENUM('NORTH', 'SOUTH', 'EAST', 'WEST') NOT NULL,
    priority_level INT NOT NULL, -- 1 = Critical, 2 = High, 3 = Normal
    arrival_order INT NOT NULL,
    status VARCHAR(50) DEFAULT 'QUEUED' NOT NULL, -- QUEUED, DISPATCHED, CLEARED, CANCELLED
    enqueued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dispatched_at TIMESTAMP NULL,
    INDEX idx_queue_prio_order (priority_level, arrival_order),
    INDEX idx_queue_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 6. Table: traffic_signal_history
-- Full audit trail of all signal phase changes across normal and emergency modes.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS traffic_signal_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    road ENUM('NORTH', 'SOUTH', 'EAST', 'WEST') NOT NULL,
    signal_color ENUM('RED', 'YELLOW', 'GREEN') NOT NULL,
    phase_type VARCHAR(50) NOT NULL,
    cycle_number BIGINT NOT NULL,
    duration_seconds INT NOT NULL,
    is_emergency_preemption BOOLEAN DEFAULT FALSE NOT NULL,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_signal_road_time (road, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 7. Table: system_logs
-- Records internal component lifecycle events, safety trips, and health checks.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_level VARCHAR(20) NOT NULL, -- INFO, WARN, ERROR, CRITICAL
    component VARCHAR(50) NOT NULL,  -- TRAFFIC_FSM, CAMERA_VISION, AUDIO_SIREN, DUAL_VERIFIER
    message TEXT NOT NULL,
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_log_level (log_level),
    INDEX idx_log_comp_time (component, logged_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 8. Existing Prototype Tables (Preserved for backward compatibility)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS emergency_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ambulance_id VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    priority INT NOT NULL,
    arrival_order INT NOT NULL,
    status VARCHAR(20) DEFAULT 'Pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS detection_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ambulance_id VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NULL,
    emergency_type VARCHAR(50) NULL,
    siren_detected BOOLEAN DEFAULT FALSE,
    signal_action VARCHAR(50) NULL,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
