package com.tfyre.bambu.view.dashboard;

import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jboss.logging.Logger;
import com.tfyre.bambu.printer.BambuPrinters;
import com.vaadin.flow.component.html.Div;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import java.time.Duration;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard")
@RolesAllowed({ SystemRoles.ROLE_ADMIN, SystemRoles.ROLE_NORMAL })
public class Dashboard extends Div {

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

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        final List<Runnable> runnables = new ArrayList<>();
        final UI ui = attachEvent.getUI();
        addClassName("dashboard-view");

        printers.getPrinters()
                .stream().sorted(Comparator.comparing(BambuPrinter::getName))
                .map(printer -> handlePrinter(printer, runnables::add))
                .forEach(this::add);
        future = ses.scheduleAtFixedRate(() -> ui.access(() -> runnables.forEach(Runnable::run)), 0, INTERVAL.getSeconds(), TimeUnit.SECONDS);
    }

    private Component handlePrinter(final BambuPrinter printer, final Consumer<Runnable> consumer) {
        final DashboardPrinter card = cardInstance.get();
        consumer.accept(card::update);
        return card.build(printer, true);
    }

    @Override
    protected void onDetach(final DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (future != null) {
            future.cancel(true);
        }
    }

}
