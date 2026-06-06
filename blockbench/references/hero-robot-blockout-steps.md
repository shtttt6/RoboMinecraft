# Hero robot blockout steps

Reference:
[1号英雄机器人.png](C:\Users\shtttt\IdeaProjects\RoboMinecraft\blockbench\机器人\1号英雄机器人.png)

Target source file:
`blockbench/projects/hero_robot_v1.bbmodel`

## Goal

Build a first-pass blockout for the RoboMaster hero robot with a mecanum-wheel
chassis, readable turret, forward gun, and right-side mast.

## Project setup

- Format: generic Blockbench model for concepting
- Grid: `1`
- Mirror editing: on for left/right armor where useful
- Texture size for first pass: `64 x 64`

## Group structure

Create these groups in this order:

1. `hero_robot`
2. `base`
3. `wheel_fl`
4. `wheel_fr`
5. `wheel_rl`
6. `wheel_rr`
7. `front_bumper`
8. `rear_bumper`
9. `left_armor`
10. `right_armor`
11. `top_deck`
12. `turret_ring`
13. `turret_body`
14. `gun_mount`
15. `main_gun`
16. `sensor_mast`
17. `sensor_head`
18. `number_plate_left`
19. `number_plate_right`

## Blockout recipe

### 1. Base chassis

Create one cube in `base`:

- size: `40 x 6 x 44`
- role: main structural chassis

Then add one thinner upper support cube:

- size: `32 x 3 x 26`
- place it centered above the base

### 2. Mecanum wheels

For each of the four wheel groups, create one chunky wheel body:

- size target: `6 x 10 x 10`
- keep the wheel centers near the four corners of the base
- make sure the body clears the side armor visually

Optional first-pass detail:

- add 2 to 4 shallow diagonal cubes on the outer face to hint at mecanum rollers
- mirror the diagonal direction:
  - `wheel_fl` and `wheel_rr`: same slant
  - `wheel_fr` and `wheel_rl`: opposite slant

### 3. Front and rear bumper shapes

In `front_bumper`:

- add one low front cube spanning most of the width
- add two small corner cubes to suggest protective structure

In `rear_bumper`:

- add one low rear cube
- keep it slightly simpler than the front unless rear detail matters in-game

### 4. Side armor

In `left_armor` and `right_armor`:

- add one large outer armor plate per side
- target size per plate: `2 x 16 x 18`
- angle or position them so they read like hanging side armor rather than flush walls

Then add one smaller front-side plate on each side:

- target size: `2 x 10 x 10`
- this helps echo the bulky black lower body seen in the reference

### 5. Top deck

In `top_deck`:

- add one white upper deck cube: `30 x 4 x 28`
- center it above the base
- keep some visible overhang and layered separation from the lower body

Then add one rear-top utility cube:

- approximate size: `10 x 4 x 8`
- use it to imply the rear platform/equipment area

### 6. Turret ring

In `turret_ring`:

- add one centered low cube: `12 x 3 x 12`
- keep it visually distinct from the top deck

This is the rotation base if you animate later.

### 7. Turret body

In `turret_body`:

- add one main cube: `18 x 10 x 16`
- place it above the turret ring
- bias the mass slightly toward the front

Then add one upper housing cube:

- approximate size: `12 x 4 x 10`
- this helps imply the layered upper mechanical assembly

### 8. Gun mount

In `gun_mount`:

- add one compact front block: `8 x 6 x 8`
- attach it to the front of the turret body

### 9. Main gun

In `main_gun`:

- add one long barrel cube: `4 x 4 x 20`
- point it forward
- keep it high enough to clear the front armor

Optional:

- add one second thinner tip segment: `2 x 2 x 6`

### 10. Sensor mast

In `sensor_mast`:

- add one thin tall cube: `2 x 24 x 2`
- place it on the robot's right-rear area
- exaggerate height slightly for readability

In `sensor_head`:

- add one top cube: `4 x 4 x 4`
- center it on the mast

### 11. Number plates

In `number_plate_left` and `number_plate_right`:

- add one flat visible plate on each side
- target size: `1 x 12 x 12`
- these will hold the `1` marking in texture

## First-pass color assignment

- base frame: dark gray
- wheels: black with light gray roller hints
- side armor: black
- top deck: white
- turret body: dark gray and black
- gun: black
- mast: black
- number plates: black with white `1`

## Stop condition for v1

Version 1 is complete when all of these are true:

- the robot clearly reads as wheeled, not tracked
- all four mecanum wheels are visible
- the turret silhouette is readable
- the gun direction is obvious
- the right-side mast is obvious
- the side number plates read as hero armor

Do not add cables, mesh guards, or internal trusses before this point.
