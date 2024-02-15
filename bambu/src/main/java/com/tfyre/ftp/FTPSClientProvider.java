package com.tfyre.ftp;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.ssl.NoopTrustSocketFactory;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@ApplicationScoped
public class FTPSClientProvider {

    private static final String SETTING = "jdk.tls.useExtendedMasterSecret";

    @Inject
    BambuConfig config;
    @Inject
    NoopTrustSocketFactory noopTrustSocketFactory;
    @Inject
    Logger log;

    private SSLContext sslContext;

    @PostConstruct
    public void postConstruct() throws KeyManagementException, NoSuchAlgorithmException, NoSuchProviderException {
        if (!config.useBouncyCastle()) {
            log.info("BouncyCastle disabled");
            return;
        }
        log.infof("BouncyCastle enabled: %s=[%s]", SETTING, System.getProperty(SETTING));

        Security.addProvider(new BouncyCastleJsseProvider());
        sslContext = SSLContext.getInstance("TLSv1.2", "BCJSSE");
        sslContext.init(null, new TrustManager[]{noopTrustSocketFactory.createNoopTrustManager()}, new SecureRandom()); // 1
    }

    @Produces
    @Dependent
    public BambuFtp provide() {
        if (!config.useBouncyCastle()) {
            return new BambuFtp();
        }
        return new BambuFtp(sslContext);
    }

}
