package com.tfyre.bambu.view;

import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.BambuPrinterConsumer;
import com.tfyre.bambu.printer.BambuPrinterException;
import com.tfyre.bambu.printer.BambuPrinters;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.microprofile.context.ManagedExecutor;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "maintenance", layout = MainLayout.class)
@PageTitle("Maintenance")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class MaintenanceView extends VerticalLayout implements NotificationHelper, GridHelper<BambuPrinters.PrinterDetail>, UpdateHeader {

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

    private final Grid<BambuPrinters.PrinterDetail> grid = new Grid<>();

    @Override
    public Grid<BambuPrinters.PrinterDetail> getGrid() {
        return grid;
    }

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        addClassName("maintenance-view");
        setSizeFull();
        configureGrid();
        add(grid);
        refreshItems();
    }

    private Button newButton(final BambuPrinters.PrinterDetail pd, final String action, final VaadinIcon icon, final BambuPrinterConsumer<String> consumer) {
        final Button result = new Button("", new Icon(icon), l -> {
            final Optional<UI> ui = getUI();
            executor.submit(() -> {
                try {
                    consumer.accept(pd.name());
                } catch (BambuPrinterException ex) {
                    Log.error(ex.getMessage(), ex);
                    ui.get().access(() -> {
                        showError(ex.getMessage());
                        refreshItems();
                    });
                }
            });
        });
        result.setTooltipText(action);
        return result;
    }

    private void refreshItems() {
        grid.setItems(printers.getPrintersDetail());
    }

    private <T> Comparator<BambuPrinters.PrinterDetail> getODTComparator(
            final Function<BambuPrinter, Optional<T>> function1,
            final Function<T, OffsetDateTime> function2) {
        return Comparator.comparing(pd ->
                function1.apply(pd.printer())
                        .map(function2)
                        .map(odt -> odt.toEpochSecond())
                        .orElse(0l)
        );
    }

    private void configureGrid() {
        final Grid.Column<BambuPrinters.PrinterDetail> colName
                = setupColumn("Name", pd -> pd.printer().getName());
        setupColumnCheckBox("Running", pd -> pd.isRunning());
        setupColumn("Last Status", pd -> pd.printer().getStatus().map(m -> DTF.format(m.lastUpdated())).orElse("--"))
                .setSortable(true).setComparator(getODTComparator(BambuPrinter::getStatus, BambuPrinter.Message::lastUpdated));
        setupColumn("Last Full Status", pd -> pd.printer().getFullStatus().map(m -> DTF.format(m.lastUpdated())).orElse("--"))
                .setSortable(true).setComparator(getODTComparator(BambuPrinter::getFullStatus, BambuPrinter.Message::lastUpdated));
        setupColumn("Last Thumbnail", pd -> pd.printer().getThumbnail().map(m -> DTF.format(m.lastUpdated())).orElse("--"))
                .setSortable(true).setComparator(getODTComparator(BambuPrinter::getThumbnail, BambuPrinter.Thumbnail::lastUpdated));

        grid.addComponentColumn(v -> {
            final Button gcode = new Button("", new Icon(VaadinIcon.COG), l -> GCodeDialog.show(v.printer()));
            gcode.setTooltipText("Send GCode");
            return new HorizontalLayout(
                    newButton(v, "Enable", VaadinIcon.PLAY, printers::startPrinter),
                    newButton(v, "Disable", VaadinIcon.STOP, printers::stopPrinter),
                    gcode
            );
        });

        grid.sort(GridSortOrder.asc(colName).build());
    }

    @Override
    public void updateHeader(final HasComponents component) {
        component.add(new Button("Refresh", new Icon(VaadinIcon.REFRESH), l -> refreshItems()));
    }

}
