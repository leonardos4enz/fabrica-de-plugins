// Compuerta 3a de VERIFY (spec 002): compila el plugin, arranca su Paper de prueba, conecta bots, corre los
// escenarios y apaga. Uso:
//   npm --prefix tests-e2e test [-- --plugin omnievents] [--sin-build] [--solo "H5 CA1"]
//     [--paper 26.1.2 --version-bot 26.1 --java-servidor "C:\ruta\jdk-25"]   (otra versión de Paper, en run-<versión>/)
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { Servidor, dormir, jarMasNuevo, copiarCarpeta } from './lib/servidor.mjs'
import { conectar } from './lib/bot.mjs'
import { prepararPaper } from './lib/paper.mjs'
import { instalarPluginsExtra, configurarEssentials, configurarGeyser } from './lib/plugins-extra.mjs'
import { conectarBedrock, VERSION_BEDROCK } from './lib/bedrock.mjs'

const AQUI = path.dirname(fileURLToPath(import.meta.url))
const RAIZ = path.resolve(AQUI, '..')

function opciones (argv) {
  const o = { plugin: 'omnievents', paper: null, versionBot: null, javaServidor: null, build: true, solo: null }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--plugin') o.plugin = argv[++i]
    else if (a === '--paper') o.paper = argv[++i]
    else if (a === '--version-bot') o.versionBot = argv[++i]
    else if (a === '--java-servidor') o.javaServidor = argv[++i]
    else if (a === '--sin-build') o.build = false
    else if (a === '--solo') o.solo = new RegExp(argv[++i], 'i')
    else throw new Error(`Opción desconocida: ${a}`)
  }
  return o
}

/** Gradle 8.14 no corre sobre Java 25 (CLAUDE.md): se prefiere el JDK 24 aunque JAVA_HOME apunte a otro. */
function javaHome () {
  const jdk24 = 'C:\\Program Files\\Java\\jdk-24'
  return process.env.FABRICA_JAVA_HOME || (fs.existsSync(jdk24) ? jdk24 : process.env.JAVA_HOME)
}

function compilar (plugin) {
  console.log(`» Compilando ${plugin} (gradlew :${plugin}:dist)…`)
  const win = process.platform === 'win32'
  // Ruta absoluta y entre comillas: la carpeta tiene espacios y cmd puede no buscar en el directorio actual.
  const gradlew = path.join(RAIZ, win ? 'gradlew.bat' : 'gradlew')
  const r = spawnSync(win ? `"${gradlew}"` : gradlew, [`:${plugin}:dist`, '-q'], {
    cwd: RAIZ, stdio: 'inherit', shell: win, env: { ...process.env, JAVA_HOME: javaHome() }
  })
  if (r.status !== 0) throw new Error('La compilación falló')
}

