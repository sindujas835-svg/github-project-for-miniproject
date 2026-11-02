package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.EmergencyVerification;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyVerificationRepository extends JpaRepository<EmergencyVerification, Long> {

    List<EmergencyVerification> findByRoadOrderByVerifiedAtDesc(Road road);

    List<EmergencyVerification> findTop20ByOrderByVerifiedAtDesc();
}
