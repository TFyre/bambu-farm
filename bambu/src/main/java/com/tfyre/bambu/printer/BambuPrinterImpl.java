package com.tfyre.bambu.printer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.model.BambuMessage;
import com.tfyre.bambu.model.Print;
import com.tfyre.bambu.model.Pushing;
import com.tfyre.bambu.printer.BambuConst.PrinterModel;
import com.tfyre.bambu.security.SecurityUtils;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Dependent
public class BambuPrinterImpl implements BambuPrinter, Processor {

    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().preservingProtoFieldNames();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

    private static final int MAX_ITEMS = 1_000;

    private String name;
    private BambuConfig.Printer config;
    private Optional<BambuPrinter.Message> status = Optional.empty();
    private Optional<BambuPrinter.Message> fullStatus = Optional.empty();
    private Optional<BambuPrinter.Thumbnail> thumbnail = Optional.empty();
    private Optional<String> iframe = Optional.empty();

    private final BlockingQueue<BambuPrinter.Message> lastMessages = new LinkedBlockingQueue<>(MAX_ITEMS);
    private final AtomicLong counter = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private OffsetDateTime nextFullStatus = OffsetDateTime.now();

    @Inject
    Logger log;
    @Inject
    CamelContext context;
    @Inject
    BambuConfig bambuConfig;

    private Endpoint endpoint;
    private ProducerTemplate producerTemplate;
    private int printerError;
    private int totalLayerNum;
    private String printType = BambuConst.PRINT_TYPE_IDLE;
    private PrinterModel model = BambuConst.PrinterModel.UNKNOWN;
    private boolean blocked;

    public BambuPrinterImpl() {
    }

    @Override
    public boolean isBlocked() {
        return blocked;
    }

    @Override
    public boolean isIdle() {
        return BambuConst.PRINT_TYPE_IDLE.equals(printType);
    }

    @Override
    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    private void setLastPrint(final Print print) {
        if (print.hasPrintError()) {
            printerError = print.getPrintError();
        }
        if (print.hasTotalLayerNum()) {
            totalLayerNum = print.getTotalLayerNum();
        }
        if (print.hasPrintType()) {
            printType = print.getPrintType();
        }
    }

    private void addLast(final BambuPrinter.Message message) {
        while (lastMessages.remainingCapacity() <= 1) {
            lastMessages.remove();
        }
        lastMessages.add(message);

        if (message.message().hasPrint()) {
            setLastPrint(message.message().getPrint());
        }
    }

    private void buildIFrame(final String id) {
        if (!config.stream().liveView()) {
            return;
        }
        iframe = config.stream().url()
                .or(() -> bambuConfig.liveViewUrl().map(url -> "%s%s".formatted(url, id)));
        if (iframe.isEmpty()) {
            log.errorf("%s: Live View needs [bambu.printers.XXX.stream.url] or [bambu.live-view-url] configured", name);
        }
    }

