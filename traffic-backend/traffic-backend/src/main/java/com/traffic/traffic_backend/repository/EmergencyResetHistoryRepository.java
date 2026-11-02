package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.EmergencyResetHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyResetHistoryRepository extends JpaRepository<EmergencyResetHistory, Long> {
    List<EmergencyResetHistory> findTop20ByOrderByResetTimestampDesc();
}

