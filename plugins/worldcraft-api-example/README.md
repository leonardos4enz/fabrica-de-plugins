# WorldcraftApiExample

A small plugin that shows how to use [Worldcraft](../worldcraft/README.md)'s API (spec
[037](../../docs/sdd/specs/037-worldcraft-api/spec.md)). It is an example for plugin developers, and the factory's
in-game suite uses it to test the API. Read its one Java file:
[`WorldcraftApiExample.java`](src/main/java/dev/leo/wcexample/WorldcraftApiExample.java).

## What it does
```
/wcx selection                                  your Worldcraft selection, read through the API
/wcx select 0 60 0 10 70 10                     select a box for you (you see its outline)
/wcx fill 50% stone, 50% andesite               fill your selection AS YOU: your limits, your history (/wc undo works)
/wcx box world 0 60 0 10 70 10 air              an edit of the plugin itself ("#WorldcraftApiExample" in /wc history)
/wcx paste castle 100 64 100 90                 paste a schematic of Worldcraft's schematics folder, turned 90°
/wcx undo                                       undo the plugin's last edit
/wcx reload                                     read config.yml again
```
`config.yml`:
```yaml
protected:              # no Worldcraft edit may touch these areas (commands, brushes, tools, other plugins, undo)
  spawn:
    world: world
    from: [-50, -64, -50]
    to: [50, 319, 50]
log_edits: true         # write every finished Worldcraft edit to the console
```
A player who runs `/wc set stone` inside a protected area sees "Another plugin stopped this edit: This area is
protected by WorldcraftApiExample (spawn)".

## Using the API in your plugin
1. `plugin.yml`: `depend: [Worldcraft]` (or `softdepend` and check that it is enabled).
2. Compile against Worldcraft's jar: `compileOnly(files("libs/Worldcraft-0.13.0.jar"))` (here in the factory:
   `compileOnly(project(":worldcraft"))`).
3. `WorldcraftApi.get()`, then `edit(this)…` and listeners for `WorldcraftEditEvent` and `WorldcraftEditDoneEvent`.
   The details are in [Worldcraft's README](../worldcraft/README.md#for-plugin-developers).

## Compatibility
Paper 1.21.4 (verified with Worldcraft 0.13.0, 2026-11-02, in the in-game suite). Java 21. Permission
`worldcraftexample.use` (op by default).
