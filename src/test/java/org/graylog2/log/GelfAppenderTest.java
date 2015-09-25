package org.graylog2.log;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Locale.Category;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager.Log4jMarker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.graylog2.GelfMessage;
import org.graylog2.GelfSender;
import org.graylog2.GelfSenderResult;
import org.graylog2.GelfTCPSender;
import org.graylog2.GelfUDPSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Anton Yakimov
 * @author Jochen Schalanda
 */
public class GelfAppenderTest {

    private static final String CLASS_NAME = GelfAppenderTest.class.getCanonicalName();
    private TestGelfSender gelfSender;
    private GelfAppender gelfAppender;
    private boolean rawExtended = false;

    @Before
    public void setUp() throws IOException {
        gelfSender = new TestGelfSender("localhost");

        gelfAppender = new GelfAppender("Test", null, null) {

            @Override
            public GelfSender getGelfSender() {
                return gelfSender;
            }
            
            @Override
            public Object transformExtendedField(String field, Object object) {
                if (rawExtended) {
                    return object;
                }
                else {
                    return super.transformExtendedField(field, object);
                }
            }
        };
    }

    @After
    public void tearDown() {
        if (gelfAppender.isAddExtendedInformation()) {
            //NDC.clear();
        }
    }

    @Test
    public void ensureHostnameForMessage() {

        LogEvent event = new Log4jLogEvent(CLASS_NAME, null, GelfAppenderTest.class.getName(), Level.INFO, new SimpleMessage("Das Auto"),
                new RuntimeException("Volkswagen"));
        gelfAppender.append(event);

        assertThat("Message hostname", gelfSender.getLastMessage().getHost(), notNullValue());

        gelfAppender.setOriginHost("example.com");
        gelfAppender.append(event);
        assertThat(gelfSender.getLastMessage().getHost(), is("example.com"));
    }

    @Test
    public void handleNullInAppend() {

        LoggingEvent event = new LoggingEvent(CLASS_NAME, Category.getInstance(this.getClass()), 123L, Level.INFO, null, new RuntimeException("LOL"));
        gelfAppender.append(event);

        assertThat("Message short message", gelfSender.getLastMessage().getShortMessage(), notNullValue());
        assertThat("Message full message", gelfSender.getLastMessage().getFullMessage(), notNullValue());
    }

    @Test
    public void handleMDC() {

        gelfAppender.setAddExtendedInformation(true);

        LoggingEvent event = new LoggingEvent(CLASS_NAME, Category.getInstance(this.getClass()), 123L, Level.INFO, "", new RuntimeException("LOL"));
        MDC.put("foo", "bar");

        gelfAppender.append(event);

        assertEquals("bar", gelfSender.getLastMessage().getAdditonalFields().get("foo"));
        assertNull(gelfSender.getLastMessage().getAdditonalFields().get("non-existent"));
    }
    
    @Test
    public void handleMDCTransform() {

        gelfAppender.setAddExtendedInformation(true);
        

        LoggingEvent event = new LoggingEvent(CLASS_NAME, Category.getInstance(this.getClass()), 123L, Level.INFO, "", new RuntimeException("LOL"));
        MDC.put("foo", 200);

        gelfAppender.append(event);

        assertEquals("200", gelfSender.getLastMessage().getAdditonalFields().get("foo"));
        assertNull(gelfSender.getLastMessage().getAdditonalFields().get("non-existent"));
        
        
        rawExtended = true;
        event = new LoggingEvent(CLASS_NAME, Category.getInstance(this.getClass()), 123L, Level.INFO, "", new RuntimeException("LOL"));
        gelfAppender.append(event);

        assertEquals(new Integer(200), gelfSender.getLastMessage().getAdditonalFields().get("foo"));
        assertNull(gelfSender.getLastMessage().getAdditonalFields().get("non-existent"));
        
    }

    @Test
    public void handleNDC() {

        gelfAppender.setAddExtendedInformation(true);

        LoggingEvent event = new LoggingEvent(CLASS_NAME, Category.getInstance(this.getClass()), 123L, Level.INFO, "", new RuntimeException("LOL"));
        NDC.push("Foobar");

        gelfAppender.append(event);

        assertEquals("Foobar", gelfSender.getLastMessage().getAdditonalFields().get("loggerNdc"));
    }

