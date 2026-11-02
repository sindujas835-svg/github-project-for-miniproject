-- ==============================================================================
-- AMBULANCE DETECTION & INTELLIGENT TRAFFIC SIGNAL PREEMPTION SYSTEM
-- MySQL Database: ambulance_db
-- JURY DEMONSTRATION & VERIFICATION SQL SCRIPT
-- ==============================================================================

USE ambulance_db;

-- ------------------------------------------------------------------------------
-- 1. INSPECT ALL TABLES AND SCHEMA IN DATABASE
-- ------------------------------------------------------------------------------
SHOW TABLES;

-- Inspect Table 1: Ambulance Detection
DESCRIBE ambulance_detection;

-- Inspect Table 2: Siren Detection
DESCRIBE siren_detection;

-- Inspect Table 3: Emergency History
DESCRIBE emergency_history;

-- Inspect Table 4: Emergency Reset History
DESCRIBE emergency_reset_history;


-- ------------------------------------------------------------------------------
-- 2. DISPLAY LIVE RECORDS FROM THE FOUR CORE TABLES
-- ------------------------------------------------------------------------------

-- Query 1: Ambulance Detection Table
-- Shows actual image filenames, detection confidence, selected road, and timestamp
SELECT 
    id AS `Detection ID`,
    image_filename AS `Image Filename`,
    ambulance_detected AS `Ambulance Detected`,
    CONCAT(ROUND(confidence * 100, 1), '%') AS `Detection Confidence`,
    road AS `Selected Road`,
    detected_at AS `Detection Timestamp`
FROM ambulance_detection
ORDER BY detected_at DESC
LIMIT 10;

-- Query 2: Siren Detection Table
-- Shows audio filenames, siren confirmed (YES/NO), acoustic confidence, and road
SELECT 
    id AS `Detection ID`,
    audio_filename AS `Audio Filename`,
    siren_detected AS `Siren Detected`,
    CONCAT(ROUND(confidence * 100, 1), '%') AS `Acoustic Confidence`,
    road AS `Selected Road`,
    detected_at AS `Detection Timestamp`
FROM siren_detection
ORDER BY detected_at DESC
LIMIT 10;

-- Query 3: Emergency History Table
-- Shows dual verification lifecycle: PENDING -> CONFIRMED / REJECTED, signal status
SELECT 
    emergency_id AS `Emergency ID`,
    ambulance_detection_id AS `Amb Detection ID`,
    siren_detection_id AS `Siren Detection ID`,
    selected_road AS `Selected Road`,
    CONCAT(ROUND(ambulance_confidence * 100, 1), '%') AS `Vision Conf`,
    IFNULL(CONCAT(ROUND(siren_confidence * 100, 1), '%'), 'PENDING') AS `Acoustic Conf`,
    emergency_status AS `Emergency Status`,
    traffic_signal_status AS `Traffic Signal Status`,
    emergency_start_time AS `Start Time`,
    emergency_confirmation_time AS `Confirmation Time`
FROM emergency_history
ORDER BY emergency_start_time DESC
LIMIT 10;

-- Query 4: Emergency Reset History Table
-- Shows fail-safe resets initiated by operator, road, previous signal state, and status
SELECT 
    reset_id AS `Reset ID`,
    emergency_id AS `Emergency ID`,
    selected_road AS `Selected Road`,
    previous_signal_status AS `Previous Signal Status`,
    reset_status AS `Reset Status`,
    reset_timestamp AS `Reset Timestamp`
FROM emergency_reset_history
ORDER BY reset_timestamp DESC
LIMIT 10;


-- ------------------------------------------------------------------------------
-- 3. JURY DEMONSTRATION JOIN QUERY
-- Correlates vision detection, acoustic detection, emergency confirmation, and reset audit
-- ------------------------------------------------------------------------------
SELECT 
    eh.emergency_id AS `Emergency ID`,
    eh.selected_road AS `Approach Road`,
    eh.emergency_status AS `Status`,
    eh.traffic_signal_status AS `Signal Status`,
    ad.image_filename AS `Vision File`,
    CONCAT(ROUND(eh.ambulance_confidence * 100, 1), '%') AS `Vision Confidence`,
    sd.audio_filename AS `Audio File`,
    IFNULL(CONCAT(ROUND(eh.siren_confidence * 100, 1), '%'), '-') AS `Audio Confidence`,
    eh.emergency_start_time AS `Initiated At`,
    eh.emergency_confirmation_time AS `Confirmed At`,
    erh.reset_id AS `Reset Audit ID`,
    erh.reset_status AS `Reset Outcome`
