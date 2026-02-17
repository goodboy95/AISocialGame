package com.aisocialgame.dto.admin;

import java.util.List;

public class AdminIntegrationStatusResponse {
    private List<ServiceStatus> services;

    public AdminIntegrationStatusResponse(List<ServiceStatus> services) {
        this.services = services;
    }

    public List<ServiceStatus> getServices() {
        return services;
    }

    public record ServiceStatus(
            String service,
            boolean reachable,
            String message
    ) {
    }
}
