package com.tfyre.bambu.printer;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    public static final String PRINT_TYPE_IDLE = "idle";
    public static final int TEMPERATURE_MAX_BED = 100;
    public static final int TEMPERATURE_MAX_NOZZLE = 300;
    public static final int AMS_TRAY_UNLOAD = 255;
    public static final int AMS_TRAY_TEMP = 210;

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

}
