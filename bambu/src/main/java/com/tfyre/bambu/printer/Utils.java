package com.tfyre.bambu.printer;

import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class Utils {

    private static final Logger log = Logger.getLogger(Utils.class.getName());

    public static double parseDouble(final String printerName, final String value, final double defaultValue) {
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            log.errorf("%s: Cannot parseInt [%s]", printerName, value);
            return defaultValue;
        }
    }

    public static int parseInt(final String printerName, final String value, final int defaultValue) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            log.errorf("%s: Cannot parseInt [%s]", printerName, value);
            return defaultValue;
        }
    }
}
