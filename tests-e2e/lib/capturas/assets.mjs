// Assets del cliente de Minecraft instalado (spec 004, H2 CA5): fuente, sprites del HUD, GUI, ítems, bloques,
// efectos e idioma. Se leen del jar de la versión y del índice de assets del lanzador, y se extraen a
// tests-e2e/.cache/minecraft-<versión>/ (ignorado por git). Nunca se copian al repositorio ni se lee nada de cuentas.
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { leerZip } from './zip.mjs'

const PREFIJOS = [
  'assets/minecraft/font/',
  'assets/minecraft/textures/font/',
  'assets/minecraft/textures/gui/sprites/hud/',
  'assets/minecraft/textures/gui/sprites/boss_bar/',
  'assets/minecraft/textures/gui/sprites/tooltip/',
  'assets/minecraft/textures/gui/sprites/container/',
  'assets/minecraft/textures/gui/container/',
  'assets/minecraft/textures/gui/menu_background.png',
  'assets/minecraft/textures/gui/sprites/widget/',
  'assets/minecraft/textures/mob_effect/',
  'assets/minecraft/textures/item/',
  'assets/minecraft/textures/block/',
  'assets/minecraft/lang/en_us.json'
]

export function carpetaMinecraft () {
  if (process.env.FABRICA_MINECRAFT) return process.env.FABRICA_MINECRAFT
  if (process.platform === 'win32') return path.join(process.env.APPDATA ?? path.join(os.homedir(), 'AppData/Roaming'), '.minecraft')
  if (process.platform === 'darwin') return path.join(os.homedir(), 'Library/Application Support/minecraft')
  return path.join(os.homedir(), '.minecraft')
}

/** Extrae (una vez) lo necesario y devuelve la carpeta con los assets y el idioma elegido. */
export function prepararAssets (version, { cache, idioma = 'es_mx' }) {
  const mc = carpetaMinecraft()
  const jar = path.join(mc, 'versions', version, `${version}.jar`)
  const json = path.join(mc, 'versions', version, `${version}.json`)
  if (!fs.existsSync(jar) || !fs.existsSync(json)) {
    throw new Error(`Las capturas usan la fuente y el HUD de tu Minecraft Java ${version} instalado y no lo encontré en ` +
      `${jar}. Abre esa versión una vez con el lanzador (o define FABRICA_MINECRAFT con la carpeta .minecraft).`)
  }
  const dir = path.join(cache, `minecraft-${version}`)
  const listo = path.join(dir, 'listo.txt')
  if (fs.existsSync(listo)) return { dir, idioma: fs.readFileSync(listo, 'utf8').trim() }

  fs.rmSync(dir, { recursive: true, force: true })
  const zip = leerZip(jar)
  for (const n of zip.nombres()) {
    if (n.endsWith('/') || !PREFIJOS.some(p => n.startsWith(p))) continue
    const destino = path.join(dir, n)
    fs.mkdirSync(path.dirname(destino), { recursive: true })
    fs.writeFileSync(destino, zip.leer(n))
  }
  // Del índice de assets: la fuente unifont (caracteres fuera de los bitmap) y el idioma.
  const indice = JSON.parse(fs.readFileSync(json, 'utf8')).assetIndex?.id
  const objetos = JSON.parse(fs.readFileSync(path.join(mc, 'assets/indexes', `${indice}.json`), 'utf8')).objects
  const copiar = (clave, destino) => {
    const o = objetos[clave]
    if (!o) return false
    const origen = path.join(mc, 'assets/objects', o.hash.slice(0, 2), o.hash)
    if (!fs.existsSync(origen)) return false
    fs.mkdirSync(path.dirname(destino), { recursive: true })
    fs.copyFileSync(origen, destino)
    return true
  }
  copiar('minecraft/font/include/unifont.json', path.join(dir, 'assets/minecraft/font/include/unifont.json'))
  copiar('minecraft/font/unifont.zip', path.join(dir, 'unifont.zip'))
  const elegido = copiar(`minecraft/lang/${idioma}.json`, path.join(dir, 'assets/minecraft/lang', `${idioma}.json`)) ? idioma : 'en_us'
  fs.writeFileSync(listo, elegido)
  return { dir, idioma: elegido }
}
