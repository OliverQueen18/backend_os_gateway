package com.osgateway.common.enums;

public enum Priority {
    URGENT(10),
    NORMAL(5),
    BASSE(1);

    private final int rabbitPriority;

    Priority(int rabbitPriority) {
        this.rabbitPriority = rabbitPriority;
    }

    public int getRabbitPriority() {
        return rabbitPriority;
    }
}
