---
description: "Add your own functions, actions and properties from another plugin."
---

# Java API

Other plugins can extend OmniEvents through `OmniEventsAPI` and listen to `OmniRuleFireEvent`. Add OmniEvents as a
`softdepend` (or `depend`) in your `plugin.yml`.

## Functions, actions and properties
```java
// A function for expressions: double(x)
OmniEventsAPI.registerFunction("double(x)", "Twice x.", 1, 1, (ctx, a) -> Values.num(a[0]) * 2);

// An action for rules: - greet: hello
OmniEventsAPI.registerAction("greet", args -> Action.of(ctx -> ctx.requirePlayer().sendMessage("hello")));

// A property: player.clan in any expression
OmniEventsAPI.registerProperty(Player.class, "clan", p -> MyPlugin.clanOf(p));

// Run a rule with trigger: call, with arguments (var.amount)
OmniEventsAPI.callRule("reward", player, Map.of("amount", 5));
```

Functions and actions registered with a description show up in `/omni schema`, so the editor autocomplete knows them.

## `OmniRuleFireEvent`
Fired when a rule passes its conditions, limit, cooldown and cost, just before its actions. It cannot be cancelled.
It is asynchronous when the rule runs off the main thread (`player_chat`, `player_receive_chat`), and it is not fired
in simulations or `tests:`.

```java
@EventHandler
public void onRule(OmniRuleFireEvent e) {
    if (e.getRule().equals("rewards:daily") && e.getPlayer() != null) {
        getLogger().info(e.getPlayer().getName() + " claimed the daily reward");
    }
}
```
