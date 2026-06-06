# Hero robot model plan

Reference:
[1号英雄机器人.png](C:\Users\shtttt\IdeaProjects\RoboMinecraft\blockbench\机器人\1号英雄机器人.png)

## Core correction

This robot uses a mecanum-wheel chassis. Do not block it out as a tracked
vehicle.

## Modeling goal

Build a first-pass low-poly hero robot that is:

- clearly identifiable as a RoboMaster hero robot
- practical to texture in Blockbench
- easy to animate later if turret rotation is added

## Recommended model hierarchy

- `hero_robot`
- `base`
- `wheel_fl`
- `wheel_fr`
- `wheel_rl`
- `wheel_rr`
- `front_bumper`
- `rear_bumper`
- `left_armor`
- `right_armor`
- `top_deck`
- `turret_ring`
- `turret_body`
- `gun_mount`
- `main_gun`
- `sensor_mast`
- `sensor_head`
- `number_plate_left`
- `number_plate_right`

## Suggested first-pass dimensions

These are Blockbench-friendly proportions for a readable stylized model, not
real-world measurements.

- Overall footprint: `48w x 56l x 28h`
- Base frame: `40w x 44l x 6h`
- Top deck: `30w x 28l x 4h`
- Wheel diameter: `10`
- Wheel width: `6`
- Side armor plates: `12w x 16h x 2d`
- Turret ring: `12w x 12l x 3h`
- Turret body: `18w x 16l x 10h`
- Gun mount: `8w x 8l x 6h`
- Main gun body: `20l x 4w x 4h`
- Sensor mast: `2w x 2l x 24h`
- Sensor head: `4w x 4l x 4h`

## Part-by-part blocking order

1. Build the `base` as a low rectangular chassis.
2. Place the four mecanum wheels at the corners with visible clearance.
3. Add front and rear bumper shapes to make the silhouette less boxy.
4. Add the slanted side armor panels with large dark plates.
5. Add the `top_deck` as a raised white platform above the chassis.
6. Add the `turret_ring` centered on the deck.
7. Block the `turret_body` as a compact heavy module.
8. Extend the `main_gun` forward with a simple rectangular barrel first.
9. Add the right-side `sensor_mast` and `sensor_head`.
10. Add number plates and only then start secondary detail passes.

## Simplification rules

- Do not model every linkage in the turret.
- Do not model true mecanum rollers individually in the first pass.
- Treat cables as texture detail or very thin late-pass boxes.
- Use broad armor plates instead of exposed frame triangulation everywhere.
- Keep undersides simple unless the player will see the bottom often.

## Mecanum wheel treatment

To make the wheels read correctly without overspending polygons:

- Use a main wheel body per corner
- Add 4 to 6 diagonal roller hints around the visible face if needed
- Prefer diagonal dark/light striping in the wheel texture
- Angle the front-left and rear-right roller pattern one way
- Angle the front-right and rear-left roller pattern the opposite way

That mirrored pattern is the key visual cue that this is a mecanum chassis.

## Color plan

- Primary white: upper armor and deck
- Primary black: side armor, wheel housings, gun body
- Dark gray: structural frame, turret ring, mast
- Accent orange: wire or fastener highlights only
- Accent light gray: vents, grilles, and edge trims

## Texture plan

Start with a single `64x64` or `128x128` texture atlas.

Recommended priority:

1. White armor panels
2. Black armor panels
3. Wheel texture with diagonal mecanum cues
4. Gray metal trim
5. Number `1` decals

## Export strategy

If this becomes a custom rendered entity later, keep the editable `.bbmodel`
source stable and export render assets only after the geometry is approved.

If you only need concept iteration for now, stop after:

- blockout
- basic colors
- number plates
- one clean screenshot pass

## Immediate next modeling pass

The first hands-on pass should produce:

- complete chassis
- four recognizable mecanum wheels
- readable turret
- readable gun silhouette
- readable right-side mast

Ignore fine internals until that silhouette looks right from:

- front 3/4
- side
- top
