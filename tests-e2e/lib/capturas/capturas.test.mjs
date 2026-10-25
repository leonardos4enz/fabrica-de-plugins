// Unitarias del renderizador de capturas (spec 004, T7). Las que dibujan necesitan `canvas` (dependencia opcional) y
// las de la fuente, el cliente de Minecraft instalado: si faltan, se omiten.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { createRequire } from 'node:module'
import { yamlDeRegla, ancla } from './galeria.mjs'
import { leerZip } from './zip.mjs'
import { carpetaMinecraft } from './assets.mjs'

const require = createRequire(import.meta.url)
let canvas = null
try { canvas = require('canvas') } catch { canvas = null }
const sinCanvas = canvas ? false : 'falta la dependencia opcional canvas'
const hud = canvas ? await import('./hud.mjs') : null

test('H2 CA1: segmentos de un componente con colores, estilos heredados y extra', { skip: sinCanvas }, () => {
  const s = hud.segmentos({ text: 'Hola ', color: 'gold', extra: [{ text: 'Ana', bold: true }, { text: '!', color: '#12abEF' }] })
  assert.deepEqual(s.map(x => [x.texto, x.color, !!x.bold]), [['Hola ', 'FFAA00', false], ['Ana', 'FFAA00', true], ['!', '12ABEF', false]])
})

test('H2 CA1: translate con %s, %1$s y %% usa el idioma del cliente', { skip: sinCanvas }, () => {
  const idioma = { 'multiplayer.player.joined': '%s se unió a la partida', x: '%2$s y %1$s al 100%%' }
  assert.equal(hud.plano(hud.segmentos({ translate: 'multiplayer.player.joined', with: ['Ana'], color: 'yellow' }, idioma)), 'Ana se unió a la partida')
  assert.equal(hud.plano(hud.segmentos({ translate: 'x', with: ['a', { text: 'b' }] }, idioma)), 'b y a al 100%')
  assert.equal(hud.plano(hud.segmentos({ translate: 'no.existe', fallback: 'respaldo' }, idioma)), 'respaldo')
})

test('H2 CA1: NBT simplificado (clave vacía y booleanos 1b) y cadenas sueltas', { skip: sinCanvas }, () => {
  const s = hud.segmentos(['', { '': 'x', italic: '1b' }, 'y'])
  assert.deepEqual(s.map(x => [x.texto, !!x.italic]), [['x', true], ['y', false]])
})

test('archivo de textura de un efecto a partir de su nombre en minecraft-data', { skip: sinCanvas }, () => {
  assert.equal(hud.archivoDeEfecto('InstantHealth'), 'instant_health')
  assert.equal(hud.archivoDeEfecto('Speed'), 'speed')
})

test('H1 CA2: recortar une las cajas pedidas, con margen y un ancho mínimo', { skip: sinCanvas }, () => {
  const lienzo = canvas.createCanvas(hud.ANCHO * hud.ESCALA, hud.ALTO * hud.ESCALA)
  const r = hud.recortar(lienzo, { chat: { x: 0, y: 150, w: 328, h: 50 }, barra: { x: 180, y: 170, w: 60, h: 12 } }, ['chat', 'barra'], { margen: 8 })
  assert.equal(r.width, (336) * hud.ESCALA)
  // Arriba el margen; abajo termina 1 px bajo el chat (sin el HUD no corta los corazones por la mitad).
  assert.equal(r.height, (8 + 50 + 1) * hud.ESCALA)
  const conHud = hud.recortar(lienzo, { chat: { x: 0, y: 150, w: 328, h: 50 }, hud: { x: 121, y: 200, w: 184, h: 40 } }, ['chat', 'hud'], { margen: 8 })
  assert.equal(conHud.height, (240 - 142) * hud.ESCALA)
  const angosto = hud.recortar(lienzo, { barra: { x: 200, y: 100, w: 20, h: 10 } }, 'barra', { margen: 0, minimo: 200 })
  assert.equal(angosto.width, 200 * hud.ESCALA)
  assert.equal(hud.recortar(lienzo, {}, 'completa'), lienzo)
})

