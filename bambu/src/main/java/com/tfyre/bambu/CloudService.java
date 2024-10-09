package com.tfyre.bambu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Singleton
public class CloudService {

    @Inject
    BambuConfig config;
    @Inject
    Logger log;

    @RestClient
    CloudApi api;

    public Optional<Data> getLoginData() {
        if (!config.cloud().enabled()) {
            return Optional.empty();
        }

        if (config.cloud().token().isPresent()) {
            log.info("token login");
            return config.cloud().token()
                    .flatMap(token -> toUserName(token)
                            .map(username -> new Data(username, token))
                    );
        }

        if (config.cloud().login().isEmpty()) {
            log.error("bambu.cloud.(login|token) should be configured, cannot enable cloud mode");
            return Optional.empty();
        }
        final BambuConfig.CloudLogin login = config.cloud().login().orElseThrow();

        final RestResponse<String> response = api
                .login(new CloudApi.Login(login.username(), login.password()))
                .await().indefinitely();

        if (response.getStatus() != 200) {
            log.errorf("Invalid response status[%d], cannot enable cloud mode", response.getStatus());
            return Optional.empty();
        }

        final String token = Optional.ofNullable(response.getCookies())
                .map(map -> map.get("token"))
                .map(cookie -> cookie.getValue())
                .orElse("");

        if (token.isEmpty()) {
            log.errorf("Could not find 'token' in cookie, cannot enable cloud mode");
            return Optional.empty();
        }

        return toUserName(token)
                .map(username -> new Data(username, token));
    }

    Optional<String> toUserName(final String token) {
        log.info("mapping username");
        final JwtConsumer consumer = new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setSkipAllDefaultValidators()
                .build();
        final JwtContext context;
        try {
            context = consumer.process(token);
        } catch (InvalidJwtException ex) {
            log.errorf(ex, "Could not parse token: %s", ex.getMessage());
            return Optional.empty();
        }
        return Optional.ofNullable(context.getJwtClaims().getClaimValueAsString("username"));

    }

    public record Data(String username, String password) {

    }

}
