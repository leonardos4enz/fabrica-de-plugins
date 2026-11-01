// Escenas de la página de presentación de SkinStatues (spec 024): estatuas de youtubers en un mundo generado con semilla
// fija, vistas por un bot espectador con prismarine-viewer, sin HUD y a 1280×720. Servidor propio en
// plugins/skinstatues/run-capturas/ (el run/ del dueño no se toca), mundo nuevo en cada corrida.
// Uso: npm --prefix tests-e2e run capturas -- --plugin skinstatues --escenas presentacion [--sin-build] [--solo "giant"]
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { dormir } from '../lib/servidor.mjs'
import { httpsLocal } from '../lib/https-local.mjs'
import { guardar } from '../lib/capturas/png.mjs'
import { skinsYoutubers, CACHE } from './skins-youtubers.mjs'

const require = createRequire(import.meta.url)
const SALIDA = 'docs/img/skinstatues/presentacion/escenas'
const RAIZ = fileURLToPath(new URL('../../', import.meta.url))
const CAM = 'Cam'
const ANCHO = 1280
const ALTO = 720
const SEMILLA = '8678942899319966093'

let web = null
let carpeta = null

const bot = (ctx) => ctx.bots[CAM]
const consola = async (ctx, ...cmds) => {
  for (const c of cmds) await ctx.srv.consola(c)
  await ctx.srv.sincronizar()
}

/** config.yml del servidor de capturas: reemplaza `clave: valor` (la primera que aparezca) y recarga. */
async function config (ctx, cambios) {
  const archivo = path.join(carpeta, 'plugins', 'SkinStatues', 'config.yml')
  let texto = fs.readFileSync(archivo, 'utf8')
  for (const [k, v] of Object.entries(cambios)) texto = texto.replace(new RegExp(`^(\\s*)${k}: .*$`, 'm'), (m, s) => `${s}${k}: ${v}`)
  fs.writeFileSync(archivo, texto)
  if (ctx) await ctx.srv.consola('statue reload', { espera: /Reloaded: \d+ poses/ })
}

