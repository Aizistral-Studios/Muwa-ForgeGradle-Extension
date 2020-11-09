package muwa.forgegradle.legacysupport;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;

import java.util.HashMap;

public class DepExtension {
    public static final String EXTENSION_NAME = "muwafgdep";
    public static final String CONFIG_NAME = "__obfuscated";

    private final Project project;
    private final Remapper remapper;
    private String prefix;

    public DepExtension(Project project, Remapper remapper) {
        this.project = project;
        this.remapper = remapper;
    }

    public void decompile() {
        remapper.decompile = true;
    }

    public void decompile(boolean value) {
        remapper.decompile = value;
    }

    public Dependency deobf(Object descrition) {
        return deobf(descrition, null);
    }

    public Dependency deobf(Object description, Closure<?> configure) {
        Dependency dependency = project.getDependencies().create(description, configure);
        project.getConfigurations().getByName(CONFIG_NAME).getDependencies().add(dependency);

        if (dependency instanceof ExternalModuleDependency) {
//            ExternalModuleDependency copy = (ExternalModuleDependency) dependency.copy();
            final HashMap<String, String> map = new HashMap<>();
            map.put("name", getPrefix() + dependency.getName());
            if (dependency.getGroup() != null && !dependency.getGroup().isEmpty())
                map.put("group", dependency.getGroup());
            if (dependency.getVersion() != null && !dependency.getVersion().isEmpty())
                map.put("version", dependency.getVersion());
            final ExternalModuleDependency copy = (ExternalModuleDependency) project.getDependencies().create(map, configure);
            ((ExternalModuleDependency) dependency).getArtifacts().forEach(art -> {
                copy.artifact(newArt -> {
                    newArt.setName(getPrefix() + art.getName());
                    newArt.setClassifier(art.getClassifier());
                    newArt.setExtension(art.getExtension());
                    newArt.setType(art.getType());
                    newArt.setUrl(art.getUrl());
                });
            });

            return copy;
        }

        if (dependency instanceof FileCollectionDependency) {
            project.getLogger().warn("files(...) dependencies are not deobfuscated. Use a flatDir repository instead: https://docs.gradle.org/current/userguide/repository_types.html#sec:flat_dir_resolver");
        }

        project.getLogger().warn("Cannot deobfuscate dependency of type {}, using obfuscated version!", dependency.getClass().getSimpleName());
        return dependency;
    }

    private String getPrefix() {
        if (prefix == null)
            prefix = "deobf_" + new MappingsInfo(project).getName() + "_";
        return prefix;
    }
}
