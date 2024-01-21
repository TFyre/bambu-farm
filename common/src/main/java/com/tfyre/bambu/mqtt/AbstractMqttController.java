package com.tfyre.bambu.mqtt;

import com.tfyre.bambu.ssl.NoopTrustSocketFactory;
import java.util.Optional;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public abstract class AbstractMqttController extends RouteBuilder {

    protected Endpoint getPrinterEndpoint(final String name) {
        return endpoint("direct:bambu-%s".formatted(name));
    }

    protected Endpoint getMqttEndpoint(final String topic, final String url, final String username, final String password) {
        return endpoint("paho:%s?brokerUrl=%s&userName=%s&password=%s&qos=0&lazyStartProducer=true&socketFactory=#%s"
                .formatted(topic, url, username, password, NoopTrustSocketFactory.FACTORY));
    }

    protected String getTopic(final Optional<String> topic, final String deviceId, final String type) {
        return topic.orElseGet(() -> "device/%s/%s".formatted(deviceId, type));
    }

}
