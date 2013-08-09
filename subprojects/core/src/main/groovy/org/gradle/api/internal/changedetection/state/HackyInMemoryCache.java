package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.MapMaker;
import org.gradle.cache.PersistentIndexedCache;
import java.util.concurrent.ConcurrentMap;

public class HackyInMemoryCache<K,V> implements PersistentIndexedCache<K, V> {
    private String cacheName;
    private Class<K> keyType;
    private PersistentIndexedCache delegate;

    private final static ConcurrentMap<String, ConcurrentMap> GLOBAL_CACHE = new MapMaker().makeMap();

    public HackyInMemoryCache(String cacheName, Class<K> keyType, PersistentIndexedCache delegate) {
        this.cacheName = cacheName;
        this.keyType = keyType;
        this.delegate = delegate;
        GLOBAL_CACHE.put(cacheName, new MapMaker().makeMap());
    }

    public V get(K key) {
        ConcurrentMap cache = GLOBAL_CACHE.get(cacheName);
        Object out = cache.get(key(key));
        if (out != null) {
            return (V) out;
        }
        out = delegate.get(key);
        if (out != null) {
            cache.put(key, out);
        }
        return (V) out;
    }

    private Object key(K key) {
        return key;
//        new ByteArrayOutputStream()
//        new DefaultSerializer<K>(keyType.getClassLoader()).write();
    }

    public void put(K key, V value) {
        ConcurrentMap cache = GLOBAL_CACHE.get(cacheName);
        cache.put(key, value);
        delegate.put(key, value);
    }

    public void remove(K key) {
        ConcurrentMap cache = GLOBAL_CACHE.get(cacheName);
        cache.remove(key);
        delegate.remove(key);
    }
}
