// Jugador de Bedrock para la suite (spec 013): un cliente de bedrock-protocol que entra por Geyser + Floodgate sin cuenta
// de Xbox (Geyser acepta logins sin firmar solo en el servidor de prueba, en 127.0.0.1; ver configurarGeyser). Solo
// hace lo que la suite necesita: recibir formularios (modal_form_request) y contestarlos.
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)

/** Versión de Bedrock que soporta el Geyser de cada versión de Paper (lib/plugins-extra.mjs). */
export const VERSION_BEDROCK = { '1.21.4': '1.21.70', '26.1.2': '1.26.30' }

const dormir = ms => new Promise(resolve => setTimeout(resolve, ms))

export class JugadorBedrock {
  constructor (cliente, nombre) {
    this.cliente = cliente
    this.nombre = nombre
    /** Formularios recibidos, en orden: { id, form (JSON ya leído), t }. */
    this.formularios = []
    this.chat = []
    cliente.on('modal_form_request', p => {
      let form
      try {
        form = JSON.parse(p.data)
      } catch {
        form = { raw: p.data }
      }
      this.formularios.push({ id: p.form_id, form, t: Date.now() })
    })
    cliente.on('text', p => this.chat.push({ texto: String(p.message ?? ''), t: Date.now() }))
  }

  marca () {
    return this.formularios.length
  }

  /** Espera un formulario recibido después de la marca que cumpla el filtro. */
  async esperarFormulario (filtro = () => true, { desde = 0, timeoutMs = 8000 } = {}) {
    const fin = Date.now() + timeoutMs
    while (Date.now() < fin) {
      const f = this.formularios.slice(desde).find(x => filtro(x.form))
      if (f) return f
      await dormir(50)
    }
    const vistos = this.formularios.slice(desde).map(x => JSON.stringify(x.form).slice(0, 200))
    throw new Error(`${this.nombre} no recibió el formulario esperado en ${timeoutMs} ms; recibió: ${vistos.join(' | ') || '(nada)'}`)
  }

  /** Espera un mensaje de chat (Geyser lo manda como paquete text) recibido desde el índice dado. */
  async esperarChat (patron, { desde = 0, timeoutMs = 8000 } = {}) {
    const fin = Date.now() + timeoutMs
    while (Date.now() < fin) {
      const m = this.chat.slice(desde).find(x => patron.test(x.texto.replace(/§./g, '')))
      if (m) return m
      await dormir(50)
    }
    throw new Error(`${this.nombre} no recibió el chat ${patron}; recibió: ${JSON.stringify(this.chat.slice(desde).map(x => x.texto).slice(-6))}`)
  }

  /** Contesta un formulario de botones con el índice del botón (null = cerrarlo). */
  responder (formulario, boton) {
    const cerrar = boton == null
    this.cliente.queue('modal_form_response', {
      form_id: formulario.id,
      has_response_data: !cerrar,
      data: cerrar ? undefined : JSON.stringify(boton),
      has_cancel_reason: cerrar,
      cancel_reason: cerrar ? 'closed' : undefined
    })
  }

  async salir () {
    try {
      this.cliente.disconnect()
    } catch {}
    await dormir(300)
  }
}

/** Conecta un jugador de Bedrock a Geyser y espera a que aparezca en el mundo. */
export async function conectarBedrock (nombre, { puerto, version, timeoutMs = 30000 }) {
  const { createClient } = require('bedrock-protocol')
  const cliente = createClient({ host: '127.0.0.1', port: puerto, username: nombre, offline: true, version, skipPing: true })
  const jugador = new JugadorBedrock(cliente, nombre)
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`${nombre} (Bedrock ${version}) no entró en ${timeoutMs} ms`)), timeoutMs)
    cliente.once('spawn', () => { clearTimeout(t); resolve() })
    cliente.once('error', e => { clearTimeout(t); reject(e) })
    cliente.once('kick', p => { clearTimeout(t); reject(new Error(`${nombre} fue expulsado: ${JSON.stringify(p)}`)) })
  })
  return jugador
}
