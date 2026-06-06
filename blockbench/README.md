# Blockbench workspace for RoboMinecraft

This folder keeps Blockbench source files close to the mod while staying out of
the repository root.

## Folder layout

- `projects/`: saved `.bbmodel` source files that should be versioned
- `references/`: screenshots, sketches, and design notes
- `exports/models/`: temporary exports before copying into the mod assets
- `exports/textures/`: temporary texture exports before copying into the mod assets
- `prompts/`: reusable prompt templates for Codex + Blockbench MCP

## MCP setup

1. Install the Blockbench desktop plugin from:
   `https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js`
2. In Blockbench, open `Settings > General`.
3. Set:
   - `MCP Server Port` = `3000`
   - `MCP Server Endpoint` = `/bb-mcp`
4. Keep Blockbench running while using Codex to edit models through MCP.

## Export targets for this mod

Namespace: `robominecraft`

- Item/block model JSON: `src/main/resources/assets/robominecraft/models/`
- Textures: `src/main/resources/assets/robominecraft/textures/`

If you later add custom entity rendering code, keep the `.bbmodel` source here
and export generated Java/model assets into the matching client render package.

## Recommended workflow

1. Save the editable model in `projects/`.
2. Export preview output into `exports/` first.
3. Copy finalized files into `src/main/resources/assets/robominecraft/`.
4. Commit both the final asset and the source `.bbmodel` file.
