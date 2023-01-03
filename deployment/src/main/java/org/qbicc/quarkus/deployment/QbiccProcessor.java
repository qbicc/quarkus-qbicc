package org.qbicc.quarkus.deployment;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedNativeImageClassBuildItem;
import io.quarkus.deployment.builditem.MainClassBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ForceNonWeakReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JPMSExportBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.LambdaCapturingTypeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBundleBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSecurityProviderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSystemPropertyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeReinitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.UnsafeAccessedFieldBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageSourceJarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.runtime.util.ClassPathUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.RepositorySystemSession;
import org.qbicc.context.DiagnosticContext;
import org.qbicc.driver.ClassPathItem;
import org.qbicc.machine.arch.Platform;
import org.qbicc.main.Backend;
import org.qbicc.main.ClassPathEntry;
import org.qbicc.main.Main;
import org.qbicc.main.QbiccMavenResolver;
import org.qbicc.plugin.initializationcontrol.QbiccFeature;
import org.qbicc.plugin.llvm.LLVMConfiguration;
import org.qbicc.plugin.llvm.ReferenceStrategy;
import org.qbicc.quarkus.config.QbiccConfiguration;
import org.qbicc.quarkus.spi.QbiccFeatureBuildItem;
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

    private void resolveClassPath(DiagnosticContext ctxt, Consumer<ClassPathItem> classPathItemConsumer, final List<ClassPathEntry> paths, Runtime.Version version) {
        try {
            MavenArtifactResolver mar = MavenArtifactResolver.builder().build();
            QbiccMavenResolver resolver = new QbiccMavenResolver(mar.getSystem());
            Settings settings = mar.getMavenContext().getEffectiveSettings();
            RepositorySystemSession session = mar.getSession();
            List<ClassPathItem> result = resolver.requestArtifacts(session, settings, paths, ctxt, version);
            result.forEach(classPathItemConsumer);
        } catch (BootstrapMavenException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BuildStep
    QbiccFeatureBuildItem generateFeature(List<RuntimeInitializedClassBuildItem> runtimeInitializedClassBuildItems,
                         List<RuntimeInitializedPackageBuildItem> runtimeInitializedPackageBuildItems,
                         List<RuntimeReinitializedClassBuildItem> runtimeReinitializedClassBuildItems,
                         List<NativeImageProxyDefinitionBuildItem> proxies,
                         List<NativeImageResourcePatternsBuildItem> resourcePatterns,
                         List<NativeImageResourceBundleBuildItem> resourceBundles,
                         List<ReflectiveMethodBuildItem> reflectiveMethods,
                         List<ReflectiveFieldBuildItem> reflectiveFields,
                         List<ReflectiveClassBuildItem> reflectiveClassBuildItems,
                         List<ServiceProviderBuildItem> serviceProviderBuildItems,
                         List<UnsafeAccessedFieldBuildItem> unsafeAccessedFields,
                         List<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses,
                         List<LambdaCapturingTypeBuildItem> lambdaCapturingTypeBuildItems) {

        QbiccFeature qf = new QbiccFeature();

        // These fields of the qbicc feature are computed from from multiple input BuildItems
        ArrayList<String> qfInitializeAtRuntime = new ArrayList<>();
        ArrayList<QbiccFeature.ReflectiveClass> qfReflectiveClasses = new ArrayList<>();
        ArrayList<String> qfRuntimeResources = new ArrayList<>();

        // Now, go through each input BuildItem and translate the information to the QbiccFeature

        for (RuntimeInitializedClassBuildItem i : runtimeInitializedClassBuildItems) {
            qfInitializeAtRuntime.add(i.getClassName());
        }

        if (runtimeInitializedPackageBuildItems.size() > 0) {
            System.out.printf("TODO: QbiccProcessor: ignored %d runtime initialized packages\n", runtimeReinitializedClassBuildItems.size());
        }

        if (runtimeReinitializedClassBuildItems.size() > 0) {
            System.out.printf("TODO: QbiccProcessor: ignored %d runtime reinitialized classes\n", runtimeReinitializedClassBuildItems.size());
        }

        if (proxies.size() > 0) {
            System.out.printf("TODO: QbiccProcessor: ignored %d proxies\n", proxies.size());
        }

        for (NativeImageResourcePatternsBuildItem rp : resourcePatterns) {
            for (String includes : rp.getIncludePatterns()) {
                qfRuntimeResources.add(includes);
            }
        }

        if (resourceBundles.size() > 0) {
            System.out.printf("TODO: QbiccProcessor: ignored %d resource bundles\n", resourceBundles.size());
        }

        if (reflectiveMethods.size() > 0) {
            ArrayList<QbiccFeature.Method> refMethods = new ArrayList<>();
            for (ReflectiveMethodBuildItem rm: reflectiveMethods) {
                refMethods.add(new QbiccFeature.Method(rm.getDeclaringClass(), rm.getName(), rm.getParams()));
            }
            qf.reflectiveMethods = refMethods.toArray(QbiccFeature.Method[]::new);
        }

        if (reflectiveFields.size() > 0) {
            ArrayList<QbiccFeature.Field> refFields = new ArrayList<>();
            for (ReflectiveFieldBuildItem rf : reflectiveFields) {
                refFields.add(new QbiccFeature.Field(rf.getDeclaringClass(), rf.getName()));
            }
            qf.reflectiveFields = refFields.toArray(QbiccFeature.Field[]::new);
        }

        for (ReflectiveClassBuildItem i : reflectiveClassBuildItems) {
            for (String name : i.getClassNames()) {
                qfReflectiveClasses.add(new QbiccFeature.ReflectiveClass(name, i.isFields(), i.isConstructors(), i.isMethods()));
            }
        }

        for (ServiceProviderBuildItem sp: serviceProviderBuildItems) {
            qfRuntimeResources.add(sp.serviceDescriptorFile());
            for (String pc: sp.providers()) {
                qfReflectiveClasses.add(new QbiccFeature.ReflectiveClass(pc, false, true, false));
            }
        }

        if (unsafeAccessedFields.size() > 0) {
            System.out.printf("TODO: QbiccProcessor: ignored %d unsafe accessed fields\n", unsafeAccessedFields.size());
        }

        if (jniRuntimeAccessibleClasses.size() > 0) {
            // Qbicc doesn't support JNI, so don't even consume this build item in the first place?
            System.out.printf("TODO: QbiccProcessor: ignored %d jni runtime accessible classes\n", jniRuntimeAccessibleClasses.size());
        }

        if (lambdaCapturingTypeBuildItems.size() > 0) {
            // TODO: Related to GraalVM serialization of initial heap, so not relevant for qbicc??  Don't consume this build item?
            System.out.printf("TODO: QbiccProcessor: ignored %d lambda capturing types\n", lambdaCapturingTypeBuildItems.size());
        }

        // Finalize the fields that were produced by multiple items.
        qf.initializeAtRuntime = qfInitializeAtRuntime.toArray(String[]::new);
        qf.reflectiveClasses = qfReflectiveClasses.toArray(QbiccFeature.ReflectiveClass[]::new);
        qf.runtimeResources = qfRuntimeResources.toArray(String[]::new);

        return new QbiccFeatureBuildItem(qf);
    }

    @BuildStep
    QbiccResultBuildItem build(
        QbiccConfiguration configuration,
        QbiccFeatureBuildItem dynamicQbiccFeature,
        MainClassBuildItem mainClassBuildItem,
        NativeImageSourceJarBuildItem nativeImageJar,
        List<NativeImageSystemPropertyBuildItem> nativeImageProperties,
        List<NativeImageSecurityProviderBuildItem> nativeImageSecurityProviders,
        OutputTargetBuildItem outputTargetBuildItem,
        PackageConfig packageConfig
    ) {
        final Path outputDirectory = outputTargetBuildItem.getOutputDirectory().resolve("native-" + outputTargetBuildItem.getBaseName());
        final String nativeImageName = outputTargetBuildItem.getBaseName() + packageConfig.getRunnerSuffix();
        boolean isContainerBuild = false; // this is something we have with graalvm
        boolean defaultPie = ! SystemUtils.IS_OS_WINDOWS && ! isContainerBuild;
        final boolean pie = configuration.pie().orElse(Boolean.valueOf(defaultPie)).booleanValue();

        final Main.Builder mainBuilder = Main.builder();
        if (configuration.classLibVersion().isPresent()) {
            mainBuilder.setClassLibVersion(configuration.classLibVersion().get());
        }
        final Platform platform = configuration.platform().orElse(Platform.HOST_PLATFORM);
        URL defaultFeature = QbiccProcessor.class.getResource("/qbicc-feature.yaml");
        mainBuilder.addQbiccYamlFeatures(List.of(defaultFeature));
        mainBuilder.addQbiccFeature((QbiccFeature)dynamicQbiccFeature.getFeature());
        mainBuilder.setPlatform(platform);
        mainBuilder.setIsPie(pie);
        mainBuilder.setLlvmConfigurationBuilder(LLVMConfiguration.builder()
            .setEmitIr(configuration.llvmConfiguration().emitIr())
            .setPie(pie)
            .setReferenceStrategy(ReferenceStrategy.POINTER_AS1)
            .setEmitAssembly(configuration.emitAsm())
            .setPlatform(platform)
            .setCompileOutput(true)
            .setOpaquePointers(configuration.llvmConfiguration().opaquePointers())
            .addLlcOptions(configuration.llvmConfiguration().llcOptions().orElse(List.of()))
        );
        mainBuilder.setBackend(Backend.llvm);
        mainBuilder.setGc(configuration.gc().toString());
        mainBuilder.setMainClass(mainClassBuildItem.getClassName());
        mainBuilder.addBuildTimeInitRootClass("io.quarkus.runner.ApplicationImpl");
        mainBuilder.setOutputPath(outputDirectory);
        mainBuilder.setOutputName(nativeImageName);
        mainBuilder.setClassPathResolver(this::resolveClassPath);
        mainBuilder.setDiagnosticsHandler(diagnostics -> diagnostics.forEach(diagnostic -> {
            try {
                // todo: tie into maven output somehow
                diagnostic.appendTo(System.out);
            } catch (IOException e) {
                // just give up
            }
        }));
        addAppPath(mainBuilder, nativeImageJar.getPath(), new HashSet<>());
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

    private void addAppPath(final Main.Builder mainBuilder, final Path path, final Set<Path> visited) {
        if (! visited.add(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            mainBuilder.addAppPath(ClassPathEntry.of(path));
        } else {
            // assume it's a JAR
            try (JarFile jar = new JarFile(path.toFile())) {
                final String classPathAttribute = jar.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
                if (classPathAttribute != null) {
                    final String[] items = classPathAttribute.split(" ");
                    for (String item : items) {
                        addAppPath(mainBuilder, path.getParent().resolve(item), visited);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mainBuilder.addAppPath(ClassPathEntry.of(path));
        }
    }
}
