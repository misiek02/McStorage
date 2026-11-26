# Copper Pipes

A lightweight item sorting system for **Minecraft 26.3 (Fabric)**. Copper pipes with glass walls connect your
chests; items visibly flow through them and are sorted into the right chests by filter cards.

Built to be far cheaper on the server than copper golems or hopper chains: no entities, no per-tick scanning of
containers, event-driven outlets and a cached pipe network.

## Features

- **Copper pipes** with a copper frame and glass walls; they connect automatically in all 6 directions.
  All four oxidation stages and their waxed variants: pipes weather like copper blocks, and oxidized pipes are
  slower (speed 1.0 / 0.7 / 0.45 / 0.25). An axe scrapes, honeycomb waxes.
- **Outlet connector**: pulls items out of the container it is attached to.
- **Inlet connector**: puts items into the container it is attached to. Holds one **sorting card**; an inlet
  without a card is an **overflow** inlet and takes whatever nothing else wants.
- **Sorting cards** with an editor: rules by **item**, **tag** (e.g. all logs) or **mod**, exclusions,
  priority 0-9, whitelist/blacklist, autocomplete, and a slot that shows any item's tags.
- **Visible items**: stacks are animated travelling through the glass, with the server's real timing.
- **Pipe wrench**: disconnect/reconnect a pipe side, rotate connectors, sneak to pick blocks up.
- Works with any container that supports the Fabric Transfer API (chests, barrels, furnaces, modded storage).
- English and Polish translations.

## How it works

1. Attach an **outlet connector** to the chest you want to empty (sneak + right-click the chest side).
2. Lay **copper pipes** from it to your storage chests.
3. Attach an **inlet connector** to each storage chest and put a **sorting card** in it (right-click the inlet
   with the card). Leave one inlet without a card as overflow.

Where an item goes:

1. inlets whose card matches it, **highest priority first**;
2. with equal priority, the **nearest** inlet;
3. if nothing matches (or matching chests are full), an **overflow** inlet;
4. if there is none, the item stays in the source chest.

If a pipe breaks while items travel through it, those items drop on the ground.

### Sorting card rules

Open the editor by right-clicking with a card. Pick the rule's kind, type a name (autocomplete helps; names are
checked against the game), then press **Add** or **Exclude**.

| Kind | Matches | Example |
|------|---------|---------|
| Item | one exact item | `minecraft:oak_log` |
| Tag  | a group of items | `minecraft:logs` = every kind of log |
| Mod  | everything from one mod | `minecraft` |

- **Exclude** (shown as NOT): never these items, even if another rule matches.
- **Priority 0-9**: higher priority chests are filled first.
- **Blacklist** flips the card: it accepts everything except what its rules match.
- Click an item into the grey slot to see its id, mod and tags as suggestions.
- Card + paper = two copies of the card. Editing a stack of cards changes only one of them.
- The `?` button next to the editor opens a legend.

### Pipe wrench

| Action | Effect |
|--------|--------|
| Right-click a pipe arm or side | disconnect / reconnect that side |
| Right-click a connector | turn it to the next side (sides with a container first) |
| Sneak + right-click | pick the pipe or connector up |

Placing a pipe while sneaking connects it only to the block you clicked, so parallel pipe lines stay separate.
Pipe ends get a copper cap.

## Recipes

| Result | Recipe |
|--------|--------|
| 8 Copper Pipes | 3 copper ingots (top row), 3 glass (middle row), 3 copper ingots (bottom row) |
| Outlet Connector | hopper above any copper pipe |
| Inlet Connector | any copper pipe above a hopper |
| Sorting Card | paper + copper ingot (shapeless) |
| 2 Sorting Cards (copy) | sorting card + paper (shapeless) |
| Pipe Wrench | copper ingots: `C C` / ` C ` / ` C ` |
| Waxed pipe | pipe + honeycomb (shapeless) |

## Settings

Client-side settings for the flowing item animation: **Options -> Copper Pipes...** or `/cpconfig`.

| Setting | Default | |
|---------|---------|---|
| Show items in pipes | on | purely visual; sorting works either way |
| Render distance | 32 blocks | up to 96 |
| Max visible items | 200 | the nearest ones are drawn |

Stored in `config/copperpipes-client.json`.

## Commands

| Command | Who | |
|---------|-----|---|
| `/copperpipes demo` | operators | builds a looping showcase sorting system next to you |
| `/copperpipes stats` | operators | pipe system cost (ms per tick), networks, awake outlets, items in transit |
| `/copperpipes stress <rigs> <length>` | operators | builds load-test rigs where items circulate forever |
| `/cpconfig [distance\|max\|render ...]` | anyone (client) | opens the settings screen or changes a setting |
| `/cpfps [seconds]` | anyone (client) | measures average / min / max FPS |

`demo` and `stress` overwrite blocks where they build; use them on empty, flat ground.

## Performance

- The pipe network is rebuilt (breadth-first search) only when a pipe or connector is placed, removed or changed,
  and only when it is next needed.
- Routes from each outlet are computed once per network (fastest route by travel time) and cached, together with
  the per-item list of allowed inlets. Card tags are expanded when a network is built, never per item.
- Outlets do not tick. Only outlets with work are processed; an outlet sleeps when it has nothing to send and is
  woken when its container or a container behind one of its inlets changes.
- Items in transit wait in a queue ordered by arrival time; each tick only its head is checked.
- The animation is client-only: one small packet per transferred stack.

Measured with `/copperpipes stress 30 40` plus 10 more rigs (80 outlets, about 1500 stacks in transit at once):
**about 0.15 ms per server tick** on average for the whole pipe system, out of the 50 ms tick budget.

## Building from source

Requires **Java 25**.

```sh
./gradlew build          # Windows: .\gradlew build
```

The jar is written to `build/libs/`. `build` also runs the Fabric gametests (network, sorting, priority,
overflow, drops on break, wrench, card copy, demo circulation) on a headless test server.

Optional: copy the jar straight into a launcher profile by creating `local.properties` with
`mods_dir=C:/path/to/profile/mods` and running `./gradlew installMod`.

Placeholder textures are generated by `java tools/GenTextures.java` and are meant to be replaced with real art.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API

Install the mod on both the server and every client (it adds blocks and items, so Fabric requires it on both
sides).

## License

[MIT](LICENSE) - free to use, modify and include in modpacks; keep the copyright notice. This covers the code
and the bundled (placeholder) textures.

---

## Po polsku (w skrócie)

Lekki system sortowania przedmiotów do Minecrafta 26.3 (Fabric). Miedziane rury ze szklanymi ściankami łączą
skrzynie, przedmioty widocznie płyną w środku i trafiają do właściwych skrzyń według kart sortujących.
Konektor wyjściowy wyciąga ze skrzyni, konektor wejściowy z kartą do niej wkłada, a wejście bez karty zbiera resztę
(overflow). Karty edytujesz prawym klikiem: reguły po przedmiocie, tagu albo modzie, wykluczenia, priorytet 0-9,
biała/czarna lista. Klucz do rur odłącza boki, obraca konektory i podnosi bloki. Ustawienia animacji są w
Opcje -> Miedziane rury..., a `/copperpipes demo` buduje pokazową sortownię.
