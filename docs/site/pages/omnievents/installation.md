# Installation

## Requirements
- A **Paper** server, 1.21.4 or newer (see [compatibility](README.md#compatibility)).
- **Java 21** or newer. Use the Java version your Paper version asks for (Paper 26.x needs Java 25).

## Steps
1. Download `OmniEvents-x.y.z.jar` and put it in your server's `plugins/` folder.
2. Start the server. OmniEvents creates `plugins/OmniEvents/` with:
   - `config.yml`: general settings.
   - `messages.yml`: every message of the plugin, in MiniMessage format (in Spanish; translate them freely).
   - `rules/`: your rules. Example rules are copied to `rules/ejemplos/` the first time.
3. Add or edit `.yml` files in `rules/` (any name, any subfolder) and run `/omni reload`.

Every top-level key of a rules file is one rule, and rule names must be unique across all files.

```text
plugins/OmniEvents/
├── config.yml
├── messages.yml
├── rules/
│   ├── ejemplos/       ← bundled examples (safe to delete)
│   └── my-server.yml   ← your rules
└── data/               ← saved variables, cooldowns and uses (do not edit while the server runs)
```

## Optional plugins
OmniEvents works on its own. These plugins unlock extra features when they are installed; a rule that needs a
missing one loads with a warning and skips that part.

| Plugin | Unlocks |
|---|---|
| PlaceholderAPI | `%placeholders%` in conditions and texts, and OmniEvents' own `%omni_…%` placeholders |
| Vault + an economy plugin | `cost: {money: …}`, the `give_money` and `take_money` actions, `money()` |
| WorldGuard (with WorldEdit) | `region_enter` and `region_leave` triggers, `in_region()`, `regions()` |
| Citizens | `npc_click` and `npc_left_click` triggers, `is_npc()` |
| Floodgate (with Geyser) | Menus shown as native forms to Bedrock players, `is_bedrock()` |
| ProtocolLib | `player_receive_chat`: react to (or hide) messages a player is about to receive |

`/omni stats` shows which integrations were detected.

## Autocomplete in VS Code
OmniEvents publishes a JSON schema for its rule files. Install the **YAML** extension by Red Hat and put this line at the
top of each rules file:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/omnievents/omnievents.schema.json
```

You get suggestions for options, triggers and actions while you type, and typos such as `cooldwon:` are underlined
before you even reload. Expressions, materials and sounds are checked by `/omni validate` on the server.

## Updating
Replace the jar and restart. Your rules, variables and cooldowns are kept. When a version changes the data format, the
plugin makes a backup in `data/` first (for example, 1.4.0 creates `data/respaldo-formato1-<date>/`).
