// Plugins de terceros para el servidor de prueba (spec 012): las integraciones opcionales de OmniEvents (Vault con
// EssentialsX como economía, WorldEdit y WorldGuard, Citizens). Aprobados por el dueño (CLARIFY 012): se bajan solo de
// su release oficial, a tests-e2e/.cache/plugins/ (fuera del repositorio), con el sha256 fijado aquí, y se copian a
// run*/plugins/ según la versión de Paper. Nada de esto se distribuye con el plugin.
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

const VAULT = { archivo: 'Vault.jar', url: 'https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar', sha256: 'a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d' }
// WorldEdit 7.4 está compilado para Java 25 y Citizens solo soporta el último parche de cada versión: para 1.21.4
// (Java 24) van WorldEdit 7.3.19 y Citizens 2.0.37-b3760, el último build con 1.21.4 (aprobados por el dueño).
const WORLDEDIT = { archivo: 'worldedit-bukkit-7.4.6.jar', url: 'https://cdn.modrinth.com/data/1u6JkXh5/versions/YFnqiUlL/worldedit-bukkit-7.4.6.jar', sha256: 'e0ce67d870ad1fef8a40dd9d7926360caf3bb44722438545d6b20b55f82a0c91' }
// Spec 013: Geyser y Floodgate para los formularios de Bedrock (aprobados por el dueño, CLARIFY 013). Geyser sigue a una
// sola versión de Java: 2.6.2 es la última para 1.21.4 y 2.10.1 la de 26.1.
const FLOODGATE = { archivo: 'floodgate-spigot-2.2.5-b141.jar', url: 'https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/141/downloads/spigot', sha256: '21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e' }
const CITIZENS = { archivo: 'Citizens-2.0.44-b4268.jar', url: 'https://ci.citizensnpcs.co/job/Citizens2/4268/artifact/dist/target/Citizens-2.0.44-b4268.jar', sha256: '908e5c548aebe22cc2d8ca88e0068ccc781ce90509a28758ab6f8dc450256d67' }
// Spec 016 (ADR 0004): ProtocolLib para player_receive_chat (aprobado por el dueño, CLARIFY 016). 5.4.0 es el último
// release de GitHub (1.21.4); para 26.1 no hay release: la build de desarrollo #765 del Jenkins oficial
// (5.5.0-SNAPSHOT), en su variante con plugin.yml (la otra es un plugin de Paper con api-version 26.2).
const PROTOCOLLIB_121 = { archivo: 'ProtocolLib-5.4.0.jar', url: 'https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar', sha256: 'ee2e7ab9b5386f2d103081c4d108e61b1035df2ca692b53d6e2409fb1f5caccf' }
const PROTOCOLLIB_26 = { archivo: 'ProtocolLib-5.5.0-b765.jar', url: 'https://ci.dmulloy2.net/job/ProtocolLib/765/artifact/build/libs/ProtocolLib-Spigot.jar', sha256: 'fae0b1673162ea8d80506210154f579a6b966c293a0ca86c70232a03f815b9c3' }

