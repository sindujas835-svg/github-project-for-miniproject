import java.util.PriorityQueue;
import java.util.Comparator;

public class EmergencyQueue {

    static class AmbulanceRequest {
        String ambulanceId;
        String direction;
        int priority;
        int arrivalOrder;

        AmbulanceRequest(String ambulanceId, String direction,
                         int priority, int arrivalOrder) {
            this.ambulanceId = ambulanceId;
            this.direction = direction;
            this.priority = priority;
            this.arrivalOrder = arrivalOrder;
        }

        public String toString() {
            return ambulanceId
                + " | Direction: " + direction
                + " | Priority: " + priority
                + " | Arrival: " + arrivalOrder;
        }
    }

    public static void main(String[] args) {

        PriorityQueue<AmbulanceRequest> queue =
            new PriorityQueue<>(
                Comparator
                    .comparingInt((AmbulanceRequest r) -> r.priority)
                    .thenComparingInt(r -> r.arrivalOrder)
            );

        queue.offer(new AmbulanceRequest("AMB-101", "North", 2, 1));
        queue.offer(new AmbulanceRequest("AMB-102", "East", 1, 2));
        queue.offer(new AmbulanceRequest("AMB-103", "West", 3, 3));
        queue.offer(new AmbulanceRequest("AMB-104", "South", 1, 4));

        System.out.println("BRAIN SPARKS");
        System.out.println("Smart Traffic Signal Control");
        System.out.println("Emergency Priority Queue");
        System.out.println("---------------------------");

        while (!queue.isEmpty()) {
            AmbulanceRequest request = queue.poll();
            System.out.println("Processing: " + request);
        }
    }
}