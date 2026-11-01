// Escenas de la página de presentación (spec 020): solo las que pide el póster, en inglés, con el paquete
// rules/examples-en tal cual. Cada escena guarda sus cuadros (ctx.cuadro) para los GIF de tests-e2e/presentacion.mjs.
// Uso: npm --prefix tests-e2e run capturas -- --escenas presentacion [--sin-build] [--solo "en-npc"]
import { createRequire } from 'node:module'
import { dormir } from '../lib/servidor.mjs'

const require = createRequire(import.meta.url)
const { Vec3 } = require('vec3')
const ANA = 'Ana'
const Y = 100

const bot = (ctx) => ctx.bots[ANA]
const consola = async (ctx, ...cmds) => {
  for (const c of cmds) await ctx.srv.consola(c)
  await ctx.srv.sincronizar()
}
/** El chat recibido entre dos marcas de tiempo (para rehacer un momento ya pasado, como la entrada de Ana). */
const chatHasta = (ctx, t) => bot(ctx).vistos.filter(x => x.tipo === 'chat' && x.t <= t).map(x => x.json)

async function prepararAna (ctx, { x = 12.5, z = 10.5, yaw = 0, pitch = 10 } = {}) {
  await consola(ctx, `gamemode survival ${ANA}`, `effect clear ${ANA}`, `effect give ${ANA} instant_health 1 10 true`,
    `effect give ${ANA} saturation 1 20 true`, `tp ${ANA} ${x} ${Y} ${z} ${yaw} ${pitch}`)
  await bot(ctx).hasta('estar en su punto', bb =>
    Math.abs(bb.entity.position.x - x) < 0.3 && Math.abs(bb.entity.position.z - z) < 0.3 && bb.health >= 20)
  await consola(ctx, `effect clear ${ANA}`)
  await bot(ctx).hasta('sin efectos', bb => Object.keys(bb.entity.effects).length === 0)
  await dormir(300)
}

/** Marca una región de WorldGuard con WorldEdit, como el escenario 019 de la suite (Ana es OP). */
async function region (ctx, id, [x1, y1, z1], [x2, y2, z2]) {
  const ana = bot(ctx)
  let m = ana.marca()
  ana.comando(`/pos1 ${x1},${y1},${z1}`)
  await ana.esperar('chat', /First position set/i, { desde: m })
  m = ana.marca()
  ana.comando(`/pos2 ${x2},${y2},${z2}`)
  await ana.esperar('chat', /Second position set/i, { desde: m })
  m = ana.marca()
  ana.comando(`rg define ${id}`)
  await ana.esperar('chat', new RegExp(`region.*${id}|${id}.*region`, 'i'), { desde: m })
}

