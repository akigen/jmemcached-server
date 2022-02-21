package com.project.jmemcached.server.impl;

import com.project.jmemcached.exception.JMemcachedConfigException;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.Properties;

import static org.hamcrest.core.Is.is;

@RunWith(Theories.class)
public class DefaultServerConfigGetServerPortTest extends AbstractDefaultServerConfigTest {
    @DataPoints
    public static String[][] testCases = new String[][]{
            {"-1", "jmemcached.server.port should be between 0 and 65535"},
            {"65536", "jmemcached.server.port should be between 0 and 65535"},
            {"qw", "jmemcached.server.port should be a number"}
    };
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private DefaultServerConfig defaultServerConfig;

    @Theory
    public void getServerPort(final String... testData) throws Exception {
        String value = testData[0];
        String message = testData[1];

        Properties p = new Properties();
        p.setProperty("jmemcached.server.port", value);
        thrown.expect(JMemcachedConfigException.class);
        thrown.expectMessage(is(message));

        defaultServerConfig = createDefaultServerConfigMock(p);
        defaultServerConfig.getServerPort();
    }
}


