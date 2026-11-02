// Página de presentación (specs 020 y 024): saca el logo y las franjas del HTML de cada plugin con Chrome sin interfaz y
// arma los GIF con ffmpeg a partir de los cuadros de las escenas (npm run capturas -- --escenas presentacion). No
// necesita servidor. Uso: npm --prefix tests-e2e run presentacion [-- --plugin skinstatues|worldcraft]   (sin --plugin: OmniEvents)
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { abrirNavegador } from './lib/capturas/visor.mjs'

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/**
 * Por plugin: la fuente HTML y sus piezas (#id → archivo). `cuadros`: cuántos pasos tiene el GIF de la pieza (un número,
 * o una función que lo calcula con las escenas que existan). `marcadores`: si el HTML puede dibujar un marcador gris en
 * lugar de una escena que falta (se avisa); si no, una imagen que falta es un error.
 */
function plugins () {
  const assets = path.join(RAIZ, 'docs/site/pages/assets')
  const oe = path.join(assets, 'presentacion')
  const ss = path.join(RAIZ, 'docs/img/skinstatues/presentacion')
  const wc = path.join(RAIZ, 'docs/img/worldcraft/presentacion')
  return {
    omnievents: {
      fuente: path.join(RAIZ, 'docs/site/assets-src/presentacion.html'),
      salida: oe,
      piezas: [
        { id: 'logo', archivo: path.join(assets, 'logo.png'), transparente: true },
        { id: 'banner', archivo: path.join(assets, 'banner.png') },
        { id: 'franja-1', archivo: path.join(oe, '1-header.png') },
        { id: 'franja-2', archivo: path.join(oe, '2-examples.gif'), cuadros: 2 },
        { id: 'franja-3', archivo: path.join(oe, '3-integrations.gif'), cuadros: 3 },
        { id: 'franja-4', archivo: path.join(oe, '4-only-in-omnievents.png') },
        { id: 'franja-5', archivo: path.join(oe, '5-yaml-to-game.png') },
        { id: 'franja-6', archivo: path.join(oe, '6-from-conditionalevents.png') },
        { id: 'franja-7', archivo: path.join(oe, '7-how-to-start.png') }
      ]
    },
    skinstatues: {
      fuente: path.join(RAIZ, 'docs/site/assets-src/skinstatues.html'),
      salida: ss,
      marcadores: true,
      piezas: [
        { id: 'logo', archivo: path.join(ss, 'logo.png'), transparente: true },
        { id: 'franja-1', archivo: path.join(ss, '1-header.png') },
        { id: 'franja-2', archivo: path.join(ss, '2-giant.png') },
        { id: 'franja-3', archivo: path.join(ss, '3-any-player.png') },
        // build-1.png … build-N.png de tests-e2e/skinstatues/presentacion.mjs (build-close.png es la foto de al lado)
        { id: 'franja-4', archivo: path.join(ss, '4-build.gif'), cuadros: () => cuadrosDe(path.join(ss, 'escenas'), 'build') },
        { id: 'franja-5', archivo: path.join(ss, '5-name-or-link.png') },
        { id: 'franja-6', archivo: path.join(ss, '6-features.png') },
        { id: 'franja-7', archivo: path.join(ss, '7-how-to-start.png') }
      ]
    },
    // Spec 038: sin GIF; las fotos son las escenas de tests-e2e/worldcraft/presentacion.mjs (terrain, shapes, image).
    worldcraft: {
      fuente: path.join(RAIZ, 'docs/site/assets-src/worldcraft.html'),
      salida: wc,
      marcadores: true,
      piezas: [
        { id: 'logo', archivo: path.join(wc, 'logo.png'), transparente: true },
        { id: 'franja-1', archivo: path.join(wc, '1-header.png') },
        { id: 'franja-2', archivo: path.join(wc, '2-terrain.png') },
        { id: 'franja-3', archivo: path.join(wc, '3-shapes-pictures.png') },
        { id: 'franja-4', archivo: path.join(wc, '4-compared.png') },
        { id: 'franja-5', archivo: path.join(wc, '5-commands.png') },
        { id: 'franja-6', archivo: path.join(wc, '6-features.png') },
        { id: 'franja-7', archivo: path.join(wc, '7-how-to-start.png') }
      ]
    }
  }
}
const SEGUNDOS_POR_CUADRO = 2.4

