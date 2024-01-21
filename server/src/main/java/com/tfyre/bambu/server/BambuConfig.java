package com.tfyre.bambu.server;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@ConfigMapping(prefix = "bambu")
public interface BambuConfig {

    Map<String, Printer> printers();

    public interface Printer {

        @WithDefault("true")
        boolean enabled();

        String deviceId();

        String url();

        @WithDefault("bblp")
        String username();

        String accessCode();

        Optional<String> reportTopic();

        Optional<String> requestTopic();

    }
}
