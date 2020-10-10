package muwa.forgegradle.legacysupport;

import groovy.lang.Closure;
import muwa.forgegradle.MuwaFG;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.internal.artifacts.dependencies.AbstractExternalModuleDependency;

import java.lang.reflect.Field;

public class DepExtension {
    public static final String EXTENSION_NAME = "muwafgdep";
    public static final String CONFIG_NAME = "__obfuscated";

    private final Project project;
    private String prefix;

    public DepExtension(Project project) {
        this.project = project;
    }

    public Dependency deobf(Object descrition) {
        return deobf(descrition, null);
    }

    public Dependency deobf(Object description, Closure<?> configure) {
        Dependency dependency = project.getDependencies().create(description, configure);
        project.getConfigurations().getByName(CONFIG_NAME).getDependencies().add(dependency);

        if (dependency instanceof ExternalModuleDependency) {
            ExternalModuleDependency copy = (ExternalModuleDependency) dependency.copy();
            try {
                Field name = AbstractExternalModuleDependency.class.getDeclaredField("name");
                name.setAccessible(true);
                name.set(copy, getPrefix() + copy.getName());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                project.getLogger().error("", e);
                return dependency;
            }
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
