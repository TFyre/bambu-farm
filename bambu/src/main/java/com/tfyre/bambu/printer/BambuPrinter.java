package com.tfyre.bambu.printer;

import com.tfyre.bambu.model.BambuMessage;
import com.tfyre.bambu.printer.BambuConst.PrinterModel;
import com.vaadin.flow.server.StreamResource;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface BambuPrinter {

    String getName();

    PrinterModel getModel();

    int getPrintError();

    int getTotalLayerNum();

    String getPrintType();

    Optional<Message> getStatus();

    Optional<Message> getFullStatus();

    Optional<String> getIFrame();

    Optional<Thumbnail> getThumbnail();

    Collection<Message> getLastMessages();

    void commandFullStatus(final boolean force);

    void commandClearPrinterError();

    void commandLight(BambuConst.LightMode lightMode);

    void commandControl(BambuConst.CommandControl control);

    void commandSpeed(BambuConst.Speed speed);

    void commandPrintGCodeLine(final String lines);

    void commandPrintGCodeLine(final List<String> lines);

    void commandPrintGCodeFile(final String filename);

    void commandPrintProjectFile(final String filename, final int plateId, final boolean useAms, final boolean timelapse, final boolean bedLevelling);

    void commandFilamentLoad(final int amsTrayId);

    void commandFilamentUnload();

    void commandFilamentSetting(final int amsId, final int trayId, final Filament filament, final String value, final int minTemp, final int maxTemp);

    record Message(OffsetDateTime lastUpdated, BambuMessage message, String raw) {

    }

    record Thumbnail(OffsetDateTime lastUpdated, StreamResource thumbnail) {

    }
}
