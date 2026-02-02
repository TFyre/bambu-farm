package com.tfyre.bambu.view;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.YesNoCancelDialog;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.bambu.security.SecurityUtils;
import com.tfyre.ftp.BambuFtp;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializablePredicate;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.MemorySize;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Batch Upload View - Upload multiple files to multiple printers
 * 
 * Features:
 * - Select multiple printers from grid (like BatchPrint)
 * - Upload multiple files (.3mf, .gcode)
 * - Upload all files to all selected printers
 * - Real-time progress tracking
 * - Upload only - does NOT start printing
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "batch-upload", layout = MainLayout.class)
@PageTitle("Batch Upload")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class BatchUploadView extends PushDiv implements NotificationHelper, GridHelper<BatchUploadView.PrinterRow> {

    private static final SerializablePredicate<PrinterRow> PREDICATE = pr -> true;

    @Inject
    BambuPrinters printers;
    
    @Inject
    Instance<BambuFtp> clientInstance;
    
    @Inject
    ManagedExecutor executor;

    @Inject
    BambuConfig config;

    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxBodySize;

    // Printer grid (matching BatchPrint style)
    private final Grid<PrinterRow> printerGrid = new Grid<>();
    private final HeaderRow printerHeaderRow = printerGrid.appendHeaderRow();
    private GridListDataView<PrinterRow> printerDataView;
    private SerializablePredicate<PrinterRow> printerPredicate = PREDICATE;
    // Upload progress UI
	private final Span uploadStatusSpan = new Span();
	private final Div progressPanel = new Div();

	// Per-file progress tracking
	private final Map<String, UploadProgress> uploadProgressMap = new ConcurrentHashMap<>();

    // File grid
    private final Grid<FileItem> fileGrid = new Grid<>();
    
    // Upload component
    private final MultiFileMemoryBuffer multiFileBuffer = new MultiFileMemoryBuffer();
    private final Upload multiFileUpload = new Upload(multiFileBuffer);
    
    // Control buttons
    private final Button refreshPrinters = new Button("Refresh", VaadinIcon.REFRESH.create());
    private final Button removeSelectedFiles = new Button("Remove Selected", VaadinIcon.TRASH.create());
    private final Button clearAllFiles = new Button("Clear All", VaadinIcon.ERASER.create());
    private final Button startUploadButton = new Button("Start Batch Upload", VaadinIcon.UPLOAD.create());
    
    // Status panels
    private final Div summaryPanel = new Div();
    private final Span uploadInfoSpan = new Span();
    private final Span uploadTimeSpan = new Span();
    
    private final Map<String, FileData> uploadedFiles = new LinkedHashMap<>();
    private UI ui;
    private boolean uploadInProgress = false;
    private long uploadStartTime = 0;

    private final NotificationHelper nh = new NotificationHelper() {};

    @Override
    public Grid<PrinterRow> getGrid() {
        return printerGrid;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        ui = attachEvent.getUI();
        
        addClassName("batchupload-view");
        
        configurePrinterGrid();
        configureFileGrid();
        configureUpload();
        configureButtons();
        configureUploadInfo();
        
        // Upload section - keep visible during uploads
        Div uploadDiv = new Div(multiFileUpload);
        uploadDiv.addClassName("upload");
        
        // Upload info section - shows batch upload progress
        Div uploadInfoDiv = new Div(uploadInfoSpan, uploadTimeSpan);
        uploadInfoDiv.addClassName("upload-info-section");
        uploadInfoDiv.setVisible(false);  // Hidden until upload starts
        
        // File section
        Div fileButtons = new Div(removeSelectedFiles, clearAllFiles, startUploadButton);
        fileButtons.addClassName("buttons");
        
        Div fileSection = new Div(
            uploadInfoDiv,  // Upload info at top
            fileButtons, 
            fileGrid, 
            summaryPanel
        );
        fileSection.addClassName("file-section");
        
        // Printer section with refresh button
        Div printerButtons = new Div(refreshPrinters);
        printerButtons.addClassName("buttons");
        
        Div printerSection = new Div(
            printerButtons, 
            printerGrid
        );
        printerSection.addClassName("printer-section");
        
        // Progress section
        Div progressSection = new Div(uploadStatusSpan, progressPanel);
        progressSection.addClassName("progress-section");
        
        add(uploadDiv, fileSection, printerSection, progressSection);
        
        loadPrinters();
        updateSummary();
        
        createFuture(this::updatePrinterStatuses, config.refreshInterval());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        uploadedFiles.clear();
    }

    private void configureUploadInfo() {
        // Upload info span - shows batch details
        uploadInfoSpan.addClassName("upload-batch-info");
        uploadTimeSpan.addClassName("upload-time-estimate");
        }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return remainingSeconds == 0 
                ? minutes + "m" 
                : String.format("%dm %ds", minutes, remainingSeconds);
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return remainingMinutes == 0 
                ? hours + "h" 
                : String.format("%dh %dm", hours, remainingMinutes);
        }
    }

    private void configurePrinterGrid() {
        printerGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        printerGrid.setWidthFull();
        
        // Name column with filter
        Grid.Column<PrinterRow> colName = setupPrinterColumnFilter("Name", PrinterRow::getName)
            .setFlexGrow(2)
            .setComparator(Comparator.comparing(PrinterRow::getName));
        
        // IP column with filter
        setupPrinterColumnFilter("IP Address", PrinterRow::getIp)
            .setFlexGrow(1);
        
        printerGrid.getColumns().forEach(c -> c.setResizable(true));
        printerGrid.sort(GridSortOrder.asc(colName).build());
        printerGrid.addSelectionListener(e -> updateSummary());
    }

    private <T extends String> Grid.Column<PrinterRow> setupPrinterColumnFilter(
            final String name, final ValueProvider<PrinterRow, T> valueProvider) {
        
        Grid.Column<PrinterRow> result = setupColumn(name, valueProvider);
        
        AtomicReference<String> filter = new AtomicReference<>(null);
        SerializablePredicate<PrinterRow> _predicate = pr ->
            Optional.ofNullable(filter.get())
                .map(s -> valueProvider.apply(pr).toLowerCase().contains(s))
                .orElse(true);
        
        printerPredicate = printerPredicate == PREDICATE ? _predicate : printerPredicate.and(_predicate);
        
        printerHeaderRow.getCell(result).setComponent(createFilterHeader(name, s -> {
            filter.set(s.toLowerCase());
            if (printerDataView != null) {
                printerDataView.refreshAll();
            }
        }));
        
        return result;
    }

    private Component createFilterHeader(final String labelText, final Consumer<String> filterChangeConsumer) {
        TextField filterField = new TextField();
        filterField.addValueChangeListener(event -> filterChangeConsumer.accept(event.getValue()));
        filterField.setValueChangeMode(ValueChangeMode.LAZY);
        filterField.setSizeFull();
        filterField.setPlaceholder(labelText);
        filterField.setClearButtonVisible(true);
        return filterField;
    }

    private void configureFileGrid() {
        fileGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        fileGrid.setWidthFull();
        fileGrid.setHeight("200px");
        
        fileGrid.addColumn(FileItem::getFileName)
            .setHeader("File Name")
            .setFlexGrow(3);
        
        fileGrid.addColumn(FileItem::getSize)
            .setHeader("Size")
            .setFlexGrow(1);
        
        fileGrid.addColumn(FileItem::getType)
            .setHeader("Type")
            .setFlexGrow(1);
        
        fileGrid.getColumns().forEach(c -> c.setResizable(true));
    }

    private void configureUpload() {
        multiFileUpload.setAcceptedFileTypes(".3mf", ".gcode");
        multiFileUpload.setMaxFileSize((int) maxBodySize.asLongValue());
        multiFileUpload.setDropLabel(new Span("Drop .3mf or .gcode files here (max: %dMB per file)".formatted(maxBodySize.asLongValue() / 1_000_000)));
        
        multiFileUpload.addSucceededListener(event -> {
            try {
                String fileName = event.getFileName();
                byte[] fileData = multiFileBuffer.getInputStream(fileName).readAllBytes();
                
                // Store file in our map
                FileData fd = new FileData(fileName, fileData);
                uploadedFiles.put(fileName, fd);
                
                refreshFileGrid();
                updateSummary();
                showNotification("File added: " + fileName);
                
            } catch (IOException e) {
                Log.error("Error reading uploaded file", e);
                showError("Error reading file: " + e.getMessage());
            }
        });
        
        multiFileUpload.addAllFinishedListener(e -> {
            // Clear the upload component after all files are processed
            multiFileUpload.clearFileList();
        });
        
        multiFileUpload.addFileRejectedListener(e -> {
            showError(e.getErrorMessage());
        });
    }

    private void configureButtons() {
        refreshPrinters.addClickListener(e -> {
            loadPrinters();
            showNotification("Printers refreshed");
        });
        
        removeSelectedFiles.addClickListener(e -> {
            Set<FileItem> selected = fileGrid.getSelectedItems();
            if (selected.isEmpty()) {
                showError("No files selected");
                return;
            }
            
            selected.forEach(fi -> uploadedFiles.remove(fi.getFileName()));
            refreshFileGrid();
            updateSummary();
            showNotification("Removed " + selected.size() + " file(s)");
        });
        
        clearAllFiles.addClickListener(e -> {
            if (uploadedFiles.isEmpty()) {
                showNotification("No files to clear");
                return;
            }
            
            YesNoCancelDialog.show("Clear all files?", ync -> {
                if (!ync.isConfirmed()) {
                    return;
                }
                int count = uploadedFiles.size();
                uploadedFiles.clear();
                refreshFileGrid();
                updateSummary();
                showNotification("Cleared " + count + " file(s)");
            });
        });
        
        startUploadButton.setEnabled(false);
        startUploadButton.addClickListener(e -> startBatchUpload());
    }

    private void loadPrinters() {
        Collection<BambuPrinters.PrinterDetail> printerDetails = printers.getPrintersDetail();
        
        List<PrinterRow> rows = printerDetails.stream()
            .filter(BambuPrinters.PrinterDetail::isRunning)
            .map(pd -> new PrinterRow(pd, ui))
            .sorted(Comparator.comparing(PrinterRow::getName))
            .collect(Collectors.toList());
        
        printerDataView = printerGrid.setItems(rows);
        printerDataView.setIdentifierProvider(PrinterRow::getId);
        printerDataView.addFilter(printerPredicate);
    }

    private void refreshFileGrid() {
        List<FileItem> items = uploadedFiles.values().stream()
            .map(fd -> new FileItem(fd.fileName, fd.fileData.length, fd.fileName.endsWith(".3mf") ? "3MF" : "GCODE"))
            .collect(Collectors.toList());
        
        fileGrid.setItems(items);
    }

    private void updateSummary() {
        int fileCount = uploadedFiles.size();
        int printerCount = printerGrid.getSelectedItems().size();
        int totalUploads = fileCount * printerCount;
        
        summaryPanel.removeAll();
        
        if (fileCount == 0 && printerCount == 0) {
            startUploadButton.setEnabled(false);
        } else if (fileCount == 0) {
            startUploadButton.setEnabled(false);
        } else if (printerCount == 0) {
            startUploadButton.setEnabled(false);
        } else {
            Span summary = new Span(String.format(
                "📊 Ready: %d file(s) × %d printer(s) = %d total upload(s)",
                fileCount, printerCount, totalUploads
            ));
            summaryPanel.add(summary);
            startUploadButton.setEnabled(!uploadInProgress);
            startUploadButton.setText("Start Batch Upload (" + totalUploads + " uploads)");
        }
  
    }

    private void updatePrinterStatuses() {
        if (printerDataView != null) {
            ui.access(() -> {
                printerDataView.getItems().forEach(PrinterRow::refresh);
                printerDataView.refreshAll();
            });
        }
    }

    private void startBatchUpload() {
        Set<PrinterRow> selectedPrinters = printerGrid.getSelectedItems();
        
        if (selectedPrinters.isEmpty() || uploadedFiles.isEmpty()) {
            showError("Please select printers and upload files");
            return;
        }
        
        String user = SecurityUtils.getPrincipal().map(p -> p.getName()).orElse("unknown");
        String ip = Optional.ofNullable(VaadinSession.getCurrent())
            .map(vs -> vs.getBrowser().getAddress())
            .orElse("unknown");
        
        Log.infof("Batch upload: user[%s] ip[%s] files[%d] printers[%d]",
            user, ip, uploadedFiles.size(), selectedPrinters.size());
        
        int totalUploads = uploadedFiles.size() * selectedPrinters.size();
        String message = String.format(
            "Upload %d file(s) to %d printer(s)?\nTotal: %d uploads",
            uploadedFiles.size(), selectedPrinters.size(), totalUploads
        );
        
        YesNoCancelDialog.show(message, ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            executeBatchUpload(selectedPrinters);
        });
    }

    private void executeBatchUpload(Set<PrinterRow> selectedPrinters) {
        uploadInProgress = true;
        uploadStartTime = System.currentTimeMillis();
        
        int totalFiles = uploadedFiles.size();
        int totalPrinters = selectedPrinters.size();
        int totalUploads = totalFiles * totalPrinters;
        
        ui.access(() -> {
            startUploadButton.setEnabled(false);
            startUploadButton.setText("Upload in Progress...");
            // Keep multiFileUpload visible
            printerGrid.setEnabled(false);
            fileGrid.setEnabled(false);
            refreshPrinters.setEnabled(false);
            removeSelectedFiles.setEnabled(false);
            clearAllFiles.setEnabled(false);
            
            // Show upload info section
            Div uploadInfoDiv = (Div) fileGrid.getParent().get().getChildren()
                .filter(c -> c instanceof Div && ((Div)c).getClassNames().contains("upload-info-section"))
                .findFirst().orElse(null);
            if (uploadInfoDiv != null) {
                uploadInfoDiv.setVisible(true);
            }
            
            uploadInfoSpan.setText(String.format(
                "📤 Uploading %d file%s to %d printer%s → %d total transfer%s",
                totalFiles, totalFiles != 1 ? "s" : "",
                totalPrinters, totalPrinters != 1 ? "s" : "",
                totalUploads, totalUploads != 1 ? "s" : ""
            ));
            uploadTimeSpan.setText("⏱️ Starting...");
            
            // Show progress tracking
            uploadStatusSpan.setText("📤 Uploading files...");
            uploadStatusSpan.getStyle().set("display", "block");
            progressPanel.removeAll();
            uploadProgressMap.clear();
            
            // Create progress entries for each file-printer combination
            for (FileData fileData : uploadedFiles.values()) {
                for (PrinterRow printerRow : selectedPrinters) {
                    String key = printerRow.getName() + ":" + fileData.fileName;
                    UploadProgress progress = new UploadProgress(fileData.fileName, printerRow.getName());
                    uploadProgressMap.put(key, progress);
                    progressPanel.add(progress.getComponent());
                }
            }
        });
        
		AtomicInteger totalUploadCount = new AtomicInteger(totalUploads);
        AtomicInteger completedUploads = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (PrinterRow printerRow : selectedPrinters) {
            for (FileData fileData : uploadedFiles.values()) {
                executor.submit(() -> {
                    uploadFileToPrinter(printerRow, fileData, 
                        successCount, failCount, completedUploads, totalUploadCount.get());
                });
            }
        }
    }

    private void uploadFileToPrinter(PrinterRow printerRow, FileData fileData,
                                     AtomicInteger successCount, AtomicInteger failCount,
                                     AtomicInteger completedUploads, int totalUploads) {
        
        String printerName = printerRow.getName();
        String fileName = fileData.fileName;
        String key = printerName + ":" + fileName;
        BambuFtp ftpClient = null;
        
        long startTime = System.currentTimeMillis();
        
        try {
            UploadProgress progress = uploadProgressMap.get(key);
            if (progress != null) {
                progress.setStatus("Connecting...");
            }
            
            ftpClient = clientInstance.get();
            ftpClient.setup(printerRow.getPrinterDetail(), (totalBytes, bytes, streamSize) -> {
                // FTP progress callback
                if (streamSize > 0 && progress != null) {
                    double pct = (double) totalBytes / streamSize;
                    long elapsed = System.currentTimeMillis() - startTime;
                    progress.updateProgress(pct, elapsed);
                }
            });
            
            ftpClient.doConnect();
            
            if (!ftpClient.doLogin()) {
                throw new IOException("Login failed");
            }
            
            if (progress != null) {
                progress.setStatus("Uploading...");
            }
            
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileData.fileData)) {
                boolean success = ftpClient.doUpload(fileName, inputStream);
                
                long totalTime = System.currentTimeMillis() - startTime;
                
                if (success) {
                    successCount.incrementAndGet();
                    if (progress != null) {
                        progress.setComplete(true, totalTime);
                    }
                    Log.infof("Uploaded %s to %s in %dms", fileName, printerName, totalTime);
                } else {
                    failCount.incrementAndGet();
                    if (progress != null) {
                        progress.setComplete(false, totalTime);
                    }
                    Log.warnf("Failed to upload %s to %s", fileName, printerName);
                }
            }
            
        } catch (Exception e) {
            failCount.incrementAndGet();
            UploadProgress progress = uploadProgressMap.get(key);
            if (progress != null) {
                long totalTime = System.currentTimeMillis() - startTime;
                progress.setComplete(false, totalTime);
                progress.setStatus("Error: " + e.getMessage());
            }
            Log.errorf(e, "Error uploading %s to %s", fileName, printerName);
            
        } finally {
            if (ftpClient != null) {
                try {
                    ftpClient.doClose();
                } catch (IOException e) {
                    Log.warnf(e, "Error closing connection to %s", printerName);
                }
            }
            
            int completed = completedUploads.incrementAndGet();
            
            // Update time info
            long elapsedSeconds = (System.currentTimeMillis() - uploadStartTime) / 1000;
            ui.access(() -> {
                uploadTimeSpan.setText(String.format(
                    "⏱️ Elapsed: %s | Progress: %d/%d transfers",
                    formatDuration(elapsedSeconds),
                    completed,
                    totalUploads
                ));
            });
            
            if (completed == totalUploads) {
                onAllUploadsComplete(successCount.get(), failCount.get());
            }
        }
    }

    private void onAllUploadsComplete(int successCount, int failCount) {
        long totalSeconds = (System.currentTimeMillis() - uploadStartTime) / 1000;
        
        ui.access(() -> {
            uploadInProgress = false;
            
            startUploadButton.setEnabled(true);
            startUploadButton.setText("Start Batch Upload");
            // Keep upload visible
            printerGrid.setEnabled(true);
            fileGrid.setEnabled(true);
            refreshPrinters.setEnabled(true);
            removeSelectedFiles.setEnabled(true);
            clearAllFiles.setEnabled(true);
            
            // Update upload info with completion status
            if (failCount == 0) {
                uploadInfoSpan.setText(String.format(
                    "✓ Upload Complete: %d transfer%s successful",
                    successCount, successCount != 1 ? "s" : ""
                ));
                uploadInfoSpan.getStyle().set("color", "var(--lumo-success-text-color)");
            } else {
                uploadInfoSpan.setText(String.format(
                    "⚠ Upload Complete: %d succeeded, %d failed",
                    successCount, failCount
                ));
                uploadInfoSpan.getStyle().set("color", "var(--lumo-error-text-color)");
            }
            
            uploadTimeSpan.setText(String.format(
                "⏱️ Total time: %s",
                formatDuration(totalSeconds)
            ));
            
            // Update status
            if (failCount == 0) {
                uploadStatusSpan.setText("✓ All uploads completed successfully!");
                uploadStatusSpan.getStyle().set("color", "var(--lumo-success-text-color)");
            } else {
                uploadStatusSpan.setText(String.format("⚠ Upload complete: %d succeeded, %d failed", successCount, failCount));
                uploadStatusSpan.getStyle().set("color", "var(--lumo-error-text-color)");
            }
            
            printerGrid.deselectAll();
            
            // Hide progress after 5 seconds
            executor.submit(() -> {
                try {
                    Thread.sleep(5000);
                    ui.access(() -> {
                        uploadStatusSpan.getStyle().set("display", "none");
                        progressPanel.removeAll();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            String message = String.format(
                "Batch Upload Complete\n✓ Succeeded: %d\n✗ Failed: %d",
                successCount, failCount
            );
            
            if (failCount == 0) {
                showNotification("All uploads completed successfully! (" + successCount + " uploads)");
            } else {
                showError(message);
            }
            
            Log.infof("Batch upload complete: %d succeeded, %d failed", successCount, failCount);
        });
    }

    // Inner classes
    
    public static class PrinterRow {
        private final BambuPrinters.PrinterDetail printerDetail;
        private final UI ui;
        private final String id;

        public PrinterRow(BambuPrinters.PrinterDetail printerDetail, UI ui) {
            this.printerDetail = printerDetail;
            this.ui = ui;
            this.id = UUID.randomUUID().toString();
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return printerDetail.name();
        }

        public String getIp() {
            return printerDetail.config().ip();
        }

        public String getStatus() {
            return printerDetail.printer().getGCodeState().getDescription();
        }

        public boolean isOnline() {
            return printerDetail.printer().getGCodeState().isReady();
        }

        public BambuPrinters.PrinterDetail getPrinterDetail() {
            return printerDetail;
        }

        public void refresh() {
            // Printer status is automatically updated by the printer's subscription
        }
    }

    public static class FileItem {
        private final String fileName;
        private final int size;
        private final String type;

        public FileItem(String fileName, int size, String type) {
            this.fileName = fileName;
            this.size = size;
            this.type = type;
        }

        public String getFileName() {
            return fileName;
        }

        public String getSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }

        public String getType() {
            return type;
        }
    }

    private static class FileData {
        final String fileName;
        final byte[] fileData;

        FileData(String fileName, byte[] fileData) {
            this.fileName = fileName;
            this.fileData = fileData;
        }
    }

    /**
     * Tracks upload progress for a single file to a single printer
     */
    private class UploadProgress {
        private final String fileName;
        private final String printerName;
        private final Div component;
        private final Span statusSpan;
        private final Span timeSpan;
        
        public UploadProgress(String fileName, String printerName) {
            this.fileName = fileName;
            this.printerName = printerName;
            
            component = new Div();
            component.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-m)")
                .set("padding", "var(--lumo-space-s)")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-s)");
            
            Span fileLabel = new Span("📄 " + fileName);
            fileLabel.getStyle()
                .set("min-width", "150px")
                .set("font-weight", "500");
            
            Span arrow = new Span("→");
            arrow.getStyle().set("color", "var(--lumo-contrast-50pct)");
            
            Span printerLabel = new Span("🖨 " + printerName);
            printerLabel.getStyle().set("min-width", "120px");
            
            statusSpan = new Span("Waiting...");
            statusSpan.getStyle()
                .set("flex-grow", "1")
                .set("color", "var(--lumo-secondary-text-color)");
            
            timeSpan = new Span("");
            timeSpan.getStyle()
                .set("min-width", "80px")
                .set("text-align", "right")
                .set("font-weight", "500");
            
            component.add(fileLabel, arrow, printerLabel, statusSpan, timeSpan);
        }
        
        public Div getComponent() {
            return component;
        }
        
        public void setStatus(String status) {
            ui.access(() -> {
                statusSpan.setText(status);
            });
        }
        
        public void updateProgress(double percentage, long elapsedMs) {
            ui.access(() -> {
                int pct = (int) (percentage * 100);
                statusSpan.setText(String.format("Uploading... %d%%", pct));
                statusSpan.getStyle().set("color", "var(--lumo-primary-text-color)");
                timeSpan.setText(formatTime(elapsedMs));
            });
        }
        
        public void setComplete(boolean success, long totalTimeMs) {
            ui.access(() -> {
                if (success) {
                    statusSpan.setText("✓ Complete");
                    statusSpan.getStyle().set("color", "var(--lumo-success-text-color)");
                    component.getStyle().set("background-color", "var(--lumo-success-color-10pct)");
                } else {
                    statusSpan.setText("✗ Failed");
                    statusSpan.getStyle().set("color", "var(--lumo-error-text-color)");
                    component.getStyle().set("background-color", "var(--lumo-error-color-10pct)");
                }
                timeSpan.setText(formatTime(totalTimeMs));
            });
        }
        
        private String formatTime(long ms) {
            long seconds = ms / 1000;
            if (seconds < 60) {
                return seconds + "s";
            } else {
                long minutes = seconds / 60;
                long remainingSeconds = seconds % 60;
                return String.format("%dm %ds", minutes, remainingSeconds);
            }
        }
    }

}
