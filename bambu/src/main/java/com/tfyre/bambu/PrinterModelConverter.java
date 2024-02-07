package com.tfyre.bambu;

import com.tfyre.bambu.printer.BambuConst;
import org.eclipse.microprofile.config.spi.Converter;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class PrinterModelConverter implements Converter<BambuConst.PrinterModel> {

    @Override
    public BambuConst.PrinterModel convert(final String value) throws IllegalArgumentException, NullPointerException {
        return BambuConst.PrinterModel.fromModel(value)
                .orElseThrow(() -> new IllegalArgumentException("[%s] cannot be converted to PrinterModel".formatted(value)));
    }

}
