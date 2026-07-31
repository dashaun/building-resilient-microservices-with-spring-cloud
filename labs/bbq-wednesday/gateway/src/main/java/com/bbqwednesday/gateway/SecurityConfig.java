package com.bbqwednesday.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Security is a cross-cutting concern the gateway owns ONCE, at the edge, so no
 * downstream service re-implements auth. Public reads stay open; casting a vote
 * (POST /survey-service/submit) requires a valid JWT bearer token.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> exchange
                // Casting a vote needs a token...
                .pathMatchers(HttpMethod.POST, "/survey-service/submit").authenticated()
                // ...everything else (UI, questions, live results, /token) is open.
                .anyExchange().permitAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Validates HS256 tokens signed with a shared secret. In production this is a
     * jwk-set-uri pointing at your identity provider; the shared secret keeps the
     * workshop offline while exercising the exact same resource-server machinery.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${bbq.jwt.secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
