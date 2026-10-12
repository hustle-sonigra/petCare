package com.pettracker.features.auth;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {
    public static final String OWNER_ID = "authenticatedOwnerId";
    private final ObjectProvider<JwtService> jwt;
    public AuthTokenFilter(ObjectProvider<JwtService> jwt) { this.jwt = jwt; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        JwtService service = jwt.getIfAvailable();
        if (service != null && header != null && header.startsWith("Bearer ")) { UUID ownerId = service.verifyAndGetOwner(header.substring(7)); if (ownerId != null) request.setAttribute(OWNER_ID, ownerId); }
        chain.doFilter(request, response);
    }
}
