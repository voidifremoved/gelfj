GELFJ - A GELF Appender for Log4j and a GELF Handler for JDK Logging
====================================================================

Forked from https://github.com/t0xa/gelfj/wiki to make it work with Log4j2.


Downloading
-----------

Add the following dependency section to your pom.xml:

    <dependencies>
      ...
      <dependency>
        <groupId>org.graylog2</groupId>
        <artifactId>gelfj</artifactId>
        <version>1.1.13-LOG4J2</version>
      </dependency>
      ...
    </dependencies>

What is GELFJ
-------------

It's very simple GELF implementation in pure Java with the Log4j appender and JDK Logging Handler. It supports chunked messages which allows you to send large log messages (stacktraces, environment variables, additional fields, etc.) to a [Graylog2](http://www.graylog2.org/) server.

Following transports are supported:

 * TCP
 * UDP
 * AMQP


How to use GELFJ
----------------

Drop the latest JAR into your classpath and configure Log4j to use it.

Log4j appender
--------------

GelfAppender will use the log message as a short message and a stacktrace (if exception available) as a long message if "extractStacktrace" is true.

To use GELF Facility as appender in Log4j2.xml:


    <GelfAppender name="graylog2"
        graylogHost="192.168.0.201"
        originHost="my.machine.example.com"
        extractStacktrace="true"
        addExtendedInformation="true"
        facility="gelf-java"
        additionalFields="{'environment': 'DEV', 'application': 'MyAPP'}">
    </GelfAppender>
    
Or for AMQP:

    <GelfAppender name="graylog2"
        amqpURI="192.168.0.201"
        amqpExchangeName="log-exchange"
        amqpRoutingKey="graylog2"
        amqpMaxRetries="5"
        originHost="my.machine.example.com"
        extractStacktrace="true"
        addExtendedInformation="true"
        facility="gelf-java"
        additionalFields="{'environment': 'DEV', 'application': 'MyAPP'}">
    </GelfAppender>    
    

and then add it as a one of appenders:

    <root>
        <priority value="INFO"/>
        <appender-ref ref="graylog2"/>
    </root>


Options
-------

GelfAppender supports the following options:

- **graylogHost**: Graylog2 server where it will send the GELF messages; to use TCP instead of UDP, prefix with `tcp:`
- **graylogPort**: Port on which the Graylog2 server is listening; default 12201 (*optional*)
- **originHost**: Name of the originating host; defaults to the local hostname (*optional*)
- **extractStacktrace** (true/false): Add stacktraces to the GELF message; default false (*optional*)
- **addExtendedInformation** (true/false): Add extended information like Log4j's NDC/MDC; default false (*optional*)
- **includeLocation** (true/false): Include caller file name and line number. Log4j documentation warns that generating caller location information is extremely slow and should be avoided unless execution speed is not an issue; default true (*optional*)
- **facility**: Facility which to use in the GELF message; default "gelf-java"
- **amqpURI**: AMQP URI (*required when using AMQP integration*)
- **amqpExchangeName**: AMQP Exchange name - should be the same as setup in graylog2-radio (*required when using AMQP integration*)
- **amqpRoutingKey**: AMQP Routing key - should be the same as setup in graylog2-radio (*required when using AMQP integration*)
- **amqpMaxRetries**: Retries count; default value 0 (*optional*)


What is GELF
------------

The Graylog Extended Log Format (GELF) avoids the shortcomings of classic plain syslog:

- Limited to length of 1024 byte
- Not much space for payloads like stacktraces
- Unstructured. You can only build a long message string and define priority, severity etc.

You can get more information here: [http://www.graylog2.org/about/gelf](http://www.graylog2.org/about/gelf)
