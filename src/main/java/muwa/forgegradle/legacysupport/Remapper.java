package muwa.forgegradle.legacysupport;

import muwa.forgegradle.util.HashFunction;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.tasks.JavaExec;
import sun.misc.IOUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class Remapper {
    private final Configuration origin;
    private final Path cachePath;
    private final Project project;
    public boolean decompile = true;
    private static int index;

    public Remapper(Configuration origin, Path cachePath, Project project) {
        this.origin = origin;
        this.cachePath = cachePath;
        this.project = project;
    }

    public void remap(String mappings, File mappingsDir) {
        ResolvedConfiguration resolvedOrigin = origin.getResolvedConfiguration();

        final String prefix = "deobf_" + mappings + "_";

        try {
            Files.createDirectories(cachePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        final File forgeFlower;
        if (decompile) {
            final Configuration c = project.getConfigurations().maybeCreate("__forgeflower");
            c.getDependencies().add(project.getDependencies().create("net.minecraftforge:forgeflower:1.+"));
            final ResolvedConfiguration origin = c.getResolvedConfiguration();
            forgeFlower = origin.getFirstLevelModuleDependencies()
                    .stream()
                    .flatMap(resolvedDependency -> resolvedDependency.getAllModuleArtifacts().stream())
                    .findFirst()
                    .map(ResolvedArtifact::getFile)
                    .orElseThrow(() -> new RuntimeException("ForgeFlower not found!"));
        }
        else {
            forgeFlower = null;
        }

        URLClassLoader loader = null;
        try {
            Path bonPath = cachePath.resolve("bon2.jar");

            if (!Files.exists(bonPath)) {
                System.out.println("Downloading BON-2.4.0.15");
                HttpsURLConnection con;
                try {
                    con = (HttpsURLConnection) new URL("https://ci.tterrag.com/job/BON2/15/artifact/build/libs/BON-2.4.0.15-all.jar").openConnection();
                } catch (IOException e) {
                    System.out.println("trying github link");
                    // when in 100 (one hundred) years tterrag's site finally goes down it will still hopefully be on github....
                    con = (HttpsURLConnection) new URL("https://github.com/Workbench61/Muwa-ForgeGradle-Extension/tree/master/lib/BON-2.4.0.15-all.jar").openConnection();
                }

                Files.createDirectories(cachePath);
                Files.write(bonPath, IOUtils.readAllBytes(con.getInputStream()));
                con.disconnect();
                System.out.println("Done Downloading BON-2.4.0.15");
            }

            loader = new URLClassLoader(new URL[]{bonPath.toUri().toURL()});
            Class<?> impl = loader.loadClass("com.github.parker8283.bon2.BON2Impl");
            Class<?> mappingsVersion = loader.loadClass("com.github.parker8283.bon2.data.MappingVersion");
            Constructor<?> mappingsVersionConstructor = mappingsVersion.getDeclaredConstructor(String.class, File.class);
            Constructor<?> errorHandler = loader.loadClass("com.github.parker8283.bon2.cli.CLIErrorHandler").getDeclaredConstructor();
            Constructor<?> progressListener = loader.loadClass("com.github.parker8283.bon2.cli.CLIProgressListener").getDeclaredConstructor();

            URLClassLoader finalLoader = loader;
            resolvedOrigin.getFirstLevelModuleDependencies()
                    .forEach(dep -> {
                        final Path base;
                        if (dep.getModuleGroup() == null || dep.getModuleGroup().isEmpty() || dep.getModuleVersion() == null || dep.getModuleVersion().isEmpty())
                            base = Paths.get("flatRepo");
                        else {
                            base = Paths.get(
                                    "repo",
                                    dep.getModuleGroup() == null ? "" : dep.getModuleGroup().replace('.', File.separatorChar),
                                    prefix + dep.getModuleName(),
                                    dep.getModuleVersion() == null ? "" : dep.getModuleVersion()
                            );
                            if ("curse.maven".equals(dep.getModuleGroup())) {
                                try {
                                    Path pomPath = base.resolve(prefix + dep.getModuleName() + '-' + (dep.getModuleVersion() == null ? "" : dep.getModuleVersion()) + ".pom");
                                    pomPath = cachePath.resolve(pomPath);
                                    Files.createDirectories(pomPath.getParent());
                                    Files.write(
                                            pomPath,
                                            ("<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                                                    "<modelVersion>4.0.0</modelVersion>\n" +
                                                    "<groupId>" + dep.getModuleGroup() + "</groupId>\n" +
                                                    "<artifactId>" + prefix + dep.getModuleName() + "</artifactId>\n" +
                                                    "<version>" + (dep.getModuleVersion() == null ? "" : dep.getModuleVersion()) + "</version>\n" +
                                                    "</project>").getBytes(StandardCharsets.UTF_8)
                                    );
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }


                        dep.getAllModuleArtifacts().forEach(art -> {
                            if (!art.getExtension().equals("jar"))
                                return;

                            Path hashPath = cachePath.resolve("hashes")
                                    .resolve(base)
                                    .resolve(art.getFile().getName() + ".md5");

                            boolean sameHash = false;

                            if (Files.exists(hashPath)) {
                                try {
                                    sameHash = HashFunction.MD5.hash(art.getFile()).equals(Files.readAllLines(hashPath).get(0));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                try {
                                    Files.createDirectories(hashPath.getParent());
                                    Files.write(hashPath, Collections.singletonList(HashFunction.MD5.hash(art.getFile())));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            Path deobfPath = cachePath
                                    .resolve(base)
                                    .resolve((prefix) + art.getFile().getName());

                            Path sourcesPath = cachePath
                                    .resolve(base)
                                    .resolve(prefix + art.getFile().getName().replace(".jar", "-sources.jar"));

                            boolean redoDecompile = false;
                            if (!Files.exists(deobfPath) || !sameHash) {
                                redoDecompile = true;
                                try {
                                    Files.createDirectories(deobfPath.getParent());
                                    System.out.println("remapping " + art + " with mappings " + mappings);

                                    impl.getDeclaredMethod("remap", File.class, File.class, mappingsVersion, finalLoader.loadClass("com.github.parker8283.bon2.data.IErrorHandler"), finalLoader.loadClass("com.github.parker8283.bon2.data.IProgressListener"))
                                            .invoke(null, art.getFile(), deobfPath.toFile(), mappingsVersionConstructor.newInstance(mappings, mappingsDir), errorHandler.newInstance(), progressListener.newInstance());

                                } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                    e.printStackTrace();
                                }
                            }

                            if (decompile && (!Files.exists(sourcesPath) || redoDecompile)) {
                                final JavaExec javaExec = project.getTasks().create("_decompile_" + index++, JavaExec.class);
                                try {
                                    try (JarFile jarFile = new JarFile(forgeFlower)) {
                                        javaExec.setMain(jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    javaExec.classpath(forgeFlower);
                                    javaExec.setArgs(Arrays.asList(deobfPath.toString(), sourcesPath.toString()));
                                    javaExec.exec();
                                } finally {
                                    project.getTasks().remove(javaExec);
                                }
                            }
                        });
                    });

            loader.close();
        } catch (NoSuchMethodException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        finally {
            if (loader != null) {
                try {
                    loader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
