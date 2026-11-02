// Escenas de la página de presentación de Worldcraft (spec 038): lo que hace el plugin en un mundo generado con semilla
// fija, visto por un bot espectador con prismarine-viewer, sin HUD y a 1280×720. Servidor propio en
// plugins/worldcraft/run-capturas/ (el run/ de la suite no se toca), mundo nuevo en cada corrida. Las obras van desde la
// consola (at=…, --at x,y,z, --now); la imagen la pide el bot (/wc image solo funciona en el juego).
// Uso: npm --prefix tests-e2e run capturas -- --plugin worldcraft --escenas presentacion [--sin-build] [--solo "terrain"]
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { dormir } from '../lib/servidor.mjs'

const require = createRequire(import.meta.url)
const SALIDA = 'docs/img/worldcraft/presentacion/escenas'
const RAIZ = fileURLToPath(new URL('../../', import.meta.url))
const LOGO = path.join(RAIZ, 'docs/img/worldcraft/presentacion/logo.png')
const CAM = 'Cam'
const ANCHO = 1280
const ALTO = 720
// La misma semilla que las escenas de SkinStatues (024): un llano junto a un río, colinas y bosque al norte.
const SEMILLA = '8678942899319966093'

const bot = (ctx) => ctx.bots[CAM]
const consola = async (ctx, ...cmds) => {
  for (const c of cmds) await ctx.srv.consola(c)
  await ctx.srv.sincronizar()
}

/** Una obra desde la consola: espera su «Done» y devuelve el número del historial (para deshacerla al final). */
async function obra (ctx, cmd, { timeoutMs = 300000 } = {}) {
  const m = ctx.srv.marca()
  await ctx.srv.consola(cmd)
  const FIN = /Done: .*#(\d+)|Unknown|Usage|refused|too big|not allowed|did you mean|Nothing|cannot/i
  let l = await ctx.srv.esperarLog(new RegExp(FIN.source + '|/wc confirm to go ahead', 'i'), { desde: m, timeoutMs })
  // Sobre confirm.above_blocks (50 000) también la consola confirma, aunque vaya con --now.
  if (/confirm to go ahead/i.test(l)) {
    const c = ctx.srv.marca()
    await ctx.srv.consola('wc confirm')
    l = await ctx.srv.esperarLog(FIN, { desde: c, timeoutMs })
  }
  const id = l.match(/#(\d+)/)?.[1]
  if (!/Done:/.test(l) || !id) throw new Error(`${cmd}: ${l}`)
  ;(ctx.obras ??= []).push(id)
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

/** El primer bloque de aire sobre el suelo en x, z (bajo los árboles), según el bot. */
function suelo (ctx, x, z) {
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
    try { suelo(ctx, x, z); return true } catch { return false }
  }), { timeoutMs: 60000 })
}

/**
 * La cámara se va lejos mientras se construye y vuelve después: así el visor arma los chunks ya terminados. Si mira
 * la obra mientras ocurre, una obra grande le llega como miles de cambios sueltos y le quedan secciones a medio
 * actualizar (la montaña salía cortada en tajadas rectas y la isla no aparecía).
 */
async function lejos (ctx) {
  await camara(ctx, 4000.5, 150, 4000.5, 0, 0)
}

