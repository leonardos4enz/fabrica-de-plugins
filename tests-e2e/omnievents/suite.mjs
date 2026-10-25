// Escenarios en el juego de OmniEvents (spec 002, H4): uno por criterio de la spec 001 que la compuerta 2 no
// podía probar. El estado se prepara por consola; el bot solo hace lo que se prueba.
import fs from 'node:fs'
import path from 'node:path'
import { dormir } from '../lib/servidor.mjs'

const ANA = 'Ana_e2e'
const BETO = 'Beto_e2e'
const PUNTO = '12.5 -60 12.5' // punto de trabajo en el mundo plano (suelo en y = -61)

const cerca = (pos, x, y, z, d = 1.2) => Math.abs(pos.x - x) < d && Math.abs(pos.y - y) < d && Math.abs(pos.z - z) < d
const bloque = (bot, x, y, z) => bot.blockAt(new bot.entity.position.constructor(x, y, z))?.name
const esperarVar = (srv, jugador, nombre, valor) =>
  srv.consola(`omni var get ${jugador} ${nombre}`, { espera: new RegExp(`${jugador}\\.${nombre} = ${valor}(\\.0)?\\s*$`) })

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
  // Los ejemplos se toman siempre del código fuente: OmniEvents solo los crea si rules/ no existe, así que en un
  // servidor de prueba ya usado (o recién creado por la suite) podrían faltar o estar viejos.
  copiar: [
    { de: 'plugins/omnievents/src/main/resources/rules/ejemplos', a: 'plugins/OmniEvents/rules/ejemplos' },
    { de: 'tests-e2e/omnievents/reglas', a: 'plugins/OmniEvents/rules/e2e', retirar: true }
  ],
  datosDeJugador: ['plugins/OmniEvents/data/players/{uuid}.yml'],
  bots: [ANA, BETO],
  puerto: 25599,
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
