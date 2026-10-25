// Comparación de una imagen nueva con la anterior (spec 004, H4): por píxeles, no por bytes, y se reescribe solo si
// cambió, para que regenerar sin cambios no toque el repositorio.
import fs from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { createCanvas, loadImage } = require('canvas')

export function pixeles (lienzo) {
  return lienzo.getContext('2d').getImageData(0, 0, lienzo.width, lienzo.height).data
}

/** Fracción de píxeles distintos (0..1) entre dos lienzos; 1 si cambió el tamaño. */
export function diferencia (a, b) {
  if (a.width !== b.width || a.height !== b.height) return 1
  const pa = pixeles(a)
  const pb = pixeles(b)
  let distintos = 0
  for (let i = 0; i < pa.length; i += 4) {
    if (pa[i] !== pb[i] || pa[i + 1] !== pb[i + 1] || pa[i + 2] !== pb[i + 2] || pa[i + 3] !== pb[i + 3]) distintos++
  }
  return distintos / (pa.length / 4)
}

export async function lienzoDe (archivo) {
  const img = await loadImage(archivo)
  const c = createCanvas(img.width, img.height)
  c.getContext('2d').drawImage(img, 0, 0)
  return c
}

/** Escribe el PNG si es nuevo o cambió. Devuelve { estado: 'nueva'|'igual'|'cambió', diferencia }. */
export async function guardar (lienzo, archivo) {
  if (fs.existsSync(archivo)) {
    const d = diferencia(lienzo, await lienzoDe(archivo))
    if (d === 0) return { estado: 'igual', diferencia: 0 }
    fs.writeFileSync(archivo, lienzo.toBuffer('image/png', { compressionLevel: 9 }))
    return { estado: 'cambió', diferencia: d }
  }
  fs.writeFileSync(archivo, lienzo.toBuffer('image/png', { compressionLevel: 9 }))
  return { estado: 'nueva', diferencia: 1 }
}