const escenas = [
  {
    id: 'en-welcome', sinPreparar: true,
    texto: 'welcome: the first time Ana joins, a broadcast, a starter kit and, two seconds later, the title.',
    async fn (ctx) {
      // Ana ya entró en `antes`: se rehace ese momento con lo que recibió.
      const anuncio = bot(ctx).vistos.find(x => x.tipo === 'chat' && /Please welcome/.test(JSON.stringify(x.json))).t
      await ctx.foto({ desde: 0, chat: chatHasta(ctx, anuncio), sinTitulo: true, sinBarra: true })
      await ctx.cuadro()
      await ctx.foto({ desde: 0, chat: chatHasta(ctx, ctx.tituloAna), sinBarra: true })
      await ctx.cuadro()
    }
  },
  {
    id: 'en-daily',
    texto: '/daily: day 5 of the streak pays 5 × 25 coins; a second /daily the same day shows the cooldown left.',
    async fn (ctx) {
      const ana = bot(ctx)
      await consola(ctx, `omni var set ${ANA} daily_streak 4 --expire 48h`, `omni cooldown reset examples:daily ${ANA}`)
      let m = ana.marca()
      ana.comando('daily')
      await ana.esperar('chat', /Daily reward/, { desde: m })
      await ctx.foto({ desde: m, sinBarra: true })
      await ctx.cuadro()
      ana.comando('daily')
      await ana.esperar('chat', /already claimed/, { desde: m })
      await ctx.foto({ desde: m, sinBarra: true })
      await ctx.cuadro()
    }
  },
  {
    id: 'en-region', timeoutMs: 90000,
    texto: 'WorldGuard: entering the mine region gives Haste; without the VIP permission the VIP region is closed.',
    async fn (ctx) {
      const ana = bot(ctx)
      // La mina (piedra y menas) al sur de Ana; la zona VIP (oro) al norte.
      await consola(ctx, `fill 9 ${Y - 1} 14 15 ${Y - 1} 20 stone`, `fill 9 ${Y} 20 15 ${Y + 2} 20 stone`,
        `setblock 10 ${Y} 20 coal_ore`, `setblock 12 ${Y + 1} 20 diamond_ore`, `setblock 14 ${Y} 20 iron_ore`, `setblock 11 ${Y + 2} 20 gold_ore`,
        `fill 9 ${Y - 1} -3 15 ${Y - 1} 3 gold_block`, `fill 9 ${Y} -3 9 ${Y} 3 gold_block`, `fill 15 ${Y} -3 15 ${Y} 3 gold_block`)
      await region(ctx, 'mine', [9, Y - 1, 14], [15, Y + 6, 20])
      await region(ctx, 'vip', [9, Y - 1, -3], [15, Y + 6, 3])
      await prepararAna(ctx, { z: 10.5 })
      await dormir(800)
      await ctx.foto({ desde: ana.marca(), sinBarra: true }) // sin el chat de WorldEdit
      await ctx.cuadro()
      let m = ana.marca()
      await consola(ctx, `tp ${ANA} 12.5 ${Y} 14.7 0 5`)
      await ana.esperar('actionbar', /Mining boost on/, { desde: m })
      await ana.hasta('tener Haste', b => Object.keys(b.entity.effects).length > 0)
      await ctx.foto({ desde: m })
      await ctx.cuadro()
      // Sin OP no tiene el permiso no declarado examples.vip: cancel la deja afuera.
      await consola(ctx, `tp ${ANA} 12.5 ${Y} 7.5 180 10`, `deop ${ANA}`)
      await ana.esperar('actionbar', /Mining boost off/, { desde: m })
      m = ana.marca()
      await consola(ctx, `tp ${ANA} 12.5 ${Y} 1.5 180 10`)
      await ana.esperar('chat', /Only VIP players/, { desde: m })
      await dormir(600)
      await ctx.foto({ desde: m, sinBarra: true })
      await ctx.cuadro()
    },
    async limpiar (ctx) {
      await consola(ctx, `op ${ANA}`, 'rg remove -w world mine', 'rg remove -w world vip')
    }
  },
  {
    id: 'en-npc',
    // El visor 3D no dibuja jugadores (visor.mjs): ni aldeanos (sin textura por defecto): el NPC es un comerciante ambulante de Citizens; npc_guide solo mira el nombre.
    texto: 'Citizens: a right click on the Guide NPC greets Ana and gives bread; hitting it answers in the action bar.',
    async fn (ctx) {
      const ana = bot(ctx)
      await consola(ctx, 'npc remove all', `omni cooldown reset examples:npc_guide ${ANA}`)
      // El visor dibuja al comerciante siempre mirando al sur (+z): Ana se para al sur, mirando al norte.
      await prepararAna(ctx, { z: 16.2, yaw: 180 })
      await ctx.srv.consola(`npc create Guide --type WANDERING_TRADER --at 12.5:${Y}:13:world`, { espera: /Guide/ })
      await consola(ctx, `npc moveto 12.5:${Y}:13.4:world --yaw 0 --pitch 0`) 
      let npc
      await ana.hasta('ver al NPC', b => {
        npc = Object.values(b.entities).find(e => e !== b.entity && e.position.distanceTo(new Vec3(12.5, Y, 13.4)) < 1.5)
        return npc != null
      })
      await dormir(1500)
      // activateEntity y attack giran la cabeza hacia el NPC: se vuelve a esta mirada para que los cuadros no salten.
      const { yaw, pitch } = ana.bot.entity
      const mirar = async () => { await ana.bot.look(yaw, pitch, true); await dormir(400) }
      await ctx.foto({ sinBarra: true })
      await ctx.cuadro()
      let m = ana.marca()
      ana.bot.activateEntity(npc)
      await ana.esperar('chat', /\[Guide\] Hi/, { desde: m })
      await ana.hasta('recibir el pan', () => ana.cantidad('bread') >= 4)
      await mirar()
      await ctx.foto({ desde: m, sinBarra: true })
      await ctx.cuadro()
      const desde = m
      m = ana.marca()
      ana.bot.attack(npc)
      await ana.esperar('actionbar', /hey, that hurts/, { desde: m })
      await mirar()
      await ctx.foto({ desde })
      await ctx.cuadro()
    },
    async limpiar (ctx) { await consola(ctx, 'npc remove all') }
  }
]

