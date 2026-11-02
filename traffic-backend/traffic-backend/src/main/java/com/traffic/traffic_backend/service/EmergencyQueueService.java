package com.traffic.traffic_backend.service;

import com.traffic.traffic_backend.dto.EmergencyQueueDto;
import com.traffic.traffic_backend.entity.EmergencyEvent;
import com.traffic.traffic_backend.entity.EmergencyQueueItem;
import com.traffic.traffic_backend.repository.EmergencyEventRepository;
import com.traffic.traffic_backend.repository.EmergencyQueueItemRepository;
import com.traffic.traffic_backend.traffic.EmergencyClearanceListener;
import com.traffic.traffic_backend.traffic.Road;
import com.traffic.traffic_backend.traffic.TrafficSignalController;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * EmergencyQueueService: Multi-Ambulance Priority Queue Management.
 *
 * EXPLANATION FOR CS STUDENTS:
 * What happens if an ambulance arrives on the EAST road while an ambulance on the NORTH road
 * is currently receiving priority? Or what if two ambulances arrive simultaneously on different roads?
 *
 * We cannot give green signals to both roads at the same time (that would cause a fatal collision!).
 * Instead, this service implements a Priority Queue:
 * 1. PRIORITY ORDERING:
 *    - Level 1 = Critical (Cardiac arrest, severe stroke, active bleeding).
 *    - Level 2 = High (Major trauma, bone fractures).
 *    - Level 3 = Normal (Routine transfer, stable patient).
 * 2. FIFO TIE-BREAKING:
 *    - If two ambulances have the same priority level, the one that arrived first (lowest arrivalOrder)
 *      is serviced first.
 * 3. SEQUENTIAL DISPATCH:
 *    - The first ambulance completes its clearance safely through ALL-RED clearance.
 *    - Once cleared, the next queued ambulance is immediately dispatched without delay.
 *    - Normal traffic cycle resumes only after all queued ambulances have been cleared.
 */
@Service
public class EmergencyQueueService implements EmergencyClearanceListener {

    private static final Logger log = LoggerFactory.getLogger(EmergencyQueueService.class);

    private final EmergencyQueueItemRepository queueRepo;
    private final EmergencyEventRepository eventRepo;
    private final TrafficSignalController signalController;
    private final TelemetryLoggingService telemetryService;
    private final SimpMessagingTemplate messagingTemplate;

    private final ReentrantLock queueLock = new ReentrantLock();
    private final AtomicInteger orderCounter = new AtomicInteger(1);
    private String currentlyServicedVehicleId = null;

    public EmergencyQueueService(
            EmergencyQueueItemRepository queueRepo,
            EmergencyEventRepository eventRepo,
            @Lazy TrafficSignalController signalController,
            TelemetryLoggingService telemetryService,
            SimpMessagingTemplate messagingTemplate) {
        this.queueRepo = queueRepo;
        this.eventRepo = eventRepo;
        this.signalController = signalController;
        this.telemetryService = telemetryService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        signalController.setClearanceListener(this);
    }

