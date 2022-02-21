package com.project.jmemcached.server.impl;

import com.project.jmemcached.server.Server;

import java.util.Properties;

public class JMemcachedServerFactory {

    public static Server buildNewServer(Properties overrideApplicationProperties) {
        return new DefaultServer(new DefaultServerConfig(overrideApplicationProperties));
    }
}
