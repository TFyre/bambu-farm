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
import java.util.stream.Collectors;

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

	private java.util.concurrent.ScheduledFuture<?> countdownFuture;
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
	private final Button queueMoreButton = new Button("Add to Queue", VaadinIcon.PLUS.create());
	private final Button cancelSelectedButton = new Button("Cancel", VaadinIcon.STOP.create());
	private final Button cancelAllButton = new Button("ABORT", VaadinIcon.BAN.create());
	private final Button clearQueueButton = new Button("Clear Queue", VaadinIcon.TRASH.create());  // ADD THIS LINE
	
	// Delay control fields
	private final Checkbox enableDelay = new Checkbox("Enable batch delay", true);
	private final IntegerField simultaneousPrintersField = new IntegerField();
	private final IntegerField delayHoursField = new IntegerField();
	private final IntegerField delayMinutesField = new IntegerField();
	private final IntegerField delaySecondsField = new IntegerField();	
	private final Span selectedPrintersSpan = new Span();
	private final Span batchInfoSpan = new Span();
	private final Span estimatedTimeSpan = new Span();
	private final Span queueStatusSpan = new Span();
	private final Span remainingBatchesSpan = new Span();
	private final Span totalDelaySpan = new Span();
	private final Div thumbnailPlaceholder = new Div();

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
		simultaneousPrintersField.setWidth("80px");
		simultaneousPrintersField.setHelperText("Number of printers in batch");
		simultaneousPrintersField.addClassName("delay-field");
		
		// Update on blur or Enter (isFromClient = true means user interaction)
		simultaneousPrintersField.addValueChangeListener(e -> {
			if (e.isFromClient()) {
				showNotification("Simultaneous printers count updated");
				updateEstimatedTime();
			}
		});
		
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
		delayHoursField.addValueChangeListener(e -> {
			if (e.isFromClient()) {
				updateEstimatedTime();
			}
		});

		// Minutes field
		delayMinutesField.setValue(initialMinutes);
		delayMinutesField.setMin(0);
		delayMinutesField.setMax(59);
		delayMinutesField.setWidth("60px");
		delayMinutesField.setPlaceholder("MM");
		delayMinutesField.setHelperText("Minutes");
		delayMinutesField.addClassName("delay-time-part");
		delayMinutesField.addValueChangeListener(e -> {
			if (e.isFromClient()) {
				updateEstimatedTime();
			}
		});

		// Seconds field
		delaySecondsField.setValue(initialSecs);
		delaySecondsField.setMin(0);
		delaySecondsField.setMax(59);
		delaySecondsField.setWidth("60px");
		delaySecondsField.setPlaceholder("SS");
		delaySecondsField.setHelperText("Seconds");
		delaySecondsField.addClassName("delay-time-part");
		delaySecondsField.addValueChangeListener(e -> {
			if (e.isFromClient()) {
				updateEstimatedTime();
			}
		});
		
		delayHoursField.addBlurListener(e -> {
			int totalSeconds = getTotalDelaySeconds();
			showNotification("Delay time updated to " + formatDuration(Duration.ofSeconds(totalSeconds)));
		});

		delayMinutesField.addBlurListener(e -> {
			int totalSeconds = getTotalDelaySeconds();
			showNotification("Delay time updated to " + formatDuration(Duration.ofSeconds(totalSeconds)));
		});

		delaySecondsField.addBlurListener(e -> {
			int totalSeconds = getTotalDelaySeconds();
			showNotification("Delay time updated to " + formatDuration(Duration.ofSeconds(totalSeconds)));
		});
		
		// Enable delay checkbox
		enableDelay.setValue(delayConfig.enableDelay());
		enableDelay.addValueChangeListener(e -> {
			boolean enabled = e.getValue();
			simultaneousPrintersField.setEnabled(enabled);
			delayHoursField.setEnabled(enabled);
			delayMinutesField.setEnabled(enabled);
			delaySecondsField.setEnabled(enabled);
			updateEstimatedTime();
		});
		
		// Info spans - CSS classes only
		selectedPrintersSpan.addClassName("selected-printers");
		batchInfoSpan.addClassName("batch-info");
		estimatedTimeSpan.addClassName("time-estimate");
		queueStatusSpan.addClassName("queue-status");
		remainingBatchesSpan.addClassName("remaining-batches");
		totalDelaySpan.addClassName("total-delay");
	}

	private void configureCancelButtons() {
		cancelSelectedButton.addClickListener(e -> {
			doConfirm(() -> {
				cancelSelectedPrints();
			});
		});
		
		cancelAllButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		cancelAllButton.addClickListener(e -> {
			doConfirm(() -> {
				cancelAllPrints();
			});
		});
	}
	
	private void configureClearQueueButton() {
		clearQueueButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		clearQueueButton.setTooltipText("Remove all jobs from the batch queue");
		clearQueueButton.setEnabled(false); // Disabled until batch is running
		clearQueueButton.addClickListener(e -> {
			doConfirm(() -> {
				clearEntireQueue();
			});
		});
	}
	
	private void configureQueueMoreButton() {
		queueMoreButton.setTooltipText("Add selected printers to batch queue");
		queueMoreButton.setEnabled(false); // Disabled until batch is running
		queueMoreButton.addClickListener(e -> queueMorePrinters());
	}
	
	private void removeSelectedFromQueue() {
		final Set<PrinterMapping> selected = grid.getSelectedItems();
		if (selected.isEmpty()) {
			showError("No printers selected");
			return;
		}
		
		List<String> printerNames = selected.stream()
			.map(pm -> pm.getPrinterDetail().name())
			.collect(Collectors.toList());
		
		int removed = delayService.abortSelectedPrinters(printerNames);
		
		if (removed > 0) {
			showNotification("Removed %d printer(s) from queue".formatted(removed));
			updateActiveJobsDisplay();
			
			// If no jobs left, disable queue buttons
			// If no jobs left, disable queue buttons
		if (delayService.getQueuedJobCount() == 0 && !delayService.isBatchRunning()) {
			queueMoreButton.setEnabled(false);
			clearQueueButton.setEnabled(false);
			updatePrintButtonState();
		}
		} else {
			showNotification("No matching printers found in queue");
		}
	}

	private void updateActiveJobsDisplay() {
    int queuedJobs = delayService.getQueuedJobCount();
    int processedJobs = delayService.getProcessedJobCount();
    int totalJobs = delayService.getTotalJobCount();
    
    if (delayService.isBatchRunning()) {
        // 2. Show remaining printers in queue
        queueStatusSpan.setText(String.format("🔄 Queue: %d / %d printers", queuedJobs, totalJobs));
        
        // 3. Show remaining batches
        int simultaneousPrinters = simultaneousPrintersField.getValue() != null 
            ? simultaneousPrintersField.getValue() 
            : 1;
        int remainingBatches = (int) Math.ceil((double) queuedJobs / simultaneousPrinters);
        int totalBatches = (int) Math.ceil((double) totalJobs / simultaneousPrinters);
        int completedBatches = totalBatches - remainingBatches;
        
        remainingBatchesSpan.setText(String.format("📊 Batches: %d / %d completed", 
            completedBatches, totalBatches));
        
        // 4. Show remaining total delay time with live countdown
        if (enableDelay.getValue() && remainingBatches > 1) {
            startCountdown(remainingBatches, simultaneousPrinters);
        } else {
            stopCountdown();
            totalDelaySpan.setText("");
        }
        
        clearQueueButton.setEnabled(queuedJobs > 0);
    } else {
        stopCountdown();
        queueStatusSpan.setText("");
        remainingBatchesSpan.setText("");
        totalDelaySpan.setText("");
        clearQueueButton.setEnabled(false);
    }
}

	private void startCountdown(int remainingBatches, int simultaneousPrinters) {
		// Stop existing countdown if any
		stopCountdown();
		
		// Start new countdown that updates every second
		countdownFuture = ses.scheduleAtFixedRate(() -> {
			getUI().ifPresent(ui -> ui.access(() -> {
				int queuedJobs = delayService.getQueuedJobCount();
				int currentRemainingBatches = (int) Math.ceil((double) queuedJobs / simultaneousPrinters);
				
				if (currentRemainingBatches > 1) {
					// Calculate total remaining delay
					int delaySeconds = getTotalDelaySeconds();
					long remainingToNextBatch = delayService.getRemainingDelaySeconds();
					
					// Total = delay to next batch + delays for all future batches
					long totalRemaining = remainingToNextBatch + (delaySeconds * (currentRemainingBatches - 2));
					
					if (totalRemaining > 0) {
						String delayText = formatDuration((int) totalRemaining);
						totalDelaySpan.setText(String.format("⏱️ Remaining delay: %s", delayText));
					} else {
						totalDelaySpan.setText("");
					}
				} else {
					totalDelaySpan.setText("");
					stopCountdown();
				}
			}));
		}, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
	}

	private void stopCountdown() {
		if (countdownFuture != null && !countdownFuture.isDone()) {
			countdownFuture.cancel(false);
			countdownFuture = null;
		}
	}
	
	private void cancelSelectedPrints() {
		final Set<PrinterMapping> selected = grid.getSelectedItems();
		if (selected.isEmpty()) {
			showError("No printers selected to cancel");
			return;
		}
		
		// Cancel actual print jobs on printers
		int cancelled = 0;
		for (PrinterMapping pm : selected) {
			try {
				// Send stop command to printer using commandControl
				pm.getPrinterDetail().printer().commandControl(BambuConst.CommandControl.STOP);
				cancelled++;
				Log.infof("Sent stop command to printer: %s", pm.getPrinterDetail().name());
			} catch (Exception ex) {
				Log.errorf(ex, "Failed to stop print on printer: %s", pm.getPrinterDetail().name());
			}
		}
		
		showNotification("Sent stop command to %d printer(s)".formatted(cancelled));
		
		// Refresh grid to show updated printer states
		executor.execute(() -> {
			try {
				Thread.sleep(1000);
				getUI().ifPresent(ui -> ui.access(() -> {
					refresh();
				}));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}
	
	private void cancelAllPrints() {
		// Get all printers that might be printing (check GCode state)
		Set<PrinterMapping> allPrinters = printerMappings.stream()
			.filter(pm -> {
				BambuConst.GCodeState state = pm.getPrinterDetail().printer().getGCodeState();
				return state == BambuConst.GCodeState.RUNNING || 
				       state == BambuConst.GCodeState.PREPARE ||
				       state == BambuConst.GCodeState.PAUSE;
			})
			.collect(Collectors.toSet());
		
		if (allPrinters.isEmpty()) {
			showNotification("No printers are currently printing");
			return;
		}
		
		// Send stop command to all printers that are printing
		int cancelled = 0;
		for (PrinterMapping pm : allPrinters) {
			try {
				// Send stop command to printer using commandControl
				pm.getPrinterDetail().printer().commandControl(BambuConst.CommandControl.STOP);
				cancelled++;
				Log.infof("Sent stop command to printer: %s", pm.getPrinterDetail().name());
			} catch (Exception ex) {
				Log.errorf(ex, "Failed to stop print on printer: %s", pm.getPrinterDetail().name());
			}
		}
		
		showNotification("Sent stop command to %d printer(s)".formatted(cancelled));
		
		// Refresh grid to show updated printer states
		executor.execute(() -> {
			try {
				Thread.sleep(1000);
				getUI().ifPresent(ui -> ui.access(() -> {
					refresh();
				}));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	private void clearEntireQueue() {
		int queuedCount = delayService.getQueuedJobCount();
		if (queuedCount == 0) {
			showNotification("Queue is already empty");
			return;
		}
		
		// Abort all queued jobs
		delayService.abortAllJobs();
		showNotification("Cleared %d job(s) from queue".formatted(queuedCount));
		updateActiveJobsDisplay();
		
		// Disable queue buttons when queue is cleared
		queueMoreButton.setEnabled(false);
		clearQueueButton.setEnabled(false);
		updatePrintButtonState();
	}

	private void queueMorePrinters() {
		final Set<PrinterMapping> selected = grid.getSelectedItems();
		if (selected.isEmpty()) {
			showError("Nothing selected");
			return;
		}
		if (selected.stream().filter(PrinterMapping::canPrint).count() != selected.size()) {
			showError("Please ensure printers are idle and filaments are mapped");
			return;
		}
		
		doConfirm(() -> {
			queueMorePrintersConfirmed(selected);
		});
	}

	private void queueMorePrintersConfirmed(final Set<PrinterMapping> selected) {
		final String user = SecurityUtils.getPrincipal().map(p -> p.getName()).orElse("null");
		final String ip = Optional.ofNullable(VaadinSession.getCurrent()).map(vs -> vs.getBrowser().getAddress()).orElse("null");
		Log.infof("queueMore: user[%s] ip[%s] file[%s] printers[%s]", user, ip, projectFile.getFilename(),
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
		
		delayService.sendBatchJobsWithDelay(jobs, delay, simultaneousPrinters)
			.thenRun(() -> {
				getUI().ifPresent(ui -> ui.access(() -> {
					showNotification("Added %d printer(s)".formatted(selected.size()));
					updateActiveJobsDisplay();
				}));
			})
			.exceptionally(ex -> {
				getUI().ifPresent(ui -> ui.access(() -> {
					showError("Error: " + ex.getMessage());
				}));
				return null;
			});
		
		showNotification("Queuing %d more...".formatted(selected.size()));
	}

	private void updatePrintButtonState() {
		boolean isInDelay = delayService.isInDelayPeriod();
		printButton.setEnabled(!isInDelay);
		
		if (isInDelay) {
			long remainingSeconds = delayService.getRemainingDelaySeconds();
			printButton.setText("Print (Wait %ds...)".formatted(remainingSeconds));
		} else {
			printButton.setText("Print");
		}
	}

	private void updateEstimatedTime() {
		Set<PrinterMapping> selected = grid.getSelectedItems();
		int printerCount = (int) selected.stream().filter(PrinterMapping::canPrint).count();
		
		// 1. ALWAYS show selected count
		if (printerCount > 0) {
			selectedPrintersSpan.setText(String.format("📋 Selected: %d printers", printerCount));
		} else {
			selectedPrintersSpan.setText("");
		}
		
		if (!enableDelay.getValue() || printerCount <= 1) {
			batchInfoSpan.setText("");
			estimatedTimeSpan.setText("");
			return;
		}
		
		Integer simultaneousValue = simultaneousPrintersField.getValue();
		if (simultaneousValue == null) {
			return;
		}

		int simultaneous = simultaneousValue;
		long delaySeconds = getTotalDelaySeconds();
		
		// Calculate batches
		int batchCount = (int) Math.ceil((double) printerCount / simultaneous);
		long totalDelaySeconds = delaySeconds * (batchCount - 1);
		
		// Batch info
		batchInfoSpan.setText(String.format(
			"📊 %d printer%s → %d batch%s of %d", 
			printerCount,
			printerCount != 1 ? "s" : "",
			batchCount,
			batchCount != 1 ? "es" : "",
			simultaneous
		));
		
		// Estimated time
		String timeEstimate = formatDuration(Duration.ofSeconds(totalDelaySeconds));
		estimatedTimeSpan.setText(String.format(
			"⏱️ Total delay: %s (%d × %ds)", 
			timeEstimate,
			batchCount - 1,
			delaySeconds
		));
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

	private String formatDuration(int totalSeconds) {
		int hours = totalSeconds / 3600;
		int minutes = (totalSeconds % 3600) / 60;
		int seconds = totalSeconds % 60;
		
		if (hours > 0) {
			return String.format("%dh %dm %ds", hours, minutes, seconds);
		} else if (minutes > 0) {
			return String.format("%dm %ds", minutes, seconds);
		} else {
			return String.format("%ds", seconds);
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
		});        
		dataView.refreshAll();
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
		grid.addSelectionListener(e -> updateEstimatedTime());

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
		
		// Update button states
		printButton.setEnabled(false);
		printButton.setText("Print (Batch in Progress...)");
		queueMoreButton.setEnabled(true);
		clearQueueButton.setEnabled(true);

		// Start batch with delay
		delayService.sendBatchJobsWithDelay(jobs, delay, simultaneousPrinters)
		.thenRun(() -> {
			getUI().ifPresent(ui -> ui.access(() -> {
				showNotification("All batch jobs completed!");
				queueMoreButton.setEnabled(false);
				clearQueueButton.setEnabled(false);
				updatePrintButtonState();
			}));
		})
		.exceptionally(ex -> {
			getUI().ifPresent(ui -> ui.access(() -> {
				// Check if it was aborted or error
				if (ex.getMessage() != null && ex.getMessage().contains("aborted")) {
					showNotification("Batch was cancelled");
				} else {
					showError("Error: " + ex.getMessage());
				}
				queueMoreButton.setEnabled(false);
				clearQueueButton.setEnabled(false);
				updatePrintButtonState();
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

    private void configureUpload() {
        upload.setAcceptedFileTypes(BambuConst.FILE_3MF);
        upload.addSucceededListener(e -> loadProjectFile(e.getFileName()));
        upload.setMaxFileSize((int) maxBodySize.asLongValue());
        upload.setDropLabel(new Span("Drop file here (max size: %dM)".formatted(maxBodySize.asLongValue() / 1_000_000)));
        upload.addFileRejectedListener(l -> {
            showError(l.getErrorMessage());
        });
		    // Add file removed listener to clear thumbnail and project data
		upload.addFileRemovedListener(e -> {
			thumbnailPlaceholder.setVisible(true);
			thumbnail.setVisible(false);
			thumbnail.setSrc(""); // Clear thumbnail to show placeholder
			closeProjectFile();
			plateLookup.setItems(List.of());
			plateLookup.clear();
		});
    }

    private void configureThumbnail() {
    thumbnail.addClassName(IMAGE_CLASS);
    thumbnail.addClickListener(l -> {
        // Only allow size toggle if image has a valid src
        String src = thumbnail.getSrc();
        if (src != null && !src.isEmpty()) {
            if (thumbnail.hasClassName(IMAGE_CLASS)) {
                thumbnail.removeClassName(IMAGE_CLASS);
            } else {
                thumbnail.addClassName(IMAGE_CLASS);
            }
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
		if (delayService.getQueuedJobCount() > 0 || delayService.isBatchRunning()) {
			Log.infof("Clearing %d queued job(s) on page load", delayService.getQueuedJobCount());
			delayService.abortAllJobs();
		}
		addClassName("batchprint-view");
		configureActions();
		configureDelayControls();
		configureCancelButtons();
		configureClearQueueButton();
		configureQueueMoreButton();
		configurePlateLookup();
		configureGrid();
		configureUpload();
		configureThumbnail();
		configureThumbnailPlaceholder();
		
		// Create actions div with delay controls included
		actions = newDiv("actions", 
			plateLookup,
			newDiv("detail", printTime, printWeight),
			printFilaments,
			newDiv("options",                          // ← Only checkboxes now
				skipSameSize, timelapse, bedLevelling, 
				flowCalibration, vibrationCalibration, skipFilamentMapping, enableDelay
			),
			newDiv("delay-controls",
				simultaneousPrintersField,
				delayHoursField,
				delayMinutesField,
				delaySecondsField,
				selectedPrintersSpan,      // NEW
				batchInfoSpan, 
				estimatedTimeSpan,
				queueStatusSpan,           // NEW
				remainingBatchesSpan,      // NEW
				totalDelaySpan             // NEW
			),
			newDiv("buttons",                          // ← Same buttons, new layout
				printButton, 
				refreshButton, 
				cancelSelectedButton, 
				cancelAllButton,
				queueMoreButton,
				clearQueueButton
			)
		);
		
		thumbnailPlaceholder.setVisible(true);
		thumbnail.setVisible(false);
    
		add(newDiv("header", thumbnailPlaceholder, thumbnail, actions, upload), grid);
		
		//add(newDiv("header", thumbnail, actions, newDiv("upload", upload)), grid);
		//add(newDiv("header", thumbnail, actions, upload), grid);
		
		final UI ui = attachEvent.getUI();
		createFuture(() -> ui.access(() -> {
			updateBulkStatus();
			updatePrintButtonState();
			updateActiveJobsDisplay();
		}), config.refreshInterval());
	}

    @Override
    protected void onDetach(final DetachEvent detachEvent) {
        super.onDetach(detachEvent);
		stopCountdown();
        closeProjectFile();
    }

	private void configureThumbnailPlaceholder() {
		thumbnailPlaceholder.addClassName("thumbnail-placeholder");
		thumbnailPlaceholder.setText("Upload a .3MF file to see preview");
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
			// Show thumbnail (empty), hide placeholder
			thumbnailPlaceholder.setVisible(false);
			thumbnail.setVisible(true);
			thumbnail.setSrc("");
			showError("No sliced plates found");
		} else {
			// Show thumbnail with image, hide placeholder
			thumbnailPlaceholder.setVisible(false);
			thumbnail.setVisible(true);
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
