package com.traffic.traffic_backend.repository;

import com.traffic.traffic_backend.entity.SystemLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemLogRecordRepository extends JpaRepository<SystemLogRecord, Long> {

    List<SystemLogRecord> findByLogLevel(String logLevel);

    List<SystemLogRecord> findByComponent(String component);

    List<SystemLogRecord> findTop50ByOrderByLoggedAtDesc();
}
