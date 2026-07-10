package com.fanet.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET = "test-security-secret-that-is-longer-than-thirty-two-bytes";

    @Test
    void rejectsMissingOrShortSecrets() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("", 3600000));
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short", 3600000));
    }

    @Test
    void createsAndValidatesRoleToken() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 3600000);
        String token = jwtUtil.generateToken("operator1", "operator");

        assertTrue(jwtUtil.isValid(token));
        assertEquals("operator1", jwtUtil.getUsername(token));
        assertEquals("operator", jwtUtil.parseToken(token).get("role", String.class));
    }
}
