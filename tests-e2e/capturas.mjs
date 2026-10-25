// Capturas en el juego (spec 004): compila el plugin, arranca su Paper de prueba, hace que los bots vivan cada
// capacidad y guarda una imagen por capacidad (render de lo que recibió el bot) en docs/img/<plugin>/, con la galería
// generada. Uso:
//   npm --prefix tests-e2e run capturas [-- --plugin omnievents] [--sin-build] [--solo "accion-title"]
//   npm --prefix tests-e2e run capturas -- --solo-galeria      (solo reescribe la galería, sin servidor)
//   --cobertura: falla si una capacidad del servidor vivo no tiene escena (por defecto es solo un aviso)
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { Servidor, dormir } from './lib/servidor.mjs'
import { conectar } from './lib/bot.mjs'
import { prepararAssets } from './lib/capturas/assets.mjs'
import { cargarFuente } from './lib/capturas/fuente.mjs'
import { Hud, recortar, plano, archivoDeEfecto } from './lib/capturas/hud.mjs'
import { Camara, abrirNavegador } from './lib/capturas/visor.mjs'
import { guardar } from './lib/capturas/png.mjs'
import { escribirGaleria } from './lib/capturas/galeria.mjs'

const require = createRequire(import.meta.url)
const AQUI = path.dirname(fileURLToPath(import.meta.url))
const RAIZ = path.resolve(AQUI, '..')

function opciones (argv) {
  const o = { plugin: 'omnievents', build: true, solo: null, soloGaleria: false, cobertura: false }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--plugin') o.plugin = argv[++i]
    else if (a === '--sin-build') o.build = false
    else if (a === '--solo-galeria') o.soloGaleria = true
    else if (a === '--cobertura') o.cobertura = true
    else if (a === '--solo') o.solo = new RegExp(argv[++i], 'i')
    else throw new Error(`Opción desconocida: ${a}`)
  }
  return o
}

/** Gradle 8.14 no corre sobre Java 25 (CLAUDE.md): se prefiere el JDK 24 aunque JAVA_HOME apunte a otro. */
function javaHome () {
  const jdk24 = 'C:\\Program Files\\Java\\jdk-24'
  return process.env.FABRICA_JAVA_HOME || (fs.existsSync(jdk24) ? jdk24 : process.env.JAVA_HOME)
}

function compilar (plugin) {
  console.log(`» Compilando ${plugin} (gradlew :${plugin}:dist)…`)
  const win = process.platform === 'win32'
  const gradlew = path.join(RAIZ, win ? 'gradlew.bat' : 'gradlew')
  const r = spawnSync(win ? `"${gradlew}"` : gradlew, [`:${plugin}:dist`, '-q'], {
    cwd: RAIZ, stdio: 'inherit', shell: win, env: { ...process.env, JAVA_HOME: javaHome() }
  })
  if (r.status !== 0) throw new Error('La compilación falló')
}

/** Estado del jugador que dibuja el HUD, leído del bot. */
function jugadorDe (bot) {
  const hotbar = []
  for (let i = 36; i < 45; i++) hotbar.push(itemDe(bot, bot.inventory.slots[i]))
  const efectos = Object.keys(bot.entity.effects ?? {}).map(Number).map(id => bot.registry.effects[id]).filter(Boolean)
    .map(ef => ({ nombre: archivoDeEfecto(ef.name), bueno: ef.type === 'good' }))
  const atributos = bot.entity.attributes ?? {}
  const armadura = (atributos['minecraft:armor'] ?? atributos['generic.armor'] ?? atributos['minecraft:generic.armor'])?.value ?? 0
  return {
    vida: bot.health, comida: bot.food, nivel: bot.experience.level, xp: bot.experience.progress,
    modo: bot.game.gameMode, seleccion: bot.quickBarSlot, hotbar, efectos, armadura,
    manoIzq: itemDe(bot, bot.inventory.slots[45])
  }
}

/** Encantamientos de un ítem (componente enchantments de 1.20.5+): [{ nombre, nivel }]. */
function encantamientosDe (bot, it) {
  const c = it.components?.find?.(x => x.type === 'enchantments')?.data
  const lista = Array.isArray(c) ? c : Array.isArray(c?.enchantments) ? c.enchantments : []
  return lista.map(e => ({ nombre: bot.registry.enchantments?.[e.id]?.name ?? String(e.id), nivel: e.level ?? e.lvl ?? 1 }))
}

