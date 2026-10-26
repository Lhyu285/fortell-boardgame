const players = [
  { userId: 1, username: "A", seatIndex: 0, color: "red", bankedTreasure: 8, roundTreasure: 3, artifacts: [] },
  { userId: 2, username: "B", seatIndex: 1, color: "blue", bankedTreasure: 4, roundTreasure: 2, artifacts: [] }
];
const seats = players.map(player => ({ seatIndex: player.seatIndex, isOccupied: true,
  currentUserSeat: player.userId === 1, user: { id: player.userId, username: player.username } }));
export function desktopProps(gameState = {}, config = {}, spectator = false) {
  return { room: { roomId: "123456", status: "IN_PROGRESS", currentUser: { id: spectator ? 99 : 1 },
    config, gameState: { players, currentPlayerId: 1, ...gameState }, notices: ["房间测试消息"] },
    seats, currentSeat: spectator ? null : seats[0], owner: !spectator,
    onAction() {}, onSubmitMove() {}, actionPending: false };
}
export function brassProps(patch = {}, spectator = false) {
  return desktopProps({ phase: "playing", era: "canal", round: 2, version: 10,
    turn: { actionsTaken: 0, actionsRemaining: 2, actionsAllowed: 2 },
    playerStats: { "1": { money: 30, incomeLevel: 10, income: 0, victoryPoints: 5 }, "2": { money: 25, incomeLevel: 10 } },
    hands: { "1": [{ id: "card-a", type: "location", key: "Birmingham", name: "伯明翰" }], "2": [{ hidden: true }] },
    playerBoards: { "1": { coal_mine: [], cotton_mill: [] }, "2": {} }, developments: {},
    initialTurnOrder: [1, 2], turnOrder: [1, 2], board: { industries: [], links: [], routes: [] },
    market: { coal: [1, 2], iron: [1, 2], beerMerchants: [] },
    availableActions: { actions: ["build", "develop", "network", "loan", "skip", "scout"],
      buildOptions: [], sellTiles: [], networkRoutes: [], networkRoutePairs: [], developIndustries: [], resourceSources: {} },
    ...patch }, {}, spectator);
}
export function incanProps(phase = "DECISION", patch = {}, spectator = false) {
  return desktopProps({ phase, round: 2, version: 10, activePlayers: [1, 2], players,
    currentCards: [{ id: "treasure-7", type: "treasure", value: 7 }],
    lastRevealedCard: { id: "treasure-7", type: "treasure", value: 7 }, deck: [{ type: "treasure", value: 2 }],
    roadTreasure: 1, roadArtifacts: [], pendingDecisions: {}, pendingTheftTargets: {},
    disasterSeen: { trap: 1 }, ...patch }, { thievesExpansion: true, eventsExpansion: true }, spectator);
}
export function thingsProps(phase = "GUESSER_TURN", patch = {}, spectator = false) {
  return desktopProps({ phase, hostPlayerId: 2, currentPlayerId: 1,
    playerHands: { "1": [{ id: "word-a", cn: "电脑", en: "computer" }], "2": [] },
    hostHand: [{ id: "word-host", cn: "图书馆", en: "library" }],
    placedWords: { SCENE: [{ id: "placed-a", cn: "雨", en: "rain" }] },
    rules: { SCENE: { description: "仅主持人知道的情境规则" }, WORD: {}, ATTRIBUTE: {} },
    ...patch }, { spectatorView: "player" }, spectator);
}
export function camelProps(phase = "PLAYING", patch = {}, spectator = false) {
  return desktopProps({ phase, leg: 1, finishPosition: 5, turn: { trackActionDone: false, betActionDone: false },
    players, hands: { "1": [{ id: "race-a", camelColor: "red", steps: 1 }], "2": [] },
    stacks: { "0": ["red", "blue"] }, trackTokens: {}, rankings: [],
    legBetMarket: {}, finalBets: {}, ...patch }, { expansion: "shortcut_fennec" }, spectator);
}
export function renderCases() {
  return [
    { name: "brass-playing", game: "brass", component: "BrassRoom", props: brassProps() },
    { name: "brass-spectator", game: "brass", component: "BrassRoom", props: brassProps({}, true) },
    { name: "brass-rail", game: "brass", component: "BrassRoom", props: brassProps({ era: "rail", round: 10 }) },
    { name: "brass-selling", game: "brass", component: "BrassRoom", props: brassProps({ turn: { actionsRemaining: 2, sellInProgress: true, sellCount: 1 } }) },
    { name: "brass-finished", game: "brass", component: "BrassRoom", props: brassProps({ phase: "finished", winners: [players[0]] }) },
    ...["EXPLORATION", "DECISION", "THEFT_TARGETS", "DISASTER", "FINISHED"].map(phase => ({
      name: `incan-${phase}`, game: "incan-gold", component: "IncanGoldRoom", props: incanProps(phase, {
        thieves: [1], targetCandidates: [2], rankings: players, winners: [1],
        currentEvent: { id: "triple_challenge", name: "三重挑战", description: "第三次灾难结束探险" }
      }) })),
    { name: "incan-spectator", game: "incan-gold", component: "IncanGoldRoom", props: incanProps("DECISION", {}, true) },
    ...["SELECTING_HOST", "HOST_INITIAL_PLACEMENT", "HOST_TURN", "GUESSER_TURN", "GAME_OVER"].map(phase => ({
      name: `things-${phase}`, game: "things-in-rings", component: "ThingsInRingsRoom", props: thingsProps(phase, { winners: [players[0]] }) })),
    { name: "things-host-judgment", game: "things-in-rings", component: "ThingsInRingsRoom", props: thingsProps("GUESSER_TURN", {
      hostPlayerId: 1, pendingGuess: { playerName: "B", word: { id: "guess-a", cn: "电脑", en: "computer" }, area: "SCENE" }
    }) },
    { name: "things-spectator", game: "things-in-rings", component: "ThingsInRingsRoom", props: thingsProps("GUESSER_TURN", {}, true) },
    ...["SETUP_SELECTION", "PLAYING", "FINISHED"].map(phase => ({
      name: `camel-${phase}`, game: "camel-up-cards", component: "CamelUpCardsRoom", props: camelProps(phase, { winners: [players[0]] }) })),
    { name: "camel-spectator", game: "camel-up-cards", component: "CamelUpCardsRoom", props: camelProps("PLAYING", {}, true) },
    ...[3, 5].flatMap(moveSet => ["free_for_all", "bracket"].map(mode => ({ name: `rps-${moveSet}-${mode}`, game: "rps", component: "GameRoom",
      props: { ...desktopProps({ phase: "playing", round: 2, activePlayers: [1, 2], submissions: {}, groups: [
        { groupId: "1", players, status: "playing" }
      ] }, { moveSet, mode }), gameType: "rps" } }))),
    { name: "rps-spectator", game: "rps", component: "GameRoom", props: { ...desktopProps({ phase: "playing", round: 2 }, {}, true), gameType: "rps" } }
  ];
}