/** Deshace las obras de la escena (la consola deshace las suyas, de la última a la primera). */
async function deshacer (ctx) {
  for (const _ of ctx.obras ?? []) {
    const m = ctx.srv.marca()
    await ctx.srv.consola('wc undo')
    await ctx.srv.esperarLog(/Undone #|has nothing to undo/, { desde: m, timeoutMs: 180000 }).catch(() => {})
  }
  ctx.obras = []
}

// El llano junto al río, con las colinas y el bosque al norte; la cámara al sur, mirando al norte.
const escenas = [
  {
    id: 'terrain', timeoutMs: 900000,
    texto: 'A mountain with snowy peaks and a floating island with trees, each made with one command, over a generated plain.',
    async fn (ctx) {
      const [mx, mz] = [-30, -70]
      const [ix, iz] = [42, -24]
      await mirar(ctx, [5, 100, 70], [5, 80, -40])
      await chunks(ctx, [[mx, mz], [ix, iz]])
      const m = suelo(ctx, mx, mz)
      const i = suelo(ctx, ix, iz)
      await lejos(ctx)
      await obra(ctx, `wc mountain large alpine at=${mx},${m},${mz} world=world seed=38 --now`)
      await obra(ctx, `wc island large at=${ix},${i + 26},${iz} world=world seed=7 --now`)
      await mirar(ctx, [8, m + 40, 66], [4, m + 34, -45])
      await foto(ctx, { espera: 30000 })
    }
  },
  {
    id: 'shapes', timeoutMs: 600000,
    texto: 'Shapes made with one command each: a sphere, a hollow cylinder, a pyramid and a cone, on the grass.',
    async fn (ctx) {
      const z = -44
      const xs = [-36, -12, 12, 36]
      await mirar(ctx, [0, 85, 30], [0, 75, z])
      await chunks(ctx, xs.map(x => [x, z]))
      const ys = xs.map(x => suelo(ctx, x, z))
      await lejos(ctx)
      await obra(ctx, `wc sphere orange_concrete 9 --at ${xs[0]},${ys[0] + 9},${z} --world world`)
      await obra(ctx, `wc hcyl 70% light_blue_concrete, 30% cyan_concrete 8 16 --at ${xs[1]},${ys[1]},${z} --world world`)
      await obra(ctx, `wc pyramid sandstone 10 --at ${xs[2]},${ys[2]},${z} --world world`)
      await obra(ctx, `wc cone white_concrete 8 18 --at ${xs[3]},${ys[3]},${z} --world world`)
      const top = Math.max(...ys)
      await mirar(ctx, [0, top + 15, z + 40], [0, top + 6, z])
      await chunks(ctx, xs.map(x => [x, z]))
      await foto(ctx, { espera: 35000 })
    }
  },
  {
    id: 'image', timeoutMs: 600000,
    texto: 'The Worldcraft logo built with blocks by /wc image, standing in front of the player like a wall.',
    async fn (ctx) {
      const [x, z] = [0, -30]
      await mirar(ctx, [x, 85, z + 30], [x, 75, z])
      await chunks(ctx, [[x, z], [x, z - 2]])
      const y = suelo(ctx, x, z)
      // El bot de pie en el suelo, mirando al norte: la imagen se para 2 bloques delante, de 48 de alto.
      await camara(ctx, x + 0.5, y, z + 0.5, 180, 0)
      const m = ctx.srv.marca()
      bot(ctx).comando('wc image worldcraft-logo.png 48')
      const l = await ctx.srv.esperarLog(/Done: .*#(\d+)|refused|Unknown|cannot/i, { desde: m, timeoutMs: 120000 }).catch(() => null)
      if (!l || !/Done:/.test(l)) {
        // El «Done» del jugador va a su chat, no al log: se mira el bloque del centro de la imagen.
        await bot(ctx).hasta('ver la imagen', b => {
          const V = b.entity.position.constructor
          for (let dy = 4; dy < 44; dy++) if (b.blockAt(new V(x, y + dy, z - 2))?.name !== 'air') return true
          return false
        }, { timeoutMs: 120000 })
        await dormir(5000)
      }
      await lejos(ctx)
      await mirar(ctx, [x + 14, y + 22, z + 44], [x, y + 22, z - 2])
      await foto(ctx, { espera: 25000 })
      // Lo del jugador lo deshace el jugador.
      bot(ctx).comando('wc undo')
      await dormir(5000)
    }
  }
]

export default {
  jar: /^Worldcraft-.*\.jar$/,
  version: '1.21.4',
  carpetaServidor: 'run-capturas',
  puerto: 25597,
  puertoVisor: 3027,
  memoria: '3G',
  // 12 chunks: lo que pide mineflayer («far»); la montaña a ~150 bloques y las colinas detrás entran.
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
  // La configuración tal como está en el código fuente.
  copiar: [{ de: 'plugins/worldcraft/src/main/resources', a: 'plugins/Worldcraft' }],
  preparar: [
    'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
    'gamerule sendCommandFeedback false', 'gamerule announceAdvancements false', 'gamerule doTileDrops false',
    'time set 6000', 'weather clear', 'difficulty peaceful', 'kill @e[type=!player]', `op ${CAM}`
  ],

  /** run-capturas/: el jar de Paper y el EULA del servidor de prueba de Worldcraft, y un mundo nuevo de la semilla. */
  async prepararCarpeta ({ run, base }) {
    if (path.basename(run) !== 'run-capturas') throw new Error(`carpeta inesperada: ${run}`)
    fs.mkdirSync(run, { recursive: true })
    const origen = path.join(base, 'run') // solo se lee: el jar de Paper y el EULA del dueño
    if (!fs.readdirSync(run).some(f => /^paper-1\.21\.4-.*\.jar$/.test(f))) {
      const jar = fs.readdirSync(origen).find(f => /^paper-1\.21\.4-.*\.jar$/.test(f))
      if (!jar) throw new Error(`No hay paper-1.21.4-*.jar en ${origen} para copiar a ${run}`)
      fs.copyFileSync(path.join(origen, jar), path.join(run, jar))
    }
    if (!fs.existsSync(path.join(run, 'eula.txt')) && fs.existsSync(path.join(origen, 'eula.txt'))) {
      fs.copyFileSync(path.join(origen, 'eula.txt'), path.join(run, 'eula.txt'))
    }
    for (const w of ['world', 'world_nether', 'world_the_end']) fs.rmSync(path.join(run, w), { recursive: true, force: true })
    // El historial de la consola de la corrida anterior hablaría de un mundo que ya no existe.
    fs.rmSync(path.join(run, 'plugins', 'Worldcraft', 'history'), { recursive: true, force: true })
  },

  /** El logo del póster en images/ (para la escena image): sale de presentacion.mjs --plugin worldcraft. */
  async antesDeArrancar ({ run }) {
    const imagenes = path.join(run, 'plugins', 'Worldcraft', 'images')
    fs.mkdirSync(imagenes, { recursive: true })
    if (fs.existsSync(LOGO)) fs.copyFileSync(LOGO, path.join(imagenes, 'worldcraft-logo.png'))
    else console.log(`  · Sin ${path.relative(RAIZ, LOGO)}: corre antes npm run presentacion -- --plugin worldcraft`)
    return {}
  },

  async antes (ctx) {
    await ctx.entrar(CAM)
    bot(ctx).bot.physicsEnabled = false // espectador quieto donde lo pone tp
    await consola(ctx, `gamemode spectator ${CAM}`)
  },

  async despuesDeCada (ctx) {
    await deshacer(ctx)
    await ctx.srv.sincronizar()
  },

  escenas
}
