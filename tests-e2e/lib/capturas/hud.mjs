// HUD y pantallas de Minecraft 1.21.4 dibujados con node-canvas a partir de lo que recibió el bot (componentes de
// texto y estado) y los sprites del cliente instalado (spec 004, plan §1). Todo se dibuja en píxeles de GUI del juego
// multiplicados por `escala`; cada elemento deja su caja en `cajas` para recortar la imagen a la zona que importa.
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { createCanvas, loadImage } = require('canvas')

export const ANCHO = 427
export const ALTO = 240
export const ESCALA = 2

const COLORES = {
  black: '000000', dark_blue: '0000AA', dark_green: '00AA00', dark_aqua: '00AAAA', dark_red: 'AA0000',
  dark_purple: 'AA00AA', gold: 'FFAA00', gray: 'AAAAAA', dark_gray: '555555', blue: '5555FF', green: '55FF55',
  aqua: '55FFFF', red: 'FF5555', light_purple: 'FF55FF', yellow: 'FFFF55', white: 'FFFFFF'
}
const ESTILOS = ['bold', 'italic', 'underlined', 'strikethrough', 'obfuscated']
const verdadero = (v) => v === true || v === 1 || v === '1b' || v === 'true'

/** Componente de texto (JSON o NBT simplificado) → segmentos { texto, color, bold, italic… }, con traducciones. */
export function segmentos (c, idioma = {}, heredado = { color: 'FFFFFF' }) {
  const out = []
  const visitar = (n, est) => {
    if (n == null) return
    if (typeof n === 'string' || typeof n === 'number' || typeof n === 'boolean') { out.push({ ...est, texto: String(n) }); return }
    if (Array.isArray(n)) { n.forEach(x => visitar(x, est)); return }
    const e = { ...est }
    if (typeof n.color === 'string') e.color = n.color.startsWith('#') ? n.color.slice(1).toUpperCase() : (COLORES[n.color] ?? e.color)
    for (const k of ESTILOS) if (n[k] != null) e[k] = verdadero(n[k])
    const texto = n.text ?? n[''] ?? ''
    if (texto !== '') out.push({ ...e, texto: String(texto) })
    if (n.translate) {
      const fmt = idioma[n.translate] ?? n.fallback ?? n.translate
      const args = n.with ?? []
      let i = 0
      let ultimo = 0
      const re = /%(?:(\d+)\$)?([sd%])/g
      let m
      while ((m = re.exec(fmt))) {
        if (m.index > ultimo) out.push({ ...e, texto: fmt.slice(ultimo, m.index) })
        if (m[2] === '%') out.push({ ...e, texto: '%' })
        else visitar(args[m[1] ? Number(m[1]) - 1 : i++], e)
        ultimo = re.lastIndex
      }
      if (ultimo < fmt.length) out.push({ ...e, texto: fmt.slice(ultimo) })
    }
    if (n.extra) visitar(n.extra, e)
  }
  visitar(c, heredado)
  return out.filter(s => s.texto.length)
}

/** Texto plano de los segmentos (para el texto alternativo de la galería). */
export const plano = (segs) => segs.map(s => s.texto).join('')

/** Nombre del efecto de minecraft-data ('InstantHealth') → archivo de textura ('instant_health'). */
export const archivoDeEfecto = (nombre) => nombre.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase()

export class Hud {
  static async crear ({ dir, idioma }, fuente) {
    const h = new Hud()
    h.dir = dir
    h.fuente = fuente
    h.idioma = JSON.parse(fs.readFileSync(path.join(dir, 'assets/minecraft/lang', `${idioma}.json`), 'utf8'))
    h.imagenes = new Map()
    return h
  }

  async imagen (rel) {
    if (!this.imagenes.has(rel)) {
      const f = path.join(this.dir, 'assets/minecraft/textures', rel + '.png')
      this.imagenes.set(rel, fs.existsSync(f) ? await loadImage(f) : null)
    }
    return this.imagenes.get(rel)
  }

  seg (json, heredado) {
    return segmentos(json, this.idioma, heredado)
  }

