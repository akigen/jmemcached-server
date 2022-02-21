package com.project.jmemcached.server.impl;

import com.project.jmemcached.protocol.model.Status;
import com.project.jmemcached.server.ServerConfig;
import com.project.jmemcached.server.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

class DefaultStorage implements Storage {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStorage.class);

    protected final Map<String, StorageItem> map;
    protected final ExecutorService executorService;
    protected final Runnable clearExpiredDataJob;

    DefaultStorage(ServerConfig serverConfig) {
        int clearDataIntervalInMs = serverConfig.getClearDataIntervalInMs();
        this.map = createMap();
        this.executorService = createClearExpiredDataExecutorService();
        this.clearExpiredDataJob = new ClearExpiredDataJob(map, clearDataIntervalInMs);
        this.executorService.submit(clearExpiredDataJob);
    }

    protected Map<String, StorageItem> createMap() {
        return new ConcurrentHashMap<String, StorageItem>();
    }

    protected ExecutorService createClearExpiredDataExecutorService() {
        return Executors.newSingleThreadExecutor(createClearExpiredDataThreadFactory());
    }

    protected ThreadFactory createClearExpiredDataThreadFactory() {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread clearExpiredDataJobThread = new Thread(r, "ClearExpiredDataJobThread");
                clearExpiredDataJobThread.setPriority(Thread.MIN_PRIORITY);
                clearExpiredDataJobThread.setDaemon(true);
                return clearExpiredDataJobThread;
            }
        };
    }

    @Override
    public Status put(String key, Long ttl, byte[] data) {
        StorageItem oldItem = map.put(key, new StorageItem(key, ttl, data));
        return oldItem == null ? Status.ADDED : Status.REPLACED;
    }

    @Override
    public byte[] get(String key) {
        StorageItem item = map.get(key);
        if (item == null || item.isExpired()) {
            return null;
        }
        return item.data;
    }

    @Override
    public Status remove(String key) {
        StorageItem item = map.remove(key);
        return item != null && !item.isExpired() ? Status.REMOVED : Status.NOT_FOUND;
    }

    @Override
    public Status clear() {
        map.clear();
        return Status.CLEARED;
    }

    @Override
    public void close() throws Exception {
        //Do nothing. Daemon threads destroy automatically.
    }

    protected static class StorageItem {
        private final String key;
        private final Long ttl;
        private final byte[] data;

        public StorageItem(String key, Long ttl, byte[] data) {
            this.key = key;
            this.data = data;
            this.ttl = ttl != null ? ttl + System.currentTimeMillis() : null;
        }

        protected boolean isExpired() {
            return ttl != null && ttl < System.currentTimeMillis();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("[").append(key).append("]=");
            if (data == null) {
                sb.append("null");
            } else {
                sb.append(data.length).append(" bytes");
            }
            if (ttl != null) {
                sb.append(" (").append(new Date(this.ttl)).append(')');
            }
            return sb.toString();
        }
    }

    protected static class ClearExpiredDataJob implements Runnable {
        private final Map<String, StorageItem> map;
        private final int clearDataIntervalInMs;

        public ClearExpiredDataJob(Map<String, StorageItem> map, int clearDataIntervalInMs) {
            this.map = map;
            this.clearDataIntervalInMs = clearDataIntervalInMs;
        }

        protected boolean interrupted() {
            return Thread.interrupted();
        }

        protected void sleepClearExpiredDataJob() throws InterruptedException {
            TimeUnit.MILLISECONDS.sleep(clearDataIntervalInMs);
        }

        @Override
        public void run() {
            LOGGER.debug("ClearExpiredDataJobThread started with interval {} ms", clearDataIntervalInMs);
            while (!interrupted()) {
                LOGGER.trace("Invoke clear job");
                for (Map.Entry<String, StorageItem> entry : map.entrySet()) {
                    if (entry.getValue().isExpired()) {
                        StorageItem item = map.remove(entry.getKey());
                        LOGGER.debug("Removed expired storageItem={}", item);
                    }
                }

                try {
                    sleepClearExpiredDataJob();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
