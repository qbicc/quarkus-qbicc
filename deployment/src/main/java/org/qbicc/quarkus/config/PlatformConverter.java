package org.qbicc.quarkus.config;

import java.io.Serial;

import org.eclipse.microprofile.config.spi.Converter;
import org.qbicc.machine.arch.Platform;

/**
 *
 */
public final class PlatformConverter implements Converter<Platform> {
    @Serial
    private static final long serialVersionUID = - 8265676124076529385L;

    public PlatformConverter() {
    }

    public Platform convert(final String s) throws IllegalArgumentException, NullPointerException {
        return Platform.parse(s);
    }
}
