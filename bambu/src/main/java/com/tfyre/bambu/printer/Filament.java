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
public enum Filament {
    UNKNOWN("Unknown", "Unknown", FilamentType.UNKNOWN),
    BAMBU_ABS("GFB00", "Bambu ABS", FilamentType.ABS),
    BAMBU_ASA("GFB01", "Bambu ASA", FilamentType.ASA),
    BAMBU_PACF("GFN03", "Bambu PA-CF", FilamentType.PACF),
    BAMBU_PA6CF("GFN05", "Bambu PA6-CF", FilamentType.PA6CF),
    BAMBU_PAHTCF("GFN04", "Bambu PAHT-CF", FilamentType.PACF),
    BAMBU_PC("GFC00", "Bambu PC", FilamentType.PC),
    BAMBU_PET_CF("GFT01", "Bambu PET-CF", FilamentType.PETCF),
    BAMBU_PETG_BASIC("GFG00", "Bambu PETG Basic", FilamentType.PETG),
    BAMBU_PETG_TRANSLUCENT("GFG01", "Bambu PETG Translucent", FilamentType.PETG),
    BAMBU_PETG_CF("GFG50", "Bambu PETG-CF", FilamentType.PETGCF),
    BAMBU_PLA_AERO("GFA11", "Bambu PLA Aero", FilamentType.PLA_AERO),
    BAMBU_PLA_BASIC("GFA00", "Bambu PLA Basic", FilamentType.PLA),
    BAMBU_PLA_IMPACT("GFA03", "Bambu PLA Impact", FilamentType.PLA),
    BAMBU_PLA_MARBLE("GFA07", "Bambu PLA Marble", FilamentType.PLA),
    BAMBU_PLA_MATTE("GFA01", "Bambu PLA Matte", FilamentType.PLA),
    BAMBU_PLA_METAL("GFA02", "Bambu PLA Metal", FilamentType.PLA),
    BAMBU_PLA_SILK("GFA05", "Bambu PLA Silk", FilamentType.PLA),
    BAMBU_PLA_SPARKLE("GFA08", "Bambu PLA Sparkle", FilamentType.PLA),
    BAMBU_PLA_TOUGH("GFA09", "Bambu PLA Tough", FilamentType.PLA),
    BAMBU_PLA_CF("GFA50", "Bambu PLA-CF", FilamentType.PLA_CF),
    BAMBU_SUPPORT_PA("GFS03", "Bambu Support For PA/PET", FilamentType.PLA),
    BAMBU_SUPPORT_PLA("GFS02", "Bambu Support For PLA", FilamentType.PLA),
    BAMBU_SUPPORT_G("GFS01", "Bambu Support G", FilamentType.PLA),
    BAMBU_SUPPORT_W("GFS00", "Bambu Support W", FilamentType.PLA),
    BAMBU_TPU("GFU01", "Bambu TPU 95A", FilamentType.TPU),
    GENERIC_ABS("GFB99", "Generic ABS", FilamentType.ABS),
    GENERIC_ASA("GFB98", "Generic ASA", FilamentType.ASA),
    GENERIC_HIPS("GFS98", "Generic HIPS", FilamentType.HIPS),
    GENERIC_PACF("GFN98", "Generic PA-CF", FilamentType.PACF),
    GENERIC_PA("GFN99", "Generic PA", FilamentType.PA),
    GENERIC_PC("GFC99", "Generic PC", FilamentType.PC),
    GENERIC_PETG("GFG99", "Generic PETG", FilamentType.PETG),
    GENERIC_PETC_CF("GFG98", "Generic PETG-CF", FilamentType.PETGCF),
    GENERIC_PLA("GFL99", "Generic PLA", FilamentType.PLA),
    GENERIC_PLA_HS("GFL95", "Generic PLA-High Speed", FilamentType.PLA),
    GENERIC_PLA_SILK("GFL96", "Generic PLA Silk", FilamentType.PLA),
    GENERIC_PLA_CF("GFL98", "Generic PLA-CF", FilamentType.PLA_CF),
    GENERIC_PVA("GFS99", "Generic PVA", FilamentType.PVA),
    GENERIC_TPU("GFU99", "Generic TPU", FilamentType.TPU),
    OVERTURE_PLA("GFL05", "Overture Matte PLA", FilamentType.PLA),
    OVERTURE_ABS("GFL04", "Overture PLA", FilamentType.PLA),
    POLYLITE_ABS("GFB60", "PolyLite ABS", FilamentType.ABS),
    POLYLITE_ASA("GFB61", "PolyLite ASA", FilamentType.ASA),
    POLYLITE_PETG("GFG60", "PolyLite PETG", FilamentType.PETG),
    POLYLITE_PLA("GFL00", "PolyLite PLA", FilamentType.PLA),
    POLYTERRA_PLA("GFL01", "PolyTerra PLA", FilamentType.PLA),
    ESUN_PLA("GFL03", "eSUN PLA+", FilamentType.PLA),
    SUNLU_PLA("GFSNL03", "SUNLU PLA+", FilamentType.PLA),
    SUNLU_PLA2("GFSNL04", "SUNLU PLA+ 2.0", FilamentType.PLA),
    SUNLU_PLA_MARBLE("GFSNL06", "SUNLU PLA Marble", FilamentType.PLA),
    SUNLU_PLA_MATTE("GFSNL02", "SUNLU PLA Matte", FilamentType.PLA),
    SUNLU_PLA_SILK("GFSNL05", "SUNLU PLA Silk", FilamentType.PLA),
    SUNLU_PLA_WOOD("GFSNL07", "SUNLU PLA Wood", FilamentType.PLA),
    SUNLU_PETG("GFSNL08", "SUNLU PETG", FilamentType.PETG),
    

    GENERIC_PLA_SLIK_01("GFSL99_01", "Generic PLA Silk 01", FilamentType.PLA),
    GENERIC_PLA_SLIK_12("GFSL99_12", "Generic PLA Silk 12", FilamentType.PLA);

    private static final Map<String, Filament> MAP = EnumSet.allOf(Filament.class).stream().collect(Collectors.toMap(Filament::getCode, Function.identity()));
    private static final Function<Filament, String> MAPPER_DESCRIPTION = filament -> filament.getDescription();
    private static final Function<Filament, String> MAPPER_TYPE = filament -> filament.getType().getDescription();

    private final String code;
    private final String description;
    private final FilamentType type;

    private Filament(final String code, final String description, final FilamentType type) {
        this.code = code;
        this.description = description;
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public FilamentType getType() {
        return type;
    }

    public static Optional<Filament> getFilament(final String code) {
        return Optional.ofNullable(MAP.get(code));
    }

    public static String getFilamentDescription(final String code, final boolean fullName) {
        final Function<Filament, String> mapper = fullName ? MAPPER_DESCRIPTION : MAPPER_TYPE;
        return Optional.ofNullable(MAP.get(code))
                .map(mapper)
                .orElseGet(() -> mapper.apply(UNKNOWN));
    }

}
