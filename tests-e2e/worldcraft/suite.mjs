// Escenarios en el juego de Worldcraft (spec 025): Ana (OP) edita con /wc y la varita; Beto mira (y, con permiso, edita
// con los límites de «default»). Se comprueba por lo que reciben los bots: mensajes, partículas y los bloques que ve
// cada uno (la vista previa son bloques falsos que solo ve Ana).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const RECURSOS = fileURLToPath(new URL('../../plugins/worldcraft/src/main/resources', import.meta.url))

const ANA = 'Ana_e2e'
const BETO = 'Beto_e2e'
// Mundo plano: el pasto está en y = -61; se trabaja de y = -60 hacia arriba.
const X = 40
const Z = 40

const v3 = (bot, x, y, z) => new bot.entity.position.constructor(x, y, z)
const bloque = (bot, x, y, z) => bot.blockAt(v3(bot, x, y, z))?.name ?? '?'

function contar (bot, [x0, x1], [y0, y1], [z0, z1], filtro = n => n !== 'air') {
  let n = 0
  for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) if (filtro(bloque(bot, x, y, z))) n++
  return n
}

/** El área de trabajo sin nada (aire de y = -60 a -40) y a Ana en el punto de trabajo, en creativo, mirando al sur. */
async function preparar ({ srv, bots }) {
  // /fill acepta hasta 32 768 bloques: 101 × 101 × 3 por tanda.
  for (let y = -60; y < -40; y += 3) await srv.consola(`fill ${X - 30} ${y} ${Z - 30} ${X + 70} ${y + 2} ${Z + 70} air`)
  for (const c of [`gamemode creative ${ANA}`, `tp ${ANA} ${X + 0.5} -60 ${Z + 0.5} 0 0`, `clear ${ANA}`]) await srv.consola(c)
  await srv.sincronizar()
  await bots[ANA].hasta('estar en el punto de trabajo', b =>
    Math.abs(b.entity.position.x - (X + 0.5)) < 0.6 && Math.abs(b.entity.position.z - (Z + 0.5)) < 0.6 && b.entity.onGround)
}

/** Manda un comando y espera una respuesta en el chat. */
async function pide (jugador, comando, patron, timeoutMs = 15000) {
  const m = jugador.marca()
  jugador.comando(comando)
  return jugador.esperar('chat', patron, { desde: m, timeoutMs })
}

/** Selecciona con comandos (Ana) la caja entre dos esquinas. */
async function seleccionar (jugador, a, b) {
  await pide(jugador, `wc pos1 ${a.join(' ')}`, /Corner 1 at/)
  await pide(jugador, `wc pos2 ${b.join(' ')}`, /Corner 2 at .* selection/)
}

