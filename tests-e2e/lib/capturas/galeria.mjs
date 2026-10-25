// Galería generada (spec 004, H3 CA4): por grupo, cada capacidad con su imagen, la regla que la produjo y el texto
// alternativo. Se escribe desde las escenas, así que nunca queda una capacidad sin su entrada ni una imagen huérfana.
import fs from 'node:fs'
import path from 'node:path'

/** YAML de una regla (clave de primer nivel) en los archivos de las carpetas dadas, sin los comentarios de la siguiente. */
export function yamlDeRegla (nombre, carpetas) {
  for (const dir of carpetas) {
    for (const f of fs.readdirSync(dir).filter(x => /\.ya?ml$/.test(x)).sort()) {
      const lineas = fs.readFileSync(path.join(dir, f), 'utf8').split(/\r?\n/)
      const i = lineas.findIndex(l => l === `${nombre}:`)
      if (i < 0) continue
      let j = i + 1
      while (j < lineas.length && !/^[A-Za-z0-9_-]+:/.test(lineas[j])) j++
      while (j > i + 1 && (/^\s*$/.test(lineas[j - 1]) || /^#/.test(lineas[j - 1]))) j--
      return { archivo: path.join(dir, f), yaml: lineas.slice(i, j).join('\n') }
    }
  }
  return null
}

/**
 * @param {object} o
 * @param {string} o.archivo destino (docs/<plugin>/galeria.md)
 * @param {string} o.titulo nombre del plugin
 * @param {string} o.imagenes carpeta de las imágenes
 * @param {string[]} o.grupos orden de los grupos
 * @param {object[]} o.escenas escenas con su resultado ({ id, grupo, titulo, texto, regla, comando, alt, soloTexto })
 * @param {string[]} o.reglas carpetas con el YAML de las reglas
 * @param {string} o.raiz raíz del repositorio (para rutas relativas)
 * @param {string} o.version versión de Minecraft del render
 */
export function escribirGaleria (o) {
  const rel = (p) => path.relative(path.dirname(o.archivo), p).split(path.sep).join('/')
  const out = []
  out.push(`# Galería de ${o.titulo}`, '')
  out.push(`Una imagen por capacidad de ${o.titulo}: qué ve un jugador cuando una regla la usa. **No son capturas del ` +
    `cliente oficial**: cada imagen es un render de lo que recibió del servidor un cliente real (un bot de mineflayer ` +
    `conectado a Paper ${o.version} con el plugin), dibujado con la fuente, el HUD y el idioma (es_mx) de Minecraft ` +
    `${o.version}; el mundo es la vista 3D de prismarine-viewer desde los ojos del bot. Lo que esa vista no puede ` +
    `mostrar con fidelidad (relámpagos, partículas, sonidos, ítems tirados) queda como texto.`, '')
  out.push('Se regenera sola: `npm --prefix tests-e2e run capturas` (spec [004](../sdd/specs/004-fabrica-capturas-en-juego/spec.md)). ' +
    'No edites este archivo a mano.', '')
  const grupos = [...new Set([...o.grupos, ...o.escenas.map(e => e.grupo)])]
  out.push('## Índice', '')
  for (const gr of grupos) {
    const es = o.escenas.filter(e => e.grupo === gr)
    if (!es.length) continue
    out.push(`- **[${gr}](#${ancla(gr)})**: ${es.map(e => `[${e.titulo}](#${e.id})`).join(' · ')}`)
  }
  out.push('')
  // Por nombre: una capacidad puede mostrarse en la escena de otra (player_join en el ejemplo bienvenida).
  const tipos = { trigger: 'Disparadores', action: 'Acciones', command: 'Subcomandos de /omni', example: 'Reglas de ejemplo' }
  out.push('## Por nombre', '')
  for (const [tipo, titulo] of Object.entries(tipos)) {
    const items = o.escenas.flatMap(e => (e.cubre ?? []).filter(c => c.startsWith(tipo + ':')).map(c => [c.slice(tipo.length + 1), e.id]))
    if (!items.length) continue
    items.sort((a, b) => a[0].localeCompare(b[0]))
    out.push(`- **${titulo}**: ` + items.map(([n, id]) => '[`' + n + '`](#' + id + ')').join(' · '))
  }
  out.push('')
  for (const gr of grupos) {
    const es = o.escenas.filter(e => e.grupo === gr)
    if (!es.length) continue
    out.push(`## ${gr}`, '')
    for (const e of es) {
      out.push(`<a id="${e.id}"></a>`, '', `### ${e.titulo}`, '')
      if (e.texto) out.push(e.texto, '')
      if (e.soloTexto) out.push(`> **Solo texto.** ${e.soloTexto}`, '')
      else out.push(`![${(e.alt ?? e.titulo).replace(/[[\]\n]/g, ' ').slice(0, 300)}](${rel(path.join(o.imagenes, e.id + '.png'))})`, '')
      if (e.comando) out.push('Comando: `' + e.comando + '`', '')
      for (const r of [].concat(e.regla ?? [])) {
        const y = yamlDeRegla(r, o.reglas)
        if (!y) throw new Error(`La escena ${e.id} cita la regla ${r}, que no está en ${o.reglas.join(', ')}`)
        out.push(`Regla (\`${path.relative(o.raiz, y.archivo).split(path.sep).join('/')}\`):`, '', '```yaml', y.yaml, '```', '')
      }
    }
  }
  fs.mkdirSync(path.dirname(o.archivo), { recursive: true })
  fs.writeFileSync(o.archivo, out.join('\n'))
}

/** Ancla de GitHub para un título: minúsculas, sin signos, espacios a guiones. */
export function ancla (titulo) {
  return titulo.toLowerCase().replace(/[^\p{L}\p{N} _-]/gu, '').replace(/ /g, '-')
}
