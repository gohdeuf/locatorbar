# Locator Triangulator

**Version 0.2.0**  
**For Minecraft 26.3 · Fabric**

Locator Triangulator adds an on-screen display that helps you find other players using Minecraft's Locator Bar. It collects the directions shown by the Locator Bar over time and uses them to estimate where a player is.

## Features

- See estimated player locations, coordinates, and distance in an in-game HUD.
- Get helpful hints when you need more readings or should move to a different position.
- Move the HUD wherever it works best for you.
- Hide or show the HUD whenever you like.

## How to use

The server must have the Locator Bar enabled. When you receive directions to another player, the mod gathers readings automatically. Moving to different viewpoints helps improve the estimate.

- Press **J** to show or hide the HUD.
- Press **H** to enter or leave HUD editing mode. While editing, hold the left mouse button on the HUD and drag it to a new position.

Both key bindings can be changed under **Options → Controls → Key Binds → Miscellaneous**.

## Commands

- `/triangulator status` — Check the current tracking status.
- `/triangulator clear` — Clear saved tracking data.
- `/triangulator forget <id>` — Remove tracking data for one target.
- `/triangulator lizenz` — View the license in chat.

## Please play fair

This mod uses information sent by Minecraft's Locator Bar, but automated player tracking may still be against the rules on some servers. Please use it only in single-player, on your own server, or with the server administrator's permission.
