// Escenas de la galería de OmniEvents (spec 004, H3): una por disparador, acción, subcomando de /omni y regla de
// ejemplo. Como en la suite, el estado se prepara por consola y el bot solo hace lo que se muestra. El escenario es una
// plataforma de pasto en y = 100 (prismarine-viewer solo dibuja de y = 0 a 255): Ana mira hacia +z desde (12.5, 10.5)
// y Beto la mira desde (12.5, 21.5).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { dormir } from '../lib/servidor.mjs'

const require = createRequire(import.meta.url)
const { Vec3 } = require('vec3')
const AQUI = path.dirname(fileURLToPath(import.meta.url))
const RAIZ = path.resolve(AQUI, '../..')
const RUN = path.join(RAIZ, 'plugins/omnievents/run')
const REGLAS_RUN = path.join(RUN, 'plugins/OmniEvents/rules')

const ANA = 'Ana'
const BETO = 'Beto'
const Y = 100
const NPC = 'NoAI:1b,Silent:1b,Rotation:[180f,0f]' // criaturas quietas y mirando a Ana: la imagen no cambia entre corridas

const bot = (ctx, n = ANA) => ctx.bots[n]
const b = (ctx) => ctx.bots[ANA].bot
const v = (x, y, z) => new Vec3(x, y, z)
const consola = async (ctx, ...cmds) => {
  for (const c of cmds) await ctx.srv.consola(c)
  await ctx.srv.sincronizar()
}
const encender = async (ctx, ...reglas) => {
  for (const r of reglas) { await ctx.srv.consola(`omni enable ${r}`); ctx.encendidas.push(r) }
  await ctx.srv.sincronizar()
}
const esperar = (ctx, tipo, patron, { camara = ANA, timeoutMs = 8000 } = {}) =>
  bot(ctx, camara).esperar(tipo, patron, { desde: ctx.marcas[camara], timeoutMs })
const cap = async (ctx, nombre, tipo = 'chat', patron = /./, o = {}) => {
  bot(ctx).comando('cap ' + nombre)
  if (tipo) await esperar(ctx, tipo, patron, o)
}
const omni = async (ctx, cmd, patron = /./, o = {}) => {
  bot(ctx).comando('omni ' + cmd)
  await esperar(ctx, 'chat', patron, o)
  await dormir(300) // las respuestas de varias líneas llegan juntas
}

/** Deja a un bot en su punto, en el modo pedido, sin efectos, con vida y hambre completas e inventario vacío. */
async function prepararJugador (ctx, nombre, { modo = 'survival', x = 12.5, z = 10.5, yaw = 0, pitch = 20 } = {}) {
  await consola(ctx, `gamemode ${modo} ${nombre}`, `clear ${nombre}`, `effect clear ${nombre}`, `xp set ${nombre} 0 levels`,
    `xp set ${nombre} 0 points`, `effect give ${nombre} instant_health 1 10 true`, `effect give ${nombre} saturation 1 20 true`,
    `tp ${nombre} ${x} ${Y} ${z} ${yaw} ${pitch}`)
  const j = ctx.bots[nombre]
  await j.hasta('estar en su punto, sano y con el modo pedido', bb =>
    Math.abs(bb.entity.position.x - x) < 0.3 && Math.abs(bb.entity.position.z - z) < 0.3 && Math.abs(bb.entity.position.y - Y) < 0.6 &&
    bb.health >= 20 && bb.food >= 20 && bb.game.gameMode === modo)
  await consola(ctx, `effect clear ${nombre}`)
  await j.hasta('sin efectos', bb => Object.keys(bb.entity.effects).length === 0)
}

// ───────────── fábricas de escenas ─────────────

/** Acción mostrada con /cap <nombre> (regla cap_<nombre> de capturas/acciones.yml o flujo.yml). */
function accion (nombre, grupo, o = {}) {
  return {
    id: 'accion-' + nombre,
    grupo,
    titulo: o.titulo ?? '`' + nombre + '`',
    cubre: o.cubre ?? ['action:' + nombre],
    regla: 'regla' in o ? o.regla : 'cap_' + nombre,
    texto: o.texto,
    recorte: o.recorte ?? 'completa',
    camara: o.camara,
    varia: o.varia,
    timeoutMs: o.timeoutMs,
    soloTexto: o.soloTexto,
    async fn (ctx) {
      await o.antes?.(ctx)
      // espera: null = no se espera ningún mensaje (la escena espera el estado en `despues`).
      await cap(ctx, nombre, o.espera === null ? null : o.espera?.[0] ?? 'chat', o.espera?.[1] ?? /./, { camara: o.camara ?? ANA })
      await o.despues?.(ctx)
      await ctx.foto({ camara: o.camara, ...(o.foto ?? {}) })
    }
  }
}

/** Disparador mostrado con su regla cap_t_<disparador> (capturas/disparadores.yml), encendida solo en su escena. */
function disparador (trigger, grupo, o = {}) {
  const regla = o.soloTexto ? null : o.regla ?? 'cap_t_' + trigger
  return {
    id: 'disparador-' + trigger,
    grupo,
    titulo: '`' + trigger + '`',
    cubre: o.cubre ?? ['trigger:' + trigger],
    regla,
    texto: o.texto,
    recorte: o.recorte ?? 'completa',
    camara: o.camara,
    varia: o.varia,
    timeoutMs: o.timeoutMs,
    soloTexto: o.soloTexto,
    async fn (ctx) {
      await o.antes?.(ctx)
      await encender(ctx, regla)
      await o.estimulo(ctx)
      if (o.espera) await esperar(ctx, o.espera[0], o.espera[1], { camara: o.camara ?? ANA, timeoutMs: o.esperaMs })
      await o.despues?.(ctx)
      await ctx.foto({ camara: o.camara, ...(o.foto ?? {}) })
    }
  }
}

/** Subcomando de /omni escrito por Ana (OP); se muestra con el chat abierto para que quepa la salida. */
function comando (id, cmds, o = {}) {
  return {
    id: 'comando-' + id,
    grupo: 'Comandos de /omni',
    titulo: '`/omni ' + (o.titulo ?? id) + '`',
    cubre: o.cubre ?? ['command:' + id],
    comando: [].concat(cmds).map(c => '/omni ' + c).join(' · '),
    regla: o.regla,
    texto: o.texto,
    recorte: 'chat',
    varia: o.varia,
    async fn (ctx) {
      await o.antes?.(ctx)
      for (const c of [].concat(cmds)) await omni(ctx, c, o.espera ?? /./)
      await o.despues?.(ctx)
      await ctx.foto({ chatAbierto: true, sinBarra: true })
    }
  }
}

const atacarHasta = async (ctx, nombre, cond, intentos = 1) => {
  for (let i = 0; i < intentos && !cond(); i++) {
    const e = Object.values(b(ctx).entities).find(x => x.name === nombre)
    if (!e) break
    await b(ctx).lookAt(e.position.offset(0, e.height * 0.6, 0), true)
    b(ctx).attack(e)
    await dormir(700)
  }
}
const verEntidad = (ctx, nombre) => bot(ctx).hasta(`ver ${nombre}`, bb => Object.values(bb.entities).some(e => e.name === nombre))
  .catch(err => { throw new Error(`${err.message}; ve: ${Object.values(b(ctx).entities).map(e => e.name).join(', ')}`) })
/** Tras una muerte: que termine la animación, sin orbes de experiencia (se mueven al azar) y sin los puntos que Ana alcanzó a juntar. */
const sinRestos = async (ctx) => { await dormir(1300); await consola(ctx, 'kill @e[type=experience_orb]', `xp set ${ANA} 0 points`); await dormir(400) }
/**
 * Tras reaparecer, el bot rehace su copia del mundo: se lo vuelve a poner en su punto mirando 20° hacia abajo (un tp que
 * llega antes de terminar de reaparecer se pierde) y la vista 3D se recarga y espera los chunks.
 */
