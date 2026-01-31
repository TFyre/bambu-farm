package com.tfyre.bambu.view.batchprint;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.bambu.security.SecurityUtils;
import com.tfyre.bambu.view.GridHelper;
import com.tfyre.bambu.view.NotificationHelper;
import com.tfyre.bambu.view.PushDiv;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import com.tfyre.bambu.printer.BatchPrintDelayConfig;
import com.tfyre.bambu.printer.BatchPrintDelayService;
import java.time.Duration;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.button.ButtonVariant;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "batchprint", layout = MainLayout.class)
@PageTitle("Batch Print")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public final class BatchPrintView extends PushDiv implements NotificationHelper, FilamentHelper, GridHelper<PrinterMapping> {

    private static final String IMAGE_CLASS = "small";
    private static final SerializablePredicate<PrinterMapping> PREDICATE = pm -> true;

    @Inject
    BambuPrinters printers;
    @Inject
    Instance<PrinterMapping> printerMappingInstance;
    @Inject
    Instance<ProjectFile> projectFileInstance;
    @Inject
    ManagedExecutor executor;
    @Inject
    ScheduledExecutorService ses;
    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxBodySize;
    @Inject
    BambuConfig config;
	@Inject
	BatchPrintDelayService delayService;
	@Inject
	BatchPrintDelayConfig delayConfig;

    private final ComboBox<Plate> plateLookup = new ComboBox<>("Plate Id");
    private final Grid<PrinterMapping> grid = new Grid<>();
    private final HeaderRow headerRow = grid.appendHeaderRow();
    private final Image thumbnail = new Image();
    private final Span printTime = new Span();
    private final Span printWeight = new Span();
    private final Div printFilaments = newDiv("filaments");
    private final Checkbox skipSameSize = new Checkbox("Skip if same size");
    private final Checkbox timelapse = new Checkbox("Timelapse");
    private final Checkbox bedLevelling = new Checkbox("Bed Levelling");
    private final Checkbox flowCalibration = new Checkbox("Flow Calibration");
    private final Checkbox vibrationCalibration = new Checkbox("Vibration Calibration");
	private final Checkbox skipFilamentMapping = new Checkbox("Skip Filament Mapping");
	private GridListDataView<PrinterMapping> dataView;
	private final Button printButton = new Button("Print", VaadinIcon.PRINT.create(), l -> printAll());
	private final Button refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create(), l -> refresh());
	// Delay control fields
	private final Checkbox enableDelay = new Checkbox("Enable batch delay", true);
	private final IntegerField simultaneousPrintersField = new IntegerField();
	//private final IntegerField delaySecondsField = new IntegerField();
	private final IntegerField delayHoursField = new IntegerField();
	private final IntegerField delayMinutesField = new IntegerField();
	private final IntegerField delaySecondsField = new IntegerField();	
	private final Button applySimultaneousButton = new Button(VaadinIcon.CHECK.create());
	private final Button applyDelayButton = new Button(VaadinIcon.CHECK.create());

	// Will be created in onAttach after configuring delay controls
	private Div actions;
    private final FileBuffer buffer = new FileBuffer();
    private final Upload upload = new Upload(buffer);
    private ProjectFile projectFile;
    private List<PrinterMapping> printerMappings = List.of();
    private SerializablePredicate<PrinterMapping> predicate = PREDICATE;

    @Override
    public Grid<PrinterMapping> getGrid() {
        return grid;
    }

	private void updateEstimatedTime() {
		Set<PrinterMapping> selected = grid.getSelectedItems();
		int printerCount = selected.size();
		
		Integer simultaneousValue = simultaneousPrintersField.getValue();
		if (simultaneousValue == null) {
			return;
		}

		int simultaneous = simultaneousValue;
		long delaySeconds = getTotalDelaySeconds();
		
		// Calculate batches
		int batchCount = (int) Math.ceil((double) printerCount / simultaneous);
		long totalDelaySeconds = delaySeconds * (batchCount - 1);
	}

	private String formatDuration(Duration duration) {
		long seconds = duration.toSeconds();
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

	/**
	 * Get total delay in seconds from the three time fields
	 */
	private int getTotalDelaySeconds() {
		Integer hours = delayHoursField.getValue();
		Integer minutes = delayMinutesField.getValue();
		Integer seconds = delaySecondsField.getValue();
		
		if (hours == null) hours = 0;
		if (minutes == null) minutes = 0;
		if (seconds == null) seconds = 0;
		
		return (hours * 3600) + (minutes * 60) + seconds;
	}

	/**
	 * Set the time fields from total seconds
	 */
	private void setTimeFieldsFromSeconds(int totalSeconds) {
		int hours = totalSeconds / 3600;
		int minutes = (totalSeconds % 3600) / 60;
		int seconds = totalSeconds % 60;
		
		delayHoursField.setValue(hours);
		delayMinutesField.setValue(minutes);
		delaySecondsField.setValue(seconds);
	}

	private void configureDelayControls() {
		// Simultaneous printers field
		simultaneousPrintersField.setValue(delayConfig.simultaneousPrinters());
		simultaneousPrintersField.setMin(1);
		simultaneousPrintersField.setPlaceholder("Enter number");
		simultaneousPrintersField.setHelperText("Number of printers in batch");
		simultaneousPrintersField.addClassName("delay-field");
		
		// Apply button for simultaneous
		applySimultaneousButton.addThemeVariants(
			ButtonVariant.LUMO_PRIMARY,
			ButtonVariant.LUMO_SMALL,
			ButtonVariant.LUMO_SUCCESS
		);
		applySimultaneousButton.addClassName("delay-apply-button");
		applySimultaneousButton.addClickListener(e -> {
			showNotification("Simultaneous printers count updated");
			updateEstimatedTime();
		});
		
		// Delay field
		// Parse initial time from config
		int initialSeconds = (int) delayConfig.jobDelay().toSeconds();
		int initialHours = initialSeconds / 3600;
		int initialMinutes = (initialSeconds % 3600) / 60;
		int initialSecs = initialSeconds % 60;

		// Hours field
		delayHoursField.setValue(initialHours);
		delayHoursField.setMin(0);
		delayHoursField.setMax(23);
		delayHoursField.setWidth("60px");
		delayHoursField.setPlaceholder("HH");
		delayHoursField.setHelperText("Hours");
		delayHoursField.addClassName("delay-time-part");
		delayHoursField.addValueChangeListener(e -> updateEstimatedTime());

		// Minutes field
		delayMinutesField.setValue(initialMinutes);
		delayMinutesField.setMin(0);
		delayMinutesField.setMax(59);
		delayMinutesField.setWidth("60px");
		delayMinutesField.setPlaceholder("MM");
		delayMinutesField.setHelperText("Minutes");
		delayMinutesField.addClassName("delay-time-part");
		delayMinutesField.addValueChangeListener(e -> updateEstimatedTime());

		// Seconds field
		delaySecondsField.setValue(initialSecs);
		delaySecondsField.setMin(0);
		delaySecondsField.setMax(59);
		delaySecondsField.setWidth("60px");
		delaySecondsField.setPlaceholder("SS");
		delaySecondsField.setHelperText("Seconds");
		delaySecondsField.addClassName("delay-time-part");
		delaySecondsField.addValueChangeListener(e -> updateEstimatedTime());
		
		// Apply button for delay
		applyDelayButton.addThemeVariants(
			ButtonVariant.LUMO_PRIMARY,
			ButtonVariant.LUMO_SMALL,
			ButtonVariant.LUMO_SUCCESS
		);
		applyDelayButton.addClassName("delay-apply-button");
		applyDelayButton.addClickListener(e -> {
			updateEstimatedTime();
			showNotification("Delay time updated");
		});
		
		// Enable delay checkbox
		enableDelay.setValue(delayConfig.enableDelay());
		enableDelay.addValueChangeListener(e -> {
			boolean enabled = e.getValue();
			simultaneousPrintersField.setEnabled(enabled);
			applySimultaneousButton.setEnabled(enabled);
			delayHoursField.setEnabled(enabled);
			delayMinutesField.setEnabled(enabled);
			delaySecondsField.setEnabled(enabled);
			applyDelayButton.setEnabled(enabled);
			updateEstimatedTime();
		});
	}

	private void updatePrintButtonState() {
		boolean isInDelay = delayService.isInDelayPeriod();
		printButton.setEnabled(!isInDelay);
		
		if (isInDelay) {
			printButton.setText("Print (Batch in Progress...)");
		} else {
			printButton.setText("Print");
		}
	}

    private void configurePlate(final Plate plate) {
        if (plate == null) {
            return;
        }
        thumbnail.setSrc(projectFile.getThumbnail(plate));
        printTime.setText("Time: %s".formatted(formatTime(plate.prediction())));
        printWeight.setText("Weight: %.2fg".formatted(plate.weight()));
        printFilaments.removeAll();
        plate.filaments().forEach(pf -> {
            printFilaments.add(newDiv("filament", newFilament(pf), new Span("%.2fg".formatted(pf.weight()))));
        });
		printerMappings.forEach(pm -> {
			pm.skipFilamentMapping(skipFilamentMapping.getValue());
			pm.setPlate(plate);
		});        dataView.refreshAll();
    }

    private void configurePlateLookup() {
        plateLookup.setItemLabelGenerator(Plate::name);
        plateLookup.addValueChangeListener(l -> configurePlate(l.getValue()));
    }

    private Component createFilterHeader(final String labelText, final Consumer<String> filterChangeConsumer) {
        final TextField filterField = new TextField();
        filterField.addValueChangeListener(event -> filterChangeConsumer.accept(event.getValue()));
        filterField.setValueChangeMode(ValueChangeMode.LAZY);
        filterField.setSizeFull();
        filterField.setPlaceholder(labelText);
        filterField.setClearButtonVisible(true);
        return filterField;
    }

    private <T extends String> Grid.Column<PrinterMapping> setupColumnFilter(final String name, final ValueProvider<PrinterMapping, T> valueProvider) {
        final Grid.Column<PrinterMapping> result = setupColumn(name, valueProvider).setComparator(Comparator.comparing(valueProvider));

        final AtomicReference<String> filter = new AtomicReference<>(null);
        final SerializablePredicate<PrinterMapping> _predicate = pm ->
                Optional.ofNullable(filter.get()).map(s -> valueProvider.apply(pm).toLowerCase().contains(s)).orElse(true);

        predicate = predicate == PREDICATE ? _predicate : predicate.and(_predicate);

        headerRow.getCell(result).setComponent(createFilterHeader(name, s -> {
            filter.set(s.toLowerCase());
            dataView.refreshAll();
        }));
        return result;

    }

    private Component newCheckbox(final boolean checked) {
        final Checkbox result = new Checkbox();
        result.setValue(checked);
        result.setReadOnly(true);
        return result;
    }

    private void configureGrid() {
        final Grid.Column<PrinterMapping> colName
                = setupColumnFilter("Name", pm -> pm.getPrinterDetail().printer().getName()).setFlexGrow(2);
        setupColumn("Plate Id", pm -> Optional.ofNullable(plateLookup.getValue()).map(Plate::name).orElse("")).setFlexGrow(1);
        setupColumnFilter("Printer Status", pm -> pm.getPrinterDetail().printer().getGCodeState().getDescription()).setFlexGrow(2);
        grid.addComponentColumn(pm -> newCheckbox(pm.getPrinterDetail().printer().getGCodeState().isReady())).setHeader("Printer Ready").setFlexGrow(1);
        grid.addComponentColumn(PrinterMapping::getBulkStatus).setHeader("Bulk Status").setFlexGrow(2);
        grid.addComponentColumn(PrinterMapping::getFilamentMapping).setHeader("Filament Mapping").setFlexGrow(3);

        grid.getColumns().forEach(c -> c.setResizable(true));
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);

        grid.sort(GridSortOrder.asc(colName).build());
        grid.setSelectionMode(Grid.SelectionMode.MULTI);
		grid.addSelectionListener(e -> {
			updateEstimatedTime();
		});

        final UI ui = getUI().get();
        printerMappings = printers.getPrintersDetail().stream()
                .filter(pd -> pd.isRunning())
                .map(pd -> printerMappingInstance.get().setup(ui, pd))
                .toList();
        dataView = grid.setItems(printerMappings);
        dataView.setIdentifierProvider(PrinterMapping::getId);
        dataView.addFilter(predicate);
    }

	private void printAll(final Set<PrinterMapping> selected) {
		final String user = SecurityUtils.getPrincipal().map(p -> p.getName()).orElse("null");
		final String ip = Optional.ofNullable(VaadinSession.getCurrent()).map(vs -> vs.getBrowser().getAddress()).orElse("null");
		Log.infof("printAll: user[%s] ip[%s] file[%s] printers[%s]", user, ip, projectFile.getFilename(),
				selected.stream().map(pm -> pm.getPrinterDetail().name()).toList());
		
		final BambuPrinter.CommandPPF command = new BambuPrinter.CommandPPF("", 0, true, 
				timelapse.getValue(), bedLevelling.getValue(), flowCalibration.getValue(), 
				vibrationCalibration.getValue(), List.of());
		
		Duration delay = enableDelay.getValue()
			? Duration.ofSeconds(getTotalDelaySeconds())
			: Duration.ZERO;
		int simultaneousPrinters = simultaneousPrintersField.getValue() != null
			? simultaneousPrintersField.getValue()
			: 1;
		
		// Create job list for delay service
		List<BatchPrintDelayService.PrinterJob> jobs = new java.util.ArrayList<>();
		for (PrinterMapping pm : selected) {
			jobs.add(new BatchPrintDelayService.PrinterJob() {
				@Override
				public String getPrinterName() {
					return pm.getPrinterDetail().name();
				}
				
				@Override
				public void execute() throws Exception {
					pm.sendPrint(projectFile, command, skipSameSize.getValue());
				}
			});
		}
		
	// Disable print button immediately
	printButton.setEnabled(false);
	printButton.setText("Print (Batch in Progress...)");

	// Start batch with delay
	delayService.sendBatchJobsWithDelay(jobs, delay, simultaneousPrinters)
		.thenRun(() -> {
			getUI().ifPresent(ui -> ui.access(() -> {
				showNotification("All batch print jobs queued successfully!");
				updatePrintButtonState();  // Re-enable button
			}));
		})
		.exceptionally(ex -> {
			getUI().ifPresent(ui -> ui.access(() -> {
				showError("Error during batch print: " + ex.getMessage());
				updatePrintButtonState();  // Re-enable button even on error
			}));
			return null;
		});

	showNotification("Starting batch print for %d printers...".formatted(selected.size()));
	}

    private void refresh() {
        printerMappings.forEach(PrinterMapping::refresh);
        dataView.refreshAll();
    }

    private void printAll() {
        final Set<PrinterMapping> selected = grid.getSelectedItems();
        if (selected.isEmpty()) {
            showError("Nothing selected");
            return;
        }
        if (selected.stream().filter(PrinterMapping::canPrint).count() != selected.size()) {
            showError("Please ensure printers are idle and filaments are mapped");
            return;
        }

        doConfirm(() -> printAll(selected));
    }

	private void headerVisible(final boolean isVisible) {
		thumbnail.setVisible(isVisible);
		actions.setVisible(isVisible);
	}

    private void configureUpload() {
        upload.setAcceptedFileTypes(BambuConst.FILE_3MF);
        upload.addSucceededListener(e -> loadProjectFile(e.getFileName()));
        upload.setMaxFileSize((int) maxBodySize.asLongValue());
        upload.setDropLabel(new Span("Drop file here (max size: %dM)".formatted(maxBodySize.asLongValue() / 1_000_000)));
        upload.addFileRejectedListener(l -> {
            showError(l.getErrorMessage());
        });
    }

    private void configureThumbnail() {
        thumbnail.addClassName(IMAGE_CLASS);
        thumbnail.addClickListener(l -> {
            if (thumbnail.hasClassName(IMAGE_CLASS)) {
                thumbnail.removeClassName(IMAGE_CLASS);
            } else {
                thumbnail.addClassName(IMAGE_CLASS);
            }
        });
    }

    private void updateBulkStatus() {
        printerMappings.forEach(PrinterMapping::updateBulkStatus);
    }

    private void configureActions() {
        skipSameSize.setValue(config.batchPrint().skipSameSize());
        timelapse.setValue(config.batchPrint().timelapse());
        bedLevelling.setValue(config.batchPrint().bedLevelling());
        flowCalibration.setValue(config.batchPrint().flowCalibration());
        vibrationCalibration.setValue(config.batchPrint().vibrationCalibration());
		skipFilamentMapping.setValue(config.batchPrint().skipFilamentMapping());
		skipFilamentMapping.addValueChangeListener(e -> {
			boolean skip = e.getValue();
			printerMappings.forEach(pm -> pm.skipFilamentMapping(skip));
			dataView.refreshAll();
		});
    }

	@Override
	protected void onAttach(final AttachEvent attachEvent) {
		super.onAttach(attachEvent);
		addClassName("batchprint-view");
		configureActions();
		configureDelayControls();  // NEW: Configure delay controls
		configurePlateLookup();
		configureGrid();
		configureUpload();
		configureThumbnail();
		
		// Create actions div with delay controls included
		actions = newDiv("actions", 
			plateLookup,
			newDiv("detail", printTime, printWeight),
			printFilaments,
			newDiv("options", 
				skipSameSize, timelapse, bedLevelling, 
				flowCalibration, vibrationCalibration, skipFilamentMapping, enableDelay, simultaneousPrintersField,
				applySimultaneousButton, delayHoursField, delayMinutesField, delaySecondsField,
				applyDelayButton
			),
			newDiv("buttons", printButton, refreshButton)
		);
		
		headerVisible(false);
		add(newDiv("header", thumbnail, actions, newDiv("upload", upload)), grid);
		
		final UI ui = attachEvent.getUI();
		createFuture(() -> ui.access(() -> {
			updateBulkStatus();
			updatePrintButtonState();
		}), config.refreshInterval());
	}

    @Override
    protected void onDetach(final DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        closeProjectFile();
    }

    private void loadProjectFile(final String filename) {
        closeProjectFile();
        plateLookup.setItems(List.of());
        try {
            projectFile = projectFileInstance.get().setup(filename, buffer.getFileData().getFile());
        } catch (ProjectException ex) {
            showError(ex);
            return;
        }
        final List<Plate> plates = projectFile.getPlates();
        plateLookup.setItems(plates);
        if (plates.isEmpty()) {
            headerVisible(false);
            showError("No sliced plates found");
        } else {
            headerVisible(true);
            plateLookup.setValue(plates.get(0));
        }
    }

    private void closeProjectFile() {
        if (projectFile == null) {
            return;
        }
        projectFileInstance.destroy(projectFile);
        projectFile = null;
    }

}
