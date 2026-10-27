---
description: "Vault money, WorldGuard regions, Citizens NPCs, ProtocolLib and PlaceholderAPI."
---

# Integrations

Every integration is **optional**. Without the other plugin OmniEvents loads normally, and a rule that needs it loads
with a warning ("needs Vault, which is not installed") and skips that part. `/omni stats` shows what was detected.

## Vault (money)
`cost: {money: 500}` charges money all or nothing, `give_money` and `take_money` change the balance, `money()` reads
it and `money_format(n)` writes an amount with the economy's currency.

```yaml
buy_vip_pass:
  trigger: command
  command: buypass
  cost:
    money: 500
  actions:
    - give_item: "PAPER;1;<gold>VIP pass"
    - message: "<green>Bought! You have {money_format(money())} left."
  on_cost_fail:
    - message: "<red>You are missing {var.cost_missing}."
```

## WorldGuard (regions)
`region_enter` and `region_leave` run for each region a player enters or leaves (walking, teleporting, joining or
changing world). `region.id` is the region; `cancel` keeps the player where they were. `in_region(id)` and `regions()`
work in any rule.

```yaml
vip_zone:
  trigger: region_enter
  conditions: ["region.id == 'vip'", "!perm('rank.vip')"]
  actions:
    - cancel
    - message: "<red>That area is for VIPs only."
```

## Citizens (NPCs)
`npc_click` (right click) and `npc_left_click` (hit), with `npc.id`, `npc.name` and `npc.entity`. `is_npc(entity)`
works in any rule.

```yaml
guide_npc:
  trigger: npc_click
  conditions: "npc.name == 'Guide'"
  cooldown: 1h
  actions:
    - message: "<yellow>[Guide]</yellow> Hi {player.name}! Type /spawn to go back."
```

## ProtocolLib (messages a player receives)
`player_receive_chat` runs when a player is **about to receive** a chat or system message: one from another plugin,
a `/tellraw`, a `message` from another rule. `cancel` hides it. The text is untrusted (it can come from another player)
and is escaped when inserted. A rule that answers with a message that triggers it again is cut off by
`protocollib.max-messages-per-second` (20).

```yaml
hide_ads:
  trigger: player_receive_chat
  conditions: ["lower(message) contains 'buy now at'"]
  actions:
    - cancel
    - log: "hidden from {player.name}: {message}"
```

## PlaceholderAPI
Any placeholder works in conditions and texts (`%vault_eco_balance% > 100`), and OmniEvents adds its own
[`%omni_…%` placeholders](../variables.md#placeholderapi) for scoreboards, tab lists and holograms.
