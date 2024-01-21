package com.tfyre.bambu.view;

import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.printer.BambuPrinterConsumer;
import com.tfyre.bambu.printer.BambuPrinterException;
import com.tfyre.bambu.printer.BambuPrinters;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
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
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Optional;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "maintenance", layout = MainLayout.class)
@PageTitle("Maintenance")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class MaintenanceView extends VerticalLayout implements ShowInterface, GridHelper<BambuPrinters.PrinterDetail> {

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

    private final Grid<BambuPrinters.PrinterDetail> grid = new Grid<>();

    @Override
    public Grid<BambuPrinters.PrinterDetail> getGrid() {
        return grid;
    }

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        setSizeFull();
        configureGrid();
        add(buildToolbar(), grid);
        refreshItems();
    }

    private Component buildToolbar() {
        final HorizontalLayout result = new HorizontalLayout();
        result.setWidthFull();
        result.add(new Button("Refresh", new Icon(VaadinIcon.REFRESH), l -> refreshItems()));
        return result;
    }

    private Button newButton(final BambuPrinters.PrinterDetail pd, final String action, final VaadinIcon icon, final BambuPrinterConsumer<String> consumer) {
        return new Button(action, new Icon(icon), l -> {
            final Optional<UI> ui = getUI();
            executor.submit(() -> {
                try {
                    consumer.accept(pd.name());
                } catch (BambuPrinterException ex) {
                    log.error(ex.getMessage(), ex);
                    ui.get().access(() -> {
                        showError(ex.getMessage());
                        refreshItems();
                    });
                }
            });

        });
    }

    private void refreshItems() {
        grid.setItems(printers.getPrintersDetail());
    }

    private void configureGrid() {
        final Grid.Column<BambuPrinters.PrinterDetail> colName
                = setupColumn("Name", pd -> pd.printer().getName());
        setupColumnCheckBox("Running", pd -> pd.isRunning());
        setupColumn("Last Status", pd -> pd.printer().getStatus().map(m -> DTF.format(m.lastUpdated())).orElse("--"));
        setupColumn("Last Full Status", pd -> pd.printer().getFullStatus().map(m -> DTF.format(m.lastUpdated())).orElse("--"));
        setupColumn("Last Thumbnail", pd -> pd.printer().getThumbnail().map(m -> DTF.format(m.lastUpdated())).orElse("--"));

        grid.addComponentColumn(v -> {
            final HorizontalLayout result = new HorizontalLayout();
            result.add(newButton(v, "Enable", VaadinIcon.PLAY, printers::startPrinter));
            result.add(newButton(v, "Disable", VaadinIcon.STOP, printers::stopPrinter));
            return result;
        });

        grid.sort(GridSortOrder.asc(colName).build());
    }

}
