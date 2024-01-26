package com.tfyre.bambu.view;

import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.bambu.view.dashboard.Dashboard;
import com.tfyre.bambu.view.dashboard.DashboardPrinter;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "printer", layout = MainLayout.class)
@PageTitle("Printer")
@RolesAllowed({SystemRoles.ROLE_ADMIN})
public class PrinterView extends Div implements HasUrlParameter<String>, ShowInterface {

    private static final Duration INTERVAL = Duration.ofSeconds(1);

    @Inject
    Logger log;
    @Inject
    BambuPrinters printers;
    @Inject
    ScheduledExecutorService ses;
    @Inject
    Instance<DashboardPrinter> cardInstance;

    private ScheduledFuture<?> future;
    private Optional<BambuPrinter> _printer = Optional.empty();
    private final ComboBox<BambuPrinter> comboBox = new ComboBox<>();
    private final Div content = new Div();

    @Override
    public void setParameter(final BeforeEvent event, @OptionalParameter final String printerName) {
        _printer = printers.getPrinter(printerName);
    }

    private void buildPrinter(final BambuPrinter printer) {
        cancelFuture();
        content.removeAll();
        final DashboardPrinter card = cardInstance.get();
        content.add(card.build(printer, false));
        final UI ui = getUI().get();
        future = ses.scheduleAtFixedRate(() -> ui.access(() -> card.update()), 0, INTERVAL.getSeconds(), TimeUnit.SECONDS);
    }

    private Component buildToolbar() {
        comboBox.setItemLabelGenerator(BambuPrinter::getName);
        comboBox.setItems(printers.getPrinters().stream().sorted(Comparator.comparing(BambuPrinter::getName)).toList());
        comboBox.addValueChangeListener(l -> buildPrinter(l.getValue()));
        final HorizontalLayout result = new HorizontalLayout(new Span("Printers"), comboBox, new RouterLink("Back to Dashboard", Dashboard.class));
        result.setWidthFull();
        result.setAlignItems(Alignment.CENTER);
        return result;
    }

    private Component buildContent() {
        content.setClassName("content");
        return content;
    }

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        addClassName("printer-view");
        add(buildToolbar(), buildContent());
        _printer.ifPresent(comboBox::setValue);
    }

    private void cancelFuture() {
        if (future == null) {
            return;
        }

        future.cancel(true);
        future = null;
    }

    @Override
    protected void onDetach(final DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        cancelFuture();
    }

}
