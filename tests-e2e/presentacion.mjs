// Página de presentación (spec 020): saca el logo, el banner y las franjas de docs/site/assets-src/presentacion.html
// con Chrome sin interfaz y arma los GIF de los ejemplos con ffmpeg a partir de los cuadros de las escenas en inglés
// (npm run capturas -- --escenas presentacion). No necesita servidor. Uso: npm --prefix tests-e2e run presentacion
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { abrirNavegador } from './lib/capturas/visor.mjs'

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const FUENTE = path.join(RAIZ, 'docs/site/assets-src/presentacion.html')
const ASSETS = path.join(RAIZ, 'docs/site/pages/assets')
const SALIDA = path.join(ASSETS, 'presentacion')
/** Pieza del HTML → archivo. `cuadros`: cuántos pasos tiene su GIF (el más largo de sus dos escenas). */
const PIEZAS = [
  { id: 'logo', archivo: path.join(ASSETS, 'logo.png'), transparente: true },
  { id: 'banner', archivo: path.join(ASSETS, 'banner.png') },
  { id: 'franja-1', archivo: path.join(SALIDA, '1-header.png') },
  { id: 'franja-2', archivo: path.join(SALIDA, '2-examples.gif'), cuadros: 2 },
  { id: 'franja-3', archivo: path.join(SALIDA, '3-integrations.gif'), cuadros: 3 },
  { id: 'franja-4', archivo: path.join(SALIDA, '4-only-in-omnievents.png') },
  { id: 'franja-5', archivo: path.join(SALIDA, '5-yaml-to-game.png') },
  { id: 'franja-6', archivo: path.join(SALIDA, '6-from-conditionalevents.png') },
  { id: 'franja-7', archivo: path.join(SALIDA, '7-how-to-start.png') }
]
const SEGUNDOS_POR_CUADRO = 2.4

async function abrir (navegador, cuadro) {
  const pagina = await navegador.newPage()
  await pagina.setViewport({ width: 1600, height: 1000 })
  const url = pathToFileURL(FUENTE).href + (cuadro ? `?cuadro=${cuadro}` : '')
  await pagina.goto(url, { waitUntil: 'networkidle0' })
  await pagina.evaluate(async () => {
    await document.fonts.ready
    await Promise.all([...document.images].map(i => i.complete ? null : new Promise(r => { i.onload = i.onerror = r })))
    const rotas = [...document.images].filter(i => !i.naturalWidth).map(i => i.getAttribute('src'))
    if (rotas.length) throw new Error('Faltan imágenes (¿corriste las escenas?): ' + rotas.join(', '))
  })
  return pagina
}

async function foto (pagina, id, archivo, transparente = false) {
  const el = await pagina.$('#' + id)
  if (!el) throw new Error(`No hay #${id} en ${FUENTE}`)
  await el.screenshot({ path: archivo, omitBackground: transparente })
}

function gif (cuadros, archivo) {
  // Una paleta para todo el GIF y sin tramado: los bordes en píxeles y el texto quedan nítidos.
  const r = spawnSync('ffmpeg', ['-loglevel', 'error', '-y', '-framerate', `1/${SEGUNDOS_POR_CUADRO}`, '-i', path.join(cuadros, '%d.png'),
    '-vf', 'split[a][b];[a]palettegen=max_colors=256:stats_mode=full[p];[b][p]paletteuse=dither=none', '-loop', '0', archivo], { stdio: 'inherit' })
  if (r.status !== 0) throw new Error('ffmpeg falló armando ' + archivo)
}

async function principal () {
  fs.mkdirSync(SALIDA, { recursive: true })
  const navegador = await abrirNavegador()
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'presentacion-'))
  try {
    const pagina = await abrir(navegador)
    for (const p of PIEZAS.filter(x => !x.cuadros)) {
      await foto(pagina, p.id, p.archivo, p.transparente)
      console.log(`  + ${path.relative(RAIZ, p.archivo)}`)
    }
    for (const p of PIEZAS.filter(x => x.cuadros)) {
      const dir = path.join(tmp, p.id)
      fs.mkdirSync(dir)
      for (let n = 1; n <= p.cuadros; n++) {
        const pg = await abrir(navegador, n)
        await foto(pg, p.id, path.join(dir, `${n}.png`))
        await pg.close()
      }
      gif(dir, p.archivo)
      console.log(`  + ${path.relative(RAIZ, p.archivo)} (${p.cuadros} cuadros, ${Math.round(fs.statSync(p.archivo).size / 1024)} KB)`)
    }
  } finally {
    await navegador.close()
    fs.rmSync(tmp, { recursive: true, force: true })
  }
}

principal().catch(err => {
  console.error('✘ ' + (err.stack || err.message))
  process.exitCode = 1
})
