# RoboMinecraft

RoboMinecraft is a Fabric mod prototype inspired by RoboMaster. The MVP turns the player body into a first-person robot chassis instead of creating a rideable vehicle.

## MVP

- Players enter robot chassis mode by default after joining a world.
- Robot mode changes the player body through attributes: max HP, larger scale, chassis speed, step height, armor, and knockback resistance.
- Hero robots fire 42mm projectiles for 200 HP damage.
- Infantry robots fire 17mm projectiles for 20 HP damage.
- Robot contact deals 2 HP collision damage with a short cooldown.
- Shooting uses heat. Shots are blocked when the current heat plus shot heat would exceed the robot's heat limit.
- Heat cools every server tick according to the selected robot and launcher mode.
- Empty-hand right click fires from the player's view direction.
- Projectiles use a server-side ballistic simulation with gravity and air drag.
- The world scale is 1 real meter to 10 Minecraft blocks.
- 17mm projectiles are modeled as 17mm TPU spheres weighing 3.2g.
- 42mm projectiles are modeled as 42mm TPE spheres weighing 44.5g.
- Muzzle velocity affects drop and travel distance. Projectile simulation continues until impact, with only a far-distance server safety boundary.
- Infantry robots are modeled as 0.6m x 0.5m x 0.5m and 20kg.
- Hero robots are modeled as 0.7m x 0.6m x 0.6m and 25kg.
- Chassis movement speed is derived from each level's chassis power limit and robot mass.
- Robots cannot jump.
- Robots can climb slopes up to 40 degrees. With the 1:10 scale this maps to about 5.0 blocks for infantry and 5.9 blocks for hero robots.
- A lightweight first-person HUD appears while robot mode is active, with a white heat ring around the crosshair and a left-bottom HP bar.

## Robot Types

Hero robot:

- `/robomc hero melee` selects the melee-priority hero chassis.
- `/robomc hero ranged` selects the ranged-priority hero chassis.
- Hero HP and chassis power scale from level 1 to 10 using the 1/5/10 rule anchors.
- Hero heat limit scales from 140 to 240 in melee-priority mode, and from 100 to 130 in ranged-priority mode.

Infantry robot:

- `/robomc infantry power burst` selects power-priority chassis and burst-priority launcher.
- `/robomc infantry power cooling` selects power-priority chassis and cooling-priority launcher.
- `/robomc infantry health burst` selects health-priority chassis and burst-priority launcher.
- `/robomc infantry health cooling` selects health-priority chassis and cooling-priority launcher.
- Burst-priority launcher heat limit scales from 170 to 260, with 5-20 heat cooling per second.
- Cooling-priority launcher heat limit scales from 40 to 120, with 15-30 heat cooling per second.

## Test Commands

- `/robomc` prints the current robot status.
- `/robomc on`, `/robomc off`, and `/robomc toggle` control the mode for testing.
- `/robomc level <1-10>` directly sets the level.
- `/robomc xp set <0-5000>` sets experience and derives the level.
- `/robomc xp add <amount>` adds experience and derives the level.

## Requirements

- Minecraft `1.21.10`
- Fabric Loader `0.19.2` or newer
- Fabric API `0.138.4+1.21.10` or newer
- Fabric Language Kotlin `1.13.10+kotlin.2.3.20` or newer
- Java 21 or newer

## Development

Use Java 21 as the Gradle JVM in IntelliJ IDEA.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

The current MVP deliberately avoids a vehicle entity. Future work should replace the temporary HUD and attribute-only body feel with a dedicated mech player renderer, custom weapons, team logic, armor/heat systems, and RoboMaster-style objective rules.

## License

This project currently keeps the template's CC0 license.
