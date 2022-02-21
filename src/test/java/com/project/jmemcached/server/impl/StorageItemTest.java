package com.project.jmemcached.server.impl;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class StorageItemTest {

    private DefaultStorage.StorageItem storageItem;

    @Test
    public void isNotExpiredTtlNull() {
        storageItem = new DefaultStorage.StorageItem("key", null, new byte[]{1, 2, 3});
        assertFalse(storageItem.isExpired());
    }

    @Test
    public void isNotExpiredTtlNotNull() {
        storageItem = new DefaultStorage.StorageItem("key", TimeUnit.SECONDS.toMillis(5), new byte[]{1, 2, 3});
        assertFalse(storageItem.isExpired());
    }

    @Test
    public void isExpiredTtlNotNull() throws InterruptedException {
        storageItem = new DefaultStorage.StorageItem("key", TimeUnit.MILLISECONDS.toMillis(5), new byte[]{1, 2, 3});
        TimeUnit.MILLISECONDS.sleep(10);
        assertTrue(storageItem.isExpired());
    }

    @Test
    public void toStringWithData() {
        storageItem = new DefaultStorage.StorageItem("key", null, new byte[]{1, 2, 3});
        assertEquals("[key]=3 bytes", storageItem.toString());
    }

    @Test
    public void toStringWithoutData() {
        storageItem = new DefaultStorage.StorageItem("key", null, null);
        assertEquals("[key]=null", storageItem.toString());
    }

    @Test
    public void toStringWithTTL() {
        storageItem = new DefaultStorage.StorageItem("key", -System.currentTimeMillis(), null);
        assertEquals("[key]=null (Thu Jan 01 03:00:00 MSK 1970)", storageItem.toString());
    }
}
