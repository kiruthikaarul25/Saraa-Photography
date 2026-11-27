package com.shop.config;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Simple in-memory login throttle: 5 failures per 15 minutes per ip+username. */
@Component
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public static String key(String ip, String username) {
        return ip + "|" + (username == null ? "" : username.trim().toLowerCase());
    }

    public boolean isBlocked(String key) {
        Deque<Long> d = failures.get(key);
        if (d == null) return false;
        synchronized (d) {
            prune(d);
            return d.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String key) {
        Deque<Long> d = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            prune(d);
            d.addLast(System.currentTimeMillis());
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Long> d) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!d.isEmpty() && d.peekFirst() < cutoff) d.removeFirst();
    }
}