package com.tfyre.bambu.view;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.tfyre.bambu.MainLayout;
import com.tfyre.bambu.SystemRoles;
import com.tfyre.bambu.model.BambuMessage;
import com.tfyre.bambu.printer.BambuPrinter;
import com.tfyre.bambu.printer.BambuPrinters;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(value = "logs", layout = MainLayout.class)
@PageTitle("Logs")
@RolesAllowed({ SystemRoles.ROLE_ADMIN })
public class LogsView extends VerticalLayout implements HasUrlParameter<String>, ShowInterface {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().preservingProtoFieldNames();
    //DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter DTF = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(".")
            .appendValue(ChronoField.MILLI_OF_SECOND, 3)
            .toFormatter();

    private final ComboBox<BambuPrinter> comboBox = new ComboBox<>();

    @Inject
    Logger log;
    @Inject
    BambuPrinters printers;

    private Optional<BambuPrinter> _printer = Optional.empty();
    private final ListBox<BambuPrinter.Message> listBox = new ListBox<>();
    private final TextField filter = new TextField();
    private final TextArea json = new TextArea("RAW");
    private final TextArea parsed = new TextArea("Parsed");
    private final List<BambuPrinter.Message> messages = new ArrayList<>();

    @Override
    public void setParameter(final BeforeEvent event, @OptionalParameter final String printerName) {
        _printer = printers.getPrinter(printerName);
    }

    private String parseJson(final String data) {
        try {
            return OM.writerWithDefaultPrettyPrinter().writeValueAsString(OM.readTree(data));
        } catch (JsonProcessingException ex) {
            showError(ex.getMessage());
            return data;
        }
    }

    private String parseMessage(final BambuMessage message) {
        try {
            return PRINTER.print(message);
        } catch (InvalidProtocolBufferException ex) {
            showError(ex.getMessage());
            return message.toString();
        }
    }

    private void buildFilter() {
        final String value = filter.getValue();
        if (value == null || value.isBlank()) {
            listBox.setItems(messages);
            return;
        }

        listBox.setItems(messages.stream().filter(m -> m.raw().contains(value)).toList());
    }

    private void buildList(final BambuPrinter printer) {
        messages.clear();
        messages.addAll(new ArrayList<>(printer.getLastMessages()).reversed());
        buildFilter();
    }

    private Component buildListBox() {
        listBox.setItemLabelGenerator(m -> "%s - %s".formatted(DTF.format(m.lastUpdated()), m.raw().length()));
        listBox.addValueChangeListener(l -> {
            if (l.getValue() == null) {
                return;
            }
            json.setValue(parseJson(l.getValue().raw()));
            parsed.setValue(parseMessage(l.getValue().message()));
        });
        listBox.setMinWidth(300, Unit.PIXELS);

        json.setReadOnly(true);
        parsed.setReadOnly(true);

        final FlexLayout flex = new FlexLayout(json, parsed);
        flex.setFlexGrow(50.0, json, parsed);
        final HorizontalLayout result = new HorizontalLayout();
        result.setSizeFull();
        result.add(listBox);
        result.addAndExpand(new Scroller(flex));
        result.setMinHeight("0");
        return result;
    }

    private Component buildToolbar() {
        comboBox.setItemLabelGenerator(BambuPrinter::getName);
        comboBox.setItems(printers.getPrinters().stream().sorted(Comparator.comparing(BambuPrinter::getName)).toList());
        comboBox.addValueChangeListener(l -> buildList(l.getValue()));
        filter.addValueChangeListener(l -> buildFilter());
        filter.setValueChangeMode(ValueChangeMode.TIMEOUT);
        final Button refresh = new Button("Refresh", new Icon(VaadinIcon.REFRESH), l -> Optional.ofNullable(comboBox.getValue()).ifPresent(this::buildList));
        final HorizontalLayout result = new HorizontalLayout(new Span("Printers"), comboBox, refresh, new Span("Filter"), filter);
        result.setWidthFull();
        result.setAlignItems(Alignment.CENTER);
        return result;
    }

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        setSizeFull();
        add(buildToolbar(), buildListBox());
        _printer.ifPresent(this::buildList);
    }

    //FIXME Implement Export
    private record Entry(String date, String raw, String parsed) {

    }

}
