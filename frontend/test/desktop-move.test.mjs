import assert from "node:assert/strict";
import { test } from "node:test";
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { parse } from "@babel/parser";
import traverseModule from "@babel/traverse";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { frontendRoot, loadMigrated, loadDispatcher } from "./support/load-desktops.mjs";
import { renderCases } from "./support/desktop-fixtures.mjs";

const traverse = traverseModule.default ?? traverseModule;
const baseline = JSON.parse(fs.readFileSync(new URL("./fixtures/desktop-move-baseline.json", import.meta.url), "utf8"));
const brassBaseline = JSON.parse(fs.readFileSync(new URL("./fixtures/brass-desktop-baseline.json", import.meta.url), "utf8"));
const hash = text => createHash("sha256").update(text.replaceAll("\r\n", "\n")).digest("hex");
const sources = new Map();
function read(file) {
  if (!sources.has(file)) {
    const code = fs.readFileSync(path.join(frontendRoot, file), "utf8");
    const ast = parse(code, { sourceType: "module", plugins: ["jsx"] });
    const declarations = new Map();
    for (const statement of ast.program.body) {
      const node = statement.type.startsWith("Export") ? statement.declaration : statement;
      if (!node) continue;
      if (node.type === "FunctionDeclaration" || node.type === "ClassDeclaration") declarations.set(node.id.name, node);
      if (node.type === "VariableDeclaration") for (const item of node.declarations) declarations.set(item.id.name, node);
    }
    sources.set(file, { code, ast, declarations });
  }
  return sources.get(file);
}

// Step 2 intentionally replaces the shell/dispatcher contract; keep original game-body expectations.
// Settings were moved again, but their original hashes remain authoritative (not regenerated).
// GAME_LABELS was retired after confirming no callers; registered names are covered separately.
const settingsFiles = { RpsSettings: "rps", ThingsSettings: "things-in-rings", CamelSettings: "camel-up-cards", GobangSettings: "gobang", IncanGoldSettings: "incan-gold" };
const roomFiles = { GameErrorBoundary: "src/features/rooms/GameErrorBoundary.jsx", createClientActionId: "src/features/rooms/roomCommands.js",
  roomCurrentPlayerName: "src/features/rooms/roomModel.js", seatListOf: "src/features/rooms/roomModel.js",
  hasCurrentUserProposed: "src/features/rooms/roomModel.js", isFinishedGameState: "src/features/rooms/roomModel.js" };
// BrassMap's merchant click priority is now an intentional behavior fix, covered by interaction tests.
for (const original of baseline.declarations.filter(item => !["RoomPage", "PrepareRoom", "labelFor", "GAME_LABELS", "BrassRoom", "BrassMap"].includes(item.name))) {
  const item = { ...original, file: settingsFiles[original.name] ? `src/games/${settingsFiles[original.name]}/Settings.jsx`
    : original.name === "SettingsToggle" ? "src/features/rooms/SettingsToggle.jsx" : brassBaseline.locations[original.name] ?? roomFiles[original.name] ?? original.file };
  test(`mechanical parity: ${item.name}`, () => {
    const { code, declarations } = read(item.file);
    const node = declarations.get(item.name);
    assert.ok(node, `${item.name} must exist in ${item.file}`);
    assert.equal(hash(code.slice(node.start, node.end)), item.hash, `${item.name} changed during a move-only migration`);
  });
}
for (const [file, digest] of Object.entries(baseline.untouched)) {
  if (file === "src/pages/GameLandingPage.jsx") continue; // Defaults/title now come from the registry; covered by stage-2 contracts.
  test(`unchanged platform/style boundary: ${file}`, () => assert.equal(hash(fs.readFileSync(path.join(frontendRoot, file), "utf8")), digest));
}
test("RPS presentation and selection effects retain original statements and lifecycle", () => {
  const { code, declarations } = read("src/games/rps/RpsRoom.jsx");
  const statements = nodes => nodes.map(node => code.slice(node.start, node.end)).join("\n  ");
  assert.equal(hash(statements(declarations.get("RpsRoom").body.body.slice(1))), baseline.rpsBodyHash);
  const selection = read("src/games/rps/useRpsSelection.js");
  const hooks = selection.declarations.get("useRpsSelection").body.body.slice(0, 2);
  assert.equal(hash(hooks.map(node => selection.code.slice(node.start, node.end)).join("\n  ")), baseline.rpsHookHash);
  const desktop = read("src/features/rooms/GameDesktop.jsx");
  const source = desktop.code;
  assert.ok(source.indexOf("useRpsSelection(props.room.gameState") < source.indexOf("const Room = game?.Room"));
});

// Detect the missing-helper failures that builds alone do not catch (including JSX names).
const globals = new Set(["undefined", "NaN", "Infinity", "Array", "Object", "String", "Number", "Boolean", "Math", "JSON", "Date", "Map", "Set",
  "window", "document", "console", "setTimeout", "clearTimeout", "Error", "Intl", "Promise"]);
