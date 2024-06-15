package com.tfyre.bambu.view.dashboard;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.YesNoCancelDialog;
import com.tfyre.bambu.model.AmsSingle;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.model.Print;
import com.tfyre.bambu.model.Tray;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuConst.Speed;
import com.tfyre.bambu.printer.BambuErrors;
import com.tfyre.bambu.printer.Filament;
import com.tfyre.bambu.security.SecurityUtils;
import com.tfyre.bambu.view.FilamentView;
import com.tfyre.bambu.view.GCodeDialog;
import com.tfyre.bambu.view.LogsView;
import com.tfyre.bambu.view.PrinterView;
import com.tfyre.bambu.view.SdCardView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jboss.logging.Logger;
import com.tfyre.bambu.view.NotificationHelper;
import com.tfyre.bambu.view.ViewHelper;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import java.util.ArrayList;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Dependent
public class DashboardPrinter implements NotificationHelper, ViewHelper {

    //DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter DTF = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter();

    private final ProgressBar progressBar;
    private final Span progressFile = newSpan();
    private final Span progressTime = newSpan();
    private final Span progressLayer = newSpan();
    private final Image monitorLamp = new Image(Images.MONITOR_LAMP_OFF.getImage(), "Monitor Lamp");
    private final Span monitorLampText = newSpan();
    private final Image bedImage = new Image(Images.MONITOR_BED_TEMP.getImage(), "Bed");
    private final Span bed = newSpan();
    private final Span bedTarget = newSpan();
    private final Image nozzleImage = new Image(Images.MONITOR_NOZZLE_TEMP.getImage(), "Nozzle");
    private final Span nozzle = newSpan();
    private final Span nozzleTarget = newSpan();
    private final Image frameImage = new Image(Images.MONITOR_FRAME_TEMP.getImage(), "Frame");
    private final Span frame = newSpan();
    private final Image speedImage = new Image(Images.MONITOR_SPEED.getImage(), "Speed");
    private final Span speed = newSpan();
    private final Image thumbnail = new Image();
    private final IFrame iframe = new IFrame();
    private final Span thumbnailUpdated = newSpan();
    private final Span printerStatus = newSpan();
    private final Div printerName = new Div();
    private String printerInfo = "";
    private String thumbnailId;
    private boolean built;
    private boolean processFull = true;
    private final boolean isAdmin;
    private int lastError = 0;
    private double temperatureNozzle = 0;
    private double temperatureBed = 0;

    private final Map<String, AmsHeader> amsHeaders = new HashMap<>();
    private final Map<String, AmsFilament> amsFilaments = new HashMap<>();
    private BambuConst.GCodeState gcodeState = BambuConst.GCodeState.IDLE;

    private Component thumbnailOrIframe;
    private BambuPrinter printer;
    private boolean fromDashboard;

    @Inject
    Logger log;
    @Inject
    BambuConfig config;

