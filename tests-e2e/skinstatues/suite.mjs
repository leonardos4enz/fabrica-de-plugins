// Escenarios en el juego de SkinStatues (spec 021): el estado se prepara por consola; el bot (OP) solo usa /statue y
// se comprueba por lo que recibe: mensajes, barra de acción y los bloques que ve en el mundo.
import fs from 'node:fs'
import path from 'node:path'

const ANA = 'Ana_e2e'
const BETO = 'Beto_e2e'
// Mundo plano: el pasto está en y = -61, así que el jugador pisa y = -60 y los pies de la estatua quedan en -58.
const X = 40
const Z = 40
const PIES = -58

const v3 = (bot, x, y, z) => new bot.entity.position.constructor(x, y, z)
const bloque = (bot, x, y, z) => bot.blockAt(v3(bot, x, y, z))?.name ?? '?'

/** Cuenta los bloques de la caja [x0..x1]×[y0..y1]×[z0..z1] que cumplen el filtro (por defecto, los que no son aire). */
function contar (bot, [x0, x1], [y0, y1], [z0, z1], filtro = n => n !== 'air') {
  let n = 0
  for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) if (filtro(bloque(bot, x, y, z))) n++
  return n
}

function nombres (bot, [x0, x1], [y0, y1], [z0, z1]) {
  const s = new Set()
  for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) s.add(bloque(bot, x, y, z))
  s.delete('air')
  return s
}

/** Caja amplia alrededor del punto de trabajo (una estatua de hasta 40 de alto entra). */
const CAJA = [[X - 16, X + 16], [PIES - 1, PIES + 42], [Z - 16, Z + 16]]

/** Deja el área vacía y a Ana en el punto de trabajo, en creativo, mirando al sur (yaw 0) o hacia donde se pida. */
async function prepararAna ({ srv, bots }, yaw = 0) {
  await limpiarArea(srv) // si un escenario anterior falló, su estatua no estorba los conteos de este
  for (const c of [`gamemode creative ${ANA}`, `tp ${ANA} ${X + 0.5} -60 ${Z + 0.5} ${yaw} 0`]) await srv.consola(c)
  await srv.sincronizar()
  await bots[ANA].hasta('estar en el punto de trabajo', b =>
    Math.abs(b.entity.position.x - (X + 0.5)) < 0.6 && Math.abs(b.entity.position.z - (Z + 0.5)) < 0.6 && b.entity.onGround)
}

/** El área de trabajo sin nada: aire de y = -60 hacia arriba (en tandas, por el tope de /fill). */
async function limpiarArea (srv) {
  for (let y = -60; y < -10; y += 10) await srv.consola(`fill ${X - 20} ${y} ${Z - 20} ${X + 20} ${y + 9} ${Z + 20} air`)
  await srv.sincronizar()
}

