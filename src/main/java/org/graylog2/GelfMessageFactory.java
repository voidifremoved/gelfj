package org.graylog2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext.ContextStack;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.graylog2.log.Log4jVersionChecker;

public class GelfMessageFactory {

    private static final int MAX_SHORT_MESSAGE_LENGTH = 250;
    private static final String ORIGIN_HOST_KEY = "originHost";
    private static final String LOGGER_NAME = "logger";
    private static final String LOGGER_NDC = "loggerNdc";
    private static final String THREAD_NAME = "thread";
    private static final String JAVA_TIMESTAMP = "timestampMs";

    @SuppressWarnings("unchecked")
    public static GelfMessage makeMessage(Layout<String> layout, LogEvent event, GelfMessageProvider provider) {
        long timeStamp = Log4jVersionChecker.getTimeStamp(event);
        Level level = event.getLevel();

        String file = null;
        String lineNumber = null;
        if (provider.isIncludeLocation()) {
        	StackTraceElement locationInformation = event.getSource();
        	if (locationInformation != null)
        	{
        		file = locationInformation.getFileName();
            	lineNumber = String.valueOf(locationInformation.getLineNumber());
        	}
        }

        String renderedMessage = layout != null ? layout.toSerializable(event) : event.getMessage().getFormattedMessage();
        String shortMessage;

        if (renderedMessage == null) {
            renderedMessage = "";
        }

        if (provider.isExtractStacktrace()) {
            Throwable throwableInformation = event.getThrown();
            if (throwableInformation != null) {
                renderedMessage += "\n\r" + extractStacktrace(throwableInformation);
            }
        }

        if (renderedMessage.length() > MAX_SHORT_MESSAGE_LENGTH) {
            shortMessage = renderedMessage.substring(0, MAX_SHORT_MESSAGE_LENGTH - 1);
        } else {
            shortMessage = renderedMessage;
        }

        GelfMessage gelfMessage = new GelfMessage(shortMessage, renderedMessage, timeStamp,
                String.valueOf(level.intLevel()), lineNumber, file);

        if (provider.getOriginHost() != null) {
            gelfMessage.setHost(provider.getOriginHost());
        }

        if (provider.getFacility() != null) {
            gelfMessage.setFacility(provider.getFacility());
        }

        Map<String, String> fields = provider.getFields();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getKey().equals(ORIGIN_HOST_KEY) && gelfMessage.getHost() == null) {
                gelfMessage.setHost(fields.get(ORIGIN_HOST_KEY));
            } else {
                gelfMessage.addField(entry.getKey(), entry.getValue());
            }
        }

        if (provider.isAddExtendedInformation()) {

            gelfMessage.addField(THREAD_NAME, event.getThreadName());
            gelfMessage.addField(LOGGER_NAME, event.getLoggerName());
            gelfMessage.addField(JAVA_TIMESTAMP, Long.toString(gelfMessage.getJavaTimestamp()));

            // Get MDC and add a GELF field for each key/value pair
			Map<String, String> mdc = event.getContextMap();

            if (mdc != null) {
                for (Map.Entry<String, String> entry : mdc.entrySet()) {
                    Object value = provider.transformExtendedField(entry.getKey(), entry.getValue());
                    gelfMessage.addField(entry.getKey(), value);
                }
            }

            // Get NDC and add a GELF field
            ContextStack ndc = event.getContextStack();

            if (ndc != null) {
                gelfMessage.addField(LOGGER_NDC, ndc);
            }
        }

        return gelfMessage;
    }

    private static String extractStacktrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
