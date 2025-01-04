package com.tfyre.bambu.server;

import com.tfyre.bambu.mqtt.AbstractMqttController;
import com.tfyre.bambu.server.BambuConfig.Printer;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.StartupListener;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Startup
@ApplicationScoped
public class CamelController extends AbstractMqttController implements StartupListener {

    @Inject
    BambuConfig config;
    @Inject
    Scheduler scheduler;

    private final List<BambuPrinterProcessor> list = new ArrayList<>();

    @Override
    public void onCamelContextStarted(final CamelContext context, final boolean alreadyStarted) throws Exception {

    }

    @Override
    public void onCamelContextFullyStarted(final CamelContext context, final boolean alreadyStarted) throws Exception {
        Log.info("Starting all printers");
        list.forEach(p -> p.start(context, scheduler));
    }

    @Override
    public void configure() throws Exception {
        getCamelContext().addStartupListener(this);
        config.printers().forEach(this::configurePrinter);
        Log.info("configured");
    }

    private BambuPrinterProcessor newPrinter(final Endpoint endpoint, final String name) {
        final BambuPrinterProcessor printer = new BambuPrinterProcessor(endpoint, name);
        list.add(printer);
        return printer;
    }

    private void configurePrinter(final String name, final Printer config) {
        if (!config.enabled()) {
            Log.infof("Skipping: %s", name);
            return;
        }
        Log.infof("Configuring: %s", name);

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
