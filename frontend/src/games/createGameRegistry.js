import { lazy } from "react";

function copyDefaults(value) {
  if (Array.isArray(value)) return value.map(copyDefaults);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, copyDefaults(item)]));
  return value;
}

/** Only immutable game definitions and one code-loading promise per game are shared.
 * No room snapshots, sockets, users, selections or pending actions belong here.
 */
export function createGameRegistry(definitions) {
  const games = new Map();
  for (const definition of definitions) {
    if (!definition || typeof definition.key !== "string" || !definition.key.trim()) throw new Error("Game key is required");
    if (games.has(definition.key)) throw new Error(`Duplicate game key: ${definition.key}`);
    if (typeof definition.createDefaults !== "function") throw new Error(`Missing defaults: ${definition.key}`);
    if (definition.loadRoom != null && typeof definition.loadRoom !== "function") throw new Error(`Invalid desktop loader: ${definition.key}`);
    if (definition.desktopProps != null && typeof definition.desktopProps !== "function") throw new Error(`Invalid desktop props adapter: ${definition.key}`);
    for (const flag of ["versioned", "blockWhilePending"]) {
      if (definition.commands?.[flag] != null && typeof definition.commands[flag] !== "boolean") throw new Error(`Invalid command policy ${flag}: ${definition.key}`);
    }
    const { min, max } = definition.seatBounds ?? {};
    if (!Number.isInteger(min) || !Number.isInteger(max) || min < 1 || max < min) throw new Error(`Invalid seat bounds: ${definition.key}`);
    const { key, createDefaults, loadRoom } = definition;
    let modulePromise;
    const preloadRoom = loadRoom ? () => {
      // Cache code only, not a room-keyed store. Rejected loads reach the existing error boundary.
      modulePromise ??= Promise.resolve().then(loadRoom).then(module => {
        if (!module || typeof module.default !== "function") throw new Error(`Invalid game desktop: ${key}`);
        return module;
      });
      return modulePromise;
    } : null;
    games.set(definition.key, Object.freeze({
      key: definition.key, name: definition.name, entryTitle: definition.entryTitle ?? definition.name,
      seatBounds: Object.freeze({ min, max }), Settings: definition.Settings ?? null,
      commands: Object.freeze({ versioned: definition.commands?.versioned === true,
        blockWhilePending: definition.commands?.blockWhilePending === true }),
      Room: preloadRoom ? lazy(preloadRoom) : null, preloadRoom,
      desktopProps: definition.desktopProps ?? (props => props),
      createDefaults: () => copyDefaults(createDefaults())
    }));
  }
  const all = Object.freeze([...games.values()]);
  return Object.freeze({
    get: key => games.get(key) ?? null,
    all: () => all,
    createDefaults: key => games.get(key)?.createDefaults() ?? { seatCount: 4, config: {} }
  });
}
