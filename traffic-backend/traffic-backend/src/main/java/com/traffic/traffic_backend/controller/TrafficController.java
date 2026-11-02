
package com.traffic.traffic_backend.controller;

import com.traffic.traffic_backend.entity.EmergencyRequest;
import com.traffic.traffic_backend.entity.DetectionHistory;
import com.traffic.traffic_backend.repository.EmergencyRequestRepository;
import com.traffic.traffic_backend.repository.DetectionHistoryRepository;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
    "http://127.0.0.1:5500",
    "http://localhost:5500"
})
public class TrafficController {

    private final EmergencyRequestRepository requestRepository;
    private final DetectionHistoryRepository historyRepository;

    public TrafficController(
            EmergencyRequestRepository requestRepository,
            DetectionHistoryRepository historyRepository) {
        this.requestRepository = requestRepository;
        this.historyRepository = historyRepository;
    }

    @GetMapping("/requests")
    public List<EmergencyRequest> getRequests() {
        return requestRepository.findAll();
    }

    @PostMapping("/requests")
    public EmergencyRequest addRequest(
            @RequestBody EmergencyRequest request) {
        return requestRepository.save(request);
    }

    @GetMapping("/history")
    public List<DetectionHistory> getHistory() {
        return historyRepository.findAll();
    }

    @PostMapping("/history")
    public DetectionHistory addHistory(
            @RequestBody DetectionHistory history) {
        return historyRepository.save(history);
    }
}
