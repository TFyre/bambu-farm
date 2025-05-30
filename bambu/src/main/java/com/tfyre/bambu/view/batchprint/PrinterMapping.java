package com.tfyre.bambu.view.batchprint;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.model.AmsSingle;
import com.tfyre.bambu.model.Print;
import com.tfyre.bambu.model.Tray;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.bambu.printer.Filament;
import com.tfyre.bambu.printer.FilamentType;
import com.tfyre.bambu.view.NotificationHelper;
import com.tfyre.ftp.BambuFtp;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.Command;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.net.ftp.FTPFile;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Dependent
public final class PrinterMapping implements FilamentHelper, NotificationHelper {

    @Inject
    BambuConfig config;
    @Inject
    Instance<BambuFtp> clientInstance;

    private final Map<Integer, Integer> amsMapping = new HashMap<>();
    private final Map<Integer, Integer> amsMappingCache = new HashMap<>();
    private final Div filamentMapping = newDiv("filamentmapping");

    private BambuPrinters.PrinterDetail printerDetail;
    private Plate plate;
    private PrinterState printerState = PrinterState.READY;
    private final Span bulkStatus = new Span();
    private long fileSize;
    private double percentageComplete = 0;
    private UI ui;

    public PrinterMapping setup(final UI ui, final BambuPrinters.PrinterDetail printerDetail) {
        this.ui = ui;
        this.printerDetail = printerDetail;
        return this;
    }

    public void updateBulkStatus() {
        final String newText;
        if (printerState == PrinterState.FTP_UPLOADING) {
            newText = "%s: %.2f%%".formatted(printerState.getDescription(), percentageComplete);
        } else {
            newText = printerState.getDescription();
        }
        if (newText.equals(bulkStatus.getText())) {
            return;
        }
        bulkStatus.setText(newText);
    }

    protected void runInUI(final Command command) {
        ui.access(command);
    }

    private void setPrinterState(final PrinterState printerState) {
        this.printerState = printerState;
        runInUI(this::updateBulkStatus);
    }

    public PrinterState getPrinterState() {
        return printerState;
    }

    public String getId() {
        return printerDetail.id();
    }

    private List<Integer> generateAmsMapping() {
        final int max = plate.filaments().stream().mapToInt(PlateFilament::filamentId).max().getAsInt();
        return IntStream.range(0, max)
                .mapToObj(i -> amsMapping.getOrDefault(i + 1, -1))
                .toList();
    }

    private void doBlock(final boolean isBlocked) {
        printerDetail.printer().setBlocked(isBlocked);
    }

    private void doFtp(final ProjectFile projectFile, final boolean skipIfSameSize) throws IOException {
        Log.debugf("%s: doFtp", printerDetail.name());
        final BambuFtp client = clientInstance.get().setup(printerDetail, this::bytesTransferred);
        try {
            Log.debugf("%s: connecting", printerDetail.name());
            setPrinterState(PrinterState.FTP_CONNECT);
            client.doConnect();
            Log.debugf("%s: loggingin", printerDetail.name());
            setPrinterState(PrinterState.FTP_LOGIN);
            client.doLogin();
            if (skipIfSameSize) {
                Log.debugf("%s: checking", printerDetail.name());
                setPrinterState(PrinterState.FTP_SIZE);
                final Optional<FTPFile> oFile = Stream.of(client.listFiles())
                        .filter(file -> file.isFile() && projectFile.getFilename().equals(file.getName()))
                        .findAny();
                if (oFile.isPresent()) {
                    final FTPFile ftpFile = oFile.get();
                    Log.debugf("%s: size local[%d] remote[%d]", printerDetail.name(), projectFile.getFileSize(), ftpFile.getSize());
                    if (projectFile.getFileSize() == ftpFile.getSize()) {
                        return;
                    }
                }
            }
            Log.debugf("%s: uploading", printerDetail.name());
            setPrinterState(PrinterState.FTP_UPLOADING);
            client.doUpload(projectFile.getFilename(), projectFile.getStream());
            Log.debugf("%s: uploaded", printerDetail.name());
        } finally {
            try {
                client.doClose();
            } catch (IOException ex) {
                Log.error(ex.getMessage(), ex);
            }
        }
    }

