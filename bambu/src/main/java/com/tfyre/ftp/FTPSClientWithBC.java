package com.tfyre.ftp;

import java.io.IOException;
import java.net.Socket;
import javax.net.ssl.SSLContext;
import org.bouncycastle.jsse.BCExtendedSSLSession;
import org.bouncycastle.jsse.BCSSLSocket;

/**
 * FTPS Client with SSL Session Reuse.
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class FTPSClientWithBC extends org.apache.commons.net.ftp.FTPSClient {

    public FTPSClientWithBC(final SSLContext context) {
        super(true, context);
    }

    @Override
    protected void _prepareDataSocket_(Socket dataSocket) {
        if (_socket_ instanceof BCSSLSocket sslSocket) {
            final BCExtendedSSLSession bcSession = sslSocket.getBCSession();
            if (bcSession != null && bcSession.isValid() && dataSocket instanceof BCSSLSocket dataSslSocket) {
                dataSslSocket.setBCSessionToResume(bcSession);
                dataSslSocket.setHost(bcSession.getPeerHost());
            }
        }
    }
}