  traducir (clave) {
    return this.idioma[clave] ?? clave
  }

  // ───────────── texto ─────────────

  /** Texto con sombra en (x, y) de GUI; devuelve la x final. */
  texto (g, segs, x, y, { sombra = true } = {}) {
    if (sombra) this._pasada(g, segs, x + 1, y + 1, true)
    return this._pasada(g, segs, x, y, false)
  }

  _pasada (g, segs, x0, y0, sombra) {
    let x = x0
    for (const s of segs) {
      const rgb = parseInt(s.color ?? 'FFFFFF', 16)
      let r = (rgb >> 16) & 255
      let v = (rgb >> 8) & 255
      let b = rgb & 255
      if (sombra) { r = Math.floor(r / 4); v = Math.floor(v / 4); b = Math.floor(b / 4) }
      g.fillStyle = `rgb(${r},${v},${b})`
      for (const car of s.texto) {
        const gl = this.fuente.glifo(car.codePointAt(0))
        const arriba = y0 + 7 - gl.ascenso
        const dibujar = (ox) => {
          gl.px.forEach((fila, py) => fila.forEach((on, px) => {
            if (!on) return
            const inclinacion = s.italic ? (1 - (py * gl.escala) / 7) * 1.5 : 0
            g.fillRect(x + ox + inclinacion + (px + gl.dx) * gl.escala, arriba + py * gl.escala, gl.escala, gl.escala)
          }))
        }
        dibujar(0)
        if (s.bold) dibujar(1)
        const av = gl.avance + (s.bold ? 1 : 0)
        if (s.underlined) g.fillRect(x - 1, y0 + 8, av + 1, 1)
        if (s.strikethrough) g.fillRect(x - 1, y0 + 3.5, av + 1, 1)
        x += av
      }
    }
    return x
  }

  /** Corta segmentos en líneas de como mucho `ancho` píxeles de GUI, por palabras y en los saltos de línea. */
  envolver (segs, ancho) {
    const lineas = [[]]
    let w = 0
    for (const s of segs) {
      const partes = s.texto.split('\n')
      partes.forEach((parte, i) => {
        if (i > 0) { lineas.push([]); w = 0 }
        for (const palabra of parte.split(/(?<= )/)) {
          if (!palabra) continue
          const pw = this.fuente.ancho([{ ...s, texto: palabra }])
          if (w + pw > ancho && w > 0) { lineas.push([]); w = 0 }
          lineas.at(-1).push({ ...s, texto: palabra })
          w += pw
        }
      })
    }
    return lineas
  }

  // ───────────── ítems ─────────────

  /** Icono de 16×16 de un ítem: su textura de ítem o, si es un bloque entero, el cubo isométrico. */
  async icono (g, nombre, x, y) {
    const it = await this.imagen('item/' + nombre)
    if (it) { g.drawImage(it, 0, 0, 16, 16, x, y, 16, 16); return true }
    const arriba = await this.imagen('block/' + nombre + '_top') ?? await this.imagen('block/' + nombre)
    const lado = await this.imagen('block/' + nombre + '_side') ?? await this.imagen('block/' + nombre)
    if (!arriba || !lado) return false
    // Cubo isométrico como el del inventario: cara de arriba y dos laterales sombreadas.
    const cara = (img, m, sombra) => {
      g.save()
      g.translate(x, y)
      g.transform(...m)
      g.drawImage(img, 0, 0, 16, 16, 0, 0, 1, 1)
      if (sombra) { g.fillStyle = `rgba(0,0,0,${sombra})`; g.fillRect(0, 0, 1, 1) }
      g.restore()
    }
    cara(arriba, [8, 4, -8, 4, 8, 0])
    cara(lado, [8, 4, 0, 8, 0, 4], 0.2)
    cara(lado, [8, -4, 0, 8, 8, 8], 0.4)
    return true
  }

  async ranura (g, item, x, y) {
    if (!item) return
    await this.icono(g, item.nombre, x, y)
    if (item.cuenta > 1) {
      const t = [{ texto: String(item.cuenta), color: 'FFFFFF' }]
      this.texto(g, t, x + 17 - this.fuente.ancho(t), y + 9)
    }
  }