    public void sendPrint(final ProjectFile projectFile, final BambuPrinter.CommandPPF command, final boolean skipIfSameSize) {
        Log.debugf("%s: sendPrint", printerDetail.name());
        doBlock(true);
        fileSize = projectFile.getFileSize();
        percentageComplete = 0;
        try {
            doFtp(projectFile, skipIfSameSize);
            final List<Integer> mapping = generateAmsMapping();
            final boolean useAms = mapping.stream().noneMatch(i -> i == BambuConst.AMS_TRAY_VIRTUAL);
            final BambuPrinter.CommandPPF _command = new BambuPrinter.CommandPPF(
                    projectFile.getFilename(),
                    plate.plateId(),
                    useAms,
                    command.timelapse(),
                    command.bedLevelling(),
                    command.flowCalibration(),
                    command.vibrationCalibration(),
                    mapping
            );
            printerDetail.printer().commandPrintProjectFile(_command);
            setPrinterState(PrinterState.SENT);
        } catch (Throwable ex) {
            final String error = "%s: %s".formatted(printerDetail.name(), ex.getMessage());
            Log.error(error, ex);
            setPrinterState(PrinterState.ERROR);
            runInUI(() -> showError(error));
        } finally {
            doBlock(false);
        }
    }

    public boolean canPrint() {
        return printerDetail.printer().getGCodeState().isReady() && !printerDetail.printer().isBlocked() && isMapped();
    }

    public boolean isMapped() {
        final boolean result = plate.filaments().stream().allMatch(pf -> amsMapping.containsKey(pf.filamentId()));
        setPrinterState(result ? PrinterState.READY : PrinterState.NEEDS_MAPPING);
        return result;
    }

    private boolean similarColor(final long col1, final long col2) {
        final long r1 = col1 >> 16 & 0xff;
        final long g1 = col1 >> 8 & 0xff;
        final long b1 = col1 & 0xff;
        final long r2 = col2 >> 16 & 0xff;
        final long g2 = col2 >> 8 & 0xff;
        final long b2 = col2 & 0xff;

        final long rmean = (r1 + r2) / 2;
        final long r = Math.abs(r1 - r2);
        final long g = Math.abs(g1 - g2);
        final long b = Math.abs(b1 - b2);
        final double result = Math.sqrt((((512 + rmean) * r * r) >> 8) + 4 * g * g + (((767 - rmean) * b * b) >> 8));
        //Log.infof("#%06X - #%06X = %.2f", col1, col2, result);
        return result < 100.0;
    }

    private void addFilamentMapping(final Div mapped, final PlateFilament plateFilament, final PrinterFilament printerFilament) {
        amsMapping.put(plateFilament.filamentId(), printerFilament.amsTrayId());
        setupFilament(mapped, printerFilament.name(), printerFilament.color());
    }

    private ContextMenu newContextMenu(final Component component) {
        final ContextMenu result = new ContextMenu(component);
        if (config.menuLeftClick()) {
            result.setOpenOnClick(true);
        }
        result.setOverlayClassName("batchprint-view-menu");
        return result;
    }

    private FilamentType mapFilament(final String filamentId) {
        return Filament.getFilament(filamentId).orElse(Filament.UNKNOWN).getType();
    }

    private PrinterFilament newPrinterFilament(final Tray tray, final String name, final int amsId, final int trayId) {
        return new PrinterFilament(name, amsId, trayId,
                mapFilament(tray.getTrayInfoIdx()),
                mapFilamentColor(tray.getTrayColor())
        );
    }

    private PrinterFilament mapTray(final AmsSingle single, final Tray tray, final String printerName) {
        final int amsId = parseInt(printerName, single.getId(), -1);
        final int trayId = parseInt(printerName, tray.getId(), -1);
        return newPrinterFilament(tray, "%s%d".formatted((char) ('A' + amsId), trayId + 1), amsId, trayId);
    }

