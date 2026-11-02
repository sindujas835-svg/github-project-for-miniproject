package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.TrafficSignalHistoryRecord;
import com.traffic.traffic_backend.traffic.Road;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrafficSignalHistoryRecordRepository extends JpaRepository<TrafficSignalHistoryRecord, Long> {

    List<TrafficSignalHistoryRecord> findByRoad(Road road);

    List<TrafficSignalHistoryRecord> findTop50ByOrderByRecordedAtDesc();
}
