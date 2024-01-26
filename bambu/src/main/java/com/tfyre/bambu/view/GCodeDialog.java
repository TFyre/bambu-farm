package com.tfyre.bambu.view;

import com.tfyre.bambu.printer.BambuPrinter;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextArea;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class GCodeDialog {

    public static void show(final BambuPrinter printer) {
        final Dialog d = new Dialog();
        d.setHeaderTitle("Send GCode (No Validation!!)");
        final TextArea text = new TextArea();
        text.setWidthFull();
        text.setHeight(95, Unit.PERCENTAGE);
        d.add(text);
        final Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_ERROR);
        final Button ok = new Button("OK", e -> {
            d.close();
            printer.commandPrintGCodeLine(text.getValue().trim().replaceAll("\n", "\\\n"));
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(cancel, ok);
        d.setWidth(80, Unit.PERCENTAGE);
        d.setHeight(80, Unit.PERCENTAGE);
        d.open();
    }

}
