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
  pluginsExtra: /worldedit/i,
  puerto: 25599,
  versionPorDefecto: '1.21.4',
  ruidoDelServidor: [/No key layers/],
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
        await pide(ana, 'wc replace 50% dirt with stone', /weights \(%\) work in patterns, not in masks/)
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
          await pide(beto, 'wc schem save e2e_cmd --overwrite', /Replacing a schematic needs the permission/)
          await pide(beto, 'wc schem load ../../server', /is not a valid name/)
          await pide(beto, 'wc schem load C:/Windows/win', /is not a valid name/)
          const nombres = (await beto.bot.tabComplete('/wc schem load e2e_')).map(x => x.match ?? x)
          for (const n of ['e2e_cmd', 'e2e_ajeno']) if (!nombres.includes(n)) throw new Error(`no sugiere ${n}: ${nombres}`)
          const ops = (await beto.bot.tabComplete('/wc schem ')).map(x => x.match ?? x)
          if (ops.includes('delete')) throw new Error(`Beto ve delete: ${ops}`)
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
        fs.rmSync(path.join(propia, 'e2e_roto.schem'), { force: true })
        fs.rmSync(path.join(carpetaWE(srv), 'e2e_ajeno.schem'), { force: true })
      }
    }
  ]
}
