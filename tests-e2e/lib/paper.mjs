// Otra versión de Paper para la suite (spec 002, H5): servidor de prueba aparte en run-<versión>/, con el jar de la
// API Fill de PaperMC y su sha256 verificado.
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

export async function prepararPaper (version, run, runBase) {
  fs.mkdirSync(run, { recursive: true })
  // El dueño aceptó el EULA para el servidor de prueba de este plugin; se copia su aceptación, no se escribe.
  const eula = path.join(run, 'eula.txt')
  if (!fs.existsSync(eula) && fs.existsSync(path.join(runBase, 'eula.txt'))) fs.copyFileSync(path.join(runBase, 'eula.txt'), eula)
  if (fs.readdirSync(run).some(f => /^paper-.*\.jar$/.test(f))) return

  const api = `https://fill.papermc.io/v3/projects/paper/versions/${encodeURIComponent(version)}/builds/latest`
  console.log(`» Bajando Paper ${version} (${api})…`)
  const info = await (await fetch(api, { headers: { 'User-Agent': 'fabrica-de-plugins-e2e' } })).json()
  const d = info?.downloads?.['server:default']
  if (!d?.url || !d?.checksums?.sha256) throw new Error(`La API Fill no devolvió una descarga para ${version}: ${JSON.stringify(info).slice(0, 300)}`)
  const bytes = Buffer.from(await (await fetch(d.url)).arrayBuffer())
  const sha = createHash('sha256').update(bytes).digest('hex')
  if (sha !== d.checksums.sha256) throw new Error(`sha256 de ${d.name} no coincide: ${sha} ≠ ${d.checksums.sha256}`)
  fs.writeFileSync(path.join(run, d.name), bytes)
  console.log(`  ${d.name} (build ${info.id}), sha256 verificado ${sha}`)
}
