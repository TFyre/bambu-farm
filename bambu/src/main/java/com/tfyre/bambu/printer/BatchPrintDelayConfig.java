package com.tfyre.bambu.printer;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/**
 * Configuration for batch print job delay settings
 */
@ConfigMapping(prefix = "bambu.batch-print")
public interface BatchPrintDelayConfig {
    
    /**
     * Default delay between sending jobs to different printer groups in batch mode
     * Format: Duration string (e.g., "30s", "1m", "90s")
     * Default: 30 seconds
     */
    @WithDefault("30s")
    Duration jobDelay();
    
    /**
     * Whether the delay feature is enabled
     * Default: true
     */
    @WithDefault("true")
    boolean enableDelay();
    
    /**
     * Number of printers that can start simultaneously in each batch
     * For example, if set to 3 with a 30s delay:
     * - T+0:00 → Printers 1, 2, 3 start
     * - T+0:30 → Printers 4, 5, 6 start
     * - T+1:00 → Printers 7, 8, 9 start
     * 
     * Default: 1 (one printer at a time)
     */
    @WithDefault("1")
    int simultaneousPrinters();
}
