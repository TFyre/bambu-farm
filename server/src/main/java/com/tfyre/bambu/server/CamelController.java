package com.tfyre.bambu.server;

import com.tfyre.bambu.mqtt.AbstractMqttController;
import com.tfyre.bambu.server.BambuConfig.Printer;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Startup
@ApplicationScoped
public class CamelController extends AbstractMqttController {

    @Inject
    BambuConfig config;
    @Inject
    Logger log;
    @Inject
    Scheduler scheduler;
    @Inject
    CamelContext camelContext;

    @Override
    public void configure() throws Exception {
        config.printers().forEach(this::configurePrinter);
        log.info("configured");
    }

    private BambuPrinterProcessor newPrinter(final Endpoint endpoint, final String name) {
        final BambuPrinterProcessor printer = new BambuPrinterProcessor(scheduler, endpoint, name);
        try {
            camelContext.addStartupListener(printer);
        } catch (Exception ex) {
            log.errorf(ex, "Error registring startupLister %s", name);
        }
        return printer;
    }

    private void configurePrinter(final String name, final Printer config) {
        if (!config.enabled()) {
            log.infof("Skipping: %s", name);
            return;
        }
        log.infof("Configuring: %s", name);

        final Endpoint ep = getPrinterEndpoint(name);
        final BambuPrinterProcessor printer = newPrinter(ep, name);

        //producer
        from(ep)
                .id("producer-%s".formatted(name))
                .group(name)
                .to(getMqttEndpoint(getTopic(config.reportTopic(), config.deviceId(), "report"), config.url(), config.username(), config.accessCode()));
        //consumer
        from(getMqttEndpoint(getTopic(config.requestTopic(), config.deviceId(), "request"), config.url(), config.username(), config.accessCode()))
                .id("consumer-%s".formatted(name))
                .group(name)
                .process(printer);
    }
}
