package com.tfyre.bambu;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class YesNoCancelDialog {

    private static final String HEADER = "Confirmation";
    private static final String TEXT_CONFIRM = "Yes";
    private static final String TEXT_REJECT = "No";
    private static final String TEXT_CANCEL = "Cancel";

    private boolean confirmed;
    private boolean rejected;
    private boolean canceled;

    private final Consumer<YesNoCancelDialog> consumer;
    private final Dialog dialog = new Dialog();

    private YesNoCancelDialog(final String message, final Consumer<YesNoCancelDialog> consumer) {
        this.consumer = consumer;
        dialog.setHeaderTitle(HEADER);
        dialog.add(new VerticalLayout(
                Arrays.asList(message.split("\n"))
                        .stream()
                        .map(Span::new)
                        .toArray(Span[]::new)));

        dialog.getFooter().add(getCancel(), getNo(), getYes());
    }

    public static void show(final String message, final Consumer<YesNoCancelDialog> consumer) {
        new YesNoCancelDialog(message, consumer).open();
    }

    private Button getCancel() {
        final Button result = new Button(TEXT_CANCEL, e -> onCancel());
        result.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        result.getStyle().set("margin-right", "auto");
        return result;

    }

    private Button getNo() {
        final Button result = new Button(TEXT_REJECT, e -> onReject());
        result.addThemeVariants(ButtonVariant.LUMO_ERROR);
        return result;
    }

    private Button getYes() {
        final Button result = new Button(TEXT_CONFIRM, e -> onOK());
        result.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return result;
    }

    private void open() {
        dialog.open();
    }

    private void onOK() {
        dialog.close();
        confirmed = true;
        consumer.accept(this);
    }

    private void onReject() {
        dialog.close();
        rejected = true;
        consumer.accept(this);
    }

    private void onCancel() {
        dialog.close();
        canceled = true;
        consumer.accept(this);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isRejected() {
        return rejected;
    }

    public boolean isCanceled() {
        return canceled;
    }

}