    public DashboardPrinter() {
        progressBar = newProgressBar();
        isAdmin = SecurityUtils.userHasAccess(SystemRoles.ROLE_ADMIN);
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    private Span newSpan() {
        return new Span("---");
    }

    private void setTemperature(final Span span, final double value) {
        span.setText("%.2fºC".formatted(value));
    }

    private Images getHumidityImage(final String id) {
        if ("2".equals(id)) {
            return Images.AMS_HUMIDITY_1;
        }
        if ("3".equals(id)) {
            return Images.AMS_HUMIDITY_2;
        }
        if ("4".equals(id)) {
            return Images.AMS_HUMIDITY_3;
        }
        if ("5".equals(id)) {
            return Images.AMS_HUMIDITY_4;
        }
        return Images.AMS_HUMIDITY_0;
    }

    private void processAms(final com.tfyre.bambu.model.Ams ams) {
        final int amsTrayId;
        if (gcodeState.isIdle() || !ams.hasTrayNow()) {
            amsTrayId = -1;
        } else {
            amsTrayId = parseInt(printer.getName(), ams.getTrayNow(), -1);
        }
        ams.getAmsList().forEach(single -> {
            final int amsId = getAmsId(single);
            Optional.ofNullable(amsHeaders.get(getAmsHeaderId(amsId))).ifPresent(header -> {
                setTemperature(header.temperature(), parseDouble(printer.getName(), single.getTemp(), 0));
                header.humidity().setSrc(getHumidityImage(single.getHumidity()).getImage());
            });

            single.getTrayList().forEach(tray -> {
                final int trayId = getTrayId(tray);
                Optional.ofNullable(amsFilaments.get(getFilamentTrayKey(amsId, trayId))).ifPresent(filament -> {
                    if (!tray.hasTrayInfoIdx()) {
                        filament.type().setText("Empty");
                        return;
                    }
                    filament.type().setText(Filament.getFilamentDescription(tray.getTrayInfoIdx()));
                    filament.color().getStyle().setBackgroundColor("#%s".formatted(tray.getTrayColor()));
                    filament.div().removeClassName("active");
                    if (amsTrayId == filament.amsTrayId()) {
                        filament.div().addClassName("active");
                    }
                });
            });
        });
    }

    private void processVtTray(final Tray tray) {
        final int trayId = getTrayId(tray);
        Optional.ofNullable(amsHeaders.get(getTrayKey(trayId))).ifPresent(header -> {
            setTemperature(header.temperature(), parseDouble(printer.getName(), tray.getTrayTemp(), 0));
        });
        Optional.ofNullable(amsFilaments.get(getTrayKey(trayId))).ifPresent(filament -> {
            filament.type().setText(Filament.getFilamentDescription(tray.getTrayInfoIdx()));
            filament.color().getStyle().setBackgroundColor("#%s".formatted(tray.getTrayColor()));
        });
    }

    private void processPrint(final BambuPrinter.Message message, final Print print) {
        if (gcodeState.isIdle()) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressFile.setText(thumbnailId);
            progressFile.setText("");
            progressTime.setText("--");
            progressLayer.setText("");
        } else {
            //Percetage
            if (print.hasMcPercent()) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(Math.min(print.getMcPercent(), 100));
            }

            //FileName
            if (print.hasSubtaskName()) {
                progressFile.setText(print.getSubtaskName());
            }

            //Time
            if (print.hasMcRemainingTime()) {
                progressTime.setText("%s remaining".formatted(formatTime(Duration.ofMinutes(print.getMcRemainingTime()))));
            }

            //Layers
            if (print.hasLayerNum()) {
                progressLayer.setText("Layer %d / %d".formatted(print.getLayerNum(), printer.getTotalLayerNum()));
            }
        }

        //Bed & Target Temperature
        if (print.hasBedTemper()) {
            setTemperature(bed, print.getBedTemper());
            bedImage.setSrc(print.getBedTemper() > 0.0 ? Images.MONITOR_BED_TEMP_ACTIVE.getImage() : Images.MONITOR_BED_TEMP.getImage());
        }
        if (print.hasBedTargetTemper()) {
            temperatureBed = print.getBedTargetTemper();
            setTemperature(bedTarget, temperatureBed);
        }

        //Nozzle & Target Temperature
        if (print.hasNozzleTemper()) {
            setTemperature(nozzle, print.getNozzleTemper());
            nozzleImage.setSrc(print.getNozzleTemper() > 0.0 ? Images.MONITOR_NOZZLE_TEMP_ACTIVE.getImage() : Images.MONITOR_NOZZLE_TEMP.getImage());
        }
        if (print.hasNozzleTargetTemper()) {
            temperatureNozzle = print.getNozzleTargetTemper();
            setTemperature(nozzleTarget, temperatureNozzle);
        }

        //Frame/Chamber Temperature
        if (print.hasChamberTemper()) {
            setTemperature(frame, print.getChamberTemper());
        }

