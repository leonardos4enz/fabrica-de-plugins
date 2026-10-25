// Jugador automático (spec 002, H3 CA2): conecta un bot de mineflayer y registra lo que ve, para esperar con
// tiempo límite un mensaje, una barra de acción, un título, un ítem o un estado del jugador.
import { createRequire } from 'node:module'
import { dormir } from './servidor.mjs'

const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')

export async function conectar (nombre, { puerto, version, timeoutMs = 30000 }) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: puerto, username: nombre, auth: 'offline', version, hideErrors: true })
  const j = new Jugador(bot, nombre)
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`${nombre} no entró en ${timeoutMs} ms`)), timeoutMs)
    bot.once('spawn', () => { clearTimeout(t); resolve() })
    bot.once('kicked', (r) => { clearTimeout(t); reject(new Error(`${nombre} expulsado al entrar: ${texto(r)}`)) })
    bot.once('error', (e) => { clearTimeout(t); reject(e) })
  })
  return j
}

/** Texto plano de un componente (JSON, NBT o cadena). */
function texto (c) {
  if (c == null) return ''
  if (typeof c === 'string') return c
  return c.toString?.() !== '[object Object]' ? c.toString() : JSON.stringify(c)
}

export class Jugador {
  constructor (bot, nombre) {
    this.bot = bot
    this.nombre = nombre
    /** Todo lo que el bot recibió, en orden: { tipo, texto, json, t } */
    this.vistos = []
    this.expulsion = null
    const ChatMessage = require('prismarine-chat')(bot.registry)
    const registrar = (tipo, txt, json) => this.vistos.push({ tipo, texto: txt, json, t: Date.now() })
    // Un mensaje de chat cambiado por el servidor (set_message) llega como contenido sin firmar: es lo que ve el jugador.
    bot.on('message', (msg, pos) => {
      const visto = msg.unsigned ?? msg
      registrar(pos === 'game_info' ? 'actionbar' : 'chat', visto.toString(), visto.json)
    })
    // Paper manda la barra de acción con su propio paquete (set_action_bar_text), que mineflayer no lee.
    bot._client.on('action_bar', (p) => {
      let m
      try { m = ChatMessage.fromNotch(p.text) } catch { m = null }
      registrar('actionbar', m ? m.toString() : texto(p.text), m?.json)
    })
    const titulo = (tipo) => (p) => {
      let m
      try { m = ChatMessage.fromNotch(p.text) } catch { m = null }
      registrar(tipo, m ? m.toString() : texto(p.text), m?.json)
    }
    bot._client.on('set_title_text', titulo('title'))
    bot._client.on('set_title_subtitle', titulo('subtitle'))
    bot.on('bossBarCreated', (b) => registrar('bossbar', texto(b.title), b))
    // Sonidos: la tabla de nombres de minecraft-data no coincide con el registro de 1.21.4 (nombra un sonido vecino),
    // así que se registra el id del paquete y el tono ("#id@tono"); las pruebas reconocen el sonido por su tono.
    const sonido = (p) => {
      const ref = p.sound ?? { soundId: p.soundId }
      registrar('sound', `${ref.data?.soundName ?? '#' + ref.soundId}@${Math.round(p.pitch * 100) / 100}`)
    }
    bot._client.on('sound_effect', sonido)
    bot._client.on('entity_sound_effect', sonido)
    bot.on('particle', (p) => registrar('particle', String(p.name ?? p.id)))
    bot._client.on('explosion', () => registrar('explosion', 'explosion'))
    bot._client.on('abilities', (p) => registrar('abilities', `flags=${p.flags}`, p))
    // Árbol de comandos (spec 005): el texto son los comandos de primer nivel, separados por espacios.
    bot._client.on('declare_commands', (p) => {
      const raiz = p.nodes?.[p.rootIndex]
      const nombres = (raiz?.children ?? []).map(i => p.nodes[i]?.extraNodeData?.name).filter(Boolean)
      registrar('comandos', ` ${nombres.join(' ')} `, p)
    })
    bot.on('entitySpawn', (e) => registrar('entity', String(e.name), e))
    bot.on('kicked', (r) => { this.expulsion = texto(r); registrar('kicked', this.expulsion, r) })
    bot.on('error', () => {})
  }

