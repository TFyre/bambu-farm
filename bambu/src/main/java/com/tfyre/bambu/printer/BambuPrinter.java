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

    BambuConst.GCodeState getGCodeState();

    Optional<Message> getStatus();

    Optional<Message> getFullStatus();

    Optional<String> getIFrame();

    Optional<Thumbnail> getThumbnail();

    Collection<Message> getLastMessages();

    boolean isBlocked();

    void setBlocked(final boolean blocked);

    void commandFullStatus(final boolean force);

    void commandDone();

    void commandClearPrinterError();

    void commandLight(BambuConst.LightMode lightMode);

    void commandControl(BambuConst.CommandControl control);

    void commandSpeed(BambuConst.Speed speed);

    void commandPrintGCodeLine(final String lines);

    void commandPrintGCodeLine(final List<String> lines);

    void commandPrintGCodeFile(final String filename);

    void commandPrintProjectFile(final CommandPPF command);

    void commandFilamentLoad(final int amsTrayId);

    void commandFilamentUnload();

    void commandFilamentSetting(final int amsId, final int trayId, final Filament filament, final String color, final int minTemp, final int maxTemp);

    void commandSystemReboot();

    record Message(OffsetDateTime lastUpdated, BambuMessage message, String raw) {

    }

    record Thumbnail(OffsetDateTime lastUpdated, StreamResource thumbnail) {

    }

    record CommandPPF(
            String filename,
            int plateId,
            boolean useAms,
            boolean timelapse,
            boolean bedLevelling,
            boolean flowCalibration,
            boolean vibrationCalibration,
            List<Integer> amsMapping) {

    }
}
