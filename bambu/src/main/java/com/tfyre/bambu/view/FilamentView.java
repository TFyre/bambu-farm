package com.tfyre.bambu.view;

import com.tfyre.bambu.YesNoCancelDialog;
import com.tfyre.bambu.model.Tray;
import com.tfyre.bambu.printer.BambuConst;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.Filament;
import com.tfyre.bambu.printer.Utils;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import jakarta.xml.bind.DatatypeConverter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class FilamentView extends FormLayout implements ShowInterface {

    private static final Logger log = Logger.getLogger(FilamentView.class.getName());

    private final ComboBox<Filament> filaments = new ComboBox<>("Filament");
    private final ColorField color = new ColorField("Custom Color");
    private final IntegerField minTemp = new IntegerField("Min Temperature");
    private final IntegerField maxTemp = new IntegerField("Max Temperature");

    private static Optional<Tray> fromPrinter(final BambuPrinter printer, final int amsId, final int trayId) {
        final String ams = Integer.toString(amsId);
        final String tray = Integer.toString(trayId);
        return printer.getFullStatus()
                .filter(m -> m.message().hasPrint())
                .map(m -> m.message().getPrint())
                .filter(p -> p.hasAms())
                .map(p -> p.getAms())
                .flatMap(a -> a.getAmsList().stream().filter(s -> ams.equals(s.getId()))
                        .findAny()
                )
                .flatMap(trays -> trays.getTrayList().stream().filter(t -> tray.equals(t.getId()))
                        .findAny()
                );
    }

    public static void show(final BambuPrinter printer, final int amsId, final int trayId) {
        final FilamentView view = new FilamentView().build(printer.getName(), fromPrinter(printer, amsId, trayId));

        YesNoCancelDialog.show(List.of(view), "Change filament?", ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            final Filament filament = view.filaments.getValue();
            if (filament == null || filament == Filament.UNKNOWN) {
                view.showError("Please select a valid filament");
                return;
            }
            final Optional<String> color = view.getColor();
            if (color.isEmpty()) {
                view.showError("Please select a valid color");
                return;
            }

            printer.commandFilamentSetting(amsId, trayId, filament, color.get(),
                    view.minTemp.getValue(), view.maxTemp.getValue());
        });

    }

    private Optional<String> getColor() {
        final String c = color.getValue();
        if (c == null || c.length() != 7) {
            return Optional.empty();
        }
        if (!c.startsWith("#")) {
            return Optional.empty();
        }

        final String result = c.substring(1);
        try {
            DatatypeConverter.parseHexBinary(result);
        } catch (IllegalArgumentException ex) {
            log.errorf("[%s] is not a valid color: %s", result, ex.getMessage());
            return Optional.empty();
        }

        return Optional.of("%sFF".formatted(result.toUpperCase()));
    }

    private List<Filament> getFilaments() {
        return EnumSet.allOf(Filament.class).stream()
                .sorted(Comparator.comparing(Filament::getDescription, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void setTemp(final IntegerField field) {
        field.setMin(0);
        field.setMax(BambuConst.TEMPERATURE_MAX_NOZZLE);
        field.setStepButtonsVisible(true);
    }

    public FilamentView build(final String printerName, final Optional<Tray> tray) {
        addClassName("filament-view");
        filaments.setItemLabelGenerator(Filament::getDescription);
        filaments.setItems(getFilaments());

        setTemp(minTemp);
        setTemp(maxTemp);

        minTemp.setValue(190);
        maxTemp.setValue(220);

        tray.ifPresent(t -> {
            filaments.setValue(Filament.getFilament(t.getTrayInfoIdx()).orElse(Filament.UNKNOWN));
            color.setValue("#%.6s".formatted(t.getTrayColor()));
            minTemp.setValue(Utils.parseInt(printerName, t.getNozzleTempMin(), 190));
            maxTemp.setValue(Utils.parseInt(printerName, t.getNozzleTempMax(), 220));
        });

        add(filaments, color, minTemp, maxTemp);
        setColspan(color, 2);

        return this;
    }

    public class ColorField extends CustomField<String> {

        private final Input input = new Input();

        public ColorField(final String label) {
            input.setType("color");
            setLabel(label);

            final Div presets = new Div();
            add(input, presets);
            presets.addClassName("presets");

            EnumSet.allOf(BambuConst.Color.class).forEach(c -> {
                final Div preset = new Div();
                preset.addClassName("preset");
                preset.getStyle().setBackgroundColor(c.getHtmlColor());
                preset.addClickListener(l -> setPresentationValue(c.getHtmlColor()));
                presets.add(preset);
            });

        }

        @Override
        protected String generateModelValue() {
            return input.getValue();
        }

        @Override
        protected void setPresentationValue(final String color) {
            input.setValue(color);
        }

    }

}
