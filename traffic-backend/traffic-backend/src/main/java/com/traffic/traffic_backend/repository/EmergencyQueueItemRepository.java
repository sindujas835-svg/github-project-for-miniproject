package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.EmergencyQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyQueueItemRepository extends JpaRepository<EmergencyQueueItem, Long> {

    List<EmergencyQueueItem> findByStatusOrderByPriorityLevelAscArrivalOrderAsc(String status);

    boolean existsByVehicleIdAndStatus(String vehicleId, String status);
}
