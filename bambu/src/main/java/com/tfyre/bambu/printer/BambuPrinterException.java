package com.tfyre.bambu.printer;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class BambuPrinterException extends Exception {

    public BambuPrinterException() {
    }

    public BambuPrinterException(final String message) {
        super(message);
    }

    public BambuPrinterException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BambuPrinterException(final Throwable cause) {
        super(cause);
    }

}