/** Corre una obra y espera el «Done»: devuelve cuántos bloques cambió y el número en el historial. */
async function obra (jugador, comando, timeoutMs = 30000) {
  const r = await pide(jugador, comando, /Done: \w+ changed ([\d,]+) blocks .*#(\d+)/, timeoutMs)
  const [, n, id] = r.texto.match(/changed ([\d,]+) blocks .*#(\d+)/)
  return { cambiados: Number(n.replace(/,/g, '')), id: Number(id) }
}

const comprobar = (srv, cond) => srv.consola(`execute if ${cond}`, { espera: /Test passed/ })

// ---------------------------------------------------------------- terreno (spec 026)
const dormir = ms => new Promise(resolve => setTimeout(resolve, ms))

/** Como preparar, y además el aire hasta y = -10 (las montañas e islas suben más que las obras de la 025). */
async function prepararAlto (ctx) {
  for (let y = -40; y < -10; y += 3) await ctx.srv.consola(`fill ${X - 30} ${y} ${Z - 30} ${X + 70} ${y + 2} ${Z + 70} air`)
  await preparar(ctx)
}

/** La y del bloque más alto que ve el bot en esa columna (de -10 hacia abajo), o -999. */
function alto (bot, x, z) {
  for (let y = -10; y >= -62; y--) if (bloque(bot, x, y, z) !== 'air') return y
  return -999
}

function alturas (bot, cx, cz, r, paso) {
  const out = []
  for (let dx = -r; dx <= r; dx += paso) for (let dz = -r; dz <= r; dz += paso) out.push(alto(bot, cx + dx, cz + dz))
  return out
}

async function tickMasLargo (jugador, tipo) {
  const jobs = await pide(jugador, 'wc jobs', new RegExp(`Last edit: ${tipo}, [\\d,]+ blocks, longest tick ([\\d.]+) ms`))
  const largo = Number(jobs.texto.match(/longest tick ([\d.]+) ms/)[1])
  console.log(`    (tick más largo de ${tipo}: ${largo} ms)`)
  if (largo > 12) throw new Error(`${tipo} usó ${largo} ms en un tick (presupuesto 8)`)
}

// ---------------------------------------------------------------- esquemas (spec 027)
const carpetaWE = srv => path.join(srv.run, 'plugins', 'WorldEdit', 'schematics')

/** Un cofre con 5 diamantes, una escalera al norte, piedra y un cartel «Hola» encima, en X+20..22, Z+20. */
async function construirMuestra (srv) {
  await srv.consola(`setblock ${X + 20} -60 ${Z + 20} chest{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}`)
  await srv.consola(`setblock ${X + 21} -60 ${Z + 20} oak_stairs[facing=north]`)
  await srv.consola(`setblock ${X + 22} -60 ${Z + 20} stone`)
  await srv.consola(`setblock ${X + 22} -59 ${Z + 20} oak_sign{front_text:{messages:['"Hola"','""','""','""']}}`)
  await srv.sincronizar()
}

/** La muestra pegada con su esquina en (x, z): bloques, ítems del cofre y texto del cartel. */
async function revisarMuestra (srv, x, z) {
  await comprobar(srv, `block ${x} -60 ${z} chest`)
  await comprobar(srv, `block ${x + 1} -60 ${z} oak_stairs[facing=north]`)
  await comprobar(srv, `block ${x + 2} -60 ${z} stone`)
  await comprobar(srv, `block ${x + 2} -59 ${z} oak_sign`)
  await srv.consola(`data get block ${x} -60 ${z} Items[0].count`, { espera: /has the following block data: 5/ })
  await srv.consola(`data get block ${x + 2} -59 ${z} front_text.messages[0]`, { espera: /Hola/ })
}

async function esperarArchivo (archivo, timeoutMs = 20000) {
  const fin = Date.now() + timeoutMs
  while (Date.now() < fin) {
    if (fs.existsSync(archivo) && fs.statSync(archivo).size > 0) return
    await dormir(250)
  }
  throw new Error(`no apareció ${archivo}`)
}

// ---------------------------------------------------------------- entidades (spec 031)
/** Un selector de consola de las entidades de un tipo en un bloque (su caja toca ese bloque). */
const en = (tipo, x, y, z) => `@e[type=${tipo},x=${x},y=${y},z=${z},dx=0,dy=0,dz=0]`
const en1 = (tipo, x, y, z) => `@e[type=${tipo},x=${x},y=${y},z=${z},dx=0,dy=0,dz=0,limit=1]`

/** Cuántas entidades de un tipo hay en una caja (por la consola). */
async function cuantas (srv, tipo, [x0, x1], [y0, y1], [z0, z1]) {
  const r = await srv.consola(`execute if entity @e[type=${tipo},x=${x0},y=${y0},z=${z0},dx=${x1 - x0},dy=${y1 - y0},dz=${z1 - z0}]`,
    { espera: /Test passed|Test failed/ })
  const m = (r.texto ?? String(r)).match(/[Cc]ount: (\d+)/)
  return m ? Number(m[1]) : 0
}

/** Un soporte con casco, un marco con una manzana colgado de una pared al norte de la piedra y una vaca, en X+2..4, Z+2..4. */
async function construirSala (srv, dx = 0) {
  await srv.consola(`setblock ${X + 3 + dx} -60 ${Z + 4} stone`)
  await srv.consola(`summon item_frame ${X + 3 + dx} -60 ${Z + 3} {Facing:2b,Item:{id:"minecraft:apple",count:1}}`)
  await srv.consola(`summon armor_stand ${X + 2.5 + dx} -60 ${Z + 2.5} {NoGravity:1b,ArmorItems:[{},{},{},{id:"minecraft:diamond_helmet",count:1}]}`)
  await srv.consola(`summon cow ${X + 4.5 + dx} -60 ${Z + 2.5} {NoAI:1b}`)
  await srv.sincronizar()
}

/** Quita las entidades del área de trabajo; la segunda vez, lo que soltaron (la vaca suelta cuero y carne). */
async function borrarEntidades (srv) {
  for (let i = 0; i < 2; i++) await srv.consola(`kill @e[type=!player,x=${X - 30},y=-62,z=${Z - 30},dx=100,dy=30,dz=100]`)
  await srv.sincronizar()
}

// ---------------------------------------------------------------- pinceles y herramientas (spec 032)
/** Ana en el punto de trabajo mirando al sur y 12° abajo: la mira cae en el pasto de (X, -61, Z + 8). */
async function mirarSuelo ({ srv, bots }) {
  await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5} 0 12`)
  await srv.sincronizar()
  await bots[ANA].hasta('mirar al suelo', b => Math.abs(Math.abs(b.entity.pitch) - 12 * Math.PI / 180) < 0.05 &&
    Math.abs(b.entity.position.z - (Z + 0.5)) < 0.6)
}

/** Empieza a picar un bloque (un clic izquierdo en él: LEFT_CLICK_BLOCK). El superpico lo cancela: el error de mineflayer se ignora. */
function picar (jugador, x, y, z) {
  const b = jugador.bot.blockAt(v3(jugador.bot, x, y, z))
  jugador.bot.dig(b, true).catch(() => {})
}

export default {
  jar: /^Worldcraft-.*\.jar$/,
  // La configuración tal como está en el código fuente; el historial de corridas anteriores se borra (cada corrida
  // empieza sin obras).
  copiar: [
    { de: 'plugins/worldcraft/src/main/resources', a: 'plugins/Worldcraft' }
  ],
  datosDeJugador: [],
  bots: [ANA, BETO],
  // Spec 027: WorldEdit 7.3.19 en el servidor de prueba (aprobado en la 012) para comprobar que los dos leen los .schem del otro.
  pluginsExtra: /^worldedit/i, // no FastAsyncWorldEdit (spec 029)
  puerto: 25599,
  versionPorDefecto: '1.21.4',
  // Spec 031: WorldEdit 7.3.19 carga el NBT de un marco antes de moverlo a donde pega: a más de 16 bloques Paper avisa
  // «Block-attached entity at invalid position» (también con un esquema hecho por WorldEdit; el marco queda bien).
  ruidoDelServidor: [/No key layers/, /Block-attached entity at invalid position/],
  erroresDelPlugin: [/\[Worldcraft\] (Could not|Edit #\d+ stopped by an error|The undo history did not)/],
  preparar: [
    'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
    'gamerule advance_time false', 'gamerule advance_weather false', 'gamerule spawn_mobs false',
    'gamerule sendCommandFeedback true', 'gamerule send_command_feedback true',
    `setworldspawn ${X} -60 ${Z}`, 'time set noon', 'weather clear', 'kill @e[type=!player]'
  ],
  limpiar: [`deop ${ANA}`, `deop ${BETO}`],

  async antes (ctx) {
    await ctx.entrar(BETO)
    await ctx.entrar(ANA)
    await ctx.srv.consola(`op ${ANA}`, { espera: new RegExp(`Made ${ANA} a server operator|Nothing changed`) })
    await ctx.srv.consola(`deop ${BETO}`)
    for (const c of [`gamemode creative ${BETO}`, `tp ${BETO} ${X + 0.5} -60 ${Z - 6.5} 0 0`]) await ctx.srv.consola(c)
  },

  escenarios: [
    {
      id: 'H8 CA1 · H8 CA4 · H8 CA5 · H2 CA6',
      nombre: 'sin permiso no se ve nada; la ayuda da un ejemplo; el autocompletado sugiere bloques en una lista',
      async fn ({ bots }) {
        const beto = bots[BETO]
        await pide(beto, 'wc set stone', /You don't have permission/)
        const deBeto = await beto.bot.tabComplete('/wc ', false, false, 2000).catch(e => /did not fire/.test(e.message) ? [] : Promise.reject(e))
        if (deBeto.length) throw new Error(`Beto ve subcomandos: ${JSON.stringify(deBeto)}`)
        const ana = bots[ANA]
        const subs = (await ana.bot.tabComplete('/wc ')).map(x => x.match ?? x)
        for (const s of ['set', 'replace', 'undo', 'paste', 'selftest']) if (!subs.includes(s)) throw new Error(`Ana no ve '${s}': ${subs}`)
        await pide(ana, 'wc help replace', /Example: \/wc replace dirt, grass_block with stone/)
        const lista = (await ana.bot.tabComplete('/wc set 70% stone,gra')).map(x => x.match ?? x)
        if (!lista.includes('stone,grass_block')) throw new Error(`no sugiere stone,grass_block: ${lista.slice(0, 8)}`)
        const etiquetas = (await ana.bot.tabComplete('/wc replace #lo')).map(x => x.match ?? x)
        if (!etiquetas.includes('#logs')) throw new Error(`no sugiere #logs: ${etiquetas.slice(0, 8)}`)
        const presets = (await ana.bot.tabComplete('/wc set $')).map(x => x.match ?? x)
        if (!presets.includes('$castle_wall')) throw new Error(`no sugiere $castle_wall: ${presets}`)
      }
    },
    {
      id: 'H1 CA1 · H1 CA2 · H1 CA3',
      nombre: 'la varita marca esquinas sin romper nada, el contorno lo ve solo Ana y un hacha común no es varita',
      timeoutMs: 60000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await pide(ana, 'wc wand', /Here is your wand/)
        await ana.hasta('tener la varita en la mano', b => b.heldItem?.name === 'golden_axe')
        // Clic izquierdo: el paquete de empezar a romper (lo que manda el cliente al pegarle a un bloque).
        let m = ana.marca()
        ana.bot._client.write('block_dig', { status: 0, location: v3(ana.bot, X, -61, Z), face: 1, sequence: 1 })
        await ana.esperar('chat', new RegExp(`Corner 1 at ${X} -61 ${Z}\\.`), { desde: m })
        m = ana.marca()
        await ana.bot.activateBlock(ana.bot.blockAt(v3(ana.bot, X + 4, -61, Z + 2)))
        await ana.esperar('chat', new RegExp(`Corner 2 at ${X + 4} -61 ${Z + 2} · selection 5×1×3 \\(15 blocks\\)`), { desde: m })
        await comprobar(srv, `block ${X} -61 ${Z} grass_block`) // no se rompió
        // El contorno: partículas para Ana mientras tiene la varita; Beto, a 7 bloques, no recibe ninguna.
        const mb = bots[BETO].marca()
        await ana.esperar('particle', /dust/, { desde: m, timeoutMs: 5000 })
        await bots[BETO].nunca('particle', /dust/, { desde: mb, ms: 1500 })
        // Un hacha de oro cualquiera no marca nada.
        await srv.consola(`item replace entity ${ANA} hotbar.1 with minecraft:golden_axe`)
        ana.bot.setQuickBarSlot(1)
        await ana.hasta('tener el hacha común', b => b.quickBarSlot === 1 && b.heldItem?.name === 'golden_axe')
        m = ana.marca()
        await ana.bot.activateBlock(ana.bot.blockAt(v3(ana.bot, X + 2, -61, Z + 2)))
        await ana.nunca('chat', /Corner/, { desde: m, ms: 1500 })
        ana.bot.setQuickBarSlot(0)
        await pide(ana, 'wc sel', /Selection 5×1×3 \(15 blocks\) in world, from 40 -61 40 to 44 -61 42/)
      }
    },
    {
      id: 'H3 CA1 · H6 CA1 · H6 CA3 · H5 CA4',
      nombre: 'set con mezcla; deshacer deja lo que otro puso después y lo dice; rehacer; faces y replace',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        const caja = [[X + 2, X + 11], [-60, -56], [Z + 2, Z + 11]]
        await seleccionar(ana, [X + 2, -60, Z + 2], [X + 11, -56, Z + 11])
        const set = await obra(ana, 'wc set 50% stone, 50% glass')
        if (set.cambiados !== 500) throw new Error(`set cambió ${set.cambiados}`)
        await ana.hasta('ver 500 bloques de piedra y vidrio', b => contar(b, ...caja, n => n === 'stone' || n === 'glass') === 500)
        const piedras = contar(ana.bot, ...caja, n => n === 'stone')
        if (piedras < 175 || piedras > 325) throw new Error(`la mezcla dio ${piedras} piedras de 500`)
        // Otro construye encima después de la obra.
        await srv.consola(`setblock ${X + 3} -60 ${Z + 3} gold_block`)
        await srv.sincronizar() // la consola no espera (lección 002 F-03)
        const m = ana.marca()
        ana.comando('wc undo')
        await ana.esperar('chat', new RegExp(`Undone #${set.id} \\(set 50% stone, 50% glass\\): 499 blocks back`), { desde: m })
        await ana.esperar('chat', /1 block\(s\) were left alone: someone changed them after the edit/, { desde: m })
        await ana.hasta('ver solo el oro', b => contar(b, ...caja) === 1 && bloque(b, X + 3, -60, Z + 3) === 'gold_block')
        await pide(ana, 'wc redo', new RegExp(`Redone #${set.id} .*: 499 blocks`))
        await ana.hasta('ver la obra otra vez', b => contar(b, ...caja) === 500)
        await pide(ana, 'wc history', new RegExp(`#${set.id} .* set .*50% stone, 50% glass.* · 500 blocks in world`))
        await pide(ana, 'wc undo', /Undone/)
        await srv.consola(`setblock ${X + 3} -60 ${Z + 3} air`)

        // faces: hueca, con piso y techo; replace solo cambia lo que dice la máscara.
        await seleccionar(ana, [X + 2, -60, Z + 2], [X + 8, -56, Z + 8])
        const faces = await obra(ana, 'wc faces glass')
        if (faces.cambiados !== 7 * 7 * 5 - 5 * 5 * 3) throw new Error(`faces cambió ${faces.cambiados}`)
        await ana.hasta('ver la caja hueca', b => bloque(b, X + 5, -58, Z + 5) === 'air' && bloque(b, X + 5, -56, Z + 5) === 'glass')
        await srv.consola(`setblock ${X + 5} -58 ${Z + 5} dirt`)
        await srv.sincronizar()
        const rep = await obra(ana, 'wc replace glass with oak_planks')
        if (rep.cambiados !== faces.cambiados) throw new Error(`replace cambió ${rep.cambiados}`)
        await ana.hasta('ver tablas y la tierra de adentro', b => bloque(b, X + 2, -58, Z + 2) === 'oak_planks' && bloque(b, X + 5, -58, Z + 5) === 'dirt')
        await pide(ana, 'wc undo 2', /Undone/)
        await ana.hasta('ver solo la tierra', b => contar(b, [X + 2, X + 8], [-60, -56], [Z + 2, Z + 8]) === 1, { timeoutMs: 10000 })
      }
    },
    {
      id: 'H4 CA1 · H4 CA2 · H4 CA3 · H4 CA4 · H5 CA1',
      nombre: 'una obra grande espera confirmación; la vista previa solo la ve Ana; confirm la hace dentro del tiempo por tick',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        const beto = bots[BETO]
        // Más de 50 000 bloques (confirm.above_blocks, decisión del dueño): espera confirmación.
        const grande = [[X + 2, X + 61], [-60, -46], [Z + 2, Z + 61]]
        await seleccionar(ana, [X + 2, -60, Z + 2], [X + 61, -46, Z + 61])
        const m = ana.marca()
        await pide(ana, 'wc set dirt', /This set changes up to 54,000 blocks .*\/wc confirm/)
        await ana.nunca('chat', /Done:/, { desde: m, ms: 1500 })
        if (contar(ana.bot, ...grande)) throw new Error('se puso algo sin confirmar')
        await pide(ana, 'wc cancel', /Dropped: nothing was changed/)

        // 36 000 bloques no necesitan confirmación, pero --preview siempre espera su confirm.
        const caja = [[X + 2, X + 61], [-60, -51], [Z + 2, Z + 61]]
        await seleccionar(ana, [X + 2, -60, Z + 2], [X + 61, -51, Z + 61])

        await pide(ana, 'wc set 50% dirt, 50% sand --preview', /Preview ready: 36,000 blocks change\. Nothing changed yet/, 30000)
        await ana.hasta('Ana ve la vista previa', b => contar(b, ...caja) === 36000, { timeoutMs: 10000 })
        const vista = new Map()
        for (let x = X + 2; x < X + 62; x += 7) for (let z = Z + 2; z < Z + 62; z += 5) vista.set(`${x},${z}`, bloque(ana.bot, x, -55, z))
        if (contar(beto.bot, ...caja)) throw new Error('Beto ve la vista previa')
        await comprobar(srv, `block ${X + 5} -55 ${Z + 5} air`) // el mundo no cambió
        await pide(ana, 'wc cancel', /Dropped: nothing was changed/)
        await ana.hasta('Ana vuelve a ver el aire', b => contar(b, ...caja) === 0, { timeoutMs: 10000 })

        await pide(ana, 'wc set 50% dirt, 50% sand --preview', /Preview ready/, 30000)
        await ana.hasta('Ana ve la vista previa', b => contar(b, ...caja) === 36000, { timeoutMs: 10000 })
        const otra = new Map()
        for (const k of vista.keys()) { const [x, z] = k.split(',').map(Number); otra.set(k, bloque(ana.bot, x, -55, z)) }
        if ([...vista.keys()].every(k => vista.get(k) === otra.get(k))) throw new Error('dos vistas previas dieron la misma mezcla')
        const r = await obra(ana, 'wc confirm', 60000)
        if (r.cambiados !== 36000) throw new Error(`confirm cambió ${r.cambiados}`)
        await beto.hasta('Beto ve la obra', b => contar(b, ...caja) === 36000, { timeoutMs: 15000 })
        for (const [k, v] of otra) {
          const [x, z] = k.split(',').map(Number)
          if (bloque(beto.bot, x, -55, z) !== v) throw new Error(`en ${k} la obra puso ${bloque(beto.bot, x, -55, z)} y la vista previa mostraba ${v}`)
        }
        const jobs = await pide(ana, 'wc jobs', /Last edit: set, 36,000 blocks, longest tick ([\d.]+) ms \(budget 8 ms\)/)
        const largo = Number(jobs.texto.match(/longest tick ([\d.]+) ms/)[1])
        console.log(`    (tick más largo de la obra de 36 000 bloques: ${largo} ms)`)
        if (largo > 12) throw new Error(`la obra usó ${largo} ms en un tick (presupuesto 8)`)
        await pide(ana, 'wc undo', /Undone .*36,000 blocks back/, 60000)
        await beto.hasta('Beto ve el aire', b => contar(b, ...caja) === 0, { timeoutMs: 15000 })
      }
    },
    {
      id: 'H3 CA2 · H3 CA3 · H3 CA4 · H6 CA4',
      nombre: 'copiar, girar y pegar: la escalera gira y el cofre lleva sus diamantes; cortar y deshacer los devuelve',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await srv.consola(`setblock ${X + 2} -60 ${Z + 2} chest{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}`)
        await srv.consola(`setblock ${X + 3} -60 ${Z + 2} oak_stairs[facing=north]`)
        await srv.consola(`setblock ${X + 4} -60 ${Z + 2} stone`)
        await srv.sincronizar()
        await seleccionar(ana, [X + 2, -60, Z + 2], [X + 4, -60, Z + 3]) // con una fila de aire en z + 3
        await pide(ana, 'wc copy', /Copied 3×1×2 \(6 blocks\)/)
        await pide(ana, 'wc rotate 90', /Clipboard turned 90°: now 2×1×3/)
        // Donde va el aire copiado hay un bloque: no se borra.
        await srv.consola(`setblock ${X - 3} -60 ${Z + 12} gold_block`)
        await srv.sincronizar()
        await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 10.5}`)
        await ana.hasta('llegar', b => Math.abs(b.entity.position.z - (Z + 10.5)) < 0.6)
        const p = await obra(ana, 'wc paste')
        if (p.cambiados !== 3) throw new Error(`paste cambió ${p.cambiados}`)
        // (dx, dz) = (2..4, 2) desde Ana gira a (-2, 2..4): el cofre en (X-2, Z+12), la escalera en (X-2, Z+13).
        await comprobar(srv, `block ${X - 2} -60 ${Z + 12} chest`)
        await comprobar(srv, `block ${X - 2} -60 ${Z + 13} oak_stairs[facing=east]`)
        await comprobar(srv, `block ${X - 2} -60 ${Z + 14} stone`)
        await comprobar(srv, `block ${X - 3} -60 ${Z + 12} gold_block`)
        await srv.consola(`data get block ${X - 2} -60 ${Z + 12} Items[0].count`, { espera: /has the following block data: 5/ })
        await pide(ana, 'wc undo', /Undone .*paste.*: 3 blocks back/)
        await comprobar(srv, `block ${X - 2} -60 ${Z + 12} air`)

        await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5}`)
        await obra(ana, 'wc cut')
        await comprobar(srv, `block ${X + 2} -60 ${Z + 2} air`)
        await pide(ana, 'wc undo', /Undone .*cut/)
        await comprobar(srv, `block ${X + 3} -60 ${Z + 2} oak_stairs[facing=north]`)
        await srv.consola(`data get block ${X + 2} -60 ${Z + 2} Items[0].count`, { espera: /has the following block data: 5/ })
        await srv.consola(`setblock ${X - 3} -60 ${Z + 12} air`)
      }
    },
    {
      id: 'H7 CA1 · H7 CA2 · H7 CA3 · H7 CA6 · H6 CA6',
      nombre: 'con los límites de default, Beto no pasa de 50 000 ni pone roca madre; un moderador deshace lo de Beto',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv } = ctx
        await preparar(ctx)
        const permisos = path.join(srv.run, 'permissions.yml')
        const antes = fs.existsSync(permisos) ? fs.readFileSync(permisos, 'utf8') : null
        try {
          // worldcraft.use para todos, como hijo de un permiso propio (uno del plugin no se puede redefinir: lección 023).
          fs.writeFileSync(permisos, ['e2e.editor:', '  default: true', '  children:', '    worldcraft.use: true', ''].join('\n'))
          await srv.consola('bukkit:reload permissions', { espera: /Permissions successfully reloaded/i })
          await ctx.entrar(BETO)
          for (const c of [`gamemode creative ${BETO}`, `tp ${BETO} ${X + 0.5} -60 ${Z - 6.5} 0 0`]) await srv.consola(c)
          const beto = ctx.bots[BETO]
          await pide(beto, 'wc limits', /Groups: default · biggest edit 50,000 blocks · 2 at once · worlds: \* · blocked: bedrock/)
          await seleccionar(beto, [X + 2, -60, Z + 2], [X + 101, -51, Z + 101])
          await pide(beto, 'wc set stone', /That is 100,000 blocks; your limit is 50,000 \(groups: default\)/)
          await seleccionar(beto, [X + 2, -60, Z + 2], [X + 4, -60, Z + 4])
          await pide(beto, 'wc set 90% stone, 10% bedrock', /You may not place bedrock/)
          const r = await obra(beto, 'wc set glass')
          await pide(ctx.bots[ANA], `wc history ${BETO}`, new RegExp(`#${r.id} .* set .*glass`))
          await pide(beto, `wc undo --player ${ANA}`, /You don't have permission/)
          await pide(ctx.bots[ANA], `wc undo --player ${BETO}`, new RegExp(`Undone #${r.id} \\(set glass\\): 9 blocks back`))
          await comprobar(srv, `block ${X + 3} -60 ${Z + 3} air`)
        } finally {
          if (antes == null) fs.rmSync(permisos, { force: true })
          else fs.writeFileSync(permisos, antes)
          await srv.consola('bukkit:reload permissions')
          await ctx.entrar(BETO)
          for (const c of [`gamemode creative ${BETO}`, `tp ${BETO} ${X + 0.5} -60 ${Z - 6.5} 0 0`]) await srv.consola(c)
        }
      }
    },
    {
      id: 'H2 CA3',
      nombre: 'los errores dicen qué corregir y no tocan nada',
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await seleccionar(ana, [X, -60, Z], [X + 3, -60, Z + 3])
        await pide(ana, 'wc set stnoe', /Unknown block 'stnoe' \(did you mean 'stone'\?\)/)
        await pide(ana, 'wc replace #lgos with stone', /Unknown block tag '#lgos' \(did you mean 'minecraft:logs'\?\)/)
        await pide(ana, 'wc set stone --previwe', /Unknown option '--previwe' \(did you mean '--preview'\?\)/)
        // Spec 029: el mensaje ahora dice cómo escribir el azar en una máscara.
        await pide(ana, 'wc replace 50% dirt with stone', /weights \(%\) work in patterns; in a mask write a chance/)
        await pide(ana, 'wc set oak_stairs[facing=up]', /'up' is not a value of 'facing'/)
        await pide(ana, 'wc expand 3 left', /'left' is not a direction/)
        await comprobar(srv, `blocks ${X} -60 ${Z} ${X + 3} -60 ${Z + 3} ${X} -50 ${Z} all`) // todo sigue en aire (igual que y = -50)
      }
    },
    {
      id: 'H8 CA2 · H8 CA6',
      nombre: 'validate y selftest desde la consola; un config.yml con errores avisa y conserva lo que funcionaba',
      timeoutMs: 60000,
      async fn ({ srv }) {
        await srv.consola('wc validate', { espera: /Configuration OK/ })
        await srv.consola('wc selftest', { espera: /Selftest passed: set \d+ blocks, undo, redo and undo; the terrain is as before/, timeoutMs: 20000 })
        const cfg = path.join(srv.run, 'plugins', 'Worldcraft', 'config.yml')
        try {
          // Confirmación apagada (decisión del dueño): una obra de más de above_blocks corre sin esperar.
          fs.writeFileSync(cfg, 'confirm:\n  enabled: false\n  above_blocks: 10\n')
          await srv.consola('wc reload', { espera: /Reloaded: 0 error\(s\), 0 warning\(s\)/ })
          await srv.consola(`wc pos1 ${X + 2} -60 ${Z + 2}`)
          await srv.consola(`wc pos2 ${X + 6} -60 ${Z + 6}`, { espera: /selection 5×1×5/ })
          await srv.consola('wc set glass', { espera: /Done: set changed 25 blocks/ })
          await srv.consola('wc undo', { espera: /Undone .*: 25 blocks back/ })
          fs.writeFileSync(cfg, 'performance:\n  max_ms_per_tik: 5\n  max_blocks_per_tick: lots\n')
          await srv.consola('wc reload', { espera: /Reloaded: 1 error\(s\), 1 warning\(s\)/ })
          await srv.consola('wc validate', { espera: /performance\.max_ms_per_tik: unknown setting \(did you mean 'max_ms_per_tick'\?\)/ })
        } finally {
          fs.copyFileSync(path.join(RECURSOS, 'config.yml'), cfg)
          await srv.consola('wc reload', { espera: /Reloaded: 0 error\(s\), 0 warning\(s\)/ })
        }
      }
    },
    {
      id: '026 H1 CA1 · H1 CA3 · H1 CA4 · H1 CA5 · H4 CA2 · H5 CA1',
      nombre: 'una montaña donde mira Ana: vista previa solo suya, otra con reroll, se construye lo que mostró, no pisa una casa y se deshace',
      timeoutMs: 240000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararAlto(ctx)
        const ana = bots[ANA]
        const beto = bots[BETO]
        const CZ = Z + 48 // desde el pilar (y = -44, mirando 20° hacia abajo) la mira cae en el suelo a unos 48 bloques
        await srv.consola(`setblock ${X + 3} -60 ${CZ} oak_planks`) // una «construcción» en medio
        await srv.consola(`setblock ${X} -45 ${Z} stone`)
        await srv.consola(`tp ${ANA} ${X + 0.5} -44 ${Z + 0.5} 0 20`)
        await srv.sincronizar()
        await ana.hasta('estar en el pilar', b => Math.abs(b.entity.position.y + 44) < 0.1)
        await pide(ana, 'wc mountain 12 height=14 seed=42', /Here is your mountain \([\d,]+ blocks, only you see it\) seed=42 · style alpine · radius 12 · height 14/, 60000)
        await ana.esperar('chat', /\[Build\] {2}\[Another\] {2}\[Cancel\]/)
        await ana.hasta('Ana ve la montaña donde mira', b => Math.abs(alto(b, X, CZ) - (-61 + 14)) <= 3, { timeoutMs: 15000 })
        if (alto(beto.bot, X, CZ) !== -61) throw new Error(`Beto ve la vista previa (${alto(beto.bot, X, CZ)})`)
        await comprobar(srv, `block ${X} -58 ${CZ} air`) // el mundo no cambió
        const primera = alturas(ana.bot, X, CZ, 8, 4)
        await pide(ana, 'wc reroll', /Here is your mountain .*seed=(\d+)/, 60000)
        await ana.hasta('otra montaña', b => alturas(b, X, CZ, 8, 4).some((h, i) => h !== primera[i]), { timeoutMs: 15000 })
        await dormir(1500)
        const vista = alturas(ana.bot, X, CZ, 10, 2)
        const r = await obra(ana, 'wc confirm', 90000)
        if (r.cambiados < 500) throw new Error(`la montaña cambió solo ${r.cambiados}`)
        await beto.hasta('Beto ve la montaña que mostró la vista previa', b => alturas(b, X, CZ, 10, 2).every((h, i) => h === vista[i]), { timeoutMs: 20000 })
        await comprobar(srv, `block ${X + 3} -60 ${CZ} oak_planks`)
        await comprobar(srv, `block ${X + 3} -59 ${CZ} air`) // la columna de la casa no se toca
        const nombres = new Set()
        for (let dx = -12; dx <= 12; dx++) for (let dz = -12; dz <= 12; dz++) {
          const y = alto(beto.bot, X + dx, CZ + dz)
          if (y > -61) nombres.add(bloque(beto.bot, X + dx, y, CZ + dz))
        }
        if (!nombres.has('grass_block') || !(nombres.has('stone') || nombres.has('andesite'))) throw new Error(`materiales: ${[...nombres]}`)
        await tickMasLargo(ana, 'mountain')
        await pide(ana, 'wc undo', /Undone .*mountain/, 60000)
        await beto.hasta('Beto ve el llano otra vez', b => alturas(b, X, CZ, 10, 2).every(h => h === -61 || h === -60), { timeoutMs: 20000 })
      }
    },
    {
      id: '026 H2 CA1 · H2 CA2 · H2 CA3 · H2 CA4',
      nombre: 'una isla flotante delante de Ana, sobre su cabeza: pasto arriba, piedra abajo, hojas que no se caen, nada toca el suelo',
      timeoutMs: 180000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararAlto(ctx)
        const ana = bots[ANA]
        const beto = bots[BETO]
        await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5} 0 0`)
        await srv.sincronizar()
        await ana.hasta('mirar al sur', b => Math.abs(b.entity.position.z - (Z + 0.5)) < 0.6)
        await pide(ana, 'wc island small seed=5 trees=1', /Here is your island .*seed=5 · style meadow · radius 8/, 60000)
        // Centro a 8 × 1,3 + 6 bloques hacia el sur; la cima a 13 + 6 sobre los pies.
        const CZ = Z + 16
        const TOP = -60 + 13 + 6
        await ana.hasta('Ana ve la isla', b => alto(b, X, CZ) >= TOP - 3, { timeoutMs: 15000 })
        if (alto(beto.bot, X, CZ) !== -61) throw new Error('Beto ve la vista previa')
        await obra(ana, 'wc confirm', 60000)
        await beto.hasta('Beto ve la isla', b => alto(b, X, CZ) >= TOP - 3, { timeoutMs: 20000 })
        // Nada toca el suelo: de y = -60 a -57 todo aire bajo la isla.
        await comprobar(srv, `blocks ${X - 12} -60 ${CZ - 12} ${X + 12} -57 ${CZ + 12} ${X - 12} -30 ${CZ - 12} all`)
        let pasto = 0
        let piedra = 0
        let hoja = null
        for (let dx = -12; dx <= 12; dx++) for (let dz = -12; dz <= 12; dz++) {
          for (let y = TOP + 9; y > -57; y--) {
            const n = bloque(beto.bot, X + dx, y, CZ + dz)
            if (n === 'grass_block') pasto++
            if (n === 'stone' || n === 'andesite') piedra++
            if (n === 'oak_leaves' && !hoja) hoja = [X + dx, y, CZ + dz]
          }
        }
        if (pasto < 60 || piedra < 30) throw new Error(`pasto ${pasto}, piedra ${piedra}`)
        if (!hoja) throw new Error('sin árbol')
        await comprobar(srv, `block ${hoja.join(' ')} oak_leaves[persistent=true]`)
        await tickMasLargo(ana, 'island')
        await pide(ana, 'wc undo', /Undone .*island/, 60000)
        await beto.hasta('Beto ve el cielo', b => alto(b, X, CZ) === -61, { timeoutMs: 20000 })
      }
    },
    {
      id: '026 H3 CA1 · H3 CA2 · H3 CA3 · H3 CA4',
      nombre: 'el pincel: sube con pasto arriba y tierra abajo, el círculo lo ve solo Ana, clic izquierdo cambia de modo, suaviza y se deshace',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararAlto(ctx)
        const ana = bots[ANA]
        await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5} 0 12`) // la mira cae en el suelo a unos 8 bloques
        await srv.sincronizar()
        await ana.hasta('mirar al suelo', b => Math.abs(b.entity.pitch + 12 * Math.PI / 180) < 0.05 || Math.abs(b.entity.pitch - 12 * Math.PI / 180) < 0.05)
        const CZ = Z + 8
        await pide(ana, 'wc brush raise 3 1', /Here is your terrain brush \(raise · radius 3 · strength 100%\)/)
        await ana.hasta('tener el pincel en la mano', b => b.heldItem?.name === 'golden_shovel')
        const mb = bots[BETO].marca()
        const m = ana.marca()
        await ana.esperar('particle', /dust/, { desde: m, timeoutMs: 5000 })
        await bots[BETO].nunca('particle', /dust/, { desde: mb, ms: 1500 })
        let mc = ana.marca()
        ana.bot.activateItem()
        await ana.esperar('chat', /Done: brush raise changed [\d,]+ blocks/, { desde: mc, timeoutMs: 20000 })
        await comprobar(srv, `block ${X} -59 ${CZ} grass_block`)
        await comprobar(srv, `block ${X} -60 ${CZ} dirt`)
        await comprobar(srv, `block ${X} -61 ${CZ} dirt`) // el viejo pasto quedó enterrado como tierra
        mc = ana.marca()
        ana.bot.swingArm()
        await ana.esperar('actionbar', /Brush lower · radius 3/, { desde: mc })
        await pide(ana, 'wc brush smooth 4 1', /Brush set to smooth · radius 4 · strength 100%/)
        mc = ana.marca()
        ana.bot.activateItem()
        await ana.esperar('chat', /Done: brush smooth changed [\d,]+ blocks/, { desde: mc, timeoutMs: 20000 })
        await comprobar(srv, `block ${X} -59 ${CZ} air`) // el bulto bajó
        await pide(ana, 'wc undo 2', /Undone .*brush/, 30000)
        await dormir(1000)
        await comprobar(srv, `block ${X} -61 ${CZ} grass_block`)
        await comprobar(srv, `block ${X} -60 ${CZ} air`)
        await srv.consola(`clear ${ANA}`)
      }
    },
    {
      id: '026 H4 CA1 · H4 CA2 · H4 CA3 · H5 CA3 · H6 CA1 · H6 CA3',
      nombre: 'experto: errores con sugerencia, autocompletado, la misma semilla da lo mismo, un terrain.yml malo avisa y default no pasa de 50 000',
      timeoutMs: 180000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararAlto(ctx)
        const ana = bots[ANA]
        await pide(ana, 'wc mountain peeks=3', /Unknown option 'peeks=' \(did you mean 'peaks'\?\)/)
        await pide(ana, 'wc island lagre', /'lagre' is not a size or a style \(did you mean 'large'\?\)/)
        await pide(ana, 'wc brush smoth', /Unknown brush mode 'smoth' \(did you mean 'smooth'\?\)/)
        await pide(ana, 'wc mountain radius=900', /radius 900 is out of range \(4\.\.160\)/)
        const m = ana.marca()
        ana.comando('wc styles')
        await ana.esperar('chat', /7 terrain styles/, { desde: m })
        await ana.esperar('chat', /volcano - A volcano with a crater/, { desde: m })
        const estilos = (await ana.bot.tabComplete('/wc mountain style=vo')).map(x => x.match ?? x)
        if (!estilos.includes('style=volcano')) throw new Error(`no sugiere style=volcano: ${estilos}`)
        const claves = (await ana.bot.tabComplete('/wc island tr')).map(x => x.match ?? x)
        if (!claves.includes('trees=')) throw new Error(`no sugiere trees=: ${claves}`)
        // La misma semilla, lo mismo (desde la consola, con at=).
        const at = `at=${X + 20},-61,${Z + 20}`
        await srv.consola(`wc mountain 8 height=8 seed=9 ${at} --now`, { espera: /Done: mountain changed/, timeoutMs: 60000 })
        await srv.sincronizar()
        await dormir(1500)
        const una = alturas(ana.bot, X + 20, Z + 20, 8, 2)
        if (Math.max(...una) < -61 + 5) throw new Error(`la montaña de la consola no se ve: ${una}`)
        await srv.consola('wc undo', { espera: /Undone .*mountain/, timeoutMs: 60000 })
        await srv.consola(`wc mountain 8 height=8 seed=9 ${at} --now`, { espera: /Done: mountain changed/, timeoutMs: 60000 })
        await ana.hasta('la misma montaña', b => alturas(b, X + 20, Z + 20, 8, 2).every((h, i) => h === una[i]), { timeoutMs: 15000 })
        await srv.consola('wc undo', { espera: /Undone .*mountain/, timeoutMs: 60000 })
        // Un terrain.yml con una condición mal escrita y un valor fuera de rango.
        const tf = path.join(srv.run, 'plugins', 'Worldcraft', 'terrain.yml')
        try {
          const malo = fs.readFileSync(path.join(RECURSOS, 'terrain.yml'), 'utf8')
            .replace('{ block: snow_block, height_above: 0.8 }', () => '{ block: snow_block, height_above: 8 }')
            .replace('slope_above: 58 }', () => 'slope_abve: 58 }')
          fs.writeFileSync(tf, malo)
          await srv.consola('wc reload', { espera: /Reloaded: 1 error\(s\), 1 warning\(s\)/ })
          await srv.consola('wc validate', { espera: /styles\.alpine\.layers\[0\]\.rules\[2\]\.slope_abve: unknown condition \(did you mean 'slope_above'\?\)/ })
        } finally {
          fs.copyFileSync(path.join(RECURSOS, 'terrain.yml'), tf)
          await srv.consola('wc reload', { espera: /Reloaded: 0 error\(s\), 0 warning\(s\)/ })
        }
        // Beto, con los límites de default: una montaña huge se rechaza antes de leer el suelo.
        const permisos = path.join(srv.run, 'permissions.yml')
        const antes = fs.existsSync(permisos) ? fs.readFileSync(permisos, 'utf8') : null
        try {
          fs.writeFileSync(permisos, ['e2e.editor:', '  default: true', '  children:', '    worldcraft.use: true', ''].join('\n'))
          await srv.consola('bukkit:reload permissions', { espera: /Permissions successfully reloaded/i })
          await ctx.entrar(BETO)
          for (const c of [`gamemode creative ${BETO}`, `tp ${BETO} ${X + 0.5} -60 ${Z - 6.5} 0 0`]) await srv.consola(c)
          await pide(ctx.bots[BETO], 'wc mountain huge', /That is about [\d,]+ blocks; your limit is 50,000 \(groups: default\)/)
        } finally {
          if (antes == null) fs.rmSync(permisos, { force: true })
          else fs.writeFileSync(permisos, antes)
          await srv.consola('bukkit:reload permissions')
          await ctx.entrar(BETO)
          for (const c of [`gamemode creative ${BETO}`, `tp ${BETO} ${X + 0.5} -60 ${Z - 6.5} 0 0`]) await srv.consola(c)
        }
      }
    },
    // ---------------------------------------------------------------- esquemas (spec 027)
    {
      id: '027 H3 CA1 · H2 CA1 · H2 CA2 · H2 CA3',
      nombre: 'esquemas de WorldEdit (v3 y v2) se ven en la lista, se cargan y se pegan con el cofre, la escalera y el cartel',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        const we = carpetaWE(srv)
        for (const f of ['e2e_we.schem', 'e2e_we2.schem']) fs.rmSync(path.join(we, f), { force: true })
        await construirMuestra(srv)
        // WorldEdit copia relativo a Ana (en X, Z) y guarda en su carpeta, en su formato de fábrica y en v2.
        await pide(ana, `/pos1 ${X + 20},-60,${Z + 20}`, /First position set/)
        await pide(ana, `/pos2 ${X + 22},-59,${Z + 20}`, /Second position set/)
        await pide(ana, '/copy', /were copied|blocks affected/)
        await pide(ana, '/schem save e2e_we', /saved/i, 30000)
        await pide(ana, '/schem save e2e_we2 sponge.2', /saved/i, 30000)
        await esperarArchivo(path.join(we, 'e2e_we.schem'))
        await esperarArchivo(path.join(we, 'e2e_we2.schem'))
        await pide(ana, 'wc schem list e2e_we', /e2e_we2 \[WorldEdit\]/)
        for (const [nombre, version, dz] of [['e2e_we', 3, 10], ['e2e_we2', 2, 14]]) {
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + dz + 0.5}`)
          await ana.hasta('llegar', b => Math.abs(b.entity.position.z - (Z + dz + 0.5)) < 0.6)
          await pide(ana, `wc schem load ${nombre}`, new RegExp(`Loaded ${nombre} \\[WorldEdit\\] \\(Sponge v${version}\\): 3×2×1 \\(6 blocks\\)`))
          const p = await obra(ana, 'wc paste')
          if (p.cambiados !== 4) throw new Error(`${nombre}: paste cambió ${p.cambiados}`)
          await revisarMuestra(srv, X + 20, Z + 20 + dz)
          await pide(ana, 'wc undo', /Undone .*paste/)
        }
      }
    },
    {
      id: '027 H1 CA1 · H1 CA2 · H1 CA3 · H1 CA4 · H1 CA5',
      nombre: 'un esquema guardado por Worldcraft (girado) lo carga y lo pega WorldEdit en el mismo lugar, con el cofre y el cartel',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await construirMuestra(srv)
        await seleccionar(ana, [X + 20, -60, Z + 20], [X + 22, -59, Z + 20])
        await pide(ana, 'wc copy', /Copied 3×2×1 \(6 blocks\)/)
        await pide(ana, 'wc schem save e2e_wc --overwrite', /Saved e2e_wc: 3×2×1 \(6 blocks\), \d+ KB/)
        await pide(ana, 'wc schem save e2e_wc', /e2e_wc already exists.*--overwrite/)
        // Girado 90: se guarda ya girado (el este pasa al sur: 1×2×3).
        await pide(ana, 'wc rotate 90', /now 1×2×3/)
        await pide(ana, 'wc schem save e2e_wc_g --overwrite', /Saved e2e_wc_g: 1×2×3 \(6 blocks\)/)
        const we = carpetaWE(srv)
        fs.mkdirSync(we, { recursive: true })
        for (const n of ['e2e_wc', 'e2e_wc_g']) fs.copyFileSync(path.join(srv.run, 'plugins', 'Worldcraft', 'schematics', `${n}.schem`), path.join(we, `${n}.schem`))
        await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 18.5}`)
        await ana.hasta('llegar', b => Math.abs(b.entity.position.z - (Z + 18.5)) < 0.6)
        await pide(ana, '/schem load e2e_wc', /loaded/i, 30000)
        await pide(ana, '/paste', /pasted/i, 30000)
        await revisarMuestra(srv, X + 20, Z + 38)
        await pide(ana, '/undo', /Undo successful|undid|undone/i)
        await pide(ana, '/schem load e2e_wc_g', /loaded/i, 30000)
        await pide(ana, '/paste', /pasted/i, 30000)
        // (dx, dz) = (20..22, 20) desde Ana gira a (-20, 20..22): el cofre en (X-20, Z+38), la escalera mira al este.
        await comprobar(srv, `block ${X - 20} -60 ${Z + 38} chest`)
        await comprobar(srv, `block ${X - 20} -60 ${Z + 39} oak_stairs[facing=east]`)
        await srv.consola(`data get block ${X - 20} -60 ${Z + 38} Items[0].count`, { espera: /has the following block data: 5/ })
        await pide(ana, '/undo', /Undo successful|undid|undone/i)
        for (const n of ['e2e_wc', 'e2e_wc_g']) fs.rmSync(path.join(we, `${n}.schem`), { force: true })
      }
    },
    {
      id: '027 H5 CA1 · H5 CA3 · H5 CA4 · H5 CA5 · H4 CA1 · H4 CA2 · H4 CA3',
      nombre: 'esquemas seguros: Beto no pega bloques de comandos ni borra; nombres peligrosos y archivos rotos no tocan nada; borrar va a la papelera',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await srv.consola(`setblock ${X + 25} -60 ${Z + 20} command_block`)
        await srv.sincronizar()
        await seleccionar(ana, [X + 25, -60, Z + 20], [X + 25, -60, Z + 20])
        await pide(ana, 'wc copy', /Copied 1×1×1/)
        await pide(ana, 'wc schem save e2e_cmd --overwrite', /Saved e2e_cmd/)
        await srv.consola(`setblock ${X + 25} -60 ${Z + 20} air`)
        const propia = path.join(srv.run, 'plugins', 'Worldcraft', 'schematics')
        fs.writeFileSync(path.join(propia, 'e2e_roto.schem'), Buffer.from('esto no es un esquema'))
        fs.mkdirSync(carpetaWE(srv), { recursive: true })
        fs.copyFileSync(path.join(propia, 'e2e_cmd.schem'), path.join(carpetaWE(srv), 'e2e_ajeno.schem'))
        await pide(ana, 'wc schem load e2e_roto', /Could not load e2e_roto: .*Your clipboard did not change/)
        await pide(ana, 'wc paste', /Done: paste changed 1 blocks/) // sigue con el bloque de comandos
        await pide(ana, 'wc undo', /Undone .*paste/)
        const permisos = path.join(srv.run, 'permissions.yml')
        const antes = fs.existsSync(permisos) ? fs.readFileSync(permisos, 'utf8') : null
        try {
          fs.writeFileSync(permisos, ['e2e.editor:', '  default: true', '  children:', '    worldcraft.use: true', '    worldcraft.schematic: true', ''].join('\n'))
          await srv.consola('bukkit:reload permissions', { espera: /Permissions successfully reloaded/i })
          await ctx.entrar(BETO)
          for (const c of [`gamemode creative ${BETO}`, `tp ${BETO} ${X + 0.5} -60 ${Z - 6.5} 0 0`]) await srv.consola(c)
          const beto = ctx.bots[BETO]
          await pide(beto, 'wc schem load e2e_cmd', /Loaded e2e_cmd \(Sponge v3\): 1×1×1/)
          await pide(beto, 'wc paste', /You may not place command_block/)
          await pide(beto, 'wc schem delete e2e_cmd', /You don't have permission/)
          // Spec 029 H5 CA2: guardar e importar piden worldcraft.schematic.save (Beto solo tiene worldcraft.schematic).
          await pide(beto, 'wc schem save e2e_cmd --overwrite', /You don't have permission/)
          await pide(beto, 'wc schem import', /You don't have permission/)
          await pide(beto, 'wc schem load ../../server', /is not a valid name/)
          await pide(beto, 'wc schem load C:/Windows/win', /is not a valid name/)
          const nombres = (await beto.bot.tabComplete('/wc schem load e2e_')).map(x => x.match ?? x)
          for (const n of ['e2e_cmd', 'e2e_ajeno']) if (!nombres.includes(n)) throw new Error(`no sugiere ${n}: ${nombres}`)
          const ops = (await beto.bot.tabComplete('/wc schem ')).map(x => x.match ?? x)
          if (ops.includes('delete')) throw new Error(`Beto ve delete: ${ops}`)
          for (const o of ['save', 'import']) if (ops.includes(o)) throw new Error(`Beto ve ${o}: ${ops}`)
        } finally {
          if (antes == null) fs.rmSync(permisos, { force: true })
          else fs.writeFileSync(permisos, antes)
          await srv.consola('bukkit:reload permissions')
          await ctx.entrar(BETO)
          for (const c of [`gamemode creative ${BETO}`, `tp ${BETO} ${X + 0.5} -60 ${Z - 6.5} 0 0`]) await srv.consola(c)
        }
        await pide(ana, 'wc schem delete e2e_ajeno', /is in the WorldEdit folder: Worldcraft only reads there/)
        await pide(ana, 'wc schem delete e2e_cmd', /Deleted e2e_cmd\. It waits 7 day/)
        if (fs.existsSync(path.join(propia, 'e2e_cmd.schem'))) throw new Error('e2e_cmd sigue en la carpeta')
        if (!fs.readdirSync(path.join(propia, '.trash')).some(f => f.startsWith('e2e_cmd-'))) throw new Error('e2e_cmd no está en la papelera')
        await pide(ana, 'wc schem load e2e_cmd', /There is no schematic called e2e_cmd/)
        if (!fs.existsSync(path.join(carpetaWE(srv), 'e2e_ajeno.schem'))) throw new Error('se borró el de WorldEdit')
        // Spec 029 H5 CA1: import copia el de WorldEdit a la carpeta propia y deja el original; la segunda vez no pisa.
        await pide(ana, 'wc schem import', /Imported [1-9]\d* schematics.*The originals were left/)
        if (!fs.existsSync(path.join(propia, 'e2e_ajeno.schem'))) throw new Error('import no copió e2e_ajeno')
        if (!fs.existsSync(path.join(carpetaWE(srv), 'e2e_ajeno.schem'))) throw new Error('import borró el original')
        await pide(ana, 'wc schem import', /Imported 0 schematics \([1-9]\d* already here/)
        fs.rmSync(path.join(propia, 'e2e_ajeno.schem'), { force: true })
        fs.rmSync(path.join(propia, 'e2e_roto.schem'), { force: true })
        fs.rmSync(path.join(carpetaWE(srv), 'e2e_ajeno.schem'), { force: true })
      }
    },

    // ---------------------------------------------------------------- spec 028: paridad con WorldEdit
    {
      id: '028 H1 CA1 · H1 CA4 · H4 CA1',
      nombre: 'una esfera con vista previa que solo ve Ana, confirm y undo; una pirámide encima de Ana y unstuck la saca',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        const beto = bots[BETO]
        const subs = (await ana.bot.tabComplete('/wc s')).map(x => x.match ?? x)
        for (const s of ['sphere', 'stack', 'smooth', 'snow']) if (!subs.includes(s)) throw new Error(`no sugiere ${s}: ${subs}`)
        await pide(ana, 'wc help sphere', /Example: \/wc sphere glass 8 --preview/)
        // Esfera de radio 4 apoyada en los pies (centro en y = -56): la cima en -52.
        await pide(ana, 'wc sphere glass 4 --raised --preview', /Preview ready/)
        await ana.hasta('ver la cima de la esfera (falsa)', b => bloque(b, X, -52, Z) === 'glass')
        if (bloque(beto.bot, X, -52, Z) !== 'air') throw new Error('Beto ve la vista previa')
        const esfera = await obra(ana, 'wc confirm')
        const r = 4.5
        let esperado = 0
        for (let dx = -4; dx <= 4; dx++) for (let dy = -4; dy <= 4; dy++) for (let dz = -4; dz <= 4; dz++) if (dx * dx + dy * dy + dz * dz <= r * r) esperado++
        if (esfera.cambiados !== esperado) throw new Error(`la esfera cambió ${esfera.cambiados}, se esperaban ${esperado}`)
        await beto.hasta('ver la esfera real', b => bloque(b, X, -52, Z) === 'glass' && bloque(b, X + 4, -56, Z) === 'glass')
        await pide(ana, 'wc undo', new RegExp(`Undone #${esfera.id} \\(sphere glass 4\\)`))
        await beto.hasta('ver que se fue', b => bloque(b, X, -52, Z) === 'air')
        // Pirámide de 4 sobre los pies de Ana: queda adentro; unstuck la deja encima de la punta (y = -56).
        const pir = await obra(ana, 'wc pyramid sandstone 4')
        if (pir.cambiados !== 7 * 7 + 5 * 5 + 3 * 3 + 1) throw new Error(`la pirámide cambió ${pir.cambiados}`)
        await pide(ana, 'wc unstuck', /Whoosh!/)
        await ana.hasta('estar sobre la punta', b => Math.floor(b.entity.position.y) === -56)
        await pide(ana, 'wc undo', /Undone .*pyramid/)
      }
    },
    {
      id: '028 H2 CA1 · H2 CA2 · H2 CA7',
      nombre: 'stack repite una columna con su cofre hacia el este, move la sube con --select y undo deshace las dos',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await srv.consola(`setblock ${X + 2} -60 ${Z + 2} gold_block`)
        await srv.consola(`setblock ${X + 2} -59 ${Z + 2} chest{Items:[{Slot:0b,id:"minecraft:diamond",count:3}]}`)
        await srv.sincronizar()
        await seleccionar(ana, [X + 2, -60, Z + 2], [X + 2, -59, Z + 2])
        const st = await obra(ana, 'wc stack 3 east')
        if (st.cambiados !== 6) throw new Error(`stack cambió ${st.cambiados}`)
        await ana.hasta('ver las tres copias', b => [3, 4, 5].every(k => bloque(b, X + k, -60, Z + 2) === 'gold_block' && bloque(b, X + k, -59, Z + 2) === 'chest'))
        await srv.consola(`data get block ${X + 5} -59 ${Z + 2} Items`, { espera: /count: 3.*diamond|diamond.*count: 3/ })
        await pide(ana, 'wc sel', /1×2×1/)
        const mv = await obra(ana, 'wc move 4 up --select')
        if (mv.cambiados !== 4) throw new Error(`move cambió ${mv.cambiados}`)
        await ana.hasta('ver la columna arriba y vacío abajo', b => bloque(b, X + 2, -56, Z + 2) === 'gold_block' && bloque(b, X + 2, -60, Z + 2) === 'air')
        await pide(ana, 'wc sel', new RegExp(`${X + 2} -56 ${Z + 2}|1×2×1`))
        await pide(ana, 'wc undo 2', /Undone/)
        await ana.hasta('ver solo la columna original', b => bloque(b, X + 2, -60, Z + 2) === 'gold_block' && bloque(b, X + 3, -60, Z + 2) === 'air' && bloque(b, X + 2, -56, Z + 2) === 'air')
        await srv.consola(`fill ${X + 2} -60 ${Z + 2} ${X + 2} -59 ${Z + 2} air`)
      }
    },
    {
      id: '028 H3 CA1 · H3 CA5 · H4 CA1 · H4 CA2 · H4 CA3',
      nombre: 'drain vacía el estanque y undo lo devuelve; ascend, descend y thru; up sin vuelo pone un vidrio que undo quita; Beto no navega',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        const beto = bots[BETO]
        await pide(beto, 'wc ascend', /You don't have permission/)
        // Un estanque de 3×3 en el pasto.
        await srv.consola(`fill ${X + 4} -61 ${Z + 4} ${X + 6} -61 ${Z + 6} water`)
        await srv.sincronizar()
        const dr = await obra(ana, 'wc drain 10') // la esquina lejana está a √73: con radio 8 queda fuera de la esfera
        if (dr.cambiados !== 9) throw new Error(`drain cambió ${dr.cambiados}`)
        await ana.hasta('ver el estanque vacío', b => bloque(b, X + 5, -61, Z + 5) === 'air')
        await pide(ana, 'wc undo', /Undone .*drain/)
        await ana.hasta('ver el agua de vuelta', b => bloque(b, X + 5, -61, Z + 5) === 'water')
        await srv.consola(`fill ${X + 4} -61 ${Z + 4} ${X + 6} -61 ${Z + 6} grass_block`)
        // Un techo en y = -57 y una pared al sur.
        await srv.consola(`fill ${X - 2} -57 ${Z - 2} ${X + 2} -57 ${Z + 1} stone`)
        await srv.consola(`fill ${X - 1} -60 ${Z + 2} ${X + 1} -58 ${Z + 2} stone`)
        await srv.sincronizar()
        await pide(ana, 'wc ascend', /Whoosh!/)
        await ana.hasta('estar sobre el techo', b => Math.floor(b.entity.position.y) === -56)
        await pide(ana, 'wc descend', /Whoosh!/)
        await ana.hasta('estar abajo otra vez', b => Math.floor(b.entity.position.y) === -60)
        await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5} 0 0`)
        await srv.sincronizar()
        await pide(ana, 'wc thru', /Whoosh!/)
        await ana.hasta('estar del otro lado de la pared', b => Math.floor(b.entity.position.z) === Z + 3)
        // Sin poder volar: up pone un vidrio bajo los pies (una obra) y undo lo quita.
        await srv.consola(`gamemode survival ${ANA}`)
        await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 5.5} 0 0`)
        await srv.sincronizar()
        const up = await obra(ana, 'wc up 6')
        if (up.cambiados !== 1) throw new Error(`up cambió ${up.cambiados}`)
        await ana.hasta('estar sobre el vidrio', b => Math.floor(b.entity.position.y) === -54 && bloque(b, X, -55, Z + 5) === 'glass')
        await srv.consola(`gamemode creative ${ANA}`)
        await pide(ana, 'wc undo', /Undone .*up/)
        await ana.hasta('ver el vidrio quitado', b => bloque(b, X, -55, Z + 5) === 'air')
        await srv.consola(`fill ${X - 2} -60 ${Z - 2} ${X + 2} -57 ${Z + 2} air`)
      }
    },

    // ---------------------------------------------------------------- spec 029: selección, información y máscaras
    {
      id: '029 H1 CA1 · H1 CA5 · H2 CA1 · H2 CA2 · H3 CA1 · H4',
      nombre: 'hpos con la mira; count y distr; ^tipo conserva el giro de la escalera; «and !surface» con la foto; gmask solo cambia la piedra',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        // Una capa de piedra de 5×5 (y = -60) sobre tierra y una escalera encima del centro. Tierra y no pasto: el pasto
        // tapado se vuelve tierra con los ticks al azar y la cuenta dependería del tiempo.
        await srv.consola(`fill ${X} -61 ${Z + 2} ${X + 4} -61 ${Z + 6} dirt`)
        await srv.consola(`fill ${X} -60 ${Z + 2} ${X + 4} -60 ${Z + 6} stone`)
        await srv.consola(`setblock ${X + 2} -59 ${Z + 4} oak_stairs[facing=west,half=top]`)
        await srv.consola(`setblock ${X + 4} -57 ${Z + 6} gold_block`) // en el aire: la mira no choca antes con el piso
        await srv.sincronizar()
        await ana.hasta('ver la piedra', b => bloque(b, X + 4, -60, Z + 2) === 'stone' && bloque(b, X + 2, -59, Z + 4) === 'oak_stairs')
        // H1 CA1: la esquina en el bloque que mira (más lejos que el alcance de un clic, que la varita necesita). hpos usa
        // hacia dónde mira Ana según el servidor; tras el escenario de navegación, mineflayer 4.39 dejó de mandar el giro
        // del bot (el servidor seguía con el anterior: data get entity … Rotation), así que el giro se pone con tp.
        const mirarY = async ([px, py, pz], comando, esperado) => {
          const ojo = [X + 0.5, -60 + 1.62, Z + 0.5]
          const dx = px - ojo[0], dy = py - ojo[1], dz = pz - ojo[2]
          const yaw = Math.atan2(-dx, dz) * 180 / Math.PI
          const pitch = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5} ${yaw.toFixed(2)} ${pitch.toFixed(2)}`)
          await srv.sincronizar()
          await pide(ana, comando, esperado)
        }
        await mirarY([X + 4.5, -56.5, Z + 6.5], 'wc hpos2', new RegExp(`Corner 2 at ${X + 4} -57 ${Z + 6}`))
        await srv.consola(`setblock ${X + 4} -57 ${Z + 6} air`)
        await srv.consola(`setblock ${X - 3} -57 ${Z + 8} gold_block`)
        await srv.sincronizar()
        await ana.hasta('ver el otro bloque en el aire', b => bloque(b, X - 3, -57, Z + 8) === 'gold_block')
        await mirarY([X - 2.5, -56.5, Z + 8.5], 'wc hpos1', new RegExp(`Corner 1 at ${X - 3} -57 ${Z + 8}`))
        await srv.consola(`setblock ${X - 3} -57 ${Z + 8} air`)
        await pide(ana, `wc pos1 ${X} -60 ${Z + 2}`, /Corner 1 at/)
        await pide(ana, `wc pos2 ${X + 4} -59 ${Z + 6}`, /selection 5×2×5 \(50 blocks\)/)
        await pide(ana, 'wc count stone above dirt', /25 of 50 blocks match stone above dirt/)
        await pide(ana, 'wc count exposd', /did you mean 'exposed'/)
        await pide(ana, 'wc distr', /50\.0% stone \(25\)|48\.0% air \(24\)/)
        // H3 CA1: la escalera pasa a piedra y sigue mirando al oeste, arriba.
        const tipo = await obra(ana, 'wc replace oak_stairs with ^stone_stairs')
        if (tipo.cambiados !== 1) throw new Error(`^tipo cambió ${tipo.cambiados}`)
        await ana.hasta('ver la escalera de piedra girada como antes', b => {
          const k = b.blockAt(v3(b, X + 2, -59, Z + 4))
          return k?.name === 'stone_stairs' && k.getProperties().facing === 'west' && k.getProperties().half === 'top'
        })
        // H2: solo la piedra que no es superficie (la que tiene la escalera encima), decidido con la foto de antes.
        const ns = await obra(ana, 'wc replace stone above dirt and !surface with andesite')
        if (ns.cambiados !== 1) throw new Error(`«and !surface» cambió ${ns.cambiados}`)
        await ana.hasta('ver la andesita bajo la escalera', b => bloque(b, X + 2, -60, Z + 4) === 'andesite')
        // H4: con la máscara global, set solo cambia la piedra (24): ni la andesita, ni la escalera, ni el aire.
        await pide(ana, 'wc gmask stone', /Global mask stone/)
        await pide(ana, 'wc gmask exposed', /a global mask can't/)
        const g = await obra(ana, 'wc set gold_block')
        if (g.cambiados !== 24) throw new Error(`set con gmask cambió ${g.cambiados}`)
        await ana.hasta('ver el oro y lo demás igual', b => bloque(b, X, -60, Z + 2) === 'gold_block' && bloque(b, X + 2, -60, Z + 4) === 'andesite' &&
          bloque(b, X, -59, Z + 2) === 'air')
        await pide(ana, 'wc gmask off', /Global mask removed/)
        await pide(ana, 'wc undo 3', /Undone/)
        await ana.hasta('ver la piedra otra vez', b => bloque(b, X, -60, Z + 2) === 'stone' && bloque(b, X + 2, -60, Z + 4) === 'stone')
        await srv.consola(`fill ${X} -60 ${Z + 2} ${X + 4} -59 ${Z + 6} air`)
        await srv.consola(`fill ${X} -61 ${Z + 2} ${X + 4} -61 ${Z + 6} grass_block`)
      }
    },
    {
      id: '029 H1 CA2 · H1 CA3 · H1 CA4 · H1 CA6 · H1 CA7',
      nombre: 'outset, inset, chunk y trim; copy --mask y paste --select en otro lugar y --origin donde se copió; clearclipboard; clearhistory pide confirm',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await srv.consola(`fill ${X + 2} -60 ${Z + 2} ${X + 4} -60 ${Z + 4} stone`)
        await srv.consola(`setblock ${X + 3} -60 ${Z + 3} gold_block`)
        await srv.sincronizar()
        await seleccionar(ana, [X + 3, -60, Z + 3], [X + 3, -60, Z + 3])
        await pide(ana, 'wc outset 1 --horizontal', /Selection now 3×1×3 \(9 blocks\)/)
        await pide(ana, 'wc outset 2', /Selection now 7×5×7/)
        await pide(ana, 'wc inset 4', /would leave the selection empty/)
        await pide(ana, 'wc trim stone, gold_block', /Selection now 3×1×3 \(9 blocks\)/)
        await pide(ana, 'wc trim gold_block', /Selection now 1×1×1/)
        await pide(ana, 'wc chunk', /Selection now 16×384×16/)
        await seleccionar(ana, [X + 2, -60, Z + 2], [X + 4, -60, Z + 4])
        // H1 CA7: solo el oro va al portapapeles; la piedra queda como aire y no se pega.
        await pide(ana, 'wc copy --mask gold_block', /Copied 3×1×3/)
        await pide(ana, 'wc distr --clipboard', /11\.1% gold_block \(1\)/)
        await srv.consola(`tp ${ANA} ${X + 10.5} -60 ${Z + 0.5} 0 0`)
        await srv.sincronizar()
        await ana.hasta('estar en el otro lugar', b => Math.abs(b.entity.position.x - (X + 10.5)) < 0.6)
        const p = await obra(ana, 'wc paste --select')
        if (p.cambiados !== 1) throw new Error(`paste cambió ${p.cambiados}`)
        await ana.hasta('ver el oro copiado sin la piedra', b => bloque(b, X + 13, -60, Z + 3) === 'gold_block' && bloque(b, X + 12, -60, Z + 2) === 'air')
        await pide(ana, 'wc sel', new RegExp(`3×1×3 .* from ${X + 12} -60 ${Z + 2}`))
        await srv.consola(`setblock ${X + 3} -60 ${Z + 3} air`)
        await srv.sincronizar() // la consola no espera (002 F-03)
        await ana.hasta('ver que el oro original se fue', b => bloque(b, X + 3, -60, Z + 3) === 'air')
        const o = await obra(ana, 'wc paste --origin')
        if (o.cambiados !== 1) throw new Error(`paste --origin cambió ${o.cambiados}`)
        await ana.hasta('ver el oro de vuelta donde se copió', b => bloque(b, X + 3, -60, Z + 3) === 'gold_block')
        await pide(ana, 'wc clearclipboard', /Clipboard emptied/)
        await pide(ana, 'wc paste', /Your clipboard is empty/)
        // H1 CA6: borrar el historial pide confirmación; después no hay nada que deshacer.
        await pide(ana, 'wc clearhistory', /This deletes your \d+ saved edits/)
        await pide(ana, 'wc confirm', /History deleted \(\d+ edits\)/)
        await pide(ana, 'wc undo', /nothing to undo/)
        await srv.consola(`fill ${X} -60 ${Z} ${X + 16} -60 ${Z + 6} air`)
      }
    },
    {
      id: '030 H1 CA1 · H1 CA2 · H2 CA6 · H3 · H4 CA2 · H4 CA5',
      nombre: 'un cilindro con la varita: centro con el clic izquierdo, radios con el derecho, el contorno solo para Ana, walls es el tubo',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        try {
          await pide(ana, 'wc wand', /Here is your wand/)
          await ana.hasta('tener la varita en la mano', b => b.heldItem?.name === 'golden_axe')
          await pide(ana, 'wc sel cyll', /did you mean 'cyl'/)
          await pide(ana, 'wc sel cyl', /Selecting a cylinder: left click the center/)
          // Clic izquierdo (empezar a romper) = el centro; clic derecho = los radios hasta ese bloque (como WorldEdit).
          let m = ana.marca()
          ana.bot._client.write('block_dig', { status: 0, location: v3(ana.bot, X + 2, -61, Z + 2), face: 1, sequence: 1 })
          await ana.esperar('chat', new RegExp(`Center at ${X + 2} -61 ${Z + 2} · cyl 1×1×1 \\(1 blocks\\)`), { desde: m })
          m = ana.marca()
          await ana.bot.activateBlock(ana.bot.blockAt(v3(ana.bot, X + 4, -61, Z + 2)))
          await ana.esperar('chat', /Radii 2, 0 at .* · cyl 5×1×1 \(5 blocks\)/, { desde: m })
          m = ana.marca()
          await ana.bot.activateBlock(ana.bot.blockAt(v3(ana.bot, X + 2, -61, Z + 4)))
          await ana.esperar('chat', /Radii 2, 2 at .* · cyl 5×1×5 \(21 blocks\)/, { desde: m })
          await comprobar(srv, `block ${X + 2} -61 ${Z + 2} grass_block`) // la varita no rompe
          // H3: el contorno del cilindro lo ve solo Ana.
          const mb = bots[BETO].marca()
          await ana.esperar('particle', /dust/, { desde: m, timeoutMs: 5000 })
          await bots[BETO].nunca('particle', /dust/, { desde: mb, ms: 1500 })
          // H4 CA5: el alto de un cilindro crece hacia arriba; hacia un lado no (se dice).
          await pide(ana, 'wc expand 3 up', /Selection now 5×4×5 \(84 blocks\)/)
          await pide(ana, 'wc expand 2 north', /does not work on a cyl selection/)
          await pide(ana, 'wc sel', new RegExp(`Selection cyl: center ${X + 2} ${Z + 2}, radii 2, 2; y -61 to -58 · 84 blocks`))
          // H4 CA2: walls = el tubo (12 columnas del borde × 4), sin tocar el centro ni las esquinas de la caja.
          const w = await obra(ana, 'wc walls glass')
          if (w.cambiados !== 48) throw new Error(`walls del cilindro cambió ${w.cambiados}`)
          await ana.hasta('ver el tubo de vidrio', b => bloque(b, X + 4, -59, Z + 2) === 'glass' && bloque(b, X + 2, -59, Z + 2) === 'air' &&
            bloque(b, X + 4, -59, Z + 4) === 'air' && bloque(b, X + 3, -58, Z + 4) === 'glass')
          await pide(ana, 'wc undo', /Undone/)
          await ana.hasta('ver el pasto otra vez', b => bloque(b, X + 4, -61, Z + 2) === 'grass_block' && bloque(b, X + 4, -59, Z + 2) === 'air')
        } finally {
          await pide(ana, 'wc sel cuboid', /Selecting a cuboid/)
        }
      }
    },
    {
      id: '030 H2 CA3 · H2 CA5 · H2 CA7 · H2 CA8 · H4 CA1 · H4 CA3 · H4 CA4',
      nombre: 'poly, mágica (con el clic derecho suma), esfera copiada y pegada sin sus esquinas, stack y move de la esfera, convexa',
      timeoutMs: 150000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        try {
          // H2 CA3: un triángulo de 3 vértices, del más bajo al más alto (y -60 a -58): 28 columnas × 3.
          await pide(ana, 'wc sel poly', /Selecting a polygon/)
          await pide(ana, `wc pos1 ${X} -60 ${Z + 10}`, /Point 1 at/)
          await pide(ana, `wc pos2 ${X + 6} -60 ${Z + 10}`, /Point 2 at/)
          await pide(ana, `wc pos2 ${X} -58 ${Z + 16}`, /Point 3 at .* · poly 7×3×7 \(84 blocks\)/)
          await pide(ana, 'wc sel', /Selection poly: 3 points: .*; y -60 to -58 · 84 blocks/)
          const p = await obra(ana, 'wc set stone')
          if (p.cambiados !== 84) throw new Error(`set del triángulo cambió ${p.cambiados}`)
          await ana.hasta('ver el triángulo', b => bloque(b, X + 1, -58, Z + 11) === 'stone' && bloque(b, X + 3, -60, Z + 13) === 'stone' &&
            bloque(b, X + 6, -60, Z + 16) === 'air' && bloque(b, X + 4, -60, Z + 13) === 'air')
          // H2 CA8: la mágica toma la piedra conectada; el clic derecho (pos2) suma otro grupo.
          await srv.consola(`fill ${X + 10} -60 ${Z + 10} ${X + 11} -60 ${Z + 10} stone`)
          await srv.sincronizar()
          await pide(ana, 'wc sel magic', /Selecting magic/)
          await pide(ana, `wc pos1 ${X + 1} -60 ${Z + 11}`, /Magic: 84 connected stone blocks\./)
          await pide(ana, `wc pos2 ${X + 10} -60 ${Z + 10}`, /Magic: added 2 connected stone blocks · now 86/)
          const g = await obra(ana, 'wc set gold_block')
          if (g.cambiados !== 86) throw new Error(`set de la mágica cambió ${g.cambiados}`)
          await ana.hasta('ver el oro en los dos grupos', b => bloque(b, X + 11, -60, Z + 10) === 'gold_block' && bloque(b, X + 2, -59, Z + 12) === 'gold_block')
          await srv.consola(`fill ${X} -60 ${Z + 10} ${X + 11} -58 ${Z + 16} air`)
          // H2 CA5 y H4 CA3: una esfera de radio 2 en el aire; copiada y pegada con --air no pisa las esquinas de su caja.
          await pide(ana, 'wc sel sphere', /Selecting a sphere/)
          await pide(ana, `wc pos1 ${X + 3} -56 ${Z + 25}`, /Center at/)
          const r = await pide(ana, `wc pos2 ${X + 5} -56 ${Z + 25}`, /Radius 2 at .* · sphere 5×5×5 \((\d+) blocks\)/)
          const vol = Number(r.texto.match(/\((\d+) blocks\)/)[1])
          if (vol >= 125 || vol < 30) throw new Error(`la esfera tiene ${vol}`)
          const e = await obra(ana, 'wc set stone')
          if (e.cambiados !== vol) throw new Error(`set de la esfera cambió ${e.cambiados}`)
          await pide(ana, 'wc copy', new RegExp(`Copied 5×5×5 \\(${vol} blocks\\)`))
          await srv.consola(`setblock ${X + 11} -58 ${Z + 23} gold_block`) // la esquina de la caja donde se va a pegar
          await srv.consola(`tp ${ANA} ${X + 10.5} -60 ${Z + 0.5} 0 0`)
          await srv.sincronizar()
          await ana.hasta('estar en el otro lugar', b => Math.abs(b.entity.position.x - (X + 10.5)) < 0.6)
          const pg = await obra(ana, 'wc paste --air')
          if (pg.cambiados !== vol) throw new Error(`paste --air de la esfera cambió ${pg.cambiados}`)
          await ana.hasta('ver la esfera pegada y la esquina intacta', b => bloque(b, X + 13, -56, Z + 25) === 'stone' &&
            bloque(b, X + 11, -58, Z + 23) === 'gold_block' && bloque(b, X + 15, -56, Z + 25) === 'stone')
          // H4 CA4: stack repite la esfera (no su caja) y move --select la mueve con la selección.
          const st = await obra(ana, 'wc stack 1 up')
          if (st.cambiados !== vol) throw new Error(`stack de la esfera cambió ${st.cambiados}`)
          await ana.hasta('ver la copia de arriba', b => bloque(b, X + 3, -51, Z + 25) === 'stone' && bloque(b, X + 1, -53, Z + 23) === 'air')
          await obra(ana, 'wc move 2 east --select')
          await pide(ana, 'wc sel', new RegExp(`Selection sphere: center ${X + 5} -56 ${Z + 25}, radius 2`))
          await ana.hasta('ver la esfera movida', b => bloque(b, X + 7, -56, Z + 25) === 'stone' && bloque(b, X + 1, -56, Z + 25) === 'air')
          // H2 CA7: una convexa de 4 vértices (un tetraedro) cuenta sus bloques de verdad.
          await pide(ana, 'wc sel convex', /Selecting a convex shape/)
          await pide(ana, `wc pos1 ${X + 20} -60 ${Z + 10}`, /Point 1 at/)
          await pide(ana, `wc pos2 ${X + 24} -60 ${Z + 10}`, /Point 2 at/)
          await pide(ana, `wc pos2 ${X + 20} -56 ${Z + 10}`, /Point 3 at/)
          const t = await pide(ana, `wc pos2 ${X + 20} -60 ${Z + 14}`, /Point 4 at .* · convex 5×5×5 \((\d+) blocks\)/)
          const tv = Number(t.texto.match(/\((\d+) blocks\)/)[1])
          const c = await obra(ana, 'wc set glass')
          if (c.cambiados !== tv || tv >= 125) throw new Error(`set de la convexa cambió ${c.cambiados} de ${tv}`)
          await ana.hasta('ver el tetraedro', b => bloque(b, X + 20, -60, Z + 10) === 'glass' && bloque(b, X + 24, -56, Z + 14) === 'air')
          await pide(ana, 'wc undo 6', /Undone/)
        } finally {
          await pide(ana, 'wc sel cuboid', /Selecting a cuboid/)
          await srv.consola(`fill ${X} -60 ${Z + 9} ${X + 26} -50 ${Z + 28} air`)
        }
      }
    },
    {
      id: '031 H1 CA1 · H1 CA2 · H1 CA3 · H3 CA1 · H3 CA3',
      nombre: 'copy --entities trae el soporte con su casco, el marco con su manzana y la vaca; girado, el marco cuelga de la otra pared; undo y cut --entities',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        try {
          await borrarEntidades(srv)
          await construirSala(srv)
          await seleccionar(ana, [X + 2, -60, Z + 2], [X + 4, -59, Z + 4])
          // Sin --entities no se copia ninguna (como //copy de WorldEdit).
          const m0 = ana.marca()
          await pide(ana, 'wc copy', /Copied 3×2×3 \(18 blocks\)/)
          await ana.nunca('chat', /entity\(ies\)/, { desde: m0, ms: 800 })
          await pide(ana, 'wc copy --entities', /\.\.\. and 3 entity\(ies\)/)
          await pide(ana, 'wc rotate 90', /Clipboard turned 90°/)
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 20.5}`)
          await ana.hasta('llegar', b => Math.abs(b.entity.position.z - (Z + 20.5)) < 0.6)
          // (dx, dz) desde Ana gira a (-dz, dx): el marco (3, 3) va a (X-3, Z+23) con la pared (3, 4) en (X-4, Z+23).
          await pide(ana, 'wc paste', /Entities: 3 added, 0 removed/)
          await comprobar(srv, `block ${X - 4} -60 ${Z + 23} stone`)
          await comprobar(srv, `entity ${en('item_frame', X - 3, -60, Z + 23)}`)
          await srv.consola(`data get entity ${en1('item_frame', X - 3, -60, Z + 23)} Facing`, { espera: /entity data: 5b/ })
          await srv.consola(`data get entity ${en1('item_frame', X - 3, -60, Z + 23)} Item.id`, { espera: /minecraft:apple/ })
          await comprobar(srv, `entity ${en('armor_stand', X - 2, -60, Z + 22)}`)
          await srv.consola(`data get entity ${en1('armor_stand', X - 2, -60, Z + 22)} ArmorItems[3].id`, { espera: /minecraft:diamond_helmet/ })
          await comprobar(srv, `entity ${en('cow', X - 2, -60, Z + 24)}`)
          // Deshacer quita las tres pegadas (las de la sala siguen).
          await pide(ana, 'wc undo', /Entities: 0 back, 3 removed/)
          if (await cuantas(srv, 'item_frame', [X - 6, X], [-61, -58], [Z + 19, Z + 26]) !== 0) throw new Error('quedó el marco pegado')
          if (await cuantas(srv, 'item_frame', [X + 2, X + 4], [-61, -58], [Z + 2, Z + 4]) !== 1) throw new Error('falta el marco de la sala')
          // cut --entities las quita (antes que la pared: el marco no se cae como ítem) y undo las devuelve.
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5}`)
          await pide(ana, 'wc cut --entities', /Entities: 0 added, 3 removed/)
          if (await cuantas(srv, 'cow', [X + 2, X + 4], [-61, -58], [Z + 2, Z + 4]) !== 0) throw new Error('la vaca sigue')
          if (await cuantas(srv, 'item', [X - 5, X + 10], [-62, -55], [Z - 5, Z + 10]) !== 0) throw new Error('el marco se cayó como ítem')
          await pide(ana, 'wc undo', /Entities: 3 back, 0 removed/)
          await srv.consola(`data get entity ${en1('item_frame', X + 3, -60, Z + 3)} Item.id`, { espera: /minecraft:apple/ })
          await comprobar(srv, `entity ${en('cow', X + 4, -60, Z + 2)}`)
          // --no-entities pega solo los bloques.
          await pide(ana, 'wc copy --entities', /and 3 entity/)
          await srv.consola(`tp ${ANA} ${X + 10.5} -60 ${Z + 0.5}`)
          await ana.hasta('llegar', b => Math.abs(b.entity.position.x - (X + 10.5)) < 0.6)
          const p = await obra(ana, 'wc paste --no-entities')
          if (p.cambiados !== 1) throw new Error(`paste --no-entities cambió ${p.cambiados}`)
          if (await cuantas(srv, 'cow', [X + 12, X + 14], [-61, -58], [Z + 2, Z + 4]) !== 0) throw new Error('--no-entities pegó la vaca')
          await pide(ana, 'wc undo', /Undone/)
        } finally {
          await borrarEntidades(srv)
          await srv.consola(`fill ${X - 6} -60 ${Z} ${X + 16} -58 ${Z + 26} air`)
        }
      }
    },
    {
      id: '031 H1 CA4 · H2 CA1 · H2 CA2 · H4 CA1 · H4 CA2 · H4 CA3 · H5 CA1 · H5 CA2',
      nombre: 'move y stack con --entities; paste --remove-entities; butcher salva al lobo de alguien y al aldeano, --animals se lleva la vaca y undo la devuelve; remove items',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        try {
          await borrarEntidades(srv)
          await srv.consola(`summon armor_stand ${X + 2.5} -60 ${Z + 2.5} {NoGravity:1b,CustomName:'"Pepe"'}`)
          await srv.sincronizar()
          await seleccionar(ana, [X + 2, -60, Z + 2], [X + 2, -60, Z + 2])
          // move --entities lleva el soporte (el mismo, con su nombre) 3 al este; stack --entities lo repite.
          await pide(ana, 'wc move 3 east --entities --select', /Entities: 1 added, 1 removed/)
          await srv.consola(`data get entity ${en1('armor_stand', X + 5, -60, Z + 2)} CustomName`, { espera: /Pepe/ })
          if (await cuantas(srv, 'armor_stand', [X + 2, X + 2], [-61, -59], [Z + 2, Z + 2]) !== 0) throw new Error('quedó el soporte atrás')
          await pide(ana, 'wc stack 2 east --entities', /Entities: 2 added, 0 removed/)
          if (await cuantas(srv, 'armor_stand', [X + 5, X + 7], [-61, -59], [Z + 2, Z + 2]) !== 3) throw new Error('stack no repitió el soporte')
          // paste --remove-entities: pegar un bloque vacío encima quita los soportes de ahí.
          await seleccionar(ana, [X + 20, -60, Z + 20], [X + 22, -60, Z + 22])
          await pide(ana, 'wc copy', /Copied/)
          await srv.consola(`tp ${ANA} ${X - 14.5} -60 ${Z - 17.5}`)
          await ana.hasta('llegar', b => Math.abs(b.entity.position.x - (X - 14.5)) < 0.6)
          await pide(ana, 'wc paste --remove-entities', /Entities: 0 added, 3 removed \(3 armor_stand\)/)
          await pide(ana, 'wc undo 3', /Undone/)
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5}`)
          await ana.hasta('volver', b => Math.abs(b.entity.position.x - (X + 0.5)) < 0.6)
          await srv.sincronizar()
          // butcher: el lobo de alguien (mascota), el aldeano y la vaca no son hostiles: de fábrica no quita nada.
          await srv.consola(`summon wolf ${X + 3.5} -60 ${Z + 5.5} {NoAI:1b,Owner:[I;1,2,3,4]}`)
          await srv.consola(`summon villager ${X + 4.5} -60 ${Z + 5.5} {NoAI:1b}`)
          await srv.consola(`summon cow ${X + 5.5} -60 ${Z + 5.5} {NoAI:1b}`)
          await srv.consola(`summon item ${X + 6.5} -60 ${Z + 5.5} {Item:{id:"minecraft:stick",count:1},PickupDelay:32767}`)
          await srv.sincronizar()
          const m = ana.marca()
          await pide(ana, 'wc butcher 10', /Done: butcher/)
          await ana.nunca('chat', /Entities:/, { desde: m, ms: 800 })
          await pide(ana, 'wc butcher 10 --animals', /Entities: 0 added, 1 removed \(1 cow\)/)
          if (await cuantas(srv, 'wolf', [X, X + 8], [-61, -58], [Z + 4, Z + 7]) !== 1) throw new Error('--animals mató al lobo de alguien')
          await pide(ana, 'wc undo', /Undone #\d+ \(butcher 10 --animals\)/)
          if (await cuantas(srv, 'cow', [X, X + 8], [-61, -58], [Z + 4, Z + 7]) !== 1) throw new Error('undo no devolvió la vaca')
          await pide(ana, 'wc remove itms 10', /did you mean 'items'/)
          await pide(ana, 'wc remove items 10', /Entities: 0 added, 1 removed \(1 item\)/)
          // Beto no tiene worldcraft.butcher.
          await pide(bots[BETO], 'wc butcher', /You don't have permission/)
          const sug = (await ana.bot.tabComplete('/wc butcher 10 --fr')).map(x => x.match ?? x)
          if (!sug.includes('--friendly')) throw new Error(`no sugiere --friendly: ${sug}`)
        } finally {
          await borrarEntidades(srv)
          await srv.consola(`fill ${X - 20} -60 ${Z - 20} ${X + 25} -58 ${Z + 25} air`)
        }
      }
    },
    {
      id: '031 H6 CA1 · H6 CA2',
      nombre: 'un esquema con entidades guardado por Worldcraft lo pega WorldEdit con //paste -e, y uno de WorldEdit (//copy -e) lo pega Worldcraft',
      timeoutMs: 150000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        const we = carpetaWE(srv)
        try {
          await borrarEntidades(srv)
          await construirSala(srv)
          await seleccionar(ana, [X + 2, -60, Z + 2], [X + 4, -59, Z + 4])
          await pide(ana, 'wc copy --entities', /and 3 entity/)
          await pide(ana, 'wc schem save e2e_ent --overwrite', /Saved e2e_ent/)
          fs.mkdirSync(we, { recursive: true })
          fs.copyFileSync(path.join(srv.run, 'plugins', 'Worldcraft', 'schematics', 'e2e_ent.schem'), path.join(we, 'e2e_ent.schem'))
          // WorldEdit lo pega 20 al sur (relativo a Ana, como Worldcraft): la vaca y el soporte donde corresponde.
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 20.5}`)
          await ana.hasta('llegar', b => Math.abs(b.entity.position.z - (Z + 20.5)) < 0.6)
          await pide(ana, '/schem load e2e_ent', /loaded/i, 30000)
          await pide(ana, '/paste -e', /pasted/i, 30000)
          await comprobar(srv, `entity ${en('cow', X + 4, -60, Z + 22)}`)
          await comprobar(srv, `entity ${en('armor_stand', X + 2, -60, Z + 22)}`)
          await srv.consola(`data get entity ${en1('armor_stand', X + 2, -60, Z + 22)} ArmorItems[3].id`, { espera: /minecraft:diamond_helmet/ })
          await pide(ana, '/undo', /Undo successful|undid|undone/i)
          await borrarEntidades(srv)
          // Al revés: WorldEdit copia con -e y guarda; Worldcraft lo carga y lo pega con las entidades.
          await construirSala(srv)
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 0.5}`)
          await ana.hasta('volver', b => Math.abs(b.entity.position.z - (Z + 0.5)) < 0.6)
          fs.rmSync(path.join(we, 'e2e_ent_we.schem'), { force: true })
          await pide(ana, `/pos1 ${X + 2},-60,${Z + 2}`, /First position set/)
          await pide(ana, `/pos2 ${X + 4},-59,${Z + 4}`, /Second position set/)
          await pide(ana, '/copy -e', /were copied|blocks affected/)
          await pide(ana, '/schem save e2e_ent_we', /saved/i, 30000)
          await esperarArchivo(path.join(we, 'e2e_ent_we.schem'))
          await srv.consola(`tp ${ANA} ${X + 0.5} -60 ${Z + 30.5}`)
          await ana.hasta('llegar', b => Math.abs(b.entity.position.z - (Z + 30.5)) < 0.6)
          await pide(ana, 'wc schem load e2e_ent_we', /Loaded e2e_ent_we \[WorldEdit\]/)
          await pide(ana, 'wc paste', /Entities: 3 added, 0 removed/)
          await comprobar(srv, `entity ${en('cow', X + 4, -60, Z + 32)}`)
          await comprobar(srv, `entity ${en('item_frame', X + 3, -60, Z + 33)}`)
          await srv.consola(`data get entity ${en1('item_frame', X + 3, -60, Z + 33)} Item.id`, { espera: /minecraft:apple/ })
          await pide(ana, 'wc undo', /Entities: 0 back, 3 removed/)
        } finally {
          for (const n of ['e2e_ent', 'e2e_ent_we']) fs.rmSync(path.join(we, `${n}.schem`), { force: true })
          await borrarEntidades(srv)
          await srv.consola(`fill ${X - 2} -60 ${Z} ${X + 8} -58 ${Z + 36} air`)
        }
      }
    },

    // ---------------------------------------------------------------- spec 032: pinceles y herramientas
    {
      id: '032 H1 CA1 · H1 CA2 · H1 CA3 · H1 CA4 · H1 CA5 · H2 CA1 · H2 CA2',
      nombre: 'la esfera con la mano vacía: clic derecho donde mira, otra vara igual no es pincel, mask=air, undo; none la deja como era; el cilindro en la vara normal',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        await mirarSuelo(ctx)
        await pide(ana, 'wc brush sphere stone 2', /Here is your brush \(sphere · stone · radius 2\)/)
        await ana.hasta('tener la vara en la mano', b => b.heldItem?.name === 'blaze_rod')
        const mb = bots[BETO].marca()
        let m = ana.marca()
        await ana.esperar('particle', /dust/, { desde: m, timeoutMs: 5000 })
        await bots[BETO].nunca('particle', /dust/, { desde: mb, ms: 1500 })
        m = ana.marca()
        ana.bot.activateItem()
        await ana.esperar('chat', /Done: brush sphere changed [\d,]+ blocks/, { desde: m, timeoutMs: 20000 })
        await comprobar(srv, `block ${X} -61 ${Z + 8} stone`)
        await comprobar(srv, `block ${X} -59 ${Z + 8} stone`)
        await comprobar(srv, `block ${X} -58 ${Z + 8} air`)
        // Otra vara de blaze igual no es pincel (solo el ítem marcado).
        await srv.consola(`item replace entity ${ANA} hotbar.1 with minecraft:blaze_rod`)
        ana.bot.setQuickBarSlot(1)
        await ana.hasta('tener la otra vara', b => b.quickBarSlot === 1 && b.heldItem?.name === 'blaze_rod')
        m = ana.marca()
        ana.bot.activateItem()
        await ana.nunca('chat', /Done: brush/, { desde: m, ms: 1500 })
        ana.bot.setQuickBarSlot(0)
        await ana.hasta('volver al pincel', b => b.quickBarSlot === 0)
        await pide(ana, 'wc undo', /Undone .*brush sphere/)
        await ana.hasta('ver la esfera quitada', b => bloque(b, X, -59, Z + 8) === 'air')
        // mask=air: solo cambia el aire (el pasto queda).
        await pide(ana, 'wc brush mask=air', /Your brush is now sphere · stone · radius 2 · only on air/)
        m = ana.marca()
        ana.bot.activateItem()
        await ana.esperar('chat', /Done: brush sphere changed [\d,]+ blocks/, { desde: m, timeoutMs: 20000 })
        await comprobar(srv, `block ${X} -61 ${Z + 8} grass_block`)
        await comprobar(srv, `block ${X} -59 ${Z + 8} stone`)
        await pide(ana, 'wc undo', /Undone .*brush sphere/)
        // none: un ítem normal otra vez, sin nombre ni marca.
        await pide(ana, 'wc brush none', /It's a normal item again/)
        const r = await srv.consola(`data get entity ${ANA} SelectedItem`, { espera: /blaze_rod/ })
        if (/custom_data|custom_name|lore/.test(r.texto ?? String(r))) throw new Error(`la vara conserva algo: ${r.texto}`)
        m = ana.marca()
        ana.bot.activateItem()
        await ana.nunca('chat', /Done: brush/, { desde: m, ms: 1500 })
        // La vara normal de la mano se vuelve el pincel (H1 CA1); un cilindro de alto 3 desde el bloque que mira.
        await pide(ana, 'wc brush cyl glass 1 3', /Your blaze_rod is now a brush \(cyl · glass · radius 1 · height 3\)/)
        m = ana.marca()
        ana.bot.activateItem()
        await ana.esperar('chat', /Done: brush cyl changed [\d,]+ blocks/, { desde: m, timeoutMs: 20000 })
        await comprobar(srv, `block ${X} -59 ${Z + 8} glass`)
        await comprobar(srv, `block ${X + 1} -60 ${Z + 8} glass`)
        await comprobar(srv, `block ${X} -58 ${Z + 8} air`)
        await pide(ana, 'wc undo', /Undone .*brush cyl/)
        await pide(ana, 'wc brush spere stone', /Unknown brush 'spere' \(did you mean 'sphere'\?\)/)
        await srv.consola(`clear ${ANA}`)
      }
    },
    {
      id: '032 H2 CA4 · H4 CA1 · H4 CA2 · H4 CA3',
      nombre: 'gravity hace caer la piedra y el cofre se queda; clipboard pega el portapapeles donde mira; extinguish apaga el fuego; butcher --animals se lleva la vaca y undo la devuelve',
      timeoutMs: 150000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        try {
          await borrarEntidades(srv)
          await srv.consola('gamerule doFireTick false')
          // Piedra flotando en y = -58 y -57 y un cofre flotando con piedra encima, junto a donde mira Ana.
          await srv.consola(`fill ${X} -58 ${Z + 8} ${X} -57 ${Z + 8} stone`)
          await srv.consola(`setblock ${X + 1} -59 ${Z + 8} chest`)
          await srv.consola(`setblock ${X + 1} -58 ${Z + 8} stone`)
          await mirarSuelo(ctx)
          await pide(ana, 'wc brush gravity 4', /Here is your brush \(gravity · radius 4\)/)
          let m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: brush gravity changed [\d,]+ blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X} -60 ${Z + 8} stone`)
          await comprobar(srv, `block ${X} -59 ${Z + 8} stone`)
          await comprobar(srv, `block ${X} -57 ${Z + 8} air`)
          await comprobar(srv, `block ${X + 1} -59 ${Z + 8} chest`)
          await comprobar(srv, `block ${X + 1} -58 ${Z + 8} stone`)
          await pide(ana, 'wc undo', /Undone .*brush gravity/)
          await comprobar(srv, `block ${X} -57 ${Z + 8} stone`)
          await srv.consola(`fill ${X} -60 ${Z + 8} ${X + 1} -57 ${Z + 8} air`)
          // El portapapeles (dos de oro), pegado centrado en el bloque que mira.
          await srv.consola(`fill ${X + 3} -60 ${Z + 3} ${X + 3} -59 ${Z + 3} gold_block`)
          await srv.sincronizar()
          await seleccionar(ana, [X + 3, -60, Z + 3], [X + 3, -59, Z + 3])
          await pide(ana, 'wc copy', /Copied/)
          await pide(ana, 'wc brush clipboard', /Your brush is now clipboard/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: brush clipboard changed 2 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X} -61 ${Z + 8} gold_block`)
          await comprobar(srv, `block ${X} -60 ${Z + 8} gold_block`)
          await pide(ana, 'wc undo', /Undone .*brush clipboard/)
          await comprobar(srv, `block ${X} -61 ${Z + 8} grass_block`)
          // Fuego en el pasto: extinguish lo apaga (la mira lo atraviesa y cae en el pasto).
          await srv.consola(`setblock ${X + 1} -60 ${Z + 8} fire`)
          await comprobar(srv, `block ${X + 1} -60 ${Z + 8} fire`)
          await pide(ana, 'wc brush extinguish 3', /Your brush is now extinguish · radius 3/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: brush extinguish changed 1 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X + 1} -60 ${Z + 8} air`)
          // butcher: sin opciones no toca la vaca; con --animals se la lleva; undo la devuelve.
          await srv.consola(`summon cow ${X + 1.5} -60 ${Z + 8.5} {NoAI:1b}`)
          await srv.sincronizar()
          await pide(ana, 'wc brush butcher 3', /Your brush is now butcher · radius 3/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: brush butcher/, { desde: m, timeoutMs: 20000 })
          if (await cuantas(srv, 'cow', [X - 3, X + 3], [-62, -56], [Z + 5, Z + 11]) !== 1) throw new Error('butcher sin opciones mató la vaca')
          await pide(ana, 'wc brush butcher 3 --animals', /Your brush is now butcher · radius 3 · animals/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Entities: 0 added, 1 removed \(1 cow\)/, { desde: m, timeoutMs: 20000 })
          if (await cuantas(srv, 'cow', [X - 3, X + 3], [-62, -56], [Z + 5, Z + 11]) !== 0) throw new Error('la vaca sigue')
          await pide(ana, 'wc undo', /Undone .*brush butcher/)
          await comprobar(srv, `entity ${en('cow', X + 1, -60, Z + 8)}`)
        } finally {
          await borrarEntidades(srv)
          await srv.consola('gamerule doFireTick true')
          await srv.consola(`fill ${X - 2} -60 ${Z} ${X + 4} -56 ${Z + 10} air`)
          await srv.consola(`fill ${X - 2} -61 ${Z} ${X + 4} -61 ${Z + 10} grass_block`)
          await srv.consola(`clear ${ANA}`)
        }
      }
    },
    {
      id: '032 H5 CA1 · H5 CA2 · H5 CA3 · H5 CA4 · H5 CA5 · H5 CA6 · H5 CA7 · H5 CA8',
      nombre: 'herramientas: repl toma y pone el tronco girado, cycler lo gira, info lo dice, farwand marca de lejos, floodfill solo lo conectado, deltree quita el árbol flotante y no el plantado, lrbuild y navwand',
      timeoutMs: 180000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        try {
          await srv.consola(`setblock ${X} -61 ${Z + 8} oak_log[axis=x]`)
          await mirarSuelo(ctx)
          await pide(ana, 'wc tool repl stone', /Here is your tool \(repl · stone\)/)
          let m = ana.marca()
          ana.bot.swingArm()
          await ana.esperar('actionbar', /Replace with oak_log\[axis=x\]/, { desde: m })
          await srv.consola(`setblock ${X} -61 ${Z + 8} grass_block`)
          await srv.sincronizar()
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: tool repl changed 1 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X} -61 ${Z + 8} oak_log[axis=x]`)
          // cycler: el eje siguiente; clic izquierdo dice la propiedad.
          await pide(ana, 'wc tool cycler', /Your tool is now cycler/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: tool cycler changed 1 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X} -61 ${Z + 8} oak_log[axis=y]`)
          m = ana.marca()
          ana.bot.swingArm()
          await ana.esperar('actionbar', /Cycler axis = y/, { desde: m })
          // info: el estado completo, el bioma y la luz, sin cambiar nada.
          await pide(ana, 'wc tool info', /Your tool is now info/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', new RegExp(`${X} -61 ${Z + 8} · minecraft:oak_log\\[axis=y\\] · biome minecraft:\\w+ · light`), { desde: m })
          // farwand: las esquinas de lejos.
          await pide(ana, 'wc tool farwand', /Your tool is now farwand/)
          m = ana.marca()
          ana.bot.swingArm()
          await ana.esperar('chat', new RegExp(`Corner 1 at ${X} -61 ${Z + 8}`), { desde: m })
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', new RegExp(`Corner 2 at ${X} -61 ${Z + 8}`), { desde: m })
          // floodfill: la lana roja conectada se vuelve azul; la suelta queda roja.
          await srv.consola(`fill ${X - 1} -61 ${Z + 8} ${X + 1} -61 ${Z + 8} red_wool`)
          await srv.consola(`setblock ${X + 3} -61 ${Z + 8} red_wool`)
          await srv.sincronizar()
          await pide(ana, 'wc tool floodfill blue_wool 5', /Your tool is now floodfill · blue_wool · range 5/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: tool floodfill changed 3 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X - 1} -61 ${Z + 8} blue_wool`)
          await comprobar(srv, `block ${X + 3} -61 ${Z + 8} red_wool`)
          // lrbuild: derecho pone piedra contra la cara que mira; izquierdo (aire) la quita.
          await pide(ana, 'wc tool lrbuild air stone', /Your tool is now lrbuild · left air · right stone/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: tool lrbuild changed 1 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X} -60 ${Z + 8} stone`)
          m = ana.marca()
          ana.bot.swingArm()
          await ana.esperar('chat', /Done: tool lrbuild changed 1 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X} -60 ${Z + 8} air`)
          // deltree: un tronco flotante con hojas en la mira (a 3 bloques) se quita entero.
          await srv.consola(`setblock ${X} -59 ${Z + 3} oak_log`)
          await srv.consola(`fill ${X} -58 ${Z + 3} ${X + 1} -58 ${Z + 3} oak_leaves[persistent=true]`)
          await srv.sincronizar()
          await pide(ana, 'wc tool deltree', /Your tool is now deltree/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Done: tool deltree changed 3 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X + 1} -58 ${Z + 3} air`)
          // Uno plantado en el pasto (a 5 bloques) no se toca.
          await srv.consola(`setblock ${X} -60 ${Z + 5} oak_log`)
          await srv.sincronizar()
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /touches the ground/, { desde: m })
          await comprobar(srv, `block ${X} -60 ${Z + 5} oak_log`)
          await srv.consola(`setblock ${X} -60 ${Z + 5} air`)
          // navwand: derecho atraviesa la pared del sur; izquierdo salta a donde mira.
          await srv.consola(`fill ${X - 1} -60 ${Z + 2} ${X + 1} -58 ${Z + 2} stone`)
          await srv.sincronizar()
          await pide(ana, 'wc tool navwand', /Your tool is now navwand/)
          m = ana.marca()
          ana.bot.activateItem()
          await ana.esperar('chat', /Whoosh!/, { desde: m })
          await ana.hasta('estar del otro lado de la pared', b => Math.floor(b.entity.position.z) === Z + 3)
          m = ana.marca()
          ana.bot.swingArm()
          await ana.esperar('chat', /Whoosh!/, { desde: m })
          await ana.hasta('saltar a donde mira', b => Math.floor(b.entity.position.z) > Z + 6)
          await pide(bots[BETO], 'wc tool info', /You don't have permission/)
        } finally {
          await srv.consola(`fill ${X - 2} -60 ${Z} ${X + 4} -56 ${Z + 10} air`)
          await srv.consola(`fill ${X - 2} -61 ${Z} ${X + 4} -61 ${Z + 10} grass_block`)
          await srv.consola(`clear ${ANA}`)
        }
      }
    },
    {
      id: '032 H6 CA1 · H6 CA2 · H6 CA3',
      nombre: 'superpico: Beto sin permiso; single rompe sin soltar nada y undo lo devuelve; area los del mismo tipo; la roca madre no se rompe; off',
      timeoutMs: 120000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await preparar(ctx)
        const ana = bots[ANA]
        try {
          await borrarEntidades(srv)
          await pide(bots[BETO], 'wc sp', /You don't have permission/)
          await srv.consola(`item replace entity ${ANA} weapon.mainhand with minecraft:diamond_pickaxe`)
          await ana.hasta('tener el pico', b => b.heldItem?.name === 'diamond_pickaxe')
          await srv.consola(`setblock ${X} -60 ${Z + 2} stone`)
          await srv.sincronizar()
          await pide(ana, 'wc sp', /Super pickaxe on \(single\)/)
          let m = ana.marca()
          picar(ana, X, -60, Z + 2)
          await ana.esperar('chat', /Done: superpickaxe changed 1 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X} -60 ${Z + 2} air`)
          await dormir(500)
          if (await cuantas(srv, 'item', [X - 3, X + 3], [-62, -56], [Z - 1, Z + 5]) !== 0) throw new Error('el superpico soltó un ítem')
          await pide(ana, 'wc undo', /Undone .*superpickaxe/)
          await comprobar(srv, `block ${X} -60 ${Z + 2} stone`)
          // area 1: las tres piedras de la fila; la tierra de encima queda.
          await srv.consola(`fill ${X - 1} -60 ${Z + 2} ${X + 1} -60 ${Z + 2} stone`)
          await srv.consola(`setblock ${X} -59 ${Z + 2} dirt`)
          await srv.sincronizar()
          await pide(ana, 'wc sp area 1', /Super pickaxe on \(area 1\)/)
          m = ana.marca()
          picar(ana, X, -60, Z + 2)
          await ana.esperar('chat', /Done: superpickaxe changed 3 blocks/, { desde: m, timeoutMs: 20000 })
          await comprobar(srv, `block ${X + 1} -60 ${Z + 2} air`)
          await comprobar(srv, `block ${X} -59 ${Z + 2} dirt`)
          // La roca madre nunca (tools.never_break).
          await srv.consola(`setblock ${X} -60 ${Z + 2} bedrock`)
          await srv.sincronizar()
          m = ana.marca()
          picar(ana, X, -60, Z + 2)
          await ana.esperar('actionbar', /never breaks bedrock/, { desde: m })
          await comprobar(srv, `block ${X} -60 ${Z + 2} bedrock`)
          await pide(ana, 'wc sp off', /Super pickaxe off/)
        } finally {
          await srv.consola(`fill ${X - 2} -60 ${Z} ${X + 2} -58 ${Z + 4} air`)
          await srv.consola(`clear ${ANA}`)
          await borrarEntidades(srv)
        }
      }
    }
  ]
}
