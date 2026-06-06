# Hero robot v1 prompt

Reference image:
`C:\Users\shtttt\IdeaProjects\RoboMinecraft\blockbench\机器人\1号英雄机器人.png`

## Intent

Create a Minecraft-friendly low-poly model inspired by the RoboMaster hero
robot. Prioritize silhouette readability and recognizable part layout over
mechanical 1:1 accuracy.

## Non-negotiable features

- Use a mecanum-wheel chassis, not tracks
- Keep the tall turret and forward main gun
- Keep the right-side sensor mast
- Keep the black/white armor identity with a visible `1` plate
- Preserve the feeling of a heavy shooter platform

## Style targets

- Industrial, practical, competition-robot feel
- Clean hard-surface volumes
- Medium detail at close range, readable from far away
- Most fine mechanisms simplified into larger blocks

## Geometry guidance

- Wheels: model four main mecanum wheels as chunky cylinders or octagons
- Rollers: imply them with texture or shallow repeated bevel-like blocks
- Armor: use flat plates and wedge-like silhouettes instead of dense internals
- Turret: separate base ring, body, and gun into clean groups
- Mast: make it thin but slightly exaggerated so it reads in Minecraft

## Save / export expectations

- Save editable source into `blockbench/projects/hero_robot_v1.bbmodel`
- Keep texture work under `blockbench/exports/textures/`
- Keep temporary model exports under `blockbench/exports/models/`
- Final game assets should later move into `src/main/resources/assets/robominecraft/`
