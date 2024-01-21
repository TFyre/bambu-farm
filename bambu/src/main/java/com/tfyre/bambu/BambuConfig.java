package com.tfyre.bambu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@ConfigMapping(prefix = "bambu")
public interface BambuConfig {

    Dashboard dashboard();

    Map<String, Printer> printers();

    Map<String, User> users();

    public interface Printer {

        @WithDefault("true")
        boolean enabled();

        Optional<String> name();

        String deviceId();

        @WithDefault("bblp")
        String username();

        String accessCode();

        String ip();

        @WithDefault("true")
        boolean useAms();

        @WithDefault("true")
        boolean timelapse();

        @WithDefault("true")
        boolean bedLevelling();

        Mqtt mqtt();

        Ftp ftp();

        Stream stream();

        public interface Mqtt {

            @WithDefault("8883")
            int port();

            Optional<String> url();

            Optional<String> reportTopic();

            Optional<String> requestTopic();

            @WithDefault("10m")
            Duration fullStatus();

        }

        public interface Ftp {

            @WithDefault("990")
            int port();

            Optional<String> url();

            @WithDefault("false")
            boolean logCommands();

        }

        public interface Stream {

            @WithDefault("6000")
            int port();

            Optional<String> url();

            @WithDefault("5m")
            Duration watchDog();
        }
    }

    public interface Dashboard {

        @WithDefault("346")
        int thumbnailMaxHeight();

        @WithDefault("615")
        int thumbnailMaxWidth();

    }

    public interface User {

        String password();

        String role();

    }
}
