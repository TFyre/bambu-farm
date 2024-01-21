package com.tfyre.bambu.printer;

import com.tfyre.bambu.model.BambuMessage;
import com.vaadin.flow.server.StreamResource;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface BambuPrinter {

    String getName();

    int getPrintError();

    int getTotalLayerNum();

    String getPrintType();

    Optional<Message> getStatus();

    Optional<Message> getFullStatus();

    Optional<Thumbnail> getThumbnail();

    Collection<Message> getLastMessages();

    void commandFullStatus(final boolean force);

    void commandClearPrinterError();

    void commandLight(BambuConst.LightMode lightMode);

    void commandControl(BambuConst.CommandControl control);

    void commandSpeed(BambuConst.Speed speed);

    void commandPrintGCodeLine(final String data);

    void commandPrintGCodeFile(final String filename);

    void commandPrintProjectFile(final String filename, final boolean useAms, final boolean timelapse, final boolean bedLevelling);

    record Message(OffsetDateTime lastUpdated, BambuMessage message, String raw) {

    }

    record Thumbnail(OffsetDateTime lastUpdated, StreamResource thumbnail) {

    }
}
