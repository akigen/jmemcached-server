package com.project.jmemcached.server;

import com.project.jmemcached.server.impl.JMemcachedServerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class CLI {
    private static final Logger LOGGER = LoggerFactory.getLogger(CLI.class);
    private static final List<String> QUIT_CMDS = List.of(new String[]{"q", "quit", "exit"});

    public static void main(String[] args) {
        Thread.currentThread().setName("CLI-main thread");
        try {
            Server server = JMemcachedServerFactory.buildNewServer(null);
            server.start();
            waitForStopCommand(server);
        } catch (Exception e) {
            LOGGER.error("Can't execute cmd: " + e.getMessage(), e);
        }
    }

    private static void waitForStopCommand(Server server) {
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8.name())) {
            while (true) {
                String cmd = scanner.nextLine();
                if (QUIT_CMDS.contains(cmd.toLowerCase())) {
                    server.stop();
                    break;
                } else {
                    LOGGER.error("Undefined command: " + cmd + "! To shutdown server please type: q");
                }
            }
        }
    }
}
