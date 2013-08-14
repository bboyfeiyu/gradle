package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.PersistentIndexedCache;

import java.io.File;
import java.util.concurrent.ConcurrentMap;

public class HackyInMemoryCache<K,V> implements PersistentIndexedCache<K, V> {

    private final PersistentIndexedCache delegate;
    private final ConcurrentMap<Object, Value<V>> cache;

    public HackyInMemoryCache(PersistentIndexedCache delegate, ConcurrentMap cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    public V get(K key) {
        assert key instanceof String || key instanceof Long || key instanceof File : "Unsupported key type: " + key;
        Value<V> value = cache.get(key);
        if (value != null) {
            return value.value;
        }
        Object out = delegate.get(key);
        cache.put(key, new Value(out));
        return (V) value;
    }

    public void put(K key, V value) {
        cache.put(key, new Value<V>(value));
        delegate.put(key, value);
    }

    public void remove(K key) {
        cache.remove(key);
        delegate.remove(key);
    }

    private static class Value<T> {
        private T value;
        public Value(T value) {
            this.value = value;
        }
    }
}