/** Construye y espera hasta que el bot ve todos los bloques: devuelve { id, bloques }. */
async function construir (ana, comando, { timeoutMs = 30000 } = {}) {
  const m = ana.marca()
  ana.comando(comando)
  const plan = await ana.esperar('chat', /Statue #(\d+) of \w+: (\d+) blocks, (\d+)×(\d+)×(\d+)/, { desde: m, timeoutMs })
  const [, id, total, ancho, alto, fondo] = plan.texto.match(/Statue #(\d+) of \w+: (\d+) blocks, (\d+)×(\d+)×(\d+)/).map(Number)
  const fin = await ana.esperar('chat', new RegExp(`Statue #${id} of \\w+ is finished \\((\\d+) blocks\\)`), { desde: m, timeoutMs })
  const puestos = Number(fin.texto.match(/\((\d+) blocks\)/)[1])
  return { id, total, puestos, ancho, alto, fondo, texto: plan.texto }
}

export default {
  jar: /^SkinStatues-.*\.jar$/,
  // La configuración y la skin de ejemplo tal como están en el código fuente: el plugin solo crea los archivos que
  // faltan, así que en un servidor de prueba ya usado podrían estar viejos. Cada corrida empieza sin estatuas.
  copiar: [
    { de: 'plugins/skinstatues/src/main/resources', a: 'plugins/SkinStatues' }
  ],
  datosDeJugador: [],
  bots: [ANA, BETO],
  puerto: 25599,
  versionPorDefecto: '1.21.4',
  ruidoDelServidor: [/No key layers/],
  // Lo que el plugin registra cuando algo falla de verdad (los «0 error(s)» de reload no cuentan).
  erroresDelPlugin: [/\[SkinStatues\] (Could not|Statue #\d+ stopped by an error)/],
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
    await limpiarArea(ctx.srv)
  },

  escenarios: [
    {
      id: 'H9 CA5 · H9 CA2 · F-02',
      nombre: 'validate sin errores ni notas y selftest desde la consola',
      async fn ({ srv }) {
        await srv.consola('statue validate', { espera: /Configuration OK: 6 poses, 5 palettes, 145 block colors/ })
        await srv.consola('statue selftest', { espera: /Selftest passed: \d+ blocks placed as planned \(\d+ replaced terrain\) and the terrain restored exactly/, timeoutMs: 15000 })
      }
    },
    {
      id: 'H9 CA1 · H9 CA4',
      nombre: 'sin permiso: no construye y el autocompletado no muestra subcomandos',
      async fn ({ bots }) {
        const beto = bots[BETO]
        const m = beto.marca()
        beto.comando('statue build Example 16')
        await beto.esperar('chat', /You do not have permission/, { desde: m })
        // Paper no contesta cuando no hay ninguna sugerencia: el tiempo agotado es la lista vacía.
        const deBeto = await beto.bot.tabComplete('/statue ', false, false, 2000).catch(e => /did not fire/.test(e.message) ? [] : Promise.reject(e))
        if (deBeto.length) throw new Error(`Beto ve subcomandos: ${JSON.stringify(deBeto)}`)
        const deAna = (await bots[ANA].bot.tabComplete('/statue ')).map(x => x.match ?? x)
        for (const s of ['build', 'undo', 'remove', 'selftest']) if (!deAna.includes(s)) throw new Error(`Ana no ve '${s}': ${deAna}`)
        const poses = (await bots[ANA].bot.tabComplete('/statue build Example 16 w')).map(x => x.match ?? x)
        if (!poses.includes('waving') || !poses.includes('walking')) throw new Error(`poses en el autocompletado: ${poses}`)
      }
    },
    {
      id: 'H1 CA1 · H1 CA2 · H1 CA4 · H1 CA7 · H5 CA1 · H2 CA1',
      nombre: 'build: 2 bloques arriba, mirando al sur, la capa atrás y el número de bloques anunciado',
      timeoutMs: 60000,
      async fn (ctx) {
        const { bots, srv } = ctx
        await prepararAna(ctx, 0)
        const ana = bots[ANA]
        const r = await construir(ana, 'statue build Example 16')
        if (!/skin from skins\/Example\.png/.test(r.texto)) throw new Error(`la skin no salió del archivo: ${r.texto}`)
        if (r.alto !== 16) throw new Error(`alto ${r.alto}, se pidió 16`)
        await ana.hasta(`ver los ${r.puestos} bloques de la estatua`, b => contar(b, ...CAJA) === r.puestos, { timeoutMs: 10000 })
        if (r.puestos !== r.total) throw new Error(`se planearon ${r.total} y se pusieron ${r.puestos} (todo era aire)`)
        if (contar(ana.bot, CAJA[0], [PIES - 1, PIES - 1], CAJA[2])) throw new Error('hay bloques por debajo de los pies')
        if (!contar(ana.bot, CAJA[0], [PIES, PIES], CAJA[2])) throw new Error('los pies no están 2 bloques arriba del jugador')
        if (contar(ana.bot, CAJA[0], [PIES + 16, PIES + 30], CAJA[2])) throw new Error('la estatua pasa de 16 de alto')
        // Mirando al sur (+z) la capa queda del lado norte: la fila de más atrás (z mínima) es verde.
        let zMin = Infinity
        for (let z = Z - 16; z <= Z + 16 && zMin === Infinity; z++) if (contar(ana.bot, CAJA[0], CAJA[1], [z, z])) zMin = z
        const atras = nombres(ana.bot, CAJA[0], [PIES + 4, PIES + 11], [zMin, zMin])
        if (![...atras].some(n => /green|moss/.test(n))) throw new Error(`la capa no está atrás (z=${zMin}): ${[...atras]}`)
        // Hueca (H1 CA4): una estatua maciza de 16×16 tendría muchos más bloques.
        if (r.puestos > 1600) throw new Error(`${r.puestos} bloques: no parece hueca`)
        ctx.primera = r
        await srv.consola('statue list', { espera: new RegExp(`#${r.id} Example \\| 16 blocks, standing`) })
      }
    },
    {
      id: 'H8 CA2 · H8 CA5',
      nombre: 'undo quita la última estatua y deja el aire como estaba',
      timeoutMs: 30000,
      async fn (ctx) {
        const ana = ctx.bots[ANA]
        const m = ana.marca()
        ana.comando('statue undo')
        await ana.esperar('chat', new RegExp(`Statue #${ctx.primera.id} removed: ${ctx.primera.puestos} blocks restored`), { desde: m, timeoutMs: 20000 })
        await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
        await ctx.srv.consola('statue list', { espera: /No statues yet/ })
      }
    },
    {
      id: 'H8 CA1 · H8 CA3 · H8 CA2',
      nombre: 'reemplaza terreno y lo devuelve; no toca cofres ni lo que otro cambió después',
      timeoutMs: 60000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararAna(ctx, 0)
        const ana = bots[ANA]
        // Una «pared» de ladrillos con agua y escaleras donde van las piernas, y un cofre con algo dentro.
        await srv.consola(`fill ${X - 3} ${PIES} ${Z - 2} ${X + 3} ${PIES + 1} ${Z + 2} minecraft:bricks`)
        await srv.consola(`setblock ${X + 1} ${PIES + 2} ${Z} minecraft:oak_stairs[facing=east,half=top]`)
        await srv.consola(`setblock ${X - 2} ${PIES} ${Z} minecraft:chest`)
        await srv.consola(`item replace block ${X - 2} ${PIES} ${Z} container.0 with minecraft:diamond 3`)
        await srv.sincronizar()
        await ana.hasta('ver el cofre', b => bloque(b, X - 2, PIES, Z) === 'chest')
        const m = ana.marca()
        const r = await construir(ana, 'statue build Example 16')
        await ana.esperar('chat', /1 block\(s\) with contents .* were left untouched/, { desde: m })
        if (bloque(ana.bot, X - 2, PIES, Z) !== 'chest') throw new Error('el cofre se reemplazó')
        // Alguien cambia un bloque de la estatua después (H8 CA3): arriba de la cabeza (la cáscara, no el hueco).
        if (bloque(ana.bot, X, PIES + 15, Z) === 'air') throw new Error('arriba de la cabeza no hay estatua')
        await srv.consola(`setblock ${X} ${PIES + 15} ${Z} minecraft:gold_block`)
        await srv.sincronizar()
        const m2 = ana.marca()
        ana.comando(`statue remove ${r.id}`)
        const quitado = await ana.esperar('chat', new RegExp(`Statue #${r.id} removed: (\\d+) blocks restored\\. (\\d+) were left`), { desde: m2, timeoutMs: 20000 })
        const cambiados = Number(quitado.texto.match(/(\d+) were left/)[1])
        if (cambiados !== 1) throw new Error(`se dejaron ${cambiados} bloques; solo uno lo cambió otro`)
        await ana.hasta('ver la pared de ladrillos de nuevo', b =>
          contar(b, [X - 3, X + 3], [PIES, PIES + 1], [Z - 2, Z + 2], n => n === 'bricks') === 7 * 2 * 5 - 1)
        if (bloque(ana.bot, X - 2, PIES, Z) !== 'chest') throw new Error('el cofre no está')
        await srv.consola(`data get block ${X - 2} ${PIES} ${Z} Items`, { espera: /diamond/ })
        const escalera = ana.bot.blockAt(v3(ana.bot, X + 1, PIES + 2, Z))
        if (escalera?.name !== 'oak_stairs' || escalera.getProperties().half !== 'top') throw new Error(`la escalera volvió mal: ${escalera?.name} ${JSON.stringify(escalera?.getProperties())}`)
        if (bloque(ana.bot, X, PIES + 15, Z) !== 'gold_block') throw new Error('se borró el bloque que puso otro')
        const resto = contar(ana.bot, ...CAJA, n => n !== 'air' && n !== 'bricks' && n !== 'chest' && n !== 'oak_stairs' && n !== 'gold_block')
        if (resto) throw new Error(`quedaron ${resto} bloques de la estatua`)
        await limpiarArea(srv)
      }
    },
    {
      id: 'H4 CA1 · H6 CA2 · H6 CA3 · H3 CA1 · H4 CA6',
      nombre: 'pose, paleta solo de lana y segunda capa en relieve',
      timeoutMs: 60000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararAna(ctx, 0)
        const ana = bots[ANA]
        const r = await construir(ana, 'statue build Example 33 victory --palette wool')
        await ana.hasta(`ver los ${r.puestos} bloques`, b => contar(b, ...CAJA) === r.puestos, { timeoutMs: 10000 })
        const usados = nombres(ana.bot, ...CAJA)
        if (![...usados].every(n => n.endsWith('_wool'))) throw new Error(`no todo es lana: ${[...usados]}`)
        if (!usados.has('orange_wool') || !usados.has('yellow_wool')) throw new Error(`falta el chaleco o el casco: ${[...usados]}`)
        // Brazos arriba: hay bloques al costado de la cabeza (de pie ahí no hay nada).
        if (!contar(ana.bot, [X - 16, X - 6], [PIES + 27, PIES + 40], CAJA[2])) throw new Error('los brazos no están arriba')
        // Segunda capa (H3 CA1): el casco cubre la cabeza y sobresale un bloque a cada costado (la cabeza va de X-4 a
        // X+3; mirar hacia arriba no cambia el ancho).
        let tope = PIES + 40
        while (tope > PIES && !contar(ana.bot, [X - 4, X + 3], [tope, tope], CAJA[2])) tope--
        const arriba = nombres(ana.bot, [X - 4, X + 3], [tope, tope], CAJA[2])
        if (!arriba.has('yellow_wool')) throw new Error(`arriba de la cabeza no está el casco: ${[...arriba]}`)
        for (const x of [X - 5, X + 4]) {
          const lado = nombres(ana.bot, [x, x], [PIES + 25, PIES + 34], CAJA[2])
          if (!lado.has('yellow_wool')) throw new Error(`el casco no sobresale en x=${x}: ${[...lado]}`)
        }
        await srv.consola(`statue remove ${r.id}`, { espera: new RegExp(`Statue #${r.id} removed`), timeoutMs: 20000 })
        await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
      }
    },
    {
      id: 'H1 CA5 · H1 CA6 · H2 CA3 · H4 CA5 · H6 CA3',
      nombre: 'nombres, alturas, poses, paletas y opciones inválidas, y skin que no está',
      async fn ({ bots }) {
        const ana = bots[ANA]
        const casos = [
          ['statue build Example 16 wavng', /Unknown pose 'wavng' \(did you mean 'waving'\?\)/],
          ['statue build Example 16 --palette wol', /Unknown palette 'wol' \(did you mean 'wool'\?\)/],
          ['statue build Example 16 --no-cap', /Unknown option '--no-cap' \(did you mean '--no-cape'\?\)/],
          ['statue build Example 500', /Height '500' must be a whole number from 8 to 128/],
          ['statue build Example alto', /Height 'alto' must be a whole number/],
          ['statue build <red>x;op 16', /'<red>x;op' is not a valid account name/],
          ['statue build Nadie_e2e 16', /No skin for 'Nadie_e2e'\. Put Nadie_e2e\.png in plugins\/SkinStatues\/skins\/, or turn on network\.mojang/],
          ['statue remove 999', /There is no statue '999'/]
        ]
        for (const [cmd, esperado] of casos) {
          const m = ana.marca()
          ana.comando(cmd)
          await ana.esperar('chat', esperado, { desde: m })
        }
      }
    },
    {
      id: 'H1 CA6',
      nombre: 'una estatua que no entra hasta el techo del mundo dice cuánto entra',
      async fn ({ srv, bots }) {
        const ana = bots[ANA]
        await srv.consola(`setblock ${X} 249 ${Z} minecraft:glass`)
        await srv.consola(`tp ${ANA} ${X + 0.5} 250 ${Z + 0.5} 0 0`)
        await srv.consola(`gamemode creative ${ANA}`)
        await srv.sincronizar()
        await ana.hasta('estar arriba', b => b.entity.position.y > 240)
        const m = ana.marca()
        ana.comando('statue build Example 100')
        const msg = await ana.esperar('chat', /does not fit between here and the top of the world\. The most that fits here is (\d+)/, { desde: m, timeoutMs: 15000 })
        const cabe = Number(msg.texto.match(/is (\d+)/)[1])
        if (cabe < 60 || cabe > 70) throw new Error(`a y=252 hasta 320 caben unos 68 bloques; dijo ${cabe}`)
        await srv.consola(`setblock ${X} 249 ${Z} air`)
      }
    },
    {
      id: 'H7 CA1 · H7 CA3 · H9 CA2',
      nombre: 'construcción por tandas con avance, cancel deja lo puesto deshacible, y reload',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        const config = path.join(srv.run, 'plugins', 'SkinStatues', 'config.yml')
        const original = fs.readFileSync(config, 'utf8')
        try {
          fs.writeFileSync(config, original.replace('blocks_per_tick: 4000', 'blocks_per_tick: 20'))
          await srv.consola('statue reload', { espera: /Reloaded: 6 poses, 5 palettes\. 0 error/ })
          await prepararAna(ctx, 0)
          const ana = bots[ANA]
          const m = ana.marca()
          ana.comando('statue build Example 24 walking')
          const plan = await ana.esperar('chat', /Statue #(\d+) of Example: (\d+) blocks/, { desde: m, timeoutMs: 15000 })
          const id = Number(plan.texto.match(/#(\d+)/)[1])
          await ana.esperar('actionbar', new RegExp(`Statue #${id} \\d+% \\(\\d+/\\d+\\)`), { desde: m, timeoutMs: 10000 })
          ana.comando('statue cancel')
          const parada = await ana.esperar('chat', new RegExp(`Statue #${id} stopped after (\\d+) blocks`), { desde: m })
          const puestos = Number(parada.texto.match(/after (\d+) blocks/)[1])
          await ana.hasta(`ver ${puestos} bloques`, b => contar(b, ...CAJA) === puestos)
          await srv.consola(`statue info ${id}`, { espera: /cancelled/ })
          // Un poses.yml roto no borra las poses que ya funcionaban (H9 CA2).
          const poses = path.join(srv.run, 'plugins', 'SkinStatues', 'poses.yml')
          const posesOriginal = fs.readFileSync(poses, 'utf8')
          fs.writeFileSync(poses, 'waving: [roto\n')
          try {
            await srv.consola('statue reload', { espera: /poses\.yml: invalid YAML .*keeping the previous version/ })
            await srv.consola('statue poses', { espera: /Poses: standing, waving, walking, victory, pointing, sitting/ })
          } finally {
            fs.writeFileSync(poses, posesOriginal)
          }
          const m2 = ana.marca()
          ana.comando(`statue remove ${id}`)
          await ana.esperar('chat', new RegExp(`Statue #${id} removed: ${puestos} blocks restored`), { desde: m2, timeoutMs: 30000 })
          await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0, { timeoutMs: 15000 })
        } finally {
          fs.writeFileSync(config, original)
          await srv.consola('statue reload', { espera: /Reloaded/ })
        }
      }
    },
    {
      id: 'H2 CA1 · H1 CA2',
      nombre: 'skin local de brazos delgados y estatua mirando al este',
      timeoutMs: 60000,
      async fn (ctx) {
        const { srv, bots } = ctx
        const skins = path.join(srv.run, 'plugins', 'SkinStatues', 'skins')
        fs.copyFileSync(path.join(skins, 'Example.png'), path.join(skins, 'Flaca_e2e.png'))
        fs.writeFileSync(path.join(skins, 'Flaca_e2e.yml'), 'model: slim\n')
        try {
          await prepararAna(ctx, -90) // mirando al este (+x)
          const ana = bots[ANA]
          const r = await construir(ana, 'statue build flaca_e2e 32 --no-overlay --no-cape')
          // Mirando al este el ancho de hombro a hombro va en z: 14 bloques con brazos de 3 (16 con los de 4).
          if (r.fondo !== 14 || r.ancho !== 8) throw new Error(`medidas ${r.ancho}×${r.alto}×${r.fondo}: se esperaba 8×32×14`)
          await ana.hasta(`ver los ${r.puestos} bloques`, b => contar(b, ...CAJA) === r.puestos, { timeoutMs: 10000 })
          await srv.consola(`statue remove ${r.id}`, { espera: new RegExp(`Statue #${r.id} removed`), timeoutMs: 20000 })
          await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
        } finally {
          fs.rmSync(path.join(skins, 'Flaca_e2e.png'), { force: true })
          fs.rmSync(path.join(skins, 'Flaca_e2e.yml'), { force: true })
        }
      }
    }
  ]
}