  marca () {
    return this.vistos.length
  }

  /** Espera algo visto desde una marca: tipo ('chat', 'actionbar', 'title', 'subtitle', 'bossbar', 'sound'…) y patrón. */
  async esperar (tipo, patron, { desde = 0, timeoutMs = 8000 } = {}) {
    const fin = Date.now() + timeoutMs
    let i = desde
    for (;;) {
      for (; i < this.vistos.length; i++) {
        const v = this.vistos[i]
        if (v.tipo === tipo && patron.test(v.texto)) return v
      }
      if (Date.now() > fin) {
        const recibidos = this.vistos.slice(desde).filter(v => v.tipo === tipo).map(v => v.texto).slice(-6)
        throw new Error(`${this.nombre} no recibió ${tipo} ${patron} en ${timeoutMs} ms. Recibió: ${JSON.stringify(recibidos)}`)
      }
      await dormir(50)
    }
  }

  /** Comprueba que NO llega nada que cumpla el patrón durante `ms`. */
  async nunca (tipo, patron, { desde = this.marca(), ms = 1500 } = {}) {
    await dormir(ms)
    const v = this.vistos.slice(desde).find(x => x.tipo === tipo && patron.test(x.texto))
    if (v) throw new Error(`${this.nombre} recibió ${tipo} que no debía llegar: ${JSON.stringify(v.texto)}`)
  }

  /** Espera hasta que la condición sobre el bot sea verdadera. */
  async hasta (que, cond, { timeoutMs = 8000 } = {}) {
    const fin = Date.now() + timeoutMs
    for (;;) {
      let ok = false
      try { ok = cond(this.bot) } catch { ok = false }
      if (ok) return
      if (Date.now() > fin) throw new Error(`${this.nombre}: no se cumplió «${que}» en ${timeoutMs} ms`)
      await dormir(50)
    }
  }

  chat (msg) {
    this.bot.chat(msg)
  }

  /** Comando del jugador (sin la barra). */
  comando (cmd) {
    this.bot.chat('/' + cmd)
  }

  items (nombre) {
    return this.bot.inventory.items().filter(i => !nombre || i.name === nombre)
  }

  cantidad (nombre) {
    return this.items(nombre).reduce((n, i) => n + i.count, 0)
  }

  /** Nombre personalizado de un ítem (componente custom_name en 1.20.5+), en texto plano. */
  nombreDe (item) {
    const ChatMessage = require('prismarine-chat')(this.bot.registry)
    const c = item?.customName ?? item?.components?.find?.(x => x.type === 'custom_name')?.data
    if (c == null) return null
    try { return ChatMessage.fromNotch(c).toString() } catch { return texto(c) }
  }

  /** Título de la ventana abierta (un menú, spec 013), en texto plano; null si no hay una. */
  tituloVentana () {
    const t = this.bot.currentWindow?.title
    if (t == null) return null
    const ChatMessage = require('prismarine-chat')(this.bot.registry)
    try { return ChatMessage.fromNotch(t).toString() } catch { return texto(t) }
  }

  /** Cuántos ítems de ese material tiene en su parte de la ventana abierta (o del inventario si no hay ventana). */
  cantidadEnVentana (nombre) {
    const w = this.bot.currentWindow
    if (!w) return this.cantidad(nombre)
    return w.slots.slice(w.inventoryStart).filter(i => i && i.name === nombre).reduce((n, i) => n + i.count, 0)
  }

  async salir () {
    if (this.expulsion != null || !this.bot._client.socket || this.bot._client.socket.destroyed) return
    const fin = new Promise(resolve => this.bot.once('end', resolve))
    this.bot.quit()
    await Promise.race([fin, dormir(3000)])
  }
}
