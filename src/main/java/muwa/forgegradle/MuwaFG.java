package muwa.forgegradle;

import muwa.forgegradle.legacysupport.DepExtension;
import muwa.forgegradle.legacysupport.MappingsInfo;
import muwa.forgegradle.legacysupport.Remapper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class MuwaFG implements org.gradle.api.Plugin<Project> {
    @Override
    public void apply(Project project) {
        Configuration configuration = project.getConfigurations().maybeCreate(DepExtension.CONFIG_NAME);
        Path cachePath = Paths.get(
                project.getGradle().getGradleUserHomeDir().getPath(),
                "caches", "muwa_forge_gradle"
        );
        Remapper remapper = new Remapper(configuration, cachePath);
        project.getExtensions().create(DepExtension.EXTENSION_NAME, DepExtension.class, project);

        project.getRepositories().maven(repo -> {
            repo.setUrl(cachePath.resolve("repo").toUri());
        });

        project.getRepositories().flatDir(repo -> {
            repo.dir(cachePath.resolve("flatRepo"));
        });

        project.afterEvaluate(pr -> {
            MappingsInfo mappingsInfo = new MappingsInfo(pr);
            File mappingsDir = null;
            if (mappingsInfo.getMappingsChannel() == null) {
                mappingsDir = Paths.get(
                        project.getGradle().getGradleUserHomeDir().getPath(),
                        "caches",
                        "minecraft",
                        "net",
                        "minecraftforge",
                        "forge",
                        mappingsInfo.getApiVersion(),
                        "unpacked",
                        "conf"
                ).toFile();
            }
            else {
                mappingsDir = Paths.get(
                        project.getGradle().getGradleUserHomeDir().getPath(),
                        "caches",
                        "minecraft",
                        "de",
                        "oceanlabs",
                        "mcp",
                        "mcp_" + mappingsInfo.getMappingsChannel(),
                        mappingsInfo.getCustomVersion() == null ? mappingsInfo.getMappingsVersion() + "" : mappingsInfo.getCustomVersion()
                ).toFile();
            }

            remapper.remap(mappingsInfo.getName(), mappingsDir);
            project.getConfigurations().removeIf(c -> c.getName().equals(DepExtension.CONFIG_NAME));
        });
    }
}
