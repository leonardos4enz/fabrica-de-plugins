// Unitarias del arnés (spec 002, H2): no arrancan ningún servidor. node --test lib/
import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { Servidor, uuidOffline, jarMasNuevo, copiarCarpeta } from './servidor.mjs'

const carpeta = () => fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-'))

test('uuidOffline coincide con UUID.nameUUIDFromBytes("OfflinePlayer:<nombre>") de Java', () => {
  assert.equal(uuidOffline('Notch'), 'b50ad385-829d-3141-a216-7e7d7539ba7f')
})

test('H2 CA1: sin EULA aceptado por el dueño, la suite no arranca ni escribe nada', () => {
  const run = carpeta()
  const s = new Servidor({ run, java: 'java', puerto: 25599 })
  assert.throws(() => s.prepararPropiedades(), /EULA/)
  fs.writeFileSync(path.join(run, 'eula.txt'), 'eula=false\n')
  assert.throws(() => s.prepararPropiedades(), /EULA/)
  assert.equal(fs.existsSync(path.join(run, 'server.properties')), false)
})

test('H2 CA2: fuerza solo localhost y sin cuentas, y respeta el resto de propiedades', () => {
  const run = carpeta()
  fs.writeFileSync(path.join(run, 'eula.txt'), 'eula=true\n')
  fs.writeFileSync(path.join(run, 'server.properties'), '#comentario\nserver-ip=0.0.0.0\nonline-mode=true\nmotd=Hola\n')
  new Servidor({ run, java: 'java', puerto: 25601 }).prepararPropiedades()
  const p = fs.readFileSync(path.join(run, 'server.properties'), 'utf8')
  assert.match(p, /^server-ip=127\.0\.0\.1$/m)
  assert.match(p, /^online-mode=false$/m)
  assert.match(p, /^server-port=25601$/m)
  assert.match(p, /^motd=Hola$/m)
  assert.match(p, /^#comentario$/m)
  assert.doesNotMatch(p, /0\.0\.0\.0|online-mode=true/)
})

test('H1 CA3: olvidar jugadores borra sus datos del mundo y del plugin, y nada más', () => {
  const run = carpeta()
  const id = uuidOffline('Ana_e2e')
  const otro = uuidOffline('Otro')
  for (const f of [`world/playerdata/${id}.dat`, `world/players/data/${id}.dat`, `world/playerdata/${otro}.dat`, `plugins/X/data/${id}.yml`]) {
    fs.mkdirSync(path.dirname(path.join(run, f)), { recursive: true })
    fs.writeFileSync(path.join(run, f), 'x')
  }
  new Servidor({ run, java: 'java', puerto: 1 }).olvidarJugadores(['Ana_e2e'], ['plugins/X/data/{uuid}.yml'])
  assert.equal(fs.existsSync(path.join(run, `world/playerdata/${id}.dat`)), false)
  assert.equal(fs.existsSync(path.join(run, `world/players/data/${id}.dat`)), false) // formato 26.1
  assert.equal(fs.existsSync(path.join(run, `plugins/X/data/${id}.yml`)), false)
  assert.equal(fs.existsSync(path.join(run, `world/playerdata/${otro}.dat`)), true)
})

test('005 F-01: con varias versiones en dist/ se instala el jar más reciente, no el primero por nombre', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-dist-'))
  try {
    for (const [f, t] of [['OmniEvents-1.0.0.jar', 1000], ['OmniEvents-1.0.1.jar', 2000], ['otro.jar', 3000]]) {
      fs.writeFileSync(path.join(dir, f), '')
      fs.utimesSync(path.join(dir, f), t, t)
    }
    const patron = /^OmniEvents-.*.jar$/
    assert.equal(fs.readdirSync(dir).find(f => patron.test(f)), 'OmniEvents-1.0.0.jar', 'así lo elegía el código anterior')
    assert.equal(jarMasNuevo(dir, patron), 'OmniEvents-1.0.1.jar')
  } finally {
    fs.rmSync(dir, { recursive: true, force: true })
  }
})

// Regresión (spec 011, F-01): los ejemplos traen un paquete en una subcarpeta (ejemplos/recompensas/) y la copia
// de la suite no entraba en subcarpetas (copyFileSync de una carpeta revienta).
test('copiarCarpeta copia también las subcarpetas', () => {
  const de = carpeta()
  const a = path.join(carpeta(), 'destino')
  fs.writeFileSync(path.join(de, 'uno.yml'), 'a: 1\n')
  fs.mkdirSync(path.join(de, 'paquete', 'sub'), { recursive: true })
  fs.writeFileSync(path.join(de, 'paquete', 'pack.yml'), 'version: 1.0.0\n')
  fs.writeFileSync(path.join(de, 'paquete', 'sub', 'dos.yml'), 'b: 2\n')
  copiarCarpeta(de, a)
  assert.equal(fs.readFileSync(path.join(a, 'uno.yml'), 'utf8'), 'a: 1\n')
  assert.equal(fs.readFileSync(path.join(a, 'paquete', 'pack.yml'), 'utf8'), 'version: 1.0.0\n')
  assert.equal(fs.readFileSync(path.join(a, 'paquete', 'sub', 'dos.yml'), 'utf8'), 'b: 2\n')
})
