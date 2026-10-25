// Unitarias del arnés para los plugins de terceros (spec 012): no bajan nada ni arrancan un servidor.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { PLUGINS_EXTRA, configurarEssentials, configurarGeyser, ponerClave } from './plugins-extra.mjs'

const carpeta = () => fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-extra-'))

test('cada versión de Paper trae Vault, EssentialsX, WorldEdit, WorldGuard, Citizens, Geyser y Floodgate con su sha256', () => {
  for (const [version, lista] of Object.entries(PLUGINS_EXTRA)) {
    assert.equal(lista.length, 7, version)
    for (const p of lista) {
      assert.match(p.sha256, /^[0-9a-f]{64}$/, p.archivo)
      assert.match(p.url, /^https:\/\/(github\.com\/MilkBowl|cdn\.modrinth\.com|ci\.citizensnpcs\.co|download\.geysermc\.org\/v2\/projects)\//, p.archivo)
    }
  }
})

test('configurarEssentials reemplaza disabled-commands y deja lo demás', () => {
  const dir = carpeta()
  fs.mkdirSync(path.join(dir, 'Essentials'))
  const archivo = path.join(dir, 'Essentials', 'config.yml')
  fs.writeFileSync(archivo, 'ops-name-color: \'4\'\ndisabled-commands:\n#  - nick\nverbose-command-usages: true\n')
  configurarEssentials(dir, ['give', 'list'])
  assert.equal(fs.readFileSync(archivo, 'utf8'), 'ops-name-color: \'4\'\ndisabled-commands: [give, list]\n#  - nick\nverbose-command-usages: true\n')
})

test('configurarEssentials crea la configuración si Essentials todavía no arrancó', () => {
  const dir = carpeta()
  configurarEssentials(dir, ['tp'])
  assert.equal(fs.readFileSync(path.join(dir, 'Essentials', 'config.yml'), 'utf8'), 'disabled-commands: [tp]\n')
})

test('ponerClave reemplaza una clave anidada, agrega la que falta bajo su sección y respeta los comentarios', () => {
  const yaml = 'bedrock:\n  # puerto\n  #address: 0.0.0.0\n  port: 19132\nremote:\n  port: 25565\nadvanced:\n  bedrock:\n    mtu: 1400\n'
  let t = ponerClave(yaml, 'bedrock.port', '19199')
  t = ponerClave(t, 'bedrock.address', '127.0.0.1')
  t = ponerClave(t, 'advanced.bedrock.validate-bedrock-login', 'false')
  assert.equal(t, 'bedrock:\n  address: 127.0.0.1\n  # puerto\n  #address: 0.0.0.0\n  port: 19199\nremote:\n  port: 25565\nadvanced:\n  bedrock:\n    validate-bedrock-login: false\n    mtu: 1400\n')
  assert.equal(ponerClave('a: 1\n', 'x.y', '2'), null)
  assert.equal(ponerClave('a: 1', 'b', '2'), 'b: 2\na: 1')
})

test('configurarGeyser deja Bedrock en 127.0.0.1, sin firma de Xbox y sin métricas (2.6 y 2.10)', () => {
  for (const [config, esperado] of [
    ['bedrock:\n  port: 19132\nnotify-on-new-bedrock-update: true\nmetrics:\n  enabled: true\nenable-proxy-connections: false\n',
      /bedrock:\n {2}address: 127\.0\.0\.1\n {2}port: 19199[\s\S]*notify-on-new-bedrock-update: false[\s\S]*enabled: false[\s\S]*enable-proxy-connections: true/],
    ['bedrock:\n  address: 0.0.0.0\n  port: 19132\nnotify-on-new-bedrock-update: true\nadvanced:\n  bedrock:\n    validate-bedrock-login: true\n',
      /address: 127\.0\.0\.1\n {2}port: 19199[\s\S]*validate-bedrock-login: false/]
  ]) {
    const dir = carpeta()
    assert.equal(configurarGeyser(dir, 19199), false)
    assert.equal(fs.readFileSync(path.join(dir, 'bStats', 'config.yml'), 'utf8'), 'enabled: false\n')
    fs.mkdirSync(path.join(dir, 'Geyser-Spigot'))
    fs.writeFileSync(path.join(dir, 'Geyser-Spigot', 'config.yml'), config)
    assert.equal(configurarGeyser(dir, 19199), true)
    assert.match(fs.readFileSync(path.join(dir, 'Geyser-Spigot', 'config.yml'), 'utf8'), esperado)
  }
})
