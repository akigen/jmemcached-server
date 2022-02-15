package com.project.jmemcached.server;

import com.project.jmemcached.protocol.model.Request;
import com.project.jmemcached.protocol.model.Response;

public interface CommandHandler {

    Response handle(Request request);
}
