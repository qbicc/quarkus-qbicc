package org.qbicc.quarkus.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import org.qbicc.machine.arch.Platform;

/**
 * The configuration of the extension.
 */
@ConfigMapping
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface QbiccConfiguration {
    /**
     * The configuration for the LLVM backend.
     *
     * @return the configuration for the LLVM backend
     */
    LlvmConfiguration llvmConfiguration();

    /**
     * The platform to compile for. If not given, the host platform will be used.
     *
     * @return the platform to compile for
     */
    Optional<@WithConverter(PlatformConverter.class) Platform> platform();

    /**
     * The class library version to use. If not given, the default for the qbicc version will be used.
     *
     * @return the class library version
     */
    Optional<String> classLibVersion();

    /**
     * Specifies whether the executable should be position-independent.
     *
     * @return {@code true} for a position-independent executable, {@code false} otherwise
     */
    Optional<Boolean> pie();

    /**
     * Specifies whether intermediate assembly-language files should be emitted.
     *
     * @return {@code true} to emit intermediate assembly-language files, {@code false} otherwise
     */
    @WithDefault("false")
    boolean emitAsm();

    /**
     * Specify the GC to use.
     *
     * @return the GC to use
     */
    @WithDefault("none")
    Gc gc();
}
