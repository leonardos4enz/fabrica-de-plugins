// Escenarios en el juego de SkinStatues (spec 021): el estado se prepara por consola; el bot (OP) solo usa /statue y
// se comprueba por lo que recibe: mensajes, barra de acción y los bloques que ve en el mundo.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { httpsLocal, png } from '../lib/https-local.mjs'

const POSES_0_1_0 = fileURLToPath(new URL('../../plugins/skinstatues/src/test/resources/poses-0.1.0.yml', import.meta.url))
const EXAMPLE_PNG = fileURLToPath(new URL('../../plugins/skinstatues/src/main/resources/skins/Example.png', import.meta.url))

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
  // \S+: el nombre de la cuenta o, con link (spec 023), url:sitio#huella.
  const plan = await ana.esperar('chat', /Statue #(\d+) of \S+: (\d+) blocks, (\d+)×(\d+)×(\d+)/, { desde: m, timeoutMs })
  const [, id, total, ancho, alto, fondo] = plan.texto.match(/Statue #(\d+) of \S+: (\d+) blocks, (\d+)×(\d+)×(\d+)/).map(Number)
  const fin = await ana.esperar('chat', new RegExp(`Statue #${id} of \\S+ is finished \\((\\d+) blocks\\)`), { desde: m, timeoutMs })
  const puestos = Number(fin.texto.match(/\((\d+) blocks\)/)[1])
  return { id, total, puestos, ancho, alto, fondo, texto: plan.texto }
}

// ---------------------------------------------------------------- spec 023: skins por link

/** Skin delgada sintética: la primera capa opaca menos las columnas que solo usan los brazos anchos. */
function skinDelgada () {
  const dentro = (x, y, [x0, y0, x1, y1]) => x >= x0 && x < x1 && y >= y0 && y < y1
  const base = [[0, 0, 32, 16], [0, 16, 64, 32], [16, 48, 48, 64]]
  const libres = [[54, 20, 56, 32], [50, 16, 52, 20], [46, 52, 48, 64], [42, 48, 44, 52]]
  return png(64, 64, (x, y) => base.some(r => dentro(x, y, r)) && !libres.some(r => dentro(x, y, r))
    ? (y < 16 ? [205, 160, 120, 255] : y < 32 ? [40, 90, 180, 255] : [60, 60, 70, 255])
    : [0, 0, 0, 0])
}

/** El servidor HTTPS local de la corrida: se arranca antes que Paper, que confía en su certificado. */
let web = null
const imagen = datos => (req, res) => {
  res.writeHead(200, { 'Content-Type': 'image/png' })
  res.end(datos())
}
const RUTAS = {
  '/e2e/Skin01.png': imagen(() => fs.readFileSync(EXAMPLE_PNG)),
  '/e2e/slim.png': imagen(skinDelgada),
  '/e2e/big.png': imagen(() => png(128, 128, () => [200, 0, 0, 255])),
  '/e2e/huge.png': imagen(() => Buffer.concat([png(64, 64, () => [0, 0, 0, 255]), Buffer.alloc(1200 * 1024, 7)])),
  '/e2e/redir': (req, res) => {
    res.writeHead(302, { Location: '/e2e/slim.png' })
    res.end()
  },
  '/e2e/page.png': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/html' })
    res.end('<html><body>not a skin</body></html>')
  },
  // Manda la cabecera y dos bytes y no termina nunca: el plugin tiene que cortar a los 8 s.
  '/e2e/lento.png': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'image/png' })
    res.write(Buffer.from([0x89, 0x50]))
  }
}
const pedidosDe = ruta => web?.pedidos[ruta] ?? 0

/** network.urls y allowed_hosts en el config.yml del servidor de prueba, y reload. */
async function links (srv, { urls, hosts = [] }) {
  const archivo = path.join(srv.run, 'plugins', 'SkinStatues', 'config.yml')
  const texto = fs.readFileSync(archivo, 'utf8')
    .replace(/^(\s*)urls: .*$/m, (m, sangria) => `${sangria}urls: ${urls}`)
    .replace(/^(\s*)allowed_hosts: .*$/m, (m, sangria) => `${sangria}allowed_hosts: ${JSON.stringify(hosts)}`)
  fs.writeFileSync(archivo, texto)
  await srv.consola('statue reload', { espera: /Reloaded: \d+ poses/ })
}

