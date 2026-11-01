version = "0.3.0"

tasks.named<Jar>("jar") {
    archiveBaseName.set("SkinStatues")
}

val sourceSets = project.extensions.getByType<SourceSetContainer>()
val resources = layout.projectDirectory.dir("src/main/resources")

// Colores de los bloques medidos de las texturas del Minecraft instalado del dueño (plan 021 §1.2): al repositorio solo
// van los números. ./gradlew :skinstatues:blockColors -Pmc="C:\...\.minecraft\versions\1.21.4\1.21.4.jar"
tasks.register<JavaExec>("blockColors") {
    group = "skinstatues"
    description = "Genera src/main/resources/block-colors.yml desde el jar del cliente de Minecraft (-Pmc=...)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.leo.skinstatues.tools.BlockColorsMain")
    args(providers.gradleProperty("mc").orElse("").get(), resources.file("block-colors.yml").asFile.absolutePath)
}

// Skin y capa de ejemplo, dibujadas por código (originales, nada de Mojang): ./gradlew :skinstatues:exampleSkin
tasks.register<JavaExec>("exampleSkin") {
    group = "skinstatues"
    description = "Genera skins/Example.png y skins/Example-cape.png"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.leo.skinstatues.tools.ExampleSkinMain")
    args(resources.dir("skins").asFile.absolutePath)
}

// Vista previa sin servidor de las poses con la skin de ejemplo (herramienta de desarrollo):
// ./gradlew :skinstatues:preview -Pout=<archivo.png> [-Pheight=48] [-Ponly=waving,sitting] [-Pgrid=true]
tasks.register<JavaExec>("preview") {
    group = "skinstatues"
    description = "Dibuja las poses de poses.yml con la skin de ejemplo (frente, costado, espalda, isométrica)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.leo.skinstatues.tools.PreviewMain")
    args(resources.asFile.absolutePath, providers.gradleProperty("out").orElse("build/preview.png").get(),
        providers.gradleProperty("height").orElse("48").get(), providers.gradleProperty("only").orElse("").get(),
        providers.gradleProperty("grid").orElse("false").get())
}
