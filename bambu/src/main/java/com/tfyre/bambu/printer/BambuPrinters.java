package com.tfyre.bambu.printer;

import com.tfyre.bambu.BambuConfig;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface BambuPrinters {

    Collection<BambuPrinter> getPrinters();

    Collection<PrinterDetail> getPrintersDetail();

    Optional<BambuPrinter> getPrinter(final String name);

    Optional<PrinterDetail> getPrinterDetail(final String name);

    PrinterDetail newPrinter(final String id, final String name, final BambuConfig.Printer config, final Endpoint endpoint);

    void startPrinter(final String name) throws BambuPrinterException;

    void stopPrinter(final String name) throws BambuPrinterException;

    void startPrinters() throws BambuPrinterException;

    void stopPrinters() throws BambuPrinterException;

    record PrinterDetail(String id, String name, AtomicBoolean running, BambuConfig.Printer config, BambuPrinter printer, Processor processor, BambuPrinterStream stream) {

        public boolean isRunning() {
            return running.get();
        }

    }

}
