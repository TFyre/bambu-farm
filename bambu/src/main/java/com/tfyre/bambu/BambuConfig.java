package com.tfyre.bambu;

import com.tfyre.bambu.printer.BambuConst.PrinterModel;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@ConfigMapping(prefix = "bambu")
public interface BambuConfig {

    @WithDefault("false")
    boolean useBouncyCastle();

    @WithDefault("true")
    boolean menuLeftClick();

    @WithDefault("false")
    boolean darkMode();

    @WithDefault("5000")
    int moveXy();

    @WithDefault("3000")
    int moveZ();

    @WithDefault("1s")
    Duration refreshInterval();

    @WithDefault("true")
    boolean remoteView();

    Optional<String> liveViewUrl();

    Dashboard dashboard();

    BatchPrint batchPrint();

    Map<String, Printer> printers();

    @WithDefault("false")
    boolean autoLogin();

    Map<String, User> users();

    Optional<List<Temperature>> preheat();

    Cloud cloud();

    public interface BatchPrint {

        @WithDefault("true")
        boolean skipSameSize();

        @WithDefault("true")
        boolean timelapse();

        @WithDefault("true")
        boolean bedLevelling();

        @WithDefault("true")
        boolean flowCalibration();

        @WithDefault("true")
        boolean vibrationCalibration();

        @WithDefault("true")
        boolean enforceFilamentMapping();
        
    }

    public interface Cloud {

        @WithDefault("false")
        boolean enabled();

        @WithDefault("ssl://us.mqtt.bambulab.com:8883")
        String url();

        Optional<String> username();

        Optional<String> token();

    }

    public interface Dashboard {

        @WithDefault("true")
        boolean remoteView();

        @WithDefault("true")
        boolean filamentFullName();

    }

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

        @WithDefault("true")
        boolean flowCalibration();

        @WithDefault("true")
        boolean vibrationCalibration();

        Mqtt mqtt();

        Ftp ftp();

        Stream stream();

        @WithDefault("unknown")
        @WithConverter(PrinterModelConverter.class)
        PrinterModel model();

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

            @WithDefault("true")
            boolean enabled();

            @WithDefault("6000")
            int port();

            @WithDefault("false")
            boolean liveView();

            Optional<String> url();

            @WithDefault("5m")
            Duration watchDog();
        }
    }

    public interface User {

        String password();

        String role();

        Optional<Boolean> darkMode();

    }

    public interface Temperature {

        String name();

        int bed();

        int nozzle();
    }
}
