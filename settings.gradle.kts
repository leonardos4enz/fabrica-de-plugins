rootProject.name = "minecraft-plugins"

// Cada carpeta en plugins/ con build.gradle.kts es un plugin de la fábrica.
file("plugins").listFiles()
    ?.filter { it.isDirectory && it.resolve("build.gradle.kts").exists() }
    ?.sortedBy { it.name }
    ?.forEach { dir ->
        include(":${dir.name}")
        project(":${dir.name}").projectDir = dir
    }
