# Crea un plugin nuevo en la fábrica: proyecto Gradle + carpeta de spec con las plantillas SDD.
# Uso: powershell -File tools/nuevo-plugin.ps1 -Nombre MiPlugin -Spec 002 [-Titulo "núcleo"]
# Guardado en UTF-8 con BOM: Windows PowerShell 5.1 lee como ANSI un script sin BOM y corrompe los acentos.
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Z][A-Za-z0-9]{2,30}$')][string]$Nombre,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d{3}$')][string]$Spec,
    [string]$Titulo = "nucleo"
)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$id = $Nombre.ToLower()
$dir = Join-Path $raiz "plugins\$id"
if (Test-Path $dir) { throw "Ya existe plugins\$id" }

$utf8 = New-Object System.Text.UTF8Encoding($false)
function Escribir([string]$ruta, [string]$texto) {
    New-Item -ItemType Directory -Force (Split-Path -Parent $ruta) | Out-Null
    [IO.File]::WriteAllText($ruta, $texto, $utf8)
}

$paquete = "dev.leo.$id"
$rutaJava = Join-Path $dir ("src\main\java\" + $paquete.Replace('.', '\'))

Escribir (Join-Path $dir "build.gradle.kts") @"
version = "0.1.0"

tasks.named<Jar>("jar") {
    archiveBaseName.set("$Nombre")
}
"@

Escribir (Join-Path $rutaJava "$Nombre.java") @"
package $paquete;

import org.bukkit.plugin.java.JavaPlugin;

public final class $Nombre extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
    }
}
"@

Escribir (Join-Path $dir "src\main\resources\plugin.yml") @"
name: $Nombre
version: '`${version}'
main: $paquete.$Nombre
api-version: '1.21'
author: Leo
"@

Escribir (Join-Path $dir "src\main\resources\config.yml") "# Configuración de $Nombre`n"

# El README se actualiza al cerrar cada spec (CLAUDE.md); la tabla de Compatibilidad solo dice «Verificada» con evidencia.
Escribir (Join-Path $dir "README.md") @"
# $Nombre

Qué hace, en una frase. Spec: [$Spec](../../docs/sdd/specs/$Spec-$id-$Titulo/spec.md).

## Compatibilidad
| Servidor | Estado | Evidencia |
|---|---|---|
| Paper 1.21.4 | Sin verificar | Pendiente de las compuertas 2 y 3a |

- **Java:** 21 o más.
- **Opcional:** —

## Instalación
1. Copia ``$Nombre-x.y.z.jar`` a ``plugins/``.
"@

$specDir = Join-Path $raiz "docs\sdd\specs\$Spec-$id-$Titulo"
foreach ($doc in 'spec', 'plan', 'tasks', 'verify') {
    $texto = [IO.File]::ReadAllText((Join-Path $raiz "docs\sdd\plantillas\$doc.md"), $utf8)
    $texto = $texto.Replace('NNN', $Spec).Replace('Plugin: título', "${Nombre}: $Titulo").Replace('Nombre', $Nombre)
    Escribir (Join-Path $specDir "$doc.md") $texto
}

Write-Host "Plugin creado en plugins\$id y spec en docs\sdd\specs\$Spec-$id-$Titulo"
Write-Host "Siguiente paso: SPECIFY (spec.md), agregarla al índice de docs\sdd\README.md y la fila del plugin al README raíz"