const extractedFiles = [...new Set(baseline.declarations.map(item => settingsFiles[item.name] ? `src/games/${settingsFiles[item.name]}/Settings.jsx`
  : item.name === "SettingsToggle" ? "src/features/rooms/SettingsToggle.jsx" : brassBaseline.locations[item.name] ?? roomFiles[item.name] ?? item.file))].filter(file => file !== "src/pages/RoomPage.jsx");
for (const file of Object.values(brassBaseline.locations)) if (!extractedFiles.includes(file)) extractedFiles.push(file);
extractedFiles.push("src/games/rps/useRpsSelection.js", "src/features/rooms/GameDesktop.jsx", "src/features/rooms/GameSettings.jsx");
extractedFiles.push("src/games/rps/RpsRoom.jsx");
extractedFiles.push("src/pages/RoomPage.jsx");
for (const module of ["PrepareRoom.jsx", "GameErrorBoundary.jsx", "roomModel.js", "roomSession.js", "roomCommands.js", "useRoomSession.js", "useRoomCommands.js"]) {
  if (!extractedFiles.includes(`src/features/rooms/${module}`)) extractedFiles.push(`src/features/rooms/${module}`);
}
extractedFiles.push("src/pages/GameLandingPage.jsx", "src/games/registry.js", "src/games/createGameRegistry.js");
for (const game of ["brass", "incan-gold", "camel-up-cards", "things-in-rings", "rps", "gobang"]) extractedFiles.push(`src/games/${game}/index.js`);
for (const file of extractedFiles) {
  test(`module scope and dependency isolation: ${file}`, () => {
    const { ast } = read(file); const unknown = new Set();
    traverse(ast, {
      ReferencedIdentifier(binding) {
        if (!binding.scope.getBinding(binding.node.name) && !globals.has(binding.node.name)) unknown.add(binding.node.name);
      },
      JSXOpeningElement(binding) {
        let name = binding.node.name;
        while (name.type === "JSXMemberExpression") name = name.object;
        if (/^[A-Z]/.test(name.name) && !binding.scope.getBinding(name.name)) unknown.add(name.name);
      },
      ImportDeclaration(binding) {
        const target = binding.node.source.value;
        assert.ok(!target.includes("pages/RoomPage"), "Modules must never import their room shell");
        if (file.startsWith("src/games/")) {
          assert.ok(!/lib\/(api|websocket)|react-router/.test(target), "Game desktops must not own room transport/navigation");
          if (target.startsWith(".")) {
            const absolute = path.resolve(path.dirname(file), target).replaceAll("\\", "/");
            for (const game of file === "src/games/registry.js" ? [] : ["brass", "incan-gold", "camel-up-cards", "things-in-rings", "rps", "gobang"]) {
              if (absolute.includes(`/games/${game}/`)) assert.ok(file.includes(`/games/${game}/`), "No cross-game dependency");
            }
          }
        }
      }
    });
    assert.deepEqual([...unknown], [], "Unbound component/helper identifiers");
  });
}

test("room shell, shared panels and game modules have an acyclic import graph", () => {
  const graph = new Map(extractedFiles.map(file => [file, read(file).ast.program.body
    .filter(node => node.type === "ImportDeclaration" && node.source.value.startsWith("."))
    .map(node => path.posix.normalize(path.posix.join(path.posix.dirname(file), node.source.value)))]));
  const visited = new Set();
  function visit(file, stack = []) {
    assert.ok(!stack.includes(file), `Import cycle: ${[...stack, file].join(" -> ")}`);
    if (visited.has(file)) return;
    for (const dependency of graph.get(file) ?? []) if (graph.has(dependency)) visit(dependency, [...stack, file]);
    visited.add(file);
  }
  for (const file of graph.keys()) visit(file);
});

const modules = loadMigrated();
const dispatcher = await loadDispatcher();
const byGame = { brass: modules.brass, "incan-gold": modules.incan, "camel-up-cards": modules.camel, "things-in-rings": modules.things, rps: dispatcher };
for (const scenario of renderCases()) {
  test(`render parity: ${scenario.name}`, () => {
    const before = JSON.stringify(scenario.props.room);
    const html = renderToStaticMarkup(React.createElement(byGame[scenario.game][scenario.component], scenario.props));
    assert.equal(hash(html), baseline.markup[scenario.name], "Markup, class names, visible information or control state changed");
    assert.equal(JSON.stringify(scenario.props.room), before, "Rendering must not mutate room snapshots");
  });
}

test("dispatcher still maps each existing game and retains the unimplemented-game placeholder", () => {
  const props = renderCases()[0].props;
  for (const [gameType, game] of [["brass", modules.brass.BrassRoom], ["incan_gold", modules.incan.IncanGoldRoom],
    ["camel_up_cards", modules.camel.CamelUpCardsRoom], ["thingsInRings", modules.things.ThingsInRingsRoom]]) {
    const viaDispatcher = renderToStaticMarkup(React.createElement(dispatcher.GameRoom, { ...props, gameType }));
    assert.ok(viaDispatcher.length > 0);
    assert.equal(viaDispatcher, renderToStaticMarkup(React.createElement(game, props)));
  }
  const unavailable = renderToStaticMarkup(React.createElement(dispatcher.GameRoom, { ...props, gameType: "gobang" }));
  assert.ok(unavailable.includes("游戏界面预留中"));
});
