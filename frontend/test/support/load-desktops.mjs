import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { buildSync } from "esbuild";
export const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const require = createRequire(path.join(frontendRoot, "package.json"));
function compile(contents, resolveDir = path.join(frontendRoot, "src/pages")) {
  const built = buildSync({ stdin: { contents, resolveDir, sourcefile: "desktop-regression.jsx", loader: "jsx" },
    bundle: true, write: false, format: "cjs", platform: "node", jsx: "transform", logLevel: "silent",
    external: ["react", "react-dom", "react-router-dom"], loader: { ".js": "jsx" } });
  const output = { exports: {} };
  new Function("require", "module", "exports", built.outputFiles[0].text)(require, output, output.exports);
  return output.exports;
}
export function loadOriginal(names) {
  return compile(fs.readFileSync(path.join(frontendRoot, "src/pages/RoomPage.jsx"), "utf8") + `\nexport { ${names.join(", ")} };`);
}
export function loadMigrated() {
  return compile(`
    import * as brass from '../games/brass/BrassRoom.jsx';
    import * as brassHelpers from '../games/brass/helpers.jsx';
    import * as incan from '../games/incan-gold/IncanGoldRoom.jsx';
    import * as incanHelpers from '../games/incan-gold/helpers.jsx';
    import * as camel from '../games/camel-up-cards/CamelUpCardsRoom.jsx';
    import * as camelHelpers from '../games/camel-up-cards/helpers.jsx';
    import * as things from '../games/things-in-rings/ThingsInRingsRoom.jsx';
    import * as thingsHelpers from '../games/things-in-rings/helpers.jsx';
    import * as rps from '../games/rps/RpsRoom.jsx';
    import * as rpsHelpers from '../games/rps/helpers.jsx';
    export { brass, brassHelpers, incan, incanHelpers, camel, camelHelpers, things, thingsHelpers, rps, rpsHelpers };
  `);
}
export async function warmGameRooms(registry) {
  for (const game of registry.registeredGames()) {
    if (!game.Room) continue;
    await game.preloadRoom();
    // React-18 unit support only: settle lazy initialization before synchronous SSR/hook tests.
    try { game.Room._init(game.Room._payload); } catch (pending) {
      if (!pending || typeof pending.then !== "function") throw pending;
      await pending;
    }
    game.Room._init(game.Room._payload);
  }
  return registry;
}
export async function loadDispatcher() {
  const source = fs.readFileSync(path.join(frontendRoot, "src/pages/RoomPage.jsx"), "utf8");
  return warmGameRooms(compile(source + '\nexport { GameRoom, PrepareRoom, GameErrorBoundary };\nexport * from "../games/registry.js";'));
}
export function loadRegistry() {
  return compile(`export * from '../games/registry.js';
    export { createGameRegistry } from '../games/createGameRegistry.js';
    export { default as GameSettings } from '../features/rooms/GameSettings.jsx';
    export { default as GameDesktop } from '../features/rooms/GameDesktop.jsx';`);
}

export function loadRoomWorkflow() {
  return compile(`export * from '../features/rooms/roomSession.js';
    export * from '../features/rooms/roomCommands.js';
    export * from '../features/rooms/roomModel.js';
    export * from '../features/rooms/useRoomSession.js';
    export * from '../features/rooms/useRoomCommands.js';
    export * from '../games/registry.js';
    export { default as PrepareRoom } from '../features/rooms/PrepareRoom.jsx';`);
}

export function loadBrassDraft() {
  return compile(`export * from '../games/brass/draft/useBrassActionDraft.js';
    export * from '../games/brass/draft/buildBrassActionModel.js';
    export * from '../games/brass/components/BrassMap.jsx';
    export * from '../games/brass/components/BrassPlayers.jsx';
    export * from '../games/brass/components/BrassControlPanel.jsx';`);
}
