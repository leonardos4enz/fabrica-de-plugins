// Skins de youtubers para las escenas de la página de presentación (spec 024, decisión del dueño). Se bajan una sola
// vez de la API pública de Mojang a tests-e2e/.cache/skins/ (ignorada por git): <nombre>.png, <nombre>.yml con
// `model: slim` y la capa (<nombre>-cape.png) si la cuenta tiene. Nunca se copian al repositorio.
// Uso suelto: node tests-e2e/skinstatues/skins-youtubers.mjs
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const YOUTUBERS = ['Technoblade', 'ElRichMC', 'LTeZla', 'Vegetta777', 'Willyrex', 'elrubius', 'Mikecrack', 'Dream',
  'GeorgeNotFound', 'Sapnap', 'DanTDM', 'Quackity', 'Bobicraft']

export const CACHE = fileURLToPath(new URL('../.cache/skins/', import.meta.url))

async function json (url) {
  const r = await fetch(url)
  if (r.status === 204 || r.status === 404) return null
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`)
  return r.json()
}

async function bajar (url, archivo) {
  const r = await fetch(url)
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`)
  fs.writeFileSync(archivo, Buffer.from(await r.arrayBuffer()))
}

/** Baja las que falten en la caché. Devuelve { listas, faltan } (faltan: nombres que Mojang no conoce o que fallaron). */
export async function skinsYoutubers (nombres = YOUTUBERS, dir = CACHE) {
  fs.mkdirSync(dir, { recursive: true })
  const listas = []
  const faltan = []
  for (const nombre of nombres) {
    if (fs.existsSync(path.join(dir, nombre + '.png'))) { listas.push(nombre); continue }
    try {
      const perfil = await json(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(nombre)}`)
      if (!perfil?.id) { faltan.push(`${nombre} (no existe en Mojang)`); continue }
      const sesion = await json(`https://sessionserver.mojang.com/session/minecraft/profile/${perfil.id}`)
      const prop = sesion?.properties?.find(p => p.name === 'textures')
      if (!prop) { faltan.push(`${nombre} (sin texturas)`); continue }
      const tex = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8')).textures ?? {}
      if (!tex.SKIN?.url) { faltan.push(`${nombre} (sin skin)`); continue }
      await bajar(tex.SKIN.url.replace(/^http:/, 'https:'), path.join(dir, nombre + '.png'))
      const yml = []
      if (tex.SKIN.metadata?.model === 'slim') yml.push('model: slim')
      if (tex.CAPE?.url) {
        await bajar(tex.CAPE.url.replace(/^http:/, 'https:'), path.join(dir, nombre + '-cape.png'))
        yml.push(`cape: ${nombre}-cape.png`)
      }
      if (yml.length) fs.writeFileSync(path.join(dir, nombre + '.yml'), yml.join('\n') + '\n')
      listas.push(nombre)
      console.log(`  · skin de ${nombre}${yml.length ? ` (${yml.join(', ')})` : ''}`)
    } catch (e) {
      faltan.push(`${nombre} (${e.message})`)
    }
  }
  return { listas, faltan }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { listas, faltan } = await skinsYoutubers()
  console.log(`Listas: ${listas.join(', ')}`)
  if (faltan.length) console.log(`Faltan: ${faltan.join('; ')}`)
}