  /** Líneas del tooltip de un ítem: nombre (cursiva si es personalizado) y lore (morado en cursiva). */
  lineasDeTooltip (item) {
    const lineas = []
    if (item.nombreJson != null) lineas.push(this.seg(item.nombreJson, { color: 'FFFFFF', italic: true }))
    else lineas.push([{ texto: this.traducir(`item.minecraft.${item.nombre}`) === `item.minecraft.${item.nombre}` ? this.traducir(`block.minecraft.${item.nombre}`) : this.traducir(`item.minecraft.${item.nombre}`), color: 'FFFFFF' }])
    for (const e of item.encantamientos ?? []) {
      const nivel = e.nivel > 1 ? ' ' + this.traducir(`enchantment.level.${e.nivel}`) : ''
      lineas.push([{ texto: this.traducir(`enchantment.minecraft.${e.nombre}`) + nivel, color: 'AAAAAA' }])
    }
    for (const l of item.lore ?? []) lineas.push(this.seg(l, { color: 'AA00AA', italic: true }))
    if (item.irrompible) lineas.push([{ texto: this.traducir('item.unbreakable'), color: '5555FF' }])
    return lineas
  }

  tooltip (g, lineas, x, y) {
    const w = Math.max(...lineas.map(l => this.fuente.ancho(l)))
    const h = lineas.length === 1 ? 8 : 8 + 2 + (lineas.length - 1) * 10
    const x0 = x + 12
    const y0 = y - 12
    g.fillStyle = 'rgba(16,0,16,0.94)'
    g.fillRect(x0 - 3, y0 - 4, w + 6, 1)
    g.fillRect(x0 - 3, y0 + h + 3, w + 6, 1)
    g.fillRect(x0 - 3, y0 - 3, w + 6, h + 6)
    g.fillRect(x0 - 4, y0 - 3, 1, h + 6)
    g.fillRect(x0 + w + 3, y0 - 3, 1, h + 6)
    const borde = g.createLinearGradient(0, y0 - 3, 0, y0 + h + 3)
    borde.addColorStop(0, 'rgba(80,0,255,0.31)')
    borde.addColorStop(1, 'rgba(40,0,127,0.31)')
    g.fillStyle = borde
    g.fillRect(x0 - 3, y0 - 3, 1, h + 6)
    g.fillRect(x0 + w + 2, y0 - 3, 1, h + 6)
    g.fillRect(x0 - 3, y0 - 3, w + 6, 1)
    g.fillRect(x0 - 3, y0 + h + 2, w + 6, 1)
    let yy = y0
    lineas.forEach((l, i) => {
      this.texto(g, l, x0, yy)
      yy += i === 0 ? 12 : 10
    })
    return { x: x0 - 4, y: y0 - 4, w: w + 8, h: h + 8 }
  }

  // ───────────── escena ─────────────

