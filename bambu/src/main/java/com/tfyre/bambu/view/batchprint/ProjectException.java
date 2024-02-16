package com.tfyre.bambu.view.batchprint;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class ProjectException extends Exception {

    public ProjectException(String message) {
        super(message);
    }

    public ProjectException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