/** Construye desde la consola y espera a que termine. Devuelve el id; `alPlan(id)` en cuanto se conoce. */
async function construir (ctx, cmd, { timeoutMs = 300000, alPlan } = {}) {
  const m = ctx.srv.marca()
  await ctx.srv.consola(`statue build ${cmd}`)
  // Por el nombre: con varias a la vez, el plan de otra estatua puede llegar después de esta marca.
  const primero = cmd.split(' ')[0]
  const nombre = primero.startsWith('https://') ? 'url:\\S+' : primero.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const plan = await ctx.srv.esperarLog(new RegExp(`Statue #(\\d+) of ${nombre}: \\d+ blocks|No skin for|cannot be used|Could not|` +
    'does not fit|would have|not allowed|There is no|links are off|Unknown|Usage', 'i'), { desde: m, timeoutMs: 60000 })
  const id = plan.match(/Statue #(\d+) of/)?.[1]
  if (!id) throw new Error(`statue build ${cmd}: ${plan}`)
  alPlan?.(id)
  await ctx.srv.esperarLog(new RegExp(`Statue #${id} of \\S+ is finished`), { desde: m, timeoutMs })
  return id
}

/** Pone la cámara (el bot espectador) en un punto, mirando con yaw/pitch de Minecraft (yaw 0 = sur, 180 = norte). */
async function camara (ctx, x, y, z, yaw, pitch) {
  // Con decimales: tp centra en el bloque las coordenadas enteras (0 → 0.5).
  await consola(ctx, `tp ${CAM} ${x.toFixed(2)} ${y.toFixed(2)} ${z.toFixed(2)} ${yaw} ${pitch}`)
  await bot(ctx).hasta('estar en su punto', b => Math.abs(b.entity.position.x - x) < 0.3 && Math.abs(b.entity.position.z - z) < 0.3 &&
    Math.abs(b.entity.position.y - y) < 0.3, { timeoutMs: 20000 })
}

/** Cámara en `desde` mirando hacia `hacia` ([x, y, z]). */
async function mirar (ctx, [x, y, z], [hx, hy, hz]) {
  const dx = hx - x
  const dz = hz - z
  const yaw = Math.atan2(-dx, dz) * 180 / Math.PI
  const pitch = -Math.atan2(hy - y, Math.hypot(dx, dz)) * 180 / Math.PI
  await camara(ctx, x, y, z, Math.round(yaw * 10) / 10, Math.round(pitch * 10) / 10)
}

/** Foto de lo que ve la cámara, sin HUD, a 1280×720 (la deja como última foto de la escena, para ctx.cuadro()). */
async function foto (ctx, { espera = 4000 } = {}) {
  await dormir(espera)
  const { createCanvas, loadImage } = require('canvas')
  const img = await loadImage(await ctx.camara(CAM).foto())
  const lienzo = createCanvas(img.width, img.height)
  lienzo.getContext('2d').drawImage(img, 0, 0)
  ctx.ultimaFoto = { lienzo, alt: ctx.escena.texto }
  return ctx.ultimaFoto
}

const NO_SOSTIENE = /^(air|cave_air|short_grass|tall_grass|fern|large_fern|vine|snow)$|leaves|_log$|flower|bush|poppy|dandelion|tulip|daisy|bluet|orchid|allium|cornflower|lily|petals|mushroom|sugar_cane|bamboo/

/** Bloque donde van los pies de una estatua en x, z: sobre el primer bloque que sostiene (bajo los árboles), según el bot. */
function pies (ctx, x, z) {
  const b = bot(ctx).bot
  const V = b.entity.position.constructor
  for (let y = 200; y > -60; y--) {
    const n = b.blockAt(new V(x, y, z))?.name
    if (n == null) throw new Error(`chunk sin cargar en ${x}, ${z}`)
    if (!NO_SOSTIENE.test(n)) return y + 1
  }
  throw new Error(`sin suelo en ${x}, ${z}`)
}

/** Espera a que el bot tenga los chunks de esos puntos [x, z]. */
async function chunks (ctx, puntos) {
  await bot(ctx).hasta('recibir los chunks', () => puntos.every(([x, z]) => {
    try { pies(ctx, x, z); return true } catch { return false }
  }), { timeoutMs: 60000 })
}

/** Quita las estatuas de la escena (devuelve el terreno) para que no tapen a la siguiente. */
async function quitar (ctx) {
  for (const id of ctx.estatuas ?? []) {
    const m = ctx.srv.marca()
    await ctx.srv.consola(`statue remove ${id}`)
    await ctx.srv.esperarLog(new RegExp(`Statue #${id} removed`), { desde: m, timeoutMs: 180000 }).catch(() => {})
  }
  ctx.estatuas = []
}

const anotar = (ctx) => (id) => { (ctx.estatuas ??= []).push(id) }

// Los sitios: el llano junto al río al pie de las colinas, con el bosque de cerezos detrás (al norte). Las estatuas
// miran al sur (+z) y la cámara está al sur, mirando al norte.
const escenas = [
  {
    id: 'giant', timeoutMs: 900000,
    texto: 'Two statues 100 blocks tall (Technoblade and ElRichMC) towering over generated hills, a river and a forest.',
    async fn (ctx) {
      const cam = [2, 84, 66]
      await mirar(ctx, cam, [2, 110, -50])
      await chunks(ctx, [[20, -48], [-48, -66]])
      const t = pies(ctx, 20, -48)
      const r = pies(ctx, -48, -66)
      await Promise.all([
        construir(ctx, `Technoblade 100 victory --at 20 ${t} -48 --world world --facing south`, { alPlan: anotar(ctx) }),
        construir(ctx, `ElRichMC 100 hands_on_hips --at -48 ${r} -66 --world world --facing south`, { alPlan: anotar(ctx) })
      ])
      await mirar(ctx, cam, [-6, Math.max(t, r) + 48, -55])
      await foto(ctx, { espera: 15000 })
    }
  },
  {
    id: 'row', timeoutMs: 900000,
    texto: 'Six statues 44 blocks tall in a row, each a different player and pose: Vegetta777, Willyrex, elrubius, Mikecrack, Dream and GeorgeNotFound.',
    async fn (ctx) {
      const fila = [['Vegetta777', 'waving'], ['Willyrex', 'victory'], ['elrubius', 'dabbing'], ['Mikecrack', 'flexing'],
        ['Dream', 'arms_crossed'], ['GeorgeNotFound', 'pointing']]
      const z = -44
      const xs = fila.map((_, i) => -80 + i * 32)
      await mirar(ctx, [0, 80, 44], [0, 85, z])
      await chunks(ctx, xs.map(x => [x, z]))
      const ys = xs.map(x => pies(ctx, x, z))
      await Promise.all(fila.map(([n, pose], i) =>
        construir(ctx, `${n} 44 ${pose} --at ${xs[i]} ${ys[i]} ${z} --world world --facing south`, { alPlan: anotar(ctx) })))
      const medio = Math.round(ys.reduce((a, b) => a + b) / ys.length)
      await mirar(ctx, [0, medio + 16, 44], [0, medio + 22, z])
      await foto(ctx, { espera: 12000 })
    }
  },
  {
    id: 'build', timeoutMs: 900000,
    texto: 'ElRichMC appearing layer by layer, 56 blocks tall, and a close-up of the second skin layer in relief.',
    async fn (ctx) {
      const [x, z] = [8, -44]
      // Sin cuadros de una corrida anterior (si esta da menos, quedarían sueltos).
      for (const f of fs.readdirSync(path.join(RAIZ, SALIDA))) if (/^build-\d+\.png$/.test(f)) fs.rmSync(path.join(RAIZ, SALIDA, f))
      await mirar(ctx, [x, 80, 26], [x, 85, z])
      await chunks(ctx, [[x, z]])
      const y = pies(ctx, x, z)
      const cam = [x, y + 22, z + 66]
      await mirar(ctx, cam, [x, y + 26, z])
      // El visor tarda en armar los 12 chunks de alrededor: con 8 s los primeros cuadros mostraban las colinas apareciendo.
      await foto(ctx, { espera: 25000 })
      const cuadros = [ctx.ultimaFoto.lienzo]
      // ~5000 bloques a 12 por tick: unos 20 s de obra, un cuadro cada ~2,5 s (la foto del visor tarda).
      await config(ctx, { blocks_per_tick: '12' })
      let listo = false
      const obra = construir(ctx, `ElRichMC 56 victory --at ${x} ${y} ${z} --world world --facing south`, { alPlan: anotar(ctx) })
        .finally(() => { listo = true })
      while (!listo) {
        await foto(ctx, { espera: 1500 })
        cuadros.push(ctx.ultimaFoto.lienzo)
      }
      await obra
      await config(ctx, { blocks_per_tick: '4000' })
      // Hasta 9 cuadros repartidos (el primero, sin estatua) y el final: build-1 … build-10.
      const n = Math.min(9, cuadros.length)
      for (let i = 0; i < n; i++) {
        ctx.ultimaFoto = { lienzo: cuadros[Math.round(i * (cuadros.length - 1) / Math.max(1, n - 1))], alt: ctx.escena.texto }
        await ctx.cuadro()
      }
      await foto(ctx, { espera: 6000 })
      await ctx.cuadro()
      const final = ctx.ultimaFoto
      // De cerca y un poco de costado (los brazos en alto no tapan), la cara y el pecho: la segunda capa (pelo, chaqueta) sobresale.
      await mirar(ctx, [x - 9, y + 45, z + 24], [x, y + 44, z])
      await foto(ctx, { espera: 8000 })
      await guardar(ctx.ultimaFoto.lienzo, path.join(RAIZ, SALIDA, 'build-close.png'))
      ctx.ultimaFoto = final
    }
  },
  {
    id: 'name-link', timeoutMs: 600000,
    texto: 'Two statues side by side: DanTDM built from the account name and LTeZla built from a link to the skin PNG.',
    async fn (ctx) {
      const z = -44
      await mirar(ctx, [0, 80, 20], [0, 80, z])
      await chunks(ctx, [[-14, z], [14, z]])
      const a = pies(ctx, -14, z)
      const b = pies(ctx, 14, z)
      await construir(ctx, `DanTDM 40 waving --at -14 ${a} ${z} --world world --facing south`, { alPlan: anotar(ctx) })
      await construir(ctx, `${web.base}/skins/LTeZla.png 40 pointing_left --at 14 ${b} ${z} --world world --facing south`, { alPlan: anotar(ctx) })
      const m = Math.max(a, b)
      await mirar(ctx, [0, m + 14, z + 52], [0, m + 19, z])
      await foto(ctx, { espera: 10000 })
    }
  },
  {
    id: 'palettes', timeoutMs: 600000,
    texto: 'Bobicraft three times with the wool, concrete and terracotta palettes.',
    async fn (ctx) {
      const z = -44
      const xs = [-24, 0, 24]
      await mirar(ctx, [0, 80, 20], [0, 80, z])
      await chunks(ctx, xs.map(x => [x, z]))
      const ys = xs.map(x => pies(ctx, x, z))
      const paletas = ['wool', 'concrete', 'terracotta']
      for (let i = 0; i < 3; i++) {
        await construir(ctx, `Bobicraft 36 standing --at ${xs[i]} ${ys[i]} ${z} --world world --facing south --palette ${paletas[i]}`,
          { alPlan: anotar(ctx) })
      }
      const m = Math.max(...ys)
      await mirar(ctx, [0, m + 13, z + 54], [0, m + 17, z])
      await foto(ctx, { espera: 10000 })
    }
  }
]

export default {
  jar: /^SkinStatues-.*\.jar$/,
  version: '1.21.4',
  carpetaServidor: 'run-capturas',
  puerto: 25597,
  puertoVisor: 3027,
  memoria: '3G',
  // 12 chunks: lo que pide mineflayer («far»); una estatua de 100 a 120 bloques y las colinas detrás entran.
  visor: { distancia: 12, ancho: ANCHO, alto: ALTO },
  propiedades: {
    'level-type': 'minecraft\\:normal', 'level-seed': SEMILLA, 'generate-structures': 'true',
    'view-distance': '12', 'simulation-distance': '4', 'spawn-monsters': 'false', 'spawn-animals': 'false',
    'difficulty': 'peaceful'
  },
  protagonista: CAM,
  bots: [CAM],
  datosDeJugador: [],
  salida: SALIDA,
  // La configuración tal como está en el código fuente; las skins se copian en antesDeArrancar.
  copiar: [{ de: 'plugins/skinstatues/src/main/resources', a: 'plugins/SkinStatues' }],
  preparar: [
    'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
    'gamerule sendCommandFeedback false', 'gamerule announceAdvancements false', 'gamerule doTileDrops false',
    'time set 6000', 'weather clear', 'difficulty peaceful', 'kill @e[type=!player]'
  ],

  /** run-capturas/: el jar de Paper y el EULA del servidor de prueba de SkinStatues, y un mundo nuevo de la semilla. */
  async prepararCarpeta ({ run, base }) {
    if (path.basename(run) !== 'run-capturas') throw new Error(`carpeta inesperada: ${run}`)
    carpeta = run
    fs.mkdirSync(run, { recursive: true })
    const origen = path.join(base, 'run') // solo se lee: el jar de Paper y el EULA del dueño
    if (!fs.readdirSync(run).some(f => /^paper-1\.21\.4-.*\.jar$/.test(f))) {
      const jar = fs.readdirSync(origen).find(f => /^paper-1\.21\.4-.*\.jar$/.test(f))
      if (!jar) throw new Error(`No hay paper-1.21.4-*.jar en ${origen} para copiar a ${run}`)
      fs.copyFileSync(path.join(origen, jar), path.join(run, jar))
    }
    // El EULA lo aceptó el dueño para el servidor de prueba de SkinStatues (run/eula.txt): se reusa esa aceptación.
    if (!fs.existsSync(path.join(run, 'eula.txt')) && fs.existsSync(path.join(origen, 'eula.txt'))) {
      fs.copyFileSync(path.join(origen, 'eula.txt'), path.join(run, 'eula.txt'))
    }
    for (const w of ['world', 'world_nether', 'world_the_end']) fs.rmSync(path.join(run, w), { recursive: true, force: true })
  },

  /** Skins (de la caché; si falta, se bajan una vez), config de capturas y el HTTPS local que sirve la skin del link. */
  async antesDeArrancar ({ run, java }) {
    const { listas, faltan } = await skinsYoutubers()
    if (faltan.length) console.log(`  · Sin skin: ${faltan.join('; ')}`)
    const skins = path.join(run, 'plugins', 'SkinStatues', 'skins')
    fs.mkdirSync(skins, { recursive: true })
    for (const f of fs.readdirSync(CACHE)) if (listas.some(n => f.startsWith(n))) fs.copyFileSync(path.join(CACHE, f), path.join(skins, f))
    await config(null, { urls: 'true', max_blocks: '2000000' })
    web = await httpsLocal({
      carpeta: path.join(run, 'e2e-https'),
      java,
      rutas: {
        '/skins/LTeZla.png': (req, res) => {
          res.writeHead(200, { 'Content-Type': 'image/png' })
          res.end(fs.readFileSync(path.join(CACHE, 'LTeZla.png')))
        }
      }
    })
    return { jvm: web.jvm }
  },

  async alTerminar () {
    await web?.cerrar()
    web = null
  },

  async antes (ctx) {
    await ctx.entrar(CAM)
    bot(ctx).bot.physicsEnabled = false // espectador quieto donde lo pone tp
    await consola(ctx, `gamemode spectator ${CAM}`)
  },

  async despuesDeCada (ctx) {
    await quitar(ctx)
    await ctx.srv.sincronizar()
  },

  escenas
}