/** Jars por versión de Paper (la de la suite si no se pide otra). */
export const PLUGINS_EXTRA = {
  '1.21.4': [
    VAULT,
    { archivo: 'EssentialsX-2.21.0.jar', url: 'https://cdn.modrinth.com/data/hXiIvTyT/versions/SKQwLLoQ/EssentialsX-2.21.0.jar', sha256: '5704322a54836b984b110f7e2208baed1886c2efed4446b497e67e512fec90c5' },
    { archivo: 'worldedit-bukkit-7.3.19.jar', url: 'https://cdn.modrinth.com/data/1u6JkXh5/versions/2YDdVDmG/worldedit-bukkit-7.3.19.jar', sha256: '639f7df29a4ec739885f19a101f497318ddc8e3da20473939908dff834e540df' },
    { archivo: 'worldguard-bukkit-7.0.13-dist.jar', url: 'https://cdn.modrinth.com/data/DKY9btbd/versions/f9NoeotB/worldguard-bukkit-7.0.13-dist.jar', sha256: '73b43a46aefc76514b90994532ea18bfd6d92e7bc5dcabbef9049a71778b21b5' },
    { archivo: 'Citizens-2.0.37-b3760.jar', url: 'https://ci.citizensnpcs.co/job/Citizens2/3760/artifact/dist/target/Citizens-2.0.37-b3760.jar', sha256: '28221e8fe7c2fe13b25f8644d659695351ab017c028617d39f62c7b42a90a6a5' },
    { archivo: 'Geyser-Spigot-2.6.2-b796.jar', url: 'https://download.geysermc.org/v2/projects/geyser/versions/2.6.2/builds/796/downloads/spigot', sha256: 'fd463346c2f859ae4c7cb0f2a670a92feb3780efb8f15186b17aedab5f39801e' },
    FLOODGATE,
    PROTOCOLLIB_121
  ],
  '26.1.2': [
    VAULT,
    { archivo: 'EssentialsX-2.22.0.jar', url: 'https://cdn.modrinth.com/data/hXiIvTyT/versions/nY6VN1XH/EssentialsX-2.22.0.jar', sha256: 'bda4685105977fca2e209820a9f0ad24275bd103390a03236f38e59bfdac58e6' },
    WORLDEDIT,
    { archivo: 'worldguard-bukkit-7.0.18.jar', url: 'https://cdn.modrinth.com/data/DKY9btbd/versions/btHBavWa/worldguard-bukkit-7.0.18.jar', sha256: '08f3ef58bc521c635d8c78aedaca96f151d2d397e7cd8018584955dd7468eb05' },
    CITIZENS,
    { archivo: 'Geyser-Spigot-2.10.1-b1184.jar', url: 'https://download.geysermc.org/v2/projects/geyser/versions/2.10.1/builds/1184/downloads/spigot', sha256: 'c3bf077b7ea642108fa3bf58e96ec46b876a18d58fc1235b15af4263195620fd' },
    FLOODGATE,
    PROTOCOLLIB_26
  ]
}

/** Nombres de los jars de todas las versiones: al instalar se quitan los de otra versión que hubiera en plugins/. */
const TODOS = new Set(Object.values(PLUGINS_EXTRA).flat().map(p => p.archivo))

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex')

/** Baja (si falta en la caché) y verifica un jar. */
async function enCache (p, cache) {
  const destino = path.join(cache, p.archivo)
  if (fs.existsSync(destino) && sha256(fs.readFileSync(destino)) === p.sha256) return destino
  console.log(`» Bajando ${p.archivo} (${p.url})…`)
  const r = await fetch(p.url, { headers: { 'User-Agent': 'fabrica-de-plugins-e2e' } })
  if (!r.ok) throw new Error(`No se pudo bajar ${p.archivo}: HTTP ${r.status}`)
  const bytes = Buffer.from(await r.arrayBuffer())
  const sha = sha256(bytes)
  if (sha !== p.sha256) throw new Error(`sha256 de ${p.archivo} no coincide: ${sha} ≠ ${p.sha256}`)
  fs.mkdirSync(cache, { recursive: true })
  fs.writeFileSync(destino, bytes)
  return destino
}

/** Deja en pluginsDir los jars de esa versión de Paper (y quita los de otras versiones). Devuelve sus nombres. */
export async function instalarPluginsExtra (version, pluginsDir, cache) {
  const lista = PLUGINS_EXTRA[version]
  if (!lista) {
    console.log(`  (sin plugins de terceros para Paper ${version}: las integraciones no se prueban)`)
    return []
  }
  const quiero = new Set(lista.map(p => p.archivo))
  for (const f of fs.readdirSync(pluginsDir)) if (TODOS.has(f) && !quiero.has(f)) fs.rmSync(path.join(pluginsDir, f))
  for (const p of lista) {
    const origen = await enCache(p, cache)
    const destino = path.join(pluginsDir, p.archivo)
    if (!fs.existsSync(destino) || sha256(fs.readFileSync(destino)) !== p.sha256) fs.copyFileSync(origen, destino)
  }
  return lista.map(p => p.archivo)
}

