package com.tfyre.bambu.view.batchprint;

import com.tfyre.bambu.view.ViewHelper;
import com.vaadin.flow.component.html.Div;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface FilamentHelper extends ViewHelper {

    default long mapFilamentColor(final String color) {
        String _color = color.replace("#", "");
        if (_color.length() > 6) {
            _color = _color.substring(0, 6);
        }
        return Integer.parseInt(_color, 16);
    }

    default Div setupFilament(final Div div, final String text, final long color) {
        div.setText(text);
        if (color < 0) {
            return div;
        }
        div.getStyle().setBackgroundColor("#%06X".formatted(color));
        //https://alienryderflex.com/hsp.html
        final long brightness = (((color >> 16 & 0xff) * 299)
                + ((color >> 8 & 0xff) * 587)
                + ((color & 0xff) * 114))
                / 1000;
        if (brightness <= 125) {
            div.addClassName("contrast");
        }
        return div;
    }

    default Div newFilament(final PlateFilament filament) {
        return setupFilament(newDiv("filament"), filament.type().getDescription(), filament.color());
    }

}
