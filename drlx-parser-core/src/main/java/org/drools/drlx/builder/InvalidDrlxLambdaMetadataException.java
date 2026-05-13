package org.drools.drlx.builder;

import java.io.IOException;

/**
 * Thrown when the DRLX lambda metadata file is malformed, contains an unsupported
 * {@code format.version}, or has missing required keys for an entry. Routed
 * through {@link DrlxMetadataMismatchMode} by callers.
 */
public class InvalidDrlxLambdaMetadataException extends IOException {
    public InvalidDrlxLambdaMetadataException(String message) { super(message); }
    public InvalidDrlxLambdaMetadataException(String message, Throwable cause) { super(message, cause); }
}
