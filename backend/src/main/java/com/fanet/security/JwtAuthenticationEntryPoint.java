package com.fanet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationEntryPoint(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String header = request.getHeader("Authorization");
        boolean validJwt = header != null && header.startsWith("Bearer ")
                && jwtUtil.isValid(header.substring(7));
        response.sendError(validJwt ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value());
    }
}
