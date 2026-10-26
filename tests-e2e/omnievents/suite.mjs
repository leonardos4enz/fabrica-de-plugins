// Escenarios en el juego de OmniEvents (spec 002, H4): uno por criterio de la spec 001 que la compuerta 2 no
// podía probar. El estado se prepara por consola; el bot solo hace lo que se prueba.
import fs from 'node:fs'
import path from 'node:path'
import { dormir } from '../lib/servidor.mjs'

const ANA = 'Ana_e2e'
const BETO = 'Beto_e2e'
// Spec 013: jugador de Bedrock (bedrock-protocol por Geyser); Floodgate le pone el prefijo «.» en el servidor.
const BEB = 'Beb_e2e'
const BEB_SERVIDOR = '.' + BEB
const PUNTO = '12.5 -60 12.5' // punto de trabajo en el mundo plano (suelo en y = -61)

const cerca = (pos, x, y, z, d = 1.2) => Math.abs(pos.x - x) < d && Math.abs(pos.y - y) < d && Math.abs(pos.z - z) < d
const bloque = (bot, x, y, z) => bot.blockAt(new bot.entity.position.constructor(x, y, z))?.name
const esperarVar = (srv, jugador, nombre, valor) =>
  srv.consola(`omni var get ${jugador} ${nombre}`, { espera: new RegExp(`${jugador}\\.${nombre} = ${valor}(\\.0)?\\s*$`) })

/** pvar.e2e_clics del jugador de Bedrock (el «.» del nombre no deja usar esperarVar). */
const clicsDeBeb = (srv, n) =>
  srv.consola(`omni var get ${BEB_SERVIDOR} e2e_clics`, { espera: new RegExp(`Beb_e2e\\.e2e_clics = ${n}(\\.0)?\\s*$`) })

/** Deja a un jugador en el punto de trabajo, en el modo pedido, sin efectos, con vida y hambre completas. */
async function prepararJugador ({ srv, bots }, nombre, modo = 'survival') {
  for (const c of [`gamemode ${modo} ${nombre}`, `clear ${nombre}`, `effect clear ${nombre}`, `xp set ${nombre} 0 levels`,
    `xp set ${nombre} 0 points`, `effect give ${nombre} instant_health 1 10 true`, `effect give ${nombre} saturation 1 20 true`,
    `tp ${nombre} ${PUNTO} 0 0`]) await srv.consola(c)
  await srv.sincronizar()
  const j = bots[nombre]
  await j.hasta('estar en el punto de trabajo, sano y sin efectos', b =>
    cerca(b.entity.position, 12.5, -60, 12.5) && b.health >= 20 && b.food >= 20 && b.game.gameMode === modo)
  await srv.consola(`effect clear ${nombre}`)
  await srv.sincronizar()
  await j.hasta('sin efectos', b => Object.keys(b.entity.effects).length === 0)
}

/** Limpia el área de trabajo: sin entidades sueltas y bloques de prueba en aire. */
async function limpiarArea (srv) {
  await srv.consola('kill @e[type=!player]')
  await srv.consola('fill 6 -60 6 18 -55 18 air')
  await srv.sincronizar()
}

