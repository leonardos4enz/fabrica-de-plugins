// Unitarias del arnés (spec 002, H2): no arrancan ningún servidor. node --test lib/
import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { Servidor, uuidOffline } from './servidor.mjs'

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
