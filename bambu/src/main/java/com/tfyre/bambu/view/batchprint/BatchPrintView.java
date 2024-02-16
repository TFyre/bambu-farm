package com.tfyre.bambu.view.batchprint;

import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.bambu.security.SecurityUtils;
import com.tfyre.bambu.view.GridHelper;
import com.tfyre.bambu.view.NotificationHelper;
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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializablePredicate;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.runtime.configuration.MemorySize;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "batchprint", layout = MainLayout.class)
@PageTitle("Batch Print")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class BatchPrintView extends VerticalLayout implements NotificationHelper, FilamentHelper, GridHelper<PrinterMapping> {

    private static final SerializablePredicate<PrinterMapping> PREDICATE = pm -> true;

    @Inject
    Logger log;
    @Inject
    BambuPrinters printers;
    @Inject
    Instance<PrinterMapping> printerMappingInstance;
    @Inject
    Instance<ProjectFile> projectFileInstance;
    @Inject
    ManagedExecutor executor;
    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxBodySize;

    private final ComboBox<Plate> plateLookup = new ComboBox<>("Plate Id");
    private final Grid<PrinterMapping> grid = new Grid<>();
    private final HeaderRow headerRow = grid.appendHeaderRow();
    private final Image thumbnail = new Image();
    private final Span printTime = new Span();
    private final Span printWeight = new Span();
    private final Div printFilaments = newDiv("filaments");
    private final Checkbox useAMS = new Checkbox("Use AMS", true);
    private final Checkbox timelapse = new Checkbox("Timelapse", true);
    private final Checkbox bedLevelling = new Checkbox("Bed Levelling", true);
    private GridListDataView<PrinterMapping> dataView;
    private final Div actions = newDiv("actions", plateLookup,
            newDiv("detail", printTime, printWeight),
            printFilaments,
            newDiv("options", useAMS, timelapse, bedLevelling),
            newDiv("buttons",
                    new Button("Print", VaadinIcon.PRINT.create(), l -> printAll()),
                    new Button("Refresh", VaadinIcon.REFRESH.create(), l -> dataView.refreshAll())
            ));
    private final FileBuffer buffer = new FileBuffer();
    private final Upload upload = new Upload(buffer);
    private ProjectFile projectFile;
    private List<PrinterMapping> printerMappings = List.of();
    private SerializablePredicate<PrinterMapping> predicate = PREDICATE;

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public Grid<PrinterMapping> getGrid() {
        return grid;
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
        printerMappings.forEach(pm -> pm.setPlate(plate));
        grid.getDataProvider().refreshAll();
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

    private Component newCheckbox(final boolean isIdle) {
        final Checkbox result = new Checkbox();
        result.setValue(isIdle);
        result.setReadOnly(true);
        return result;
    }

    private void configureGrid() {
        final Grid.Column<PrinterMapping> colName
                = setupColumnFilter("Name", pm -> pm.getPrinterDetail().printer().getName()).setFlexGrow(2);
        setupColumn("Plate Id", pm -> Optional.ofNullable(plateLookup.getValue()).map(Plate::name).orElse("")).setFlexGrow(1);
        setupColumnFilter("Printer Status", pm -> pm.getPrinterDetail().printer().getPrintType()).setFlexGrow(2);
        grid.addComponentColumn(pm -> newCheckbox(pm.getPrinterDetail().printer().isIdle())).setHeader("Printer Idle").setFlexGrow(1);
        grid.addComponentColumn(PrinterMapping::getBulkStatus).setHeader("Bulk Status").setFlexGrow(2);
        grid.addComponentColumn(PrinterMapping::getFilamentMapping).setHeader("Filament Mapping").setFlexGrow(3);

        grid.getColumns().forEach(c -> c.setResizable(true));
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);

        grid.sort(GridSortOrder.asc(colName).build());
        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        final Optional<UI> ui = getUI();
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
        log.infof("printAll: user[%s] ip[%s] file[%s] printers[%s]", user, ip, projectFile.getFilename(),
                selected.stream().map(pm -> pm.getPrinterDetail().name()).toList());
        selected.forEach(pm -> executor.submit(() -> pm.sendPrint(projectFile, useAMS.getValue(), timelapse.getValue(), bedLevelling.getValue())));
        showNotification("Queued: %d".formatted(selected.size()));
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

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        addClassName("batchprint-view");
        setSizeFull();
        configurePlateLookup();
        configureGrid();
        upload.setAcceptedFileTypes(BambuConst.FILE_3MF);
        upload.addSucceededListener(e -> loadProjectFile(e.getFileName()));
        upload.setMaxFileSize((int) maxBodySize.asLongValue());
        upload.setDropLabel(new Span("Drop file here (max size: %dM)".formatted(maxBodySize.asLongValue() / 1_000_000)));
        upload.addFileRejectedListener(l -> {
            showError(l.getErrorMessage());
        });
        headerVisible(false);
        add(newDiv("header", thumbnail, actions, newDiv("upload", upload)), grid);
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
