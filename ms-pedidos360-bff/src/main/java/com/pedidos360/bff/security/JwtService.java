package com.pedidos360.bff.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expected-issuer:}")
    private String expectedIssuer;

    @Value("${jwt.expected-audience:}")
    private String expectedAudience;

    /**
     * Tolerancia de desfase de reloj (clock skew) en segundos, aplicada a las
     * validaciones de iat/exp. Evita rechazar tokens recién emitidos cuando el
     * reloj del cliente va unos segundos adelante del servidor (caso típico en
     * despliegues detrás de API Gateway/ALB, donde la latencia de la cadena
     * hace que el iat del token quede "en el futuro" visto desde el BFF).
     */
    @Value("${jwt.clock-skew-seconds:30}")
    private long clockSkewSeconds;

    /**
     * Valida el JWT: firma (HMAC-SHA), vigencia y, si están configurados,
     * el issuer y la audience esperados (evidencia para EP1).
     */
    public Claims validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Instant now = Instant.now();
        if (claims.getExpiration() == null
                || claims.getExpiration().toInstant().isBefore(now.minusSeconds(clockSkewSeconds))) {
            throw new JwtException("Token expirado");
        }
        if (claims.getIssuedAt() != null
                && claims.getIssuedAt().toInstant().isAfter(now.plusSeconds(clockSkewSeconds))) {
            throw new JwtException("Token emitido en el futuro");
        }

        if (expectedIssuer != null && !expectedIssuer.isBlank()
                && !expectedIssuer.equals(claims.getIssuer())) {
            log.warn("Issuer inválido. Esperado={}, token={}", expectedIssuer, claims.getIssuer());
            throw new JwtException("Issuer inválido");
        }
        if (expectedAudience != null && !expectedAudience.isBlank()
                && !audienceMatches(claims, expectedAudience)) {
            log.warn("Audience inválida. Esperada={}, token={}", expectedAudience, claims.get("aud"));
            throw new JwtException("Audience inválida");
        }

        return claims;
    }

    /**
     * El claim "aud" puede venir como String o como lista. Acepta cualquiera
     * de las dos formas mientras contenga la audiencia esperada.
     */
    private boolean audienceMatches(Claims claims, String expected) {
        Object aud = claims.get("aud");
        if (aud instanceof String s) {
            return expected.equals(s);
        }
        if (aud instanceof Collection<?> col) {
            return col.stream().anyMatch(expected::equals);
        }
        if (aud instanceof String[] arr) {
            for (String a : arr) {
                if (expected.equals(a)) return true;
            }
        }
        return false;
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}