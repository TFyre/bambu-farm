package com.tfyre.bambu.server;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.tfyre.bambu.model.BambuMessage;
import io.quarkus.scheduler.Scheduler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class BambuPrinterProcessor implements Processor {

    private static final Logger log = Logger.getLogger(BambuPrinterProcessor.class.getName());
    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().preservingProtoFieldNames();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();
    private static final Map<String, String> MAP = new ConcurrentHashMap<>();
    private static final Random RND = new SecureRandom();
    private static final String RES_STATUS = "status";
    private static final String RES_FULLSTATUS = "fullstatus";

    private final AtomicLong counter = new AtomicLong(Math.abs(RND.nextInt()));
    private final AtomicInteger time = new AtomicInteger(RND.nextInt(100));
    private final Endpoint endpoint;
    private ProducerTemplate producerTemplate;
    private final String name;

    public BambuPrinterProcessor(final Endpoint endpoint, final String name) {
        this.endpoint = endpoint;
        this.name = name;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message message = exchange.getMessage();
        final String body = message.getBody(String.class);
        log.debugf("%s: Received - [%d]", name, body.length());
        log.tracef("%s: Received RAW: %s", name, body);
        if (body.contains("pushall")) {
            sendFullStatus();
        }
    }

    private BambuMessage.Builder fromJson(final String data) {
        final BambuMessage.Builder builder = BambuMessage.newBuilder();
        try {
            PARSER.merge(data, builder);
        } catch (InvalidProtocolBufferException ex) {
            log.errorf(ex, "Cannot build message: %s", ex.getMessage());
        }
        return builder;
    }

    private String getDataFromResource(final String name) {
        final String fullName = String.format("json/%s.json", name);
        try {
            try (final InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(fullName)) {
                if (resource == null) {
                    return null;
                }
                try (InputStreamReader isr = new InputStreamReader(resource); BufferedReader reader = new BufferedReader(isr)) {
                    return reader.lines().collect(Collectors.joining(System.lineSeparator()));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException(String.format("Cannot read %s - %s", fullName, ex.getMessage()));
        }
    }

    private BambuMessage.Builder fromResource(final String name) {
        final String data = MAP.computeIfAbsent(name, this::getDataFromResource);
        return fromJson(data);
    }

    private Optional<String> toJson(final BambuMessage.Builder builder) {
        try {
            return Optional.of(PRINTER.print(builder));
        } catch (InvalidProtocolBufferException ex) {
            log.errorf(ex, "Cannot build message: %s", ex.getMessage());
            return Optional.empty();
        }
    }

    private void sendData(final String data) {
        if (producerTemplate == null) {
            return;
        }
        log.debugf("%s: Sending - [%d]", name, data.length());
        log.tracef("%s: Sending RAW: %s", name, data);
        producerTemplate.sendBody(endpoint, data);
    }

    public void sendStatus() {
        if (time.decrementAndGet() < 1) {
            time.set(100);
        }

        final BambuMessage.Builder builder = fromResource(RES_STATUS);
        builder.getPrintBuilder()
                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                .setNozzleTemper(RND.nextDouble(230))
                .setBedTemper(RND.nextDouble(65))
                .setMcRemainingTime(time.get());
        toJson(builder)
                .ifPresent(this::sendData);
    }

    public void start(final CamelContext context, final Scheduler scheduler) {
        log.debug("start");
        producerTemplate = context.createProducerTemplate();
        scheduler.newJob("%s#%s".formatted(getClass().getSimpleName(), this.name))
                .setInterval("1s")
                .setTask(c -> sendStatus())
                .schedule();
    }

    private void sendFullStatus() {
        final BambuMessage.Builder builder = fromResource(RES_FULLSTATUS);
        builder.getPrintBuilder()
                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                .setSpdLvl(RND.nextInt(4) + 1)
                .setChamberTemper(RND.nextDouble(55));
        builder.getPrintBuilder().getLightsReportBuilder(0).setMode(counter.get() % 20 >= 10 ? "on" : "off");
        toJson(builder)
                .ifPresent(this::sendData);
    }

}
