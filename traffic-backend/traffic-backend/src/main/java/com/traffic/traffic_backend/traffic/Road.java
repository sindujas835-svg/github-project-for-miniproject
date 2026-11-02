package com.traffic.traffic_backend.traffic;

/**
 * Represents the four approaching roads of the intelligent intersection.
 */
public enum Road {
    NORTH,
    SOUTH,
    EAST,
    WEST;

    /**
     * Returns the next road in the normal four-way cycle:
     * NORTH -> SOUTH -> EAST -> WEST -> NORTH
     */
    public Road next() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> WEST;
            case WEST -> NORTH;
        };
    }
}
