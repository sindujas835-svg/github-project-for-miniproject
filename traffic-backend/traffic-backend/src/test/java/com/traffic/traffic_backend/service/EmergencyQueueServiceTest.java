package com.traffic.traffic_backend.service;

import com.traffic.traffic_backend.entity.EmergencyEvent;
import com.traffic.traffic_backend.entity.EmergencyQueueItem;
import com.traffic.traffic_backend.repository.EmergencyEventRepository;
import com.traffic.traffic_backend.repository.EmergencyQueueItemRepository;
import com.traffic.traffic_backend.traffic.Road;
import com.traffic.traffic_backend.traffic.TrafficSignalController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmergencyQueueServiceTest {

    private EmergencyQueueItemRepository queueRepo;
    private EmergencyEventRepository eventRepo;
    private TrafficSignalController signalController;
    private TelemetryLoggingService telemetryService;
    private SimpMessagingTemplate messagingTemplate;

    private EmergencyQueueService queueService;
    private final List<EmergencyQueueItem> inMemoryDb = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inMemoryDb.clear();
        queueRepo = mock(EmergencyQueueItemRepository.class);
        eventRepo = mock(EmergencyEventRepository.class);
        signalController = mock(TrafficSignalController.class);
        telemetryService = mock(TelemetryLoggingService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        when(queueRepo.save(any(EmergencyQueueItem.class))).thenAnswer(i -> {
            EmergencyQueueItem item = i.getArgument(0);
            if (!inMemoryDb.contains(item)) {
                inMemoryDb.add(item);
            }
            return item;
        });

        when(queueRepo.findByStatusOrderByPriorityLevelAscArrivalOrderAsc("QUEUED")).thenAnswer(i ->
                inMemoryDb.stream().filter(it -> "QUEUED".equals(it.getStatus())).toList()
        );

        when(queueRepo.findByStatusOrderByPriorityLevelAscArrivalOrderAsc("DISPATCHED")).thenAnswer(i ->
                inMemoryDb.stream().filter(it -> "DISPATCHED".equals(it.getStatus())).toList()
        );

        queueService = new EmergencyQueueService(
                queueRepo, eventRepo, signalController, telemetryService, messagingTemplate
        );
    }

    @Test
    void testImmediateDispatchWhenIdle() {
        when(signalController.isEmergencyActive()).thenReturn(false);

        EmergencyQueueItem item = queueService.enqueueVehicle("AMB-101", Road.NORTH, 1);

        assertNotNull(item);
        assertEquals("DISPATCHED", item.getStatus());
        verify(signalController).requestEmergencyPreemption(Road.NORTH, "AMB-101", 1);
    }

    @Test
    void testEnqueueBehindActiveEmergency() {
        // First vehicle is active
        when(signalController.isEmergencyActive()).thenReturn(false);
        queueService.enqueueVehicle("AMB-101", Road.NORTH, 1);

        // Second vehicle arrives while first is active
        when(signalController.isEmergencyActive()).thenReturn(true);
        EmergencyQueueItem second = queueService.enqueueVehicle("AMB-202", Road.EAST, 2);

        assertNotNull(second);
        assertEquals("QUEUED", second.getStatus(), "Second vehicle must be queued behind active emergency");
        // Controller preemption was called for first, but not yet for second
        verify(signalController, times(1)).requestEmergencyPreemption(any(), any(), anyInt());
    }

    @Test
    void testSequentialDispatchOnClearance() {
        // Enqueue first (Critical 1)
        when(signalController.isEmergencyActive()).thenReturn(false);
        queueService.enqueueVehicle("AMB-101", Road.NORTH, 1);

        // Enqueue second (High 2)
        when(signalController.isEmergencyActive()).thenReturn(true);
        queueService.enqueueVehicle("AMB-202", Road.EAST, 2);

        // First ambulance finishes clearance
        queueService.onEmergencyCompleted("AMB-101", Road.NORTH);

        // Second ambulance should now be dispatched!
        verify(signalController).requestEmergencyPreemption(Road.EAST, "AMB-202", 2);
    }
}
