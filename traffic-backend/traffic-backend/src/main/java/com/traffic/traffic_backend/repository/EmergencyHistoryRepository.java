package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.EmergencyHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmergencyHistoryRepository extends JpaRepository<EmergencyHistory, Long> {
    List<EmergencyHistory> findTop20ByOrderByEmergencyStartTimeDesc();
    Optional<EmergencyHistory> findTopByEmergencyStatusOrderByEmergencyStartTimeDesc(String status);
}

