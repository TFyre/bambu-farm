package com.tfyre.bambu.view.batchprint;

import com.tfyre.bambu.printer.FilamentType;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public record PlateFilament(int filamentId, FilamentType type, double weight, long color) {

}
