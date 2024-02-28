package com.tfyre.bambu.view;

import com.tfyre.bambu.YesNoCancelDialog;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import java.time.Duration;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface ViewHelper {

    Logger getLogger();

    default double parseDouble(final String printerName, final String value, final double defaultValue) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            getLogger().errorf("%s: Cannot parseDouble [%s]", printerName, value);
            return defaultValue;
        }
    }

    default double parseDouble(final String value, final double defaultValue) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            getLogger().errorf("Cannot parseDouble [%s]", value);
            return defaultValue;
        }
    }

    default int parseInt(final String printerName, final String value, final int defaultValue) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            getLogger().errorf("s: Cannot parseInt [%s]", printerName, value);
            return defaultValue;
        }
    }

    default int parseInt(final String value, final int defaultValue) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            getLogger().errorf("Cannot parseInt [%s]", value);
            return defaultValue;
        }
    }

    default String formatTime(final Duration duration) {
        final StringBuilder sb = new StringBuilder();
        final long days = duration.toDays();
        if (days > 0) {
            sb.append(days)
                    .append(" day(s) ");
        }
        sb
                .append(duration.toHoursPart())
                .append(" hour(s) ")
                .append(duration.toMinutesPart())
                .append(" minute(s)");
        return sb.toString();
    }

    default Div newDiv(final String className, final Component... components) {
        final Div result = new Div(components);
        result.addClassName(className);
        return result;
    }

    default Span newSpan(final String className) {
        final Span result = new Span();
        result.addClassName(className);
        return result;
    }

    default ProgressBar newProgressBar() {
        final ProgressBar result = new ProgressBar(0.0, 100.0);
        result.addClassName("progress");
        return result;
    }

    default void doConfirm(final Runnable runnable) {
        YesNoCancelDialog.show("Are you sure?", ync -> {
            if (!ync.isConfirmed()) {
                return;
            }
            runnable.run();
        });
    }

}
