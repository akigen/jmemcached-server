package com.project.jmemcached.server.impl;

import com.project.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClearExpiredDataJobTest {
    private Logger logger;
    private DefaultStorage.ClearExpiredDataJob clearExpiredDataJob;
    private Map<String, DefaultStorage.StorageItem> map;
    private Set<Map.Entry<String, DefaultStorage.StorageItem>> set;
    private Iterator<Map.Entry<String, DefaultStorage.StorageItem>> iterator;
    private int clearDataIntervalInMs = 10000;

    @BeforeEach
    public void before() throws IllegalAccessException {
        logger = mock(Logger.class);
        map = mock(Map.class);
        set = mock(Set.class);
        when(map.entrySet()).thenReturn(set);
        iterator = mock(Iterator.class);
        when(set.iterator()).thenReturn(iterator);
        clearExpiredDataJob = spy(new DefaultStorage.ClearExpiredDataJob(map, clearDataIntervalInMs){
            private boolean stop = true;
            @Override
            protected boolean interrupted() {
                stop = !stop;
                return stop;
            }
            @Override
            protected void sleepClearExpiredDataJob() throws InterruptedException {
                //do nothing
            }
        });
        TestUtils.setLoggerMockViaReflection(DefaultStorage.class, logger);
    }

    private void verifyCommonOperations() throws InterruptedException {
        verify(logger).debug("ClearExpiredDataJobThread started with interval {} ms", clearDataIntervalInMs);
        verify(logger).trace("Invoke clear job");
        verify(clearExpiredDataJob).sleepClearExpiredDataJob();
        verify(clearExpiredDataJob, times(2)).interrupted();
    }

    @Test
    public void verifyWhenMapIsEmpty() throws InterruptedException {
        when(iterator.hasNext()).thenReturn(false);

        clearExpiredDataJob.run();

        verifyCommonOperations();
    }

    @Test
    public void verifyWhenMapEntryIsNotExpired() throws InterruptedException {
        when(iterator.hasNext()).thenReturn(true).thenReturn(false);
        Map.Entry<String, DefaultStorage.StorageItem> entry = mock(Map.Entry.class);
        when(iterator.next()).thenReturn(entry);
        DefaultStorage.StorageItem item = mock(DefaultStorage.StorageItem.class);
        when(entry.getValue()).thenReturn(item);
        when(item.isExpired()).thenReturn(false);

        clearExpiredDataJob.run();

        verifyCommonOperations();
        verify(map, never()).remove(anyString());
        verify(logger, never()).debug("Removed expired storageItem={}", item);
    }

    @Test
    public void verifyWhenMapEntryIsExpired() throws InterruptedException {
        when(iterator.hasNext()).thenReturn(true).thenReturn(false);
        Map.Entry<String, DefaultStorage.StorageItem> entry = mock(Map.Entry.class);
        when(iterator.next()).thenReturn(entry);
        DefaultStorage.StorageItem item = mock(DefaultStorage.StorageItem.class);
        when(entry.getKey()).thenReturn("key");
        when(map.remove("key")).thenReturn(item);
        when(entry.getValue()).thenReturn(item);
        when(item.isExpired()).thenReturn(true);

        clearExpiredDataJob.run();

        verifyCommonOperations();
        verify(map).remove("key");
        verify(logger).debug("Removed expired storageItem={}", item);
    }
    @Test
    public void verifyWhenInterruptedException() throws InterruptedException {
        when(iterator.hasNext()).thenReturn(false);

        clearExpiredDataJob = spy(new DefaultStorage.ClearExpiredDataJob(map, clearDataIntervalInMs){
            @Override
            protected void sleepClearExpiredDataJob() throws InterruptedException {
                throw new InterruptedException("InterruptedException");
            }
        });

        clearExpiredDataJob.run();

        verify(logger).debug("ClearExpiredDataJobThread started with interval {} ms", clearDataIntervalInMs);
        verify(logger).trace("Invoke clear job");
        verify(clearExpiredDataJob).sleepClearExpiredDataJob();
        verify(clearExpiredDataJob, times(1)).interrupted();
    }
}
