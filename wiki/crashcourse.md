---
outline: deep
---

# Multiversion Gradle Crash Course

The template is set up to be easy to add dependencies and work without in-depth Gradle knowledge, but if you'd like
to understand how this whole setup works from top to bottom, this page is for you. Otherwise, you're free to skip it!

## Why Multiversion?

This template combines two different things---*multiversion* and *multiloader*.

Traditionally, if you want to support multiple Minecraft versions, you would need to create multiple Git branches for each version.
This can be a major pain if you are actively maintaining each one, so instead, *multiversion* Minecraft mods
build all jars from a single branch, often called a *monorepo*.

Of course, you still need some way to separate version specific code. This is typically done with some kind of *preprocessor*,
which enables conditional compilation so that each Minecraft version target only includes the code that works for it.
While this makes Gradle scripts more complicated to set up and understand, it massively simplifies updates across many supported versions.
Fortunately, setting up Gradle is largely a one time operation, and this template takes care of most of it.

Likewise, if you wanted to support both Forge and Fabric, you would also need separated Git branches, and this can compound
the maintenance problem when trying to support multiple versions as well. To make this easier, **multiloader** Minecraft mods build from one repo,
typically with a lot of shared code (since both modloaders are just Minecraft!).

You've probably heard of Architectury, which is a multiloader Gradle setup that uses separated `common`, `forge`, and `fabric`
sourcesets. However, it's just simply cleaner to combine both, since they can both be set up with preprocessors to build from a single `src` directory.

Note that you don't actually need to do both multiloader and multiversion---you can remove one or the other and this setup will work the same.

---

For more context, you will probably want to read the [Stonecutter wiki!](https://stonecutter.kikugie.dev/stonecutter/tips) This is the backbone
of this template, and has a lot of quirks. For the most part though, you can ignore the parts about the versioned comments,
unless you want to use them in `.json5` resource files.

## Why Do You Have Three Gradle Files?

Because of the way the Stonecutter plugin works, a few different Gradle files are needed. If you cloned the template only
to find yourself asking "wtf is a stonecutter.gradle," it's actually pretty simple.

`settings.gradle.kts` is the root of the project. This is the first thing loaded by your IDE, and is usually used for
Maven repositories, but in this project it also is used to bootstrap Blahaj. You'll see here below that, among other things,
the required plugins are applied and configured. This is also where you will add or remove supported versions and loaders:

::: code-group
```kts [settings.gradle.kts]
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.txni.dev/releases")
    }
}

plugins {
    id("dev.architectury.loom") version "1.9-SNAPSHOT" apply false
    kotlin("jvm") version "2.0.0" apply false
    kotlin("plugin.serialization") version "2.0.0" apply false
    id("toni.blahaj") version "1.0.78"
    id("dev.kikugie.stonecutter") version "0.6-alpha.5"
}

blahaj {
    init(rootProject) {
        mc("1.20.1", "fabric", "forge")
        mc("1.21.1", "fabric", "neoforge")
    }
}

rootProject.name = settings.extra["mod.name"] as String
``` 
:::

`stonecutter.gradle.kts` is the main controller, which handles the multiversion builds by creating a new project for
each version registered with Stonecutter. You can also register your normal plugins here, though you don't need to apply 
all of them since this file doesn't do the actual build. You won't see most of the magic in this file, and it's basically empty with Blahaj, 
but you can do some pretty advanced [configuration stuff](https://stonecutter.kikugie.dev/stonecutter/configuration) here.

::: code-group
```kts [stonecutter.gradle.kts]
plugins {
    id("dev.kikugie.stonecutter")
    id("toni.blahaj")
}

stonecutter active "1.21.1-neoforge" /* [SC] DO NOT EDIT */
``` 
:::

`build.gradle.kts` is the one you're probably familiar with, and what you will be modifying the most, usually to add your own dependencies.

::: code-group
```kts [build.gradle.kts]
plugins {
    id("toni.blahaj")
}

blahaj {
    config {
        
    }
    setup {
        txnilib("1.0.22")
        forgeConfig()
    }
}
``` 
:::

## The Blahaj Setup Process

Blahaj is first initialized in `settings.gradle.kts`, where it creates a `BlahajSettings` Gradle extension, providing access to the 
`init(rootProject) { }` function. This is where it configures Stonecutter, and passes the Stonecutter `TreeBuilder` to the user for configuration.
It also provides the `mc("version", "fabric", "forge")` function, which is a wrapper for the Stonecutter functions that ensures 
the `versions/` directories and `loom.platform` properties are set up properly in advance of Stonecutter being initialized.

Then, it is applied to the Stonecutter controller in `stonecutter.gradle.kts`, where it doesn't do much besides register some chiseledTasks.
These tasks run for each version all at once, which is a useful way to automate mass builds.

Then, it's applied to the main `build.gradle.kts`, which is where most of the magic happens. Before creating the BlahajBuild extension though, 
it applies most of the plugins that the main build script needs, such as Arch Loom, which had their versions configured in `settings.gradle.kts`.

### BlahajBuild Extension Init

First, Blahaj will automatically grab references to the applied plugins to configure Loom and access Stonecutter information. The first thing
it does is initialize all needed data, and create a `ModData` instance, which loads a ton of properties from `gradle.properties`, 
as well as Stonecutter version info, into one object. You can use this data in your scripts to selectively apply configuration.

After that, it automatically applies a bunch of common default repositories. These get added to the project itself, and removes the need to configure
repositories for most things like Modrinth and Curse mavens.

Then, Architectury Loom is set up, which mostly involves setting Access Widener paths, mixin config paths, and generates run configurations and datagen runs.

Then, it creates a `DependencyHandlerScope` and applies all the necessary Minecraft dependencies, including setting up game versions, adding
mappings (Official with Parchment by default), and some necessary things like Fabric API and ModMenu. This is also where some optional things,
like Txnilib and Forge Config, are applied.

The versions for each of these are fetched from `VersionInfo.versionDefaults`, which contains a nested map with each configured version for all the 
dependencies that Blahaj fetches. Blahaj only intends to provide sane defaults, and each one of these is user overridable with the corresponding 
Gradle properties key, such as `deps.fabric_loader`. You should use Stonecutter versioned properties to override these per-project.

In the dependency scope, the `configure` function for the BlahajSettings is ran. This assembles a list of `BlahajDependency` that is used to 
apply the needed deps, and also create a list of needed mod IDs and slugs for injection into `fabric.mod.json` and `mods.toml`, into and Curseforge/Modrinth releases

Then, all the Gradle tasks are added, which includes basic necessary things like `compileJava` and `jar`, as well as custom helper tasks such as `buildAndCopyToModrinth`.
This is where `processResources` is set up, which is used to inject all settings and dependencies in platform metadata.

During the build, `ManifoldMC` injects the necessary parameters into the preprocessor plugin, which uses the Stonecutter version info to generate
a list of `build.properties` for each chiseled build version.

After that, the build is ready to go, and the only other thing Blahaj handles is publishing the mod.