        //Speed
        if (print.hasSpdLvl()) {
            speed.setText(Speed.fromSpeed(print.getSpdLvl()).getDescription());
        }

        if (print.hasAms() && print.getAms().getAmsCount() > 0) {
            processAms(print.getAms());
        } else if (print.hasVtTray()) {
            processVtTray(print.getVtTray());
        }

        print.getLightsReportList().stream()
                .filter(lr -> BambuConst.CHAMBER_LIGHT.equals(lr.getNode()))
                .findFirst()
                .ifPresent(lr -> {
                    monitorLampText.setText(lr.getMode());
                    monitorLamp.setSrc(BambuConst.LightMode.ON.getValue().equals(lr.getMode()) ? Images.MONITOR_LAMP_ON.getImage() : Images.MONITOR_LAMP_OFF.getImage());
                });
    }

    private <T> void process(final boolean hasValue, final BambuPrinter.Message message, final T data, final BiConsumer<BambuPrinter.Message, T> consumer) {
        if (!hasValue) {
            return;
        }
        consumer.accept(message, data);
    }

    private void processError(final BambuPrinter.Message message) {
        lastError = printer.getPrintError();
        final String errorString;
        final boolean hasError;
        if (lastError == 0) {
            hasError = false;
            errorString = "";
        } else {
            hasError = true;
            errorString = "\n\nPrint Error [%d / %s]: %s".formatted(
                    lastError, Integer.toHexString(lastError),
                    BambuErrors.getPrinterError(lastError).orElseGet(() -> "No Translation"));
        }

        printerInfo = "Last Updated: %s%s".formatted(DTF.format(message.lastUpdated()), errorString);
        if (hasError) {
            printerName.addClassName(LumoUtility.Background.ERROR_50);
        } else {
            printerName.removeClassName(LumoUtility.Background.ERROR_50);
        }
    }

    private void processMessage(final BambuPrinter.Message message) {
        process(message.message().hasPrint(), message, message.message().getPrint(), this::processPrint);
        processError(message);
    }

    private void updatePrinterStatus() {
        final String value = "Status: %s".formatted(gcodeState.getDescription());
        if (value.equals(printerStatus.getText())) {
            return;
        }
        printerStatus.setText(value);
        if (gcodeState.isError()) {
            printerStatus.addClassName(LumoUtility.TextColor.ERROR);
        } else if(gcodeState.isReady()){
            printerStatus.addClassName(LumoUtility.TextColor.SUCCESS);
        } else if(gcodeState.isRunning()) {
            printerStatus.addClassName(LumoUtility.TextColor.PRIMARY);
        } else {
            printerStatus.removeClassName(LumoUtility.TextColor.ERROR);
            printerStatus.removeClassName(LumoUtility.TextColor.SUCCESS);
            printerStatus.removeClassName(LumoUtility.TextColor.PRIMARY);
        }
    }

    public void update() {
        if (!built) {
            return;
        }
        gcodeState = printer.getGCodeState();
        if (processFull) {
            printer.getFullStatus().ifPresent(message -> {
                processFull = false;
                processMessage(message);
            });
        }
        printer.getStatus().ifPresent(this::processMessage);
        printer.getThumbnail().ifPresent(data -> {
            if (data.thumbnail().getId().equals(thumbnailId)) {
                return;
            }
            thumbnailId = data.thumbnail().getId();
            thumbnail.setSrc(data.thumbnail());
            thumbnailUpdated.setText(DTF.format(data.lastUpdated()));
        });
        updatePrinterStatus();
    }

    private void doConfirm(final String description, final Runnable runnable) {
        YesNoCancelDialog.show("%s - %s\n\nAre you sure?".formatted(printer.getName(), description), ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            runnable.run();
        });
    }

    private void doConfirm(final BambuConst.CommandControl command) {
        doConfirm(command.getValue(), () -> printer.commandControl(command));
    }

    private void doConfirm(final String description, final String gcode) {
        doConfirm(description, () -> printer.commandPrintGCodeLine(gcode));
    }

    private Button newButton(final String toolTip, final VaadinIcon icon, final ComponentEventListener<ClickEvent<Button>> clickListener) {
        final Button result = new Button(new Icon(icon), clickListener);
        result.setTooltipText(toolTip);
        return result;
    }

    private Button fanControl(final String toolTip, final VaadinIcon icon) {
        final Button result = new Button(new Icon(icon));
        result.setTooltipText(toolTip);
        final ContextMenu menu = newContextMenu(result);
        EnumSet.allOf(BambuConst.Fan.class).forEach(fan -> {
            final SubMenu fanMenu = menu.addItem(fan.getName()).getSubMenu();
            EnumSet.allOf(BambuConst.FanSpeed.class).forEach(fanSpeed -> {
                fanMenu.addItem(fanSpeed.getName(), l -> doConfirm("Fan [%s] Speed[%s]".formatted(fan.getName(), fanSpeed.getName()),
                        BambuConst.gcodeFanSpeed(fan, fanSpeed)));
            });
        });
        return result;
    }

    private void showStatus() {
        final Dialog d = new Dialog();
        d.setHeaderTitle("%s: Status".formatted(printer.getName()));
        final Div div = new Div(printerInfo);
        d.add(div);
        if (lastError != 0) {
            div.getStyle()
                    .setPadding("10px")
                    .setWhiteSpace(Style.WhiteSpace.PRE_WRAP);
            div.addClassName(LumoUtility.Background.ERROR_50);
        }
        final Button ok = new Button("OK", e -> d.close());
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(ok);
        d.open();
    }

    private Div buildName() {
        final Div result = newDiv("name", printerName);
        printerName.add(new Div(printer.getName()), newButton("", VaadinIcon.INFO, l -> showStatus()));
        if (isAdmin) {
            result.add(
                    newButton("Show Logs", VaadinIcon.CLIPBOARD_TEXT, l -> UI.getCurrent().navigate(LogsView.class, printer.getName())),
                    newButton("Show SD Card", VaadinIcon.ARCHIVE, l -> UI.getCurrent().navigate(SdCardView.class, printer.getName())),
                    newButton("Request full status", VaadinIcon.REFRESH, l -> printer.commandFullStatus(true)),
                    newButton("Clear Print Error", VaadinIcon.WARNING, l -> printer.commandClearPrinterError()),
                    newButton("Resume Print", VaadinIcon.PLAY, l -> doConfirm(BambuConst.CommandControl.RESUME)),
                    newButton("Pause Print", VaadinIcon.PAUSE, l -> doConfirm(BambuConst.CommandControl.PAUSE)),
                    newButton("Stop Print", VaadinIcon.STOP, l -> doConfirm(BambuConst.CommandControl.STOP))
            );
            if (fromDashboard) {
                result.add(newButton("Show Detail Printer", VaadinIcon.SEARCH_PLUS, l -> UI.getCurrent().navigate(PrinterView.class, printer.getName())));
            } else {
                result.add(
                        newButton("Disable Stepper Motors", VaadinIcon.COGS, l -> doConfirm("Disable Stepper Motors", BambuConst.gcodeDisableSteppers())),
                        fanControl("Fan Control", VaadinIcon.ASTERISK),
                        newButton("Send GCode", VaadinIcon.COG, l -> GCodeDialog.show(printer)),
                        newButton("Reboot", VaadinIcon.POWER_OFF, l -> doConfirm("Reboot", printer::commandSystemReboot)),
                        newButton("Back to Dashboard", VaadinIcon.ARROW_BACKWARD, l -> UI.getCurrent().navigate(Dashboard.class))
                );
            }
        }
        return result;
    }

    private Component buildControls() {
        final BiConsumer<BambuConst.Move, Integer> movexy = (m, value) ->
                printer.commandPrintGCodeLine(BambuConst.gcodeMoveXYZ(m, value, config.moveXy()));
        final BiConsumer<BambuConst.Move, Integer> movez = (m, value) ->
                printer.commandPrintGCodeLine(BambuConst.gcodeMoveXYZ(m, value, config.moveZ()));
        final Consumer<Boolean> movee = (up) ->
                printer.commandPrintGCodeLine(BambuConst.gcodeMoveExtruder(up));
        final Consumer<Boolean> home = b ->
                printer.commandPrintGCodeLine(b ? BambuConst.gcodeHomeXY() : BambuConst.gcodeHomeZ());

        final Div xyControl = newDiv("controlxy",
                newDiv("updown",
                        new Span("X/Y Control"),
                        newButton("Y+10", VaadinIcon.ANGLE_DOUBLE_UP, l -> movexy.accept(BambuConst.Move.Y, 10)),
                        newButton("Y+1", VaadinIcon.ANGLE_UP, l -> movexy.accept(BambuConst.Move.Y, 1))
                ),
                newDiv("leftright",
                        newButton("X-10", VaadinIcon.ANGLE_DOUBLE_LEFT, l -> movexy.accept(BambuConst.Move.X, -10)),
                        newButton("X-1", VaadinIcon.ANGLE_LEFT, l -> movexy.accept(BambuConst.Move.X, -1)),
                        newButton("XY Home", VaadinIcon.HOME, l -> home.accept(true)),
                        newButton("X+1", VaadinIcon.ANGLE_RIGHT, l -> movexy.accept(BambuConst.Move.X, 1)),
                        newButton("X+10", VaadinIcon.ANGLE_DOUBLE_RIGHT, l -> movexy.accept(BambuConst.Move.X, 10))
                ),
                newDiv("updown",
                        newButton("Y-1", VaadinIcon.ANGLE_DOWN, l -> movexy.accept(BambuConst.Move.Y, -1)),
                        newButton("Y-10", VaadinIcon.ANGLE_DOUBLE_DOWN, l -> movexy.accept(BambuConst.Move.Y, -10))
                )
        );

        final Div zControl = newDiv("controlz",
                new Span("Bed Control"),
                newButton("Bed+10", VaadinIcon.ANGLE_DOUBLE_UP, l -> movez.accept(BambuConst.Move.Z, -10)),
                newButton("Bed+1", VaadinIcon.ANGLE_UP, l -> movez.accept(BambuConst.Move.Z, -1)),
                newButton("Bed Home", VaadinIcon.HOME, l -> home.accept(false)),
                newButton("Bed-1", VaadinIcon.ANGLE_DOWN, l -> movez.accept(BambuConst.Move.Z, 1)),
                newButton("Bed-10", VaadinIcon.ANGLE_DOUBLE_DOWN, l -> movez.accept(BambuConst.Move.Z, 10))
        );

        final Div extruder = newDiv("extruder",
                new Span("Extruder"),
                newSpan("spacer"),
                newButton("Extruder-10", VaadinIcon.ANGLE_UP, l -> movee.accept(true)),
                newSpan("spacer"),
                newButton("Extruder+10", VaadinIcon.ANGLE_DOWN, l -> movee.accept(false)),
                newSpan("spacer")
        );

        final Button homeAll = newButton("All Home", VaadinIcon.HOME, l -> printer.commandPrintGCodeLine(BambuConst.gcodeHomeAll()));

        final Consumer<Boolean> setEnabled = enabled -> {
            xyControl.setEnabled(enabled);
            zControl.setEnabled(enabled);
            extruder.setEnabled(enabled);
            homeAll.setEnabled(enabled);
        };
        final Checkbox enableControl = new Checkbox("Enable Controls", l -> setEnabled.accept(l.getValue()));
        setEnabled.accept(false);

        return newDiv("controlsbox", thumbnailOrIframe,
                newDiv("controls",
                        newDiv("controlheader", enableControl, homeAll),
                        newDiv("controlbody", xyControl, zControl, extruder)));
    }

    private Component buildImage() {
        return newDiv("image",
                fromDashboard ? thumbnailOrIframe : buildControls(),
                newDiv("imagestatus", thumbnailUpdated, printerStatus)
        );
    }

    private Div getBadge(final String toolTip, final Component... components) {
        final Div result = newDiv("badge", components);
        result.addClassName(toolTip.toLowerCase());
        result.getElement().setProperty("title", toolTip);
        return result;
    }

    private ContextMenu newContextMenu(final Component component) {
        final ContextMenu result = new ContextMenu(component);
        if (config.menuLeftClick()) {
            result.setOpenOnClick(true);
        }
        return result;
    }

    private <T extends Component> T wrapMonitorMenu(final T result) {
        if (!isAdmin) {
            return result;
        }
        final ContextMenu menu = newContextMenu(result);
        Arrays.asList(BambuConst.LightMode.values())
                .forEach(lm -> {
                    menu.addItem("Set %s".formatted(lm.getValue()), l -> printer.commandLight(lm));
                });
        return result;
    }

    private <T extends Component> T wrapSpeedMenu(final T result) {
        if (!isAdmin) {
            return result;
        }
        final ContextMenu menu = newContextMenu(result);
        Arrays.asList(BambuConst.Speed.values())
                .forEach(s -> {
                    if (s == BambuConst.Speed.UNKNOWN) {
                        return;
                    }
                    menu.addItem(s.getDescription(), l -> doConfirm(s.getDescription(), () -> printer.commandSpeed(s)));
                });
        return result;
    }

    private void confirmTemperature(final String description, final List<String> list) {
        doConfirm(description, () -> printer.commandPrintGCodeLine(list));
    }

    private void confirmTemperature(final Supplier<Integer> current, final int maxTemp, final Function<Integer, String> function) {
        final IntegerField temp = new IntegerField("Target Temperature");
        temp.setMin(0);
        temp.setMax(maxTemp);
        temp.setStepButtonsVisible(true);
        temp.setValue(current.get());
        YesNoCancelDialog.show(List.of(temp), "%s\n\nAre you sure?".formatted(printer.getName()), ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            if (temp.getValue() < 0 || temp.getValue() > maxTemp) {
                showError("Invalid Temperature");
                return;
            }
            printer.commandPrintGCodeLine(function.apply(temp.getValue()));
        });
    }

    private <T extends Component> T wrapTemperature(final T result, final Supplier<Integer> current, final int maxTemp, final Function<Integer, String> function) {
        if (!isAdmin) {
            return result;
        }
        final ContextMenu menu = newContextMenu(result);
        menu.addItem("Set Target", l -> confirmTemperature(current, maxTemp, function));

        final List<BambuConfig.Temperature> list = config.preheat().orElse(BambuConst.PREHEAT);

        if (list.isEmpty()) {
            return result;
        }

        final SubMenu preheat = menu.addItem("Preheat").getSubMenu();
        list.forEach(t -> {
            if (t.bed() < 0 || t.bed() > BambuConst.TEMPERATURE_MAX_BED) {
                log.errorf("Skipping invalid bed preheat: %d", t.bed());
                return;
            }
            if (t.nozzle() < 0 || t.nozzle() > BambuConst.TEMPERATURE_MAX_NOZZLE) {
                log.errorf("Skipping invalid nozzle preheat: %d", t.nozzle());
                return;
            }

            preheat.addItem(t.name(), l -> confirmTemperature(t.name(), List.of(
                    BambuConst.gcodeTargetTemperatureBed(t.bed()),
                    BambuConst.gcodeTargetTemperatureNozzle(t.nozzle()))));

        });
        return result;
    }

    private FlexLayout buildStatus() {
        final FlexLayout result = new FlexLayout();
        result.addClassName("status");
        result.add(
                wrapTemperature(getBadge("Nozzle", nozzleImage, nozzle, nozzleTarget), () -> (int) temperatureNozzle, BambuConst.TEMPERATURE_MAX_NOZZLE, BambuConst::gcodeTargetTemperatureNozzle),
                wrapTemperature(getBadge("Bed", bedImage, bed, bedTarget), () -> (int) temperatureBed, BambuConst.TEMPERATURE_MAX_BED, BambuConst::gcodeTargetTemperatureBed)
        );

        if (printer.getModel() == BambuConst.PrinterModel.X1C) {
            result.add(getBadge("Frame", frameImage, frame));
        }

        result.add(
                wrapSpeedMenu(getBadge("Speed", speedImage, speed)),
                wrapMonitorMenu(getBadge("Lamp", monitorLamp, monitorLampText))
        );
        return result;
    }

    private HorizontalLayout buildProgressBar() {
        final HorizontalLayout result = new HorizontalLayout(progressFile, progressTime, progressLayer);
        result.addClassName("progress");
        return result;
    }

    private Div buildAmsHeader(final AmsHeader header) {
        amsHeaders.put(header.id(), header);
        final Div result = newDiv("amsheader",
                new Span(header.id()),
                newSpan("filler")
        );
        if (printer.getModel() == BambuConst.PrinterModel.X1C) {
            result.add(header.temperature());
        }
        result.add(header.humidity());
        return result;
    }

    private void doFilamentConfigure(final AmsFilament filament) {
        FilamentView.show(printer, filament.amsId(), filament.trayId());
    }

    private Div wrapAmsFilament(final AmsFilament filament) {
        final Div result = filament.div();
        if (!isAdmin) {
            return result;
        }
        final ContextMenu menu = newContextMenu(result);
        menu.addItem("Configure", l -> doFilamentConfigure(filament));
        menu.addItem("Load", l -> doConfirm(() -> printer.commandFilamentLoad(filament.trayId())));
        menu.addItem("Unload", l -> doConfirm(printer::commandFilamentUnload));

        return result;
    }

    private Div buildAmsFilament(final String key, final int amsId, final int trayId) {
        final Div result = newDiv("filament");
        final AmsFilament filament = new AmsFilament(amsId, trayId, result, new Span(), newSpan("color"));
        amsFilaments.put(key, filament);

        result.add(filament.type(), filament.color());
        return wrapAmsFilament(filament);
    }

    private int getAmsId(final AmsSingle single) {
        return parseInt(printer.getName(), single.getId(), BambuConst.AMS_TRAY_UNLOAD);
    }

    private int getTrayId(final Tray tray) {
        return parseInt(printer.getName(), tray.getId(), BambuConst.AMS_TRAY_UNLOAD);
    }

    private Div buildAmsFilament(final AmsSingle single, final Tray tray) {
        final int amsId = getAmsId(single);
        final int trayId = getTrayId(tray);
        return buildAmsFilament(getFilamentTrayKey(amsId, trayId), amsId, trayId);
    }

    private String getFilamentTrayKey(final int amsId, final int trayId) {
        return "single[%d]tray[%d]".formatted(amsId, trayId);
    }

    private String getAmsHeaderId(final int id) {
        return "AMS#%d".formatted(id);
    }

    private String getTrayKey(final int id) {
        return "Tray#%d".formatted(id);
    }

    private Div buildTray(final String amsHeaderId, final boolean hasHumidity, final List<Div> filaments) {
        final Image image = new Image(Images.AMS_HUMIDITY_0.getImage(), "Humidity");
        image.setTitle("Humidity");
        final AmsHeader amsHeader = new AmsHeader(amsHeaderId, newSpan(), image);
        if (!hasHumidity) {
            amsHeader.humidity().getStyle().setDisplay(Style.Display.NONE);
        }
        final Div trayL = newDiv("amstray");
        filaments.forEach(trayL::add);
        return newDiv("ams", buildAmsHeader(amsHeader), trayL);
    }

    private void buildAms(final Div parent, final com.tfyre.bambu.model.Ams ams) {
        ams.getAmsList().forEach(single -> {
            parent.add(buildTray(
                    getAmsHeaderId(getAmsId(single)),
                    true,
                    single.getTrayList().stream()
                            .map(tray -> buildAmsFilament(single, tray))
                            .toList()
            ));
        });
    }

    private void buildVtTray(final Div parent, final com.tfyre.bambu.model.Tray tray) {
        final int trayId = getTrayId(tray);
        parent.add(buildTray(
                getTrayKey(trayId),
                false,
                List.of(buildAmsFilament(getTrayKey(trayId), BambuConst.AMS_TRAY_UNLOAD, trayId))
        ));
    }

    private Div buildAms() {
        final Div result = newDiv("filaments");
        printer.getFullStatus().ifPresent(m -> {
            if (!m.message().hasPrint() || !m.message().getPrint().hasAms()) {
                return;
            }
            if (m.message().getPrint().getAms().getAmsCount() > 0) {
                buildAms(result, m.message().getPrint().getAms());
                return;
            }
            if (m.message().getPrint().hasVtTray()) {
                buildVtTray(result, m.message().getPrint().getVtTray());
            }
        });
        return result;
    }

    private Component getThumbnailOrIframe() {
        return printer.getIFrame()
                .map(url -> {
                    iframe.setSrc(url);
                    return (Component) iframe;
                })
                .orElse(thumbnail);
    }

    public Component build(final BambuPrinter printer, final boolean fromDashboard) {
        this.printer = printer;
        this.fromDashboard = fromDashboard;
        thumbnailOrIframe = getThumbnailOrIframe();
        try {
            progressBar.setIndeterminate(true);
            printerStatus.getStyle().setFontWeight(Style.FontWeight.BOLD);
            final List<Component> list = new ArrayList<>();
            list.add(buildName());
            if (!config.remoteView()) {
                // Dont buildImage
            } else if (fromDashboard && !config.dashboard().remoteView()) {
                // Dont buildImage
            } else {
                list.add(buildImage());
            }
            list.add(buildStatus());
            list.add(buildAms());
            list.add(buildProgressBar());
            list.add(progressBar);
            return createContent(list);
        } finally {
            built = true;
        }
    }

    private Component createContent(final List<Component> list) {
        final VerticalLayout content = new VerticalLayout();
        content.addClassName("dashboard-printer");
        content.setPadding(false);
        content.setSpacing(false);
        list.forEach(content::add);
        content.setSizeUndefined();
        return content;
    }

    private record AmsHeader(String id, Span temperature, Image humidity) {

    }

    private record AmsFilament(int amsId, int trayId, Div div, Span type, Span color) {

        public int amsTrayId() {
            return amsId * 4 + trayId;
        }

    }

    private enum Images {
        AMS_HUMIDITY_0("ams_humidity_0.svg"),
        AMS_HUMIDITY_1("ams_humidity_1.svg"),
        AMS_HUMIDITY_2("ams_humidity_2.svg"),
        AMS_HUMIDITY_3("ams_humidity_3.svg"),
        AMS_HUMIDITY_4("ams_humidity_4.svg"),
        MONITOR_BED_TEMP("monitor_bed_temp.svg"),
        MONITOR_BED_TEMP_ACTIVE("monitor_bed_temp_active.svg"),
        MONITOR_NOZZLE_TEMP("monitor_nozzle_temp.svg"),
        MONITOR_NOZZLE_TEMP_ACTIVE("monitor_nozzle_temp_active.svg"),
        MONITOR_SPEED("monitor_speed.svg"),
        MONITOR_SPEED_ACTIVE("monitor_speed_active.svg"),
        MONITOR_FRAME_TEMP("monitor_frame_temp.svg"),
        MONITOR_LAMP_ON("monitor_lamp_on.svg"),
        MONITOR_LAMP_OFF("monitor_lamp_off.svg");

        private final String image;

        private Images(final String image) {
            this.image = "bambu/%s".formatted(image);
        }

        public String getImage() {
            return image;
        }

    }

}
