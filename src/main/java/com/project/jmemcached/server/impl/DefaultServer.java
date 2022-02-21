package com.project.jmemcached.server.impl;

import com.project.jmemcached.exception.JMemcachedException;
import com.project.jmemcached.server.Server;
import com.project.jmemcached.server.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class DefaultServer implements Server {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultServer.class);
    private final ServerConfig serverConfig;
    private final ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final Thread mainServerThread;
    private volatile boolean serverStopped;

    public DefaultServer(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.serverSocket = createServerSocket();
        this.executorService = createExecutorService();
        this.mainServerThread = createMainServerThread(createServerRunnable());
    }

    protected ServerSocket createServerSocket() {
        try {
            ServerSocket serverSocket = new ServerSocket(serverConfig.getServerPort());
            serverSocket.setReuseAddress(true);
            return serverSocket;
        } catch (IOException e) {
            throw new JMemcachedException("Can't create server socket with port=" + serverConfig.getServerPort(), e);
        }
    }

    protected ExecutorService createExecutorService() {
        ThreadFactory threadFactory = serverConfig.getWorkerThreadFactory();
        int initThreadCount = serverConfig.getInitThreadCount();
        int maxThreadCount = serverConfig.getMaxThreadCount();
        return new ThreadPoolExecutor(initThreadCount, maxThreadCount,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    protected Thread createMainServerThread(Runnable r) {
        Thread th = new Thread(r, "Main Server Thread");
        th.setPriority(Thread.MAX_PRIORITY);
        th.setDaemon(false);
        return th;
    }

    protected Runnable createServerRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                while (!mainServerThread.isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        try {
                            executorService.submit(serverConfig.buildNewClientSocketHandler(clientSocket));
                            LOGGER.info("A new client connection established: " + clientSocket.getRemoteSocketAddress().toString());
                        } catch (RejectedExecutionException e) {
                            LOGGER.error("All worker threads are busy. A new connection rejected: " + e.getMessage());
                            clientSocket.close();
                        }
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            LOGGER.error("Can't accept client socket: " + e.getMessage(), e);
                        }
                        destroyJMemcachedServer();
                        break;
                    }
                }
            }
        };
    }

    protected Thread getShutdownHook() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                if (!serverStopped) {
                    destroyJMemcachedServer();
                }
            }
        }, "ShutdownHook");
    }

    protected void destroyJMemcachedServer() {
        try {
            serverConfig.close();
        } catch (Exception e) {
            LOGGER.error("Close serverConfig failed: " + e.getMessage(), e);
        }
        executorService.shutdownNow();
        LOGGER.info("Server stopped");
        serverStopped = true;
    }

    @Override
    public void start() {
        if (mainServerThread.getState() != Thread.State.NEW) {
            throw new JMemcachedException("Current JMemcached server already started or stopped! Please create a new server instance");
        }
        Runtime.getRuntime().addShutdownHook(getShutdownHook());
        mainServerThread.start();
        LOGGER.info("Server started: " + serverConfig);
    }

    @Override
    public void stop() {
        LOGGER.info("detected stop cmd");
        mainServerThread.interrupt();
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.warn("Error during close server socket: " + e.getMessage(), e);
        }
    }
}
