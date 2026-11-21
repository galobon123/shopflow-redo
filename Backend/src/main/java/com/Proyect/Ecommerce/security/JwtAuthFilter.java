package com.Proyect.Ecommerce.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Request attribute with the token's session id, for SessionController. */
    public static final String SESSION_ID = "sessionId";

    private final JwtService jwtService;
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final UserDetailsServiceImpl userDetailsService;
    private final SessionService sessions;
    private final RestAuthenticationHandlers errors;

    public JwtAuthFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService,
                         SessionService sessions, RestAuthenticationHandlers errors) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean replaced = false;
        try {
            final String jwt = authHeader.substring(7);
            final String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long userId = jwtService.extractUserId(jwt);
                    String sessionId = jwtService.extractSessionId(jwt);
                    boolean valid = jwtService.isTokenValid(jwt, userDetails);
                    replaced = valid && !sessions.isCurrent(userId, sessionId);
                    if (valid && !replaced) {
                        request.setAttribute(SESSION_ID, sessionId);
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, userId, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }  
                }
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.warn("Jwt validation failed : {}", e.getMessage());
         }

        if (replaced) {
            // The account was opened in another browser: this one is signed out, even on public pages.
            errors.commence(request, response, new SessionReplacedException());
            return;
        }
        filterChain.doFilter(request, response);
    }
}