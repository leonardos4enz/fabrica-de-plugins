# More features

A short tour of the advanced features. Each section links to the full explanation in the plugin README, which is in
Spanish for now.

## State rules
"When X **becomes** true" instead of "every X seconds, check". `on_enter` runs once when `when` becomes true for a
player, and `on_exit` once when it stops being true. The plugin works out which events can change the expression and
only checks then, so there is no polling and no "already warned" variable.

```yaml
low_health:
  trigger: state
  when: "player.health <= 6"
  cooldown: 20s
  on_enter:
    - title: "<red>Danger!;<gray>Find shelter"
  on_exit:
    - actionbar: "<green>Safe after {var.state_seconds}s"
```

If the expression uses something that sends no event (a placeholder, `perm()`, the time), the rule asks you to add
`check_every: 5s`. `in_state('low_health')` tells other rules whether the player is in the state.
[Full details](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#disparador-de-estado-trigger-state).

## Rule tests
Each rule can carry test cases: the context that goes in (`given`) and what you expect (`expect`). They run with no
server, no players and no real effects, on every load and with `/omni test`.

```yaml
diamond_reward:
  trigger: block_break
  conditions: ["block.type == 'DIAMOND_ORE'", "player.gamemode == 'survival'"]
  actions:
    - set: "pvar.diamonds = default(pvar.diamonds, 0) + 1"
    - console: "give {player.name} emerald 1"
  tests:
    - name: counts one
      given:
        player.name: Alex
        player.gamemode: SURVIVAL
        block.type: DIAMOND_ORE
        pvar.diamonds: 9
      expect:
        fired: true
        pvar.diamonds: 10
        commands: ["give Alex emerald"]
    - name: stone does not count
      given: {block.type: STONE}
      expect: {fired: false, branch: none}
```

`expect` can check `fired`, `branch` (`actions`, `else`, `deny`, `none`…), variables, `messages`, `commands`,
`actions`, `cancelled` and `assert` expressions.
[Full details](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#pruebas-de-reglas-tests-y-omni-test).

## Menus
An entry with `menu:` instead of `trigger:` is an inventory menu. Each click is a small rule with its own
`conditions`, `cost`, `cooldown` and `actions`. Players cannot take the items out. With Floodgate, Bedrock players get
the same menu as a native button form.

```yaml
open_shop:
  trigger: command
  command: shop
  actions:
    - open_menu: shop

shop:
  menu:
    title: "<dark_green>Shop"
    rows: 3
    fill: GRAY_STAINED_GLASS_PANE
    items:
      sword:
        slot: 13
        item: "DIAMOND_SWORD;1;<aqua>Sword;<gray>5 emeralds"
        click:
          cost: {items: "EMERALD;5"}
          actions: ["give_item: DIAMOND_SWORD"]
          on_cost_fail: ["message: <red>You are missing {var.cost_missing}"]
          close: true
```

[Full details](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#menús-de-inventario-y-formularios-de-bedrock).

## Packs and templates
A folder in `rules/` with a `pack.yml` is a **pack**: its rules are called `pack:rule`, so you can copy a pack from
another server without name clashes. A pack has a version, can require other packs or a plugin version, and can run
migrations when you update it. A rule with `template: true` can be reused by others with `extends:` and
`${parameters}`.
[Full details](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#paquetes-plantillas-y-versión).

## Integrations
- **Vault:** `cost: {money: 500}`, `give_money`, `take_money`, `money()`, `money_format()`.
- **WorldGuard:** `region_enter` and `region_leave` triggers (cancel them to block the way), `in_region()`, `regions()`.
- **Citizens:** `npc_click` and `npc_left_click` triggers, `is_npc()`.
- **ProtocolLib:** `player_receive_chat` reacts to messages a player is about to receive, and `cancel` hides them.
- **PlaceholderAPI:** placeholders in conditions and texts, and the `%omni_…%` placeholders (see [variables](variables.md#placeholderapi)).

[Full details](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#integraciones-costos-vault-worldguard-y-citizens).

## Performance tools
`/omni cpu on` turns on a sampling profiler; `/omni cpu` then lists each rule with its average time per run and its
most expensive conditions and actions. `budget: 0.5ms` on a rule warns in the console when it gets slower than that.
[Full details](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#rendimiento-omni-cpu-y-budget).

## Java API
Other plugins can add functions, actions and properties, call rules, and listen to `OmniRuleFireEvent`:

```java
OmniEventsAPI.registerFunction("double(x)", "Twice x.", 1, 1, (ctx, a) -> Values.num(a[0]) * 2);
OmniEventsAPI.registerProperty(Player.class, "clan", p -> MyPlugin.clanOf(p));
OmniEventsAPI.callRule("reward", player, Map.of("amount", 5));
```

[Full details](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#api).