test('H4 CA1: la comparación cuenta píxeles distintos, no bytes', { skip: sinCanvas }, async () => {
  const { diferencia, guardar } = await import('./png.mjs')
  const a = canvas.createCanvas(10, 10)
  const b = canvas.createCanvas(10, 10)
  assert.equal(diferencia(a, b), 0)
  b.getContext('2d').fillRect(0, 0, 1, 1)
  assert.equal(diferencia(a, b), 0.01)
  assert.equal(diferencia(a, canvas.createCanvas(5, 5)), 1)
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'capturas-'))
  const f = path.join(dir, 'x.png')
  assert.equal((await guardar(a, f)).estado, 'nueva')
  const antes = fs.statSync(f).mtimeMs
  assert.equal((await guardar(a, f)).estado, 'igual')
  assert.equal(fs.statSync(f).mtimeMs, antes)
  assert.equal((await guardar(b, f)).estado, 'cambió')
})

test('H3 CA4: yamlDeRegla toma una regla sin los comentarios de la siguiente', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'reglas-'))
  fs.writeFileSync(path.join(dir, 'a.yml'), '# cabecera\nuno:\n  trigger: call\n  actions:\n    - message: "1"\n\n# la siguiente\ndos:\n  trigger: call\n')
  assert.equal(yamlDeRegla('uno', [dir]).yaml, 'uno:\n  trigger: call\n  actions:\n    - message: "1"')
  assert.equal(yamlDeRegla('dos', [dir]).yaml, 'dos:\n  trigger: call')
  assert.equal(yamlDeRegla('tres', [dir]), null)
})

test('anclas de la galería como las genera GitHub', () => {
  assert.equal(ancla('Acciones · Mensajes'), 'acciones--mensajes')
  assert.equal(ancla('Comandos de /omni'), 'comandos-de-omni')
  assert.equal(ancla('Disparadores · Ítems'), 'disparadores--ítems')
})

test('H2 CA5: el lector de ZIP lee entradas guardadas y comprimidas', () => {
  const zlib = require('node:zlib')
  // ZIP mínimo con una entrada deflate («hola»), armado a mano.
  const datos = zlib.deflateRawSync(Buffer.from('hola'))
  const nombre = Buffer.from('a/b.txt')
  const local = Buffer.alloc(30)
  local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(8, 8); local.writeUInt32LE(datos.length, 18); local.writeUInt32LE(4, 22)
  local.writeUInt16LE(nombre.length, 26)
  const central = Buffer.alloc(46)
  central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(8, 10); central.writeUInt32LE(datos.length, 20); central.writeUInt32LE(4, 24)
  central.writeUInt16LE(nombre.length, 28); central.writeUInt32LE(0, 42)
  const inicioCentral = local.length + nombre.length + datos.length
  const fin = Buffer.alloc(22)
  fin.writeUInt32LE(0x06054b50, 0); fin.writeUInt16LE(1, 8); fin.writeUInt16LE(1, 10)
  fin.writeUInt32LE(central.length + nombre.length, 12); fin.writeUInt32LE(inicioCentral, 16)
  const f = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'zip-')), 'x.zip')
  fs.writeFileSync(f, Buffer.concat([local, nombre, datos, central, nombre, fin]))
  const z = leerZip(f)
  assert.deepEqual(z.nombres(), ['a/b.txt'])
  assert.equal(z.leer('a/b.txt').toString(), 'hola')
  assert.equal(z.leer('no'), null)
})

const jar = path.join(carpetaMinecraft(), 'versions/1.21.4/1.21.4.jar')
test('fuente real: avances del juego (espacio 4, «i» 2, «W» 6, negrita +1) y unifont para 中', { skip: !canvas || !fs.existsSync(jar) ? 'sin canvas o sin Minecraft 1.21.4 instalado' : false }, async () => {
  const { prepararAssets } = await import('./assets.mjs')
  const { cargarFuente } = await import('./fuente.mjs')
  const a = prepararAssets('1.21.4', { cache: path.resolve(import.meta.dirname, '../../.cache') })
  const f = await cargarFuente(a.dir)
  assert.equal(f.ancho([{ texto: ' ' }]), 4)
  assert.equal(f.ancho([{ texto: 'i' }]), 2)
  assert.equal(f.ancho([{ texto: 'W' }]), 6)
  assert.equal(f.ancho([{ texto: 'W', bold: true }]), 7)
  assert.equal(f.glifo('中'.codePointAt(0)).escala, 0.5, '中 viene de unifont, a media escala')
  assert.ok(f.ancho([{ texto: 'ñ' }]) > 0)
})