  /**
   * Dibuja una escena y devuelve { lienzo, cajas }. `e`: { fondo (Image), chat: [json], chatAbierto, actionbar,
   * titulo, subtitulo, bossbars: [{ titulo, color, progreso }], jugador: { vida, comida, nivel, xp, modo, seleccion,
   * hotbar[9], efectos: [{ nombre, bueno }] }, gui: { tipo: 'inventario'|'cofre', titulo, filas, ranuras, raton },
   * desconexion: { titulo, motivo }, consola: [línea] }
   */
  async dibujar (e) {
    const c = createCanvas(ANCHO * ESCALA, ALTO * ESCALA)
    const g = c.getContext('2d')
    g.imageSmoothingEnabled = false
    const cajas = {}
    if (e.consola) return { lienzo: await this._consola(c, g, e.consola, cajas), cajas }
    if (e.fondo) g.drawImage(e.fondo, 0, 0, c.width, c.height)
    g.scale(ESCALA, ESCALA)
    if (e.desconexion) { await this._desconexion(g, e.desconexion, cajas); return { lienzo: c, cajas } }

    const cx = Math.floor(ANCHO / 2)
    const j = e.jugador
    if (j && !e.gui) {
      await this._hud(g, j, cx, cajas)
      await this._efectos(g, j.efectos ?? [], cajas)
    }
    if (!e.gui) {
      let by = 12
      for (const b of e.bossbars ?? []) {
        const color = (b.color ?? 'pink').toLowerCase()
        const fondo = await this.imagen(`gui/sprites/boss_bar/${color}_background`)
        const barra = await this.imagen(`gui/sprites/boss_bar/${color}_progress`)
        if (fondo) g.drawImage(fondo, cx - 91, by)
        const p = Math.round((b.progreso ?? 1) * 182)
        if (barra && p > 0) g.drawImage(barra, 0, 0, p, 5, cx - 91, by, p, 5)
        const t = this.seg(b.titulo)
        const w = this.fuente.ancho(t)
        this.texto(g, t, cx - w / 2, by - 9)
        unir(cajas, 'bossbar', { x: Math.min(cx - 91, cx - w / 2), y: by - 10, w: Math.max(182, w), h: 16 })
        by += 19
      }
    }

    if (e.chat?.length && !e.gui) {
      const lineas = e.chat.flatMap(m => this.envolver(this.seg(m), 320)).slice(e.chatAbierto ? -20 : -10)
      let y = ALTO - 40
      for (const l of [...lineas].reverse()) {
        g.fillStyle = 'rgba(0,0,0,0.5)'
        g.fillRect(0, y - 9, 328, 9)
        this.texto(g, l, 4, y - 8)
        y -= 9
      }
      unir(cajas, 'chat', { x: 0, y, w: 328, h: ALTO - 40 - y })
    }

    if (e.actionbar && !e.gui) {
      const t = this.seg(e.actionbar)
      const w = this.fuente.ancho(t)
      const y = ALTO - 68 + (j && j.modo !== 'survival' && j.modo !== 'adventure' ? 7 : 0)
      this.texto(g, t, cx - w / 2, y)
      unir(cajas, 'barra', { x: cx - w / 2 - 2, y: y - 2, w: w + 4, h: 12 })
    }

    if ((e.titulo || e.subtitulo) && !e.gui) {
      const cy = ALTO / 2
      if (e.titulo) {
        const t = this.seg(e.titulo)
        const w = this.fuente.ancho(t)
        g.save(); g.translate(cx, cy); g.scale(4, 4)
        this.texto(g, t, -w / 2, -10)
        g.restore()
        unir(cajas, 'titulo', { x: cx - w * 2 - 4, y: cy - 44, w: w * 4 + 8, h: 40 })
      }
      if (e.subtitulo) {
        const t = this.seg(e.subtitulo)
        const w = this.fuente.ancho(t)
        g.save(); g.translate(cx, cy); g.scale(2, 2)
        this.texto(g, t, -w / 2, 5)
        g.restore()
        unir(cajas, 'titulo', { x: cx - w - 4, y: cy + 6, w: w * 2 + 8, h: 22 })
      }
    }

    if (e.gui) await this._gui(g, e.gui, cx, cajas)
    return { lienzo: c, cajas }
  }