export default {
  jar: /^OmniEvents-.*\.jar$/,
  // Spec 012: Vault, EssentialsX, WorldEdit, WorldGuard y Citizens en el servidor de prueba (lib/plugins-extra.mjs).
  pluginsExtra: true,
  // Comandos de Minecraft que usa la suite y que EssentialsX reemplazaría (con otra salida): se le desactivan.
  comandosDeMinecraft: ['clear', 'clearinventory', 'gamemode', 'give', 'item', 'kill', 'list', 'time', 'tp', 'weather', 'xp', 'exp'],
  // Los ejemplos se toman siempre del código fuente: OmniEvents solo los crea si rules/ no existe, así que en un
  // servidor de prueba ya usado (o recién creado por la suite) podrían faltar o estar viejos.
  copiar: [
    { de: 'plugins/omnievents/src/main/resources/rules/ejemplos', a: 'plugins/OmniEvents/rules/ejemplos' },
    { de: 'tests-e2e/omnievents/reglas', a: 'plugins/OmniEvents/rules/e2e', retirar: true }
  ],
  datosDeJugador: ['plugins/OmniEvents/data/players/{uuid}.yml'],
  bots: [ANA, BETO],
  puerto: 25599,
  // Spec 013: Geyser escucha a los jugadores de Bedrock aquí (UDP, solo 127.0.0.1).
  puertoBedrock: 19199,
  versionPorDefecto: '1.21.4',
  // Ruido conocido de Paper que no es del plugin.
  ruidoDelServidor: [/No key layers/],
  // Errores del plugin que no son excepciones (una acción que falla en tiempo de ejecución se registra como aviso).
  erroresDelPlugin: [/\[OmniEvents\] Error/],
  preparar: [
    'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
    'gamerule naturalRegeneration false', 'gamerule doImmediateRespawn true', 'gamerule sendCommandFeedback true',
    'gamerule spawnRadius 0',
    // Regresión (spec 008, F-02): en 26.x las reglas de juego se llaman en snake_case y las de arriba fallan con
    // «Incorrect argument» (el jugador se regeneraba solo). Cada versión rechaza los nombres de la otra, sin efecto.
    'gamerule advance_time false', 'gamerule advance_weather false', 'gamerule spawn_mobs false',
    'gamerule natural_health_regeneration false', 'gamerule immediate_respawn true', 'gamerule send_command_feedback true',
    'gamerule respawn_radius 0',
    'setworldspawn 12 -60 12', 'time set noon', 'weather clear', 'difficulty easy'
  ],

  /** Comandos de consola al terminar, antes de apagar: el servidor de prueba queda como estaba (H1 CA4). */
  limpiar: ['deop Ana_e2e', 'deop Beto_e2e', 'kill @e[type=!player]', 'fill 6 -60 6 18 -55 18 air'],

  /** Entra Beto (observador) y después Ana, que vive su primera vez en el servidor. */
  async antes (ctx) {
    await ctx.entrar(BETO)
    ctx.marcaBetoAntesDeAna = ctx.bots[BETO].marca()
    ctx.marcaLogAntesDeAna = ctx.srv.marca()
    await ctx.entrar(ANA)
    ctx.anaEntro = Date.now()
    for (const n of [ANA, BETO]) await ctx.srv.consola(`op ${n}`, { espera: new RegExp(`Made ${n} a server operator|Nothing changed`) })
  },

  escenarios: [
    {
      id: 'H6 CA1 · H5 CA1 · H5 CA5 · H8 CA2',
      nombre: 'player_join: bienvenida de primera vez, título, mensaje de entrada y recompensa diaria',
      timeoutMs: 20000,
      async fn ({ srv, bots, marcaBetoAntesDeAna }) {
        const ana = bots[ANA]
        const beto = bots[BETO]
        await ana.esperar('chat', /Démosle la bienvenida a Ana_e2e, es su primera vez/)
        await beto.esperar('chat', /\[\+\] Ana_e2e/, { desde: marcaBetoAntesDeAna }) // set_join_message
        await ana.hasta('tener el pan de bienvenida', () => ana.cantidad('bread') === 8)
        const pan = ana.items('bread')[0]
        if (!/Pan de bienvenida/.test(ana.nombreDe(pan) ?? '')) throw new Error(`el pan no tiene el nombre: ${ana.nombreDe(pan)}`)
        await ana.esperar('title', /Bienvenido/)
        await ana.esperar('subtitle', /jugadores en línea/)
        await ana.esperar('chat', /Recompensa diaria: \+25 monedas \(total 125\)/, { timeoutMs: 10000 })
        await esperarVar(srv, ANA, 'monedas', 125)
      }
    },
    {
      id: 'H5 CA1',
      nombre: 'mensajes: message, broadcast, actionbar, title, bossbar, sound, console_message y log',
      async fn (ctx) {
        const { srv, bots } = ctx
        await prepararJugador(ctx, ANA)
        const ana = bots[ANA]
        const beto = bots[BETO]
        const ma = ana.marca()
        const mb = beto.marca()
        const ml = srv.marca()
        ana.comando('e2e mensajes')
        await ana.esperar('chat', /^e2e mensaje para Ana_e2e$/, { desde: ma })
        await beto.esperar('chat', /^e2e difusión de Ana_e2e$/, { desde: mb })
        await ana.esperar('chat', /^e2e difusión de Ana_e2e$/, { desde: ma })
        await ana.esperar('actionbar', /^e2e barra de Ana_e2e$/, { desde: ma })
        await ana.esperar('title', /^e2e título$/, { desde: ma })
        await ana.esperar('subtitle', /^e2e subtítulo$/, { desde: ma })
        await ana.esperar('bossbar', /e2e jefe/, { desde: ma })
        await ana.esperar('sound', /@1\.37$/, { desde: ma }) // tono propio de e2e_mensajes
        await srv.esperarLog(/e2e consola de Ana_e2e/, { desde: ml })
        await srv.esperarLog(/\[OmniEvents\] \[e2e_mensajes\] e2e registro de Ana_e2e/, { desde: ml })
        await beto.nunca('actionbar', /e2e barra/, { desde: mb, ms: 300 })
      }
    },
    {
      id: 'H5 CA2',
      nombre: 'comandos: console, command (como el jugador) y chat (como el jugador)',
      async fn ({ bots }) {
        const ana = bots[ANA]
        const beto = bots[BETO]
        const ma = ana.marca()
        const mb = beto.marca()
        ana.comando('e2e comandos')
        await beto.esperar('chat', /e2e-console Ana_e2e/, { desde: mb })
        await ana.esperar('chat', /^e2e eco desde el comando de Ana_e2e$/, { desde: ma })
        await beto.esperar('chat', /Ana_e2e.*e2e chat de Ana_e2e/, { desde: mb })
      }
    },
    {
      id: 'H5 CA3',
      nombre: 'jugador: damage, heal, set_food y feed',
      async fn (ctx) {
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        ana.comando('e2e salud')
        await ana.hasta('vida 14 tras damage 6', b => b.health === 14)
        ana.comando('e2e curar')
        await ana.hasta('vida 18 tras heal 4', b => b.health === 18)
        ana.comando('e2e hambre')
        await ana.hasta('hambre 5 tras set_food 5', b => b.food === 5)
        ana.comando('e2e comer')
        await ana.hasta('hambre 9 tras feed 4', b => b.food === 9)
      }
    },
    {
      id: 'H5 CA3',
      nombre: 'jugador: teleport, gamemode y fly',
      async fn (ctx) {
        await prepararJugador(ctx, ANA)
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        await srv.consola(`tp ${ANA} 8.5 -60 8.5`)
        await ana.hasta('estar en 8.5, -60, 8.5', b => cerca(b.entity.position, 8.5, -60, 8.5))
        ana.comando('e2e tp')
        await ana.hasta('volver a 12.5, -60, 12.5 por teleport', b => cerca(b.entity.position, 12.5, -60, 12.5))
        ana.comando('e2e creativo')
        await ana.hasta('modo creativo', b => b.game.gameMode === 'creative')
        ana.comando('e2e supervivencia')
        await ana.hasta('modo supervivencia', b => b.game.gameMode === 'survival')
        const m = ana.marca()
        ana.comando('e2e volar')
        await ana.esperar('abilities', /flags=\d+/, { desde: m })
        await ana.hasta('poder volar (allowFlight)', () => (ana.vistos.filter(v => v.tipo === 'abilities').at(-1).json.flags & 4) === 4)
        ana.comando('e2e no_volar')
        await ana.hasta('no poder volar', () => (ana.vistos.filter(v => v.tipo === 'abilities').at(-1).json.flags & 4) === 0)
      }
    },
    {
      id: 'H5 CA3',
      nombre: 'jugador: effect y remove_effect',
      async fn (ctx) {
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        const speed = ana.bot.registry.effectsByName.Speed?.id ?? ana.bot.registry.effectsByName.speed?.id
        ana.comando('e2e efecto')
        await ana.hasta('tener Velocidad II', b => b.entity.effects[speed]?.amplifier === 1)
        ana.comando('e2e sin_efecto')
        await ana.hasta('no tener Velocidad', b => !b.entity.effects[speed])
      }
    },
    {
      id: 'H5 CA3',
      nombre: 'jugador: give_item (texto y mapa), remove_item, clear_inventory, give_level y give_exp',
      async fn (ctx) {
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        ana.comando('e2e items')
        await ana.hasta('3 diamantes y 2 manzanas', () => ana.cantidad('diamond') === 3 && ana.cantidad('golden_apple') === 2)
        const diamante = ana.nombreDe(ana.items('diamond')[0])
        const manzana = ana.nombreDe(ana.items('golden_apple')[0])
        if (diamante !== 'Diamante e2e') throw new Error(`nombre del diamante: ${diamante}`)
        if (manzana !== 'Manzana de Ana_e2e') throw new Error(`nombre de la manzana: ${manzana}`)
        ana.comando('e2e quitar')
        await ana.hasta('1 diamante tras remove_item DIAMOND;2', () => ana.cantidad('diamond') === 1)
        ana.comando('e2e vaciar')
        await ana.hasta('inventario vacío', () => ana.items().length === 0)
        ana.comando('e2e experiencia')
        await ana.hasta('nivel 3 o más', b => b.experience.level >= 3)
      }
    },
    {
      id: 'H5 CA4',
      nombre: 'mundo: set_block, summon, drop_item, lightning y particle',
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        const m = ana.marca()
        ana.comando('e2e mundo')
        await ana.hasta('bloque de oro en 14, -60, 12', b => bloque(b, 14, -60, 12) === 'gold_block')
        await ana.esperar('entity', /^pig$/, { desde: m })
        await ana.esperar('entity', /^item$/, { desde: m })
        await ana.esperar('entity', /^lightning_bolt$/, { desde: m })
        await ana.esperar('particle', /flame/i, { desde: m })
        await srv.consola('execute if entity @e[type=pig,x=10.5,y=-60,z=14.5,distance=..2]', { espera: /Test passed/ })
        await srv.consola('execute if entity @e[type=item,nbt={Item:{id:"minecraft:emerald"}}]', { espera: /Test passed/ })
      }
    },
    {
      id: 'H5 CA4',
      nombre: 'mundo: explosion no rompe bloques por defecto',
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA, 'creative')
        await srv.consola('setblock 13 -60 12 stone')
        const ana = ctx.bots[ANA]
        await ana.hasta('ver la piedra', b => bloque(b, 13, -60, 12) === 'stone')
        const m = ana.marca()
        ana.comando('e2e explosion')
        await ana.esperar('explosion', /explosion/, { desde: m })
        await dormir(500)
        for (const [x, y, z, b] of [[13, -60, 12, 'stone'], [12, -61, 12, 'grass_block']]) {
          await srv.consola(`execute if block ${x} ${y} ${z} ${b}`, { espera: /Test passed/ })
        }
      }
    },
    {
      id: 'H5 CA5 · H6 CA1',
      nombre: 'block_break + cancel: el ejemplo bloque_protegido impide romper roca madre',
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA, 'creative')
        await srv.consola('setblock 13 -60 12 bedrock')
        const ana = ctx.bots[ANA]
        await ana.hasta('ver la roca madre', b => bloque(b, 13, -60, 12) === 'bedrock')
        const m = ana.marca()
        await ana.bot.dig(ana.bot.blockAt(new ana.bot.entity.position.constructor(13, -60, 12)), true).catch(() => {})
        await ana.esperar('chat', /No puedes romper ese bloque aquí/, { desde: m })
        await dormir(500)
        await srv.consola('execute if block 13 -60 12 bedrock', { espera: /Test passed/ })
      }
    },
    {
      id: 'H6 CA1 · H7 CA1',
      nombre: 'block_break: el ejemplo diamantes_contados suma pvar.diamantes y lo muestra',
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA)
        await srv.consola(`give ${ANA} diamond_pickaxe`)
        await srv.consola('setblock 13 -60 12 diamond_ore')
        const ana = ctx.bots[ANA]
        await ana.hasta('tener el pico y ver el diamante', b => ana.cantidad('diamond_pickaxe') === 1 && bloque(b, 13, -60, 12) === 'diamond_ore')
        await ana.bot.equip(ana.items('diamond_pickaxe')[0], 'hand')
        const m = ana.marca()
        await ana.bot.dig(ana.bot.blockAt(new ana.bot.entity.position.constructor(13, -60, 12)), true)
        await ana.esperar('actionbar', /Diamantes: 1$/, { desde: m })
        await esperarVar(srv, ANA, 'diamantes', 1)
        await srv.consola('execute if block 13 -60 12 air', { espera: /Test passed/ })
      }
    },
    {
      id: 'H6 CA1 · H5 CA5',
      nombre: 'player_attack + set_damage: un golpe con set_damage 1000 mata a una vaca',
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA)
        await srv.consola('summon cow 14.5 -60 12.5 {NoAI:1b,Tags:["e2e"]}')
        const ana = ctx.bots[ANA]
        await ana.hasta('ver la vaca', b => Object.values(b.entities).some(e => e.name === 'cow'))
        const vaca = Object.values(ana.bot.entities).find(e => e.name === 'cow')
        await ana.bot.lookAt(vaca.position.offset(0, 1, 0), true)
        const m = ana.marca()
        ana.bot.attack(vaca)
        await ana.esperar('actionbar', /e2e golpe a COW/, { desde: m })
        await ana.hasta('la vaca murió de un golpe', b => !b.entities[vaca.id] || b.entities[vaca.id].isValid === false, { timeoutMs: 5000 })
        await srv.consola('execute unless entity @e[type=cow,tag=e2e]', { espera: /Test passed/ })
      }
    },
    {
      id: 'H5 CA5',
      nombre: 'player_chat + set_message: el mensaje llega cambiado a los demás',
      async fn ({ bots }) {
        const ana = bots[ANA]
        const beto = bots[BETO]
        const mb = beto.marca()
        ana.chat('!megafono hola a todos')
        await beto.esperar('chat', /\[megáfono\] hola a todos/, { desde: mb })
        await beto.nunca('chat', /!megafono/, { desde: mb, ms: 500 })
      }
    },
    {
      id: 'H4 CA10',
      nombre: 'escape de MiniMessage: el texto del jugador repetido con {message} queda literal',
      async fn ({ bots }) {
        const ana = bots[ANA]
        const m = ana.marca()
        const malicioso = "<red>rojo</red> <click:run_command:'/op Ana_e2e'>clic</click> &cno"
        ana.chat('!eco ' + malicioso)
        const v = await ana.esperar('chat', /^eco: /, { desde: m })
        if (!v.texto.includes(malicioso)) throw new Error(`el eco no es literal: ${JSON.stringify(v.texto)}`)
        if (JSON.stringify(v.json).includes('clickEvent') || JSON.stringify(v.json).includes('click_event')) {
          throw new Error('el eco tiene un evento de clic: se interpretó MiniMessage')
        }
        const a = await ana.esperar('actionbar', /^eco: /, { desde: m })
        if (!a.texto.includes(malicioso)) throw new Error(`la barra no es literal: ${JSON.stringify(a.texto)}`)
      }
    },
    {
      id: 'H6 CA1 · H4 CA10',
      nombre: 'player_chat: el ejemplo menciones avisa al mencionado con su texto literal y un sonido',
      async fn ({ bots }) {
        const ana = bots[ANA]
        const beto = bots[BETO]
        const mb = beto.marca()
        const ma = ana.marca()
        ana.chat('hola beto_e2e, <bold>mira</bold>')
        await beto.esperar('actionbar', /^Ana_e2e te mencionó: hola beto_e2e, <bold>mira<\/bold>$/, { desde: mb })
        await beto.esperar('sound', /@2$/, { desde: mb }) // el ejemplo usa tono 2
        await ana.nunca('actionbar', /te mencionó/, { desde: ma, ms: 500 })
      }
    },
    {
      id: 'H4 CA9 · H7 CA1',
      nombre: 'filtro de chat asíncrono: cancela, avisa, cuenta pvar.avisos y a la tercera expulsa por consola',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv, bots } = ctx
        const ana = bots[ANA]
        const beto = bots[BETO]
        const mb = beto.marca()
        for (let i = 1; i <= 2; i++) {
          const ma = ana.marca()
          ana.chat(`eres un tonto ${i}`)
          await ana.esperar('chat', /^Cuida tu lenguaje, Ana_e2e\.$/, { desde: ma })
          await esperarVar(srv, ANA, 'avisos', i)
          await dormir(600)
        }
        await beto.nunca('chat', /tonto/, { desde: mb, ms: 300 })
        ana.chat('eres un idiota')
        await ana.hasta('ser expulsada a la tercera', () => /Lenguaje inapropiado/.test(ana.expulsion ?? ''), { timeoutMs: 8000 })
        await dormir(1000) // los datos de quien sale se guardan un tick después, en otro hilo
        // Regresión (002 F-01): el reinicio de avisos después del kick se perdía («ya no está conectado»).
        await esperarVar(srv, ANA, 'avisos', 0)
        await beto.nunca('chat', /idiota/, { desde: mb, ms: 300 })
      }
    },
    {
      id: 'H6 CA1 · H8 CA2',
      nombre: 'player_join de nuevo: saludo con las monedas guardadas y sin segunda recompensa diaria',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv } = ctx
        if (ctx.bots[ANA]?.expulsion == null) await ctx.salir(ANA)
        await dormir(1000)
        const ana = await ctx.entrar(ANA)
        await ana.esperar('chat', /^¡Hola de nuevo, Ana_e2e! Tienes 125 monedas\.$/)
        await ana.nunca('chat', /Recompensa diaria/, { desde: 0, ms: 7000 })
        await esperarVar(srv, ANA, 'monedas', 125)
      }
    },
    {
      id: 'H6 CA1 · H7 CA1 · H7 CA4',
      nombre: 'comandos de reglas: /monedas lee pvar puesta por /omni var set y /monedas dar la transfiere con call',
      async fn ({ srv, bots }) {
        const ana = bots[ANA]
        const beto = bots[BETO]
        await srv.consola(`omni var set ${ANA} monedas 40`, { espera: /Ana_e2e\.monedas = 40/ })
        let ma = ana.marca()
        ana.comando('monedas')
        await ana.esperar('chat', /^Tienes 40 monedas\.$/, { desde: ma })
        ma = ana.marca()
        ana.comando('monedas dar Beto_e2e 999')
        await ana.esperar('chat', /^Cantidad inválida: tienes 40 monedas\.$/, { desde: ma })
        ma = ana.marca()
        const mb = beto.marca()
        ana.comando('monedas dar Beto_e2e 15')
        await ana.esperar('chat', /^Diste 15 monedas a Beto_e2e\.$/, { desde: ma })
        await beto.esperar('chat', /^Recibiste 15 monedas de Ana_e2e\.$/, { desde: mb })
        await esperarVar(srv, ANA, 'monedas', 25)
        await esperarVar(srv, BETO, 'monedas', 140)
      }
    },
    {
      id: '005 H1 CA1 · H1 CA2 · H1 CA3 · H2 CA1 · H2 CA2',
      nombre: '/omni enable|disable seguidos: sin reenviar comandos si no cambian, un árbol por cambio y sin errores',
      timeoutMs: 30000,
      async fn ({ srv, bots }) {
        const ana = bots[ANA]
        const arboles = (m) => ana.vistos.slice(m).filter(v => v.tipo === 'comandos')
        const ml = srv.marca()
        // Regresión (004 F-01): cada cambio re-registraba los comandos y mandaba el árbol 4 veces; dos seguidos
        // dejaban ConcurrentModificationException en Commands.sendAsync.
        let m = ana.marca()
        for (const c of ['disable', 'enable', 'disable', 'enable']) await srv.consola(`omni ${c} e2e_mensajes`)
        await srv.sincronizar()
        await dormir(1500)
        if (arboles(m).length) throw new Error(`una regla que no es comando mandó ${arboles(m).length} árboles de comandos`)

        m = ana.marca()
        for (const c of ['disable', 'enable', 'disable']) await srv.consola(`omni ${c} e2e`)
        await srv.sincronizar()
        await ana.hasta('recibir un árbol por cada cambio de /e2e', () => arboles(m).length >= 3)
        await dormir(1000)
        const a = arboles(m)
        if (a.length !== 3) throw new Error(`se esperaban 3 árboles (uno por cambio) y llegaron ${a.length}`)
        // Paper arma cada árbol en otro hilo: los tres salen con el estado final (desactivada).
        if (a.at(-1).texto.includes(' e2e ')) throw new Error('/e2e sigue en el árbol con la regla desactivada')
        let ma = ana.marca()
        ana.comando('e2e mensajes')
        await ana.esperar('chat', /Unknown or incomplete command/i, { desde: ma })
        await ana.nunca('chat', /e2e mensaje para/, { desde: ma, ms: 500 })

        await srv.consola('omni enable e2e', { espera: /Regla e2e activada/ })
        await ana.hasta('ver /e2e de nuevo en el árbol', () => arboles(m).length === 4 && arboles(m)[3].texto.includes(' e2e '))
        ma = ana.marca()
        ana.comando('e2e mensajes')
        await ana.esperar('chat', /^e2e mensaje para Ana_e2e$/, { desde: ma })
        const cme = srv.errores({ desde: ml }).filter(l => /ConcurrentModification/.test(l))
        if (cme.length) throw new Error('el log tiene ConcurrentModificationException:\n    ' + cme.join('\n    '))
      }
    },
    {
      id: '006 H1 CA1 · H1 CA2 · H1 CA3 · H2 CA1 · H2 CA2 · H4 CA4',
      nombre: 'deny por condición y «2 de 3»: /omni run real corre el deny de la que falla, si no el else',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        await srv.consola(`omni var del ${ANA} e2e_bloqueo`)
        await srv.consola(`omni var set ${ANA} e2e_compras 0`, { espera: /e2e_compras = 0/ })
        const correr = async (espera) => {
          const m = ana.marca()
          await srv.consola(`omni run e2e_tienda ${ANA}`, { espera: /(ejecutada|no corrió sus acciones) para/ })
          await ana.esperar('chat', espera, { desde: m })
          await ana.nunca('chat', /^e2e (else|deny|compra)/, { desde: ana.marca(), ms: 300 })
          return m
        }
        // Nivel 0: falla la condición con deny propio → su deny y no el else.
        let m = await correr(/^e2e deny nivel: tienes 0$/)
        await ana.nunca('chat', /^e2e else$/, { desde: m, ms: 200 })
        // Nivel 5: pasa la de nivel; del grupo solo se cumple 1 de 3 (hambre llena) → deny del grupo.
        await srv.consola(`xp set ${ANA} 5 levels`)
        await correr(/^e2e deny any$/)
        // Con una esmeralda se cumplen 2 de 3 → corre actions de verdad (H4 CA4) y queda en la consola.
        await srv.consola(`give ${ANA} emerald 1`)
        await srv.sincronizar()
        await ana.hasta('tener la esmeralda', () => ana.cantidad('emerald') === 1)
        const ml = srv.marca()
        await correr(/^e2e compra 1$/)
        await srv.esperarLog(/CONSOLE ejecutó la regla e2e_tienda para Ana_e2e \(\/omni run\)/, { desde: ml })
        await esperarVar(srv, ANA, 'e2e_compras', 1)
        // Falla una condición sin deny (forma corta de texto) → el else de la regla, como antes.
        await srv.consola(`omni var set ${ANA} e2e_bloqueo 1`, { espera: /e2e_bloqueo = 1/ })
        await correr(/^e2e else$/)
      }
    },
    {
      id: '006 H3 CA1 · H3 CA2 · H2 CA4',
      nombre: '/omni check: todas las condiciones con sus valores y la rama, sin ejecutar nada',
      timeoutMs: 20000,
      async fn ({ srv, bots }) {
        const ana = bots[ANA]
        const ma = ana.marca()
        // Estado del escenario anterior: esmeralda, e2e_bloqueo = 1, e2e_compras = 1. Con nivel 10 se cumplen al
        // menos 2 de 3 aunque el hambre haya bajado.
        await srv.consola(`xp set ${ANA} 10 levels`)
        let ml = srv.marca()
        await srv.consola(`omni check e2e_tienda ${ANA}`, { espera: /→ correría: else \(1 acciones\), falla conditions\[3\]/ })
        for (const re of [/Revisión de e2e_tienda · call · para Ana_e2e/, /✔ player\.level \(10\) >= 3 → true/,
          /✔ al menos 2 de 3 → se cumplen [23]$/, /✔ has_item\('EMERALD', 1\) → true/, /✔ player\.level \(10\) >= 10 → true/,
          /✘ default\(pvar\.e2e_bloqueo, 0\) \(1\) == 0 → false/]) {
          await srv.esperarLog(re, { desde: ml, timeoutMs: 2000 })
        }
        // Sin cortar: con nivel 0 falla la primera con deny, y aun así se ven las de después.
        await srv.consola(`xp set ${ANA} 0 levels`)
        ml = srv.marca()
        await srv.consola(`omni check e2e_tienda ${ANA}`, { espera: /→ correría: deny de conditions\[1\] \(1 acciones\)/ })
        await srv.esperarLog(/✘ player\.level \(0\) >= 3 → false/, { desde: ml, timeoutMs: 2000 })
        await srv.esperarLog(/al menos 2 de 3 → se cumplen [12]$/, { desde: ml, timeoutMs: 2000 })
        await srv.esperarLog(/✘ default\(pvar\.e2e_bloqueo, 0\) \(1\) == 0 → false/, { desde: ml, timeoutMs: 2000 })
        await ana.nunca('chat', /^e2e /, { desde: ma, ms: 300 })
        await esperarVar(srv, ANA, 'e2e_compras', 1)
      }
    },
    {
      id: '006 H4 CA1 · H4 CA2 · H4 CA3',
      nombre: '/omni run --simular lista las acciones (set, message, console, delay, call) sin aplicarlas',
      timeoutMs: 20000,
      async fn ({ srv, bots }) {
        const ana = bots[ANA]
        await srv.consola(`xp set ${ANA} 10 levels`)
        await srv.consola(`omni var del ${ANA} e2e_bloqueo`)
        const ma = ana.marca()
        let ml = srv.marca()
        await srv.consola(`omni run e2e_tienda ${ANA} --simular`, { espera: /simulación de e2e_tienda para Ana_e2e: no se aplica ninguna acción/ })
        await srv.esperarLog(/• e2e_tienda\.actions\[0\] set: pvar\.e2e_compras = default\(pvar\.e2e_compras, 0\) \+ 1/, { desde: ml, timeoutMs: 2000 })
        await srv.esperarLog(/• e2e_tienda\.actions\[1\] message: e2e compra \{pvar\.e2e_compras\}/, { desde: ml, timeoutMs: 2000 })
        ml = srv.marca()
        await srv.consola(`omni run e2e_simulable ${ANA} --simular`, { espera: /simulación de e2e_simulable/ })
        await srv.esperarLog(/• e2e_simulable\.actions\[0\] console: say e2e no debe salir/, { desde: ml, timeoutMs: 2000 })
        await srv.esperarLog(/espera 40 ticks: en la simulación no se espera/, { desde: ml, timeoutMs: 2000 })
        await srv.esperarLog(/↳ disparo de call por Ana_e2e/, { desde: ml, timeoutMs: 2000 })
        await srv.esperarLog(/• e2e_simulada_llamada\.actions\[0\] message: e2e llamada/, { desde: ml, timeoutMs: 2000 })
        await dormir(2500) // más que el delay: nada llega después
        if (srv.lineas.slice(ml).some(l => /\[Server\] e2e no debe salir/.test(l))) throw new Error('la simulación corrió el comando de consola')
        await ana.nunca('chat', /^e2e (compra|llamada)/, { desde: ma, ms: 200 })
        await esperarVar(srv, ANA, 'e2e_compras', 1)
      }
    },
    {
      id: '006 H5 CA1 · H5 CA2 · H5 CA3',
      nombre: '/omni debug export guarda las trazas y simulaciones en trazas/ con el YAML de las reglas',
      timeoutMs: 20000,
      async fn ({ srv }) {
        await srv.consola('omni debug e2e_tienda', { espera: /Depurando e2e_tienda/ })
        await srv.consola(`omni run e2e_tienda ${ANA}`, { espera: /ejecutada para Ana_e2e/ })
        await srv.consola('omni debug off', { espera: /Depuración apagada/ })
        const r = await srv.consola('omni debug export', { espera: /(\d+) trazas guardadas en plugins\/OmniEvents\/trazas\/([\w-]+\.txt)/ })
        const archivo = r.match(/plugins\/OmniEvents\/trazas\/([\w-]+\.txt)/)[1]
        const texto = fs.readFileSync(path.join(srv.run, 'plugins', 'OmniEvents', 'trazas', archivo), 'utf8')
        for (const parte of ['# Traza de OmniEvents', '## Problemas de la última carga', '] regla e2e_tienda', '→ actions',
          '] simulación de e2e_simulable', '# e2e_tienda', 'trigger: call']) {
          if (!texto.includes(parte)) throw new Error(`la traza exportada no tiene «${parte}»:\n${texto.slice(0, 1500)}`)
        }
      }
    },
    {
      id: '006 H6 CA1 · H6 CA2 · H6 CA3 · H7 CA2 · H7 CA3',
      nombre: '/omni cpu mide por regla y acción con muestreo; budget avisa una vez en la consola',
      timeoutMs: 40000,
      async fn ({ srv }) {
        await srv.consola('omni cpu reset')
        await srv.consola('omni cpu on', { espera: /Perfilador encendido: mide 1 de cada 10 disparos/ })
        const ml = srv.marca()
        for (let i = 0; i < 25; i++) await srv.consola('omni call e2e_presupuesto')
        await srv.esperarLog(/La regla 'e2e_presupuesto' promedia [\d.]+ ms por disparo, más que su budget de 0\.001 ms/, { desde: ml })
        await srv.sincronizar()
        const avisos = srv.lineas.slice(ml).filter(l => /La regla 'e2e_presupuesto' promedia/.test(l))
        if (avisos.length !== 1) throw new Error(`se esperaba un solo aviso de budget y hubo ${avisos.length}`)
        const mc = srv.marca()
        await srv.consola('omni cpu', { espera: /Presupuestos/ })
        await srv.esperarLog(/Perfilador · encendido · .* · 1 de cada 10 disparos · OmniEvents ≈ [\d.]+ % del hilo principal/, { desde: mc, timeoutMs: 2000 })
        await srv.esperarLog(/e2e_presupuesto · call · 2 medidos · prom [\d.]+ µs/, { desde: mc, timeoutMs: 2000 })
        await srv.esperarLog(/ {4}actions\[0\][^ ]* · prom [\d.]+ µs/, { desde: mc, timeoutMs: 2000 })
        await srv.esperarLog(/e2e_presupuesto · [\d.]+ ms de 0\.001 ms · ✘ lo pasa/, { desde: mc, timeoutMs: 2000 })
        await srv.consola('omni cpu off', { espera: /Perfilador apagado/ })
        await srv.consola('omni cpu reset', { espera: /Perfilador reiniciado/ })
      }
    },
    {
      id: '008 H1 CA1 · H1 CA2 · H1 CA4 · H2 CA1 · H2 CA2',
      nombre: 'state: la vida baja dispara on_enter una vez (sin repetir con más daño) y la curación on_exit con los segundos',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        await srv.consola(`omni var set ${ANA} e2e_estado_on 1`)
        await srv.sincronizar()
        let m = ana.marca()
        await srv.consola(`damage ${ANA} 12 minecraft:generic`)
        await ana.esperar('chat', /^e2e vida entra 8$/, { desde: m })
        await dormir(800) // pasa la invulnerabilidad tras un golpe
        const m2 = ana.marca()
        await srv.consola(`damage ${ANA} 1 minecraft:generic`)
        await ana.hasta('perder otro medio corazón', b => b.health <= 7.5)
        await ana.nunca('chat', /^e2e vida entra/, { desde: m2, ms: 800 })
        m = ana.marca()
        await srv.consola(`effect give ${ANA} instant_health 1 10 true`)
        await ana.esperar('chat', /^e2e vida sale tras \d+(\.\d)?s$/, { desde: m })
        await srv.consola(`omni var del ${ANA} e2e_estado_on`)
      }
    },
    {
      id: '008 H2 CA1 · H3 CA1',
      nombre: 'state: entrar y salir de una zona (in_area) con teletransporte',
      async fn (ctx) {
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        let m = ana.marca()
        await srv.consola(`tp ${ANA} 32 -60 32`)
        await ana.esperar('chat', /^e2e zona entra$/, { desde: m })
        m = ana.marca()
        await srv.consola(`tp ${ANA} ${PUNTO}`)
        await ana.esperar('chat', /^e2e zona sale$/, { desde: m })
      }
    },
    {
      id: '008 H2 CA1 · H4 CA1 · H4 CA2 · H5 CA1 · H5 CA4',
      nombre: 'state: pvar por acción y por /omni var; debug, check, info, /omni state e in_state',
      timeoutMs: 25000,
      async fn (ctx) {
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        await srv.consola(`omni var del ${ANA} e2e_nivel`)
        await srv.consola('omni info e2e_estado_var', { espera: /Depende de: pvar\.e2e_nivel/ })
        await srv.consola(`omni check e2e_estado_var ${ANA}`, { espera: /→ correría: nada \(el estado no cambia\)/ })
        await srv.consola('omni debug e2e_estado_var', { espera: /Depurando e2e_estado_var/ })
        let m = ana.marca()
        const ml = srv.marca()
        await srv.consola(`omni call e2e_estado_subir ${ANA}`)
        await ana.esperar('chat', /^e2e var entra$/, { desde: m })
        await srv.esperarLog(/✔ Ana_e2e entra en el estado → on_enter/, { desde: ml })
        await srv.consola('omni debug off', { espera: /Depuración apagada/ })
        m = ana.marca()
        await srv.consola(`omni call e2e_estado_pregunta ${ANA}`)
        await ana.esperar('chat', /^e2e in_state true$/, { desde: m })
        await srv.consola('omni state e2e_estado_var', { espera: /Ana_e2e desde hace/ })
        await srv.consola(`omni check e2e_estado_var ${ANA}`, { espera: /estado guardado: dentro desde hace/ })
        m = ana.marca()
        await srv.consola(`omni var set ${ANA} e2e_nivel 0`)
        await ana.esperar('chat', /^e2e var sale$/, { desde: m })
        m = ana.marca()
        await srv.consola(`omni call e2e_estado_pregunta ${ANA}`)
        await ana.esperar('chat', /^e2e in_state false$/, { desde: m })
        await srv.consola('omni state e2e_estado_var', { espera: /0 jugador\(es\) en el estado/ })
      }
    },
    {
      id: '008 H2 CA3',
      nombre: 'state con check_every: la noche (world.day) se nota sin evento',
      timeoutMs: 20000,
      async fn (ctx) {
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        await srv.consola(`omni var set ${ANA} e2e_estado_on 1`)
        let m = ana.marca()
        try {
          await srv.consola('time set midnight')
          await ana.esperar('chat', /^e2e noche entra$/, { desde: m, timeoutMs: 4000 })
          m = ana.marca()
        } finally {
          await srv.consola('time set noon')
        }
        await ana.esperar('chat', /^e2e noche sale$/, { desde: m, timeoutMs: 4000 })
        await srv.consola(`omni var del ${ANA} e2e_estado_on`)
      }
    },
    {
      id: '008 H6 CA1 · H6 CA2 · H5 CA4',
      nombre: 'state del servidor (per_player: false) con gvar: on_enter y on_exit una vez para todos',
      async fn (ctx) {
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        const beto = ctx.bots[BETO]
        let ma = ana.marca()
        let mb = beto.marca()
        await srv.consola('omni var set global e2e_evento 1')
        await ana.esperar('chat', /^e2e servidor entra$/, { desde: ma })
        await beto.esperar('chat', /^e2e servidor entra$/, { desde: mb })
        await srv.consola('omni state e2e_estado_servidor', { espera: /estado del servidor activo desde hace/ })
        ma = ana.marca()
        await srv.consola('omni var set global e2e_evento 1') // mismo valor: no se repite
        await ana.nunca('chat', /^e2e servidor entra$/, { desde: ma, ms: 600 })
        ma = ana.marca()
        mb = beto.marca()
        await srv.consola('omni var del global e2e_evento')
        await ana.esperar('chat', /^e2e servidor sale$/, { desde: ma })
        await beto.esperar('chat', /^e2e servidor sale$/, { desde: mb })
      }
    },
    {
      id: '008 H1 CA3',
      nombre: 'state con cooldown: si on_enter no corre, su on_exit tampoco (van en pareja)',
      async fn (ctx) {
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        let m = ana.marca()
        await srv.consola(`omni var set ${ANA} e2e_pareja 1`)
        await ana.esperar('chat', /^e2e pareja entra$/, { desde: m })
        m = ana.marca()
        await srv.consola(`omni var set ${ANA} e2e_pareja 0`)
        await ana.esperar('chat', /^e2e pareja sale$/, { desde: m })
        m = ana.marca()
        await srv.consola(`omni var set ${ANA} e2e_pareja 1`) // en enfriamiento: on_enter no corre
        await srv.sincronizar()
        await srv.consola(`omni var set ${ANA} e2e_pareja 0`)
        await ana.nunca('chat', /^e2e pareja (entra|sale)$/, { desde: m, ms: 1000 })
        await srv.consola(`omni var del ${ANA} e2e_pareja`)
      }
    },
    {
      id: '008 H3 CA1',
      nombre: 'state al entrar al servidor: on_enter si ya es verdad; initial: silent no avisa',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv } = ctx
        let beto = ctx.bots[BETO]
        const m = beto.marca()
        await srv.consola(`omni var set ${BETO} e2e_inicial 1`)
        await beto.esperar('chat', /^e2e inicial entra$/, { desde: m })
        await beto.esperar('chat', /^e2e silencio entra$/, { desde: m })
        await ctx.salir(BETO)
        await dormir(1000)
        beto = await ctx.entrar(BETO)
        await beto.esperar('chat', /^e2e inicial entra$/, { timeoutMs: 8000 })
        await beto.nunca('chat', /^e2e silencio entra$/, { desde: 0, ms: 1000 })
        await srv.consola(`omni var del ${BETO} e2e_inicial`)
        await srv.consola(`op ${BETO}`, { espera: /Made Beto_e2e a server operator|Nothing changed/ })
      }
    },
    {
      id: '009 H1 CA1 · H1 CA4 · H1 CA5 · H2 CA1 · H2 CA2 · H2 CA3',
      nombre: 'expire: la pvar caduca sola, ttl cuenta, var_expire avisa y el estado que la lee sale',
      timeoutMs: 25000,
      async fn (ctx) {
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        await srv.consola(`omni var del ${ANA} e2e_racha`)
        let m = ana.marca()
        await srv.consola(`omni call e2e_racha ${ANA}`)
        await ana.esperar('chat', /^e2e racha 1 ttl true$/, { desde: m })
        await srv.consola(`omni call e2e_racha ${ANA}`)
        await ana.esperar('chat', /^e2e racha 2 ttl true$/, { desde: m })
        await ana.esperar('chat', /^e2e racha entra$/, { desde: m })
        await srv.consola(`omni var get ${ANA} e2e_racha`, { espera: /e2e_racha = 2(\.0)? \(caduca en [12]s\)/ })
        m = ana.marca()
        await ana.esperar('chat', /^e2e racha perdida 2 pvar e2e_racha$/, { desde: m, timeoutMs: 5000 })
        await ana.esperar('chat', /^e2e racha sale$/, { desde: m })
        await srv.consola(`omni var get ${ANA} e2e_racha`, { espera: /e2e_racha = nulo/ })
        // /omni var set … --expire (H1 CA5): caduca sin regla que avise (variables: [e2e_racha] no la incluye).
        await srv.consola(`omni var set ${ANA} e2e_bono 5 --expire 1s`, { espera: /e2e_bono = 5/ })
        await srv.consola(`omni var list ${ANA}`, { espera: /e2e_bono = 5(\.0)? \(caduca en [01]s\)/ })
        await dormir(2200)
        await srv.consola(`omni var get ${ANA} e2e_bono`, { espera: /e2e_bono = nulo/ })
      }
    },
    {
      id: '009 H3 CA1 · H3 CA2 · H3 CA3 · H3 CA6',
      nombre: 'block.data: un bloque recuerda quién lo marcó, /omni data lo muestra y al romperlo se borra',
      timeoutMs: 25000,
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA, 'creative')
        await srv.consola('setblock 13 -60 12 emerald_block')
        const ana = ctx.bots[ANA]
        await ana.hasta('ver el bloque de esmeralda', b => bloque(b, 13, -60, 12) === 'emerald_block')
        const esmeralda = () => ana.bot.blockAt(new ana.bot.entity.position.constructor(13, -60, 12))
        let m = ana.marca()
        await ana.bot.activateBlock(esmeralda())
        await ana.esperar('chat', /^e2e bloque marcado$/, { desde: m })
        m = ana.marca()
        await ana.bot.activateBlock(esmeralda())
        await ana.esperar('chat', /^e2e bloque de Ana_e2e$/, { desde: m })
        await ana.bot.lookAt(esmeralda().position.offset(0.5, 0.5, 0.5), true)
        m = ana.marca()
        ana.comando('omni data bloque')
        await ana.esperar('chat', /e2e_dueño = 'Ana_e2e'/, { desde: m })
        m = ana.marca()
        await ana.bot.dig(esmeralda(), true).catch(() => {})
        await ana.esperar('chat', /^e2e roto con Ana_e2e$/, { desde: m })
        await srv.consola('setblock 13 -60 12 emerald_block')
        await ana.hasta('ver el bloque nuevo', b => bloque(b, 13, -60, 12) === 'emerald_block')
        m = ana.marca()
        await ana.bot.activateBlock(esmeralda())
        await ana.esperar('chat', /^e2e bloque marcado$/, { desde: m })
        await limpiarArea(srv)
      }
    },
    {
      id: '009 H3 CA1 · H3 CA2 · H3 CA6',
      nombre: 'item.data: /ligar guarda el dueño en el ítem, y el ítem lo lleva a otro jugador',
      timeoutMs: 25000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        const beto = ctx.bots[BETO]
        await srv.consola(`clear ${BETO}`)
        await srv.consola(`give ${ANA} diamond_sword`)
        await ana.hasta('tener la espada', () => ana.cantidad('diamond_sword') === 1)
        await ana.bot.equip(ana.items('diamond_sword')[0], 'hand')
        let m = ana.marca()
        ana.comando('ligar')
        await ana.esperar('chat', /ligado a Ana_e2e\.$/, { desde: m })
        m = ana.marca()
        ana.comando('omni data mano')
        await ana.esperar('chat', /dueño = 'Ana_e2e'/, { desde: m })
        await srv.consola(`item replace entity ${BETO} weapon.mainhand from entity ${ANA} weapon.mainhand`)
        await beto.hasta('tener la espada copiada', () => beto.cantidad('diamond_sword') === 1)
        m = beto.marca()
        beto.comando('omni data mano')
        await beto.esperar('chat', /dueño = 'Ana_e2e'/, { desde: m })
        await srv.consola(`clear ${BETO}`)
      }
    },
    {
      id: '010 H3 CA1 · H3 CA2 · H3 CA3 · H4 CA1',
      nombre: '/omni test corre los tests: de las reglas sin cambiar la pvar real ni correr la consola; avisa al cargar',
      timeoutMs: 20000,
      async fn ({ srv, bots }) {
        const ana = bots[ANA]
        await srv.esperarLog(/La prueba «espera mal» de la regla 'e2e_probada_falla' falla: pvar\.e2e_x: esperaba 2, dio 1/, { desde: 0, timeoutMs: 2000 })
        await srv.consola(`omni var set ${ANA} e2e_probada 5`)
        const ml = srv.marca()
        let m = ana.marca()
        ana.comando('omni test e2e_probada')
        await ana.esperar('chat', /e2e_probada 2\/2$/, { desde: m })
        await ana.esperar('chat', /2 pruebas: todas pasan/, { desde: m })
        m = ana.marca()
        ana.comando('omni test e2e_probada_falla')
        await ana.esperar('chat', /«espera mal»/, { desde: m })
        await ana.esperar('chat', /pvar\.e2e_x: esperaba 2, dio 1/, { desde: m })
        await ana.esperar('chat', /1 pruebas: 1 fallan/, { desde: m })
        await srv.consola('omni test', { espera: /\d+ pruebas: 1 fallan, \d+ pasan/ })
        await ana.nunca('chat', /^e2e probada/, { desde: m, ms: 200 })
        if (srv.lineas.slice(ml).some(l => /\[Server\] e2e prueba no debe salir/.test(l))) throw new Error('la prueba corrió el comando de consola')
        await esperarVar(srv, ANA, 'e2e_probada', 5)
        await srv.consola(`omni var del ${ANA} e2e_probada`)
      }
    },
    {
      id: '011 H1 CA1 · H1 CA2 · H2 CA3 · H3 CA2 · H4 CA1 · H4 CA3',
      nombre: 'paquete: plantilla con parámetros y call relativo; /omni pack list|disable|enable; migración al subir de versión',
      timeoutMs: 30000,
      async fn ({ srv, bots }) {
        const ana = bots[ANA]
        await srv.consola(`omni var del ${ANA} e2e_paquete`)
        await srv.sincronizar() // la consola no espera (002 F-03)
        let m = ana.marca()
        ana.comando('e2epaquete')
        await ana.esperar('chat', /^e2e paquete: Ana_e2e tiene 3$/, { desde: m })
        // avisar es e2epack:avisar, no el de recompensas (ni una global).
        await ana.nunca('chat', /fichas/, { desde: m, ms: 200 })
        await esperarVar(srv, ANA, 'e2e_paquete', 3)
        m = ana.marca()
        ana.comando('omni pack list')
        await ana.esperar('chat', /e2epack 1\.1\.0 · 2 reglas · 1 plantillas$/, { desde: m })
        await ana.esperar('chat', /recompensas 1\.1\.0 · 3 reglas · 1 plantillas$/, { desde: m })
        m = ana.marca()
        ana.comando('omni pack disable e2epack')
        await ana.esperar('chat', /Paquete e2epack desactivado: 2 reglas\./, { desde: m })
        m = ana.marca()
        ana.comando('e2epaquete')
        await ana.nunca('chat', /^e2e paquete:/, { desde: m, ms: 500 })
        m = ana.marca()
        ana.comando('omni pack enable e2epack')
        await ana.esperar('chat', /Paquete e2epack activado: 2 reglas\./, { desde: m })
        m = ana.marca()
        ana.comando('e2epaquete')
        await ana.esperar('chat', /^e2e paquete: Ana_e2e tiene 6$/, { desde: m })
        await srv.consola(`omni var del ${ANA} e2e_paquete`)

        // Migración: la última versión cargada vuelve a 1.0.0 y un reload sube a 1.1.0 (una vez).
        const archivo = path.join(srv.run, 'plugins', 'OmniEvents', 'data', 'packs.yml')
        const texto = fs.existsSync(archivo) ? fs.readFileSync(archivo, 'utf8') : 'packs:\n'
        fs.writeFileSync(archivo, /^ {2}e2epack: .*$/m.test(texto) ? texto.replace(/^ {2}e2epack: .*$/m, '  e2epack: 1.0.0') : texto.replace(/^packs:.*$/m, 'packs:\n  e2epack: 1.0.0'))
        await srv.consola('omni var del global e2e_migrado')
        let ml = srv.marca()
        await srv.consola('omni reload', { espera: /Recargado en/ })
        await srv.esperarLog(/e2e migración 1\.0\.0 → 1\.1\.0/, { desde: ml })
        await srv.esperarLog(/Paquete e2epack: 1\.0\.0 → 1\.1\.0 \(1 migraciones\)/, { desde: ml })
        await srv.consola('omni var get global e2e_migrado', { espera: /e2e_migrado = '?1\.0\.0 a 1\.1\.0'?\s*$/ })
        ml = srv.marca()
        await srv.consola('omni reload', { espera: /Recargado en/ })
        await srv.sincronizar()
        await srv.consola('list', { espera: /players online/ })
        if (srv.lineas.slice(ml).some(l => /e2e migración/.test(l))) throw new Error('la migración corrió dos veces')
        await srv.consola('omni var del global e2e_migrado')
      }
    },
    {
      id: '012 H1 CA1 · H2 CA1 · H2 CA2',
      nombre: 'cost todo o nada con el dinero de EssentialsX (Vault): cobra todo; si falta algo no cobra nada y corre on_cost_fail; give_money',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        await srv.consola(`eco set ${ANA} 1000`)
        await srv.consola(`give ${ANA} diamond 3`)
        await srv.consola(`omni var set ${ANA} e2e_fichas 1`, { espera: /e2e_fichas = 1/ })
        await srv.sincronizar() // la consola no espera (002 F-03)
        await ana.hasta('tener 3 diamantes', () => ana.cantidad('diamond') === 3)
        let m = ana.marca()
        ana.comando('e2ecosto')
        await ana.esperar('chat', /^e2e costo comprado, saldo 700$/, { desde: m })
        await ana.hasta('quedar con 1 diamante', () => ana.cantidad('diamond') === 1)
        await esperarVar(srv, ANA, 'e2e_fichas', 0)
        // Falta un diamante y la ficha: no se cobra nada (el saldo sigue en 700).
        m = ana.marca()
        ana.comando('e2ecosto')
        await ana.esperar('chat', /^e2e costo falta: 1 × DIAMOND, 1 de pvar\.e2e_fichas \(saldo 700\)$/, { desde: m })
        await dormir(300)
        if (ana.cantidad('diamond') !== 1) throw new Error(`se cobraron diamantes sin alcanzar el costo: quedan ${ana.cantidad('diamond')}`)
        m = ana.marca()
        ana.comando('e2epagar')
        await ana.esperar('chat', /^e2e pagado, saldo 750$/, { desde: m })
        await srv.consola(`omni var del ${ANA} e2e_fichas`)
      }
    },
    {
      id: '012 H3 CA1 · H3 CA2',
      nombre: 'WorldGuard: region_enter y region_leave al teletransportarse, in_region y regions; cancel impide entrar',
      timeoutMs: 40000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA, 'creative')
        const ana = ctx.bots[ANA]
        for (const r of ['e2ezona', 'e2ebloqueo']) await srv.consola(`rg remove -w world ${r}`)
        await srv.sincronizar()
        // Las regiones se marcan con WorldEdit como Ana (OP): la consola no tiene selección.
        for (const [id, x] of [['e2ezona', 30], ['e2ebloqueo', 40]]) {
          let m = ana.marca()
          ana.comando(`/pos1 ${x},-61,30`)
          await ana.esperar('chat', /First position set/i, { desde: m })
          m = ana.marca()
          ana.comando(`/pos2 ${x + 4},-55,34`)
          await ana.esperar('chat', /Second position set/i, { desde: m })
          m = ana.marca()
          ana.comando(`rg define ${id}`)
          await ana.esperar('chat', new RegExp(`region.*${id}|${id}.*region`, 'i'), { desde: m })
        }
        await srv.consola(`tp ${ANA} 32 -60 20`)
        await srv.sincronizar()
        await ana.hasta('estar fuera de la zona', b => cerca(b.entity.position, 32, -60, 20))
        let m = ana.marca()
        await srv.consola(`tp ${ANA} 32 -60 32`)
        await ana.esperar('chat', /^e2e entra e2ezona, regiones e2ezona, dentro true$/, { desde: m })
        await ana.hasta('estar en la zona', b => cerca(b.entity.position, 32, -60, 32))
        m = ana.marca()
        await srv.consola(`tp ${ANA} 32 -60 20`)
        await ana.esperar('chat', /^e2e sale e2ezona$/, { desde: m })
        await ana.hasta('volver afuera', b => cerca(b.entity.position, 32, -60, 20))
        // La zona bloqueada cancela el teletransporte: Ana se queda donde estaba.
        m = ana.marca()
        await srv.consola(`tp ${ANA} 42 -60 32`)
        await ana.esperar('chat', /^e2e bloqueado e2ebloqueo$/, { desde: m })
        await dormir(500)
        if (!cerca(ana.bot.entity.position, 32, -60, 20)) throw new Error(`entró a la zona bloqueada: ${ana.bot.entity.position}`)
        await ana.nunca('chat', /^e2e entra/, { desde: m, ms: 200 })
        for (const r of ['e2ezona', 'e2ebloqueo']) await srv.consola(`rg remove -w world ${r}`)
        await prepararJugador(ctx, ANA)
      }
    },
    {
      id: '012 H4 CA1 · H4 CA2',
      nombre: 'Citizens: npc_click y npc_left_click con npc.name, npc.id e is_npc',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        await srv.consola('npc remove all')
        await srv.consola('npc create E2eGuia --at 15:-60:12:world', { espera: /E2eGuia/ })
        await srv.sincronizar()
        let npc
        await ana.hasta('ver al NPC', b => {
          npc = Object.values(b.entities).find(e => e.type === 'player' && e !== b.entity && cerca(e.position, 15.5, -60, 12.5, 1.5))
          return npc != null
        })
        let m = ana.marca()
        await ana.bot.lookAt(npc.position.offset(0, 1.6, 0))
        ana.bot.activateEntity(npc)
        await ana.esperar('chat', /^e2e npc clic E2eGuia id \d+ es_npc true$/, { desde: m })
        m = ana.marca()
        ana.bot.attack(npc)
        await ana.esperar('chat', /^e2e npc golpe E2eGuia$/, { desde: m })
        await srv.consola('npc remove all')
      }
    },
    {
      id: '013 H1 CA1 · H1 CA4 · H1 CA5 · H2 CA1 · H2 CA2',
      nombre: 'menú en Java: título, ítems y view; compra con cost, falta, clic derecho, redibujo, nada sale del menú, close y on_close',
      timeoutMs: 45000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        await srv.consola(`omni var del ${ANA} e2e_clics`)
        await srv.consola(`give ${ANA} emerald 6`)
        await srv.sincronizar() // la consola no espera (002 F-03)
        await ana.hasta('tener 6 esmeraldas', () => ana.cantidad('emerald') === 6)
        let m = ana.marca()
        ana.comando('e2emenu')
        await ana.esperar('chat', /^e2e menú abierto, bedrock false$/, { desde: m })
        const w = () => ana.bot.currentWindow
        await ana.hasta('ver la tienda con su título', () => w() && /e2e tienda Ana_e2e/.test(ana.tituloVentana()))
        await ana.hasta('ver los ítems y el relleno', () => w().slots[11]?.name === 'diamond_sword' && w().slots[13]?.name === 'paper' &&
          w().slots[0]?.name === 'gray_stained_glass_pane' && w().slots[22]?.name === 'barrier')
        if (ana.nombreDe(w().slots[11]) !== 'e2e espada') throw new Error(`nombre de la espada: ${ana.nombreDe(w().slots[11])}`)
        // view: false oculta la TNT y la casilla la toma el siguiente ítem visible.
        if (w().slots[15]?.name !== 'emerald_block') throw new Error(`casilla 15: ${w().slots[15]?.name} (se esperaba el visible, no la TNT)`)
        // Clic izquierdo: cobra 5 esmeraldas (cost de la 012) y entrega.
        m = ana.marca()
        await ana.bot.clickWindow(11, 0, 0)
        await ana.esperar('chat', /^e2e comprada por 5, clic left$/, { desde: m })
        await ana.hasta('tener la espada y 1 esmeralda', () => ana.cantidadEnVentana('diamond_sword') === 1 && ana.cantidadEnVentana('emerald') === 1)
        // Ya no alcanza: no se cobra nada.
        m = ana.marca()
        await ana.bot.clickWindow(11, 0, 0)
        await ana.esperar('chat', /^e2e falta 4 × EMERALD$/, { desde: m })
        // Clic derecho: otro cuerpo, con click y slot.
        m = ana.marca()
        await ana.bot.clickWindow(11, 1, 0)
        await ana.esperar('chat', /^e2e precio 5 clic right casilla 11$/, { desde: m })
        // Después de un clic el menú se vuelve a dibujar con los valores nuevos.
        await ana.bot.clickWindow(13, 0, 0)
        await ana.hasta('ver el contador en 1', () => ana.nombreDe(w().slots[13]) === 'e2e clics 1')
        await esperarVar(srv, ANA, 'e2e_clics', 1)
        // Shift-clic y tomar el relleno: el servidor lo cancela y nada sale del menú.
        await ana.bot.clickWindow(11, 0, 1)
        await ana.bot.clickWindow(0, 0, 0)
        await ana.bot.clickWindow(15, 0, 0)
        await dormir(600)
        if (w().slots[11]?.name !== 'diamond_sword' || w().slots[0]?.name !== 'gray_stained_glass_pane' || w().slots[15]?.name !== 'emerald_block') {
          throw new Error('el menú perdió un ítem: ' + [11, 0, 15].map(i => w().slots[i]?.name).join(', '))
        }
        // close: true cierra después de las acciones y corre on_close.
        m = ana.marca()
        await ana.bot.clickWindow(22, 0, 0)
        await ana.esperar('chat', /^e2e cerrar$/, { desde: m })
        await ana.esperar('chat', /^e2e menú cerrado$/, { desde: m })
        await ana.hasta('cerrar la ventana', b => !b.currentWindow)
        await dormir(300)
        for (const n of ['gray_stained_glass_pane', 'barrier', 'emerald_block', 'paper']) {
          if (ana.cantidad(n) > 0) throw new Error(`Ana se llevó ${n} del menú`)
        }
        if (ana.cantidad('diamond_sword') !== 1) throw new Error(`espadas: ${ana.cantidad('diamond_sword')} (se esperaba 1: shift-clic no copia)`)
        await srv.consola(`omni var del ${ANA} e2e_clics`)
      }
    },
    {
      id: '013 F-04',
      nombre: 'has_item cuenta por material aunque los ítems tengan nombre',
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        // Regresión (spec 013, F-04): containsAtLeast comparaba el ítem exacto y uno con nombre no contaba.
        await srv.consola(`give ${ANA} diamond[custom_name='"e2e brillante"'] 2`)
        await srv.sincronizar()
        await ana.hasta('tener 2 diamantes con nombre', () => ana.cantidad('diamond') === 2)
        const m = ana.marca()
        ana.comando('e2etiene')
        await ana.esperar('chat', /^e2e tiene diamantes true$/, { desde: m })
        await srv.consola(`clear ${ANA}`)
      }
    },
    {
      id: '013 H3 CA1 · H3 CA2 · H3 CA4',
      nombre: 'Bedrock (Geyser + Floodgate): el menú llega como formulario de botones; un botón corre su clic y vuelve con el valor nuevo; una respuesta vieja no vale; cerrar corre on_close',
      timeoutMs: 45000,
      async fn (ctx) {
        const { srv } = ctx
        const be = await ctx.entrarBedrock(BEB)
        await srv.consola('list', { espera: new RegExp(BEB_SERVIDOR.replace('.', '\\.')) })
        await srv.consola(`omni var del ${BEB_SERVIDOR} e2e_clics`)
        await srv.sincronizar()
        // La regla abre el menú con sus args (precio 5); a un jugador de Bedrock se le manda el formulario.
        let m = be.marca()
        await srv.consola(`omni run e2e_abrir_menu ${BEB_SERVIDOR}`)
        const f1 = await be.esperarFormulario(x => x.type === 'form' && /e2e tienda \.Beb_e2e/.test(x.title), { desde: m })
        const textos = f => f.form.buttons.map(b => b.text)
        if (JSON.stringify(textos(f1)) !== JSON.stringify(['e2e espada', 'e2e contar (0)', 'e2e cerrar'])) {
          throw new Error(`botones: ${JSON.stringify(textos(f1))} (solo los ítems visibles con clic, en orden de casilla)`)
        }
        if (f1.form.content !== 'e2e elige, precio 5') throw new Error(`contenido: ${JSON.stringify(f1.form.content)}`)
        await be.esperarChat(/e2e menú abierto, bedrock true/)
        // El botón del contador corre su clic y el formulario vuelve con el valor nuevo.
        m = be.marca()
        be.responder(f1, 1)
        const f2 = await be.esperarFormulario(x => x.buttons?.[1]?.text === 'e2e contar (1)', { desde: m })
        await clicsDeBeb(srv, 1)
        // Una respuesta al formulario viejo no corre nada (H3 CA4).
        m = be.marca()
        be.responder(f1, 1)
        await dormir(800)
        await clicsDeBeb(srv, 1)
        if (be.formularios.length > m) throw new Error('una respuesta vieja volvió a mandar el formulario')
        // El botón cerrar (close: true) corre su clic y on_close; no vuelve el formulario.
        const chat = be.chat.length
        be.responder(f2, 2)
        await be.esperarChat(/e2e cerrar/, { desde: chat })
        await be.esperarChat(/e2e menú cerrado/, { desde: chat })
        await dormir(500)
        if (be.formularios.length > m) throw new Error('el formulario volvió después de close: true')
        // Cerrar el formulario (la X) también corre on_close.
        m = be.marca()
        await srv.consola(`omni menu open ${BEB_SERVIDOR} e2e_menu_tienda`, { espera: /formulario de Bedrock/ })
        const f3 = await be.esperarFormulario(x => x.type === 'form', { desde: m })
        const chat2 = be.chat.length
        be.responder(f3, null)
        await be.esperarChat(/e2e menú cerrado/, { desde: chat2 })
        await srv.consola(`omni var del ${BEB_SERVIDOR} e2e_clics`)
        await ctx.salir(BEB)
      }
    },
    {
      id: 'H4 CA11 · H8 CA1',
      nombre: 'delay en spawnvip: el teletransporte llega a los 3 s sin bloquear el servidor; luego enfriamiento',
      timeoutMs: 30000,
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA, 'creative')
        const ana = ctx.bots[ANA]
        let m = ana.marca()
        ana.comando('spawnvip')
        const aviso = await ana.esperar('chat', /^Teletransportando en 3 segundos\.\.\.$/, { desde: m })
        const t = Date.now()
        await srv.consola('list', { espera: /players online/, timeoutMs: 1500 }) // el servidor responde durante el delay
        if (Date.now() - t > 1500) throw new Error('el servidor no respondió durante el delay')
        await ana.hasta('llegar al spawn VIP (0.5, ~100, 0.5)', b => Math.abs(b.entity.position.x - 0.5) < 1 &&
          Math.abs(b.entity.position.z - 0.5) < 1 && b.entity.position.y > 60, { timeoutMs: 6000 })
        const ms = Date.now() - aviso.t
        if (ms < 2800) throw new Error(`el teletransporte llegó a los ${ms} ms, antes del delay de 3 s`)
        m = ana.marca()
        ana.comando('spawnvip')
        await ana.esperar('chat', /^Espera (5\d|60)s para volver a usarlo\.$/, { desde: m })
        await ana.nunca('chat', /Teletransportando/, { desde: m, ms: 500 })
        await prepararJugador(ctx, ANA)
      }
    },
    {
      id: '014 H1 CA1',
      nombre: 'centered_message: cada línea llega centrada con los espacios de la fuente (negrita incluida)',
      async fn (ctx) {
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        const m = ana.marca()
        ana.comando('e2e centrado')
        // «e2e centrado» en negrita mide 80 px → (154 − 40) / 4 = 28 espacios; «segunda línea e2e» mide 94 → 26.
        await ana.esperar('chat', /^ {28}e2e centrado$/, { desde: m })
        await ana.esperar('chat', /^ {26}segunda línea e2e$/, { desde: m })
      }
    },
    {
      id: '014 H2 CA1 · H2 CA2',
      nombre: 'firework: el cohete sale del jugador y no hace daño salvo damage:true',
      timeoutMs: 25000,
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        // Techo de vidrio justo encima: el cohete choca y explota al lado del jugador.
        await srv.consola('fill 11 -57 11 14 -57 14 glass')
        await srv.sincronizar()
        let m = ana.marca()
        ana.comando('e2e cohete')
        await ana.esperar('entity', /firework/, { desde: m })
        await dormir(2500)
        if (ana.bot.health !== 20) throw new Error(`el cohete sin daño lastimó: vida ${ana.bot.health}`)
        m = ana.marca()
        ana.comando('e2e cohete_danino')
        await ana.esperar('entity', /firework/, { desde: m })
        await ana.hasta('perder vida con damage:true', b => b.health < 20, { timeoutMs: 5000 })
        await srv.consola('fill 11 -57 11 14 -57 14 air')
        await prepararJugador(ctx, ANA)
      }
    },
    {
      id: '014 H3 CA1 · H3 CA2',
      nombre: 'item_repair: renombrar en el yunque y limpiar en la afiladora; cancel impide la reparación',
      timeoutMs: 40000,
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        const w = () => ana.bot.currentWindow
        const abrir = async (x, tipo) => {
          await ana.bot.activateBlock(ana.bot.blockAt(new ana.bot.entity.position.constructor(x, -60, 14)))
          await ana.hasta(`abrir ${tipo}`, () => w() && new RegExp(tipo).test(w().type ?? ''))
        }
        // Casillas de la ventana: 0 y 1 entrada, 2 resultado; la barra rápida del jugador empieza en 30.
        const ponerYTomar = async (nombre) => {
          await ana.bot.clickWindow(30, 0, 0)
          await ana.bot.clickWindow(0, 0, 0)
          if (nombre != null) {
            await dormir(200)
            ana.bot._client.write('name_item', { name: nombre })
          }
          try {
            await ana.hasta('ver el resultado', () => w().slots[2] != null)
          } catch (e) {
            throw new Error(e.message + ' · casillas: ' + [0, 1, 2, 30].map(i => w()?.slots[i]?.name ?? '-').join(', ') + ' · ventana ' + w()?.type)
          }
          await ana.bot.clickWindow(2, 0, 0)
          await dormir(400)
        }
        await srv.consola('setblock 12 -60 14 anvil')
        await srv.consola('setblock 14 -60 14 grindstone[face=floor]')
        await srv.consola(`xp set ${ANA} 10 levels`)
        await srv.consola(`give ${ANA} iron_sword 1`)
        await srv.sincronizar()
        await ana.hasta('tener la espada y 10 niveles', b => ana.cantidad('iron_sword') === 1 && b.experience.level === 10)

        // Yunque: renombrar corre la regla con el nombre y el costo.
        await abrir(12, 'anvil')
        let m = ana.marca()
        await ponerYTomar('Hoja e2e')
        await ana.esperar('chat', /^e2e reparar ANVIL 'Hoja e2e' costo 1$/, { desde: m })
        ana.bot.closeWindow(w())
        await ana.hasta('tener la espada renombrada y 9 niveles', b => ana.nombreDe(ana.items('iron_sword')[0]) === 'Hoja e2e' && b.experience.level === 9)

        // Un nombre bloqueado: cancel deja la espada como estaba y no cobra niveles.
        await abrir(12, 'anvil')
        m = ana.marca()
        await ponerYTomar('bloqueada')
        await ana.esperar('chat', /^e2e reparar cancelado$/, { desde: m })
        ana.bot.closeWindow(w())
        await dormir(500)
        if (ana.bot.experience.level !== 9) throw new Error(`cobró niveles al cancelar: ${ana.bot.experience.level}`)
        if (ana.nombreDe(ana.items('iron_sword')[0]) !== 'Hoja e2e') throw new Error(`la espada cambió: ${ana.nombreDe(ana.items('iron_sword')[0])}`)

        // Afiladora: quita el encantamiento; repair_type GRINDSTONE, sin nombre ni costo.
        await srv.consola(`clear ${ANA}`)
        await srv.consola(`give ${ANA} iron_sword 1`)
        await srv.sincronizar()
        await ana.hasta('tener otra espada', () => ana.cantidad('iron_sword') === 1)
        await srv.consola(`minecraft:enchant ${ANA} sharpness 1`) // EssentialsX reemplaza enchant (012 F-03)
        await srv.sincronizar()
        await abrir(14, 'grindstone')
        m = ana.marca()
        await ponerYTomar(null)
        await ana.esperar('chat', /^e2e reparar GRINDSTONE '' costo 0$/, { desde: m })
        ana.bot.closeWindow(w())
        await srv.consola('setblock 12 -60 14 air')
        await srv.consola('setblock 14 -60 14 air')
        await prepararJugador(ctx, ANA)
      }
    },
    {
      id: '014 H4 CA1 · H4 CA2 · H4 CA3',
      nombre: 'cooldown_bypass: el permiso dado salta el enfriamiento (un OP sin él no); reset con * para todos',
      timeoutMs: 40000,
      async fn (ctx) {
        const { srv } = ctx
        // Permisos dados por permissions.yml (Paper no trae un comando de permisos): por defecto, a todos.
        const archivo = path.join(srv.run, 'permissions.yml')
        const antes = fs.existsSync(archivo) ? fs.readFileSync(archivo, 'utf8') : null
        fs.writeFileSync(archivo, ['omnievents.bypasscooldown.e2e_bypass:', '  default: true',
          'e2e.saltar.enfriamiento:', '  default: true', ''].join('\n'))
        try {
          await srv.consola('bukkit:reload permissions', { espera: /Permissions successfully reloaded/i })
          await ctx.entrar(ANA) // vuelve a calcular sus permisos al entrar
          await ctx.bots[ANA].esperar('title', /Bienvenido/, { timeoutMs: 10000 }) // la bienvenida (con delay) termina antes de seguir
          await srv.consola(`op ${ANA}`, { espera: new RegExp(`Made ${ANA} a server operator|Nothing changed`) })
          await prepararJugador(ctx, ANA)
          const ana = ctx.bots[ANA]
          await srv.consola('omni cooldown reset * *', { espera: /enfriamiento de \* \(\d+ reglas\) reiniciado para todos \(\d+ jugadores\)/ })
          const dos = async (grupo, patron) => {
            for (let i = 0; i < 2; i++) {
              const m = ana.marca()
              ana.comando(`e2e ${grupo}`)
              await ana.esperar('chat', patron, { desde: m })
            }
          }
          await dos('bypass', /^e2e bypass corrió$/)
          await dos('bypass_propio', /^e2e propio corrió$/)
          let m = ana.marca()
          ana.comando('e2e sin_bypass')
          await ana.esperar('chat', /^e2e control corrió$/, { desde: m })
          m = ana.marca()
          ana.comando('e2e sin_bypass')
          await ana.esperar('chat', /^e2e control en enfriamiento$/, { desde: m }) // Ana es OP y no lo salta
          await srv.consola(`omni check e2e_bypass ${ANA}`, { espera: /libre \(permiso omnievents\.bypasscooldown\.e2e_bypass\)/ })
          await srv.consola('omni cooldown reset e2e_sin_bypass *', { espera: /enfriamiento de e2e_sin_bypass reiniciado para todos \(\d+ jugadores\)/ })
          m = ana.marca()
          ana.comando('e2e sin_bypass')
          await ana.esperar('chat', /^e2e control corrió$/, { desde: m })
        } finally {
          if (antes == null) fs.rmSync(archivo, { force: true })
          else fs.writeFileSync(archivo, antes)
          await srv.consola('bukkit:reload permissions')
          await srv.sincronizar()
        }
      }
    },
    {
      id: '015 H1 CA1 · H1 CA2 · H1 CA3',
      nombre: 'give_item en una casilla con banderas y durabilidad; remove_item por nombre y remove_item_slot',
      async fn (ctx) {
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        const inv = () => ana.bot.inventory.slots
        let m = ana.marca()
        ana.comando('e2e items015')
        await ana.esperar('chat', /^e2e items015 listos$/, { desde: m })
        // Ventana del jugador: 5 es el casco, 36 + n la casilla n de la barra.
        await ana.hasta('el libro en la casilla 3 y el casco puesto', () => inv()[39]?.name === 'book' && inv()[5]?.name === 'iron_helmet')
        if (ana.nombreDe(inv()[39]) !== 'e2e libro') throw new Error(`el libro se llama ${ana.nombreDe(inv()[39])}`)
        if (inv()[5].durabilityUsed !== 50) throw new Error(`el casco tiene ${inv()[5].durabilityUsed} de daño, no 50`)
        await ana.hasta('5 papeles (2 con nombre)', () => ana.cantidad('paper') === 5)
        m = ana.marca()
        ana.comando('e2e quitar015')
        await ana.esperar('chat', /^e2e quitar015 listo$/, { desde: m })
        // Solo se van los dos con nombre; los tres sin nombre quedan. El libro de la casilla 3 se quita.
        await ana.hasta('3 papeles sin nombre y sin el libro', () => ana.cantidad('paper') === 3 && inv()[39] == null)
        if (ana.items('paper').some(i => ana.nombreDe(i))) throw new Error('quedó un papel con nombre')
        if (ana.cantidad('emerald') !== 5) throw new Error('remove_item tocó otro material')
        await prepararJugador(ctx, ANA)
      }
    },
    {
      id: '015 H2 CA1',
      nombre: 'summon en forma de mapa: dos soportes con nombre, vida 40 y equipo',
      async fn (ctx) {
        const { srv } = ctx
        await limpiarArea(srv)
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        const m = ana.marca()
        ana.comando('e2e guardian015')
        await ana.esperar('chat', /^e2e guardian015 listo$/, { desde: m })
        await srv.consola('minecraft:execute if entity @e[type=minecraft:armor_stand,name=e2e_guardian]', { espera: /Test passed[,.] [cC]ount: 2/ }) // 26.x: «Test passed. Count: 2»
        await srv.consola('minecraft:data get entity @e[type=minecraft:armor_stand,name=e2e_guardian,limit=1] Health', { espera: /entity data: 40(\.0)?f/ })
        // El bot ve el equipo de los soportes (casco y espada).
        await ana.hasta('ver un soporte con casco de diamante y espada', b => Object.values(b.entities).some(e =>
          e.name === 'armor_stand' && e.equipment?.some(i => i?.name === 'diamond_helmet') && e.equipment?.some(i => i?.name === 'iron_sword')))
        await limpiarArea(srv)
      }
    },
    {
      id: '015 H3 CA1',
      nombre: 'json_message: el JSON llega como texto con la expresión resuelta',
      async fn (ctx) {
        const ana = ctx.bots[ANA]
        const m = ana.marca()
        ana.comando('e2e json015')
        await ana.esperar('chat', /^e2e json de Ana_e2e$/, { desde: m })
      }
    },
    {
      id: '015 H4 CA1 · H4 CA2',
      nombre: '/omni interrupt corta lo que falta tras un delay; -s no responde',
      timeoutMs: 25000,
      async fn (ctx) {
        const { srv } = ctx
        const ana = ctx.bots[ANA]
        let m = ana.marca()
        ana.comando('e2e espera015')
        await ana.esperar('chat', /^e2e espera 1$/, { desde: m })
        await srv.consola(`omni interrupt e2e_espera015 ${ANA}`, { espera: /Cortadas 1 ejecuciones en espera de e2e_espera015/ })
        await ana.nunca('chat', /^e2e espera 2$/, { desde: m, ms: 5000 })
        // Sin cortar, la segunda llega (la prueba distingue los dos casos).
        m = ana.marca()
        ana.comando('e2e espera015')
        await ana.esperar('chat', /^e2e espera 2$/, { desde: m, timeoutMs: 8000 })
        // -s: la regla corre pero el comando no contesta.
        const desde = srv.marca()
        m = ana.marca()
        await srv.consola(`omni call e2e_silencio015 ${ANA} -s`)
        await ana.esperar('chat', /^e2e silencio corrió$/, { desde: m })
        await srv.consola('omni interrupt * -s')
        await srv.sincronizar()
        const respuestas = srv.lineas.slice(desde).filter(l => /ejecutada|Cortadas/.test(l))
        if (respuestas.length) throw new Error(`-s respondió: ${respuestas.join(' | ')}`)
        await srv.consola(`omni call e2e_silencio015 ${ANA}`, { espera: /Regla e2e_silencio015 ejecutada/ })
      }
    },
    {
      id: '015 H5 CA1',
      nombre: 'player_statistic: saltar sube la estadística JUMP y la regla corre',
      async fn (ctx) {
        const { srv } = ctx
        await prepararJugador(ctx, ANA)
        const ana = ctx.bots[ANA]
        await srv.consola(`omni var set ${ANA} e2e_saltos true`)
        await srv.sincronizar()
        try {
          const m = ana.marca()
          ana.bot.setControlState('jump', true)
          await ana.esperar('chat', /^e2e salto 1$/, { desde: m })
        } finally {
          ana.bot.setControlState('jump', false)
          await srv.consola(`omni var del ${ANA} e2e_saltos`)
        }
      }
    },
    {
      id: '015 H5 CA3',
      nombre: 'player_tab_complete + tab_complete: el bot pide autocompletar /e2e y recibe las sugerencias de la regla',
      async fn (ctx) {
        const ana = ctx.bots[ANA]
        const res = await ana.bot.tabComplete('/e2e e2e_tab', false, false)
        const nombres = res.map(r => typeof r === 'string' ? r : r.match).sort()
        if (JSON.stringify(nombres) !== JSON.stringify(['e2e_tab_alfa', 'e2e_tab_beta'])) {
          throw new Error(`sugerencias: ${JSON.stringify(nombres)} (se esperaban e2e_tab_alfa y e2e_tab_beta)`)
        }
      }
    },
    {
      id: 'H5 CA3',
      nombre: 'jugador: kick con su motivo',
      async fn (ctx) {
        const ana = ctx.bots[ANA]
        ana.comando('e2e expulsar')
        await ana.hasta('ser expulsada con el motivo', () => /e2e expulsado por la suite/.test(ana.expulsion ?? ''))
        await dormir(1000)
        await ctx.entrar(ANA)
      }
    },
    {
      id: 'H6 CA1 · H5 CA5',
      nombre: 'player_quit: el ejemplo despedida cambia el mensaje de salida',
      async fn (ctx) {
        const beto = ctx.bots[BETO]
        const mb = beto.marca()
        await ctx.salir(ANA)
        await beto.esperar('chat', /\[-\] Ana_e2e/, { desde: mb })
      }
    }
  ]
}
