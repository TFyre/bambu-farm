package com.tfyre.bambu.ssl;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
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

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@ApplicationScoped
public class NoopTrustSocketFactory {

    public static final String FACTORY = "NoopTrustSocketFactory";


    public X509ExtendedTrustManager createNoopTrustManager() {
        return new X509ExtendedTrustManager() {

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                Log.debug("INSECURE: getAcceptedIssuers");
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                Log.debugf("INSECURE: checkClientTrusted %s", authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                Log.debugf("INSECURE: checkServerTrusted %s", authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                Log.debugf("INSECURE: checkClientTrusted %s", authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                Log.debugf("INSECURE: checkServerTrusted %s", authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                Log.debugf("INSECURE: checkClientTrusted %s", authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                Log.debugf("INSECURE: checkServerTrusted %s", authType);
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
        Log.errorf("Using INSECURE %s", getClass().getName());
    }

}
