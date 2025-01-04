package com.tfyre.bambu.view;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.BambuPrinters;
import com.tfyre.bambu.view.dashboard.DashboardPrinter;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.Optional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "printer", layout = MainLayout.class)
@PageTitle("Printer")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class PrinterView extends PushDiv implements HasUrlParameter<String>, NotificationHelper, UpdateHeader {

    @Inject
    BambuPrinters printers;
    @Inject
    Instance<DashboardPrinter> cardInstance;
    @Inject
    BambuConfig config;

    private Optional<BambuPrinter> _printer = Optional.empty();
    private final ComboBox<BambuPrinter> comboBox = new ComboBox<>();
    private final Div content = new Div();

    @Override
    public void setParameter(final BeforeEvent event, @OptionalParameter final String printerName) {
        _printer = printers.getPrinter(printerName);
    }

    private void buildPrinter(final BambuPrinter printer) {
        content.removeAll();
        final DashboardPrinter card = cardInstance.get();
        content.add(card.build(printer, false));
        final UI ui = getUI().get();
        createFuture(() -> ui.access(card::update), config.refreshInterval());
    }

    private Component buildContent() {
        content.setClassName("content");
        return content;
    }

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        addClassName("printer-view");
        add(buildContent());
        comboBox.setItemLabelGenerator(BambuPrinter::getName);
        comboBox.setItems(printers.getPrinters().stream().sorted(Comparator.comparing(BambuPrinter::getName)).toList());
        comboBox.addValueChangeListener(l -> buildPrinter(l.getValue()));
        _printer.ifPresent(comboBox::setValue);
    }

    @Override
    public void updateHeader(final HasComponents component) {
        component.add(new Span("Printers"), comboBox);
    }

}
