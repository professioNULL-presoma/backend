package com.professionull.presoma.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
final class JwtAccessTokens {
    private static final String ISSUER = "jipsa-backend";
    static final Duration LIFETIME = Duration.ofHours(1);
    private final byte[] secret;

    JwtAccessTokens(@Value("${AUTH_JWT_SECRET:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    String issue(UUID userId) {
        checkConfiguration();
        Instant now = Instant.now();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(LIFETIME)))
                .build());
        try {
            token.sign(new MACSigner(secret));
            return token.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not sign access token", exception);
        }
    }

    UUID requireUserId(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw AuthException.invalidToken();
        }
        checkConfiguration();
        try {
            SignedJWT token = SignedJWT.parse(authorization.substring(7));
            if (!JWSAlgorithm.HS256.equals(token.getHeader().getAlgorithm()) || !token.verify(new MACVerifier(secret))) {
                throw AuthException.invalidToken();
            }
            JWTClaimsSet claims = token.getJWTClaimsSet();
            Date expiresAt = claims.getExpirationTime();
            String subject = claims.getSubject();
            if (!ISSUER.equals(claims.getIssuer()) || expiresAt == null || !expiresAt.toInstant().isAfter(Instant.now())
                    || subject == null) {
                throw AuthException.invalidToken();
            }
            return UUID.fromString(subject);
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw AuthException.invalidToken();
        }
    }

    private void checkConfiguration() {
        if (secret.length < 32) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "로그인 서비스를 준비 중이에요.");
        }
    }
}
