package com.tfyre.bambu.printer;

import com.tfyre.bambu.BambuConfig;
import com.vaadin.flow.server.StreamResource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.quarkus.scheduler.Scheduler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Dependent
public class BambuPrinterStream {

    private static final byte[] EMPTY = new byte[32];
    private static final int MAX_SIZE = 10_000_000;

    private final NetClient client;
    private NetSocket socket;

    private OffsetDateTime nextImage = OffsetDateTime.now();

    @Inject
    ScheduledExecutorService executor;
    @Inject
    Logger log;

    private BambuConfig.Printer config;
    private boolean enabled;
    private String name;
    private Consumer<BambuPrinter.Thumbnail> consumer;

    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    public BambuPrinterStream(final Vertx vertx) {
        final NetClientOptions options = new NetClientOptions()
                .setHostnameVerificationAlgorithm("")
                .setSsl(true)
                .setTrustAll(true);
        client = vertx.createNetClient(options);
    }

    public void setup(final boolean enabled, final Scheduler scheduler, final String name, final BambuConfig.Printer config, final Consumer<BambuPrinter.Thumbnail> consumer) {
        this.enabled = enabled;
        this.name = name;
        this.config = config;
        this.consumer = consumer;

        if (!enabled) {
            return;
        }

        scheduler.newJob("%s.checkLastImage#%s".formatted(getClass().getName(), name))
                .setInterval("1m")
                .setTask(e -> checkLastImage())
                .schedule();
    }

    private Buffer getHandshake() {
        return Buffer.buffer(80)
                .appendIntLE(0x40)
                .appendIntLE(0x3000)
                .appendLong(0)
                .appendString(config.username())
                .appendBytes(EMPTY, 0, 32 - config.username().length())
                .appendString(config.accessCode())
                .appendBytes(EMPTY, 0, 32 - config.accessCode().length());
    }

    private URI getURI() {
        return URI.create(config.stream().url().orElseGet(() -> "ssl://%s:%d".formatted(config.ip(), config.stream().port())));
    }

    private void startStream() {
        final URI uri = getURI();
        client.connect(uri.getPort(), uri.getHost())
                .onSuccess(_s -> {
                    socket = _s;
                    final ByteBuf buffer = Unpooled.buffer(0, MAX_SIZE);
                    socket.handler(h -> {
                        buffer.writeBytes(h.getBytes());
                        log.debugf("%s: readable %d", name, buffer.readableBytes());
                        if (buffer.readableBytes() <= 16) {
                            return;
                        }

                        buffer.markReaderIndex();
                        final int size = buffer.readIntLE();
                        log.debugf("%s: size %d", name, size);
                        buffer.skipBytes(4 + 8);
                        if (buffer.readableBytes() < size) {
                            buffer.resetReaderIndex();
                            return;
                        }

                        final byte[] data = new byte[size];
                        buffer.readBytes(data).discardReadBytes();

                        consumer.accept(new BambuPrinter.Thumbnail(OffsetDateTime.now(), new StreamResource("image.jpg", () -> new ByteArrayInputStream(data))));
                        nextImage = OffsetDateTime.now().plus(config.stream().watchDog());
                    })
                            .write(getHandshake())
                            .onFailure(h -> {
                                log.errorf(h, "%s: socketFailure", name);
                            });
                })
                .onFailure(h -> {
                    log.errorf("%s: clientFailure: %s - %s", name, h.getClass().getName(), h.getMessage());
                });
    }

    private void closeSocket() {
        if (socket == null) {
            return;
        }
        socket.close();
        socket = null;
    }

    public void checkLastImage() {
        if (!running.get()) {
            return;
        }
        if (nextImage.isAfter(OffsetDateTime.now())) {
            return;
        }
        log.errorf("%s: No image received since %s", name, nextImage);
        closeSocket();
        executor.schedule(this::startStream, 10, TimeUnit.SECONDS);
    }

    public void start() {
        if (!enabled) {
            return;
        }
        nextImage = OffsetDateTime.now();
        running.set(true);
        startStream();
    }

    public void stop() {
        if (!enabled) {
            return;
        }
        running.set(false);
        log.infof("%s: stopping", name);
        closeSocket();
    }

}
