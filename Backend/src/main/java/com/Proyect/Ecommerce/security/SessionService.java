package com.Proyect.Ecommerce.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * One open session per account: signing in from another browser closes the previous one at once.
 * Redis keeps `session:{userId}` → the id of the only valid session, which the JWT carries as `sid`;
 * a token with another id is rejected. Browsers keep a stream open (see SessionController) and hear
 * "replaced" as soon as a newer session starts, on whichever backend instance (Redis pub/sub).
 */
@Service
public class SessionService {

    static final String CHANNEL = "session:replaced";
    /** A stream closes after this; the browser opens another one. */
    static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final StringRedisTemplate redis;
    private final Duration ttl;
    /** userId → this instance's open streams, each with the session id it was opened for. */
    private final Map<Long, Map<SseEmitter, String>> streams = new ConcurrentHashMap<>();

    public SessionService(StringRedisTemplate redis, @Value("${jwt.expiration}") long expirationMs) {
        this.redis = redis;
        this.ttl = Duration.ofMillis(expirationMs);
    }

    static String key(Long userId) {
        return "session:" + userId;
    }

    /** A new session for this account; any other one stops working now. */
    public String start(Long userId) {
        String sessionId = SecureTokens.newToken();
        redis.opsForValue().set(key(userId), sessionId, ttl);
        redis.convertAndSend(CHANNEL, userId + ":" + sessionId);
        return sessionId;
    }

    /** The open session, for a fresh token in the same browser; starts one if there is none. */
    public String current(Long userId) {
        String sessionId = redis.opsForValue().get(key(userId));
        return sessionId != null ? sessionId : start(userId);
    }

    public boolean isCurrent(Long userId, String sessionId) {
        return sessionId != null && sessionId.equals(redis.opsForValue().get(key(userId)));
    }

    /** A stream that gets a "replaced" event and closes when another browser signs in. */
    public SseEmitter watch(Long userId, String sessionId) throws IOException {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Map<SseEmitter, String> mine = streams.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
        mine.put(emitter, sessionId);
        Runnable forget = () -> mine.remove(emitter);
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(error -> forget.run());
        // Sends the headers now, so the browser knows the stream is open.
        emitter.send(SseEmitter.event().comment("open"));
        // It may have been replaced between the check in the filter and here.
        if (!isCurrent(userId, sessionId)) {
            replace(emitter);
        }
        return emitter;
    }

    /** Called with each message of the channel: "userId:newSessionId". */
    void replaced(String message) {
        int colon = message.indexOf(':');
        Long userId = Long.valueOf(message.substring(0, colon));
        String current = message.substring(colon + 1);
        Map<SseEmitter, String> mine = streams.getOrDefault(userId, Map.of());
        Set.copyOf(mine.entrySet()).stream()
                .filter(stream -> !stream.getValue().equals(current))
                .forEach(stream -> replace(stream.getKey()));
    }

    private static void replace(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("replaced").data("Abriste tu cuenta en otro navegador."));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // The browser already left.
            emitter.completeWithError(e);
        }
    }
}