/**
 * EssentialsX reemplaza comandos de Minecraft que la suite usa por consola (list, give, tp…) y cambia su salida. En el
 * servidor de prueba se le desactivan (disabled-commands) para que los atienda Minecraft como sin EssentialsX.
 */
export function configurarEssentials (pluginsDir, comandos) {
  const archivo = path.join(pluginsDir, 'Essentials', 'config.yml')
  const linea = `disabled-commands: [${comandos.join(', ')}]`
  if (!fs.existsSync(archivo)) {
    fs.mkdirSync(path.dirname(archivo), { recursive: true })
    fs.writeFileSync(archivo, linea + '\n')
    return
  }
  const texto = fs.readFileSync(archivo, 'utf8')
  fs.writeFileSync(archivo, texto.replace(/^disabled-commands:.*$/m, linea))
}

/**
 * Pone una clave de un YAML por su ruta (bedrock.port) sin tocar lo demás (comentarios incluidos): reemplaza la línea
 * si está o la agrega bajo su sección. Devuelve el texto nuevo (null si no existe la sección).
 */
export function ponerClave (texto, ruta, valor) {
  const partes = ruta.split('.')
  const lineas = texto.split(/\r?\n/)
  const pila = []
  let padre = partes.length === 1 ? -1 : null
  for (let i = 0; i < lineas.length; i++) {
    const m = /^( *)([A-Za-z0-9_\-]+):(.*)$/.exec(lineas[i])
    if (!m) continue
    const sangria = m[1].length
    while (pila.length && pila[pila.length - 1].sangria >= sangria) pila.pop()
    pila.push({ sangria, clave: m[2] })
    const actual = pila.map(p => p.clave).join('.')
    if (actual === ruta) {
      lineas[i] = `${m[1]}${m[2]}: ${valor}`
      return lineas.join('\n')
    }
    if (actual === partes.slice(0, -1).join('.')) padre = i
  }
  if (padre == null) return null
  const sangria = padre < 0 ? '' : ' '.repeat(/^( *)/.exec(lineas[padre])[1].length + 2)
  lineas.splice(padre + 1, 0, `${sangria}${partes[partes.length - 1]}: ${valor}`)
  return lineas.join('\n')
}

/**
 * Spec 013: Geyser escucha Bedrock solo en 127.0.0.1 y en el puerto de la suite, y acepta logins sin firmar (el bot no
 * tiene cuenta de Xbox): enable-proxy-connections en Geyser 2.6 y advanced.bedrock.validate-bedrock-login en 2.10.
 * Sin avisos de actualización ni métricas (bStats lo comparten todos los plugins: constitución II.4 también en el
 * servidor de prueba). Devuelve false si Geyser todavía no generó su configuración (primer arranque).
 */
export function configurarGeyser (pluginsDir, puertoBedrock) {
  const bstats = path.join(pluginsDir, 'bStats', 'config.yml')
  fs.mkdirSync(path.dirname(bstats), { recursive: true })
  const b = fs.existsSync(bstats) ? fs.readFileSync(bstats, 'utf8') : ''
  fs.writeFileSync(bstats, ponerClave(b, 'enabled', 'false'))
  const archivo = path.join(pluginsDir, 'Geyser-Spigot', 'config.yml')
  if (!fs.existsSync(archivo)) return false
  let t = fs.readFileSync(archivo, 'utf8')
  const nuevo = /^advanced:/m.test(t)
  const claves = {
    'bedrock.address': '127.0.0.1',
    'bedrock.port': String(puertoBedrock),
    'notify-on-new-bedrock-update': 'false',
    ...(nuevo
      ? { 'advanced.bedrock.validate-bedrock-login': 'false' }
      : { 'enable-proxy-connections': 'true', 'metrics.enabled': 'false' })
  }
  for (const [ruta, valor] of Object.entries(claves)) t = ponerClave(t, ruta, valor) ?? t
  fs.writeFileSync(archivo, t)
  return true
}
