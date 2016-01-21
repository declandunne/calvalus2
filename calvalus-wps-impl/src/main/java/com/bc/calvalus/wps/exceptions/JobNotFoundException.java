package com.bc.calvalus.wps.exceptions;

/**
 * @author hans
 */
public class JobNotFoundException extends Exception {

    public JobNotFoundException(String message) {
        super(message);
    }

    public JobNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public JobNotFoundException(Throwable cause) {
        super(cause);
    }

}