    private List<PrinterFilament> getPrinterFilaments(final BambuPrinter printer) {
        final Optional<BambuPrinter.Message> message = printer.getFullStatus();
        if (message.isEmpty() || !message.get().message().hasPrint()) {
            return List.of();
        }
        final Print print = message.get().message().getPrint();
        if (print.hasAms() && !print.getAms().getAmsList().isEmpty()) {
            return print.getAms().getAmsList().stream()
                    .flatMap(single -> single.getTrayList().stream()
                            .filter(Tray::hasTrayInfoIdx)
                            .map(tray -> mapTray(single, tray, printer.getName())))
                    .toList();
        }
        if (print.hasVtTray()) {
            return List.of(newPrinterFilament(print.getVtTray(), "Tray", 0, BambuConst.AMS_TRAY_VIRTUAL));
        }
        return List.of();
    }

    private void setupFilamentMapping(final Div mapped, final PlateFilament plateFilament) {
        final ContextMenu menu = newContextMenu(mapped);
        final List<PrinterFilament> list = getPrinterFilaments(printerDetail.printer());
        Optional.ofNullable(amsMappingCache.get(plateFilament.filamentId()))
                .ifPresent(amsTrayId -> {
                    list.stream().filter(pf -> plateFilament.type() == pf.type() && pf.amsTrayId() == amsTrayId)
                            .findFirst()
                            .ifPresent(pf -> addFilamentMapping(mapped, plateFilament, pf));
                });
        list.stream().filter(pf -> plateFilament.type() == pf.type() && similarColor(plateFilament.color(), pf.color()))
                .findFirst()
                .ifPresent(pf -> addFilamentMapping(mapped, plateFilament, pf));
        list.forEach(pf -> {
            final Div ftop = newDiv("filament");
            ftop.addClassName("filamenttop");
            setupFilament(ftop, pf.type().getDescription(), pf.color());

            final Div fbottom = newDiv("filament");
            fbottom.addClassName("filamentbottom");
            setupFilament(fbottom, pf.name(), pf.color());

            menu.addItem(newDiv("myfilament", ftop, fbottom), l -> {
                if (config.batchPrint().enforceFilamentMapping() && plateFilament.type() != pf.type()) {
                    showError("Filament [%s] does not match Printer [%s]".formatted(plateFilament.type(), pf.type()));
                    return;
                }
                addFilamentMapping(mapped, plateFilament, pf);
                isMapped();
            });
        });
        if (menu.getItems().isEmpty()) {
            menu.addItem("No Filaments");
        }
    }

    private void setupPlate() {
        amsMapping.clear();
        filamentMapping.removeAll();
        plate.filaments().forEach(pf -> {
            final Div filament = newFilament(pf);
            filament.addClassName("filamenttop");
            setupFilament(filament, pf.type().getDescription(), pf.color());

            final Div mapped = newDiv("filament");
            mapped.addClassName("filamentbottom");
            setupFilament(mapped, "--\u25BC", 0xffffff);

            setupFilamentMapping(mapped, pf);
            filamentMapping.add(newDiv("mapping", filament, mapped));
        });
    }

    public void setPlate(final Plate plate) {
        this.plate = plate;
        amsMappingCache.clear();
        setupPlate();
        isMapped();
    }

    public void refresh() {
        amsMappingCache.clear();
        amsMappingCache.putAll(amsMapping);
        setupPlate();
        isMapped();
    }

    public BambuPrinters.PrinterDetail getPrinterDetail() {
        return printerDetail;
    }

    public Map<Integer, Integer> getAmsMapping() {
        return amsMapping;
    }

    public Component getBulkStatus() {
        return bulkStatus;
    }

    public Component getFilamentMapping() {
        return filamentMapping;
    }

    private void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
        percentageComplete = 100.0 * totalBytesTransferred / fileSize;
    }

    private record PrinterFilament(String name, int amsId, int trayId, FilamentType type, long color) {

        public int amsTrayId() {
            return amsId * 4 + trayId;
        }

    }

    public enum PrinterState {
        READY("Ready"),
        NEEDS_MAPPING("Needs Filament Mapping"),
        FTP_CONNECT("FTP Connect"),
        FTP_LOGIN("FTP Login"),
        FTP_SIZE("FTP Size"),
        FTP_UPLOADING("FTP Uploading"),
        SENT("Sent"),
        ERROR("Error");

        private final String description;

        private PrinterState(final String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

    }
}
