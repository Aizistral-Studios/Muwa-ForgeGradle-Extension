package muwa.forgegradle;

import muwa.forgegradle.legacysupport.DepExtension;
import muwa.forgegradle.legacysupport.LegacyExtension;
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
        int fgVersion;
        try {
            Class<?> aClass = Class.forName("net.minecraftforge.gradle.common.BaseExtension");
            aClass.getDeclaredField("forgeGradleVersion");
            fgVersion = 20;
        } catch (ClassNotFoundException e) {
            fgVersion = 30;
        } catch (NoSuchFieldException e) {
            fgVersion = 10;
        }

        if (fgVersion <= 20) {
            new LegacyExtension().apply(project);
        }
    }
}
