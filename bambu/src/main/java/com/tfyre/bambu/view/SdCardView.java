package com.tfyre.bambu.view;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.YesNoCancelDialog;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinters;
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
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "sdcard", layout = MainLayout.class)
@PageTitle("SD Card")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class SdCardView extends VerticalLayout implements HasUrlParameter<String>, ShowInterface, GridHelper<FTPFile> {

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
    Instance<FTPSClient> ftpsClientInstance;

    private Optional<BambuPrinters.PrinterDetail> _printer = Optional.empty();

    private final ComboBox<BambuPrinters.PrinterDetail> comboBox = new ComboBox<>();
    private final Grid<FTPFile> grid = new Grid<>();
    private final TextField path = new TextField("", BambuConst.PATHSEP, l -> doPath(l.getValue()));
    private final Button connect = new Button("Connect", new Icon(VaadinIcon.CONNECT), l -> doConnect());
    private final Button disconnect = new Button("Disconnect", new Icon(VaadinIcon.CLOSE), l -> doDisconnect());
    private final Button cdup = new Button("", new Icon(VaadinIcon.ARROW_BACKWARD), l -> doCDUP());
    private final Button refresh = new Button("Refresh", new Icon(VaadinIcon.REFRESH), l -> doRefresh());
    private final MemoryBuffer buffer = new MemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private FTPSClient client;

    @Override
    public Grid<FTPFile> getGrid() {
        return grid;
    }

    @Override
    public void setParameter(final BeforeEvent event, @OptionalParameter final String printerName) {
        _printer = printers.getPrinterDetail(printerName);
    }

    private void runCallable(final Callable<Boolean> callable) {
        final Optional<UI> ui = getUI();
        executor.submit(() -> {
            try {
                callable.call();
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
                ui.get().access(() -> {
                    showError(ex.getMessage());
                });
            }
        });
    }

    private void disconnect() {
        if (client == null) {
            return;
        }
        final FTPSClient _client = client;
        client = null;
        if (!_client.isConnected()) {
            return;
        }
        grid.setItems(List.of());
        runCallable(() -> {
            _client.quit();
            _client.disconnect();
            return true;
        });
    }

    private ProtocolCommandListener getListener(final String name) {
        return new ProtocolCommandListener() {

            private void log(ProtocolCommandEvent event) {
                log.infof("%s: command[%s] message[%s]", name, event.getCommand(), event.getMessage().trim());
            }

            @Override
            public void protocolCommandSent(ProtocolCommandEvent event) {
                log(event);
            }

            @Override
            public void protocolReplyReceived(ProtocolCommandEvent event) {
                log(event);
            }
        };
    }

    private FTPSClient getFtpsClient(final BambuPrinters.PrinterDetail printer) {
        final FTPSClient result = ftpsClientInstance.get();
        if (printer.config().ftp().logCommands()) {
            result.addProtocolCommandListener(getListener(printer.name()));
        }
        result.setUseEPSVwithIPv4(true);
        //sent: USER bblp
        //recv: 331
        //org.apache.commons.net.MalformedServerReplyException: Truncated server reply: '331 '
        //at org.apache.commons.net.ftp.FTP.getReply(FTP.java:609)
        result.setStrictReplyParsing(false);
        return result;
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
        client = getFtpsClient(printer);
        setConnectDisconnect(true);
    }

    private URI getURI(final BambuConfig.Printer config) {
        return URI.create(config.ftp().url().orElseGet(() -> "ftps://%s:%d".formatted(config.ip(), config.ftp().port())));
    }

    private void doConnect() {
        connect.setEnabled(false);
        final Optional<UI> ui = getUI();
        final BambuConfig.Printer config = comboBox.getValue().config();
        final URI uri = getURI(config);
        runCallable(() -> {
            if (!client.isConnected()) {
                client.connect(uri.getHost(), uri.getPort());
            }
            if (!client.login(config.username(), config.accessCode())) {
                ui.get().access(() -> showError("Login Failed"));
                return true;
            }
            client.execPROT("P");
            client.enterLocalPassiveMode();
            ui.get().access(() -> setConnectDisconnect(false));
            doPath(path.getValue());
            return true;
        });
    }

    private void doDisconnect() {
        disconnect.setEnabled(false);
        grid.setItems(List.of());
        final Optional<UI> ui = getUI();
        runCallable(() -> {
            client.quit();
            client.disconnect();
            ui.get().access(() -> setConnectDisconnect(true));
            return true;
        });
    }

    private void doPath(final String value) {
        if (value == null || value.isEmpty()) {
            path.setValue(BambuConst.PATHSEP);
            return;
        }
        if (!client.isConnected()) {
            return;
        }
        final Optional<UI> ui = getUI();
        runCallable(() -> {
            if (!client.changeWorkingDirectory(value)) {
                ui.get().access(() -> showError("Change Directory Failed"));
                return false;
            }
            final List<FTPFile> files = Arrays.asList(client.listFiles());
            ui.get().access(() -> grid.setItems(files));
            return true;
        });
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
        final HorizontalLayout result = new HorizontalLayout(new Span("Printers"), comboBox, connect, disconnect, new Span("Path"),
                path, cdup, refresh, upload
        );
        result.setWidthFull();
        result.setAlignItems(Alignment.CENTER);
        result.setMinHeight(80, Unit.PIXELS);
        return result;
    }

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        addClassName("sdcard-view");
        setSizeFull();
        configureGrid();
        add(buildToolbar(), grid);
        _printer.ifPresent(this::buildList);
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
                client.setFileType(FTP.BINARY_FILE_TYPE);
                return new BufferedInputStream(client.retrieveFileStream(file.getName()));
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

    private void doRefresh() {
        doPath(path.getValue());
    }

    private void doUpload(final SucceededEvent event) {
        final Optional<UI> ui = getUI();
        final InputStream inputStream = buffer.getInputStream();
        showNotification("Uploading to Printer");
        runCallable(() -> {
            client.setFileType(FTP.BINARY_FILE_TYPE);
            client.storeFile(event.getFileName(), inputStream);
            ui.get().access(() -> showNotification("Uploaded: %s".formatted(event.getFileName())));
            doRefresh();
            return true;
        });
    }

    private void doRemoveFile(final FTPFile file) {
        YesNoCancelDialog.show("Confirm to delete: %s".formatted(file.getName()), ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            final Optional<UI> ui = getUI();
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
                    ui.get().access(() -> showError("Delete Failed"));
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
        YesNoCancelDialog.show(List.of(plateId, useAMS, timelapse, bedLevelling), "Confirm to print: %s".formatted(file.getName()), ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            final String fileName = buildFileName(file.getName());
            if (fileName.endsWith(BambuConst.FILE_GCODE)) {
                comboBox.getValue().printer().commandPrintGCodeFile(fileName);
            } else if (fileName.endsWith(BambuConst.FILE_3MF)) {
                comboBox.getValue().printer().commandPrintProjectFile(fileName, plateId.getValue(), useAMS.getValue(), timelapse.getValue(), bedLevelling.getValue());
            } else {
                showError("Unknown File: %s".formatted(fileName));
            }
        });
    }

}
