package com.tfyre.bambu.printer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public enum FilamentType {
    UNKNOWN("Unknown"),
    ABS("ABS"),
    ASA("ASA"),
    HIPS("HIPS"),
    PA("PA"),
    PVA("PVA"),
    PACF("PA-CF"),
    PA6CF("PA6-CF"),
    PC("PC"),
    PETCF("PET-CF"),
    PETG("PETG"),
    PETGCF("PETG-CF"),
    PLA("PLA"),
    PLA_AERO("PLA-AERO"),
    PLA_CF("PLA-CF"),
    TPU("TPU");

    private static final Map<String, FilamentType> MAP = EnumSet.allOf(FilamentType.class).stream().collect(Collectors.toMap(FilamentType::getDescription, Function.identity()));

    private final String description;

    private FilamentType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static Optional<FilamentType> getFilamentType(final String code) {
        return Optional.ofNullable(MAP.get(code));
    }

}
