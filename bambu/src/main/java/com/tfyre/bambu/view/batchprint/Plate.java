package com.tfyre.bambu.view.batchprint;

import java.time.Duration;
import java.util.List;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public record Plate(String name, int plateId, Duration prediction, double weight, List<PlateFilament> filaments) {

}
