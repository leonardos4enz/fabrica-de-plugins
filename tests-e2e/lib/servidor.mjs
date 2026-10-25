// Servidor Paper de prueba (spec 002, plan §2): prepara run/, arranca, manda comandos de consola y lee el log.
import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

const ANSI = /\x1b\[[0-9;]*[A-Za-z]/g

/** UUID de un jugador sin cuenta (online-mode=false), igual que UUID.nameUUIDFromBytes de Java. */
export function uuidOffline (nombre) {
  const h = createHash('md5').update('OfflinePlayer:' + nombre, 'utf8').digest()
  h[6] = (h[6] & 0x0f) | 0x30
  h[8] = (h[8] & 0x3f) | 0x80
  const x = h.toString('hex')
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`
}

export class Servidor {
  /**
   * @param {object} o
   * @param {string} o.run carpeta del servidor de prueba (plugins/<plugin>/run o run-<versión>)
   * @param {string} o.java ejecutable de Java
   * @param {number} o.puerto puerto local
   */
  constructor ({ run, java, puerto }) {
    this.run = run
    this.java = java
    this.puerto = puerto
    this.lineas = []
    this.proceso = null
    this.salida = null
  }

  /**
   * H2: EULA del dueño, solo localhost, sin cuentas. Nunca escribe fuera de run/. `ademas`: propiedades propias de quien
   * usa el servidor (las capturas de la spec 004 necesitan monstruos); no pueden cambiar las de seguridad.
   */
  prepararPropiedades (ademas = {}) {
    const eula = path.join(this.run, 'eula.txt')
    if (!fs.existsSync(eula) || !/eula=true/.test(fs.readFileSync(eula, 'utf8'))) {
      throw new Error(`El EULA de Minecraft lo acepta el dueño en ${eula}; la suite no lo acepta por él.`)
    }
    const forzadas = {
      'server-ip': '127.0.0.1',
      'server-port': String(this.puerto),
      'online-mode': 'false',
      'enforce-secure-profile': 'false',
      'allow-flight': 'true',
      'spawn-protection': '0',
      'level-type': 'minecraft\\:flat',
      'spawn-monsters': 'false',
      'difficulty': 'easy',
      'view-distance': '4',
      'simulation-distance': '4'
    }
    for (const [k, v] of Object.entries(ademas)) if (!['server-ip', 'online-mode', 'enforce-secure-profile'].includes(k)) forzadas[k] = v
    const archivo = path.join(this.run, 'server.properties')
    const lineas = fs.existsSync(archivo) ? fs.readFileSync(archivo, 'utf8').split(/\r?\n/) : []
    const vistas = new Set()
    const nuevas = lineas.map(l => {
      const i = l.indexOf('=')
      if (l.startsWith('#') || i < 0) return l
      const k = l.slice(0, i)
      if (!(k in forzadas)) return l
      vistas.add(k)
      return `${k}=${forzadas[k]}`
    })
    for (const [k, v] of Object.entries(forzadas)) if (!vistas.has(k)) nuevas.push(`${k}=${v}`)
    fs.writeFileSync(archivo, nuevas.join('\n'))
  }

  /** H1 CA3: borra los datos de los jugadores de prueba (mundo y plugins). */
  olvidarJugadores (nombres, carpetasDePlugin = []) {
    for (const n of nombres) {
      const id = uuidOffline(n)
      const archivos = [
        `world/playerdata/${id}.dat`, `world/playerdata/${id}.dat_old`,
        `world/stats/${id}.json`, `world/advancements/${id}.json`,
        // Formato de mundo de 26.1 en adelante.
        `world/players/data/${id}.dat`, `world/players/data/${id}.dat_old`,
        `world/players/stats/${id}.json`, `world/players/advancements/${id}.json`,
        ...carpetasDePlugin.map(c => c.replace('{uuid}', id))
      ]
      for (const a of archivos) fs.rmSync(path.join(this.run, a), { force: true })
    }
  }

  arrancar ({ memoria = '1536M', timeoutMs = 180000 } = {}) {
    const jar = fs.readdirSync(this.run).find(f => /^paper-.*\.jar$/.test(f))
    if (!jar) throw new Error(`No hay paper-*.jar en ${this.run}`)
    this.salida = fs.createWriteStream(path.join(this.run, 'e2e.log'))
    this.proceso = spawn(this.java, [`-Xms512M`, `-Xmx${memoria}`, '-Dterminal.jline=false', '-Dterminal.ansi=false',
      '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dfile.encoding=UTF-8',
      '-jar', jar, '--nogui'], { cwd: this.run, stdio: ['pipe', 'pipe', 'pipe'] })
    let resto = ''
    const leer = (buf) => {
      resto += buf.toString('utf8')
      const partes = resto.split(/\r?\n/)
      resto = partes.pop()
      for (const p of partes) {
        const l = p.replace(ANSI, '')
        this.lineas.push(l)
        this.salida.write(l + '\n')
      }
    }
    this.proceso.stdout.on('data', leer)
    this.proceso.stderr.on('data', leer)
    this.terminado = new Promise(resolve => this.proceso.on('exit', code => resolve(code)))
    return this.esperarLog(/Done \([\d.,]+s\)!/, { desde: 0, timeoutMs })
  }

  marca () {
    return this.lineas.length
  }

  /** Espera una línea del log que cumpla el patrón, a partir de una marca. Devuelve la línea. */
  async esperarLog (patron, { desde = this.marca(), timeoutMs = 10000 } = {}) {
    const fin = Date.now() + timeoutMs
    let i = desde
    for (;;) {
      for (; i < this.lineas.length; i++) if (patron.test(this.lineas[i])) return this.lineas[i]
      if (this.proceso?.exitCode != null) throw new Error(`El servidor se apagó esperando ${patron}`)
      if (Date.now() > fin) {
        throw new Error(`El log no mostró ${patron} en ${timeoutMs} ms. Últimas líneas:\n  ` +
          this.lineas.slice(Math.max(desde, this.lineas.length - 8)).join('\n  '))
      }
      await dormir(50)
    }
  }

  /** H3 CA1: manda un comando de consola; si se da `espera`, devuelve la primera línea que la cumple. */
  async consola (cmd, { espera, timeoutMs = 10000 } = {}) {
    const desde = this.marca()
    this.proceso.stdin.write(cmd + '\n')
    if (espera) return this.esperarLog(espera, { desde, timeoutMs })
    return null
  }

  /**
   * Espera a que el servidor haya procesado todos los comandos de consola enviados antes: la consola los atiende en
   * orden, así que basta con esperar la respuesta de uno inocuo (seed).
   */
  async sincronizar () {
    await this.consola('seed', { espera: /Seed: \[/ })
  }

  /** Líneas del log con errores o excepciones (H1 CA5), sin el ruido conocido de Paper. */
  errores ({ desde = 0, ignorar = [], ademas = [] } = {}) {
    return this.lineas.slice(desde).filter(l => (/(ERROR\]|Exception|SEVERE)/.test(l) || ademas.some(r => r.test(l))) &&
      !ignorar.some(r => r.test(l)))
  }

  async detener (timeoutMs = 60000) {
    if (!this.proceso || this.proceso.exitCode != null) return
    this.proceso.stdin.write('stop\n')
    const t = setTimeout(() => this.proceso.kill(), timeoutMs)
    await this.terminado
    clearTimeout(t)
    await new Promise(resolve => this.salida.end(resolve))
  }
}

/**
 * El jar del plugin más reciente de una carpeta (dist/ puede tener varias versiones). Regresión (005 F-01): se tomaba
 * el primero por orden alfabético y, al subir de 1.0.0 a 1.0.1, la suite probaba el jar viejo.
 */
export function jarMasNuevo (dir, patron) {
  const jars = fs.readdirSync(dir).filter(f => patron.test(f))
  jars.sort((a, b) => fs.statSync(path.join(dir, b)).mtimeMs - fs.statSync(path.join(dir, a)).mtimeMs)
  return jars[0]
}

export const dormir = (ms) => new Promise(resolve => setTimeout(resolve, ms))
