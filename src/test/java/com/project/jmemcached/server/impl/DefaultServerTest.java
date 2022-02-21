package com.project.jmemcached.server.impl;

import com.project.TestUtils;
import com.project.jmemcached.exception.JMemcachedException;
import com.project.jmemcached.server.ClientSocketHandler;
import com.project.jmemcached.server.ServerConfig;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DefaultServerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private DefaultServer server;
    private Logger logger;
    private ServerConfig serverConfig;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private Thread mainServerThread;

    @Before
    public void before() throws IllegalAccessException {
        logger = mock(Logger.class);
        serverConfig = mock(ServerConfig.class);
        when(serverConfig.toString()).thenReturn("serverConfig");
        serverSocket = mock(ServerSocket.class);
        executorService = mock(ExecutorService.class);
        mainServerThread = mock(Thread.class);

        TestUtils.setLoggerMockViaReflection(DefaultServer.class, logger);
    }

    @Test
    public void createMainServerThread() {
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };
        Thread thread = server.createMainServerThread(mock(Runnable.class));
        assertEquals(Thread.MAX_PRIORITY, thread.getPriority());
        assertEquals("Main Server Thread", thread.getName());
        assertFalse(thread.isDaemon());
        assertFalse(thread.isAlive());
    }

    @Test
    public void startSuccess() {
        when(mainServerThread.getState()).thenReturn(Thread.State.NEW);
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }

            @Override
            protected Thread getShutdownHook() {
                return new Thread();
            }
        };
        server.start();

        verify(mainServerThread).getState();
        verify(mainServerThread).start();
        verify(logger).info("Server started: serverConfig");
    }

    @Test
    public void startFailed() {
        thrown.expect(JMemcachedException.class);
        thrown.expectMessage(is("Current JMemcached server already started or stopped! Please create a new server instance"));

        when(mainServerThread.getState()).thenReturn(Thread.State.TERMINATED);
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };
        server.start();
    }

    @Test
    public void stopSuccess() throws IOException {
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };
        server.stop();

        verify(mainServerThread).interrupt();
        verify(serverSocket).close();
        verify(logger).info("detected stop cmd");
        verify(logger, never()).warn(anyString(), any(Throwable.class));
    }

    @Test
    public void stopWithIOException() throws IOException {
        IOException ex = new IOException("Close");
        doThrow(ex).when(serverSocket).close();
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };
        server.stop();

        verify(mainServerThread).interrupt();
        verify(serverSocket).close();
        verify(logger).info("detected stop cmd");
        verify(logger).warn("Error during close server socket: Close", ex);
    }

    @Test
    public void destroyServerWithException() throws Exception {
        doThrow(new Exception("Close")).when(serverConfig).close();
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };
        server.destroyJMemcachedServer();

        verify(serverConfig).close();
        verify(executorService).shutdownNow();
    }

    @Test
    public void shutdownHookWithSuccessDestroyServer() throws Exception {
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };
        server.getShutdownHook().run();

        verify(serverConfig).close();
        verify(executorService).shutdownNow();
        verify(logger).info("Server stopped");
        verify(logger, never()).error(anyString(), any(Throwable.class));
    }

    @Test
    public void shutdownHookWithoutDestroyServer() throws Exception {
        server = new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };
        //set private field serverStopped via reflection
        FieldUtils.getDeclaredField(DefaultServer.class, "serverStopped", true).set(server, Boolean.TRUE);
        server.getShutdownHook().run();

        verify(serverConfig, never()).close();
        verify(executorService, never()).shutdownNow();
        verify(logger, never()).info("Server stopped");
    }

    @Test
    public void createExecutorService() throws IllegalAccessException {
        when(serverConfig.getInitThreadCount()).thenReturn(1);
        when(serverConfig.getMaxThreadCount()).thenReturn(10);
        ThreadFactory threadFactory = mock(ThreadFactory.class);
        when(serverConfig.getWorkerThreadFactory()).thenReturn(threadFactory);
        server = new DefaultServer(serverConfig) {
            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        };

        //get private field executorService via reflection
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)
                FieldUtils.getDeclaredField(DefaultServer.class, "executorService", true).get(server);

        verify(serverConfig).getInitThreadCount();
        verify(serverConfig).getMaxThreadCount();
        verify(serverConfig).getWorkerThreadFactory();

        assertEquals(1, threadPoolExecutor.getCorePoolSize());
        assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        assertEquals(60, threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS));
        assertSame(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(SynchronousQueue.class, threadPoolExecutor.getQueue().getClass());
        assertEquals(ThreadPoolExecutor.AbortPolicy.class, threadPoolExecutor.getRejectedExecutionHandler().getClass());
    }

    @Test
    public void createServerRunnableSuccessRun() throws IOException {
        server = spy(new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        });
        when(mainServerThread.isInterrupted()).thenReturn(false).thenReturn(true);
        Socket clientSocket = mock(Socket.class);
        SocketAddress socketAddress = mock(SocketAddress.class);
        when(clientSocket.getRemoteSocketAddress()).thenReturn(socketAddress);
        when(socketAddress.toString()).thenReturn("localhost");
        when(serverSocket.accept()).thenReturn(clientSocket);
        ClientSocketHandler clientSocketHandler = mock(ClientSocketHandler.class);
        when(serverConfig.buildNewClientSocketHandler(clientSocket)).thenReturn(clientSocketHandler);

        server.createServerRunnable().run();

        verify(mainServerThread, times(2)).isInterrupted();
        verify(serverSocket).accept();
        verify(serverConfig).buildNewClientSocketHandler(clientSocket);
        verify(logger).info("A new client connection established: localhost");

        verify(logger, never()).error(anyString(), any(Throwable.class));
        verify(clientSocket, never()).close();
        verify(server, never()).destroyJMemcachedServer();
    }

    @Test
    public void createServerRunnableRunWithRejectedExecutionException() throws IOException {
        server = spy(new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        });
        when(mainServerThread.isInterrupted()).thenReturn(false).thenReturn(true);
        Socket clientSocket = mock(Socket.class);
        when(serverSocket.accept()).thenReturn(clientSocket);
        ClientSocketHandler clientSocketHandler = mock(ClientSocketHandler.class);
        when(serverConfig.buildNewClientSocketHandler(clientSocket)).thenReturn(clientSocketHandler);
        when(executorService.submit(clientSocketHandler)).thenThrow(new RejectedExecutionException("RejectedExecutionException"));

        server.createServerRunnable().run();

        verify(mainServerThread, times(2)).isInterrupted();
        verify(serverSocket).accept();
        verify(serverConfig).buildNewClientSocketHandler(clientSocket);
        verify(clientSocket).close();
        verify(logger).error("All worker threads are busy. A new connection rejected: RejectedExecutionException");

        verify(logger, never()).info("A new client connection established: localhost");
        verify(server, never()).destroyJMemcachedServer();
    }

    @Test
    public void createServerRunnableRunWithIOExceptionAndServerSocketIsClosed() throws IOException {
        server = spy(new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        });
        when(mainServerThread.isInterrupted()).thenReturn(false);
        when(serverSocket.accept()).thenThrow(new IOException("IOException"));
        when(serverSocket.isClosed()).thenReturn(true);

        server.createServerRunnable().run();

        verify(mainServerThread, times(1)).isInterrupted();
        verify(serverSocket).accept();
        verify(server).destroyJMemcachedServer();
        verify(serverConfig, never()).buildNewClientSocketHandler(any(Socket.class));
        verify(logger, never()).error(anyString(), any(Throwable.class));
    }

    @Test
    public void createServerRunnableRunWithIOExceptionAndServerSocketIsNotClosed() throws IOException {
        server = spy(new DefaultServer(serverConfig) {
            @Override
            protected ExecutorService createExecutorService() {
                return executorService;
            }

            @Override
            protected Thread createMainServerThread(Runnable r) {
                return mainServerThread;
            }

            @Override
            protected ServerSocket createServerSocket() {
                return serverSocket;
            }
        });
        when(mainServerThread.isInterrupted()).thenReturn(false);
        IOException ex = new IOException("IOException");
        when(serverSocket.accept()).thenThrow(ex);
        when(serverSocket.isClosed()).thenReturn(false);

        server.createServerRunnable().run();

        verify(mainServerThread, times(1)).isInterrupted();
        verify(serverSocket).accept();
        verify(server).destroyJMemcachedServer();
        verify(logger).error("Can't accept client socket: IOException", ex);

        verify(serverConfig, never()).buildNewClientSocketHandler(any(Socket.class));
    }
}