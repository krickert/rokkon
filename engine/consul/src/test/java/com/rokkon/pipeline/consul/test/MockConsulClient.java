package com.rokkon.pipeline.consul.test;

import io.smallrye.mutiny.Uni;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.KeyValueList;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of ConsulClient for testing.
 * Provides in-memory storage of key-value pairs.
 */
public class MockConsulClient extends ConsulClient {
    private static final Logger LOG = Logger.getLogger(MockConsulClient.class);

    private final ConcurrentHashMap<String, String> kvStore = new ConcurrentHashMap<>();
    private volatile boolean connected = true;

    public MockConsulClient() {
        super(null); // No delegate needed for mock
    }

    @Override
    public Uni<Boolean> putValue(String key, String value) {
        LOG.infof("Mock putValue: key=%s, value=%s", key, value);
        kvStore.put(key, value);
        return Uni.createFrom().item(true);
    }

    @Override
    public Uni<KeyValue> getValue(String key) {
        LOG.infof("Mock getValue: key=%s", key);
        String value = kvStore.get(key);
        if (value == null) {
            return Uni.createFrom().nullItem();
        }

        // Create a mock KeyValue
        KeyValue kv = new KeyValue() {
            @Override
            public String getKey() {
                return key;
            }

            @Override
            public String getValue() {
                return value;
            }

            @Override
            public long getCreateIndex() {
                return 1L;
            }

            @Override
            public long getFlags() {
                return 0L;
            }

            @Override
            public long getModifyIndex() {
                return 1L;
            }

            @Override
            public long getLockIndex() {
                return 0L;
            }

            @Override
            public String getSession() {
                return null;
            }
        };

        return Uni.createFrom().item(kv);
    }

    @Override
    public Uni<Void> deleteValue(String key) {
        LOG.infof("Mock deleteValue: key=%s", key);
        kvStore.remove(key);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<KeyValueList> getValues(String keyPrefix) {
        LOG.infof("Mock getValues: keyPrefix=%s", keyPrefix);
        // For now, return empty list
        KeyValueList list = new KeyValueList();
        return Uni.createFrom().item(list);
    }

    // Helper methods for testing
    public void clear() {
        kvStore.clear();
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isConnected() {
        return connected;
    }

    public Map<String, String> getKvStore() {
        return new HashMap<>(kvStore);
    }
}
