package com.tfyre.bambu.mqtt;

import com.tfyre.bambu.ssl.NoopTrustSocketFactory;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public abstract class AbstractMqttController extends RouteBuilder {

    private final Random rnd = new SecureRandom();

    private String newClientId() {
        return "camel-paho-%s".formatted(Long.toHexString(Math.abs(rnd.nextLong())));
    }

    protected Endpoint getPrinterEndpoint(final String name) {
        return endpoint("direct:bambu-%s".formatted(name));
    }

    protected Endpoint getMqttEndpoint(final String topic, final String url, final String username, final String password) {
        return endpoint("paho:%s?brokerUrl=%s&userName=%s&password=%s&qos=0&lazyStartProducer=true&socketFactory=#%s&clientId=%s"
                .formatted(topic, url, username, password, NoopTrustSocketFactory.FACTORY, newClientId()));
    }

    protected String getTopic(final Optional<String> topic, final String deviceId, final String type) {
        return topic.orElseGet(() -> "device/%s/%s".formatted(deviceId, type));
    }

}