export default {
  jar: /^OmniEvents-.*\.jar$/,
  version: '1.21.4',
  puerto: 25599,
  puertoVisor: 3017,
  propiedades: { 'spawn-monsters': 'true' },
  protagonista: ANA,
  bots: [ANA],
  datosDeJugador: ['plugins/OmniEvents/data/players/{uuid}.yml'],
  salida: 'docs/img/omnievents/presentacion',
  pluginsExtra: /worldedit|worldguard|Citizens/i,
  copiar: [
    { de: 'plugins/omnievents/src/main/resources/rules/examples-en', a: 'plugins/OmniEvents/rules/examples-en' },
    { de: null, a: 'plugins/OmniEvents/rules/ejemplos' } // sin los ejemplos en español: su bienvenida también saludaría
  ],
  preparar: [
    'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
    'gamerule naturalRegeneration false', 'gamerule sendCommandFeedback false', 'gamerule announceAdvancements false',
    'gamerule doTileDrops false', 'time set 6000', 'weather clear', 'difficulty peaceful', 'kill @e[type=!player]', 'npc remove all',
    'rg remove -w world mine', 'rg remove -w world vip',
    'fillbiome -4 96 -4 28 110 28 minecraft:plains', `fill -4 ${Y - 1} -4 28 ${Y - 1} 28 grass_block`,
    `fill -4 ${Y} -4 28 ${Y + 8} 28 air`, `setworldspawn 12 ${Y} 12`,
    // Solo el paquete en inglés, encendido antes de que entre Ana (vive su primera vez); sin avisos que se cuelen.
    'omni pack enable examples', 'omni disable examples:announcer', 'omni disable examples:spawn_zone', 'omni disable examples:playtime'
  ],
  limpiar: ['deop Ana', 'npc remove all', 'rg remove -w world mine', 'rg remove -w world vip', `fill -4 ${Y - 1} -4 28 ${Y + 8} 28 air`,
    'setworldspawn 12 -60 12', 'gamerule doTileDrops true', 'omni pack disable examples'],

  async antes (ctx) {
    await ctx.entrar(ANA)
    await consola(ctx, `op ${ANA}`)
    ctx.tituloAna = (await bot(ctx).esperar('title', /Welcome/, { timeoutMs: 15000 })).t
    await bot(ctx).esperar('chat', /Daily login bonus/, { timeoutMs: 15000 })
    await dormir(1500)
  },

  async antesDeCada (ctx, e) {
    if (e.sinPreparar) return
    await consola(ctx, 'kill @e[type=!player]', `fill -4 ${Y - 1} -4 28 ${Y - 1} 28 grass_block`, `fill -4 ${Y} -4 28 ${Y + 8} 28 air`)
    await prepararAna(ctx)
  },

  async despuesDeCada (ctx, e) {
    await e.limpiar?.(ctx)
    await ctx.srv.sincronizar()
  },

  escenas
}
