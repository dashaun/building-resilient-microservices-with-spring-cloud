package com.bbqwednesday.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Where the gateway CircuitBreaker filter forwards when survey-service is open.
 * The user gets a friendly 503 instead of a hung request or a raw stack trace.
 */
@RestController
public class FallbackController {

    @RequestMapping({"/fallback/results", "/fallback/ui"})
    public ResponseEntity<Map<String, Object>> serviceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503,
                        "message", "The service is unavailable. Try again shortly."));
    }

    @RequestMapping("/fallback/survey")
    public ResponseEntity<Map<String, Object>> surveyFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "message", "The pit is backed up — survey-service is unavailable. Try again shortly."));
    }
}