async function principal () {
  const o = opciones(process.argv.slice(2))
  const suite = (await import(`./${o.plugin}/suite.mjs`)).default
  if (o.build) compilar(o.plugin)

  const base = path.join(RAIZ, 'plugins', o.plugin)
  const run = o.paper ? path.join(base, `run-${o.paper}`) : path.join(base, 'run')
  if (o.paper) await prepararPaper(o.paper, run, path.join(base, 'run'))
  const versionBot = o.versionBot ?? o.paper ?? suite.versionPorDefecto

  // Instala el jar recién compilado y las reglas de la suite (H3 CA3).
  const pluginsDir = path.join(run, 'plugins')
  fs.mkdirSync(pluginsDir, { recursive: true })
  for (const f of fs.readdirSync(pluginsDir)) if (suite.jar.test(f)) fs.rmSync(path.join(pluginsDir, f))
  const jar = jarMasNuevo(path.join(RAIZ, 'dist'), suite.jar)
  if (!jar) throw new Error(`No hay ${suite.jar} en dist/: compila con gradlew :${o.plugin}:dist`)
  fs.copyFileSync(path.join(RAIZ, 'dist', jar), path.join(pluginsDir, jar))
  // Spec 012: plugins de terceros de las integraciones (Vault, EssentialsX, WorldEdit, WorldGuard, Citizens).
  if (suite.pluginsExtra) {
    await instalarPluginsExtra(o.paper ?? suite.versionPorDefecto, pluginsDir, path.join(AQUI, '.cache', 'plugins'))
    configurarEssentials(pluginsDir, suite.comandosDeMinecraft ?? [])
    // Spec 013: Geyser + Floodgate para los jugadores de Bedrock (solo 127.0.0.1).
    if (suite.puertoBedrock && !configurarGeyser(pluginsDir, suite.puertoBedrock)) {
      console.log('  (Geyser aún no generó su configuración: los escenarios de Bedrock fallarán en esta corrida; la siguiente ya la tiene)')
    }
  }
  // Configuración que se copia en cada corrida (origen relativo a la raíz del repositorio, destino a run/): los
  // ejemplos del plugin tal como están en el código fuente y las reglas propias de la suite.
  for (const { de, a } of suite.copiar) {
    fs.rmSync(path.join(run, a), { recursive: true, force: true })
    copiarCarpeta(path.join(RAIZ, de), path.join(run, a))
  }

  const srv = new Servidor({ run, java: path.join(o.javaServidor ?? javaHome(), 'bin', 'java'), puerto: suite.puerto })
  srv.prepararPropiedades()
  srv.olvidarJugadores(suite.bots, suite.datosDeJugador)

  const resultados = []
  const bots = {}
  const t0 = Date.now()
  try {
    console.log(`» Arrancando Paper en ${path.relative(RAIZ, run)} (127.0.0.1:${suite.puerto})…`)
    await srv.arrancar()
    for (const c of suite.preparar) await srv.consola(c)
    await srv.sincronizar()

    const ctx = {
      srv,
      bots,
      version: versionBot,
      /** Conecta (o reconecta) un bot y lo deja listo. */
      async entrar (nombre) {
        if (bots[nombre]) await bots[nombre].salir()
        bots[nombre] = await conectar(nombre, { puerto: suite.puerto, version: versionBot })
        return bots[nombre]
      },
      /** Conecta un jugador de Bedrock por Geyser (spec 013); en el servidor se llama con el prefijo de Floodgate. */
      async entrarBedrock (nombre) {
        if (bots[nombre]) await bots[nombre].salir()
        const version = VERSION_BEDROCK[o.paper ?? suite.versionPorDefecto]
        if (!version) throw new Error(`sin versión de Bedrock para Paper ${o.paper ?? suite.versionPorDefecto}`)
        bots[nombre] = await conectarBedrock(nombre, { puerto: suite.puertoBedrock, version })
        return bots[nombre]
      },
      async salir (nombre) {
        await bots[nombre]?.salir()
        delete bots[nombre]
      }
    }
    await suite.antes?.(ctx)

    const escenarios = suite.escenarios.filter(e => !o.solo || o.solo.test(`${e.id} ${e.nombre}`))
    for (const e of escenarios) {
      const inicio = Date.now()
      const marcaLog = srv.marca()
      try {
        await Promise.race([
          e.fn(ctx),
          dormir(e.timeoutMs ?? 60000).then(() => { throw new Error(`tiempo agotado (${e.timeoutMs ?? 60000} ms)`) })
        ])
        const errores = srv.errores({ desde: marcaLog, ignorar: suite.ruidoDelServidor, ademas: suite.erroresDelPlugin })
        if (errores.length) throw new Error('el log del servidor tiene errores:\n    ' + errores.slice(0, 5).join('\n    '))
        resultados.push({ e, ok: true, ms: Date.now() - inicio })
        console.log(`  ✔ ${e.id} — ${e.nombre} (${Date.now() - inicio} ms)`)
      } catch (err) {
        resultados.push({ e, ok: false, ms: Date.now() - inicio, err })
        console.log(`  ✘ ${e.id} — ${e.nombre}\n    ${String(err.message).split('\n').join('\n    ')}`)
      }
    }
  } finally {
    for (const n of Object.keys(bots)) await bots[n].salir().catch(() => {})
    if (srv.proceso?.exitCode == null) {
      for (const c of suite.limpiar ?? []) await srv.consola(c)
      await srv.sincronizar().catch(() => {})
    }
    await srv.detener()
    for (const { a, retirar } of suite.copiar) if (retirar) fs.rmSync(path.join(run, a), { recursive: true, force: true }) // H1 CA4
  }

  // H1 CA5: errores del plugin en todo el log (arranque y apagado incluidos).
  const errores = srv.errores({ ignorar: suite.ruidoDelServidor, ademas: suite.erroresDelPlugin })
  const fallos = resultados.filter(r => !r.ok).length
  console.log(`\n${resultados.length - fallos}/${resultados.length} escenarios en verde en ${Math.round((Date.now() - t0) / 1000)} s` +
    ` · log: ${path.relative(RAIZ, path.join(run, 'e2e.log'))}`)
  if (errores.length) console.log(`✘ El log del servidor tiene ${errores.length} errores:\n  ` + errores.slice(0, 10).join('\n  '))
  process.exitCode = fallos || errores.length || resultados.length === 0 ? 1 : 0
}

principal().catch(err => {
  console.error('✘ ' + (err.stack || err.message))
  process.exitCode = 2
})
