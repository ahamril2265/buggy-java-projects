package com.orchestrator.service;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResultCache<K, V> {
    private final int capacity;
    private final Map<K, V> entries;

    public ResultCache(int capacity) {
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > ResultCache.this.capacity;
            }
        };
    }

    public V get(K key) {
        return entries.get(key);
    }

    public void put(K key, V value) {
        entries.put(key, value);
    }

    public boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    public int size() {
        return entries.size();
    }
}
