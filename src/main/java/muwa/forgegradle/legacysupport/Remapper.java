package muwa.forgegradle.legacysupport;

import muwa.forgegradle.util.HashFunction;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class Remapper {
    private final Configuration origin;
    private final Path cachePath;
    private final Project project;
    private static int index = 0;

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

        resolvedOrigin.getFirstLevelModuleDependencies()
            .forEach(dep -> {
                Path base;
                boolean flat = false;
                if (dep.getModuleGroup() == null || dep.getModuleGroup().isEmpty() || dep.getModuleVersion() == null || dep.getModuleVersion().isEmpty()) {
                    base = Paths.get("flatRepo");
                    flat = true;
                }
                else
                    base = Paths.get(
                        "repo",
                        dep.getModuleGroup() == null ? "" : dep.getModuleGroup().replace('.', File.pathSeparatorChar),
                        prefix + dep.getModuleName(),
                        dep.getModuleVersion() == null ? "" : dep.getModuleVersion()
                    );

                boolean flatRepo = flat;
                dep.getAllModuleArtifacts().forEach(art -> {
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
                    }
                    else {
                        try {
                            sameHash = false;
                            Files.createDirectories(hashPath.getParent());
                            Files.write(hashPath, Collections.singletonList(HashFunction.MD5.hash(art.getFile())));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    Path deobfPath = cachePath
                            .resolve(base)
                            .resolve((flatRepo ? prefix : "") + art.getFile().getName());

                    if (!Files.exists(deobfPath) || !sameHash) {
                        try {
                            Files.createDirectories(deobfPath.getParent());
                            System.out.println("remapping " + art + " with mappings " + mappings);

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

                            URLClassLoader loader = new URLClassLoader(new URL[]{bonPath.toUri().toURL()});
                            Class<?> impl = loader.loadClass("com.github.parker8283.bon2.BON2Impl");
                            Class<?> mappingsVersion = loader.loadClass("com.github.parker8283.bon2.data.MappingVersion");
                            Constructor<?> mappingsVersionConstructor = mappingsVersion.getDeclaredConstructor(String.class, File.class);
                            Constructor<?> errorHandler = loader.loadClass("com.github.parker8283.bon2.cli.CLIErrorHandler").getDeclaredConstructor();
                            Constructor<?> progressListener = loader.loadClass("com.github.parker8283.bon2.cli.CLIProgressListener").getDeclaredConstructor();

                            impl.getDeclaredMethod("remap", File.class, File.class, mappingsVersion, loader.loadClass("com.github.parker8283.bon2.data.IErrorHandler"), loader.loadClass("com.github.parker8283.bon2.data.IProgressListener"))
                                    .invoke(null, art.getFile(), deobfPath.toFile(), mappingsVersionConstructor.newInstance(mappings, mappingsDir), errorHandler.newInstance(), progressListener.newInstance());

                            loader.close();
                        } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                });
            });
    }
}
