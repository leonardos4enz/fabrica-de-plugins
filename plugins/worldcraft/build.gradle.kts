version = "0.14.0"

tasks.named<Jar>("jar") {
    archiveBaseName.set("Worldcraft")
}
val sourceSets = project.extensions.getByType<SourceSetContainer>()

// Vista sin servidor de montañas e islas (herramienta de desarrollo, spec 026):
// ./gradlew :worldcraft:terrainPreview -Pout=<archivo.png> [-Pwhat="mountain medium;island large trees=5"]
tasks.register<JavaExec>("terrainPreview") {
    group = "worldcraft"
    description = "Corre montañas e islas en un mundo en memoria y las dibuja en un PNG"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.leo.worldcraft.terrain.TerrainPreviewMain")
    workingDir = projectDir
    args(providers.gradleProperty("out").orElse("build/terrain.png").get(), providers.gradleProperty("what").orElse("").get())
}

// Spec 037: el plugin de ejemplo de la API se arma con Worldcraft (la suite instala los dos desde dist/).
tasks.named("dist") {
    finalizedBy(":worldcraft-api-example:dist")
}
