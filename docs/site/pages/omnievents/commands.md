# Commands and permissions

The main command is `/omni` (aliases `/oe` and `/omnievents`). Every subcommand has the permission
`omnievents.command.<subcommand>`, and `omnievents.command.*` gives all of them. By default, only operators have them.

> Give `omnievents.command.*` only to administrators: `run`, `call`, `var` and `eval` can change players and the
> server.

## Everyday commands
| Command | What it does |
|---|---|
| `/omni reload` | Reloads `config.yml`, `messages.yml` and every rule. Says how many rules loaded, with errors and warnings. |
| `/omni validate` | Checks every rule and lists each problem with its file, key and a "did you mean…?" hint. |
| `/omni list [filter]` | Lists the rules, enabled or not. |
| `/omni info <rule>` | Shows a rule: trigger, options, file. |
| `/omni enable <rule>` / `disable <rule>` | Turns a rule on or off until the next reload (for good, use `enabled:` in the file). |

## Finding out why
| Command | What it does |
|---|---|
| `/omni debug <rule\|all\|off>` | Shows each run live: every condition with its values (`player.level (3) >= 10 → false`), the branch and each action. |
| `/omni debug export` | Saves the latest traces to `plugins/OmniEvents/trazas/` to share when asking for help (check it first: it has player names). |
| `/omni check <rule> [player]` | Evaluates the rule **now** for that player without running anything: all conditions, uses, cooldown and the branch it would take. |
| `/omni run <rule> <player> [--simular]` | Runs the rule as if that player triggered it. With `--simular` (Spanish for "simulate") it only lists the actions it would run. |
| `/omni eval <expression>` | Evaluates an expression and shows its value and type. |
| `/omni test [rule]` | Runs the rules' [tests](more-features.md#rule-tests). |
| `/omni stats` | Runs, passes, errors and time per rule; active listeners; detected integrations. |
| `/omni cpu [on\|off\|reset]` | Profiler: the most expensive rules, conditions and actions. |
| `/omni triggers`, `/omni actions` | Lists every trigger or action. |

## Data and players
| Command | What it does |
|---|---|
| `/omni var get\|set\|del\|list <player\|global> [name] [value] [--expire 10m]` | Reads and changes [variables](variables.md). |
| `/omni data [block\|entity\|hand]` | Shows the data saved on a block, an entity or the item in your hand. |
| `/omni cooldown reset <rule\|*> [player\|*\|global]` | Resets cooldowns. |
| `/omni limit reset <rule\|*> [player\|*\|global]` | Resets uses. |
| `/omni call <rule> [player] [key=value…]` | Runs a `trigger: call` rule with arguments. |
| `/omni interrupt <rule\|*> [player]` | Cancels what is left of runs that are waiting on a `delay`. |
| `/omni state <rule>` | Who is inside a [state rule](more-features.md#state-rules), and since when. |
| `/omni menu list\|open <player> <menu>\|close <player>` | Lists, opens or closes [menus](more-features.md#menus). |
| `/omni pack list\|info\|enable\|disable [pack]` | Manages [packs](more-features.md#packs-and-templates). |
| `/omni item save\|delete\|list [name]` | Saves the item in your hand, to use it later as `give_item: "saved:<name>"`. |

## Tools
| Command | What it does |
|---|---|
| `/omni import conditionalevents` | Converts your ConditionalEvents configuration. See [migrating](migrating-from-conditionalevents.md). |
| `/omni schema` | Writes the JSON schema and the reference for **your** server, including what other plugins added. |
| `/omni selftest` | Checks that the plugin works on this server. |
| `/omni help` | Lists the subcommands. |

`-s` at the end of `call`, `enable`, `disable` and `interrupt` sends no reply, which is useful in `console:` actions
of other rules.

## Permissions used by rules
| Permission | What it does |
|---|---|
| `omnievents.bypasscooldown.<rule>` | Skips that rule's cooldown (only when it is given to the player; see [cooldowns](rule-options.md#cooldowns)). |
| Your own | `permission:` and `bypass_permission:` in a rule, the `permission` of a `command` rule, and `perm('…')` in expressions use any permission you choose. |

The plugin's messages are in Spanish by default; change them in `messages.yml`.
