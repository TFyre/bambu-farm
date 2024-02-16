package com.tfyre.bambu.view;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.YesNoCancelDialog;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.ftp.BambuFtp;
import com.tfyre.ftp.FTPEventListener;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.SucceededEvent;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import io.quarkus.runtime.configuration.MemorySize;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "sdcard", layout = MainLayout.class)
@PageTitle("SD Card")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class SdCardView extends VerticalLayout implements HasUrlParameter<String>, NotificationHelper, GridHelper<FTPFile>, ViewHelper {

    private final String UIERROR = "UI not present";
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

    @Inject
    Logger log;
    @Inject
    BambuPrinters printers;
    @Inject
    ManagedExecutor executor;
    @Inject
    ScheduledExecutorService ses;
    @Inject
    Instance<BambuFtp> clientInstance;
    @Inject
    BambuConfig config;

    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxBodySize;

    private Optional<BambuPrinters.PrinterDetail> _printer = Optional.empty();

    private final ComboBox<BambuPrinters.PrinterDetail> comboBox = new ComboBox<>();
    private final Grid<FTPFile> grid = new Grid<>();
    private final TextField path = new TextField("", BambuConst.PATHSEP, l -> runCallable(this::doPath));
    private final Button connect = new Button("Connect", new Icon(VaadinIcon.CONNECT), l -> doConnect());
    private final Button disconnect = new Button("Disconnect", new Icon(VaadinIcon.CLOSE), l -> doDisconnect());
    private final Button cdup = new Button("", new Icon(VaadinIcon.ARROW_BACKWARD), l -> doCDUP());
    private final Button refresh = new Button("Refresh", new Icon(VaadinIcon.REFRESH), l -> runCallable(this::doRefresh));
    private final ProgressBar progressBar = newProgressBar();
    private final MemoryBuffer buffer = new MemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private BambuFtp client;
    private double percentageComplete;
    private long fileSize;
    private Optional<UI> ui = Optional.empty();

    private void showProgressBar(final boolean visible) {
        percentageComplete = 0;
        progressBar.setValue(percentageComplete);
        progressBar.setVisible(visible);
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public Grid<FTPFile> getGrid() {
        return grid;
    }

    @Override
    public void setParameter(final BeforeEvent event, @OptionalParameter final String printerName) {
        _printer = printers.getPrinterDetail(printerName);
    }

    private void runInUI(final Runnable runnable) {
        if (ui.isEmpty()) {
            log.error(UIERROR, new Exception(UIERROR));
            return;
        }
        ui.get().access(runnable::run);
    }

    private void runCallable(final Callable<Boolean> callable) {
        executor.submit(() -> {
            try {
                callable.call();
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
                runInUI(() -> {
                    showError(ex.getMessage());
                });
            }
        });
    }

    private void disconnect() {
        if (client == null) {
            return;
        }
        final BambuFtp _client = client;
        client = null;
        if (!_client.isConnected()) {
            return;
        }
        grid.setItems(List.of());
        runCallable(() -> {
            _client.doClose();
            return true;
        });
    }

    private void setConnectDisconnect(final boolean canConnect) {
        connect.setEnabled(canConnect);
        disconnect.setEnabled(!canConnect);
        path.setEnabled(!canConnect);
        cdup.setEnabled(!canConnect);
        refresh.setEnabled(!canConnect);
        upload.setVisible(!canConnect);
    }

    private void buildList(final BambuPrinters.PrinterDetail printer) {
        disconnect();
        client = clientInstance.get().setup(printer, this::bytesTransferred);
        setConnectDisconnect(true);
    }

    private void doConnect() {
        connect.setEnabled(false);
        runCallable(() -> {
            client.doConnect();
            if (!client.doLogin()) {
                runInUI(() -> showError("Login Failed"));
                return true;
            }
            runInUI(() -> setConnectDisconnect(false));
            doPath();
            return true;
        });
    }

    private void doDisconnect() {
        disconnect.setEnabled(false);
        grid.setItems(List.of());
        setConnectDisconnect(true);
        runCallable(() -> {
            client.doClose();
            return true;
        });
    }

    private boolean doPath() throws IOException {
        final String value = path.getValue();
        if (value == null || value.isEmpty()) {
            runInUI(() -> path.setValue(BambuConst.PATHSEP));
            return false;
        }
        if (!client.isConnected()) {
            return false;
        }
        if (!client.changeWorkingDirectory(value)) {
            runInUI(() -> showError("Change Directory Failed"));
            return false;
        }
        final List<FTPFile> files = Arrays.asList(client.listFiles());
        runInUI(() -> {
            grid.setItems(files);
            if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
                showError(client.getReplyString());
            }
        });
        return true;
    }

    private void doCDUP() {
        final int pos = path.getValue().lastIndexOf(BambuConst.PATHSEP);
        if (pos == -1) {
            path.setValue(BambuConst.PATHSEP);
            return;
        }
        final String value = path.getValue().substring(0, pos).trim();
        path.setValue(value.isEmpty() ? BambuConst.PATHSEP : value);
    }

    private Component buildToolbar() {
        comboBox.setItemLabelGenerator(BambuPrinters.PrinterDetail::name);
        comboBox.setItems(printers.getPrintersDetail().stream().sorted(Comparator.comparing(BambuPrinters.PrinterDetail::name)).toList());
        comboBox.addValueChangeListener(l -> buildList(l.getValue()));
        setConnectDisconnect(true);
        connect.setEnabled(false);
        upload.setAcceptedFileTypes(BambuConst.EXT.toArray(String[]::new));
        upload.addSucceededListener(this::doUpload);
        upload.setMaxFileSize((int) maxBodySize.asLongValue());
        upload.setDropLabel(new Span("Drop file here (max size: %dM)".formatted(maxBodySize.asLongValue() / 1_000_000)));
        upload.addFileRejectedListener(l -> {
            showError(l.getErrorMessage());
        });
        final HorizontalLayout result = new HorizontalLayout(new Span("Printers"), comboBox, connect, disconnect, new Span("Path"),
                path, cdup, refresh, upload
        );
        result.setWidthFull();
        result.setAlignItems(Alignment.CENTER);
        result.setMinHeight(80, Unit.PIXELS);
        return result;
    }

    private void updateProgressBar() {
        if (!progressBar.isVisible()) {
            return;
        }

        runInUI(() -> progressBar.setValue(percentageComplete));
    }

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        ui = Optional.of(attachEvent.getUI());
        addClassName("sdcard-view");
        setSizeFull();
        configureGrid();
        showProgressBar(false);
        add(buildToolbar(), progressBar, grid);
        _printer.ifPresent(comboBox::setValue);
        ses.scheduleAtFixedRate(this::updateProgressBar, 0, config.refreshInterval().getSeconds(), TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(final DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        disconnect();
    }

    private ComponentRenderer<Icon, FTPFile> getTypeRender() {
        return new ComponentRenderer<>(file -> {
            final VaadinIcon icon;
            if (file.isDirectory()) {
                icon = VaadinIcon.FOLDER;
            } else if (file.isFile()) {
                icon = VaadinIcon.FILE;
            } else if (file.isSymbolicLink()) {
                icon = VaadinIcon.LINK;
            } else {
                icon = VaadinIcon.QUESTION;
            }
            return new Icon(icon);
        });
    }

    private Anchor getDownloadLink(final FTPFile file) {
        final String fileName = file.getName();
        final StreamResource stream = new StreamResource(fileName, () -> {
            try {
                fileSize = file.getSize();
                runInUI(() -> showProgressBar(true));

                client.setFileType(FTP.BINARY_FILE_TYPE);
                try (final InputStream s = client.retrieveFileStream(file.getName())) {
                    return new ByteArrayInputStream(s.readAllBytes());
                    //return new BufferedInputStream(s);
                } finally {
                    if (!client.completePendingCommand()) {
                        log.error("could not complete pending command");
                    }
                    runInUI(() -> showProgressBar(false));
                }
            } catch (IOException ex) {
                log.errorf(ex, "Cannot find file: %s - %s", file.getName(), ex.getMessage());
            }
            return null;
        });
        final Anchor anchor = new Anchor();
        anchor.setHref(stream);
        anchor.getElement().setAttribute("download", true);
        anchor.add(new Button(new Icon(VaadinIcon.DOWNLOAD)));
        return anchor;
    }

    private Component getComponentColumn(final FTPFile file) {
        final HorizontalLayout result = new HorizontalLayout();
        if (file.isDirectory()) {
            result.add(new Button(new Icon(VaadinIcon.FOLDER_OPEN), l -> doDoubleClick(file)));
            result.add(new Button(new Icon(VaadinIcon.FOLDER_REMOVE), l -> doRemoveFile(file)));
        }
        if (file.isFile()) {
            if (BambuConst.EXT.stream().anyMatch(ext -> file.getName().endsWith(ext))) {
                result.add(new Button(new Icon(VaadinIcon.PRINT), l -> doPrintFile(file)));
            }
            result.add(getDownloadLink(file));
            result.add(new Button(new Icon(VaadinIcon.FILE_REMOVE), l -> doRemoveFile(file)));
        }
        return result;
    }

    private void configureGrid() {
        setupColumn("Type", getTypeRender());
        setupColumn("Name", f -> f.getName());

        setupColumn("Size", f -> f.getSize())
                .setSortable(true).setComparator(FTPFile::getSize);
        final Grid.Column<FTPFile> coldDate
                = setupColumn("Date", f -> DTF.format(f.getTimestampInstant().atOffset(ZoneOffset.UTC)))
                        .setSortable(true).setComparator(FTPFile::getTimestampInstant);

        grid.addComponentColumn(this::getComponentColumn);
        grid.addItemDoubleClickListener(l -> doDoubleClick(l.getItem()));
        grid.sort(GridSortOrder.desc(coldDate).build());
    }

    private String buildFileName(final String fileName) {
        final StringBuilder sb = new StringBuilder(path.getValue());
        if (!path.getValue().endsWith(BambuConst.PATHSEP)) {
            sb.append(BambuConst.PATHSEP);
        }
        sb.append(fileName.startsWith(BambuConst.PATHSEP) ? fileName.substring(1) : fileName);
        return sb.toString();
    }

    private void doDoubleClick(final FTPFile item) {
        if (!item.isDirectory()) {
            return;
        }
        path.setValue(buildFileName(item.getName()));
    }

    private boolean doRefresh() throws IOException {
        return doPath();
    }

    private void doUpload(final SucceededEvent event) {
        fileSize = event.getContentLength();
        showProgressBar(true);

        final InputStream inputStream = buffer.getInputStream();
        showNotification("Uploading to Printer");
        runCallable(() -> {
            client.doUpload(event.getFileName(), inputStream);
            runInUI(() -> {
                showProgressBar(false);
                showNotification("Uploaded: %s".formatted(event.getFileName()));
            });
            doRefresh();
            return true;
        });
    }

    private void doRemoveFile(final FTPFile file) {
        YesNoCancelDialog.show("Confirm to delete: %s".formatted(file.getName()), ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            runCallable(() -> {
                final boolean ok;
                if (file.isDirectory()) {
                    ok = client.removeDirectory(file.getName());
                } else if (file.isFile()) {
                    ok = client.deleteFile(file.getName());
                } else {
                    ok = true;
                }

                if (!ok) {
                    runInUI(() -> showError("Delete Failed"));
                }
                doRefresh();
                return true;
            });
        });
    }

    private void doPrintFile(final FTPFile file) {
        final IntegerField plateId = new IntegerField("Plate Id");
        plateId.setMin(1);
        plateId.setMax(20);
        plateId.setStepButtonsVisible(true);
        plateId.setValue(1);
        final Checkbox useAMS = new Checkbox("Use AMS", comboBox.getValue().config().useAms());
        final Checkbox timelapse = new Checkbox("Timelapse", comboBox.getValue().config().timelapse());
        final Checkbox bedLevelling = new Checkbox("Bed Levelling", comboBox.getValue().config().bedLevelling());

        final String fileName = buildFileName(file.getName());
        final boolean is3mf = fileName.endsWith(BambuConst.FILE_3MF);

        final List<Component> list;
        if (is3mf) {
            list = List.of(plateId, useAMS, timelapse, bedLevelling);

        } else {
            list = List.of(useAMS, timelapse, bedLevelling);
        }

        YesNoCancelDialog.show(list, "Confirm to print: %s".formatted(file.getName()), ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            if (fileName.endsWith(BambuConst.FILE_GCODE)) {
                comboBox.getValue().printer().commandPrintGCodeFile(fileName);
            } else if (is3mf) {
                comboBox.getValue().printer().commandPrintProjectFile(fileName, plateId.getValue(),
                        useAMS.getValue(), timelapse.getValue(), bedLevelling.getValue(),
                        List.of());
            } else {
                showError("Unknown File: %s".formatted(fileName));
            }
        });
    }

    private void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
        percentageComplete = 100.0 * totalBytesTransferred / fileSize;
    }

}
