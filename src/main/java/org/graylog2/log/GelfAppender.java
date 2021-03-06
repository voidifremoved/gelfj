package org.graylog2.log;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.SocketAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.core.net.AbstractSocketManager;
import org.apache.logging.log4j.core.net.Protocol;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.util.Booleans;
import org.apache.logging.log4j.util.EnglishEnums;
import org.graylog2.GelfAMQPSender;
import org.graylog2.GelfMessage;
import org.graylog2.GelfMessageFactory;
import org.graylog2.GelfMessageProvider;
import org.graylog2.GelfSender;
import org.graylog2.GelfSenderResult;
import org.graylog2.GelfTCPSender;
import org.graylog2.GelfUDPSender;
import org.json.simple.JSONValue;

/**
 *
 * @author Anton Yakimov
 * @author Jochen Schalanda
 */
@Plugin(name="GelfAppender", category="Core", elementType="appender", printObject=true)
public class GelfAppender extends AbstractAppender implements GelfMessageProvider {

	private static String originHost;
	
    private String graylogHost; //
    private String amqpURI; //
    private String amqpExchangeName; //
    private String amqpRoutingKey; //
    private int amqpMaxRetries = 0; //
    private int graylogPort = 12201;
    private String facility;
    private GelfSender gelfSender;
    private boolean extractStacktrace;
    private boolean addExtendedInformation;
    private boolean includeLocation = true;
    private Map<String, String> fields;

