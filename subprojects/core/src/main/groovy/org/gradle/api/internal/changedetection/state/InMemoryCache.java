/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.MapMaker;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Serializer;

import java.io.File;
import java.util.concurrent.ConcurrentMap;

public class InMemoryCache {

    private final ConcurrentMap<String, ConcurrentMap> cache = new MapMaker().makeMap();
    private final Object lock = new Object();

    private <K, V> PersistentIndexedCache memCached(String cacheName, PersistentIndexedCache<K, V> target) {
        synchronized (lock) {
            ConcurrentMap data;
            if (this.cache.containsKey(cacheName)) {
                data = this.cache.get(cacheName);
            } else {
                data = new MapMaker().makeMap();
                this.cache.put(cacheName, data);
            }
            return new MapCache(target, data);
        }
    }

    private File expirationMarker;

    public PersistentCache withMemoryCaching(final PersistentCache target, final String masterCacheName) {
        return new PersistentCache() {
            public File getBaseDir() {
                return target.getBaseDir();
            }

            public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Serializer<V> valueSerializer) {
                String cacheName = cacheFile.getName();
                if (cacheName.startsWith(masterCacheName)) {
                    expirationMarker = cacheFile;
                    //TODO if the file is different than the existing one, drop all data
                }
                PersistentIndexedCache<K, V> out = target.createCache(cacheFile, keyType, valueSerializer);
                return memCached(cacheName, out);
            }

            public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
                throw new UnsupportedOperationException("Not supported atm");
            }

            public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
                throw new UnsupportedOperationException("Not supported atm");
            }

            public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
                beforeUnlocked();
                try {
                    return target.longRunningOperation(operationDisplayName, action);
                } finally {
                    beforeLocked();
                }
            }

            public <T> T useCache(final String operationDisplayName, final Factory<? extends T> action) {
                return target.useCache(operationDisplayName, new Factory<T>() {
                    public T create() {
                        beforeLocked();
                        try {
                            return target.useCache(operationDisplayName, action);
                        } finally {
                            beforeUnlocked();
                        }
                    }
                });
            }

            public void useCache(String operationDisplayName, Runnable action) {
                useCache(operationDisplayName, Factories.toFactory(action));
            }

            public void longRunningOperation(String operationDisplayName, Runnable action) {
                longRunningOperation(operationDisplayName, Factories.toFactory(action));
            }
        };
    }

    private void beforeLocked() {

    }

    private void beforeUnlocked() {

    }

    private static class MapCache<K,V> implements PersistentIndexedCache<K, V> {

        private final PersistentIndexedCache delegate;
        private final ConcurrentMap<Object, Value<V>> cache;

        public MapCache(PersistentIndexedCache delegate, ConcurrentMap cache) {
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
}
