---
description: "Inventory menus where each click is a small rule, and native forms for Bedrock players."
---

# Menus

An entry with `menu:` instead of `trigger:` is an inventory menu. Open it with the `open_menu` action from any rule.
**Each click is a small rule** with its own `conditions` (and `deny`), `cost`, `cooldown`, `limit`, `actions` and
`tests`, so there is no second language to learn. Players cannot take the items out.

```yaml
open_shop:
  trigger: command
  command: shop
  actions:
    - open_menu: shop

shop:
  menu:
    title: "<dark_green>Shop of {player.name}"
    rows: 3
    fill: GRAY_STAINED_GLASS_PANE
    on_open: ["sound: UI_BUTTON_CLICK"]
    items:
      sword:
        slot: 11
        item: "DIAMOND_SWORD;1;<aqua>Sword;<gray>5 emeralds"
        left_click:
          cost: {items: "EMERALD;5"}
          actions: ["give_item: DIAMOND_SWORD"]
          on_cost_fail: ["message: <red>You are missing {var.cost_missing}"]
          close: true
        right_click:
          actions: ["message: It costs 5 emeralds"]
      vip_locked:
        slot: 15
        view: "!perm('rank.vip')"
        item: "BARRIER;1;<red>VIP only"
      vip_kit:
        slot: 15
        item: "NETHER_STAR;1;<gold>VIP kit"
        click: {limit: 1, limit_reset: daily, actions: ["give_item: GOLDEN_APPLE;3"]}
```

## Menu keys
| Key | What it does |
|---|---|
| `title` | The inventory title (expressions are evaluated when it opens). |
| `rows` | 1 to 6. |
| `fill` | An item for the empty slots. |
| `permission`, `conditions`, `cost` | Requirements to open the menu. |
| `on_open`, `on_close` | Actions when it opens and closes. |
| `items` | The items, each with `slot` (`11`, `[11, 12]` or `"0-8"`), `item`, `view` and its clicks. |
| `bedrock` | How Bedrock players see it (below). |

Clicks are `click`, `left_click`, `right_click` and `shift_click` (the most specific wins). Inside a click, `menu`,
`slot`, `click` and `item` describe what was clicked. Two items in the same slot with `view` conditions show the first
visible one, which is how you show a different item to VIPs.

## Bedrock players
With Floodgate installed, Bedrock players get the same menu as a **native button form**: the title, `bedrock.content`
and one button per visible item with a click. `bedrock: {enabled: false}` shows them the inventory instead, and
`is_bedrock()` tells you whether a player is on Bedrock.

`/omni menu open <player> <menu>` opens a menu from the console.
