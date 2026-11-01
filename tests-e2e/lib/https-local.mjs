// Servidor HTTPS local para la suite (spec 023): sirve archivos falsos a 127.0.0.1 con un certificado propio de la
// corrida, hecho con el keytool del JDK del servidor. Java confía en él solo por la línea de comandos que devuelve
// `jvm` (una copia del cacerts del JDK con el certificado agregado); nada de internet real.
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import https from 'node:https'
import path from 'node:path'
import zlib from 'node:zlib'

const CLAVE = 'e2e-local'
const CACERTS = 'changeit' // la contraseña conocida del cacerts del JDK

/** PNG RGBA mínima: `pixel(x, y)` devuelve [r, g, b, a]. */
export function png (ancho, alto, pixel) {
  const crudo = Buffer.alloc((ancho * 4 + 1) * alto)
  for (let y = 0; y < alto; y++) {
    const fila = y * (ancho * 4 + 1)
    crudo[fila] = 0 // sin filtro
    for (let x = 0; x < ancho; x++) {
      const [r, g, b, a] = pixel(x, y)
      crudo.writeUInt8(r, fila + 1 + x * 4)
      crudo.writeUInt8(g, fila + 2 + x * 4)
      crudo.writeUInt8(b, fila + 3 + x * 4)
      crudo.writeUInt8(a, fila + 4 + x * 4)
    }
  }
  const bloque = (tipo, datos) => {
    const largo = Buffer.alloc(4)
    largo.writeUInt32BE(datos.length)
    const td = Buffer.concat([Buffer.from(tipo, 'ascii'), datos])
    const crc = Buffer.alloc(4)
    crc.writeUInt32BE(zlib.crc32(td) >>> 0)
    return Buffer.concat([largo, td, crc])
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(ancho, 0)
  ihdr.writeUInt32BE(alto, 4)
  ihdr[8] = 8 // bits por canal
  ihdr[9] = 6 // RGBA
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), bloque('IHDR', ihdr),
    bloque('IDAT', zlib.deflateSync(crudo)), bloque('IEND', Buffer.alloc(0))])
}

function keytool (java, args) {
  const exe = path.join(path.dirname(java), 'keytool')
  const r = spawnSync(exe, args, { encoding: 'utf8' })
  if (r.status !== 0) throw new Error(`keytool ${args[0]} falló: ${r.stderr || r.stdout || r.error}`)
}

/**
 * @param {object} o
 * @param {string} o.carpeta dónde dejar el certificado (dentro de run/)
 * @param {string} o.java el java del servidor (keytool está al lado)
 * @param {Record<string, (req, res) => void>} o.rutas ruta → respuesta
 * @returns {Promise<{ puerto: number, base: string, jvm: string[], pedidos: Record<string, number>, cerrar: () => Promise<void> }>}
 */
export async function httpsLocal ({ carpeta, java, rutas }) {
  fs.rmSync(carpeta, { recursive: true, force: true })
  fs.mkdirSync(carpeta, { recursive: true })
  const servidor = path.join(carpeta, 'servidor.p12')
  const cer = path.join(carpeta, 'e2e.cer')
  const confianza = path.join(carpeta, 'confianza.p12')
  keytool(java, ['-genkeypair', '-alias', 'e2e', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
    '-dname', 'CN=127.0.0.1', '-ext', 'SAN=ip:127.0.0.1,dns:localhost', '-keystore', servidor, '-storetype', 'PKCS12',
    '-storepass', CLAVE, '-keypass', CLAVE])
  keytool(java, ['-exportcert', '-alias', 'e2e', '-keystore', servidor, '-storepass', CLAVE, '-file', cer])
  // Las autoridades del JDK más el certificado local: Paper sigue pudiendo hablar con Mojang (si el almacén solo tuviera
  // el local, «Failed to request yggdrasil public key»).
  fs.copyFileSync(path.join(path.dirname(path.dirname(java)), 'lib', 'security', 'cacerts'), confianza)
  keytool(java, ['-importcert', '-noprompt', '-alias', 'e2e-local', '-file', cer, '-keystore', confianza, '-storepass', CACERTS])

  const pedidos = {}
  const abiertos = new Set()
  const srv = https.createServer({ pfx: fs.readFileSync(servidor), passphrase: CLAVE }, (req, res) => {
    const ruta = req.url.split('?')[0]
    pedidos[ruta] = (pedidos[ruta] ?? 0) + 1
    const f = rutas[ruta]
    if (!f) {
      res.writeHead(404, { 'Content-Type': 'text/plain' })
      res.end('not found')
      return
    }
    f(req, res)
  })
  srv.on('connection', s => {
    abiertos.add(s)
    s.on('close', () => abiertos.delete(s))
  })
  await new Promise((resolve, reject) => srv.listen(0, '127.0.0.1', resolve).on('error', reject))
  const puerto = srv.address().port
  return {
    puerto,
    base: `https://127.0.0.1:${puerto}`,
    pedidos,
    jvm: ['-Dskinstatues.test.localLinks=true', `-Djavax.net.ssl.trustStore=${confianza}`,
      `-Djavax.net.ssl.trustStorePassword=${CACERTS}`],
    async cerrar () {
      for (const s of abiertos) s.destroy()
      await new Promise(resolve => srv.close(() => resolve()))
      fs.rmSync(carpeta, { recursive: true, force: true })
    }
  }
}
