package org.qbicc.quarkus.deployment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.MainClassBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSecurityProviderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSystemPropertyBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageSourceJarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import org.apache.commons.lang3.SystemUtils;
import org.qbicc.context.Diagnostic;
import org.qbicc.context.DiagnosticContext;
import org.qbicc.machine.arch.Platform;
import org.qbicc.main.Backend;
import org.qbicc.main.ClassPathEntry;
import org.qbicc.main.Main;
import org.qbicc.plugin.llvm.LLVMConfiguration;
import org.qbicc.plugin.llvm.ReferenceStrategy;
import org.qbicc.quarkus.config.QbiccConfiguration;
import org.qbicc.quarkus.spi.QbiccResultBuildItem;

class QbiccProcessor {

    private static final String FEATURE = "qbicc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ArtifactResultBuildItem result(QbiccResultBuildItem item) {
        return new ArtifactResultBuildItem(
            item.getOutputPath(),
            PackageConfig.NATIVE,
            Map.of(
                "qbicc.version", item.getVersion()
            )
        );
    }

    @BuildStep
    QbiccResultBuildItem build(
        QbiccConfiguration configuration,
        MainClassBuildItem mainClassBuildItem,
        NativeImageSourceJarBuildItem nativeImageJar,
        List<NativeImageSystemPropertyBuildItem> nativeImageProperties,
        List<NativeImageSecurityProviderBuildItem> nativeImageSecurityProviders,
        List<NativeImageFeatureBuildItem> graalvmFeatures,
        OutputTargetBuildItem outputTargetBuildItem,
        PackageConfig packageConfig
    ) {
        final Path outputDirectory = outputTargetBuildItem.getOutputDirectory();
        final String nativeImageName = outputTargetBuildItem.getBaseName() + packageConfig.getRunnerSuffix();
        boolean isContainerBuild = false; // this is something we have with graalvm
        boolean defaultPie = ! SystemUtils.IS_OS_WINDOWS && ! isContainerBuild;
        final boolean pie = configuration.pie().orElse(Boolean.valueOf(defaultPie)).booleanValue();

        final Main.Builder mainBuilder = Main.builder();
        if (configuration.classLibVersion().isPresent()) {
            mainBuilder.setClassLibVersion(configuration.classLibVersion().get());
        }
        mainBuilder.setPlatform(configuration.platform().orElse(Platform.HOST_PLATFORM));
        mainBuilder.setIsPie(pie);
        mainBuilder.setLlvmConfigurationBuilder(LLVMConfiguration.builder()
            .setEmitIr(configuration.llvmConfiguration().emitIr())
            .setPie(pie)
            .setReferenceStrategy(ReferenceStrategy.POINTER_AS1)
            .setEmitAssembly(configuration.emitAsm())
            .addLlcOptions(configuration.llvmConfiguration().llcOptions().orElse(List.of()))
        );
        mainBuilder.setBackend(Backend.llvm);
        mainBuilder.setGc(configuration.gc().toString());
        mainBuilder.setMainClass(mainClassBuildItem.getClassName());
        mainBuilder.setOutputPath(outputDirectory);
        mainBuilder.setOutputName(nativeImageName);
        mainBuilder.setDiagnosticsHandler(diagnostics -> diagnostics.forEach(diagnostic -> {
            try {
                // todo: tie into maven output somehow
                diagnostic.appendTo(System.out);
            } catch (IOException e) {
                // just give up
            }
        }));
        // for now...
        mainBuilder.addGraalFeatures(graalvmFeatures.stream().map(NativeImageFeatureBuildItem::getQualifiedName).toList());
        mainBuilder.addAppPath(ClassPathEntry.of(nativeImageJar.getPath()));
        final Main main = mainBuilder.build();
        DiagnosticContext context = main.call();
        int errors = context.errors();
        int warnings = context.warnings();
        if (errors > 0) {
            if (warnings > 0) {
                System.out.printf("Compilation failed with %d error(s) and %d warning(s)%n", Integer.valueOf(errors), Integer.valueOf(warnings));
            } else {
                System.out.printf("Compilation failed with %d error(s)%n", Integer.valueOf(errors));
            }
        } else if (warnings > 0) {
            System.out.printf("Compilation completed with %d warning(s)%n", Integer.valueOf(warnings));
        }

        final DiagnosticContext dc = main.call();
        if (dc.errors() > 0) {
            throw new RuntimeException("Native image build failed due to errors");
        }
        return new QbiccResultBuildItem(outputDirectory.resolve(nativeImageName), "<todo>");
    }
}
