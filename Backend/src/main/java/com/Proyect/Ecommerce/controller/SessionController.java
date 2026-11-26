package com.Proyect.Ecommerce.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.Proyect.Ecommerce.security.JwtAuthFilter;
import com.Proyect.Ecommerce.security.SessionService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    /** Stays open while the session lives; says "replaced" when the account is opened elsewhere. */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Authentication auth,
                             @RequestAttribute(JwtAuthFilter.SESSION_ID) String sessionId,
                             HttpServletResponse response) throws IOException {
        // Nginx (front-end/nginx.conf) would otherwise hold the events in its buffer.
        response.setHeader("X-Accel-Buffering", "no");
        return sessions.watch((Long) auth.getCredentials(), sessionId);
    }
}