const trasReaparecer = async (ctx) => {
  await dormir(500)
  await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`)
  await bot(ctx).hasta('mirar 20° hacia abajo', bb => Math.abs(bb.entity.pitch + 20 * Math.PI / 180) < 0.02)
  await ctx.camara(ANA).recargar(b(ctx))
  await dormir(2500)
}
const bloqueEn = (ctx, x, y, z) => b(ctx).blockAt(v(x, y, z))
const tener = (ctx, item, n = 1) => bot(ctx).hasta(`tener ${n} ${item}`, () => bot(ctx).cantidad(item) >= n)
const enMano = async (ctx, item) => {
  await tener(ctx, item)
  await b(ctx).equip(bot(ctx).items(item)[0], 'hand')
}

const MENSAJES = 'Acciones · Mensajes'
const COMANDOS = 'Acciones · Comandos'
const JUGADOR = 'Acciones · Jugador'
const MUNDO = 'Acciones · Mundo'
const EVENTO = 'Acciones · Evento'
const FLUJO = 'Acciones · Control de flujo'
const OPCIONES = 'Opciones de una regla'
const D_JUGADOR = 'Disparadores · Jugador'
const D_COMBATE = 'Disparadores · Combate'
const D_BLOQUES = 'Disparadores · Bloques'
const D_ITEMS = 'Disparadores · Ítems'
const D_INVENTARIO = 'Disparadores · Inventario'
const D_ENTIDADES = 'Disparadores · Entidades'
const D_SERVIDOR = 'Disparadores · Servidor'

const COFRE = ' (El visor 3D no dibuja cofres, camas, carteles ni mesas: los bloques con entidad propia no se ven en la vista del mundo.)'
const SONIDO = 'El sonido no se ve. El bot recibe el paquete de sonido (la suite en el juego lo comprueba por su tono), pero una imagen no puede mostrarlo.'
const PARTICULAS = 'El visor 3D no dibuja partículas, así que una imagen no puede mostrarlas con fidelidad. La suite en el juego comprueba que el paquete llega al jugador.'

const escenas = [
  // ───────────── Ejemplos (las reglas de src/main/resources/rules/ejemplos tal cual) ─────────────
  {
    id: 'ejemplo-bienvenida', grupo: 'Ejemplos', titulo: '`bienvenida`', regla: 'bienvenida', sinPreparar: true,
    cubre: ['example:bienvenida', 'trigger:player_join'],
    texto: 'La primera vez que Ana entra: anuncio para todos, pan de bienvenida y, dos segundos después, el título.',
    async fn (ctx) {
      // Ana ya entró en `antes`: lo que recibió desde su entrada está en vistos.
      await bot(ctx).esperar('title', /Bienvenido/, { desde: ctx.marcaEntradaAna, timeoutMs: 10000 })
      await ctx.foto({ desde: ctx.marcaEntradaAna, chat: bot(ctx).vistos.filter(x => x.tipo === 'chat' && x.t <= ctx.tituloAna).map(x => x.json) })
    }
  },
  {
    id: 'ejemplo-recompensa_diaria', grupo: 'Ejemplos', titulo: '`recompensa_diaria`', regla: 'recompensa_diaria', sinPreparar: true,
    cubre: ['example:recompensa_diaria', 'option:limit'], recorte: 'chat',
    texto: 'Un uso por jugador por día (`limit: 1` y `limit_reset: daily`): cinco segundos después de entrar llega la recompensa.',
    async fn (ctx) {
      await bot(ctx).esperar('chat', /Recompensa diaria/, { desde: ctx.marcaEntradaAna, timeoutMs: 10000 })
      await ctx.foto({ desde: ctx.marcaEntradaAna, sinTitulo: true })
    }
  },
  {
    id: 'accion-set_join_message', grupo: EVENTO, titulo: '`set_join_message`', regla: 'bienvenida', sinPreparar: true, camara: BETO,
    cubre: ['action:set_join_message'], recorte: 'chat',
    texto: 'Lo que ve Beto cuando entra Ana: el mensaje de entrada cambiado por el ejemplo `bienvenida` y su anuncio.',
    async fn (ctx) {
      await ctx.foto({ camara: BETO, desde: ctx.marcaBetoAntesDeAna, sinTitulo: true })
    }
  },
  {
    id: 'ejemplo-diamantes_contados', grupo: 'Ejemplos', titulo: '`diamantes_contados`', regla: 'diamantes_contados',
    cubre: ['example:diamantes_contados', 'trigger:block_break'],
    texto: 'Al romper mena de diamante en supervivencia se suma `pvar.diamantes` y se muestra en la barra de acción.',
    async fn (ctx) {
      await consola(ctx, `omni var del ${ANA} diamantes`, `give ${ANA} diamond_pickaxe`, `setblock 12 ${Y} 13 diamond_ore`)
      await enMano(ctx, 'diamond_pickaxe')
      await bot(ctx).hasta('ver el diamante', () => bloqueEn(ctx, 12, Y, 13)?.name === 'diamond_ore')
      await b(ctx).dig(bloqueEn(ctx, 12, Y, 13), true)
      await esperar(ctx, 'actionbar', /Diamantes: 1/)
      await ctx.foto({ recorte: 'completa' })
    }
  },
  {
    id: 'ejemplo-bloque_protegido', grupo: 'Ejemplos', titulo: '`bloque_protegido` · `cancel`', regla: 'bloque_protegido',
    cubre: ['example:bloque_protegido', 'action:cancel'],
    texto: 'Una condición con `||`, `&&`, `!` y paréntesis. `cancel` anula el evento: la roca madre sigue en su lugar.',
    async fn (ctx) {
      await prepararJugador(ctx, ANA, { modo: 'creative' })
      await consola(ctx, `setblock 12 ${Y} 13 bedrock`)
      await bot(ctx).hasta('ver la roca madre', () => bloqueEn(ctx, 12, Y, 13)?.name === 'bedrock')
      await b(ctx).dig(bloqueEn(ctx, 12, Y, 13), true).catch(() => {})
      await esperar(ctx, 'chat', /No puedes romper/)
      await ctx.srv.consola(`execute if block 12 ${Y} 13 bedrock`, { espera: /Test passed/ })
      // mineflayer 4.39 en creativo borra el bloque en su copia del mundo aunque el servidor cancele la rotura (no
      // aplica la corrección del servidor): se le reenvía el bloque real, que sigue siendo roca madre.
      await consola(ctx, `setblock 12 ${Y} 13 air`, `setblock 12 ${Y} 13 bedrock`)
      await bot(ctx).hasta('la roca madre sigue ahí', () => bloqueEn(ctx, 12, Y, 13)?.name === 'bedrock')
      await ctx.foto()
    }
  },
  {
    id: 'ejemplo-suerte_minera', grupo: 'Ejemplos', titulo: '`suerte_minera`', regla: 'suerte_minera', cubre: ['example:suerte_minera'],
    soloTexto: 'Depende del azar: `chance(2)` (2 % de las piedras) y después un `random` ponderado. No se puede provocar a propósito sin cambiar el ejemplo. Cuando sale, el jugador ve en el chat «¡Encontraste hierro escondido!» (70 %), «¡Una pepita de oro!» (25 %) o, para todos, «<jugador> encontró un diamante de la suerte.» (5 %).'
  },
  {
    id: 'ejemplo-filtro_palabras', grupo: 'Ejemplos', titulo: '`filtro_palabras`', regla: 'filtro_palabras', recorte: 'chat',
    cubre: ['example:filtro_palabras', 'trigger:player_chat'],
    texto: 'El mensaje no llega a nadie (`cancel` en el chat asíncrono) y el autor recibe el aviso. A la tercera, `console: kick`.',
    async fn (ctx) {
      await consola(ctx, `omni var del ${ANA} avisos`)
      bot(ctx).chat('eres un tonto')
      await esperar(ctx, 'chat', /Cuida tu lenguaje/)
      await consola(ctx, `omni var del ${ANA} avisos`)
      await ctx.foto()
    }
  },
  {
    id: 'ejemplo-menciones', grupo: 'Ejemplos', titulo: '`menciones`', regla: 'menciones', camara: BETO, recorte: ['barra', 'chat', 'hud'],
    cubre: ['example:menciones'],
    texto: 'Beto ve en su barra de acción que Ana lo mencionó (y oye un sonido). El texto de Ana nunca se interpreta como MiniMessage.',
    async fn (ctx) {
      bot(ctx).chat('hola beto, ¿vienes a la mina?')
      await esperar(ctx, 'actionbar', /te mencionó/, { camara: BETO })
      await ctx.foto({ camara: BETO })
    }
  },
  {
    id: 'ejemplo-spawn_vip', grupo: 'Ejemplos', titulo: '`spawn_vip`', regla: 'spawn_vip', recorte: 'chat', varia: true, timeoutMs: 20000,
    cubre: ['example:spawn_vip', 'option:cooldown'],
    texto: 'Comando real `/spawnvip` (alias `/svip`): avisa, espera 3 s con `delay`, teletransporta. El segundo intento cae en ' +
      'el enfriamiento de 1 minuto (`on_cooldown`). Varía: los segundos que faltan dependen de cuánto tardó la escena.',
    async fn (ctx) {
      await consola(ctx, `omni cooldown reset spawn_vip ${ANA}`)
      bot(ctx).comando('spawnvip')
      await esperar(ctx, 'chat', /Teletransportando/)
      await bot(ctx).hasta('llegar al spawn VIP', bb => Math.abs(bb.entity.position.x - 0.5) < 0.5 && Math.abs(bb.entity.position.z - 0.5) < 0.5, { timeoutMs: 6000 })
      bot(ctx).comando('svip')
      await esperar(ctx, 'chat', /Espera \d+s/)
      await ctx.foto()
    }
  },
  {
    id: 'ejemplo-monedas', grupo: 'Ejemplos', titulo: '`monedas`', regla: 'monedas', recorte: 'chat',
    cubre: ['example:monedas', 'trigger:command'],
    texto: 'Un comando real con autocompletado (`tab`) y subcomandos resueltos con `if`.',
    async fn (ctx) {
      await consola(ctx, `omni var set ${ANA} monedas 125`, `omni var set ${BETO} monedas 40`)
      for (const c of ['monedas', 'monedas dar Beto 999', 'monedas dar Beto 15']) {
        bot(ctx).comando(c)
        await dormir(400)
      }
      await esperar(ctx, 'chat', /Diste 15 monedas/)
      await ctx.foto()
    }
  },
  {
    id: 'ejemplo-sumar_monedas', grupo: 'Ejemplos', titulo: '`sumar_monedas` (`call`)', regla: 'sumar_monedas', camara: BETO, recorte: 'chat',
    cubre: ['example:sumar_monedas', 'trigger:call', 'action:call'],
    texto: 'Lo que ve Beto cuando Ana le da monedas: `monedas` llama a `sumar_monedas` con `call`, y sus argumentos llegan como `var.jugador` y `var.cantidad`.',
    async fn (ctx) {
      await consola(ctx, `omni var set ${ANA} monedas 125`, `omni var set ${BETO} monedas 40`)
      bot(ctx).comando('monedas dar Beto 15')
      await esperar(ctx, 'chat', /Recibiste 15 monedas/, { camara: BETO })
      await ctx.foto({ camara: BETO })
    }
  },
  {
    id: 'ejemplo-golpe_critico', grupo: 'Ejemplos', titulo: '`golpe_critico`', regla: 'golpe_critico', timeoutMs: 60000, varia: true,
    cubre: ['example:golpe_critico'],
    texto: 'Con espada, un 15 % de los golpes (`chance(15)`) hace el doble de daño con `set_damage: 200%`. La escena golpea hasta que sale. Varía: cuántos golpes hacen falta es azar, y cada golpe empuja a la vaca.',
    async fn (ctx) {
      await consola(ctx, `give ${ANA} iron_sword`, `summon cow 12.5 ${Y} 13.5 {${NPC},attributes:[{id:"minecraft:max_health",base:1000.0}],Health:1000f}`)
      await enMano(ctx, 'iron_sword')
      await verEntidad(ctx, 'cow')
      const m = bot(ctx).marca()
      await atacarHasta(ctx, 'cow', () => bot(ctx).vistos.slice(m).some(x => x.tipo === 'actionbar' && /Crítico/.test(x.texto)), 60)
      await esperar(ctx, 'actionbar', /Crítico/, { timeoutMs: 1000 })
      await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`)
      await ctx.foto({ sinTitulo: true })
    }
  },
  {
    id: 'ejemplo-cazador', grupo: 'Ejemplos', titulo: '`cazador`', regla: 'cazador',
    cubre: ['example:cazador', 'trigger:player_kill', 'action:give_level'],
    texto: 'Cada 50 monstruos se anuncia y se dan 5 niveles (`give_level`). La escena deja `pvar.monstruos` en 49 y Ana caza una creeper.',
    async fn (ctx) {
      await consola(ctx, `omni var set ${ANA} monstruos 49`, `summon creeper 12.5 ${Y} 13.5 {${NPC},Health:1f}`)
      await verEntidad(ctx, 'creeper')
      await atacarHasta(ctx, 'creeper', () => !Object.values(b(ctx).entities).some(e => e.name === 'creeper'), 5)
      await esperar(ctx, 'chat', /cazó/)
      await bot(ctx).hasta('nivel 5', bb => bb.experience.level === 5)
      await sinRestos(ctx)
      await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`)
      await dormir(500)
      await ctx.foto()
    }
  },
  {
    id: 'ejemplo-mensaje_de_muerte', grupo: 'Ejemplos', titulo: '`mensaje_de_muerte`', regla: 'mensaje_de_muerte', camara: BETO, recorte: 'chat',
    cubre: ['example:mensaje_de_muerte'],
    texto: 'Cuando a Ana la mata Beto, el ejemplo cambia el mensaje de muerte con `set_death_message`.',
    async fn (ctx) {
      await prepararJugador(ctx, BETO, { modo: 'creative', z: 12.5, yaw: 180, pitch: 10 })
      await consola(ctx, `damage ${ANA} 19 minecraft:generic`)
      await bot(ctx).hasta('quedar con 1 de vida', bb => bb.health <= 1.5)
      await dormir(800) // el daño deja medio segundo de invulnerabilidad
      const ana = b(ctx).entity
      await bot(ctx, BETO).bot.lookAt(ana.position.offset(0, 1.2, 0), true)
      bot(ctx, BETO).bot.attack(Object.values(bot(ctx, BETO).bot.entities).find(e => e.username === ANA))
      await esperar(ctx, 'chat', /cayó ante/, { camara: BETO })
      await prepararJugador(ctx, BETO, { modo: 'creative', z: 21.5, yaw: 180, pitch: 15 })
      await ctx.foto({ camara: BETO })
    }
  },
  {
    id: 'ejemplo-huevo_sin_pollitos', grupo: 'Ejemplos', titulo: '`huevo_sin_pollitos` · `custom` · `set_event`', regla: 'huevo_sin_pollitos',
    recorte: 'chat', cubre: ['example:huevo_sin_pollitos', 'trigger:custom', 'action:set_event'],
    texto: 'Cualquier evento de Bukkit por su clase (`PlayerEggThrowEvent`); `set_event: "hatching = false"` usa su setter. ' +
      'El ejemplo viene apagado: la escena lo enciende con `/omni enable`.',
    async fn (ctx) {
      await encender(ctx, 'huevo_sin_pollitos')
      await consola(ctx, `give ${ANA} egg`)
      await enMano(ctx, 'egg')
      b(ctx).activateItem()
      await esperar(ctx, 'chat', /no tendrá pollitos/)
      await ctx.foto()
    }
  },
  {
    id: 'ejemplo-sol_del_desierto', grupo: 'Ejemplos', titulo: '`sol_del_desierto` · `timer`', regla: 'sol_del_desierto', timeoutMs: 20000,
    cubre: ['example:sol_del_desierto'],
    texto: '`timer` por jugador cada 5 s: en el desierto, de día y en supervivencia, aplica Hambre y avisa. La escena convierte la plataforma en desierto con `/fillbiome`.',
    async fn (ctx) {
      await consola(ctx, `fillbiome -4 96 -4 28 110 28 minecraft:desert`)
      await esperar(ctx, 'actionbar', /sol del desierto/, { timeoutMs: 12000 })
      await bot(ctx).hasta('tener Hambre', bb => Object.keys(bb.entity.effects).length > 0)
      await consola(ctx, `fillbiome -4 96 -4 28 110 28 minecraft:plains`)
      await ctx.foto()
    },
    async limpiar (ctx) { await consola(ctx, `fillbiome -4 96 -4 28 110 28 minecraft:plains`) }
  },
  {
    id: 'ejemplo-anuncio', grupo: 'Ejemplos', titulo: '`anuncio`', regla: 'anuncio', cubre: ['example:anuncio'],
    soloTexto: 'Es un `timer` global cada 10 minutos: la escena no espera tanto. Cuando corre, todos ven en el chat uno de los dos consejos al azar («Consejo: usa /monedas para ver tus monedas.»). El disparador `timer` tiene su imagen más abajo.'
  },
  {
    id: 'ejemplo-mensaje_noche', grupo: 'Ejemplos', titulo: '`mensaje_noche`', regla: 'mensaje_noche', cubre: ['example:mensaje_noche'],
    soloTexto: 'Es un `clock` a las 22:00 (hora del servidor): la escena no puede adelantar el reloj sin cambiar el ejemplo. Todos ven en el chat «Son las 22:00. ¡No olvides descansar!». El disparador `clock` tiene su imagen más abajo.'
  },
  {
    id: 'ejemplo-sin_caida_vip', grupo: 'Ejemplos', titulo: '`sin_caida_vip`', regla: 'sin_caida_vip', cubre: ['example:sin_caida_vip'],
    soloTexto: 'Cancela el daño por caída de quien tiene el permiso `omnievents.vip`. No muestra nada: lo que se nota es que la vida no baja.'
  },
  {
    id: 'ejemplo-arranque', grupo: 'Ejemplos', titulo: '`arranque` · `server_start` · `log`', regla: 'arranque', recorte: 'consola', varia: true,
    cubre: ['example:arranque', 'trigger:server_start', 'action:log'],
    texto: 'Al arrancar el servidor se suma `gvar.arranques` y se escribe en el log. **No lo ve un jugador**: la imagen es la consola del servidor. Varía: el número de arranque crece en cada corrida.',
    async fn (ctx) {
      await ctx.foto({ consola: lineasDeConsola(ctx, /OmniEvents listo/) })
    }
  },

  // ───────────── Comandos de /omni ─────────────
  comando('help', 'help', { texto: 'Cada jugador ve solo los subcomandos que su permiso le deja usar (`omnievents.command.<sub>`).' }),
  comando('reload', 'reload', { espera: /Recargado/, varia: true, texto: 'Recarga config, mensajes y reglas sin reiniciar. Varía: muestra cuánto tardó.' }),
  comando('validate', 'validate', {
    espera: /errores/, regla: 'saludo_roto',
    texto: 'Cada error dice archivo, ruta y causa, con «¿quisiste decir…?». La escena agrega una regla con errores a propósito; las reglas con errores no se cargan y el resto sigue funcionando.',
    async antes (ctx) {
      fs.cpSync(path.join(AQUI, 'capturas-errores'), path.join(REGLAS_RUN, 'capturas-errores'), { recursive: true })
    },
    async despues (ctx) {
      fs.rmSync(path.join(REGLAS_RUN, 'capturas-errores'), { recursive: true, force: true })
    }
  }),
  comando('list', 'list ejemplos/', { titulo: 'list [filtro]', espera: /Reglas/, texto: 'Filtra por nombre, disparador o archivo. Cada regla se puede pulsar para ver su detalle.' }),
  comando('info', 'info monedas', { titulo: 'info <regla>', espera: /monedas/ }),
  comando('enable', ['disable menciones', 'enable menciones'], {
    titulo: 'enable | disable <regla>', cubre: ['command:enable', 'command:disable'], espera: /menciones/
  }),
  {
    id: 'comando-debug', grupo: 'Comandos de /omni', titulo: '`/omni debug <regla|all|off>`', cubre: ['command:debug'], recorte: 'chat', varia: true,
    comando: '/omni debug diamantes_contados', regla: 'diamantes_contados',
    texto: 'En cada disparo se ve cada condición con sus valores, la rama que se tomó y cada acción. Aquí, con el ejemplo `diamantes_contados` al romper una mena. Varía: el fondo es el bloque recién roto.',
    async fn (ctx) {
      await consola(ctx, `omni var del ${ANA} diamantes`, `give ${ANA} diamond_pickaxe`, `setblock 12 ${Y} 13 diamond_ore`)
      await enMano(ctx, 'diamond_pickaxe')
      await bot(ctx).hasta('ver el diamante', () => bloqueEn(ctx, 12, Y, 13)?.name === 'diamond_ore')
      await omni(ctx, 'debug diamantes_contados', /Depurando/)
      await b(ctx).dig(bloqueEn(ctx, 12, Y, 13), true)
      await esperar(ctx, 'actionbar', /Diamantes: 1/)
      await dormir(400)
      await ctx.foto({ chatAbierto: true })
      bot(ctx).comando('omni debug off')
      await dormir(300)
    }
  },
  comando('eval', ["eval player.name + ' está en ' + player.world.name", "eval player.gamemode == 'SURVIVAL' && player.health > 10", 'eval max(3, 7) * 2 + 1'], {
    titulo: 'eval <expresión>', texto: 'Evalúa cualquier expresión con el contexto de quien la escribe y dice el tipo del resultado.'
  }),
  comando('call', 'call sumar_monedas Beto jugador="Beto" cantidad=5', {
    titulo: 'call <regla> [jugador] [k=v…]', espera: /ejecutada/,
    texto: 'Ejecuta una regla `trigger: call` con argumentos; aquí el ejemplo `sumar_monedas`.',
    async antes (ctx) { await consola(ctx, `omni var set ${BETO} monedas 40`) }
  }),
  comando('var', ['var set Ana gemas 7', 'var get Ana gemas', 'var del Ana gemas'], {
    titulo: 'var get | set | del <jugador|global> <nombre> [valor]', texto: 'Variables por jugador (`pvar`) y globales (`gvar`), persistentes.'
  }),
  comando('var-list', 'var list global', {
    titulo: 'var list <jugador|global>', cubre: [], varia: true,
    texto: 'Todas las variables de un jugador o del servidor. Varía: `arranques` crece en cada corrida.'
  }),
  comando('cooldown', 'cooldown reset spawn_vip Ana', { titulo: 'cooldown reset <regla> [jugador|global]', espera: /reiniciado/ }),
  comando('limit', 'limit reset recompensa_diaria Ana', { titulo: 'limit reset <regla> [jugador|global]', espera: /reiniciado/ }),
  comando('stats', 'stats', { varia: true, texto: 'Disparos, aciertos, errores y tiempo promedio y máximo por regla; oyentes y tareas activos. Varía: los tiempos cambian en cada corrida.' }),
  comando('triggers', 'triggers', { espera: /Disparadores/ }),
  comando('actions', 'actions', { espera: /Acciones/ }),
  comando('item', ['item save espada_demo', 'item list', 'item delete espada_demo'], {
    titulo: 'item save | list | delete <nombre>', texto: 'Guarda el ítem de la mano para usarlo con `give_item: saved:<nombre>`.',
    async antes (ctx) {
      await consola(ctx, `give ${ANA} diamond_sword[custom_name='{"text":"Espada del torneo","color":"gold","italic":false}']`)
      await enMano(ctx, 'diamond_sword')
    }
  }),
  comando('import', 'import conditionalevents', {
    titulo: 'import conditionalevents', espera: /importados/,
    texto: 'Convierte los eventos de ConditionalEvents a `rules/importado/`. La escena pone una configuración de ConditionalEvents de dos eventos.',
    async antes () {
      fs.cpSync(path.join(AQUI, 'capturas-ce'), path.join(RUN, 'plugins/ConditionalEvents'), { recursive: true })
    },
    async despues () {
      fs.rmSync(path.join(RUN, 'plugins/ConditionalEvents'), { recursive: true, force: true })
      fs.rmSync(path.join(REGLAS_RUN, 'importado'), { recursive: true, force: true })
    }
  }),
  comando('selftest', 'selftest', { espera: /Autoprueba/, texto: 'Casos incorporados del motor que se prueban en el servidor vivo.' }),

  // ───────────── Acciones · Mensajes ─────────────
  accion('message', MENSAJES, { recorte: 'chat' }),
  accion('broadcast', MENSAJES, { camara: BETO, recorte: 'chat', texto: 'Lo que ve Beto cuando Ana dispara el anuncio.' }),
  accion('actionbar', MENSAJES, { espera: ['actionbar', /Puntos/], recorte: ['barra', 'hud'] }),
  accion('title', MENSAJES, {
    espera: ['subtitle', /ronda/], texto: '`título;subtítulo;entrada;permanencia;salida` (en ticks).',
    async despues () { await dormir(600) }
  }),
  accion('bossbar', MENSAJES, {
    espera: ['bossbar', /Guardián/], recorte: 'bossbar', texto: '`texto;segundos;color;progreso`.',
    async despues (ctx) { await bot(ctx).hasta('ver la bossbar', bb => Object.keys(bb.bossBars ?? {}).length > 0) }
  }),
  accion('sound', MENSAJES, { soloTexto: SONIDO, regla: null }),
  accion('stop_sound', MENSAJES, { soloTexto: SONIDO, regla: null }),
  {
    id: 'accion-console_message', grupo: MENSAJES, titulo: '`console_message`', regla: 'cap_console_message', recorte: 'consola',
    cubre: ['action:console_message'], texto: 'Escribe en la consola del servidor. **No lo ve un jugador**: la imagen es la consola.',
    async fn (ctx) {
      const desde = ctx.srv.marca()
      await cap(ctx, 'console_message', null)
      await ctx.srv.esperarLog(/Ana abrió el cofre/, { desde })
      await ctx.foto({ consola: lineasDeConsola(ctx, /Ana abrió el cofre/, desde) })
    }
  },

  // ───────────── Acciones · Comandos ─────────────
  accion('console', COMANDOS, { recorte: 'chat', espera: ['chat', /palanca/], texto: 'Un comando de consola; aquí `say`, que todos ven en el chat.' }),
  accion('command', COMANDOS, { recorte: 'chat', espera: ['chat', /monedas/], texto: 'Un comando como si lo escribiera el jugador: aquí `/monedas`.' }),
  accion('chat', COMANDOS, { camara: BETO, recorte: 'chat', espera: ['chat', /carrera/], texto: 'El jugador «dice» el texto en el chat: lo ve todo el servidor (aquí, Beto).' }),
  accion('send_to_server', COMANDOS, { regla: null, soloTexto: 'Manda al jugador a otro servidor de la red: necesita un proxy (Velocity o BungeeCord), que el servidor de prueba no tiene.' }),

  // ───────────── Acciones · Jugador ─────────────
  accion('teleport', JUGADOR, {
    espera: ['actionbar', /torre/], texto: '`mundo;x;y;z;yaw;pitch` (con `~` para relativo). Ana sube a la torre y mira la plataforma.',
    async antes (ctx) { await consola(ctx, `fill 11 ${Y} 5 14 ${Y + 3} 8 stone_bricks`) },
    async despues (ctx) { await bot(ctx).hasta('estar en la torre', bb => bb.entity.position.y > Y + 3.5 && bb.entity.onGround) }
  }),
  accion('heal', JUGADOR, {
    espera: null, recorte: 'hud', texto: 'Ana tenía 10 de vida (la escena la daña por consola) y `heal: 6` la deja en 16.',
    async antes (ctx) {
      await consola(ctx, `damage ${ANA} 10 minecraft:generic`)
      await bot(ctx).hasta('quedar con 10 de vida', bb => bb.health === 10)
    },
    async despues (ctx) { await bot(ctx).hasta('16 de vida', bb => bb.health === 16) }
  }),
  accion('damage', JUGADOR, { espera: null, recorte: 'hud', async despues (ctx) { await bot(ctx).hasta('13 de vida', bb => bb.health === 13) } }),
  accion('set_food', JUGADOR, { espera: null, recorte: 'hud', async despues (ctx) { await bot(ctx).hasta('hambre 7', bb => bb.food === 7) } }),
  accion('feed', JUGADOR, {
    espera: null, recorte: 'hud', texto: 'Ana tenía 7 de comida (con `set_food`) y `feed: 6` la deja en 13.',
    async antes (ctx) {
      await cap(ctx, 'set_food', null)
      await bot(ctx).hasta('hambre 7', bb => bb.food === 7)
    },
    async despues (ctx) { await bot(ctx).hasta('hambre 13', bb => bb.food === 13) }
  }),
  accion('gamemode', JUGADOR, { espera: null, recorte: 'hud', texto: 'En creativo no hay vida, hambre ni experiencia en el HUD.', async despues (ctx) { await bot(ctx).hasta('creativo', bb => bb.game.gameMode === 'creative') } }),
  accion('fly', JUGADOR, {
    espera: ['actionbar', /volar/], varia: true, texto: 'Permite volar en supervivencia. Ana despega con doble salto. Varía: la altura depende del tiempo de vuelo.',
    async despues (ctx) {
      await dormir(300)
      b(ctx).creative.startFlying()
      b(ctx).setControlState('jump', true)
      await dormir(700)
      b(ctx).setControlState('jump', false)
    }
  }),
  accion('effect', JUGADOR, {
    espera: null, texto: '`EFECTO;segundos;nivel;partículas`. Arriba a la derecha, los efectos buenos y, debajo, los malos.',
    async despues (ctx) { await bot(ctx).hasta('4 efectos', bb => Object.keys(bb.entity.effects).length === 4) }
  }),
  accion('remove_effect', JUGADOR, {
    espera: null, texto: 'Después de `cap_effect`, `remove_effect: SLOWNESS` quita solo la lentitud.', regla: ['cap_effect', 'cap_remove_effect'],
    async antes (ctx) {
      await cap(ctx, 'effect', null)
      await bot(ctx).hasta('4 efectos', bb => Object.keys(bb.entity.effects).length === 4)
    },
    async despues (ctx) { await bot(ctx).hasta('3 efectos', bb => Object.keys(bb.entity.effects).length === 3) }
  }),
  accion('fire', JUGADOR, { regla: null, soloTexto: 'Las llamas en pantalla las dibuja el cliente; el bot solo recibe la marca «en llamas» de la entidad, y el render no la dibuja. Lo que sí se ve es la vida que baja.' }),
  accion('freeze', JUGADOR, { regla: null, soloTexto: 'La escarcha en pantalla la dibuja el cliente a partir de los ticks de congelamiento; el render no la dibuja.' }),
  accion('velocity', JUGADOR, {
    espera: null, varia: true, texto: '`x;y;z`: aquí un impulso hacia arriba. Varía: la foto se toma en el aire.',
    async despues (ctx) { await bot(ctx).hasta('estar en el aire', bb => bb.entity.position.y > Y + 1.5) }
  }),
  accion('launch', JUGADOR, {
    espera: null, varia: true, texto: 'Lanza hacia donde mira el jugador (aquí, hacia adelante y abajo, a ras del suelo). Varía: la foto se toma en movimiento.',
    async antes (ctx) { await consola(ctx, `tp ${ANA} 12.5 ${Y} 4.5 0 -25`) },
    async despues (ctx) { await bot(ctx).hasta('estar en el aire', bb => bb.entity.position.y > Y + 1) }
  }),
  accion('give_item', JUGADOR, {
    espera: null, recorte: 'gui', texto: 'Ítem en mapa: `material`, `amount`, `name`, `lore`, `model`, `enchants`, `glow`, `unbreakable`, `hide_flags`; o en texto (`DIAMOND;2;nombre;lore`); o `saved:<nombre>`. La imagen es el inventario (tecla E) con el ratón sobre el ítem.',
    async despues (ctx) { await tener(ctx, 'diamond_sword') },
    foto: { gui: { raton: 36 } }
  }),
  accion('drop_item', JUGADOR, { regla: null, soloTexto: 'Los ítems tirados en el suelo no se dibujan en estas imágenes (el visor 3D no tiene su modelo). La suite en el juego comprueba que el ítem aparece.' }),
  accion('remove_item', JUGADOR, {
    espera: null, recorte: 'hud', texto: '`MATERIAL;cantidad`, por material aunque el ítem tenga nombre. Ana tenía 8 diamantes; le quedan 3.',
    async antes (ctx) { await consola(ctx, `give ${ANA} diamond 8`); await tener(ctx, 'diamond', 8) },
    async despues (ctx) { await bot(ctx).hasta('3 diamantes', () => bot(ctx).cantidad('diamond') === 3) }
  }),
  accion('clear_inventory', JUGADOR, {
    espera: ['chat', /vacío/], recorte: ['chat', 'hud'],
    async antes (ctx) { await consola(ctx, `give ${ANA} diamond 8`, `give ${ANA} bread 16`); await tener(ctx, 'bread', 16) },
    async despues (ctx) { await bot(ctx).hasta('inventario vacío', () => bot(ctx).items().length === 0) }
  }),
  accion('close_inventory', JUGADOR, {
    espera: ['chat', /cerró/], texto: 'Ana tenía un cofre abierto; la acción lo cierra.' + COFRE,
    async antes (ctx) {
      await consola(ctx, `setblock 12 ${Y} 13 chest[facing=north]`)
      await bot(ctx).hasta('ver el cofre', () => bloqueEn(ctx, 12, Y, 13)?.name === 'chest')
      await b(ctx).openContainer(bloqueEn(ctx, 12, Y, 13))
    },
    async despues (ctx) { await bot(ctx).hasta('cofre cerrado', bb => !bb.currentWindow) }
  }),
  accion('give_exp', JUGADOR, { espera: null, recorte: 'hud', texto: '12 puntos de experiencia: nivel 1 y parte de la barra.', async despues (ctx) { await bot(ctx).hasta('nivel 1', bb => bb.experience.level === 1) } }),
  accion('give_level', JUGADOR, { espera: null, recorte: 'hud', async despues (ctx) { await bot(ctx).hasta('nivel 5', bb => bb.experience.level === 5) } }),
  {
    id: 'accion-kick', grupo: JUGADOR, titulo: '`kick`', regla: 'cap_kick', cubre: ['action:kick'],
    texto: 'La pantalla que ve el jugador expulsado, con el motivo en MiniMessage.',
    async fn (ctx) {
      await cap(ctx, 'kick', 'kicked', /expulsó/)
      await ctx.foto({ desconexion: { titulo: { translate: 'disconnect.lost' }, motivo: motivoDeExpulsion(ctx, ANA) } })
    }
  },

  // ───────────── Acciones · Mundo ─────────────
  accion('set_block', MUNDO, {
    espera: null, texto: '`mundo;x;y;z;MATERIAL` (o solo el material, en un disparador con bloque).',
    async despues (ctx) { await bot(ctx).hasta('ver los 3 bloques', () => bloqueEn(ctx, 13, Y, 14)?.name === 'emerald_block') }
  }),
  accion('summon', MUNDO, {
    espera: null, varia: true, texto: '`TIPO;mundo;x;y;z`. Varía: las criaturas se mueven.',
    async despues (ctx) { await verEntidad(ctx, 'cow'); await verEntidad(ctx, 'sheep'); await dormir(300) }
  }),
  accion('explosion', MUNDO, {
    espera: null, texto: '`potencia;fuego;romper`. Por defecto no rompe bloques; aquí `romper` está en `true` y la explosión es en la posición de Beto (`to: "player:Beto"`, que está en creativo).',
    async antes (ctx) { await prepararJugador(ctx, ANA, { modo: 'creative', pitch: 25 }) },
    async despues (ctx) { await bot(ctx).hasta('ver el cráter', () => bloqueEn(ctx, 12, Y - 1, 21)?.name === 'air') }
  }),
  accion('lightning', MUNDO, { regla: null, soloTexto: 'El visor 3D no dibuja relámpagos. El jugador ve el rayo y oye el trueno; el relámpago de OmniEvents es solo visual (no hace daño ni prende fuego).' }),
  accion('particle', MUNDO, { regla: null, soloTexto: PARTICULAS }),

  // ───────────── Acciones · Evento ─────────────
  {
    id: 'accion-set_damage', grupo: EVENTO, titulo: '`set_damage`', regla: 'cap_golpe_letal', cubre: ['action:set_damage'],
    texto: '`10` fija el daño; `150%` lo multiplica. Un puñetazo de Ana con `set_damage: 1000` deja fuera de combate a la vaca.',
    async fn (ctx) {
      await encender(ctx, 'cap_golpe_letal')
      await consola(ctx, `summon cow 12.5 ${Y} 13.5 {${NPC}}`)
      await verEntidad(ctx, 'cow')
      await atacarHasta(ctx, 'cow', () => false)
      await esperar(ctx, 'actionbar', /letal/)
      await sinRestos(ctx)
      await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`)
      await dormir(500)
      await ctx.foto()
    }
  },
  {
    id: 'accion-set_message', grupo: EVENTO, titulo: '`set_message`', regla: 'cap_megafono', cubre: ['action:set_message'], camara: BETO, recorte: 'chat',
    texto: 'Lo que ve Beto cuando Ana escribe «!La subasta empieza en 5 minutos»: el mensaje cambiado. El texto del jugador nunca se interpreta como MiniMessage.',
    async fn (ctx) {
      await encender(ctx, 'cap_megafono')
      bot(ctx).chat('!La subasta empieza en 5 minutos')
      await esperar(ctx, 'chat', /megáfono/, { camara: BETO })
      await ctx.foto({ camara: BETO })
    }
  },
  {
    id: 'accion-set_quit_message', grupo: EVENTO, titulo: '`set_quit_message` · `player_quit`', regla: 'despedida', camara: BETO, recorte: 'chat',
    cubre: ['action:set_quit_message', 'trigger:player_quit', 'example:despedida'],
    texto: 'El ejemplo `despedida`: lo que ve Beto cuando Ana sale.',
    async fn (ctx) {
      await ctx.salir(ANA)
      await esperar(ctx, 'chat', /\[-\] Ana/, { camara: BETO })
      await ctx.foto({ camara: BETO })
    }
  },
  {
    id: 'accion-set_death_message', grupo: EVENTO, titulo: '`set_death_message` · `player_death`', regla: 'cap_muerte', camara: BETO, recorte: 'chat',
    cubre: ['action:set_death_message', 'trigger:player_death'], texto: 'Lo que ve Beto cuando muere Ana (`none` oculta el mensaje).',
    async fn (ctx) {
      await encender(ctx, 'cap_muerte')
      await consola(ctx, `kill ${ANA}`)
      await esperar(ctx, 'chat', /vacío/, { camara: BETO })
      await ctx.foto({ camara: BETO })
    }
  },
  {
    id: 'accion-keep_inventory', grupo: EVENTO, titulo: '`keep_inventory`', regla: 'cap_conservar', cubre: ['action:keep_inventory'],
    texto: 'Ana muere con espada, pan y nivel 8 y reaparece con todo.',
    async fn (ctx) {
      await encender(ctx, 'cap_conservar')
      await consola(ctx, `give ${ANA} iron_sword`, `give ${ANA} bread 12`, `xp set ${ANA} 8 levels`)
      await tener(ctx, 'bread', 12)
      await consola(ctx, `kill ${ANA}`)
      await esperar(ctx, 'chat', /Conservaste/)
      await bot(ctx).hasta('reaparecer con el pan', bb => bb.health > 0 && bot(ctx).cantidad('bread') === 12, { timeoutMs: 10000 })
      await trasReaparecer(ctx)
      await ctx.foto()
    }
  },
  {
    id: 'accion-cancel_drops', grupo: EVENTO, titulo: '`cancel_drops` · `entity_death`', regla: 'cap_sin_botin', recorte: ['barra', 'hud'],
    cubre: ['action:cancel_drops'], texto: 'La gallina muere sin soltar nada. (Los ítems tirados no se dibujan en estas imágenes: lo que muestra es el aviso de la regla.)',
    async fn (ctx) {
      await encender(ctx, 'cap_sin_botin')
      await consola(ctx, `summon chicken 12.5 ${Y} 12.5 {${NPC},Health:1f}`)
      await verEntidad(ctx, 'chicken')
      await atacarHasta(ctx, 'chicken', () => false)
      await esperar(ctx, 'actionbar', /no soltó/)
      await sinRestos(ctx)
      await ctx.foto()
    }
  },
  {
    id: 'accion-deny_join', grupo: EVENTO, titulo: '`deny_join` · `player_pre_login`', regla: 'cap_portero',
    cubre: ['action:deny_join', 'trigger:player_pre_login'],
    texto: 'Antes de entrar (evento asíncrono, con `name`, `uuid` e `ip`): la pantalla que ve quien intenta conectarse.',
    async fn (ctx) {
      await encender(ctx, 'cap_portero')
      const motivo = await intentarEntrar(ctx, 'Intruso')
      await ctx.foto({ desconexion: { titulo: { translate: 'connect.failed' }, motivo } })
    }
  },

  // ───────────── Acciones · Control de flujo ─────────────
  accion('if', FLUJO, { recorte: 'chat', texto: '`if` / `then` / `else` dentro de las acciones. Ana tiene nivel 0.' }),
  accion('repeat', FLUJO, { recorte: 'chat', espera: ['chat', /Ya!/], texto: '`repeat: n` con `var.i` (0, 1, 2…).' }),
  accion('while', FLUJO, { recorte: 'chat', espera: ['chat', /64/], texto: '`while` con tope de vueltas para que un error de configuración no congele el servidor.' }),
  accion('for_each', FLUJO, { recorte: 'chat', espera: ['chat', /Beto/], texto: 'Sobre jugadores o entidades (`all`, `world:<w>`, `radius:<n>`, una expresión…). Con `as`, cada uno queda en esa variable y el jugador de la regla sigue siendo el mismo; sin `as`, cada uno pasa a ser `player` (y el original, `source`).' }),
  accion('random', FLUJO, { recorte: 'chat', varia: true, texto: 'Ramas con `weight`. Varía: sale una rama al azar.' }),
  accion('delay', FLUJO, { recorte: 'chat', espera: ['chat', /esmeraldas/], texto: 'Pausa solo esa ejecución; el servidor sigue.' }),
  accion('wait_ticks', FLUJO, { recorte: 'chat', espera: ['chat', /Fuera/], texto: 'Como `delay`, en ticks (20 por segundo).' }),
  accion('stop', FLUJO, { recorte: 'chat', espera: ['chat', /pase del evento/], texto: 'Corta la ejecución: «Adelante.» nunca llega (Ana tiene nivel 0).', async despues () { await dormir(500) } }),
  accion('set', FLUJO, {
    recorte: 'chat', espera: ['chat', /visitas/], texto: '`var.` (de la ejecución), `pvar.` (del jugador) y `gvar.` (del servidor), con `=`, `+=`, `-=`, `*=`.',
    async antes (ctx) { await consola(ctx, `omni var del ${ANA} visitas`, 'omni var del global visitas_totales') }
  }),
  accion('unset', FLUJO, {
    recorte: 'chat', espera: ['chat', /borradas/], regla: ['cap_set', 'cap_unset'],
    async antes (ctx) {
      await consola(ctx, `omni var del ${ANA} visitas`, 'omni var del global visitas_totales')
      await cap(ctx, 'set', 'chat', /visitas/)
    }
  }),
  accion('no_cooldown', FLUJO, {
    recorte: 'chat', espera: null, texto: 'Ana no tiene nivel 30: el intento no gasta el enfriamiento de 1 hora y puede volver a probar.',
    async antes (ctx) { await consola(ctx, `omni cooldown reset cap_no_cooldown ${ANA}`) },
    async despues (ctx) {
      await esperar(ctx, 'chat', /no gasta/)
      await dormir(300)
      await cap(ctx, 'no_cooldown', null)
      await bot(ctx).hasta('dos intentos', () => bot(ctx).vistos.slice(ctx.marcas[ANA]).filter(x => /no gasta/.test(x.texto)).length === 2)
    }
  }),
  {
    id: 'accion-reset_cooldown', grupo: FLUJO, titulo: '`reset_cooldown` · `cooldown` · `on_cooldown`', regla: ['cap_cofre', 'cap_reset_cooldown'], recorte: 'chat',
    cubre: ['action:reset_cooldown'], texto: 'El cofre diario tiene `cooldown: 1h`: el segundo intento cae en `on_cooldown`; `reset_cooldown` lo libera.',
    async fn (ctx) {
      await consola(ctx, `omni cooldown reset cap_cofre ${ANA}`)
      for (const [n, re] of [['cofre', /Abriste/], ['cofre', /vuelve en/], ['reset_cooldown', /disponible/], ['cofre', /Abriste/]]) {
        const m = bot(ctx).marca()
        bot(ctx).comando('cap ' + n)
        await bot(ctx).esperar('chat', re, { desde: m })
      }
      await ctx.foto()
    }
  },
  {
    id: 'opcion-limit', grupo: OPCIONES, titulo: '`limit` · `on_limit`', regla: 'cap_premio_unico', recorte: 'chat', cubre: ['option:on_limit'],
    texto: 'Usos máximos por jugador (o globales) con reinicio `never`, `daily`, `weekly`, `monthly` o una duración.',
    async fn (ctx) {
      await consola(ctx, `omni limit reset cap_premio_unico ${ANA}`)
      for (const re of [/Reclamaste/, /Ya reclamaste/]) {
        const m = bot(ctx).marca()
        bot(ctx).comando('cap premio_unico')
        await bot(ctx).esperar('chat', re, { desde: m })
      }
      await ctx.foto()
    }
  },

  // ───────────── Disparadores · Jugador ─────────────
  disparador('player_respawn', D_JUGADOR, {
    recorte: 'chat', espera: ['chat', /Reapareciste/], estimulo: (ctx) => consola(ctx, `kill ${ANA}`),
    async despues (ctx) { await bot(ctx).hasta('estar viva', bb => bb.health > 0); await trasReaparecer(ctx) }
  }),
  disparador('player_command', D_JUGADOR, {
    recorte: 'chat', espera: ['chat', /trampas/], texto: 'Intercepta un comando escrito (aquí `/reglas`, que no existe) y lo cancela.',
    estimulo: async (ctx) => { bot(ctx).comando('reglas') }
  }),
  disparador('player_move', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Caminas/], varia: true, texto: '`move_mode: block` (por defecto) solo dispara al cambiar de bloque; también `chunk` o `any`. Varía: dónde termina el paso.',
    estimulo: async (ctx) => { b(ctx).setControlState('forward', true); await dormir(400); b(ctx).setControlState('forward', false) }
  }),
  disparador('player_teleport', D_JUGADOR, {
    recorte: 'chat', espera: ['chat', /Teletransporte/], estimulo: (ctx) => consola(ctx, `tp ${ANA} 12.5 ${Y} 14.5 180 20`)
  }),
  disparador('player_world_change', D_JUGADOR, {
    recorte: 'chat', espera: ['chat', /desde world_nether/], timeoutMs: 40000,
    texto: 'Ana va al Nether y vuelve (por consola).',
    estimulo: async (ctx) => {
      await consola(ctx, `execute in minecraft:the_nether run tp ${ANA} 0.5 128 0.5`)
      await esperar(ctx, 'chat', /world_nether/, { timeoutMs: 15000 })
      await dormir(1500)
      await consola(ctx, `execute in minecraft:overworld run tp ${ANA} 12.5 ${Y} 10.5 0 20`)
    },
    async despues (ctx) {
      await bot(ctx).hasta('volver a la plataforma', bb => Math.abs(bb.entity.position.y - Y) < 0.6, { timeoutMs: 15000 })
      await ctx.camara(ANA).recargar(b(ctx))
      await dormir(2500)
    }
  }),
  disparador('player_sneak', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /sigilo/],
    // mineflayer 4.39 manda agacharse en 1.21.4 con un número donde el protocolo espera el nombre de la acción:
    // se manda el paquete entity_action con 'start_sneaking'.
    estimulo: async (ctx) => { b(ctx)._client.write('entity_action', { entityId: b(ctx).entity.id, actionId: 'start_sneaking', jumpBoost: 0 }) },
    async despues (ctx) { await dormir(300); b(ctx)._client.write('entity_action', { entityId: b(ctx).entity.id, actionId: 'stop_sneaking', jumpBoost: 0 }) }
  }),
  disparador('player_sprint', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /velocidad/], varia: true, texto: 'Varía: la foto se toma corriendo.',
    estimulo: async (ctx) => { b(ctx).setControlState('forward', true); b(ctx).setControlState('sprint', true) },
    async despues (ctx) { b(ctx).clearControlStates() }
  }),
  disparador('player_jump', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Salto/],
    estimulo: async (ctx) => { b(ctx).setControlState('jump', true); await dormir(200); b(ctx).setControlState('jump', false) },
    async despues (ctx) { await dormir(800) }
  }),
  disparador('player_level_change', D_JUGADOR, {
    espera: ['subtitle', /Subiste/], estimulo: (ctx) => consola(ctx, `xp add ${ANA} 3 levels`), async despues () { await dormir(500) }
  }),
  disparador('player_gamemode_change', D_JUGADOR, {
    recorte: ['chat', 'hud'], espera: ['chat', /Modo de juego/], estimulo: (ctx) => consola(ctx, `gamemode creative ${ANA}`)
  }),
  disparador('player_toggle_flight', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Vuelo/],
    antes: (ctx) => prepararJugador(ctx, ANA, { modo: 'creative' }),
    estimulo: async (ctx) => { b(ctx)._client.write('abilities', { flags: 2 }) }
  }),
  disparador('player_bed_enter', D_JUGADOR, {
    recorte: 'chat', espera: ['chat', /Buenas noches/], timeoutMs: 20000, texto: 'Con `result` (`OK`, `NOT_POSSIBLE_NOW`…). La escena pone una cama y hace de noche.' + COFRE,
    antes: (ctx) => consola(ctx, `setblock 12 ${Y} 13 red_bed[facing=south,part=foot]`, `setblock 12 ${Y} 14 red_bed[facing=south,part=head]`, 'time set 18000'),
    estimulo: async (ctx) => {
      await bot(ctx).hasta('ver la cama', () => bloqueEn(ctx, 12, Y, 13)?.name === 'red_bed')
      await b(ctx).sleep(bloqueEn(ctx, 12, Y, 13)).catch(() => {})
    },
    async despues (ctx) {
      await b(ctx).wake().catch(() => {})
      await consola(ctx, 'time set 6000', `tp ${ANA} 12.5 ${Y} 10.5 0 20`)
      await dormir(800)
    }
  }),
  disparador('player_fish', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /caña/], texto: 'Con `state` (`FISHING`, `CAUGHT_FISH`…) y `target` (lo que se pescó).',
    antes: (ctx) => consola(ctx, `fill 11 ${Y - 1} 13 13 ${Y - 1} 16 water`, `give ${ANA} fishing_rod`),
    estimulo: async (ctx) => {
      await enMano(ctx, 'fishing_rod')
      await b(ctx).lookAt(v(12.5, Y - 0.5, 15), true)
      b(ctx).activateItem()
    },
    async despues (ctx) { await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`); await dormir(600) }
  }),
  disparador('player_regain_health', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /de vida/], texto: 'Con `amount` y `reason`. Ana tenía 12 de vida y recibe Curación instantánea.',
    antes: async (ctx) => { await consola(ctx, `damage ${ANA} 8 minecraft:generic`); await bot(ctx).hasta('12 de vida', bb => bb.health === 12) },
    estimulo: (ctx) => consola(ctx, `effect give ${ANA} instant_health 1 0`),
    async despues (ctx) { await bot(ctx).hasta('16 de vida', bb => bb.health === 16) }
  }),
  disparador('player_food_change', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Hambre/], varia: true, texto: 'Con `old_food` y `new_food`. La escena le da Hambre a Ana por consola. Varía: el hambre baja mientras se toma la foto.',
    estimulo: (ctx) => consola(ctx, `effect give ${ANA} hunger 20 120`)
  }),
  disparador('player_advancement', D_JUGADOR, {
    recorte: 'chat', espera: ['chat', /logro/], texto: 'Con `advancement` (los de recetas no cuentan). Arriba, el anuncio del propio juego.',
    antes: (ctx) => consola(ctx, 'gamerule announceAdvancements true'),
    estimulo: (ctx) => consola(ctx, `advancement revoke ${ANA} only minecraft:story/mine_stone`, `advancement grant ${ANA} only minecraft:story/mine_stone`),
    async despues (ctx) { await dormir(300); await consola(ctx, 'gamerule announceAdvancements false') }
  }),
  disparador('player_swap_hands', D_JUGADOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Mano izquierda/], texto: 'Tecla F: con `item` y `offhand`.',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} torch 16`); await enMano(ctx, 'torch') },
    estimulo: async (ctx) => {
      b(ctx)._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 })
    },
    async despues (ctx) { await bot(ctx).hasta('antorcha en la mano izquierda', bb => bb.inventory.slots[45]?.name === 'torch') }
  }),
  disparador('player_armor_change', D_JUGADOR, {
    recorte: ['chat', 'hud'], espera: ['chat', /equipaste/], texto: 'Con `item`, `old_item` y `slot`. Sobre la vida, la barra de armadura.',
    estimulo: (ctx) => consola(ctx, `item replace entity ${ANA} armor.chest with minecraft:diamond_chestplate`),
    async despues () { await dormir(500) }
  }),
  disparador('player_portal', D_JUGADOR, {
    espera: ['chat', /Nether está cerrado/], timeoutMs: 30000, texto: 'Con `from`, `to` y `cause`. La regla lo cancela: Ana no viaja.',
    antes: async (ctx) => {
      await prepararJugador(ctx, ANA, { modo: 'creative' })
      await consola(ctx, `fill 11 ${Y} 14 14 ${Y + 4} 14 obsidian`, `fill 12 ${Y + 1} 14 13 ${Y + 3} 14 nether_portal[axis=x]`)
    },
    estimulo: (ctx) => consola(ctx, `tp ${ANA} 12.5 ${Y + 1} 14.5 0 0`),
    async despues (ctx) { await consola(ctx, `tp ${ANA} 12.5 ${Y} 9.5 0 0`); await dormir(1000) }
  }),
  disparador('player_kick', D_JUGADOR, {
    recorte: 'chat', espera: ['chat', /expulsado/], texto: 'Con `reason`. Lo que ve Ana cuando expulsan a Beto.',
    estimulo: (ctx) => consola(ctx, `kick ${BETO} Lenguaje inapropiado`),
    async despues (ctx) { await ctx.entrar(BETO); await dormir(1500) }
  }),
  disparador('player_resource_pack', D_JUGADOR, {
    soloTexto: 'Dispara cuando el jugador acepta, rechaza o termina de bajar un paquete de recursos (`status`). El servidor de prueba no manda ninguno y OmniEvents no tiene una acción para mandarlo, así que no se puede provocar.'
  }),

  // ───────────── Disparadores · Combate ─────────────
  disparador('player_damage', D_COMBATE, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /de vida/], texto: 'Con `cause`, `damage` y `damager` (quien pegó, también tras un proyectil).',
    estimulo: (ctx) => consola(ctx, `damage ${ANA} 5 minecraft:cactus`), async despues (ctx) { await bot(ctx).hasta('15 de vida', bb => bb.health === 15) }
  }),
  disparador('player_attack', D_COMBATE, {
    espera: ['actionbar', /Golpeaste/], texto: 'Con `target`, `damage` y `projectile`.',
    antes: async (ctx) => { await consola(ctx, `summon cow 12.5 ${Y} 13.5 {${NPC},attributes:[{id:"minecraft:max_health",base:100.0}],Health:100f}`); await verEntidad(ctx, 'cow') },
    estimulo: (ctx) => atacarHasta(ctx, 'cow', () => false),
    async despues (ctx) { await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`); await dormir(600) }
  }),
  disparador('player_kill', D_COMBATE, {
    recorte: 'chat', espera: ['chat', /Cazaste/], texto: 'Cualquier criatura que mate el jugador (`target`). Aquí, una gallina.',
    antes: async (ctx) => { await consola(ctx, `summon chicken 12.5 ${Y} 12.5 {${NPC},Health:1f}`); await verEntidad(ctx, 'chicken') },
    estimulo: (ctx) => atacarHasta(ctx, 'chicken', () => false)
  }),
  disparador('player_shoot', D_COMBATE, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Disparo/], varia: true, texto: 'Con `item` (el arco), `projectile` y `force`. Varía: la flecha sale en vuelo.',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} bow`, `give ${ANA} arrow 8`); await enMano(ctx, 'bow') },
    estimulo: async (ctx) => { await b(ctx).look(0, 0.15, true); b(ctx).activateItem(); await dormir(1300); b(ctx).deactivateItem() }
  }),
  disparador('projectile_hit', D_COMBATE, {
    recorte: 'chat', espera: ['chat', /flecha pegó/], texto: 'Con `target` (entidad) o `block`, y `projectile`.',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} bow`, `give ${ANA} arrow 8`); await enMano(ctx, 'bow') },
    estimulo: async (ctx) => { await b(ctx).look(0, -0.35, true); b(ctx).activateItem(); await dormir(1300); b(ctx).deactivateItem() },
    async despues (ctx) { await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`) }
  }),

  // ───────────── Disparadores · Bloques ─────────────
  disparador('block_place', D_BLOQUES, {
    espera: ['actionbar', /Pusiste/], texto: 'Con `block`, `against` e `item`.',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} oak_planks 16`); await enMano(ctx, 'oak_planks') },
    estimulo: (ctx) => b(ctx).placeBlock(bloqueEn(ctx, 12, Y - 1, 13), v(0, 1, 0)).catch(() => {}),
    async despues (ctx) { await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`); await dormir(600) }
  }),
  disparador('block_interact', D_BLOQUES, {
    recorte: 'chat', espera: ['chat', /palanca/], texto: 'Con `action` (`RIGHT_CLICK`, `LEFT_CLICK`, `SHIFT_RIGHT_CLICK`…), `block`, `face` e `item`.',
    antes: async (ctx) => { await consola(ctx, `setblock 12 ${Y} 13 lever[face=floor]`); await bot(ctx).hasta('ver la palanca', () => bloqueEn(ctx, 12, Y, 13)?.name === 'lever') },
    estimulo: (ctx) => b(ctx).activateBlock(bloqueEn(ctx, 12, Y, 13))
  }),
  disparador('sign_change', D_BLOQUES, {
    recorte: 'chat', espera: ['chat', /Cartel escrito/], timeoutMs: 20000, texto: 'Con `lines` (lo que escribió el jugador, como texto literal).' + COFRE,
    antes: async (ctx) => { await consola(ctx, `give ${ANA} oak_sign`); await enMano(ctx, 'oak_sign') },
    estimulo: async (ctx) => {
      await b(ctx).placeBlock(bloqueEn(ctx, 12, Y - 1, 13), v(0, 1, 0)).catch(() => {})
      await bot(ctx).hasta('ver el cartel', () => /sign/.test(bloqueEn(ctx, 12, Y, 13)?.name ?? ''))
      await dormir(300)
      b(ctx).updateSign(bloqueEn(ctx, 12, Y, 13), 'Bienvenidos\nal evento\nde otoño')
    }
  }),
  disparador('bucket_fill', D_BLOQUES, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Llenaste/], texto: 'Con `block` e `item` (el balde lleno).',
    antes: async (ctx) => { await consola(ctx, `setblock 12 ${Y - 1} 13 water`, `give ${ANA} bucket`); await enMano(ctx, 'bucket') },
    estimulo: async (ctx) => { await b(ctx).lookAt(v(12.5, Y - 0.1, 13.5), true); b(ctx).activateItem() }
  }),
  disparador('bucket_empty', D_BLOQUES, {
    espera: ['actionbar', /Vaciaste/], varia: true, texto: 'Con `block` y `bucket`. Varía: el agua se mueve.',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} water_bucket`); await enMano(ctx, 'water_bucket') },
    estimulo: async (ctx) => { await b(ctx).lookAt(v(12.5, Y, 13.5), true); b(ctx).activateItem() },
    async despues (ctx) { await consola(ctx, `tp ${ANA} 12.5 ${Y} 10.5 0 20`); await dormir(2000) }
  }),

  // ───────────── Disparadores · Ítems ─────────────
  disparador('player_interact', D_ITEMS, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Clic derecho/], texto: 'Cualquier clic (`action`), con o sin bloque (`block`) e ítem (`item`).',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} stick`); await enMano(ctx, 'stick') },
    estimulo: async (ctx) => { await b(ctx).look(0, 0.8, true); b(ctx).activateItem() },
    async despues (ctx) { await b(ctx).look(0, -0.35, true) }
  }),
  disparador('item_interact', D_ITEMS, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /vara de fuego/], texto: 'Como `player_interact`, pero solo con un ítem en la mano.',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} blaze_rod`); await enMano(ctx, 'blaze_rod') },
    estimulo: async (ctx) => { await b(ctx).look(0, 0.8, true); b(ctx).activateItem() },
    async despues (ctx) { await b(ctx).look(0, -0.35, true) }
  }),
  disparador('item_consume', D_ITEMS, {
    recorte: ['chat', 'hud'], espera: ['chat', /Comiste/], texto: 'Comida y pociones (`item`).',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} golden_apple`); await enMano(ctx, 'golden_apple') },
    estimulo: (ctx) => b(ctx).consume().catch(() => {})
  }),
  disparador('item_craft', D_ITEMS, {
    recorte: ['chat', 'hud'], espera: ['chat', /Fabricaste/], texto: 'Con `item` (el resultado) y `click`. Aquí, tablones en la cuadrícula del inventario.',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} oak_log`); await tener(ctx, 'oak_log') },
    estimulo: async (ctx) => {
      const tablones = b(ctx).registry.itemsByName.oak_planks.id
      const receta = b(ctx).recipesFor(tablones, null, 1, null)[0]
      await b(ctx).craft(receta, 1, null)
    }
  }),
  disparador('item_drop', D_ITEMS, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Tiraste/], texto: 'Con `item` y `entity` (el ítem en el suelo).',
    antes: async (ctx) => { await consola(ctx, `give ${ANA} diamond 3`); await tener(ctx, 'diamond', 3) },
    estimulo: (ctx) => b(ctx).toss(b(ctx).registry.itemsByName.diamond.id, null, 1)
  }),
  disparador('item_pickup', D_ITEMS, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /EMERALD/], texto: 'Con `item` y `entity`. La escena suelta 3 esmeraldas a los pies de Ana.',
    estimulo: (ctx) => consola(ctx, `summon item 12.5 ${Y} 10.5 {Item:{id:"minecraft:emerald",count:3},PickupDelay:0}`),
    async despues (ctx) { await tener(ctx, 'emerald', 3) }
  }),
  disparador('item_held', D_ITEMS, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Ranura 4/], texto: 'Cambiar de ranura en la barra: con `item`, `previous_item` y `slot`.',
    antes: async (ctx) => {
      await consola(ctx, `item replace entity ${ANA} hotbar.0 with minecraft:bread 6`, `item replace entity ${ANA} hotbar.3 with minecraft:diamond_sword`)
      await tener(ctx, 'diamond_sword')
    },
    estimulo: async (ctx) => { b(ctx).setQuickBarSlot(3) }
  }),
  {
    id: 'disparador-item_enchant', grupo: D_ITEMS, titulo: '`item_enchant`', regla: 'cap_t_item_enchant', cubre: ['trigger:item_enchant'],
    texto: 'Con `item`, `enchants` y `cost`.',
    soloTexto: 'La escena no puede encantar con el bot: mineflayer 4.39 no logra poner el ítem ni el lapislázuli en la mesa de encantamientos en 1.21.4 (la ventana queda vacía). Con un jugador real, esta regla escribe en su chat «Encantaste IRON_SWORD por 1 niveles».'
  },
  disparador('item_break', D_ITEMS, {
    recorte: 'chat', espera: ['chat', /Se rompió/], texto: 'Con `item` (el que se rompió). Un pico de madera a un uso de romperse pica una piedra.',
    antes: async (ctx) => {
      await consola(ctx, `item replace entity ${ANA} weapon.mainhand with minecraft:wooden_pickaxe[damage=58]`, `setblock 12 ${Y} 13 stone`)
      await tener(ctx, 'wooden_pickaxe')
      await bot(ctx).hasta('ver la piedra', () => bloqueEn(ctx, 12, Y, 13)?.name === 'stone')
    },
    estimulo: (ctx) => b(ctx).dig(bloqueEn(ctx, 12, Y, 13), true)
  }),

  // ───────────── Disparadores · Inventario ─────────────
  disparador('inventory_open', D_INVENTARIO, {
    espera: ['chat', /Abriste un cofre/], texto: 'Con `title` e `inventory_type`. La foto es después de cerrar el cofre: con un inventario abierto el chat no se ve.' + COFRE,
    antes: (ctx) => ponerCofre(ctx),
    estimulo: async (ctx) => { await b(ctx).openContainer(bloqueEn(ctx, 12, Y, 13)) },
    async despues (ctx) { b(ctx).closeWindow(b(ctx).currentWindow); await dormir(500) }
  }),
  {
    id: 'disparador-inventory_click', grupo: D_INVENTARIO, titulo: '`inventory_click`', regla: 'cap_t_inventory_click', recorte: 'gui',
    cubre: ['trigger:inventory_click'],
    texto: 'Con `item`, `cursor`, `slot`, `raw_slot`, `click`, `title` e `inventory_type`. Arriba, el cofre con el ratón sobre las esmeraldas; abajo, el chat después de hacer clic y cerrar.',
    async fn (ctx) {
      await ponerCofre(ctx)
      await encender(ctx, 'cap_t_inventory_click')
      const cofre = await b(ctx).openContainer(bloqueEn(ctx, 12, Y, 13))
      await dormir(400)
      const gui = await ctx.foto({ gui: { raton: 13 } })
      await b(ctx).clickWindow(13, 0, 0)
      await esperar(ctx, 'chat', /Clic/)
      await b(ctx).clickWindow(13, 0, 0)
      b(ctx).closeWindow(cofre)
      await dormir(500)
      const chat = await ctx.foto({ recorte: 'chat' })
      ctx.ultimaFoto = { lienzo: ctx.apilar(gui.lienzo, chat.lienzo), alt: gui.alt + '. ' + chat.alt }
    }
  },
  disparador('inventory_close', D_INVENTARIO, {
    recorte: 'chat', espera: ['chat', /Cerraste el cofre/], texto: 'Con `title` e `inventory_type`.' + COFRE,
    antes: async (ctx) => { await ponerCofre(ctx); await b(ctx).openContainer(bloqueEn(ctx, 12, Y, 13)) },
    estimulo: async (ctx) => { b(ctx).closeWindow(b(ctx).currentWindow) }
  }),

  // ───────────── Disparadores · Entidades ─────────────
  disparador('entity_interact', D_ENTIDADES, {
    espera: ['chat', /Acariciaste/], texto: 'Clic derecho sobre una entidad (`target`).',
    antes: async (ctx) => { await consola(ctx, `summon cow 12.5 ${Y} 13 {${NPC}}`); await verEntidad(ctx, 'cow') },
    estimulo: async (ctx) => b(ctx).activateEntity(Object.values(b(ctx).entities).find(e => e.name === 'cow'))
  }),
  disparador('entity_spawn', D_ENTIDADES, {
    espera: ['chat', /Apareció una oveja/], texto: 'Con `entity` y `reason` (`NATURAL`, `SPAWNER`, `COMMAND`…). Sin jugador: aquí la regla avisa a todos.',
    estimulo: (ctx) => consola(ctx, `summon sheep 12.5 ${Y} 13.5 {${NPC},Color:0b}`),
    async despues (ctx) { await verEntidad(ctx, 'sheep'); await dormir(300) }
  }),
  disparador('entity_death', D_ENTIDADES, {
    recorte: 'chat', espera: ['chat', /conejo/], texto: 'Con `entity` y `killer` (si lo mató un jugador).',
    antes: async (ctx) => { await consola(ctx, `summon rabbit 12.5 ${Y} 12.5 {${NPC},Health:1f,RabbitType:0}`); await verEntidad(ctx, 'rabbit') },
    estimulo: (ctx) => atacarHasta(ctx, 'rabbit', () => false)
  }),

  // ───────────── Disparadores · Servidor ─────────────
  disparador('console_command', D_SERVIDOR, {
    recorte: 'chat', espera: ['chat', /torneo/], texto: 'Un comando escrito en la consola (`cmd.name`, `cmd.args`): aquí `anuncio …`, que no existe y la regla cancela.',
    estimulo: (ctx) => consola(ctx, 'anuncio El torneo empieza a las 18:00')
  }),
  disparador('timer', D_SERVIDOR, {
    recorte: ['barra', 'hud'], espera: ['actionbar', /Evento en curso/], texto: '`every` (`30s`, `5m`…) y `per_player` (por defecto, una ejecución por jugador conectado).',
    estimulo: async () => {}
  }),
  {
    id: 'disparador-clock', grupo: D_SERVIDOR, titulo: '`clock`', regla: 'cap_t_clock', recorte: 'chat', cubre: ['trigger:clock'], timeoutMs: 150000,
    texto: '`at: ["08:00", "20:30"]` y `timezone`. La escena cambia la hora de la regla al minuto siguiente en el servidor de prueba y espera.',
    async fn (ctx) {
      const ahora = new Date(Date.now() + (new Date().getSeconds() > 45 ? 120000 : 60000))
      const hora = `${String(ahora.getHours()).padStart(2, '0')}:${String(ahora.getMinutes()).padStart(2, '0')}`
      const archivo = path.join(REGLAS_RUN, 'capturas', 'disparadores.yml')
      fs.writeFileSync(archivo, fs.readFileSync(archivo, 'utf8').replace('at: ["20:00"]', `at: ["${hora}"]`))
      await consola(ctx, 'omni reload')
      await encender(ctx, 'cap_t_clock')
      ctx.marcas[ANA] = bot(ctx).marca()
      await esperar(ctx, 'chat', /evento nocturno/, { timeoutMs: 140000 })
      await ctx.foto()
    }
  },
  {
    id: 'disparador-server_stop', grupo: D_SERVIDOR, titulo: '`server_stop`', regla: 'cap_t_server_stop', recorte: 'consola', alApagar: true,
    cubre: ['trigger:server_stop'], texto: 'Al apagar el servidor, sin jugadores conectados. **No lo ve un jugador**: la imagen es la consola.',
    async fn (ctx) {
      await ctx.foto({ consola: lineasDeConsola(ctx, /Guardando el evento/) })
    }
  }
]

/** Líneas del log del servidor que cumplen el patrón, sin la hora (que cambia en cada corrida). */
function lineasDeConsola (ctx, patron, desde = 0) {
  return ctx.srv.lineas.slice(desde).filter(l => patron.test(l)).map(l => l.replace(/^\[\d\d:\d\d:\d\d /, '[').replace(/^\[[^\]]*\]: /, ''))
}

function motivoDeExpulsion (ctx, nombre) {
  const r = bot(ctx, nombre).vistos.filter(x => x.tipo === 'kicked').at(-1)?.json
  return componente(bot(ctx, nombre).bot, r)
}

function componente (bb, r) {
  if (r == null) return ''
  if (typeof r === 'string') { try { return JSON.parse(r) } catch { return r } }
  const ChatMessage = require('prismarine-chat')(bb.registry)
  try { return ChatMessage.fromNotch(r).json } catch { return String(r) }
}

/** Un cliente que intenta entrar y es rechazado antes del login: devuelve el motivo (componente). */
async function intentarEntrar (ctx, nombre) {
  const mineflayer = require('mineflayer')
  const intento = mineflayer.createBot({ host: '127.0.0.1', port: ctx.puerto, username: nombre, auth: 'offline', version: ctx.version, hideErrors: true })
  try {
    return await new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error(`${nombre} no fue rechazado`)), 15000)
      intento.once('kicked', (r) => { clearTimeout(t); resolve(componente(intento, r)) })
      intento.once('spawn', () => { clearTimeout(t); reject(new Error(`${nombre} entró: la regla no lo rechazó`)) })
    })
  } finally {
    intento.end()
  }
}

async function ponerCofre (ctx) {
  await consola(ctx, `setblock 12 ${Y} 13 chest[facing=north]{CustomName:'"Cofre del tesoro"',Items:[{Slot:0b,id:"minecraft:diamond",count:5},` +
    `{Slot:4b,id:"minecraft:golden_apple",count:2},{Slot:13b,id:"minecraft:emerald",count:12,components:{"minecraft:custom_name":'{"text":"Esmeraldas del mercader","color":"green","italic":false}',"minecraft:lore":['{"text":"Valen 1 moneda cada una","color":"gray","italic":false}']}},` +
    `{Slot:22b,id:"minecraft:iron_sword",count:1}]}`)
  await bot(ctx).hasta('ver el cofre', () => bloqueEn(ctx, 12, Y, 13)?.name === 'chest')
}

/** Nombres de primer nivel de los YAML de una carpeta (las reglas de ejemplo). */
function reglasDe (dir) {
  return fs.readdirSync(dir).filter(f => /\.ya?ml$/.test(f)).flatMap(f =>
    fs.readFileSync(path.join(dir, f), 'utf8').split(/\r?\n/).map(l => /^([A-Za-z0-9_-]+):\s*$/.exec(l)?.[1]).filter(Boolean))
}

export default {
  jar: /^OmniEvents-.*\.jar$/,
  version: '1.21.4',
  puerto: 25599,
  puertoVisor: 3017,
  // En 1.21.4, spawn-monsters=false (el de la suite) borra al instante hasta los monstruos invocados; las criaturas
  // naturales siguen apagadas con doMobSpawning false.
  propiedades: { 'spawn-monsters': 'true' },
  protagonista: ANA,
  bots: [ANA, BETO, 'Intruso'],
  datosDeJugador: ['plugins/OmniEvents/data/players/{uuid}.yml'],
  salida: 'docs/img/omnievents',
  copiar: [
    { de: 'plugins/omnievents/src/main/resources/rules/ejemplos', a: 'plugins/OmniEvents/rules/ejemplos' },
    { de: 'tests-e2e/omnievents/capturas', a: 'plugins/OmniEvents/rules/capturas', retirar: true }
  ],
  galeria: {
    archivo: 'docs/omnievents/galeria.md',
    titulo: 'OmniEvents',
    grupos: ['Ejemplos', 'Comandos de /omni', MENSAJES, COMANDOS, JUGADOR, MUNDO, EVENTO, FLUJO, OPCIONES,
      D_JUGADOR, D_COMBATE, D_BLOQUES, D_ITEMS, D_INVENTARIO, D_ENTIDADES, D_SERVIDOR],
    reglas: ['plugins/omnievents/src/main/resources/rules/ejemplos', 'tests-e2e/omnievents/capturas', 'tests-e2e/omnievents/capturas-errores']
  },
  preparar: [
    'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
    'gamerule naturalRegeneration false', 'gamerule doImmediateRespawn true', 'gamerule sendCommandFeedback false',
    'gamerule announceAdvancements false', 'gamerule keepInventory false', 'gamerule spawnRadius 0', 'gamerule doTileDrops false',
    'gamerule doEntityDrops false', 'gamerule mobGriefing false', 'gamerule doFireTick false',
    'time set 6000', 'weather clear', 'difficulty easy', 'kill @e[type=!player]',
    `fillbiome -4 96 -4 28 110 28 minecraft:plains`, `fill -4 ${Y - 1} -4 28 ${Y - 1} 28 grass_block`,
    `fill -4 ${Y} -4 28 ${Y + 8} 28 air`, `setworldspawn 12 ${Y} 12`
  ],
  limpiar: ['deop Ana', 'deop Beto', 'kill @e[type=!player]', `fill -4 ${Y - 1} -4 28 ${Y + 8} 28 air`, 'setworldspawn 12 -60 12',
    'gamerule doTileDrops true', 'gamerule doEntityDrops true', 'gamerule mobGriefing true', 'gamerule doFireTick true'],

  /** Entra Beto (observador) y después Ana, que vive su primera vez (ejemplo bienvenida). */
  async antes (ctx) {
    ctx.encendidas = []
    ctx.puerto = 25599
    await ctx.entrar(BETO)
    await consola(ctx, `op ${BETO}`, `gamemode creative ${BETO}`, `tp ${BETO} 12.5 ${Y} 21.5 180 15`)
    ctx.marcaBetoAntesDeAna = bot(ctx, BETO).marca()
    ctx.marcaEntradaAna = 0
    await ctx.entrar(ANA)
    await consola(ctx, `op ${ANA}`)
    // Que termine la bienvenida (título a los 2 s, recompensa a los 5 s) antes de la primera escena, con o sin --solo.
    ctx.tituloAna = (await bot(ctx).esperar('title', /Bienvenido/, { timeoutMs: 15000 })).t
    await bot(ctx).esperar('chat', /Recompensa diaria/, { timeoutMs: 15000 })
    await dormir(4000)
  },

  async antesDeCada (ctx, e) {
    if (e.sinPreparar) return
    for (const n of [BETO, ANA]) {
      if (!ctx.bots[n] || ctx.bots[n].expulsion != null || ctx.bots[n].bot._client.socket?.destroyed) {
        await ctx.entrar(n)
        await consola(ctx, `op ${n}`)
        await dormir(n === ANA ? 7500 : 1500) // que pasen el saludo y el título de bienvenida
      }
    }
    if (b(ctx).currentWindow) b(ctx).closeWindow(b(ctx).currentWindow)
    b(ctx).clearControlStates()
    await consola(ctx, 'kill @e[type=!player]', `fill -4 ${Y - 1} -4 28 ${Y - 1} 28 grass_block`, `fill -4 ${Y} -4 28 ${Y + 8} 28 air`,
      'time set 6000', 'weather clear')
    await prepararJugador(ctx, BETO, { modo: 'creative', z: 21.5, yaw: 180, pitch: 15 })
    await prepararJugador(ctx, ANA)
    // Una bossbar de la escena anterior seguiría en pantalla.
    await bot(ctx).hasta('sin bossbars', bb => Object.keys(bb.bossBars ?? {}).length === 0, { timeoutMs: 12000 })
    await dormir(300)
  },

  async despuesDeCada (ctx, e) {
    for (const r of ctx.encendidas.splice(0)) await ctx.srv.consola(`omni disable ${r}`)
    await e.limpiar?.(ctx)
    if (ctx.bots[ANA]?.bot?.entity) b(ctx).clearControlStates()
    await ctx.srv.sincronizar()
  },

  /** H3 CA3: lo que el servidor vivo dice que existe (disparadores, acciones, subcomandos) y las reglas de ejemplo. */
  async cobertura (ctx) {
    const leer = async (cmd, cabecera) => {
      const desde = ctx.srv.marca()
      await ctx.srv.consola(cmd, { espera: cabecera })
      await ctx.srv.sincronizar()
      return ctx.srv.lineas.slice(desde)
    }
    const sinPrefijo = (l) => l.replace(/^\[[^\]]*\]:?\s*/, '')
    const triggers = (await leer('omni triggers', /Disparadores \(\d+\)/)).map(sinPrefijo).filter(l => /^[a-zá-ú ]+: /i.test(l))
      .flatMap(l => l.slice(l.indexOf(':') + 1).split(',').map(s => s.trim())).filter(s => /^[a-z_]+$/.test(s))
    const acciones = (await leer('omni actions', /Acciones:/)).map(sinPrefijo).filter(l => l.startsWith('Acciones:'))
      .flatMap(l => l.slice('Acciones:'.length).split(',').map(s => s.trim())).filter(s => /^[a-z_]+$/.test(s))
    const subs = (await leer('omni help', /OmniEvents/)).map(sinPrefijo).map(l => /^\/omni ([a-z]+) /.exec(l)?.[1]).filter(Boolean)
    if (!triggers.length || !acciones.length || !subs.length) throw new Error('No pude leer /omni triggers, actions o help de la consola')
    const ejemplos = reglasDe(path.join(RAIZ, 'plugins/omnievents/src/main/resources/rules/ejemplos'))
    return [...triggers.map(t => 'trigger:' + t), ...acciones.map(a => 'action:' + a), ...subs.map(s => 'command:' + s),
      ...ejemplos.map(x => 'example:' + x)]
  },

  escenas
}
