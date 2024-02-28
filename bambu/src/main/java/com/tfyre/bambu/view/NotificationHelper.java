package com.tfyre.bambu.view;

import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import java.time.Duration;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface NotificationHelper {

    default Notification getNotificationSpan(final String content) {
        final Span s = new Span(content);
        if (content.contains("\n")) {
            s.setWhiteSpace(HasText.WhiteSpace.PRE);
        }
        final Notification n = new Notification(s);
        s.addClickListener(e -> n.close());
        return n;
    }

    default void showError(final String error, final int duration) {
        final Notification n = getNotificationSpan(error);
        n.setDuration(duration);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        n.setPosition(Notification.Position.BOTTOM_END);
        n.open();
    }

    default void showError(final String error) {
        showError(error, -1);
    }

    default void showError(final String error, final Duration duration) {
        showError(error, (int) duration.toMillis());
    }

    default void showError(final Throwable ex) {
        showError(ex.getMessage());
    }

    default void showNotification(final String message, final int duration) {
        final Notification n = getNotificationSpan(message);
        n.setDuration(duration);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        n.setPosition(Notification.Position.BOTTOM_END);
        n.open();
    }

    default void showNotification(final String message) {
        showNotification(message, Duration.ofSeconds(3));
    }

    default void showNotification(final String message, final Duration duration) {
        showNotification(message, (int) duration.toMillis());
    }

    default void showErrorLog(final Throwable ex) {
        showError(ex);
        Logger.getLogger(getClass().getName()).log(Logger.Level.ERROR, ex.getMessage(), ex);
    }

    default void showWarning(final String warning) {
        final Notification n = getNotificationSpan(warning);
        n.setDuration(1500);
        n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        n.setPosition(Notification.Position.BOTTOM_END);
        n.open();
    }

}
