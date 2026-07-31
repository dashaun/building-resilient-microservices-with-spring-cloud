package com.bbqwednesday.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * DEV ONLY. Mints a short-lived HS256 token so participants can POST a vote
 * without standing up a full identity provider. Delete this before production —
 * a real client obtains tokens from your IdP, never from the gateway.
 */
@RestController
public class DevTokenController {

    private final byte[] secret;

    public DevTokenController(@Value("${bbq.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/token")
    public Map<String, String> token() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("bbq-fan")
                .issuer("bbq-wednesday")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .claim("scope", "vote")
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret));

        return Map.of("access_token", jwt.serialize(), "token_type", "Bearer");
    }
}
