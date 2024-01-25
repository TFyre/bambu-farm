package com.tfyre.bambu.printer;

import java.util.EnumSet;
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
    public static final Set<String> EXT = Set.of(/*FIXME not working FILE_GCODE,*/FILE_3MF);
    public static final String PATHSEP = "/";
    public static final String PRINT_TYPE_IDLE = "idle";
    public static final int TEMPERATURE_MAX_BED = 100;
    public static final int TEMPERATURE_MAX_NOZZLE = 300;

    public static final Map<String, String> FILAMENTS = Map.ofEntries(
            Map.entry("default", "Unknown"),
            Map.entry("GFB00", "Bambu ABS"),
            Map.entry("GFB01", "Bambu ASA"),
            Map.entry("GFN03", "Bambu PA-CF"),
            Map.entry("GFN05", "Bambu PA6-CF"),
            Map.entry("GFN04", "Bambu PAHT-CF"),
            Map.entry("GFC00", "Bambu PC"),
            Map.entry("GFT01", "Bambu PET-CF"),
            Map.entry("GFG00", "Bambu PETG Basic"),
            Map.entry("GFG50", "Bambu PETG-CF"),
            Map.entry("GFA11", "Bambu PLA Aero"),
            Map.entry("GFA00", "Bambu PLA Basic"),
            Map.entry("GFA03", "Bambu PLA Impact"),
            Map.entry("GFA07", "Bambu PLA Marble"),
            Map.entry("GFA01", "Bambu PLA Matte"),
            Map.entry("GFA02", "Bambu PLA Metal"),
            Map.entry("GFA05", "Bambu PLA Silk"),
            Map.entry("GFA08", "Bambu PLA Sparkle"),
            Map.entry("GFA09", "Bambu PLA Tough"),
            Map.entry("GFA50", "Bambu PLA-CF"),
            Map.entry("GFS03", "Bambu Support For PA/PET"),
            Map.entry("GFS02", "Bambu Support For PLA"),
            Map.entry("GFS01", "Bambu Support G"),
            Map.entry("GFS00", "Bambu Support W"),
            Map.entry("GFU01", "Bambu TPU 95A"),
            Map.entry("GFB99", "Generic ABS"),
            Map.entry("GFB98", "Generic ASA"),
            Map.entry("GFS98", "Generic HIPS"),
            Map.entry("GFN98", "Generic PA-CF"),
            Map.entry("GFN99", "Generic PA"),
            Map.entry("GFC99", "Generic PC"),
            Map.entry("GFG99", "Generic PETG"),
            Map.entry("GFG98", "Generic PETG-CF"),
            Map.entry("GFL99", "Generic PLA"),
            Map.entry("GFL95", "Generic PLA-High Speed"),
            Map.entry("GFL96", "Generic PLA Silk"),
            Map.entry("GFL98", "Generic PLA-CF"),
            Map.entry("GFS99", "Generic PVA"),
            Map.entry("GFU99", "Generic TPU"),
            Map.entry("GFL05", "Overture Matte PLA"),
            Map.entry("GFL04", "Overture PLA"),
            Map.entry("GFB60", "PolyLite ABS"),
            Map.entry("GFB61", "PolyLite ASA"),
            Map.entry("GFG60", "PolyLite PETG"),
            Map.entry("GFL00", "PolyLite PLA"),
            Map.entry("GFL01", "PolyTerra PLA"),
            Map.entry("GFL03", "eSUN PLA+"),
            Map.entry("GFSL99_01", "Generic PLA Silk"),
            Map.entry("GFSL99_12", "Generic PLA Silk")
    );

    public static Optional<String> getFilament(final String filament) {
        return Optional.ofNullable(FILAMENTS.get(filament));
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

    private BambuConst() {
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
