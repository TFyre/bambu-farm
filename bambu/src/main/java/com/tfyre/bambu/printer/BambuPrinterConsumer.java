package com.tfyre.bambu.printer;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@FunctionalInterface
public interface BambuPrinterConsumer<T> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     */
    void accept(T t) throws BambuPrinterException;

}
