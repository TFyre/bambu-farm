package com.tfyre.bambu.printer;

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

    private final String description;

    private FilamentType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

}