FROM emergency_history eh
LEFT JOIN ambulance_detections ad ON eh.ambulance_detection_id = ad.id
LEFT JOIN siren_detections sd ON eh.siren_detection_id = sd.id
LEFT JOIN emergency_reset_history erh ON eh.emergency_id = erh.emergency_id
ORDER BY eh.emergency_start_time DESC
LIMIT 10;


-- ------------------------------------------------------------------------------
-- 4. REALISTIC DEMO / BENCHMARK RECORDS (OPTIONAL SEED DATA)
-- Clearly labelled as DEMO records for testing and presentation
-- ------------------------------------------------------------------------------
-- Uncomment the block below if you wish to seed 4 realistic multi-road test scenarios:

/*
INSERT INTO ambulance_detections (camera_id, road, vehicle_track_id, confidence, bbox_x, bbox_y, bbox_width, bbox_height, is_simulated, image_filename, ambulance_detected, detected_at)
VALUES 
('CAM-NORTH-01', 'NORTH', 'TRACK-DEMO-01', 0.985, 248, 380, 175, 147, 0, 'demo_ambulance_north.jpg', 'YES', NOW() - INTERVAL 15 MINUTE),
('CAM-EAST-02',  'EAST',  'TRACK-DEMO-02', 0.942, 310, 290, 160, 130, 0, 'demo_ambulance_east.jpg',  'YES', NOW() - INTERVAL 10 MINUTE),
('CAM-SOUTH-03', 'SOUTH', 'TRACK-DEMO-03', 0.967, 280, 310, 190, 150, 0, 'demo_ambulance_south.jpg', 'YES', NOW() - INTERVAL 5 MINUTE),
('CAM-WEST-04',  'WEST',  'TRACK-DEMO-04', 0.520, 150, 200, 110,  90, 0, 'demo_civilian_car_west.jpg','NO',  NOW() - INTERVAL 2 MINUTE);

INSERT INTO siren_detections (mic_id, road, sound_class, confidence, frequency_hz, decibel_level, consecutive_hits, is_simulated, audio_filename, siren_detected, detected_at)
VALUES 
('MIC-NORTH-01', 'NORTH', 'EMERGENCY_SIREN', 0.992, 1195.3, 78.4, 2, 0, 'demo_siren_north.wav', 'YES', NOW() - INTERVAL 15 MINUTE),
('MIC-EAST-02',  'EAST',  'EMERGENCY_SIREN', 0.915,  980.5, 75.2, 2, 0, 'demo_siren_east.wav',  'YES', NOW() - INTERVAL 10 MINUTE),
('MIC-SOUTH-03', 'WEST',  'EMERGENCY_SIREN', 0.930, 1020.0, 76.0, 2, 0, 'demo_siren_west.wav',  'YES', NOW() - INTERVAL 5 MINUTE), -- Road mismatch!
('MIC-WEST-04',  'WEST',  'TRAFFIC_NOISE',   0.310,  320.0, 62.0, 0, 0, 'demo_traffic_noise.wav','NO',  NOW() - INTERVAL 2 MINUTE);

INSERT INTO emergency_history (ambulance_detection_id, siren_detection_id, selected_road, ambulance_confidence, siren_confidence, emergency_status, emergency_start_time, emergency_confirmation_time, traffic_signal_status)
VALUES
(LAST_INSERT_ID() - 3, LAST_INSERT_ID() - 3, 'NORTH', 0.985, 0.992, 'CONFIRMED', NOW() - INTERVAL 15 MINUTE, NOW() - INTERVAL 15 MINUTE + INTERVAL 2 SECOND, 'NORTH GREEN'),
(LAST_INSERT_ID() - 2, LAST_INSERT_ID() - 2, 'EAST',  0.942, 0.915, 'CONFIRMED', NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 10 MINUTE + INTERVAL 3 SECOND, 'EAST GREEN'),
(LAST_INSERT_ID() - 1, LAST_INSERT_ID() - 1, 'SOUTH', 0.967, 0.930, 'REJECTED',  NOW() - INTERVAL 5 MINUTE,  NULL, 'ALL_RED'); -- Mismatch: Vision=South, Siren=West

INSERT INTO emergency_reset_history (emergency_id, selected_road, previous_signal_status, reset_timestamp, reset_status)
VALUES
(LAST_INSERT_ID() - 2, 'NORTH', 'NORTH GREEN', NOW() - INTERVAL 14 MINUTE, 'RESET_SUCCESS'),
(LAST_INSERT_ID() - 1, 'EAST',  'EAST GREEN',  NOW() - INTERVAL 9 MINUTE,  'RESET_SUCCESS');
*/

