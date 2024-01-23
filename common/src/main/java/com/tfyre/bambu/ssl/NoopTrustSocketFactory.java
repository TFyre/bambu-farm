package com.tfyre.bambu.ssl;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@ApplicationScoped
public class NoopTrustSocketFactory {

    public static final String FACTORY = "NoopTrustSocketFactory";

    @Inject
    Logger log;

    public X509ExtendedTrustManager createNoopTrustManager() {
        return new X509ExtendedTrustManager() {

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                log.debug("INSECURE: getAcceptedIssuers");
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                log.debugf("INSECURE: checkClientTrusted %s", authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                log.debugf("INSECURE: checkServerTrusted %s", authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                log.debugf("INSECURE: checkClientTrusted %s", authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                log.debugf("INSECURE: checkServerTrusted %s", authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                log.debugf("INSECURE: checkClientTrusted %s", authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                log.debugf("INSECURE: checkServerTrusted %s", authType);
            }
        };
    }

    @Produces
    @Named(FACTORY)
    public SocketFactory createSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        final SSLContext sc = SSLContext.getInstance("ssl");
        sc.init(null, new TrustManager[]{ createNoopTrustManager() }, null);
        return sc.getSocketFactory();
    }

    @PostConstruct
    public void postConstruct() {
        log.errorf("Using INSECURE %s", getClass().getName());
    }

}
