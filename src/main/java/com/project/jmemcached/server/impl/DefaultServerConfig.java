package com.project.jmemcached.server.impl;

import com.project.jmemcached.exception.JMemcachedConfigException;
import com.project.jmemcached.protocol.RequestConverter;
import com.project.jmemcached.protocol.ResponseConverter;
import com.project.jmemcached.protocol.impl.DefaultRequestConverter;
import com.project.jmemcached.protocol.impl.DefaultResponseConverter;
import com.project.jmemcached.server.ClientSocketHandler;
import com.project.jmemcached.server.CommandHandler;
import com.project.jmemcached.server.ServerConfig;
import com.project.jmemcached.server.Storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;

class DefaultServerConfig implements ServerConfig {
    private final Properties applicationProperties = new Properties();
    private final RequestConverter requestConverter;
    private final ResponseConverter responseConverter;
    private final Storage storage;
    private final CommandHandler commandHandler;

    DefaultServerConfig(Properties overrideApplicationProperties) {
        loadApplicationProperties("server.properties");
        if (overrideApplicationProperties != null) {
            applicationProperties.putAll(overrideApplicationProperties);
        }
        requestConverter = createRequestConverter();
        responseConverter = createResponseConverter();
        storage = createStorage();
        commandHandler = createCommandHandler();
    }

    protected RequestConverter createRequestConverter() {
        return new DefaultRequestConverter();
    }

    protected ResponseConverter createResponseConverter() {
        return new DefaultResponseConverter();
    }

    protected Storage createStorage() {
        return new DefaultStorage(this);
    }

    protected CommandHandler createCommandHandler() {
        return new DefaultCommandHandler(storage);
    }

    protected InputStream getClassParhResourceInputStream(String classPathResource) {
        return getClass().getClassLoader().getResourceAsStream(classPathResource);
    }

    protected void loadApplicationProperties(String classPathResource) {
        try (InputStream in = getClassParhResourceInputStream(classPathResource)) {
            if (in == null) {
                throw new JMemcachedConfigException("Classpath resource not found: " + classPathResource);
            } else {
                applicationProperties.load(in);
            }
        } catch (IOException e) {
            throw new JMemcachedConfigException("Can't load application properties from classpath:" + classPathResource, e);
        }
    }

    @Override
    public RequestConverter getRequestConverter() {
        return requestConverter;
    }

    @Override
    public ResponseConverter getResponseConverter() {
        return responseConverter;
    }

    @Override
    public Storage getStorage() {
        return storage;
    }

    @Override
    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    @Override
    public ThreadFactory getWorkerThreadFactory() {
        return new ThreadFactory() {
            private int threadCount = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread th = new Thread(r, "Worker-" + threadCount);
                threadCount++;
                th.setDaemon(true);
                return th;
            }
        };
    }

    @Override
    public int getClearDataIntervalInMs() {
        String value = applicationProperties.getProperty("jmemcached.storage.clear.data.interval.ms");
        try {
            int clearDataIntervalInMs = Integer.parseInt(value);
            if (clearDataIntervalInMs < 1000) {
                throw new JMemcachedConfigException("jmemcached.storage.clear.data.interval.ms should be >= 1000 ms");
            }
            return clearDataIntervalInMs;
        } catch (NumberFormatException e) {
            throw new JMemcachedConfigException("jmemcached.storage.clear.data.interval.ms should be a number", e);
        }
    }

    @Override
    public int getServerPort() {
        String value = applicationProperties.getProperty("jmemcached.server.port");
        try {
            int port = Integer.parseInt(value);
            if (port < 0 || port > 65535) {
                throw new JMemcachedConfigException("jmemcached.server.port should be between 0 and 65535");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new JMemcachedConfigException("jmemcached.server.port should be a number", e);
        }
    }

    protected int getThreadCount(String propertyName) {
        String value = applicationProperties.getProperty(propertyName);
        try {
            int threadCount = Integer.parseInt(value);
            if (threadCount < 1) {
                throw new JMemcachedConfigException(propertyName + " should be >= 1");
            }
            return threadCount;
        } catch (NumberFormatException e) {
            throw new JMemcachedConfigException(propertyName + " should be a number", e);
        }
    }

    @Override
    public int getInitThreadCount() {
        return getThreadCount("jmemcached.server.init.thread.count");
    }

    @Override
    public int getMaxThreadCount() {
        return getThreadCount("jmemcached.server.max.thread.count");
    }

    @Override
    public ClientSocketHandler buildNewClientSocketHandler(Socket clientSocket) {
        return new DefaultClientSocketHandler(clientSocket, this);
    }

    @Override
    public void close() throws Exception {
        storage.close();
    }

    @Override
    public String toString() {
        return String.format("DefaultServerConfig: port=%s, initThreadCount=%s, maxThreadCount=%s, clearDataIntervalInMs=%sms",
                getServerPort(), getInitThreadCount(), getMaxThreadCount(), getClearDataIntervalInMs());
    }
}