  async _hud (g, j, cx, cajas) {
    g.drawImage(await this.imagen('gui/sprites/hud/hotbar'), cx - 91, ALTO - 22)
    for (let i = 0; i < 9; i++) await this.ranura(g, j.hotbar?.[i], cx - 88 + i * 20, ALTO - 19)
    g.drawImage(await this.imagen('gui/sprites/hud/hotbar_selection'), cx - 92 + (j.seleccion ?? 0) * 20, ALTO - 23)
    unir(cajas, 'hud', { x: cx - 92, y: ALTO - 23, w: 184, h: 23 })
    if (j.manoIzq) {
      g.drawImage(await this.imagen('gui/sprites/hud/hotbar_offhand_left'), cx - 91 - 29, ALTO - 23)
      await this.ranura(g, j.manoIzq, cx - 91 - 26, ALTO - 19)
      unir(cajas, 'hud', { x: cx - 91 - 29, y: ALTO - 23, w: 29, h: 23 })
    }
    if (j.modo !== 'survival' && j.modo !== 'adventure') return
    g.drawImage(await this.imagen('gui/sprites/hud/experience_bar_background'), cx - 91, ALTO - 29)
    const p = Math.floor((j.xp ?? 0) * 183)
    if (p > 0) g.drawImage(await this.imagen('gui/sprites/hud/experience_bar_progress'), 0, 0, p, 5, cx - 91, ALTO - 29, p, 5)
    if (j.nivel > 0) {
      const t = String(j.nivel)
      const x = cx - this.fuente.ancho([{ texto: t }]) / 2
      const y = ALTO - 35
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) this._pasada(g, [{ texto: t, color: '000000' }], x + dx, y + dy, false)
      this._pasada(g, [{ texto: t, color: '80FF20' }], x, y, false)
    }
    const vida = Math.ceil(j.vida ?? 20)
    const tipo = j.efectos?.some(e => e.nombre === 'poison') ? 'poisoned_' : j.efectos?.some(e => e.nombre === 'wither') ? 'withered_' : ''
    for (let i = 0; i < 10; i++) {
      const x = cx - 91 + i * 8
      g.drawImage(await this.imagen('gui/sprites/hud/heart/container'), x, ALTO - 39)
      if (vida >= i * 2 + 2) g.drawImage(await this.imagen(`gui/sprites/hud/heart/${tipo}full`), x, ALTO - 39)
      else if (vida === i * 2 + 1) g.drawImage(await this.imagen(`gui/sprites/hud/heart/${tipo}half`), x, ALTO - 39)
    }
    if (j.armadura > 0) {
      for (let i = 0; i < 10; i++) {
        const sprite = i * 2 + 1 < j.armadura ? 'armor_full' : i * 2 + 1 === j.armadura ? 'armor_half' : 'armor_empty'
        g.drawImage(await this.imagen('gui/sprites/hud/' + sprite), cx - 91 + i * 8, ALTO - 49)
      }
      unir(cajas, 'hud', { x: cx - 92, y: ALTO - 50, w: 184, h: 50 })
    }
    const comida = j.comida ?? 20
    for (let i = 0; i < 10; i++) {
      const x = cx + 91 - i * 8 - 9
      g.drawImage(await this.imagen('gui/sprites/hud/food_empty'), x, ALTO - 39)
      if (comida >= i * 2 + 2) g.drawImage(await this.imagen('gui/sprites/hud/food_full'), x, ALTO - 39)
      else if (comida === i * 2 + 1) g.drawImage(await this.imagen('gui/sprites/hud/food_half'), x, ALTO - 39)
    }
    unir(cajas, 'hud', { x: cx - 92, y: ALTO - 40, w: 184, h: 40 })
  }

  /** Efectos arriba a la derecha: los buenos en la primera fila, los malos en la segunda. */
  async _efectos (g, efectos, cajas) {
    let buenos = 0
    let malos = 0
    for (const ef of efectos) {
      const i = ef.bueno ? buenos++ : malos++
      const x = ANCHO - 25 * (i + 1)
      const y = ef.bueno ? 1 : 27
      g.drawImage(await this.imagen('gui/sprites/hud/effect_background'), x, y)
      const icono = await this.imagen('mob_effect/' + ef.nombre)
      if (icono) g.drawImage(icono, x + 3, y + 3, 18, 18)
      unir(cajas, 'efectos', { x, y, w: 24, h: 24 })
    }
  }

  async _gui (g, gui, cx, cajas) {
    // Con una pantalla abierta el juego oscurece el mundo con un degradado.
    const d = g.createLinearGradient(0, 0, 0, ALTO)
    d.addColorStop(0, 'rgba(16,16,16,0.75)')
    d.addColorStop(1, 'rgba(16,16,16,0.82)')
    g.fillStyle = d
    g.fillRect(0, 0, ANCHO, ALTO)
    let x0, y0, w, h
    const ranuras = []
    if (gui.tipo === 'cofre') {
      const filas = gui.filas ?? 3
      w = 176
      h = 114 + filas * 18
      x0 = cx - w / 2
      y0 = Math.floor((ALTO - h) / 2)
      const tex = await this.imagen('gui/container/generic_54')
      g.drawImage(tex, 0, 0, w, filas * 18 + 17, x0, y0, w, filas * 18 + 17)
      g.drawImage(tex, 0, 126, w, 96, x0, y0 + filas * 18 + 17, w, 96)
      this.texto(g, this.seg(gui.titulo ?? { translate: 'container.chest' }, { color: '404040' }), x0 + 8, y0 + 6, { sombra: false })
      this.texto(g, [{ texto: this.traducir('container.inventory'), color: '404040' }], x0 + 8, y0 + h - 94, { sombra: false })
      for (let i = 0; i < filas * 9; i++) ranuras.push([x0 + 8 + (i % 9) * 18, y0 + 18 + Math.floor(i / 9) * 18])
      for (let i = 0; i < 27; i++) ranuras.push([x0 + 8 + (i % 9) * 18, y0 + filas * 18 + 31 + Math.floor(i / 9) * 18])
      for (let i = 0; i < 9; i++) ranuras.push([x0 + 8 + i * 18, y0 + filas * 18 + 89])
    } else {
      w = 176
      h = 166
      x0 = cx - w / 2
      y0 = Math.floor((ALTO - h) / 2)
      g.drawImage(await this.imagen('gui/container/inventory'), 0, 0, w, h, x0, y0, w, h)
      this.texto(g, [{ texto: this.traducir('container.crafting'), color: '404040' }], x0 + 97, y0 + 6, { sombra: false })
      // Ranuras en el orden de la ventana del jugador: 0 resultado, 1–4 crafteo, 5–8 armadura, 9–35, 36–44, 45 mano izq.
      ranuras.push([x0 + 154, y0 + 28])
      for (let i = 0; i < 4; i++) ranuras.push([x0 + 98 + (i % 2) * 18, y0 + 18 + Math.floor(i / 2) * 18])
      for (let i = 0; i < 4; i++) ranuras.push([x0 + 8, y0 + 8 + i * 18])
      for (let i = 0; i < 27; i++) ranuras.push([x0 + 8 + (i % 9) * 18, y0 + 84 + Math.floor(i / 9) * 18])
      for (let i = 0; i < 9; i++) ranuras.push([x0 + 8 + i * 18, y0 + 142])
      ranuras.push([x0 + 77, y0 + 62])
    }
    for (let i = 0; i < ranuras.length; i++) await this.ranura(g, gui.ranuras?.[i], ranuras[i][0], ranuras[i][1])
    unir(cajas, 'gui', { x: x0, y: y0, w, h })
    // Ratón sobre una ranura: se resalta y se muestra su tooltip, como al pasar el cursor.
    if (gui.raton != null && gui.ranuras?.[gui.raton]) {
      const [rx, ry] = ranuras[gui.raton]
      g.fillStyle = 'rgba(255,255,255,0.5)'
      g.fillRect(rx, ry, 16, 16)
      unir(cajas, 'gui', this.tooltip(g, this.lineasDeTooltip(gui.ranuras[gui.raton]), rx + 8, ry + 8))
    }
  }

  async _desconexion (g, { titulo, motivo }, cajas) {
    const fondo = await this.imagen('gui/menu_background')
    for (let y = 0; y < ALTO; y += 32) for (let x = 0; x < ANCHO; x += 32) g.drawImage(fondo, x, y, 32, 32)
    const t = this.seg(titulo, { color: 'AAAAAA' })
    const lineas = this.envolver(this.seg(motivo), ANCHO - 50)
    const alto = 9 * lineas.length
    let y = Math.floor((ALTO - alto) / 2 - 9 * 2) - 12
    this.texto(g, t, ANCHO / 2 - this.fuente.ancho(t) / 2, y)
    y += 9 * 2 + 4
    for (const l of lineas) { this.texto(g, l, ANCHO / 2 - this.fuente.ancho(l) / 2, y); y += 9 }
    const boton = await this.imagen('gui/sprites/widget/button')
    y = Math.min(ALTO - 30, y + 12)
    g.drawImage(boton, ANCHO / 2 - 100, y)
    const b = [{ texto: this.traducir('gui.toMenu'), color: 'FFFFFF' }]
    this.texto(g, b, ANCHO / 2 - this.fuente.ancho(b) / 2, y + 6)
    unir(cajas, 'pantalla', { x: 0, y: 0, w: ANCHO, h: ALTO })
  }

  /** Líneas del log del servidor (no es algo que vea un jugador: la galería lo dice). */
  async _consola (c, g, lineas, cajas) {
    g.fillStyle = '#0c0c0c'
    g.fillRect(0, 0, c.width, c.height)
    g.scale(ESCALA, ESCALA)
    let y = 6
    for (const l of lineas.slice(-24)) {
      for (const parte of this.envolver([{ texto: l, color: 'CCCCCC' }], ANCHO - 12)) {
        this.texto(g, parte, 6, y, { sombra: false })
        y += 10
      }
    }
    unir(cajas, 'consola', { x: 0, y: 0, w: ANCHO, h: Math.min(ALTO, y + 4) })
    return c
  }
}

