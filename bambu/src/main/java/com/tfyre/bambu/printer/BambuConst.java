package com.tfyre.bambu.printer;

import com.tfyre.bambu.BambuConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class BambuConst {

    public static final String CHAMBER_LIGHT = "chamber_light";
    public static final String FILE_GCODE = ".gcode";
    public static final String FILE_3MF = ".3mf";
    //FIXME GCODE not printing public static final Set<String> EXT = Set.of(FILE_GCODE, FILE_3MF);
    public static final Set<String> EXT = Set.of(FILE_3MF);
    public static final String PATHSEP = "/";
    public static final int TEMPERATURE_MAX_BED = 100;
    public static final int TEMPERATURE_MAX_NOZZLE = 300;
    public static final int AMS_TRAY_VIRTUAL = 254;
    public static final int AMS_TRAY_UNLOAD = 255;
    public static final int AMS_TRAY_TEMP = 210;

    public static final List<BambuConfig.Temperature> PREHEAT = List.of(
            newTemperature("Off 0 / 0", 0, 0),
            newTemperature("PLA 55 / 220", 55, 220),
            newTemperature("ABS 90 / 270", 90, 270)
    );

    private static BambuConfig.Temperature newTemperature(final String name, final int bed, final int nozzle) {
        return new BambuConfig.Temperature() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int bed() {
                return bed;
            }

            @Override
            public int nozzle() {
                return nozzle;
            }
        };
    }

    public static String gcodeTargetTemperatureBed(final int temperature) {
        return "M140 S%d".formatted(Math.max(Math.min(temperature, TEMPERATURE_MAX_BED), 0));
    }

    public static String gcodeTargetTemperatureNozzle(final int temperature) {
        return "M104 S%d".formatted(Math.max(Math.min(temperature, TEMPERATURE_MAX_NOZZLE), 0));
    }

    public static String gcodeDisableSteppers() {
        return "M18";
    }

    public static String gcodeFanSpeed(final Fan fan, final FanSpeed speed) {
        return "M106 P%d S%d".formatted(fan.getValue(), speed.getValue());

    }

    public static List<String> gcodeMoveXYZ(final Move move, final int value, final int speed) {
        return List.of(
                "M211 S",
                "M211 X1 Y1 Z1",
                "M1002 push_ref_mode",
                "G91",
                "G1 %s%d F%d".formatted(move.getValue(), value, speed),
                "M1002 pop_ref_mode",
                "M211 R"
        );
    }

    public static List<String> gcodeMoveExtruder(final boolean up) {
        return List.of(
                "M83",
                "G0 %s%d F900".formatted(Move.E.getValue(), up ? -10 : 10)
        );
    }

    public static String gcodeHomeAll() {
        return "G28";
    }

    public static String gcodeHomeXY() {
        return "G28 X Y";
    }

    public static String gcodeHomeZ() {
        return "G28 Z";
    }

    private BambuConst() {
    }

    public enum Move {
        E("E"),
        X("X"),
        Y("Y"),
        Z("Z");

        private final String value;

        private Move(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }

    public enum Fan {
        PART("Part", 1),
        AUX("AUX", 2),
        CHAMBER("Chamber", 3);

        private final String name;
        private final int value;

        private Fan(final String name, final int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }

    }

    public enum FanSpeed {
        OFF("Off", 0),
        P25("25%", (int) (0.25 * 255)),
        P50("50%", (int) (0.50 * 255)),
        P75("75%", (int) (0.75 * 255)),
        FULL("Full", (int) (1.0 * 255));

        private final String name;
        private final int value;

        private FanSpeed(final String name, final int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }

    }

    public enum LightMode {
        ON("on"),
        OFF("off");
        //FIXME what does this do?
        //FLASHING("flashing");

        private final String value;

        private LightMode(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }

    public enum CommandControl {
        STOP("stop"),
        PAUSE("pause"),
        RESUME("resume");

        private final String value;

        private CommandControl(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum Speed {
        UNKNOWN(0, "Unknown"),
        SILENT(1, "Silent"),
        NORMAL(2, "Normal"),
        SPORT(3, "Sport"),
        LUDICROUS(4, "Ludicrous");

        private final int speed;
        private final String description;

        private static final Map<Integer, Speed> MAP = EnumSet.allOf(Speed.class).stream().collect(Collectors.toMap(Speed::getSpeed, Function.identity()));

        private Speed(final int speed, final String description) {
            this.speed = speed;
            this.description = description;
        }

        public int getSpeed() {
            return speed;
        }

        public String getDescription() {
            return description;
        }

        public static Speed fromSpeed(final int speed) {
            return MAP.getOrDefault(speed, UNKNOWN);
        }

    }

    public enum Color {
        COL00(0xffffff),
        COL01(0xfff144),
        COL02(0xdcf478),
        COL03(0x0acc38),
        COL04(0x057748),
        COL05(0x0d6284),
        COL06(0x0ee2a0),
        COL07(0x76d9f4),
        COL08(0x46a8f9),
        COL09(0x2850e0),
        COL10(0x443089),
        COL11(0xa03cf7),
        COL12(0xf330f9),
        COL13(0xd4b1dd),
        COL14(0xf95d73),
        COL15(0xf72323),
        COL16(0x7c4b00),
        COL17(0xf98c36),
        COL18(0xfcecd6),
        COL19(0xd3c5a3),
        COL20(0xaf7933),
        COL21(0x898989),
        COL22(0xbcbcbc),
        COL23(0x161616);

        private final long color;
        private final String htmlColor;

        private Color(final long color) {
            this.color = color;
            this.htmlColor = "#%06X".formatted(color);
        }

        public long getColor() {
            return color;
        }

        public String getHtmlColor() {
            return htmlColor;
        }

    }

    public enum PrinterModel {
        UNKNOWN("unknown"),
        A1("a1"),
        A1MINI("a1mini"),
        P1P("p1p"),
        P1S("p1s"),
        X1C("x1c");

        private static final Map<String, PrinterModel> MAP = EnumSet.allOf(PrinterModel.class).stream().collect(Collectors.toMap(PrinterModel::getModel, Function.identity()));

        private final String model;

        private PrinterModel(final String model) {
            this.model = model;
        }

        public String getModel() {
            return model;
        }

        public static Optional<PrinterModel> fromModel(final String model) {
            return Optional.ofNullable(MAP.get(model));
        }

    }

    public enum GCodeState {
        UNKNOWN("", "Unknown"),
        OFFLINE("OFFLINE", "Offline"),
        IDLE("IDLE", "Idle"),
        RUNNING("RUNNING", "Running"),
        PAUSE("PAUSE", "Pause"),
        PREPARE("PREPARE", "Prepare"),
        FINISH("FINISH", "Finish"),
        FAILED("FAILED", "Failed"),
        SLICING("SLICING", "Slicing");

        private static final Map<String, GCodeState> MAP = EnumSet.allOf(GCodeState.class).stream().collect(Collectors.toMap(GCodeState::getValue, Function.identity()));
        private static final Set<GCodeState> IS_IDLE = Set.of(IDLE, FINISH);
        private static final Set<GCodeState> IS_READY = Set.of(IDLE, FINISH, FAILED);
        private static final Set<GCodeState> IS_ERROR = Set.of(UNKNOWN, OFFLINE, FAILED);
        private static final Set<GCodeState> IS_PRINTING = Set.of(PREPARE, SLICING, RUNNING);
        private final String value;
        private final String description;

        private GCodeState(final String value, final String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public boolean isIdle() {
            return IS_IDLE.contains(this);
        }

        public boolean isReady() {
            return IS_READY.contains(this);
        }

        public boolean isError() {
            return IS_ERROR.contains(this);
        }

        public boolean isPrinting() {
            return IS_PRINTING.contains(this);
        }

        public static GCodeState fromValue(final String value) {
            return MAP.getOrDefault(value, UNKNOWN);
        }

    }

}
