package com.tfyre.bambu.printer;

import com.tfyre.bambu.BambuConfig;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@ApplicationScoped
public class BambuPrintersImpl implements BambuPrinters {

    @Inject
    Logger log;
    @Inject
    Instance<BambuPrinter> _bambuPrinter;
    @Inject
    Instance<BambuPrinterStream> _bambuPrinterStream;
    @Inject
    CamelContext camelContext;
    @Inject
    Scheduler scheduler;
    @Inject
    BambuConfig bambuConfig;

    private final Map<String, PrinterDetail> map = new HashMap<>();

    public BambuPrintersImpl() {
    }

    @Override
    public Collection<BambuPrinter> getPrinters() {
        return map.values().stream()
                .filter(PrinterDetail::isRunning)
                .map(PrinterDetail::printer)
                .toList();
    }

    @Override
    public Collection<PrinterDetail> getPrintersDetail() {
        return Collections.unmodifiableCollection(map.values());
    }

    private Consumer<BambuPrinter.Thumbnail> getConsumer(final String name) {
        return o -> {
            log.errorf("%s: no image consumer, BambuPrinter does not extend BambuPrinterImpl", name);
        };
    }

    @Override
    public PrinterDetail newPrinter(final String id, final String name, final BambuConfig.Printer config, final Endpoint endpoint) {
        final BambuPrinter printer = _bambuPrinter.get();
        Consumer<BambuPrinter.Thumbnail> consumer = getConsumer(name);
        if (printer instanceof BambuPrinterImpl impl) {
            impl.setup(scheduler, name, config, endpoint, id);
            consumer = impl::setThumbnail;
        }

        if (!Processor.class.isInstance(printer)) {
            throw new RuntimeException("%s does not implement %s".formatted(printer.getClass().getName(), Processor.class.getName()));
        }

        final BambuPrinterStream stream = _bambuPrinterStream.get();
        final boolean enabled = bambuConfig.remoteView() && config.stream().enabled() && !config.stream().liveView();
        stream.setup(enabled, scheduler, name, config, consumer);

        final PrinterDetail result = new PrinterDetail(id, name, new AtomicBoolean(), config, printer, Processor.class.cast(printer), stream);
        map.put(name, result);
        return result;
    }

    private List<Route> getRoutes(final PrinterDetail detail) {
        return camelContext.getRoutes()
                .stream()
                .filter(r -> detail.name().equals(r.getGroup()))
                .toList();
    }

    private void startPrinter(final PrinterDetail detail) throws BambuPrinterException {
        if (detail.isRunning()) {
            return;
        }
        log.infof("%s: starting", detail.name());
        try {
            for (final Route r : getRoutes(detail)) {
                try {
                    camelContext.getRouteController().startRoute(r.getRouteId());
                } catch (Exception ex) {
                    throw new BambuPrinterException("%s: Error starting route: %s".formatted(detail.name(), r.getRouteId()), ex);
                }
            }
            if (detail.printer() instanceof BambuPrinterImpl impl) {
                impl.start();
            }
            detail.stream().start();
            detail.running().set(true);
            log.infof("%s: started", detail.name());
        } catch (Throwable t) {
            throw new BambuPrinterException("Unknown Exception: %s".formatted(t), t);
        }
    }

    private void stopPrinter(final PrinterDetail detail) throws BambuPrinterException {
        if (!detail.isRunning()) {
            return;
        }
        log.infof("%s: stopping", detail.name());
        detail.running().set(false);
        try {
            detail.stream().stop();
            if (detail.printer() instanceof BambuPrinterImpl impl) {
                impl.stop();
            }
            for (final Route r : getRoutes(detail)) {
                try {
                    camelContext.getRouteController().stopRoute(r.getRouteId());
                } catch (Exception ex) {
                    throw new BambuPrinterException("%s: Error starting route: %s".formatted(detail.name(), r.getRouteId()), ex);
                }
            }
            log.infof("%s: stopped", detail.name());
        } catch (Throwable t) {
            throw new BambuPrinterException("Unknown Exception: %s".formatted(t), t);
        }
    }

    @Override
    public Optional<BambuPrinter> getPrinter(final String name) {
        return Optional.ofNullable(map.get(name))
                .map(PrinterDetail::printer);
    }

    @Override
    public Optional<PrinterDetail> getPrinterDetail(final String name) {
        return Optional.ofNullable(map.get(name));
    }

    private PrinterDetail getPrinterDetailE(final String name) throws BambuPrinterException {
        return getPrinterDetail(name).orElseThrow(() -> new BambuPrinterException("%s not found".formatted(name)));
    }

    @Override
    public void startPrinter(String name) throws BambuPrinterException {
        startPrinter(getPrinterDetailE(name));
    }

    @Override
    public void stopPrinter(String name) throws BambuPrinterException {
        stopPrinter(getPrinterDetailE(name));
    }

    private void stopStart(final BambuPrinterConsumer<PrinterDetail> consumer) throws BambuPrinterException {
        final List<String> errors = new ArrayList<>();
        for (final PrinterDetail pd : map.values()) {
            try {
                consumer.accept(pd);
            } catch (BambuPrinterException ex) {
                final String message = "%s: %s".formatted(pd.name(), ex.getMessage());
                errors.add(message);
                log.error(message, ex);
            }
        }

        if (errors.isEmpty()) {
            return;
        }
        throw new BambuPrinterException("Errors with stopStart: %s".formatted(errors));
    }

    @Override
    public void startPrinters() throws BambuPrinterException {
        stopStart(this::startPrinter);
    }

    @Override
    public void stopPrinters() throws BambuPrinterException {
        stopStart(this::stopPrinter);
    }

    @PreDestroy
    public void preDestroy() {
        log.info("Stopping Printers");
        try {
            stopPrinters();
        } catch (BambuPrinterException ex) {
            log.error(ex);
        }
    }

}
