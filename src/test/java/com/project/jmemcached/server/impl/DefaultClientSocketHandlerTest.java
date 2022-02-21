package com.project.jmemcached.server.impl;

import com.project.TestUtils;
import com.project.jmemcached.protocol.RequestConverter;
import com.project.jmemcached.protocol.ResponseConverter;
import com.project.jmemcached.protocol.model.Request;
import com.project.jmemcached.protocol.model.Response;
import com.project.jmemcached.server.CommandHandler;
import com.project.jmemcached.server.ServerConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(Theories.class)
public class DefaultClientSocketHandlerTest {
    private Logger logger;
    private Socket socket;
    private ServerConfig serverConfig;
    private RequestConverter requestConverter;
    private ResponseConverter responseConverter;
    private CommandHandler commandHandler;
    private InputStream inputStream;
    private OutputStream outputStream;
    private DefaultClientSocketHandler defaultClientSocketHandler;
    private Request request;
    private Response response;

    @Before
    public void before() throws IOException, IllegalAccessException {
        logger = mock(Logger.class);
        socket = mock(Socket.class);
        SocketAddress socketAddress = mock(SocketAddress.class);
        when(socketAddress.toString()).thenReturn("localhost");
        when(socket.getRemoteSocketAddress()).thenReturn(socketAddress);
        serverConfig = mock(ServerConfig.class);
        requestConverter = mock(RequestConverter.class);
        when(serverConfig.getRequestConverter()).thenReturn(requestConverter);
        responseConverter = mock(ResponseConverter.class);
        when(serverConfig.getResponseConverter()).thenReturn(responseConverter);
        commandHandler = mock(CommandHandler.class);
        when(serverConfig.getCommandHandler()).thenReturn(commandHandler);
        inputStream = mock(InputStream.class);
        when(socket.getInputStream()).thenReturn(inputStream);
        outputStream = mock(OutputStream.class);
        when(socket.getOutputStream()).thenReturn(outputStream);
        request = mock(Request.class);
        response = mock(Response.class);
        TestUtils.setLoggerMockViaReflection(DefaultClientSocketHandler.class, logger);
        defaultClientSocketHandler = spy(new DefaultClientSocketHandler(socket, serverConfig) {
            private boolean stop = true;

            @Override
            protected boolean interrupted() {
                stop = !stop;
                // interrupted should return false and then true
                return stop;
            }
        });
    }

    private void verifyCommonRequiredOperations() throws IOException {
        verify(serverConfig).getRequestConverter();
        verify(serverConfig).getResponseConverter();
        verify(serverConfig).getCommandHandler();
        verify(socket).getInputStream();
        verify(socket).getOutputStream();

        verify(socket).close();
    }

    @Test
    public void successRun() throws IOException {
        when(requestConverter.readRequest(inputStream)).thenReturn(request);
        when(commandHandler.handle(request)).thenReturn(response);

        defaultClientSocketHandler.run();
        verifyCommonRequiredOperations();
        verify(requestConverter).readRequest(inputStream);
        verify(commandHandler).handle(request);
        verify(responseConverter).writeResponse(outputStream, response);

        verify(defaultClientSocketHandler, times(2)).interrupted();
        verify(logger).debug("Command {} -> {}", request, response);
    }

    @DataPoints
    public static Object[][] testDataForRunWithExceptionsMethod = new Object[][]{
            {new RuntimeException("Test"), 2},
            {new EOFException("Test"), 1},
            {new SocketException("Test"), 1},
            {new IOException("Test"), 1}
    };

    @Theory
    public void runWithExceptions(final Object... testData) throws Exception {
        Exception exception = (Exception) testData[0];
        int interruptedMethodCallCount = (int) testData[1];
        when(requestConverter.readRequest(inputStream)).thenThrow(exception);

        defaultClientSocketHandler.run();
        verifyCommonRequiredOperations();
        verify(requestConverter).readRequest(inputStream);
        verify(commandHandler, never()).handle(request);
        verify(responseConverter, never()).writeResponse(outputStream, response);

        verify(defaultClientSocketHandler, times(interruptedMethodCallCount)).interrupted();
    }

    @Test
    public void runtimeExceptionLoggerMessage() throws IOException {
        RuntimeException ex = new RuntimeException("RuntimeException");
        when(requestConverter.readRequest(inputStream)).thenThrow(ex);

        defaultClientSocketHandler.run();
        verify(logger).error("Handle request failed: RuntimeException", ex);
    }

    @Test
    public void eofExceptionLoggerMessage() throws IOException {
        when(requestConverter.readRequest(inputStream)).thenThrow(new EOFException("EOFException"));

        defaultClientSocketHandler.run();
        verify(logger).info("Remote client connection closed: localhost: EOFException");
    }

    @Test
    public void socketExceptionLoggerMessage() throws IOException {
        when(requestConverter.readRequest(inputStream)).thenThrow(new SocketException("SocketException"));

        defaultClientSocketHandler.run();
        verify(logger).info("Remote client connection closed: localhost: SocketException");
    }

    @Test
    public void ioExceptionLoggerMessage() throws IOException {
        when(socket.isClosed()).thenReturn(false);
        IOException ex = new IOException("IOException");
        when(requestConverter.readRequest(inputStream)).thenThrow(ex);

        defaultClientSocketHandler.run();
        verify(logger).error("IO Error: IOException", ex);
    }

    @Test
    public void ioExceptionWithoutLoggerMessage() throws IOException {
        when(socket.isClosed()).thenReturn(true);
        IOException ex = new IOException("IOException");
        when(requestConverter.readRequest(inputStream)).thenThrow(ex);

        defaultClientSocketHandler.run();
        verify(logger, never()).error("IO Error: IOException", ex);
    }

    @Test
    public void socketCloseErrorLoggerMessage() throws IOException {
        IOException ex = new IOException("IOException");
        doThrow(ex).when(socket).close();
        defaultClientSocketHandler.run();
        verify(logger).error("Close socket failed: IOException", ex);
    }

    @Test
    public void interrupted() {
        defaultClientSocketHandler = new DefaultClientSocketHandler(socket, serverConfig);
        assertFalse(defaultClientSocketHandler.interrupted());
        Thread.currentThread().interrupt();
        assertTrue(defaultClientSocketHandler.interrupted());
    }
}