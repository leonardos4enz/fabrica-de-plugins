// Lector mínimo de ZIP (directorio central + deflate) para leer el jar del cliente sin dependencias (spec 004).
import fs from 'node:fs'
import zlib from 'node:zlib'

export function leerZip (archivo) {
  const b = fs.readFileSync(archivo)
  let fin = b.length - 22
  while (fin >= 0 && b.readUInt32LE(fin) !== 0x06054b50) fin--
  if (fin < 0) throw new Error(`${archivo} no es un ZIP`)
  const total = b.readUInt16LE(fin + 10)
  let p = b.readUInt32LE(fin + 16)
  const entradas = new Map()
  for (let i = 0; i < total; i++) {
    if (b.readUInt32LE(p) !== 0x02014b50) throw new Error(`${archivo}: directorio central dañado`)
    const metodo = b.readUInt16LE(p + 10)
    const comprimido = b.readUInt32LE(p + 20)
    const largoNombre = b.readUInt16LE(p + 28)
    const largoExtra = b.readUInt16LE(p + 30)
    const largoComentario = b.readUInt16LE(p + 32)
    const local = b.readUInt32LE(p + 42)
    const nombre = b.toString('utf8', p + 46, p + 46 + largoNombre)
    entradas.set(nombre, { metodo, comprimido, local })
    p += 46 + largoNombre + largoExtra + largoComentario
  }
  return {
    nombres: () => [...entradas.keys()],
    leer (nombre) {
      const e = entradas.get(nombre)
      if (!e) return null
      const inicio = e.local + 30 + b.readUInt16LE(e.local + 26) + b.readUInt16LE(e.local + 28)
      const datos = b.subarray(inicio, inicio + e.comprimido)
      if (e.metodo === 0) return Buffer.from(datos)
      if (e.metodo === 8) return zlib.inflateRawSync(datos)
      throw new Error(`${nombre}: método de compresión ${e.metodo} no soportado`)
    }
  }
}
