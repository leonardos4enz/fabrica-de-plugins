---
description: "Share folders of rules without name clashes, and reuse rules with parameters."
---

# Packs and templates

## Packs
A folder in `rules/` with a `pack.yml` is a **pack**. Its rules are called `pack:rule`, so you can copy a pack from
another server without its names clashing with yours.

```text
rules/rewards/
├── pack.yml
└── rules.yml   → rewards:base, rewards:daily, rewards:notify
```

| `pack.yml` key | What it does |
|---|---|
| `name` | The pack name (default: the folder name). |
| `version` | `x.y.z`. |
| `description`, `author` | Free text. |
| `enabled` | `false` loads every rule of the pack disabled. |
| `params` | Values for `${name}` in every rule of the pack. |
| `requires` | `omnievents: ">=1.6.0"` and other packs with a version range. |
| `migrations` | Actions that run once when the pack is updated to a version. |

Inside a pack, a name without a pack (`call`, `cooldown()`, `uses()`, `in_state()`, `extends`) is looked up in the
pack first and then among the global rules. `/omni pack list|info|enable|disable` manages packs.

## Templates
A rule with `template: true` never runs by itself. Other rules copy it with `extends:` and fill its `${parameters}`
with `with:`.

```yaml
reward_base:
  template: true
  params: {amount: 1, wait: 1h}
  trigger: command
  cooldown: ${wait}
  actions:
    - set: "pvar.tokens = default(pvar.tokens, 0) + ${amount}"
    - message: "<green>+${amount} tokens"

daily_tokens:
  extends: reward_base
  with: {amount: 10, wait: 1d}
  command: dailytokens

hourly_tokens:
  extends: reward_base
  command: hourlytokens
```

- The keys the new rule writes replace the template's keys entirely.
- Parameters are looked up in `with`, then in `params` of the rule and its templates, then in `params` of the pack.
  An unknown `${parameter}` is a load error with a "did you mean…?". `\${` keeps a literal `${`.
- Templates can extend other templates, up to 8 levels.
