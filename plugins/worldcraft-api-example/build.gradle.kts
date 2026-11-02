// Plugin de ejemplo de la API de Worldcraft (spec 037): la prueba en el juego y la documentación para desarrolladores.
version = "1.0.0"

dependencies {
    "compileOnly"(project(":worldcraft"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("WorldcraftApiExample")
}
