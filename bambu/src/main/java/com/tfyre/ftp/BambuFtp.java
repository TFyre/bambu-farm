package com.tfyre.ftp;

import com.tfyre.bambu.BambuConfig;
import com.tfyre.bambu.printer.BambuPrinters;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import javax.net.ssl.SSLContext;
import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.bouncycastle.jsse.BCExtendedSSLSession;
import org.bouncycastle.jsse.BCSSLSocket;
import org.jboss.logging.Logger;

/**
 * FTPS Client with SSL Session Reuse.
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class BambuFtp extends org.apache.commons.net.ftp.FTPSClient {

    private static final Logger log = Logger.getLogger(BambuFtp.class.getName());

    private final boolean useBC;
    private BambuConfig.Printer config;
    private URI uri;

    private BambuFtp(final boolean useBC, final SSLContext context) {
        super(true, context);
        this.useBC = useBC;
    }

    public BambuFtp() {
        this(false, null);
    }

    public BambuFtp(final SSLContext context) {
        this(true, context);
    }

    @Override
    protected void _prepareDataSocket_(Socket dataSocket) {
        if (!useBC) {
            return;
        }
        if (_socket_ instanceof BCSSLSocket sslSocket) {
            final BCExtendedSSLSession bcSession = sslSocket.getBCSession();
            if (bcSession != null && bcSession.isValid() && dataSocket instanceof BCSSLSocket dataSslSocket) {
                dataSslSocket.setBCSessionToResume(bcSession);
                dataSslSocket.setHost(bcSession.getPeerHost());
            }
        }
    }

    private ProtocolCommandListener getListener(final String name) {
        return new ProtocolCommandListener() {

            private void log(ProtocolCommandEvent event) {
                log.infof("%s: command[%s] message[%s]", name, event.getCommand(), event.getMessage().trim());
            }

            @Override
            public void protocolCommandSent(ProtocolCommandEvent event) {
                log(event);
            }

            @Override
            public void protocolReplyReceived(ProtocolCommandEvent event) {
                log(event);
            }
        };
    }

    private URI getURI() {
        return URI.create(config.ftp().url().orElseGet(() -> "ftps://%s:%d".formatted(config.ip(), config.ftp().port())));
    }

    public BambuFtp setup(final BambuPrinters.PrinterDetail printer, final FTPEventListener listener) {
        setCopyStreamListener(listener);
        config = printer.config();
        if (config.ftp().logCommands()) {
            addProtocolCommandListener(getListener(printer.name()));
        }
        setStrictReplyParsing(false);
        uri = getURI();
        return this;
    }

    public void doConnect() throws IOException {
        if (!isConnected()) {
            connect(uri.getHost(), uri.getPort());
        }
    }

    public boolean doLogin() throws IOException {
        if (!login(config.username(), config.accessCode())) {
            return false;
        }

        execPROT("P");
        enterLocalPassiveMode();
        return true;
    }

    public boolean doUpload(final String fileName, final InputStream inputStream) throws IOException {
        deleteFile(fileName);
        setFileType(FTP.BINARY_FILE_TYPE);
        return storeFile(fileName, inputStream);
    }

    public void doClose() throws IOException {
        quit();
        disconnect();
    }
}
