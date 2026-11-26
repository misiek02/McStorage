# Roadmap

Released: **0.1.0** (all 10 original milestones). Ideas below are grouped by release and ordered by value;
nothing here is final.

## 0.2.0 — Control and insight

- [ ] **Redstone control for outlets**: a powered outlet stops sending (like a hopper). It wakes again when
      the signal is removed.
- [ ] **Comparator output on inlets**: signal strength from how full the container behind the inlet is.
- [ ] **Network inspection with the wrench**: right-clicking shows the outlets and inlets in the network,
      the items in transit, and where the held item would go.
- [ ] **Sorting card tooltip**: rules, priority and mode shown in the inventory without opening the editor.

## 0.3.0 — More sorting options

- [ ] **Outlet filter**: an outlet can hold a card and then pulls only the matching items (e.g. only coal from
      a furnace output).
- [ ] **Round-robin mode**: with equal priority, spread items over the inlets in turn instead of always
      filling the nearest one first.
- [ ] **Outlet upgrades**: speed upgrade and stack upgrade (send a whole stack at once).

## 0.4.0 — Look and feel

- [ ] **Real textures** to replace the generated placeholders (`tools/GenTextures.java`): pipe frame and glass
      for all oxidation stages, connectors, sorting card and wrench.
- [ ] **Sounds**: inserting a card, using the wrench, items arriving.
- [ ] **Particles** when a pipe with items inside is broken.
- [ ] Mod icon.

## 1.0.0 — Public release

- [ ] **GitHub Actions**: build and run the gametests on every push, attach the jar to a release on a `v*` tag.
- [ ] **Modrinth page**: description, screenshots, gallery, automatic upload from CI.
- [ ] **Recipe viewer support** (EMI / JEI / REI): recipes, and dragging items into the card's ghost slot.
- [ ] **More gametests**: networks across chunk borders with unload/load, items in transit across a server
      restart, modded containers through the Transfer API.

## Maybe later

- Fluid pipes on the same network code.
- A "request" terminal: pick an item from all connected chests and have it delivered.
- Item counts per chest in the network inspection view.
