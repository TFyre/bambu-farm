package com.tfyre.bambu.view;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public abstract class PushDiv extends Div implements FlexComponent {

    @Inject
    Logger log;
    @Inject
    ScheduledExecutorService ses;

    private Optional<ScheduledFuture<?>> future = Optional.empty();

    private void cancelFuture() {
        future.ifPresent(f -> f.cancel(true));
        future = Optional.empty();
    }

    @Override
    protected void onDetach(final DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        cancelFuture();
    }

    public ScheduledFuture<?> createFuture(final Runnable runnable, final Duration interval) {
        cancelFuture();
        future = Optional.of(ses.scheduleAtFixedRate(runnable, 0, interval.getSeconds(), TimeUnit.SECONDS));
        return future.get();
    }

}
