package muwa.forgegradle.legacysupport;

import org.gradle.api.Project;

import java.lang.reflect.Field;

public class MappingsInfo {
    private String mappingsChannel;
    private int mappingsVersion;
    private String customVersion;
    private String apiVersion;

    public MappingsInfo(Project project) {
        try {
            Object minecraft = project.getExtensions().getByName("minecraft");
            Class<?> mcClass = Class.forName("net.minecraftforge.gradle.common.BaseExtension");
            Field mappingsChannel__ = mcClass.getDeclaredField("mappingsChannel");
            mappingsChannel__.setAccessible(true);
            Field mappingsVersion__ = mcClass.getDeclaredField("mappingsVersion");
            mappingsVersion__.setAccessible(true);
            Field customVersion__ = mcClass.getDeclaredField("customVersion");
            customVersion__.setAccessible(true);
            Class<?> upClass = Class.forName("net.minecraftforge.gradle.user.patch.UserPatchExtension");
            Field apiVersion__ = upClass.getDeclaredField("apiVersion");
            apiVersion__.setAccessible(true);
            apiVersion = (String) apiVersion__.get(minecraft);
            mappingsChannel = (String) mappingsChannel__.get(minecraft);
            mappingsVersion = mappingsVersion__.getInt(minecraft);
            customVersion = (String) customVersion__.get(minecraft);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public String getMappingsChannel() {
        return mappingsChannel;
    }

    public int getMappingsVersion() {
        return mappingsVersion;
    }

    public String getCustomVersion() {
        return customVersion;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getName() {
        if (mappingsChannel == null)
            return apiVersion;
        else
            return mappingsChannel + "_" + (customVersion == null ? mappingsVersion + "" : customVersion);
    }
}
