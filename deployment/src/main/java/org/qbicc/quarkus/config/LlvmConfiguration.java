package org.qbicc.quarkus.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

/**
 * The configuration of the LLVM backend.
 */
@ConfigGroup
public interface LlvmConfiguration {
    /**
     * Set to emit intermediate LLVM IR files.
     *
     * @return {@code true} to emit intermediate LLVM IR files
     */
    @WithDefault("false")
    boolean emitIr();

    /**
     * Set to enable LLVM opaque pointers (the default in LLVM 16 and later).
     *
     * @return {@code true} if opaque pointers are enabled
     */
    @WithDefault("false")
    boolean opaquePointers();

    /**
     * Optional extra options for {@code llc}.
     *
     * @return the extra options
     */
    Optional<List<String>> llcOptions();
}
