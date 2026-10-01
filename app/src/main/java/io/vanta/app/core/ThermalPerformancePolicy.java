package io.vanta.app.core;

public enum ThermalPerformancePolicy {
    ECO("eco", 30),
    BALANCED("balanced", 60),
    PERFORMANCE("performance", 0),
    UNLIMITED("unlimited", 0);

    public final String id;
    public final int fpsCeiling;

    ThermalPerformancePolicy(String id, int fpsCeiling) {
        this.id = id;
        this.fpsCeiling = fpsCeiling;
    }

    public static ThermalPerformancePolicy fromId(String id) {
        for (ThermalPerformancePolicy policy : values()) {
            if (policy.id.equals(id)) return policy;
        }
        return BALANCED;
    }

    public int limit(int userLimit, boolean severeThermalOverride) {
        int policyLimit = fpsCeiling;
        if (severeThermalOverride) policyLimit = policyLimit == 0 ? 30 : Math.min(policyLimit, 30);
        if (userLimit > 0 && policyLimit > 0) return Math.min(userLimit, policyLimit);
        return userLimit > 0 ? userLimit : policyLimit;
    }
}
