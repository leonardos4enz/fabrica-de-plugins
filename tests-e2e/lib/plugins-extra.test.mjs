// Unitarias del arnés para los plugins de terceros (spec 012): no bajan nada ni arrancan un servidor.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { PLUGINS_EXTRA, configurarEssentials } from './plugins-extra.mjs'

const carpeta = () => fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-extra-'))

test('cada versión de Paper trae Vault, EssentialsX, WorldEdit, WorldGuard y Citizens con su sha256', () => {
  for (const [version, lista] of Object.entries(PLUGINS_EXTRA)) {
    assert.equal(lista.length, 5, version)
    for (const p of lista) {
      assert.match(p.sha256, /^[0-9a-f]{64}$/, p.archivo)
      assert.match(p.url, /^https:\/\/(github\.com\/MilkBowl|cdn\.modrinth\.com|ci\.citizensnpcs\.co)\//, p.archivo)
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
