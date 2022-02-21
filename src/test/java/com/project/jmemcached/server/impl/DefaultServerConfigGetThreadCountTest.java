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
public class DefaultServerConfigGetThreadCountTest extends AbstractDefaultServerConfigTest {
    @DataPoints
    public static String[][] testCases = new String[][]{
            {"0", " should be >= 1"},
            {"qw", " should be a number"}
    };
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private DefaultServerConfig defaultServerConfig;

    private void setUpDefaultServerConfig(String property, String... testData) {
        String value = testData[0];
        String message = testData[1];

        Properties p = new Properties();
        p.setProperty(property, value);
        thrown.expect(JMemcachedConfigException.class);
        thrown.expectMessage(is(property + message));

        defaultServerConfig = createDefaultServerConfigMock(p);
    }

    @Theory
    public void getInitThreadCount(final String... testData) throws Exception {
        setUpDefaultServerConfig("jmemcached.server.init.thread.count", testData);
        defaultServerConfig.getInitThreadCount();
    }

    @Theory
    public void getMaxThreadCount(final String... testData) throws Exception {
        setUpDefaultServerConfig("jmemcached.server.max.thread.count", testData);
        defaultServerConfig.getMaxThreadCount();
    }
}


