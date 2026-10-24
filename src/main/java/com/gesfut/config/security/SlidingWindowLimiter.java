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
        if (!entries.containsKey(key) && entries.size() >= maxKeys) { cleanup(now); if (entries.size() >= maxKeys) evictOldest(Math.max(1, maxKeys / 10)); }
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

    /** Lleno: se descarta de una vez el 10% con uso mas viejo (un solo recorrido cada maxKeys/10 claves nuevas, no por cada una). */
    private synchronized void evictOldest(int n) {
        if (entries.size() < maxKeys) return;
        java.util.List<java.util.Map.Entry<String, Long>> lasts = new java.util.ArrayList<>();
        for (var en : entries.entrySet()) { Long last; synchronized (en.getValue()) { last = en.getValue().hits().peekLast(); } lasts.add(java.util.Map.entry(en.getKey(), last == null ? Long.MIN_VALUE : last)); }
        lasts.sort(java.util.Map.Entry.comparingByValue());
        for (int i = 0; i < n && i < lasts.size(); i++) entries.remove(lasts.get(i).getKey());
    }

    int size() { return entries.size(); }
}
