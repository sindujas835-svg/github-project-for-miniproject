
package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.EmergencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyRequestRepository
        extends JpaRepository<EmergencyRequest, Integer> {
}
