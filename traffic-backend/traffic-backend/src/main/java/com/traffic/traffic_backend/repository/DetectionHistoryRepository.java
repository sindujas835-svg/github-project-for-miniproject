
package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.DetectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionHistoryRepository
        extends JpaRepository<DetectionHistory, Integer> {
}
