// Fuente de Minecraft (spec 004, plan §1): los proveedores bitmap de font/include/default.json (ascii, accented,
// nonlatin_european), el espacio y unifont (unihex, a media escala) para lo demás. Cada glifo es un mapa de píxeles.
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { leerZip } from './zip.mjs'
const require = createRequire(import.meta.url)
const { loadImage, createCanvas } = require('canvas')

export async function cargarFuente (dir) {
  const def = JSON.parse(fs.readFileSync(path.join(dir, 'assets/minecraft/font/include/default.json'), 'utf8'))
  const glifos = new Map()
  for (const p of def.providers) {
    if (p.type !== 'bitmap') continue
    const img = await loadImage(path.join(dir, 'assets/minecraft/textures', p.file.replace('minecraft:', '')))
    const c = createCanvas(img.width, img.height)
    const g = c.getContext('2d')
    g.drawImage(img, 0, 0)
    const datos = g.getImageData(0, 0, img.width, img.height).data
    const cw = img.width / [...p.chars[0]].length
    const ch = img.height / p.chars.length
    const escala = (p.height ?? 8) / ch
    p.chars.forEach((fila, fy) => {
      [...fila].forEach((car, fx) => {
        const cp = car.codePointAt(0)
        if (cp === 0 || glifos.has(cp)) return // el primer proveedor que tiene el carácter gana
        const px = []
        let ancho = 0
        for (let y = 0; y < ch; y++) {
          const r = []
          for (let x = 0; x < cw; x++) {
            const on = datos[((fy * ch + y) * img.width + fx * cw + x) * 4 + 3] > 0
            r.push(on)
            if (on && x + 1 > ancho) ancho = x + 1
          }
          px.push(r)
        }
        // Avance del juego: (última columna con píxeles + 1) × escala + 1.
        glifos.set(cp, { px, escala, ascenso: p.ascent, avance: ancho * escala + 1, dx: 0 })
      })
    })
  }
  glifos.set(32, { px: [], escala: 1, ascenso: 7, avance: 4, dx: 0 })
  return new Fuente(glifos, path.join(dir, 'unifont.zip'))
}

export class Fuente {
  constructor (glifos, unifontZip) {
    this.glifos = glifos
    this.unifontZip = unifontZip
    this.hex = null
  }

  /** Unifont solo se lee si hace falta un carácter que no está en los bitmap (✔, ✘, →…). */
  _unihex (cp) {
    if (!this.hex) {
      this.hex = new Map()
      if (fs.existsSync(this.unifontZip)) {
        const zip = leerZip(this.unifontZip)
        const nombre = zip.nombres().find(n => n.endsWith('.hex'))
        for (const l of zip.leer(nombre).toString('latin1').split('\n')) {
          const i = l.indexOf(':')
          if (i > 0) this.hex.set(parseInt(l.slice(0, i), 16), l.slice(i + 1).trim())
        }
      }
    }
    const h = this.hex.get(cp)
    if (!h) return null
    const anchoBits = h.length / 4 // 32 dígitos → 8 columnas, 64 → 16
    const porFila = anchoBits / 4
    const px = []
    let izq = anchoBits
    let der = -1
    for (let y = 0; y < 16; y++) {
      const bits = parseInt(h.slice(y * porFila, (y + 1) * porFila), 16)
      const r = []
      for (let x = 0; x < anchoBits; x++) {
        const on = ((bits >> (anchoBits - 1 - x)) & 1) === 1
        r.push(on)
        if (on) { izq = Math.min(izq, x); der = Math.max(der, x) }
      }
      px.push(r)
    }
    if (der < 0) return { px: [], escala: 0.5, ascenso: 7, avance: 4, dx: 0 }
    return { px, escala: 0.5, ascenso: 7, avance: (der - izq + 1) / 2 + 1, dx: -izq }
  }

  glifo (cp) {
    let g = this.glifos.get(cp)
    if (!g) {
      g = this._unihex(cp) ?? this.glifos.get(63) // '?'
      this.glifos.set(cp, g)
    }
    return g
  }

  /** Ancho en píxeles de GUI de una lista de segmentos. */
  ancho (segmentos) {
    let w = 0
    for (const s of segmentos) for (const car of s.texto) w += this.glifo(car.codePointAt(0)).avance + (s.bold ? 1 : 0)
    return w
  }
}
