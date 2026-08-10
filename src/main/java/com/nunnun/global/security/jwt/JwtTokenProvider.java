package com.nunnun.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }

    public GeneratedJwt createAccessToken(Long userId) {
        return createToken(userId, ACCESS_TOKEN_TYPE, jwtProperties.getAccessExpiration());
    }

    public GeneratedJwt createRefreshToken(Long userId) {
        return createToken(userId, REFRESH_TOKEN_TYPE, jwtProperties.getRefreshExpiration());
    }

    public Long getUserIdFromAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new MalformedJwtException("The token is not an access token.");
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException("The token subject is invalid.");
        }
    }

    private GeneratedJwt createToken(Long userId, String tokenType, long expirationMilliseconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMilliseconds);
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new GeneratedJwt(token, expiresAt);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
