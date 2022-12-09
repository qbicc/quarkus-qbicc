package org.qbicc.quarkus.spi;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The result of a qbicc build.
 */
public final class QbiccResultBuildItem extends SimpleBuildItem {
    private final Path outputPath;
    private final String version;

    public QbiccResultBuildItem(final Path outputPath, final String version) {
        this.outputPath = outputPath;
        this.version = version;
    }

    public Path getOutputPath() {
        return outputPath;
    }

    public String getVersion() {
        return version;
    }
}
