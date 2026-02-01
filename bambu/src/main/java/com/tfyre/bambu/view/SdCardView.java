package com.tfyre.bambu.view;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.YesNoCancelDialog;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.ftp.BambuFtp;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
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
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.StreamResource;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.MemorySize;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

/**
 * SD Card View with Bulk Operations Support
 * 
 * Features:
 * - Bulk file download
 * - Bulk file deletion
 * - SD card wipe functionality
 * - Individual file operations (existing)
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "sdcard", layout = MainLayout.class)
@PageTitle("SD Card")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public final class SdCardView extends PushDiv implements HasUrlParameter<String>, GridHelper<FTPFile>, ViewHelper {

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
    BambuPrinters printers;
    @Inject
    ManagedExecutor executor;
    @Inject
    Instance<BambuFtp> clientInstance;
    @Inject
    BambuConfig config;

    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxBodySize;

    private Optional<BambuPrinters.PrinterDetail> _printer = Optional.empty();

    private final ComboBox<BambuPrinters.PrinterDetail> comboBox = new ComboBox<>();
    private final Grid<FTPFile> grid = new MyGrid<>();
    private final TextField path = new TextField("", BambuConst.PATHSEP, l -> runCallable(this::doPath));
    private final Button connect = new Button("Connect", new Icon(VaadinIcon.CONNECT), l -> doConnect());
    private final Button disconnect = new Button("Disconnect", new Icon(VaadinIcon.CLOSE), l -> doDisconnect());
    private final Button cdup = new Button("", new Icon(VaadinIcon.ARROW_BACKWARD), l -> doCDUP());
    private final Button refresh = new Button("Refresh", new Icon(VaadinIcon.REFRESH), l -> runCallable(this::doRefresh));
    
    // New bulk operation buttons
    private final Button bulkDownload = new Button("Download Selected", new Icon(VaadinIcon.DOWNLOAD_ALT), l -> doBulkDownload());
    private final Button bulkDelete = new Button("Delete Selected", new Icon(VaadinIcon.TRASH), l -> doBulkDelete());
    private final Button wipeSD = new Button("Wipe SD Card", new Icon(VaadinIcon.ERASER), l -> doWipeSD());
    
    private final ProgressBar progressBar = newProgressBar();
    private final Span statusLabel = new Span();
    private final MemoryBuffer buffer = new MemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private BambuFtp client;
    private double percentageComplete;
    private long fileSize;
    private UI ui;

    private final NotificationHelper nh = new NotificationHelper() {
    };

    private void showProgressBar(final boolean visible) {
        percentageComplete = 0;
        progressBar.setValue(percentageComplete);
        progressBar.setVisible(visible);
        statusLabel.setVisible(visible);
    }

    @Override
    public Grid<FTPFile> getGrid() {
        return grid;
    }

    @Override
    public void setParameter(final BeforeEvent event, @OptionalParameter final String printerName) {
        _printer = printers.getPrinterDetail(printerName);
    }

    private void runInUI(final Command command) {
        ui.access(command);
    }

    private void runCallable(final Callable callable) {
        executor.submit(() -> {
            try {
                callable.run();
            } catch (Exception ex) {
                Log.error(ex.getMessage(), ex);
                if (ex.getCause() != null) {
                    Log.error(ex.getCause().getMessage(), ex.getCause());
                }
                runInUI(() -> nh.showError(ex.getMessage()));
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
        runCallable(_client::doClose);
    }

    private void setConnectDisconnect(final boolean canConnect) {
        connect.setEnabled(canConnect);
        disconnect.setEnabled(!canConnect);
        path.setEnabled(!canConnect);
        cdup.setEnabled(!canConnect);
        refresh.setEnabled(!canConnect);
        upload.setVisible(!canConnect);
        
        // Bulk operation buttons
        bulkDownload.setEnabled(!canConnect);
        bulkDelete.setEnabled(!canConnect);
        wipeSD.setEnabled(!canConnect);
        
        updateBulkButtonStates();
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
                runInUI(() -> nh.showError("Login Failed"));
            }
            runInUI(() -> setConnectDisconnect(false));
            doPath();
        });
    }

    private void doDisconnect() {
        disconnect.setEnabled(false);
        grid.setItems(List.of());
        setConnectDisconnect(true);
        runCallable(client::doClose);
    }

    private void doPath() throws IOException {
        final String value = path.getValue();
        if (value == null || value.isEmpty()) {
            runInUI(() -> path.setValue(BambuConst.PATHSEP));
            return;
        }
        if (!client.isConnected()) {
            return;
        }
        if (!client.changeWorkingDirectory(value)) {
            runInUI(() -> nh.showError("Change Directory Failed"));
            return;
        }
        final List<FTPFile> files = Arrays.asList(client.listFiles());
        runInUI(() -> {
            grid.setItems(files);
            if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
                nh.showError(client.getReplyString());
            }
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
        upload.setMaxFileSize((int) maxBodySize.asLongValue());
        upload.setDropLabel(new Span("Drop file here (max size: %dM)".formatted(maxBodySize.asLongValue() / 1_000_000)));
        upload.addFileRejectedListener(l -> {
            nh.showError(l.getErrorMessage());
        });
        
        // Style bulk operation buttons
        bulkDelete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        wipeSD.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        bulkDownload.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        final HorizontalLayout topRow = new HorizontalLayout(
            new Span("Printers"), comboBox, connect, disconnect, 
            new Span("Path"), path, cdup, refresh, upload
        );
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.CENTER);
        
        final HorizontalLayout bulkOpsRow = new HorizontalLayout(
            new Span("Bulk Operations:"), bulkDownload, bulkDelete, wipeSD
        );
        bulkOpsRow.setAlignItems(Alignment.CENTER);
        
        final VerticalLayout result = new VerticalLayout(topRow, bulkOpsRow);
        result.setWidthFull();
        result.setPadding(true);
        result.setSpacing(true);
        
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
        ui = attachEvent.getUI();
        addClassName("sdcard-view");
        configureGrid();
        showProgressBar(false);
        
        statusLabel.getStyle().set("margin-left", "10px");
        final HorizontalLayout progressLayout = new HorizontalLayout(progressBar, statusLabel);
        progressLayout.setAlignItems(Alignment.CENTER);
        progressLayout.setWidthFull();
        
        add(buildToolbar(), progressLayout, grid);
        _printer.ifPresent(comboBox::setValue);
        createFuture(this::updateProgressBar, config.refreshInterval());
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
                runInUI(() -> {
                    showProgressBar(true);
                    statusLabel.setText("Downloading: " + fileName);
                });

                client.setFileType(FTP.BINARY_FILE_TYPE);
                try (final InputStream s = client.retrieveFileStream(file.getName())) {
                    return new ByteArrayInputStream(s.readAllBytes());
                } finally {
                    if (!client.completePendingCommand()) {
                        Log.error("could not complete pending command");
                    }
                    runInUI(() -> showProgressBar(false));
                }
            } catch (IOException ex) {
                Log.errorf(ex, "Cannot find file: %s - %s", file.getName(), ex.getMessage());
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
        // Enable multi-selection for bulk operations
        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addSelectionListener(this::onSelectionChanged);
        
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

    private void onSelectionChanged(SelectionEvent<Grid<FTPFile>, FTPFile> event) {
        updateBulkButtonStates();
    }

    private void updateBulkButtonStates() {
        Set<FTPFile> selected = grid.getSelectedItems();
        boolean hasSelection = !selected.isEmpty();
        boolean hasFiles = selected.stream().anyMatch(FTPFile::isFile);
        
        bulkDownload.setEnabled(hasFiles && client != null && client.isConnected());
        bulkDelete.setEnabled(hasSelection && client != null && client.isConnected());
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

    private void doRefresh() throws IOException {
        doPath();
    }

    private void doUpload(final SucceededEvent event) {
        fileSize = event.getContentLength();
        showProgressBar(true);
        statusLabel.setText("Uploading: " + event.getFileName());

        final InputStream inputStream = buffer.getInputStream();
        nh.showNotification("Uploading to Printer");
        runCallable(() -> {
            client.doUpload(event.getFileName(), inputStream);
            runInUI(() -> {
                showProgressBar(false);
                nh.showNotification("Uploaded: %s".formatted(event.getFileName()));
            });
            doRefresh();
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
                    runInUI(() -> nh.showError("Delete Failed"));
                }
                doRefresh();
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
        final Checkbox flowCalibration = new Checkbox("Flow Calibration", comboBox.getValue().config().flowCalibration());
        final Checkbox vibrationCalibration = new Checkbox("Vibration Calibration", comboBox.getValue().config().vibrationCalibration());

        final String fileName = buildFileName(file.getName());
        final boolean is3mf = fileName.endsWith(BambuConst.FILE_3MF);

        final List<Component> list;
        if (is3mf) {
            list = List.of(plateId, useAMS, timelapse, bedLevelling, flowCalibration, vibrationCalibration);
        } else {
            list = List.of(useAMS, timelapse, bedLevelling, flowCalibration, vibrationCalibration);
        }

        YesNoCancelDialog.show(list, "Confirm to print: %s".formatted(file.getName()), ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            if (fileName.endsWith(BambuConst.FILE_GCODE)) {
                comboBox.getValue().printer().commandPrintGCodeFile(fileName);
            } else if (is3mf) {
                comboBox.getValue().printer().commandPrintProjectFile(
                        new BambuPrinter.CommandPPF(
                                fileName, plateId.getValue(),
                                useAMS.getValue(), timelapse.getValue(), bedLevelling.getValue(),
                                flowCalibration.getValue(), vibrationCalibration.getValue(),
                                List.of()));
            } else {
                nh.showError("Unknown File: %s".formatted(fileName));
            }
        });
    }

    // ==================== BULK OPERATIONS ====================

    /**
     * Bulk download selected files from the printer.
     */
    private void doBulkDownload() {
        Set<FTPFile> selectedFiles = grid.getSelectedItems().stream()
                .filter(FTPFile::isFile)
                .collect(Collectors.toSet());
        
        if (selectedFiles.isEmpty()) {
            nh.showError("No files selected");
            return;
        }

        // Create download dialog
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Bulk Download");
        
        TextField downloadPathField = new TextField("Download Directory");
        downloadPathField.setValue(System.getProperty("user.home") + "/bambu-downloads");
        downloadPathField.setWidthFull();
        
        Span fileCount = new Span("Files to download: " + selectedFiles.size());
        Div fileList = new Div();
        selectedFiles.forEach(f -> {
            Div fileItem = new Div(new Span("• " + f.getName() + " (" + formatSize(f.getSize()) + ")"));
            fileList.add(fileItem);
        });
        
        Button downloadButton = new Button("Download", e -> {
            dialog.close();
            executeBulkDownload(selectedFiles, downloadPathField.getValue());
        });
        downloadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        VerticalLayout layout = new VerticalLayout(
            downloadPathField,
            fileCount,
            fileList,
            new HorizontalLayout(downloadButton, cancelButton)
        );
        
        dialog.add(layout);
        dialog.open();
    }

    private void executeBulkDownload(Set<FTPFile> files, String downloadPath) {
        runInUI(() -> {
            showProgressBar(true);
            statusLabel.setText("Preparing bulk download...");
        });
        
        runCallable(() -> {
            Path localDir = Paths.get(downloadPath);
            if (!Files.exists(localDir)) {
                Files.createDirectories(localDir);
            }
            
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();
            
            client.setFileType(FTP.BINARY_FILE_TYPE);
            
            for (FTPFile file : files) {
                try {
                    fileSize = file.getSize();
                    runInUI(() -> statusLabel.setText("Downloading: " + file.getName()));
                    
                    Path localPath = localDir.resolve(file.getName());
                    try (OutputStream outputStream = Files.newOutputStream(localPath)) {
                        boolean success = client.retrieveFile(file.getName(), outputStream);
                        if (success) {
                            successCount++;
                            Log.infof("Downloaded: %s", file.getName());
                        } else {
                            failCount++;
                            errors.add(file.getName() + ": Download returned false");
                        }
                    }
                } catch (IOException e) {
                    failCount++;
                    errors.add(file.getName() + ": " + e.getMessage());
                    Log.errorf(e, "Error downloading: %s", file.getName());
                }
            }
            
            final int finalSuccess = successCount;
            final int finalFail = failCount;
            final String errorMsg = errors.isEmpty() ? "" : "\nErrors:\n" + String.join("\n", errors);
            
            runInUI(() -> {
                showProgressBar(false);
                grid.deselectAll();
                
                String message = String.format("Bulk Download Complete\nSucceeded: %d\nFailed: %d%s", 
                    finalSuccess, finalFail, errorMsg);
                
                if (finalFail == 0) {
                    nh.showNotification(message);
                } else {
                    showResultDialog("Bulk Download Results", message, finalSuccess, finalFail);
                }
            });
        });
    }

    /**
     * Bulk delete selected files/directories from the printer.
     */
    private void doBulkDelete() {
        Set<FTPFile> selectedItems = grid.getSelectedItems();
        
        if (selectedItems.isEmpty()) {
            nh.showError("No items selected");
            return;
        }

        Div confirmContent = new Div();
        confirmContent.add(new Span("Are you sure you want to delete " + selectedItems.size() + " item(s)?"));
        
        Div itemList = new Div();
        selectedItems.forEach(f -> {
            String type = f.isDirectory() ? "[DIR]" : "[FILE]";
            itemList.add(new Div(new Span(type + " " + f.getName())));
        });
        confirmContent.add(itemList);
        
        YesNoCancelDialog.show(List.of(confirmContent), "Confirm Bulk Delete", ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            executeBulkDelete(selectedItems);
        });
    }

    private void executeBulkDelete(Set<FTPFile> items) {
        runInUI(() -> {
            showProgressBar(true);
            statusLabel.setText("Deleting files...");
        });
        
        runCallable(() -> {
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (FTPFile item : items) {
                try {
                    runInUI(() -> statusLabel.setText("Deleting: " + item.getName()));
                    
                    boolean success;
                    if (item.isDirectory()) {
                        success = client.removeDirectory(item.getName());
                    } else if (item.isFile()) {
                        success = client.deleteFile(item.getName());
                    } else {
                        success = true; // Skip unknown types
                    }
                    
                    if (success) {
                        successCount++;
                        Log.infof("Deleted: %s", item.getName());
                    } else {
                        failCount++;
                        errors.add(item.getName() + ": Delete returned false");
                    }
                } catch (IOException e) {
                    failCount++;
                    errors.add(item.getName() + ": " + e.getMessage());
                    Log.errorf(e, "Error deleting: %s", item.getName());
                }
            }
            
            final int finalSuccess = successCount;
            final int finalFail = failCount;
            final String errorMsg = errors.isEmpty() ? "" : "\nErrors:\n" + String.join("\n", errors);
            
            runInUI(() -> {
                showProgressBar(false);
                grid.deselectAll();
                
                String message = String.format("Bulk Delete Complete\nSucceeded: %d\nFailed: %d%s", 
                    finalSuccess, finalFail, errorMsg);
                
                if (finalFail == 0) {
                    nh.showNotification(message);
                } else {
                    showResultDialog("Bulk Delete Results", message, finalSuccess, finalFail);
                }
            });
            
            doRefresh();
        });
    }

    /**
     * Wipe all files from the SD card.
     * WARNING: This is a destructive operation!
     */
    private void doWipeSD() {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("⚠️ WARNING: Wipe SD Card");
        
        VerticalLayout layout = new VerticalLayout();
        layout.add(new H3("This will delete ALL files in the current directory!"));
        layout.add(new Span("Current path: " + path.getValue()));
        
        Checkbox recursiveCheckbox = new Checkbox("Include subdirectories (recursive delete)", false);
        recursiveCheckbox.getStyle().set("color", "red");
        layout.add(recursiveCheckbox);
        
        Checkbox confirmCheckbox = new Checkbox("I understand this action cannot be undone", false);
        layout.add(confirmCheckbox);
        
        Button wipeButton = new Button("WIPE SD CARD", e -> {
            if (!confirmCheckbox.getValue()) {
                nh.showError("Please confirm you understand this action");
                return;
            }
            confirmDialog.close();
            executeWipeSD(recursiveCheckbox.getValue());
        });
        wipeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        wipeButton.setEnabled(false);
        
        confirmCheckbox.addValueChangeListener(e -> wipeButton.setEnabled(e.getValue()));
        
        Button cancelButton = new Button("Cancel", e -> confirmDialog.close());
        
        layout.add(new HorizontalLayout(wipeButton, cancelButton));
        confirmDialog.add(layout);
        confirmDialog.open();
    }

    private void executeWipeSD(boolean recursive) {
        runInUI(() -> {
            showProgressBar(true);
            statusLabel.setText("Wiping SD card...");
        });
        
        runCallable(() -> {
            List<String> allFiles = new ArrayList<>();
            String currentPath = path.getValue();
            
            // List all files
            if (recursive) {
                allFiles.addAll(listFilesRecursive(currentPath));
            } else {
                FTPFile[] files = client.listFiles(currentPath);
                for (FTPFile file : files) {
                    if (file.isFile()) {
                        allFiles.add(file.getName());
                    }
                }
            }
            
            if (allFiles.isEmpty()) {
                runInUI(() -> {
                    showProgressBar(false);
                    nh.showNotification("No files found to delete");
                });
                return;
            }
            
            Log.warnf("WIPING SD CARD: %d files at path %s (recursive: %s)", 
                allFiles.size(), currentPath, recursive);
            
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (String fileName : allFiles) {
                try {
                    runInUI(() -> statusLabel.setText("Deleting: " + fileName));
                    
                    boolean success = client.deleteFile(fileName);
                    if (success) {
                        successCount++;
                    } else {
                        failCount++;
                        errors.add(fileName + ": Delete returned false");
                    }
                } catch (IOException e) {
                    failCount++;
                    errors.add(fileName + ": " + e.getMessage());
                    Log.errorf(e, "Error deleting: %s", fileName);
                }
            }
            
            final int finalSuccess = successCount;
            final int finalFail = failCount;
            final String errorMsg = errors.isEmpty() ? "" : "\nErrors:\n" + String.join("\n", errors.subList(0, Math.min(10, errors.size())));
            
            runInUI(() -> {
                showProgressBar(false);
                
                String message = String.format("SD Card Wipe Complete\nDeleted: %d files\nFailed: %d%s", 
                    finalSuccess, finalFail, finalFail > 10 ? errorMsg + "\n... and more" : errorMsg);
                
                showResultDialog("SD Card Wipe Results", message, finalSuccess, finalFail);
            });
            
            doRefresh();
        });
    }

    /**
     * Recursively list all files in a directory.
     */
    private List<String> listFilesRecursive(String remotePath) throws IOException {
        List<String> allFiles = new ArrayList<>();
        listFilesRecursiveHelper(remotePath, allFiles);
        return allFiles;
    }

    private void listFilesRecursiveHelper(String remotePath, List<String> accumulator) throws IOException {
        FTPFile[] files = client.listFiles(remotePath);
        
        if (files == null) {
            return;
        }
        
        for (FTPFile file : files) {
            if (file == null) {
                continue;
            }
            
            String fullPath = remotePath.endsWith(BambuConst.PATHSEP) ? 
                remotePath + file.getName() : 
                remotePath + BambuConst.PATHSEP + file.getName();
            
            if (file.isFile()) {
                accumulator.add(fullPath);
            } else if (file.isDirectory() && !file.getName().equals(".") && !file.getName().equals("..")) {
                listFilesRecursiveHelper(fullPath, accumulator);
            }
        }
    }

    /**
     * Show a dialog with operation results.
     */
    private void showResultDialog(String title, String message, int successCount, int failCount) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        
        VerticalLayout layout = new VerticalLayout();
        
        Div successDiv = new Div(new Span("✓ Succeeded: " + successCount));
        successDiv.getStyle().set("color", "green");
        layout.add(successDiv);
        
        if (failCount > 0) {
            Div failDiv = new Div(new Span("✗ Failed: " + failCount));
            failDiv.getStyle().set("color", "red");
            layout.add(failDiv);
        }
        
        if (message.contains("Errors:")) {
            Div errorDetails = new Div(new Span(message.substring(message.indexOf("Errors:"))));
            errorDetails.getStyle()
                .set("max-height", "200px")
                .set("overflow-y", "auto")
                .set("font-family", "monospace")
                .set("font-size", "12px");
            layout.add(errorDetails);
        }
        
        Button closeButton = new Button("Close", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        layout.add(closeButton);
        
        dialog.add(layout);
        dialog.open();
    }

    /**
     * Format file size for display.
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
        percentageComplete = 100.0 * totalBytesTransferred / fileSize;
    }

    @FunctionalInterface
    private interface Callable {
        void run() throws Exception;
    }

    private class MyGrid<T> extends Grid<T> {
        private Optional<UI> ui = Optional.empty();

        @Override
        public Optional<UI> getUI() {
            return ui.or(super::getUI);
        }

        @Override
        protected void onAttach(final AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            ui = Optional.of(attachEvent.getUI());
        }
    }
}
