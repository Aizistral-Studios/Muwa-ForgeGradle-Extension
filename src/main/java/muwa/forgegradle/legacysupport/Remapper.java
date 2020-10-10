package muwa.forgegradle.legacysupport;

import com.github.parker8283.bon2.BON2Impl;
import com.github.parker8283.bon2.cli.CLIProgressListener;
import com.github.parker8283.bon2.data.MappingVersion;
import muwa.forgegradle.util.HashFunction;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class Remapper {
    private final Configuration origin;
    private final Path cachePath;

    public Remapper(Configuration origin, Path cachePath) {
        this.origin = origin;
        this.cachePath = cachePath;
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
                            BON2Impl.remap(art.getFile(), deobfPath.toFile(), new MappingVersion(mappings, mappingsDir), (s, b) -> true, new CLIProgressListener());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            });
    }
}
