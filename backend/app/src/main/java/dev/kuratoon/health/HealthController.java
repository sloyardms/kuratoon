package dev.kuratoon.health;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health check under the API prefix, routed through nginx. Delegates to the Actuator
 * health endpoint (which stays internal at {@code /actuator/health}) and exposes the status only.
 */
@RestController
class HealthController {

    private final HealthEndpoint healthEndpoint;

    HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/api/v1/health")
    ResponseEntity<HealthResponse> health() {
        Status status = healthEndpoint.health().getStatus();
        HttpStatus httpStatus = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(new HealthResponse(status.getCode()));
    }

    record HealthResponse(String status) {}
}
