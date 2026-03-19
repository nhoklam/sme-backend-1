package sme.backend.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sme.backend.security.UserPrincipal;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Provider
 *
 * Token payload chứa:
 * - sub: username
 * - userId: UUID
 * - role: ROLE_ADMIN | ROLE_MANAGER | ROLE_CASHIER
 * - warehouseId: UUID (NULL cho ADMIN)
 *
 * Đây là cơ chế bảo mật cốt lõi để phân luồng dữ liệu.
 * Backend extract warehouseId từ token để đảm bảo Manager/Cashier
 * chỉ thao tác trên dữ liệu của chi nhánh mình.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Tạo Access Token chứa đầy đủ claims cần thiết
     */
    public String generateAccessToken(UserPrincipal userPrincipal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        JwtBuilder builder = Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getId().toString())
                .claim("role", userPrincipal.getRole().name())
                .claim("fullName", userPrincipal.getFullName())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey());

        // ADMIN không có warehouseId
        if (userPrincipal.getWarehouseId() != null) {
            builder.claim("warehouseId", userPrincipal.getWarehouseId().toString());
        }

        return builder.compact();
    }

    /**
     * Tạo Refresh Token (chỉ chứa subject, sống lâu hơn)
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID getUserIdFromToken(String token) {
        String id = parseClaims(token).get("userId", String.class);
        return id != null ? UUID.fromString(id) : null;
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public UUID getWarehouseIdFromToken(String token) {
        String wid = parseClaims(token).get("warehouseId", String.class);
        return wid != null ? UUID.fromString(wid) : null;
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
