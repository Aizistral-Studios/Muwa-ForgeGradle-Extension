## About
This is plugin for using in Minecraft dev environment alongside [ForgeGradle](https://github.com/MinecraftForge/ForgeGradle).

Mainly targeting extended support for legacy versions (1.7.10).

It will work with 1.7.10.

```
apply plugin: 'muwafg'
```

### Deobfuscated Dependencies
Support for deobfuscated dependencies like in FG-3
```
dependencies {
    compile muwafgdep.deobf('group:name:version')
    compile muwafgdep.deobf('group:name:version', {/* configuration */})
}
```
Just reload the project to apply changes.

Cache located at `.gradle/caches/muwa_forge_gradle`
