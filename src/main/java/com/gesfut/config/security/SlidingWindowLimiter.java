package com.gesfut.config.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ventana deslizante por clave con memoria acotada: las claves vencen solas (limpieza periodica)
 *  y nunca hay mas de maxKeys; si se llena, se descarta la clave con el ultimo uso mas viejo. */
final class SlidingWindowLimiter {
    private record Entry(Deque<Long> hits, long window) { }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxKeys;
    private final long cleanupEveryMs;
    private volatile long lastCleanup = 0;

    SlidingWindowLimiter(int maxKeys, long cleanupEveryMs) { this.maxKeys = maxKeys; this.cleanupEveryMs = cleanupEveryMs; }

    /** true = bloqueado (ya llego al limite dentro de la ventana). */
    boolean hit(String key, int limit, long window, long now) {
        if (now - lastCleanup > cleanupEveryMs) cleanup(now);
        if (!entries.containsKey(key) && entries.size() >= maxKeys) { cleanup(now); if (entries.size() >= maxKeys) evictOldest(); }
        Entry e = entries.computeIfAbsent(key, k -> new Entry(new ArrayDeque<>(), window));
        synchronized (e) {
            Deque<Long> q = e.hits();
            while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
            if (q.size() >= limit) return true;
            q.addLast(now);
            return false;
        }
    }

    void cleanup(long now) {
        lastCleanup = now;
        entries.entrySet().removeIf(en -> { synchronized (en.getValue()) { Deque<Long> q = en.getValue().hits();
            return q.isEmpty() || now - q.peekLast() > en.getValue().window(); } });
    }

    private void evictOldest() {
        String oldest = null; long t = Long.MAX_VALUE;
        for (var en : entries.entrySet()) { Long last; synchronized (en.getValue()) { last = en.getValue().hits().peekLast(); }
            long v = last == null ? Long.MIN_VALUE : last; if (v < t) { t = v; oldest = en.getKey(); } }
        if (oldest != null) entries.remove(oldest);
    }

    int size() { return entries.size(); }
}
