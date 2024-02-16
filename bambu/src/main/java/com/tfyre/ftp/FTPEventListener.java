package com.tfyre.ftp;

import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public interface FTPEventListener extends CopyStreamListener {

    @Override
    default void bytesTransferred(final CopyStreamEvent event) {
        bytesTransferred(event.getTotalBytesTransferred(), event.getBytesTransferred(), event.getStreamSize());
    }
}
