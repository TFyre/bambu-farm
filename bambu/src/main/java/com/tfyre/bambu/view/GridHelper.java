package com.tfyre.bambu.view;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.function.ValueProvider;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface GridHelper<ENTITY> {

    Grid<ENTITY> getGrid();

    default ComponentRenderer<Checkbox, ENTITY> getCheckboxRenderer(final ValueProvider<ENTITY, Boolean> valueProvider) {
        return new ComponentRenderer<>((source) -> {
            final Checkbox result = new Checkbox();
            result.setReadOnly(true);
            result.setValue(valueProvider.apply(source));
            return result;
        });
    }

    default <T> Grid.Column<ENTITY> setupColumn(final String name, final ValueProvider<ENTITY, T> valueProvider) {
        return getGrid().addColumn(valueProvider)
                .setHeader(name);
    }

    default Grid.Column<ENTITY> setupColumn(final String name, final ComponentRenderer<?, ENTITY> component) {
        return getGrid().addColumn(component)
                .setHeader(name);
    }

    default Grid.Column<ENTITY> setupColumnCheckBox(final String name, final ValueProvider<ENTITY, Boolean> valueProvider) {
        return getGrid().addColumn(getCheckboxRenderer(valueProvider))
                .setHeader(name);
    }
}