/** Manda el comando y espera el mensaje de rechazo. */
async function rechaza (ana, comando, patron, timeoutMs = 15000) {
  const m = ana.marca()
  ana.comando(comando)
  await ana.esperar('chat', patron, { desde: m, timeoutMs })
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

  async antesDeArrancar ({ run, java }) {
    web = await httpsLocal({ carpeta: path.join(run, 'e2e-https'), java, rutas: RUTAS })
    return { jvm: web.jvm }
  },
  async alTerminar () {
    await web?.cerrar()
    web = null
  },

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
        // 28 poses incorporadas (spec 022) + los 2 ejemplos de poses.yml.
        await srv.consola('statue validate', { espera: /Configuration OK: 30 poses, 5 palettes, 145 block colors/ })
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
          await srv.consola('statue reload', { espera: /Reloaded: 30 poses, 5 palettes\. 0 error/ })
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
            await srv.consola('statue poses', { espera: /Yours \(poses\.yml\): my_wave, my_point_left/ })
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
    },

    // ------------------------------------------------------------------ spec 022
    {
      id: '022 H3 CA1 · H3 CA2 · H3 CA7 · H1 CA1',
      nombre: '--at relativo y --facing: los pies en ese bloque, sin offset_up, mirando al norte (pose nueva t_pose)',
      timeoutMs: 60000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararAna(ctx, 0)
        const ana = bots[ANA]
        const facings = (await ana.bot.tabComplete('/statue build Example 16 --facing ')).map(x => x.match ?? x)
        if (['north', 'south', 'east', 'west'].some(f => !facings.includes(f))) throw new Error(`--facing sugiere ${facings}`)
        const at = (await ana.bot.tabComplete('/statue build Example 16 --at ')).map(x => x.match ?? x)
        if (!at.includes('~')) throw new Error(`--at sugiere ${at}`)
        const flags = (await ana.bot.tabComplete('/statue build Example 16 --')).map(x => x.match ?? x)
        for (const f of ['--at', '--facing', '--world']) if (!flags.includes(f)) throw new Error(`falta ${f}: ${flags}`)

        // Ana está en (X, -60, Z) mirando al sur; la estatua va 6 bloques al sur, con los pies en y = -60.
        const r = await construir(ana, 'statue build Example 16 t_pose --at ~ ~ ~6 --facing north')
        const caja = [[X - 16, X + 16], [-60, -18], [Z - 4, Z + 16]]
        await ana.hasta(`ver los ${r.puestos} bloques`, b => contar(b, ...caja) === r.puestos, { timeoutMs: 10000 })
        if (!contar(ana.bot, caja[0], [-60, -60], caja[2])) throw new Error('los pies no están en y = -60 (sin offset_up)')
        if (contar(ana.bot, caja[0], [-58 + 16, -18], caja[2])) throw new Error('pasa de 16 de alto')
        if (contar(ana.bot, caja[0], caja[1], [Z - 4, Z + 2])) throw new Error('hay bloques fuera del lugar pedido')
        // t_pose de frente al norte: los brazos van a lo ancho (x), de unos 16 bloques.
        if (r.ancho < 16 || r.fondo > 5) throw new Error(`medidas ${r.ancho}×${r.alto}×${r.fondo}: t_pose mirando al norte`)
        // Mirando al norte la capa queda al sur: la fila de z máxima es verde.
        let zMax = -Infinity
        for (let z = Z + 16; z >= Z && zMax === -Infinity; z--) if (contar(ana.bot, caja[0], caja[1], [z, z])) zMax = z
        const atras = nombres(ana.bot, caja[0], [-56, -50], [zMax, zMax])
        if (![...atras].some(n => /green|moss/.test(n))) throw new Error(`la capa no está al sur (z=${zMax}): ${[...atras]}`)
        await srv.consola(`statue info ${r.id}`, { espera: new RegExp(`Where: world ${X} -60 ${Z + 6}, facing north`) })
        await srv.consola(`statue remove ${r.id}`, { espera: new RegExp(`Statue #${r.id} removed`), timeoutMs: 20000 })
        await ana.hasta('no ver la estatua', b => contar(b, ...caja) === 0)
      }
    },
    {
      id: '022 F-01',
      nombre: 'construir donde ya están esos mismos bloques no dice que había cofres',
      timeoutMs: 60000,
      async fn (ctx) {
        // Regresión (spec 022, F-01): un bloque que ya era el de la estatua se contaba como «con contenido».
        await prepararAna(ctx, 0)
        const ana = ctx.bots[ANA]
        const r1 = await construir(ana, 'statue build Example 16')
        await ana.hasta(`ver los ${r1.puestos} bloques`, b => contar(b, ...CAJA) === r1.puestos, { timeoutMs: 10000 })
        const m = ana.marca()
        const r2 = await construir(ana, 'statue build Example 16')
        if (r2.puestos !== 0) throw new Error(`encima de una igual puso ${r2.puestos} bloques`)
        await ana.nunca('chat', /with contents/, { desde: m, ms: 1500 })
        for (const id of [r2.id, r1.id]) {
          await ctx.srv.consola(`statue remove ${id}`, { espera: new RegExp(`Statue #${id} removed`), timeoutMs: 20000 })
        }
        await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
      }
    },
    {
      id: '022 H3 CA4 · H3 CA5 · H3 CA6',
      nombre: 'desde la consola, lejos de todo jugador (chunks sin generar), con --world, y undo de la consola',
      timeoutMs: 120000,
      async fn ({ srv }) {
        // Errores primero.
        await srv.consola('statue build Example 16', { espera: /From the console, say where: .*--at/ })
        await srv.consola('statue build Example 16 --at ~ 70 0', { espera: /the console is nowhere: use plain numbers with --at/ })
        await srv.consola('statue build Example 16 --at 0 70 0 --world wrld', { espera: /There is no world 'wrld' \(did you mean 'world'\?\)/ })
        await srv.consola('statue build Example 16 --at 0 70 zero', { espera: /'zero' is not a coordinate/ })
        await srv.consola('statue build Example 16 --at 0 70 0 --facing up', { espera: /'up' is not a direction/ })
        await srv.consola('worldborder set 2000')
        try {
          await srv.consola('statue build Example 16 --at 3000 70 3000 --world world', { espera: /3000 70 3000 is outside the world border of world/ })
        } finally {
          await srv.consola('worldborder set 59999968')
        }
        // Lejos (x = z = 4000: en un mundo nuevo nadie generó esos chunks), mirando al este. Si una corrida anterior
        // falló a mitad, su estatua sigue ahí: se limpia primero.
        await srv.consola('forceload add 4000 4000', { espera: /[Mm]arked chunk|force loaded/ })
        await srv.consola('fill 3990 -60 3990 4010 -40 4010 air')
        await srv.consola('forceload remove 4000 4000')
        const m = srv.marca()
        const plan = await srv.consola('statue build Example 16 waving --at 4000 -60 4000 --world world --facing east',
          { espera: /Statue #(\d+) of Example: (\d+) blocks/, timeoutMs: 20000 })
        const [, id, total] = plan.match(/Statue #(\d+) of Example: (\d+) blocks/)
        await srv.esperarLog(new RegExp(`Statue #${id} of Example is finished \\(${total} blocks\\)`), { desde: m, timeoutMs: 60000 })
        const mi = srv.marca()
        await srv.consola(`statue info ${id}`, { espera: /Where: world 4000 -60 4000, facing east/ })
        await srv.esperarLog(/Built by Console/, { desde: mi })
        // Al terminar se sueltan los chunks (H3 CA6) y Paper los descarga: para mirar el bloque hay que cargarlo. Lo más alto de la cabeza
        // (alto 16: la capa 15 sobre los pies; por dentro es hueca) está ahí.
        await srv.consola('forceload add 4000 4000', { espera: /[Mm]arked chunk|force loaded/ })
        await srv.consola('execute unless block 4000 -45 4000 minecraft:air', { espera: /Test passed/ })
        await srv.consola('statue undo', { espera: new RegExp(`Statue #${id} removed: ${total} blocks restored`), timeoutMs: 60000 })
        await srv.consola('execute if block 4000 -45 4000 minecraft:air', { espera: /Test passed/ })
        await srv.consola('forceload remove 4000 4000')
      }
    },
    {
      id: '022 H1 CA2 · H1 CA3 · H1 CA4 · H2 CA1 · H2 CA2 · H2 CA3 · H2 CA5',
      nombre: 'poses incorporadas y propias: extends, mirror, reemplazo, errores y el poses.yml de la 0.1.0',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        const carpeta = path.join(srv.run, 'plugins', 'SkinStatues')
        const poses = path.join(carpeta, 'poses.yml')
        const original = fs.readFileSync(poses, 'utf8')
        try {
          // La copia de referencia existe, dice que se reescribe y vuelve si alguien la edita.
          const ref = path.join(carpeta, 'builtin-poses.yml')
          fs.writeFileSync(ref, 'roto: {}\n')
          await srv.consola('statue reload', { espera: /Reloaded: 30 poses/ })
          if (!/REWRITTEN every time/.test(fs.readFileSync(ref, 'utf8')) || !/^t_pose:/m.test(fs.readFileSync(ref, 'utf8'))) {
            throw new Error('builtin-poses.yml no se reescribió')
          }
          await srv.consola('statue poses', { espera: /Built in: standing, waving, walking, victory, pointing, sitting, running/ })

          // El poses.yml de la 0.1.0 (las 6 de entonces): sin errores y sin «reemplazos».
          fs.writeFileSync(poses, fs.readFileSync(POSES_0_1_0, 'utf8'))
          await srv.consola('statue reload', { espera: /Reloaded: 28 poses, 5 palettes\. 0 error\(s\), 0 note/ })
          await srv.consola('statue poses', { espera: /Yours \(poses\.yml\): none/ })

          // Propias: reemplazo, extends ángulo por ángulo, mirror y errores que no tumban a las demás.
          fs.writeFileSync(poses, [
            't_pose:', '  right_arm: { roll: 45 }', '  left_arm: { roll: 45 }',
            'apunta_izq:', '  extends: pointing', '  mirror: true',
            'mas_codo:', '  extends: waving', '  right_arm: { elbow: 120 }',
            'huerfana:', '  extends: wavng',
            'circulo:', '  extends: circulo', ''
          ].join('\n'))
          await srv.consola('statue reload', { espera: /Reloaded: 30 poses, 5 palettes\. 2 error/ })
          await srv.consola('statue validate', { espera: /huerfana\.extends: there is no pose 'wavng' \(did you mean 'waving'\?\)/ })
          const mp = srv.marca()
          await srv.consola('statue poses', { espera: /Built in: .*t_pose\*/ })
          await srv.esperarLog(/Yours \(poses\.yml\): apunta_izq, mas_codo/, { desde: mp })

          // El espejo en el mundo: pointing apunta con la mano derecha (al oeste, mirando al sur), apunta_izq con la izquierda.
          await prepararAna(ctx, 0)
          const ana = bots[ANA]
          const adelante = [Z + 3, Z + 9]
          const r1 = await construir(ana, 'statue build Example 16 pointing --no-cape')
          await ana.hasta(`ver los ${r1.puestos} bloques`, b => contar(b, ...CAJA) === r1.puestos, { timeoutMs: 10000 })
          const der = [contar(ana.bot, [X - 16, X - 1], CAJA[1], adelante), contar(ana.bot, [X, X + 16], CAJA[1], adelante)]
          await srv.consola(`statue remove ${r1.id}`, { espera: new RegExp(`Statue #${r1.id} removed`), timeoutMs: 20000 })
          await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
          const r2 = await construir(ana, 'statue build Example 16 apunta_izq --no-cape')
          await ana.hasta(`ver los ${r2.puestos} bloques`, b => contar(b, ...CAJA) === r2.puestos, { timeoutMs: 10000 })
          const izq = [contar(ana.bot, [X - 16, X - 1], CAJA[1], adelante), contar(ana.bot, [X, X + 16], CAJA[1], adelante)]
          await srv.consola(`statue remove ${r2.id}`, { espera: new RegExp(`Statue #${r2.id} removed`), timeoutMs: 20000 })
          await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
          if (!(der[0] > 4 && der[1] === 0)) throw new Error(`pointing: brazo adelante al oeste/este = ${der}`)
          if (!(izq[1] > 4 && izq[0] === 0)) throw new Error(`apunta_izq: brazo adelante al oeste/este = ${izq}`)
          if (r1.puestos !== r2.puestos) throw new Error(`el espejo tiene otro número de bloques: ${r1.puestos} y ${r2.puestos}`)
        } finally {
          fs.writeFileSync(poses, original)
          await srv.consola('statue reload', { espera: /Reloaded: 30 poses/ })
        }
      }
    },
    {
      id: '023 H1 CA4 · H1 CA5 · H2 CA4',
      nombre: 'links: apagados por defecto, permiso aparte y lista de sitios, sin pedir nada a la red',
      timeoutMs: 60000,
      async fn (ctx) {
        const { srv, bots } = ctx
        const link = `${web.base}/e2e/Skin01.png`
        const permisos = path.join(srv.run, 'permissions.yml')
        const antes = fs.existsSync(permisos) ? fs.readFileSync(permisos, 'utf8') : null
        try {
          await links(srv, { urls: false })
          await rechaza(bots[ANA], `statue build ${link} 16`, /Skin links are off\. Turn on network\.urls/)

          await links(srv, { urls: true })
          await srv.esperarLog(/network\.urls is on: \/statue build accepts skin links from any public site/, { desde: 0 })
          const sugerencias = (await bots[ANA].bot.tabComplete('/statue build h')).map(x => x.match ?? x)
          if (!sugerencias.includes('https://')) throw new Error(`el autocompletado no sugiere https://: ${sugerencias}`)

          // Beto con skinstatues.build (dado a todos por permissions.yml, como hijo de uno propio: el del plugin ya está
          // definido y no se puede redefinir) pero sin skinstatues.build.url.
          fs.writeFileSync(permisos, ['e2e.constructor:', '  default: true', '  children:', '    skinstatues.build: true', ''].join('\n'))
          await srv.consola('bukkit:reload permissions', { espera: /Permissions successfully reloaded/i })
          await ctx.entrar(BETO)
          await rechaza(ctx.bots[BETO], `statue build ${link} 16`, /You do not have permission/)

          await links(srv, { urls: true, hosts: ['textures.minecraft.net', '*.imgur.com'] })
          await rechaza(bots[ANA], `statue build ${link} 16`,
            /Links from 127\.0\.0\.1 are not allowed\. Allowed sites: textures\.minecraft\.net, \*\.imgur\.com/)
          if (pedidosDe('/e2e/Skin01.png')) throw new Error(`se pidió la skin ${pedidosDe('/e2e/Skin01.png')} veces`)
        } finally {
          if (antes == null) fs.rmSync(permisos, { force: true })
          else fs.writeFileSync(permisos, antes)
          await srv.consola('bukkit:reload permissions')
          await ctx.entrar(BETO)
          await links(srv, { urls: true })
        }
      }
    },
    {
      id: '023 H1 CA1 · H1 CA6 · H3 CA1 · H3 CA2',
      nombre: 'build con un link: la estatua, url:sitio#huella en list e info sin el link, y la caché',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await links(srv, { urls: true })
        await prepararAna(ctx, 0)
        const ana = bots[ANA]
        const link = `${web.base}/e2e/Skin01.png`
        const r = await construir(ana, `statue build ${link} 16`)
        if (!/Statue #\d+ of url:127\.0\.0\.1#[0-9a-f]{8}:/.test(r.texto)) throw new Error(`la estatua no se llama url:sitio#huella: ${r.texto}`)
        if (!/skin from link \(127\.0\.0\.1\)/.test(r.texto)) throw new Error(`no dice de dónde salió: ${r.texto}`)
        await ana.hasta(`ver los ${r.puestos} bloques`, b => contar(b, ...CAJA) === r.puestos, { timeoutMs: 10000 })
        // La misma skin que el archivo de ejemplo: el mismo número de bloques.
        const huella = r.texto.match(/url:127\.0\.0\.1#([0-9a-f]{8})/)[1]
        await srv.consola('statue list', { espera: new RegExp(`#${r.id} url:127\\.0\\.0\\.1#${huella} \\| 16 blocks, standing`) })
        await srv.consola(`statue info ${r.id}`, { espera: new RegExp(`Skin: url:127\\.0\\.0\\.1#${huella}, 16 blocks`) })
        const indice = fs.readFileSync(path.join(srv.run, 'plugins', 'SkinStatues', 'data', 'statues.yml'), 'utf8')
        if (indice.includes('Skin01') || indice.includes('/e2e/')) throw new Error('data/statues.yml guarda el link')
        if (!indice.includes(`url:127.0.0.1#${huella}`)) throw new Error('data/statues.yml no guarda la etiqueta')
        // Paper anota cada comando de un jugador («issued server command»); el plugin no escribe el link en ningún otro lado.
        const conLink = srv.lineas.filter(l => l.includes('Skin01') && !/issued server command/.test(l))
        if (conLink.length) throw new Error(`el log tiene el link: ${conLink[0]}`)
        const cache = path.join(srv.run, 'plugins', 'SkinStatues', 'cache', 'links')
        if (fs.readdirSync(cache).some(n => /Skin01|127/.test(n))) throw new Error('la caché se llama como el link')
        await srv.consola(`statue remove ${r.id}`, { espera: new RegExp(`Statue #${r.id} removed`), timeoutMs: 20000 })
        await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)

        // La segunda vez, de la caché: el servidor no recibe otro pedido.
        const pedidos = pedidosDe('/e2e/Skin01.png')
        const r2 = await construir(ana, `statue build ${link} 16`)
        if (!/skin from cache/.test(r2.texto)) throw new Error(`no salió de la caché: ${r2.texto}`)
        if (pedidosDe('/e2e/Skin01.png') !== pedidos) throw new Error('se volvió a pedir la skin')
        if (r2.puestos !== r.puestos) throw new Error(`la caché dio otra estatua: ${r2.puestos} y ${r.puestos}`)
        await srv.consola(`statue remove ${r2.id}`, { espera: new RegExp(`Statue #${r2.id} removed`), timeoutMs: 20000 })
        await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
      }
    },
    {
      id: '023 H1 CA2 · H1 CA3',
      nombre: 'http pasa a https y sigue una redirección; el modelo delgado se detecta y --classic lo fuerza',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await links(srv, { urls: true })
        await prepararAna(ctx, 0)
        const ana = bots[ANA]
        // http:// → https:// y /e2e/redir → /e2e/slim.png (el servidor local solo habla https).
        const delgada = await construir(ana, `statue build ${web.base.replace('https', 'http')}/e2e/redir 32`)
        await ana.hasta(`ver los ${delgada.puestos} bloques`, b => contar(b, ...CAJA) === delgada.puestos, { timeoutMs: 10000 })
        await srv.consola(`statue remove ${delgada.id}`, { espera: new RegExp(`Statue #${delgada.id} removed`), timeoutMs: 20000 })
        await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
        if (!pedidosDe('/e2e/redir') || !pedidosDe('/e2e/slim.png')) throw new Error('no siguió la redirección')

        const clasica = await construir(ana, `statue build ${web.base}/e2e/slim.png 32 --classic`)
        await ana.hasta(`ver los ${clasica.puestos} bloques`, b => contar(b, ...CAJA) === clasica.puestos, { timeoutMs: 10000 })
        await srv.consola(`statue remove ${clasica.id}`, { espera: new RegExp(`Statue #${clasica.id} removed`), timeoutMs: 20000 })
        await ana.hasta('no ver la estatua', b => contar(b, ...CAJA) === 0)
        // De pie, el ancho es el de los hombros: 16 píxeles con brazos de 4 y 14 con brazos de 3 (a 32 de alto, 1 bloque por píxel).
        if (!(delgada.ancho === 14 && clasica.ancho === 16)) throw new Error(`anchos: detectada ${delgada.ancho}, --classic ${clasica.ancho}`)
        await rechaza(ana, `statue build ${web.base}/e2e/slim.png 32 --slim --classic`, /Use --slim or --classic, not both/)
      }
    },
    {
      id: '023 H2 CA1 · H2 CA2 · H2 CA5',
      nombre: 'solo una skin válida: otro tamaño, una página, más de 1 MB, 404, usuario en el link y un sitio que no termina',
      timeoutMs: 90000,
      async fn (ctx) {
        const { srv, bots } = ctx
        await links(srv, { urls: true })
        await prepararAna(ctx, 0)
        const ana = bots[ANA]
        const b = web.base
        const cache = path.join(srv.run, 'plugins', 'SkinStatues', 'cache', 'links')
        const enCache = () => fs.existsSync(cache) ? fs.readdirSync(cache).sort().join(',') : ''
        const antes = enCache()
        await rechaza(ana, `statue build ${b}/e2e/big.png 16`, /cannot be used: it is 128x128, expected 64x64 or 64x32/)
        await rechaza(ana, `statue build ${b}/e2e/page.png 16`, /cannot be used: not a PNG image/)
        await rechaza(ana, `statue build ${b}/e2e/huge.png 16`, /Could not get the skin from 127\.0\.0\.1: the file is larger than 1 MB/)
        await rechaza(ana, `statue build ${b}/e2e/nada.png 16`, /Could not get the skin from 127\.0\.0\.1: HTTP 404/)
        await rechaza(ana, `statue build https://ana:secreto@127.0.0.1:${web.puerto}/e2e/Skin01.png 16`,
          /That link cannot be used: links with a user or password are not allowed/)
        await rechaza(ana, `statue build ${b}/e2e/lento.png 16`, /Could not get the skin from 127\.0\.0\.1: no answer from 127\.0\.0\.1/, 20000)
        if (contar(ana.bot, ...CAJA)) throw new Error('se construyó algo')
        // Lo que no es una skin no queda en la caché.
        if (enCache() !== antes) throw new Error(`la caché cambió: ${antes} → ${enCache()}`)
        await srv.consola('statue list', { espera: /No statues yet|statue\(s\)/ })
      }
    }
  ]
}