    public void setup(final Scheduler scheduler, final String name, final BambuConfig.Printer config, final Endpoint endpoint, final String id) {
        this.name = name;
        this.model = config.model();
        this.config = config;
        this.endpoint = endpoint;
        buildIFrame(id);
        scheduler.newJob("%s.requestFullStatus#%s".formatted(getClass().getName(), name))
                .setInterval("1m")
                .setTask(e -> commandFullStatusInternal(false, false))
                .schedule();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PrinterModel getModel() {
        return model;
    }

    @Override
    public Optional<BambuPrinter.Message> getStatus() {
        return status;
    }

    public void setStatus(final BambuPrinter.Message status) {
        addLast(status);
        this.status = Optional.of(status);
    }

    public void setFullStatus(final BambuPrinter.Message fullStatus) {
        setStatus(fullStatus);
        this.fullStatus = Optional.of(fullStatus);
    }

    @Override
    public int getPrintError() {
        return printerError;
    }

    @Override
    public int getTotalLayerNum() {
        return totalLayerNum;
    }

    @Override
    public String getPrintType() {
        return printType;
    }

    @Override
    public Optional<BambuPrinter.Message> getFullStatus() {
        return fullStatus;
    }

    @Override
    public Optional<String> getIFrame() {
        return iframe;
    }

    @Override
    public Optional<BambuPrinter.Thumbnail> getThumbnail() {
        return thumbnail;
    }

    @Override
    public Collection<Message> getLastMessages() {
        return Collections.unmodifiableCollection(lastMessages);
    }

    public void setThumbnail(final BambuPrinter.Thumbnail thumbnail) {
        this.thumbnail = Optional.of(thumbnail);
    }

    private Optional<BambuMessage> fromJson(final String data) {
        final BambuMessage.Builder builder = BambuMessage.newBuilder();
        try {
            PARSER.merge(data, builder);
            return Optional.of(builder.build());
        } catch (InvalidProtocolBufferException ex) {
            log.errorf(ex, "Cannot build message: %s - %s", ex.getMessage(), data);
            return Optional.empty();
        }
    }

    private Optional<String> toJson(final BambuMessage message) {
        try {
            return Optional.of(PRINTER.print(message));
        } catch (InvalidProtocolBufferException ex) {
            log.errorf(ex, "Cannot build message: %s", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final org.apache.camel.Message message = exchange.getMessage();
        final String body = message.getBody(String.class);
        log.debugf("%s: Received - [%d]", name, body.length());
        log.tracef("%s: Received RAW: %s", name, body);

        fromJson(body)
                .map(msg -> new BambuPrinter.Message(OffsetDateTime.now(), msg, body))
                .ifPresent(msg -> {
                    if (body.length() > 2_000) {
                        setFullStatus(msg);
                    } else {
                        setStatus(msg);
                    }
                });
    }

    private void sendData(final String data) {
        if (producerTemplate == null) {
            log.debugf("%s: producerTemplate is null", name);
            return;
        }
        log.debugf("%s: Sending - [%d]", name, data.length());
        log.tracef("%s: Sending RAW: %s", name, data);
        producerTemplate.sendBody(endpoint, data);
    }

    private void logUser(final String data) {
        final String user = SecurityUtils.getPrincipal().map(p -> p.getName()).orElse("null");
        final String ip = Optional.ofNullable(VaadinSession.getCurrent()).map(vs -> vs.getBrowser().getAddress()).orElse("null");
        log.infof("%s user[%s] ip[%s]", data, user, ip);
    }

    private void commandFullStatusInternal(final boolean fromUser, final boolean force) {
        if (!running.get()) {
            return;
        }
        if (!force && nextFullStatus.isAfter(OffsetDateTime.now())) {
            return;
        }
        nextFullStatus = OffsetDateTime.now().plus(config.mqtt().fullStatus());
        if (fromUser) {
            logUser("%s: Requesting full Status, next: %s".formatted(name, nextFullStatus));
        } else {
            log.debugf("%s: Requesting full Status, next: %s", name, nextFullStatus);
        }
        final BambuMessage message = BambuMessage.newBuilder()
                .setPushing(
                        Pushing.newBuilder()
                                .setCommand("pushall")
                                .setPushTarget(1)
                                .setVersion(1)
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandFullStatus(final boolean force) {
        commandFullStatusInternal(true, force);
    }

    @PostConstruct
    public void postConstruct() {
        log.debug("postConstruct");
        producerTemplate = context.createProducerTemplate();
    }

    public void start() {
        log.debug("start");
        running.set(true);
        commandFullStatusInternal(false, false);
    }

    public void stop() {
        log.debug("stop");
        running.set(false);
    }

    @Override
    public void commandLight(final BambuConst.LightMode lightMode) {
        logUser("%s: commandLight %s".formatted(name, lightMode));
        final BambuMessage message = BambuMessage.newBuilder()
                .setSystem(
                        com.tfyre.bambu.model.System.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("ledctrl")
                                .setLedNode(BambuConst.CHAMBER_LIGHT)
                                .setLedMode(lightMode.getValue())
                                .setLedOnTime(500)
                                .setLedOffTime(500)
                                .setLoopTimes(1)
                                .setIntervalTime(1000)
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandFilamentLoad(final int amsTrayId) {
        logUser("%s: commandFilamentLoad %d".formatted(name, amsTrayId));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        com.tfyre.bambu.model.Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("ams_change_filament")
                                .setTarget(amsTrayId)
                                .setCurrTemp(BambuConst.AMS_TRAY_TEMP)
                                .setTarTemp(BambuConst.AMS_TRAY_TEMP)
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandFilamentSetting(final int amsId, final int trayId, final Filament filament, final String color, final int minTemp, final int maxTemp) {
        logUser("%s: commandFilamentSetting ams[%d] tray[%d] filament[%s] color[%s] min[%d] max[%d]"
                .formatted(name, amsId, trayId, filament, color, minTemp, maxTemp));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        com.tfyre.bambu.model.Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("ams_filament_setting")
                                .setAmsId(amsId)
                                .setTrayId(trayId)
                                .setTrayInfoIdx(filament.getCode())
                                .setTrayColor(color)
                                .setNozzleTempMin(minTemp)
                                .setNozzleTempMax(maxTemp)
                                .setTrayType(filament.getType().getDescription())
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandFilamentUnload() {
        logUser("%s: commandFilamentUnload".formatted(name));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        com.tfyre.bambu.model.Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("unload_filament")
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandControl(final BambuConst.CommandControl control) {
        logUser("%s: commandControl: %s".formatted(name, control));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand(control.getValue())
                                .setParam("")
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandSpeed(final BambuConst.Speed speed) {
        logUser("%s: commandSpeed: %s".formatted(name, speed));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("print_speed")
                                .setParam("%d".formatted(speed.getSpeed()))
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandClearPrinterError() {
        logUser("%s: commandClearPrinterError".formatted(name));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("clean_print_error")
                                .setSubtaskId("0")
                                .setPrintError(printerError)
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    private String stripSlash(final String fileName) {
        if (fileName.startsWith(BambuConst.PATHSEP)) {
            return fileName.substring(1);
        }
        return fileName;
    }

    @Override
    public void commandPrintGCodeLine(final String lines) {
        logUser("%s: commandPrintGCodeLine: [%s]".formatted(name, lines));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("gcode_line")
                                .setParam(lines.concat("\n"))
                )
                .build();

        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandPrintGCodeLine(final List<String> lines) {
        commandPrintGCodeLine(String.join("\n", lines));
    }

    @Override
    public void commandPrintGCodeFile(final String filename) {
        final String _filename = stripSlash(filename);
        logUser("%s: commandPrintGCode: %s".formatted(name, _filename));
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("gcode_file")
                                .setParam("/sdcard/%s".formatted(_filename))
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

    @Override
    public void commandPrintProjectFile(final String filename, final int plateId, final boolean useAms, final boolean timelapse, final boolean bedLevelling, final List<Integer> amsMapping) {
        final String _filename = stripSlash(filename);
        logUser("%s: commandPrintProject: %s ams[%s] timelapse[%s] bedlevelling[%s] amsMapping[%s]".formatted(name, _filename, useAms, timelapse, bedLevelling, amsMapping));
        final int pos = _filename.lastIndexOf(".");
        final String taskName = pos == -1 ? _filename : _filename.substring(0, pos);
        final BambuMessage message = BambuMessage.newBuilder()
                .setPrint(
                        Print.newBuilder()
                                .setSequenceId("%d".formatted(counter.incrementAndGet()))
                                .setCommand("project_file")
                                .setParam("Metadata/plate_%d.gcode".formatted(plateId))
                                .setProjectId("0")
                                .setProfileId("0")
                                .setTaskId("0")
                                .setSubtaskId("0")
                                .setSubtaskName(taskName)
                                .setFile("")
                                .setUrl("file:///sdcard/%s".formatted(_filename))
                                .setMd5("")
                                .setTimelapse(timelapse)
                                .setBedType("auto")
                                .setBedLevelling(bedLevelling)
                                .setFlowCali(true)
                                .setVibrationCali(true)
                                .setLayerInspect(true)
                                .addAllAmsMapping(amsMapping)
                                .setUseAms(useAms)
                )
                .build();
        toJson(message).ifPresent(this::sendData);
    }

}