function unir (cajas, nombre, r) {
  const a = cajas[nombre]
  if (!a) { cajas[nombre] = { ...r }; return }
  const x = Math.min(a.x, r.x)
  const y = Math.min(a.y, r.y)
  a.w = Math.max(a.x + a.w, r.x + r.w) - x
  a.h = Math.max(a.y + a.h, r.y + r.h) - y
  a.x = x
  a.y = y
}

/**
 * Recorta el lienzo a la unión de las cajas pedidas (más un margen), en píxeles de la imagen. `zonas`: lista de nombres
 * de caja o 'completa'. Un recorte nunca queda más angosto que `minimo` píxeles de GUI (para que se vea el mundo).
 */
export function recortar (lienzo, cajas, zonas, { margen = 8, minimo = 200 } = {}) {
  if (!zonas || zonas === 'completa') return lienzo
  // Solo el HUD: margen chico arriba, para no mostrar media línea del chat que está justo encima.
  const soloHud = [].concat(zonas).every(z => z === 'hud')
  let r = null
  for (const z of [].concat(zonas)) {
    const b = cajas[z]
    if (!b) continue
    if (!r) r = { ...b }
    else {
      const x = Math.min(r.x, b.x)
      const y = Math.min(r.y, b.y)
      r.w = Math.max(r.x + r.w, b.x + b.w) - x
      r.h = Math.max(r.y + r.h, b.y + b.h) - y
      r.x = x
      r.y = y
    }
  }
  if (!r) return lienzo
  let x0 = Math.max(0, r.x - margen)
  let x1 = Math.min(ANCHO, r.x + r.w + margen)
  if (x1 - x0 < minimo) {
    const falta = minimo - (x1 - x0)
    x0 = Math.max(0, x0 - falta / 2)
    x1 = Math.min(ANCHO, x0 + minimo)
  }
  const y0 = Math.max(0, r.y - (soloHud ? 2 : margen))
  let y1 = Math.min(ALTO, r.y + r.h + margen)
  // El chat termina justo sobre la vida y la comida: sin el HUD, el recorte no corta los corazones por la mitad.
  const z = [].concat(zonas)
  if (cajas.chat && z.includes('chat') && !z.includes('hud') && cajas.chat.y + cajas.chat.h >= r.y + r.h) y1 = Math.min(y1, cajas.chat.y + cajas.chat.h + 1)
  const c = createCanvas(Math.round((x1 - x0) * ESCALA), Math.round((y1 - y0) * ESCALA))
  c.getContext('2d').drawImage(lienzo, Math.round(x0 * ESCALA), Math.round(y0 * ESCALA), c.width, c.height, 0, 0, c.width, c.height)
  return c
}