    /**
     * Enqueues an approaching emergency vehicle.
     * If the intersection is clear, immediately dispatches preemption.
     * Otherwise, holds in priority queue until the active vehicle clears.
     */
    public EmergencyQueueItem enqueueVehicle(String vehicleId, Road road, int priorityLevel) {
        queueLock.lock();
        try {
            // Check if this vehicle is already queued or active to prevent duplicate entries
            if (currentlyServicedVehicleId != null && currentlyServicedVehicleId.equalsIgnoreCase(vehicleId)) {
                log.info("Vehicle {} is already actively receiving priority. Refreshing state.", vehicleId);
                return null;
            }

            if (queueRepo.existsByVehicleIdAndStatus(vehicleId, "QUEUED")) {
                log.info("Vehicle {} is already in the priority queue.", vehicleId);
                return null;
            }

            int order = orderCounter.getAndIncrement();
            EmergencyQueueItem item = new EmergencyQueueItem(vehicleId, road, priorityLevel, order);
            item = queueRepo.save(item);

            log.info("ENQUEUED EMERGENCY VEHICLE: {} on Road {} (Priority: {}, Arrival Order: {})",
                    vehicleId, road, priorityLevel, order);

            telemetryService.logSystemEvent("EMERGENCY_QUEUE", "ENQUEUED",
                    String.format("Vehicle %s enqueued for Road %s (Priority %d, Order %d)",
                            vehicleId, road, priorityLevel, order));

            // If no emergency is currently active, immediately dispatch this vehicle
            if (currentlyServicedVehicleId == null && !signalController.isEmergencyActive()) {
                dispatchVehicle(item);
            } else {
                // Record event: enqueued waiting
                eventRepo.save(new EmergencyEvent(null, vehicleId, road, "QUEUED",
                        "Vehicle enqueued behind active emergency " + currentlyServicedVehicleId));
            }

            broadcastQueue();
            return item;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Dispatches an emergency vehicle to the signal controller for preemption.
     */
    private void dispatchVehicle(EmergencyQueueItem item) {
        this.currentlyServicedVehicleId = item.getVehicleId();
        item.setStatus("DISPATCHED");
        item.setDispatchedAt(LocalDateTime.now());
        queueRepo.save(item);

        log.info("DISPATCHING EMERGENCY VEHICLE: {} on Road {} (Priority: {})",
                item.getVehicleId(), item.getRoad(), item.getPriorityLevel());

        eventRepo.save(new EmergencyEvent(null, item.getVehicleId(), item.getRoad(), "DISPATCHED",
                "Priority preemption requested for road " + item.getRoad()));

        signalController.requestEmergencyPreemption(item.getRoad(), item.getVehicleId(), item.getPriorityLevel());
    }

    /**
     * Callback triggered when the active emergency vehicle completes its post-clearance.
     */
    @Override
    public void onEmergencyCompleted(String vehicleId, Road road) {
        queueLock.lock();
        try {
            log.info("Emergency vehicle {} on Road {} completed clearance.", vehicleId, road);

            if (currentlyServicedVehicleId != null && currentlyServicedVehicleId.equalsIgnoreCase(vehicleId)) {
                // Mark item as cleared
                List<EmergencyQueueItem> items = queueRepo.findByStatusOrderByPriorityLevelAscArrivalOrderAsc("DISPATCHED");
                for (EmergencyQueueItem item : items) {
                    if (item.getVehicleId().equalsIgnoreCase(vehicleId)) {
                        item.setStatus("CLEARED");
                        queueRepo.save(item);
                    }
                }
                this.currentlyServicedVehicleId = null;
            }

            // Check if there are more ambulances waiting in the queue
            List<EmergencyQueueItem> pending = queueRepo.findByStatusOrderByPriorityLevelAscArrivalOrderAsc("QUEUED");
            if (!pending.isEmpty()) {
                EmergencyQueueItem next = pending.get(0);
                log.info("NEXT VEHICLE IN QUEUE: {} on Road {} (Priority: {}). Dispatching immediately.",
                        next.getVehicleId(), next.getRoad(), next.getPriorityLevel());
                dispatchVehicle(next);
            } else {
                log.info("Emergency queue is empty. Traffic signal controller returning to normal cycle.");
            }

            broadcastQueue();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Returns the list of currently queued and dispatched vehicles.
     */
    public List<EmergencyQueueDto> getActiveQueue() {
        queueLock.lock();
        try {
            List<EmergencyQueueItem> queued = queueRepo.findByStatusOrderByPriorityLevelAscArrivalOrderAsc("QUEUED");
            List<EmergencyQueueItem> dispatched = queueRepo.findByStatusOrderByPriorityLevelAscArrivalOrderAsc("DISPATCHED");

            List<EmergencyQueueDto> dtos = new ArrayList<>();
            for (EmergencyQueueItem item : dispatched) {
                dtos.add(toDto(item));
            }
            for (EmergencyQueueItem item : queued) {
                dtos.add(toDto(item));
            }
            return dtos;
        } finally {
            queueLock.unlock();
        }
    }

    private EmergencyQueueDto toDto(EmergencyQueueItem item) {
        return new EmergencyQueueDto(
                item.getId(),
                item.getVehicleId(),
                item.getRoad(),
                item.getPriorityLevel(),
                item.getArrivalOrder(),
                item.getStatus(),
                item.getEnqueuedAt(),
                item.getDispatchedAt()
        );
    }

    /**
     * Broadcasts updated queue state to WebSocket topic `/topic/emergency-queue`.
     */
    public void broadcastQueue() {
        messagingTemplate.convertAndSend("/topic/emergency-queue", getActiveQueue());
    }

    public String getCurrentlyServicedVehicleId() {
        return currentlyServicedVehicleId;
    }

    public void resetQueue() {
        queueLock.lock();
        try {
            this.currentlyServicedVehicleId = null;
            queueRepo.deleteAll();
            broadcastQueue();
            log.info("Emergency queue cleared and reset.");
        } finally {
            queueLock.unlock();
        }
    }
}