function itemDe (bot, it) {
  if (!it) return null
  const ChatMessage = require('prismarine-chat')(bot.registry)
  const json = (c) => { try { return ChatMessage.fromNotch(c).json } catch { return String(c) } }
  const nombre = it.customName ?? it.components?.find?.(x => x.type === 'custom_name')?.data
  const lore = it.customLore ?? it.components?.find?.(x => x.type === 'lore')?.data
  return {
    nombre: it.name,
    cuenta: it.count,
    nombreJson: nombre != null ? json(nombre) : null,
    lore: Array.isArray(lore) ? lore.map(json) : [],
    encantamientos: encantamientosDe(bot, it),
    irrompible: !!it.components?.find?.(x => x.type === 'unbreakable')
  }
}

function datosDeGaleria (cfg, salida, previo) {
  return {
    archivo: path.join(RAIZ, cfg.galeria.archivo),
    titulo: cfg.galeria.titulo,
    imagenes: salida,
    grupos: cfg.galeria.grupos,
    reglas: cfg.galeria.reglas.map(r => path.join(RAIZ, r)),
    raiz: RAIZ,
    version: cfg.version,
    escenas: cfg.escenas.map(e => ({ ...e, alt: previo[e.id]?.alt }))
  }
}

async function principal () {
  const o = opciones(process.argv.slice(2))
  const cfg = (await import(`./${o.plugin}/capturas.mjs`)).default
  if (o.soloGaleria) {
    // Solo regenera el Markdown con las imágenes y los textos alternativos ya guardados (sin servidor).
    const salida = path.join(RAIZ, cfg.salida)
    escribirGaleria(datosDeGaleria(cfg, salida, JSON.parse(fs.readFileSync(path.join(salida, 'capturas.json'), 'utf8'))))
    console.log(`» Galería escrita: ${cfg.galeria.archivo}`)
    return
  }
  const assets = prepararAssets(cfg.version, { cache: path.join(AQUI, '.cache') })
  const hud = await Hud.crear(assets, await cargarFuente(assets.dir))
  if (o.build) compilar(o.plugin)

  const base = path.join(RAIZ, 'plugins', o.plugin)
  const run = path.join(base, 'run')
  const pluginsDir = path.join(run, 'plugins')
  fs.mkdirSync(pluginsDir, { recursive: true })
  for (const f of fs.readdirSync(pluginsDir)) if (cfg.jar.test(f)) fs.rmSync(path.join(pluginsDir, f))
  const jar = fs.readdirSync(path.join(RAIZ, 'dist')).find(f => cfg.jar.test(f))
  if (!jar) throw new Error(`No hay ${cfg.jar} en dist/: compila con gradlew :${o.plugin}:dist`)
  fs.copyFileSync(path.join(RAIZ, 'dist', jar), path.join(pluginsDir, jar))
  for (const { de, a } of cfg.copiar) {
    fs.rmSync(path.join(run, a), { recursive: true, force: true })
    fs.cpSync(path.join(RAIZ, de), path.join(run, a), { recursive: true })
  }

  const salida = path.join(RAIZ, cfg.salida)
  fs.mkdirSync(salida, { recursive: true })
  const manifiesto = path.join(salida, 'capturas.json')
  const previo = fs.existsSync(manifiesto) ? JSON.parse(fs.readFileSync(manifiesto, 'utf8')) : {}

  const srv = new Servidor({ run, java: path.join(javaHome(), 'bin', 'java'), puerto: cfg.puerto })
  srv.prepararPropiedades(cfg.propiedades)
  srv.olvidarJugadores(cfg.bots, cfg.datosDeJugador)

  const resultados = []
  const bots = {}
  const camaras = {}
  let navegador = null
  let cobertura = null
  let correrEscena = null
  const alApagar = []
  const t0 = Date.now()
  try {
    console.log(`» Arrancando Paper en ${path.relative(RAIZ, run)} (127.0.0.1:${cfg.puerto})…`)
    await srv.arrancar()
    for (const c of cfg.preparar) await srv.consola(c)
    await srv.sincronizar()
    navegador = await abrirNavegador()

    const ctx = {
      srv,
      bots,
      hud,
      version: cfg.version,
      async entrar (nombre, { camara = true } = {}) {
        if (bots[nombre]) await bots[nombre].salir()
        bots[nombre] = await conectar(nombre, { puerto: cfg.puerto, version: cfg.version })
        if (camara) {
          if (camaras[nombre]) await camaras[nombre].recargar(bots[nombre].bot)
          else {
            camaras[nombre] = new Camara(bots[nombre].bot, cfg.puertoVisor + Object.keys(camaras).length)
            await camaras[nombre].abrir(navegador)
          }
        }
        return bots[nombre]
      },
      async salir (nombre) {
        await bots[nombre]?.salir()
        delete bots[nombre]
      },
      camara: (nombre) => camaras[nombre],
      /**
       * Foto de la escena actual vista por un bot: mundo 3D + HUD con lo que ese bot recibió desde su marca.
       * `o`: { camara, desde, chatAbierto, recorte, gui, desconexion, consola, sinMundo, sinHud, chat, titulo }
       */
      async foto (o = {}) {
        const nombre = o.camara ?? cfg.protagonista
        const j = bots[nombre]
        const desde = o.desde ?? ctx.marcas[nombre] ?? 0
        const vistos = j ? j.vistos.slice(desde) : []
        const ultimo = (t) => vistos.filter(v => v.tipo === t).at(-1)?.json
        const e = o.consola
          ? { consola: o.consola }
          : o.desconexion
            ? { desconexion: o.desconexion }
            : {
                chat: o.chat ?? vistos.filter(v => v.tipo === 'chat').map(v => v.json),
                chatAbierto: o.chatAbierto,
                actionbar: o.sinBarra ? null : ultimo('actionbar'),
                titulo: o.sinTitulo ? null : ultimo('title'),
                subtitulo: o.sinTitulo ? null : ultimo('subtitle'),
                bossbars: Object.values(j.bot.bossBars ?? {}).map(b => ({ titulo: b.title?.json ?? b.title, color: b.color, progreso: b.health })),
                jugador: o.sinHud ? null : jugadorDe(j.bot),
                gui: o.gui ? guiDe(j.bot, o.gui) : null
              }
        if (!o.consola && !o.desconexion && !o.sinMundo && camaras[nombre]) {
          await dormir(o.esperaMundo ?? 500)
          const { loadImage } = require('canvas')
          e.fondo = await loadImage(await camaras[nombre].foto())
        }
        const { lienzo, cajas } = await hud.dibujar(e)
        ctx.ultimaFoto = { lienzo: recortar(lienzo, cajas, o.recorte ?? ctx.escena.recorte ?? 'completa'), alt: altDe(e) }
        return ctx.ultimaFoto
      },
      marcas: {},
      /** Dos imágenes una sobre otra (p. ej. un inventario abierto y el chat después de cerrarlo). */
      apilar (arriba, abajo) {
        const { createCanvas } = require('canvas')
        const c = createCanvas(Math.max(arriba.width, abajo.width), arriba.height + abajo.height + 4)
        const g = c.getContext('2d')
        g.fillStyle = '#000000'
        g.fillRect(0, 0, c.width, c.height)
        g.drawImage(arriba, Math.floor((c.width - arriba.width) / 2), 0)
        g.drawImage(abajo, Math.floor((c.width - abajo.width) / 2), arriba.height + 4)
        return c
      }
    }

    const guiDe = (bot, g) => {
      const v = g.ventana ?? bot.currentWindow ?? bot.inventory
      const ranuras = v.slots.map(s => itemDe(bot, s))
      const cofre = v !== bot.inventory && /chest|generic_9x/.test(String(v.type))
      return {
        tipo: cofre ? 'cofre' : 'inventario',
        filas: cofre ? Math.round((v.slots.length - 36) / 9) : 0,
        titulo: cofre ? tituloDe(bot, v.title) : null,
        ranuras,
        raton: g.raton ?? null
      }
    }
    const tituloDe = (bot, t) => {
      const ChatMessage = require('prismarine-chat')(bot.registry)
      try { return ChatMessage.fromNotch(t).json } catch { return String(t ?? '') }
    }
    const altDe = (e) => {
      if (e.consola) return 'Consola del servidor: ' + e.consola.join(' / ')
      if (e.desconexion) return plano(hud.seg(e.desconexion.titulo)) + ': ' + plano(hud.seg(e.desconexion.motivo))
      const partes = []
      if (e.titulo) partes.push('Título: ' + plano(hud.seg(e.titulo)))
      if (e.subtitulo) partes.push('Subtítulo: ' + plano(hud.seg(e.subtitulo)))
      for (const b of e.bossbars ?? []) partes.push('Bossbar: ' + plano(hud.seg(b.titulo)))
      if (e.actionbar) partes.push('Barra de acción: ' + plano(hud.seg(e.actionbar)))
      if (e.chat?.length) partes.push('Chat: ' + e.chat.slice(-6).map(m => plano(hud.seg(m))).join(' / '))
      return partes.join('. ')
    }

    await cfg.antes(ctx)

    const escenas = cfg.escenas.filter(e => !o.solo || o.solo.test(e.id))
    // Una escena: preparar, correr con tiempo límite, guardar si cambió y contar el resultado.
    correrEscena = async (e, { preparar = true } = {}) => {
      ctx.escena = e
      ctx.ultimaFoto = null
      const inicio = Date.now()
      try {
        // La preparación entra en el tiempo límite: una reconexión colgada no frena toda la corrida.
        await Promise.race([
          (async () => {
            if (preparar) await cfg.antesDeCada?.(ctx, e)
            for (const n of Object.keys(bots)) ctx.marcas[n] = bots[n].marca()
            await e.fn(ctx)
          })(),
          dormir(e.timeoutMs ?? 45000).then(() => { throw new Error(`tiempo agotado (${e.timeoutMs ?? 45000} ms)`) })
        ])
        if (!ctx.ultimaFoto) await ctx.foto()
        const r = await guardar(ctx.ultimaFoto.lienzo, path.join(salida, e.id + '.png'))
        previo[e.id] = { alt: ctx.ultimaFoto.alt }
        resultados.push({ e, ...r })
        const marca = r.estado === 'cambió' ? (e.varia ? '≈' : '!') : r.estado === 'nueva' ? '+' : '='
        console.log(`  ${marca} ${e.id} — ${r.estado}${r.estado === 'cambió' ? ` (${(r.diferencia * 100).toFixed(2)} % de píxeles${e.varia ? ', varía' : ''})` : ''} (${Date.now() - inicio} ms)`)
      } catch (err) {
        resultados.push({ e, estado: 'falló', err })
        console.log(`  ✘ ${e.id}\n    ${String(err.message).split('\n').join('\n    ')}`)
      } finally {
        if (preparar) await cfg.despuesDeCada?.(ctx, e).catch(() => {})
      }
    }
    for (const e of escenas) {
      if (e.soloTexto) { resultados.push({ e, estado: 'texto' }); continue }
      if (e.alApagar) { alApagar.push(e); continue }
      await correrEscena(e)
    }
    if (!o.solo) cobertura = await cfg.cobertura(ctx)
  } finally {
    for (const c of Object.values(camaras)) await c.cerrarServidor().catch(() => {})
    await navegador?.close().catch(() => {})
    for (const n of Object.keys(bots)) await bots[n].salir().catch(() => {})
    if (srv.proceso?.exitCode == null) {
      for (const c of cfg.limpiar ?? []) await srv.consola(c)
      await srv.sincronizar().catch(() => {})
    }
    await srv.detener()
    for (const { a, retirar } of cfg.copiar) if (retirar) fs.rmSync(path.join(run, a), { recursive: true, force: true }) // H1 CA3
  }
  // Escenas que muestran lo que pasa al apagar (server_stop): con el log completo y sin jugadores.
  for (const e of alApagar) await correrEscena(e, { preparar: false })

  // H3 CA3: cada capacidad del servidor vivo tiene su escena (con imagen o solo texto).
  const cubiertas = new Set(cfg.escenas.flatMap(e => e.cubre ?? []))
  const faltan = cobertura ? cobertura.filter(c => !cubiertas.has(c)) : []
  for (const e of cfg.escenas.filter(x => x.soloTexto)) previo[e.id] = { soloTexto: e.soloTexto }
  fs.writeFileSync(manifiesto, JSON.stringify(Object.fromEntries(Object.entries(previo).sort()), null, 2) + '\n')
  escribirGaleria(datosDeGaleria(cfg, salida, previo))

  const cuenta = (est) => resultados.filter(r => r.estado === est).length
  const inesperadas = resultados.filter(r => r.estado === 'cambió' && !r.e.varia)
  console.log(`\n${resultados.length} escenas en ${Math.round((Date.now() - t0) / 1000)} s: ${cuenta('igual')} iguales, ` +
    `${cuenta('cambió')} cambiaron (${inesperadas.length} inesperadas), ${cuenta('nueva')} nuevas, ${cuenta('texto')} solo texto, ` +
    `${cuenta('falló')} fallaron · galería: ${cfg.galeria.archivo}`)
  if (inesperadas.length) console.log('  Cambiaron sin estar marcadas como «varía»: ' + inesperadas.map(r => r.e.id).join(', '))
  // Capturas solo puntuales (lección de la 004): una capacidad sin escena es un aviso; --cobertura la vuelve a exigir.
  if (faltan.length) console.log(`${o.cobertura ? '✘' : '·'} Sin escena en la galería: ${faltan.join(', ')}`)
  process.exitCode = cuenta('falló') || (o.cobertura && faltan.length) ? 1 : 0
}

principal().catch(err => {
  console.error('✘ ' + (err.stack || err.message))
  process.exitCode = 2
})
