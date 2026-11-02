package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.EmergencyEvent;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyEventRepository extends JpaRepository<EmergencyEvent, Long> {

    List<EmergencyEvent> findByRoad(Road road);

    List<EmergencyEvent> findByVehicleId(String vehicleId);

    List<EmergencyEvent> findTop20ByOrderByStartedAtDesc();
}
