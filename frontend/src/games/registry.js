import { createGameRegistry } from "./createGameRegistry.js";
import brass from "./brass/index.js";
import incan from "./incan-gold/index.js";
import camel from "./camel-up-cards/index.js";
import things from "./things-in-rings/index.js";
import rps from "./rps/index.js";
import gobang from "./gobang/index.js";

const registry = createGameRegistry([brass, incan, camel, things, rps, gobang]);
export const getGameDefinition = registry.get;
export const registeredGames = registry.all;
export const createGameDefaults = registry.createDefaults;
export function gameName(key, entry = false) {
  const game = registry.get(key);
  return (entry ? game?.entryTitle : game?.name) ?? key;
}
export function gameSeatBounds(key) { return registry.get(key)?.seatBounds ?? { min: 2, max: 2 }; }
export function preloadGameRoom(key) { return registry.get(key)?.preloadRoom?.() ?? Promise.resolve(null); }
