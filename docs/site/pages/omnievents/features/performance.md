---
description: "Measure what each rule costs and get a warning when one gets slow."
---

# Performance

OmniEvents only listens to a Bukkit event if an enabled rule uses it, so rules you do not write cost nothing. For the
rules you do write, there are two tools.

## `/omni cpu`
`/omni cpu on` turns on a sampling profiler. `/omni cpu` then lists, from most to least expensive, each rule with its
average time per run (µs) and its estimated share of the main thread, its 3 most expensive conditions or actions, and
the cost per trigger. `off` stops it (the numbers are kept) and `reset` clears them. It measures 1 of every
`profiler.sample-every` runs of each rule (10 by default); when it is off it measures nothing.

## `budget`
```yaml
heavy_rule:
  trigger: player_move
  budget: 0.5ms
  conditions: "player.location.block.type == 'GOLD_BLOCK'"
  actions:
    - actionbar: "<gold>Standing on gold"
```

If the recent average of the rule goes over its budget, the console warns you, at most once every
`profiler.budget-warn-every` (5 minutes). The first 10 runs are ignored (Java warm-up). `/omni cpu` lists every rule
with a budget and whether it goes over, even with the profiler off.

## `/omni stats`
Runs, passes, errors, and average and maximum time per rule, plus the active listeners and tasks.

```yaml
# config.yml
profiler:
  sample-every: 10
  budget-warn-every: 5m
```
