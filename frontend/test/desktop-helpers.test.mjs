import assert from "node:assert/strict";
import { test } from "node:test";
import { loadMigrated } from "./support/load-desktops.mjs";
const { brassHelpers: brass, thingsHelpers: things, rpsHelpers: rps, camelHelpers: camel, incanHelpers: incan } = loadMigrated();

const options = patch => ({ activeAction: "build", state: { market: { coal: [1], iron: [] } }, cityLabel: city => city,
  selectedBuildOption: { city: "Birmingham", coalCost: 0, ironCost: 0 }, buildCoalSources: [], buildIronSources: [], ...patch });
test("resource-free builds and non-resource actions still omit resource requirements", () => {
  assert.deepEqual(brass.buildBrassResourceRequirements(options({})), []);
  assert.deepEqual(brass.buildBrassResourceRequirements(options({ activeAction: "loan" })), []);
});
test("build coal and iron requirement order and legacy source identities remain stable", () => {
  const rows = brass.buildBrassResourceRequirements(options({ selectedBuildOption: { city: "Birmingham", coalCost: 1, ironCost: 2 },
    buildCoalSources: [{ id: "coal-slot", coal: 1 }], buildIronSources: [{ id: "iron-slot", iron: 2 }] }));
  assert.deepEqual(rows.map(row => [row.type, row.amount]), [["coal", 1], ["iron", 2]]);
  assert.equal(rows[0].sources[0].id, "coal-slot"); assert.equal(rows[0].sources.at(-1).id, "market:coal");
  assert.equal(rows[1].sources[0].id, "iron-slot"); assert.equal(rows[1].marketAvailable, 0);
});
test("double rail source lists retain ordered stage-specific coal then beer requirements", () => {
  const rows = brass.buildBrassResourceRequirements(options({ activeAction: "network", networkNeedsCoal: true, secondNetworkNeedsCoal: true,
    selectedRoute: { from: "Birmingham", to: "Dudley" }, selectedSecondRoute: { from: "Dudley", to: "Wolverhampton" },
    networkCoalSources: [{ id: "first-stage", coal: 1 }], secondNetworkCoalSources: [{ id: "second-stage", coal: 1 }],
    networkBeerSources: [{ id: "brewery-slot", beer: 1 }] }));
  assert.deepEqual(rows.map(row => [row.type, row.amount, row.sources[0].id]),
    [["coal", 1, "first-stage"], ["coal", 1, "second-stage"], ["beer", 1, "brewery-slot"]]);
});
test("single railway has one coal requirement and canals have no resource requirements", () => {
  const values = options({ activeAction: "network", networkNeedsCoal: true, secondNetworkNeedsCoal: false, networkCoalSources: [] });
  assert.deepEqual(brass.buildBrassResourceRequirements(values).map(row => row.type), ["coal"]);
  assert.deepEqual(brass.buildBrassResourceRequirements({ ...values, networkNeedsCoal: false }), []);
});
test("multi-tile development retains its iron quantity", () => {
  const rows = brass.buildBrassResourceRequirements(options({ activeAction: "develop", developNeedsIron: true, developAmount: 2,
    developIronSources: [{ id: "shared-iron", iron: 2 }] }));
  assert.equal(rows[0].type, "iron"); assert.equal(rows[0].amount, 2); assert.equal(rows[0].freeAvailable, 2);
});
test("selling keeps own/other brewery sources and filters merchant beer by selected market", () => {
  const rows = brass.buildBrassResourceRequirements(options({ activeAction: "sell", selectedSellOption: { city: "Birmingham", saleBeerCost: 3 },
    sellBeerSources: [{ id: "own-brewery", beer: 1 }, { id: "other-brewery", beer: 1 }],
    sellMerchantSources: [{ id: "merchant-oxford", city: "Oxford", beer: 1 }, { id: "merchant-other", city: "Other", beer: 1 }],
    selectedMerchantId: "merchant-oxford" }));
  assert.deepEqual(rows[0].sources.map(row => row.id), ["own-brewery", "other-brewery", "merchant-oxford"]);
});
test("source projection prefers authoritative candidate lists, including empty lists", () => {
  const industries = [{ id: "not-reachable", industryType: "coal_mine", coal: 3 }];
  assert.deepEqual(brass.resourceSourcesForSelect([], industries, "coal_mine", "coal"), []);
  assert.equal(brass.resourceSourcesForSelect(undefined, industries, "coal_mine", "coal")[0].id, "not-reachable");
});
test("RPS submissions stay hidden until the group finishes", () => {
  assert.equal(rps.moveDisplayFor({ userId: 2 }, { "2": "stone" }, {}, { status: "playing" }), "已完成选择");
  assert.equal(rps.moveDisplayFor({ userId: 2 }, { "2": "stone" }, {}, { status: "finished" }), "石头");
  assert.equal(rps.relationLines(false).length, 3); assert.equal(rps.relationLines(true).length, 5);
});
test("RPS bracket filtering and spectator group fallback remain unchanged", () => {
  const groups = [{ groupId: 1, players: [{ userId: 1 }], status: "playing" }, { groupId: 2, players: [{ userId: 2 }], status: "playing" }];
  assert.deepEqual(rps.visibleRpsGroups(groups, "1", "bracket"), [groups[0]]);
  assert.deepEqual(rps.visibleRpsGroups(groups, "99", "bracket"), groups);
});
test("Things word/rule display uses existing save field names and notes", () => {
  assert.equal(things.wordText({ Chinese: "电脑", English: "computer", note: "测试" }), "电脑/computer[测试]");
  assert.equal(things.wordText(null), ""); assert.equal(things.ruleText({ describe: "规则", note: "说明" }), "规则[说明]");
  assert.equal(things.THINGS_AREAS.length, 8);
});
test("Camel public token labels handle placed, disabled and unknown values", () => {
  assert.equal(camel.camelTokenAt({ shortcut: { status: "placed", position: 2 }, fennec: { status: "placed", position: 2 } }, 2), "捷径, 耳廓狐");
  assert.equal(camel.camelTokenStatusText("disabled"), "未启用"); assert.equal(camel.camelTokenAt({}, 0), "");
});
test("Incan base and triple-challenge phase labels retain their distinctions", () => {
  assert.equal(incan.incanDisasterText("trap"), "陷阱");
  assert.notEqual(incan.incanPhaseHint("EXPLORATION", "triple_challenge"), "");
  assert.equal(incan.incanPlayerName([{ userId: 1, username: "A" }], "1"), "A");
});
