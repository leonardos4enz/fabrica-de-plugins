# FAQ

### My rule does not run. What do I check?
1. `/omni validate`: if the rule has an error, it did not load. The message names the file and the key.
2. `/omni check <rule> <player>`: shows every condition with its value, and the cooldown and uses.
3. `/omni debug <rule>`, then do the thing in game: you see each run as it happens.
4. Check the quick filters: `worlds`, `permission`, `enabled: false`.

### My file breaks when a text starts with `{`
YAML reads a value that starts with `{` as a map. Quote texts with expressions: `message: "{player.name} joined"`.
Quoting every text and every condition is a good habit.

### How do I compare text without caring about uppercase?
Use `~=`: `player.world.name ~= 'World'`, or `lower(message) contains 'help'`.

### Can I use `and` and `or` in the same condition?
Yes, with parentheses when you need them: `(perm('rank.vip') || player.level >= 30) && !player.flying`.

### How do I run something every 10 minutes?
```yaml
announcer:
  trigger: timer
  every: 10m
  per_player: false
  actions:
    - broadcast: "<gold>Remember to vote: /vote"
```
`per_player: false` runs it once for the server instead of once for each player.

### How do I make my own command?
```yaml
spawn_command:
  trigger: command
  command: {name: spawn, aliases: [s]}
  cooldown: 5s
  actions:
    - teleport: "world;0;80;0"
    - message: "<green>Welcome to spawn."
```
The command is registered for real: it has tab completion and appears in `/help`. The arguments are in `args`
(`args[0]` is the first).

### Do OPs skip cooldowns?
No. Only players who are **given** `omnievents.bypasscooldown.<rule>` (or the rule's `cooldown_bypass`) skip them. Set
`cooldown-bypass-op: true` in `config.yml` if you want operators to skip every cooldown.

### Where are variables saved? Can I edit them by hand?
In `plugins/OmniEvents/data/`. Use `/omni var` while the server runs; edit the files only with the server stopped.

### Does it slow down my server?
A Bukkit event is only listened to if some enabled rule uses it. Use
`/omni cpu` to measure, and `budget:` to get a warning when a rule gets slow.

### The plugin's messages are in Spanish. Can I change them?
Yes: every message is in `plugins/OmniEvents/messages.yml`. Translate them and run `/omni reload`.

### Does it work on Spigot or Folia?
No. OmniEvents needs Paper (it uses Paper's Adventure API). Folia is not supported yet.

### Is there autocomplete for the YAML?
Yes, in VS Code with the JSON schema. See [installation](installation.md#autocomplete-in-vs-code).

### Where do I report a bug or ask for help?
Open an issue in the [repository](https://github.com/leonardos4enz/fabrica-de-plugins/issues). `/omni debug export`
creates a file with the latest traces and the rules involved. Check it before sharing, because it contains player
names and messages.