/** Cuántos `<prefijo>-1.png`, `<prefijo>-2.png`… seguidos hay en la carpeta (al menos 1: sin cuadros, un marcador). */
function cuadrosDe (dir, prefijo) {
  let n = 0
  while (fs.existsSync(path.join(dir, `${prefijo}-${n + 1}.png`))) n++
  return Math.max(1, n)
}

function argumento (nombre) {
  const i = process.argv.indexOf(nombre)
  return i >= 0 ? process.argv[i + 1] : undefined
}

async function abrir (navegador, plugin, consulta = '') {
  const pagina = await navegador.newPage()
  await pagina.setViewport({ width: 1600, height: 1000 })
  await pagina.goto(pathToFileURL(plugin.fuente).href + consulta, { waitUntil: 'networkidle0' })
  const faltan = await pagina.evaluate(async () => {
    await document.fonts.ready
    await Promise.all([...document.images].map(i => i.complete ? null : new Promise(r => { i.onload = i.onerror = r })))
    const rotas = [...document.images].filter(i => !i.naturalWidth).map(i => i.getAttribute('src'))
    if (rotas.length) throw new Error('Faltan imágenes (¿corriste las escenas?): ' + rotas.join(', '))
    return [...document.querySelectorAll('[data-falta]')].map(e => e.dataset.falta)
  })
  if (faltan.length && !plugin.marcadores) throw new Error('Faltan imágenes: ' + faltan.join(', '))
  return { pagina, faltan }
}

async function foto (pagina, plugin, id, archivo, transparente = false) {
  const el = await pagina.$('#' + id)
  if (!el) throw new Error(`No hay #${id} en ${plugin.fuente}`)
  await el.screenshot({ path: archivo, omitBackground: transparente })
}

function gif (cuadros, archivo) {
  // Una paleta para todo el GIF y sin tramado: los bordes en píxeles y el texto quedan nítidos.
  const r = spawnSync('ffmpeg', ['-loglevel', 'error', '-y', '-framerate', `1/${SEGUNDOS_POR_CUADRO}`, '-i', path.join(cuadros, '%d.png'),
    '-vf', 'split[a][b];[a]palettegen=max_colors=256:stats_mode=full[p];[b][p]paletteuse=dither=none', '-loop', '0', archivo], { stdio: 'inherit' })
  if (r.status !== 0) throw new Error('ffmpeg falló armando ' + archivo)
}

async function principal () {
  const nombre = argumento('--plugin') || 'omnievents'
  const plugin = plugins()[nombre]
  if (!plugin) throw new Error(`Plugin desconocido: ${nombre} (hay: ${Object.keys(plugins()).join(', ')})`)
  fs.mkdirSync(plugin.salida, { recursive: true })
  const navegador = await abrirNavegador()
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'presentacion-'))
  const marcadores = new Set()
  try {
    const { pagina, faltan } = await abrir(navegador, plugin)
    faltan.forEach(f => marcadores.add(f))
    for (const p of plugin.piezas.filter(x => !x.cuadros)) {
      await foto(pagina, plugin, p.id, p.archivo, p.transparente)
      console.log(`  + ${path.relative(RAIZ, p.archivo)}`)
    }
    for (const p of plugin.piezas.filter(x => x.cuadros)) {
      const total = typeof p.cuadros === 'function' ? p.cuadros() : p.cuadros
      const dir = path.join(tmp, p.id)
      fs.mkdirSync(dir)
      for (let n = 1; n <= total; n++) {
        // OmniEvents solo lee ?cuadro; SkinStatues también ?cuadros (cuántos hay, para el rótulo n/N)
        const { pagina: pg, faltan: f } = await abrir(navegador, plugin, `?cuadro=${n}` + (nombre === 'omnievents' ? '' : `&cuadros=${total}`))
        f.forEach(x => marcadores.add(x))
        await foto(pg, plugin, p.id, path.join(dir, `${n}.png`))
        await pg.close()
      }
      gif(dir, p.archivo)
      console.log(`  + ${path.relative(RAIZ, p.archivo)} (${total} cuadros, ${Math.round(fs.statSync(p.archivo).size / 1024)} KB)`)
    }
  } finally {
    await navegador.close()
    fs.rmSync(tmp, { recursive: true, force: true })
  }
  if (marcadores.size) console.log(`  ! Con marcador gris (faltan las escenas): ${[...marcadores].join(', ')}`)
}

principal().catch(err => {
  console.error('✘ ' + (err.stack || err.message))
  process.exitCode = 1
})
