package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.AmbulanceDetection;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AmbulanceDetectionRepository extends JpaRepository<AmbulanceDetection, Long> {

    List<AmbulanceDetection> findByRoadAndDetectedAtAfter(Road road, LocalDateTime timestamp);

    List<AmbulanceDetection> findTop20ByOrderByDetectedAtDesc();
}
