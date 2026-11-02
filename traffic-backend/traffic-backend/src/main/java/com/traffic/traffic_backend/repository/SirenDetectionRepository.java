package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.SirenDetection;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SirenDetectionRepository extends JpaRepository<SirenDetection, Long> {

    List<SirenDetection> findByRoadAndDetectedAtAfter(Road road, LocalDateTime timestamp);

    List<SirenDetection> findTop20ByOrderByDetectedAtDesc();
}