    @Test
    public void disableExtendedInformation() {

        gelfAppender.setAddExtendedInformation(false);

        LoggingEvent event = new LoggingEvent(CLASS_NAME, Category.getInstance(this.getClass()), 123L, Level.INFO, "", new RuntimeException("LOL"));

        MDC.put("foo", "bar");
        NDC.push("Foobar");

        gelfAppender.append(event);

        assertNull(gelfSender.getLastMessage().getAdditonalFields().get("loggerNdc"));
        assertNull(gelfSender.getLastMessage().getAdditonalFields().get("foo"));
    }

    @Test
    public void checkExtendedInformation() throws UnknownHostException, SocketException {

        gelfAppender.setAddExtendedInformation(true);

        LoggingEvent event = new LoggingEvent(CLASS_NAME, Category.getInstance(GelfAppenderTest.class), 123L, Level.INFO, "Das Auto", new RuntimeException("LOL"));

        gelfAppender.append(event);

        assertEquals(gelfSender.getLastMessage().getAdditonalFields().get("logger"), CLASS_NAME);
    }

    @Test
    public void testTcpUdpUrls() {

        GelfAppender testGelfAppender = new GelfAppender() {

            @Override
            protected GelfUDPSender getGelfUDPSender(String udpGraylogHost, int port) throws IOException {
                return new MockGelfUDPSender(udpGraylogHost, port);
            }

            @Override
            protected GelfTCPSender getGelfTCPSender(String tcpGraylogHost, int port) throws IOException {
                return new MockGelfTCPSender(tcpGraylogHost, port);
            }

        };

        TestingEH testingEH = new TestingEH();
        testGelfAppender.setErrorHandler(testingEH);

        testGelfAppender.setGraylogHost("tcp:www.github.com");
        testGelfAppender.activateOptions();

        assertThat("No errors when using tcp: url", testingEH.getErrorMessage(), is(not("Unknown Graylog2 hostname:tcp:www.github.com")));

        testGelfAppender.setGraylogHost("udp:www.github.com");
        testGelfAppender.activateOptions();

        assertThat("No errors when using udp: url", testingEH.getErrorMessage(), is(not("Unknown Graylog2 hostname:udp:www.github.com")));

        testGelfAppender.setGraylogHost("www.github.com");
        testGelfAppender.activateOptions();

        assertThat("No errors when using udp: url", testingEH.getErrorMessage(), is(not("Unknown Graylog2 hostname:www.github.com")));
    }

    private class TestGelfSender extends GelfUDPSender {

        private GelfMessage lastMessage;

        public TestGelfSender(String host) throws IOException {
            super(host);
        }

        @Override
        public GelfSenderResult sendMessage(GelfMessage message) {
            this.lastMessage = message;
            return GelfSenderResult.OK;
        }

        public GelfMessage getLastMessage() {
            return lastMessage;
        }
    }

    private class TestingEH implements ErrorHandler {

        private String errorMessage = "";

        public void setLogger(Logger logger) {
        }

        public void error(String s, Exception e, int i) {
            errorMessage = s;
        }

        public void error(String s) {
            errorMessage = s;
        }

        public void error(String s, Exception e, int i, LoggingEvent loggingEvent) {
            errorMessage = s;
        }

        public void setAppender(Appender appender) {
        }

        public void setBackupAppender(Appender appender) {
        }

        public void activateOptions() {
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private class MockGelfUDPSender extends GelfUDPSender {

        private MockGelfUDPSender(String host, int port) throws IOException {
            if (host.contains("udp:")) {
                throw new UnknownHostException("udp: found in host");
            }
        }

    }

    private class MockGelfTCPSender extends GelfTCPSender {

        private MockGelfTCPSender(String host, int port) throws IOException {
            if (host.contains("tcp:")) {
                throw new UnknownHostException("tcp: found in host");
            }
        }
    }
}