    private GelfAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions)
	{
		super(name, filter, layout, ignoreExceptions);
	}

	private GelfAppender(String name, Filter filter, Layout<? extends Serializable> layout)
	{
		super(name, filter, layout);
	}

    @PluginFactory
    public static GelfAppender createAppender(
            // @formatter:off
    		@PluginAttribute("name") final String name,
            @PluginAttribute("graylogHost") final String graylogHost,
            @PluginAttribute("originHost") final String originHost,
            @PluginAttribute(value="graylogPort", defaultInt=0) final int graylogPort,
            
            @PluginAttribute("amqpURI") final String amqpURI,
            @PluginAttribute("amqpExchangeName") final String amqpExchangeName,
            @PluginAttribute("amqpRoutingKey") final String amqpRoutingKey,
            
  
            @PluginAttribute(value = "amqpMaxRetries", defaultInt = 0) final int amqpMaxRetries,
            
            @PluginAttribute("facility") final String facility,
            @PluginAttribute("additionalFields") final String additionalFields,
            
            @PluginAttribute(value="extractStacktrace", defaultBoolean=false) final boolean extractStacktrace,
            @PluginAttribute(value="includeLocation", defaultBoolean=true) final boolean includeLocation,
            @PluginAttribute(value="addExtendedInformation", defaultBoolean=false) final boolean addExtendedInformation,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") final Filter filter, 
            @PluginConfiguration final Configuration config) {
            // @formatter:on
    	

        if (layout == null) {
            layout = SerializedLayout.createLayout();
        }

        if (name == null) {
            LOGGER.error("No name provided for GelfAppender");
            return null;
        }

        GelfAppender appender = new GelfAppender(name, filter, layout);
        appender.setGraylogHost(graylogHost);
        appender.setOriginHost(originHost != null && !originHost.isEmpty() ? originHost : null);
        appender.setGraylogPort(graylogPort);
        appender.setAmqpURI(amqpURI);
        appender.setAmqpExchangeName(amqpExchangeName);
        appender.setAmqpRoutingKey(amqpRoutingKey);
        appender.setAmqpMaxRetries(amqpMaxRetries);
        appender.setFacility(facility);
        appender.setAdditionalFields(additionalFields);
        appender.setExtractStacktrace(extractStacktrace);
        appender.setIncludeLocation(includeLocation);
        appender.setAddExtendedInformation(addExtendedInformation);
        
        return appender;
    }
	
	
	
	@SuppressWarnings("unchecked")
    public void setAdditionalFields(String additionalFields) {
        fields = (Map<String, String>) JSONValue.parse(additionalFields.replaceAll("'", "\""));
    }

    public int getGraylogPort() {
        return graylogPort;
    }

    public void setGraylogPort(int graylogPort) {
        this.graylogPort = graylogPort;
    }

    public String getGraylogHost() {
        return graylogHost;
    }

    public void setGraylogHost(String graylogHost) {
        this.graylogHost = graylogHost;
    }

    public String getAmqpURI() {
        return amqpURI;
    }

    public void setAmqpURI(String amqpURI) {
        this.amqpURI = amqpURI;
    }

    public String getAmqpExchangeName() {
        return amqpExchangeName;
    }

    public void setAmqpExchangeName(String amqpExchangeName) {
        this.amqpExchangeName = amqpExchangeName;
    }

    public String getAmqpRoutingKey() {
        return amqpRoutingKey;
    }

    public void setAmqpRoutingKey(String amqpRoutingKey) {
        this.amqpRoutingKey = amqpRoutingKey;
    }

    public int getAmqpMaxRetries() {
        return amqpMaxRetries;
    }

    public void setAmqpMaxRetries(int amqpMaxRetries) {
        this.amqpMaxRetries = amqpMaxRetries;
    }

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    public boolean isExtractStacktrace() {
        return extractStacktrace;
    }

    public void setExtractStacktrace(boolean extractStacktrace) {
        this.extractStacktrace = extractStacktrace;
    }

    public String getOriginHost() {
        if (originHost == null) {
            originHost = getLocalHostName();
        }
        return originHost;
    }

    private String getLocalHostName() {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            super.getHandler().error("Unknown local hostname", e);
        }

        return hostName;
    }

    public void setOriginHost(String originHost) {
        GelfAppender.originHost = originHost;
    }

    public boolean isAddExtendedInformation() {
        return addExtendedInformation;
    }

    public void setAddExtendedInformation(boolean addExtendedInformation) {
        this.addExtendedInformation = addExtendedInformation;
    }

    public boolean isIncludeLocation() {
        return this.includeLocation;
    }

    public void setIncludeLocation(boolean includeLocation) {
        this.includeLocation = includeLocation;
    }

    public Map<String, String> getFields() {
        if (fields == null) {
            fields = new HashMap<String, String>();
        }
        return Collections.unmodifiableMap(fields);
    }
    
    public Object transformExtendedField(String field, Object object) {
        if (object != null)
            return object.toString();
        return null;
    }

    
    
    @Override
	protected void setStarting()
	{
		super.setStarting();
        if (graylogHost == null && amqpURI == null) {
            getHandler().error("Graylog2 hostname and amqp uri are empty!", null);
        } else if (graylogHost != null && amqpURI != null) {
            getHandler().error("Graylog2 hostname and amqp uri are both informed!", null);
        } else {
            try {
                if (graylogHost != null && graylogHost.startsWith("tcp:")) {
                    String tcpGraylogHost = graylogHost.substring(4);
                    gelfSender = getGelfTCPSender(tcpGraylogHost, graylogPort);
                } else if (graylogHost != null && graylogHost.startsWith("udp:")) {
                    String udpGraylogHost = graylogHost.substring(4);
                    gelfSender = getGelfUDPSender(udpGraylogHost, graylogPort);
                } else if (amqpURI != null) {
                    gelfSender = getGelfAMQPSender(amqpURI, amqpExchangeName, amqpRoutingKey, amqpMaxRetries);
                } else {
                    gelfSender = getGelfUDPSender(graylogHost, graylogPort);
                }
            } catch (UnknownHostException e) {
                getHandler().error("Unknown Graylog2 hostname:" + getGraylogHost(), e);
            } catch (SocketException e) {
                getHandler().error("Socket exception", e);
            } catch (IOException e) {
                getHandler().error("IO exception", e);
            } catch (URISyntaxException e) {
                getHandler().error("AMQP uri exception", e);
            } catch (NoSuchAlgorithmException e) {
                getHandler().error("AMQP algorithm exception", e);
            } catch (KeyManagementException e) {
                getHandler().error("AMQP key exception", e);
            }
        }
    }

    protected GelfUDPSender getGelfUDPSender(String udpGraylogHost, int graylogPort) throws IOException {
        return new GelfUDPSender(udpGraylogHost, graylogPort);
    }

    protected GelfTCPSender getGelfTCPSender(String tcpGraylogHost, int graylogPort) throws IOException {
        return new GelfTCPSender(tcpGraylogHost, graylogPort);
    }

    protected GelfAMQPSender getGelfAMQPSender(String amqpURI, String amqpExchangeName, String amqpRoutingKey, int amqpMaxRetries) throws IOException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
        return new GelfAMQPSender(amqpURI, amqpExchangeName, amqpRoutingKey, amqpMaxRetries);
    }


    
  
    public void append(LogEvent event) {
        GelfMessage gelfMessage = GelfMessageFactory.makeMessage((Layout<String>) getLayout(), event, this);

        if(getGelfSender() == null) {
            getHandler().error("Could not send GELF message. Gelf Sender is not initialised and equals null");
        } else {
            GelfSenderResult gelfSenderResult = getGelfSender().sendMessage(gelfMessage);
            if (!GelfSenderResult.OK.equals(gelfSenderResult)) {
                getHandler().error("Error during sending GELF message. Error code: " + gelfSenderResult.getCode() + ".", gelfSenderResult.getException());
            }
        }

    }


	public GelfSender getGelfSender() {
        return gelfSender;
    }

    public void close() {
        getGelfSender().close();
    }

    public boolean requiresLayout() {
        return true;
    }

}
