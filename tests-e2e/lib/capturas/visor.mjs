// Vista 3D desde los ojos de un bot (spec 004, H2 CA2): prismarine-viewer arma el mundo con los chunks y entidades que
// recibió el bot, y Chrome sin interfaz (puppeteer-core con el navegador instalado) lo dibuja con WebGL por software.
// Como el adaptador de mineflayer de prismarine-viewer, pero escucha solo en 127.0.0.1 y no manda las entidades que
// el visor no sabe dibujar (las pintaría como cubos magenta: ítems tirados, relámpagos, jugadores).
import fs from 'node:fs'
import http from 'node:http'
import { createRequire } from 'node:module'
import { ANCHO, ALTO, ESCALA } from './hud.mjs'
const require = createRequire(import.meta.url)

export function buscarNavegador () {
  const candidatos = [
    process.env.FABRICA_CHROME,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'
  ].filter(Boolean)
  const n = candidatos.find(c => fs.existsSync(c))
  if (!n) throw new Error('Las capturas necesitan Chrome o Edge instalado (o FABRICA_CHROME con la ruta del ejecutable).')
  return n
}

export async function abrirNavegador () {
  const puppeteer = require('puppeteer-core')
  return puppeteer.launch({
    executablePath: buscarNavegador(),
    headless: true,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-gpu-vsync', '--mute-audio']
  })
}

export class Camara {
  constructor (bot, puerto) {
    this.bot = bot
    this.puerto = puerto
  }

  async abrir (navegador) {
    const express = require('express')
    const { WorldView } = require('prismarine-viewer/viewer')
    const { setupRoutes } = require('prismarine-viewer/lib/common')
    const modelos = require('prismarine-viewer/viewer/lib/entity/entities.json')
    const dibujable = (nombre) => nombre !== 'player' && nombre in modelos
    const app = express()
    this.http = http.createServer(app)
    const io = require('socket.io')(this.http, { path: '/socket.io' })
    setupRoutes(app, '')
    io.on('connection', (socket) => {
      const bot = this.bot // el de ahora: después de una reconexión, la página recargada usa el bot nuevo
      socket.emit('version', bot.version)
      const emitir = socket.emit.bind(socket)
      const filtrado = {
        emit (evento, datos) {
          if (evento === 'entity' && datos.name !== undefined && !dibujable(datos.name)) return
          return emitir(evento, datos)
        },
        on: socket.on.bind(socket)
      }
      const vista = new WorldView(bot.world, 3, bot.entity.position, filtrado)
      vista.init(bot.entity.position)
      const posicion = () => {
        emitir('position', { pos: bot.entity.position, yaw: bot.entity.yaw, pitch: bot.entity.pitch, addMesh: true })
        vista.updatePosition(bot.entity.position)
      }
      bot.on('move', posicion)
      vista.listenToBot(bot)
      posicion()
      socket.on('disconnect', () => { bot.removeListener('move', posicion); vista.removeListenersFromBot(bot) })
    })
    await new Promise(resolve => this.http.listen(this.puerto, '127.0.0.1', resolve))
    this.pagina = await navegador.newPage()
    await this.pagina.setViewport({ width: ANCHO * ESCALA, height: ALTO * ESCALA })
    await this.pagina.goto(`http://127.0.0.1:${this.puerto}`)
  }

  /**
   * Recarga la vista (vuelve a pedir los chunks) después de un cambio de mundo o una reconexión del bot. No cierra el
   * servidor del visor: esperar a que cierre con la página conectada lo colgaba.
   */
  async recargar (bot = this.bot) {
    this.bot = bot
    await this.pagina.reload({ waitUntil: 'load' })
  }

  async foto () {
    return this.pagina.screenshot({ type: 'png' })
  }

  async cerrarServidor () {
    if (!this.http) return
    await this.pagina?.close().catch(() => {})
    this.http.closeAllConnections?.()
    await Promise.race([new Promise(resolve => this.http.close(resolve)), new Promise(resolve => setTimeout(resolve, 2000))])
    this.http = null
  }
}
