package com.tfyre.bambu;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Singleton
public class CloudService {

    @Inject
    BambuConfig config;

    @RestClient
    CloudApi api;

    public Optional<Data> getLoginData() {
        if (!config.cloud().enabled()) {
            return Optional.empty();
        }
        if (config.cloud().username().isEmpty()) {
            Log.error("bambu.cloud.username should be configured, cannot enable cloud mode");
            return Optional.empty();
        }
        if (config.cloud().token().isEmpty()) {
            Log.error("bambu.cloud.token should be configured, cannot enable cloud mode");
            return Optional.empty();
        }
        Log.info("username & token login");
        return Optional.of(new Data(config.cloud().username().get(), config.cloud().token().get()));
    }

    public record Data(String username, String password) {

    }

}
