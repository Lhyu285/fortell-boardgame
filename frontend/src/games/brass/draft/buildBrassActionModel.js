import { BRASS_PLAYER_COLORS, BRASS_CITIES, brassRouteKey, resourceSourcesForSelect, brassIndustryNames, buildBrassMapCities, brassCityName, buildBrassResourceRequirements, brassSlotKey } from "../helpers.jsx";

function buildBrassActionModel({ room, currentSeat, draft }) {
  const {
    selectedCards, selectedTileId, buildCity, buildIndustry,
    buildSlotIndex, networkRoute, secondNetworkRoute, selectedMerchantId,
    resourceSelections, resourceRequirementIndex, developIndustries, activeAction
  } = draft;
  const state = room.gameState ?? {};
  const currentUserId = String(room.currentUser?.id ?? "");
  const currentPlayerId = String(state.currentPlayerId ?? "");
  const isCurrentPlayer = currentSeat && currentUserId === currentPlayerId && state.phase === "playing";
  const hand = state.hands?.[currentUserId] ?? [];
  const turn = state.turn ?? {};
  const availableActionState = state.availableActions ?? {};
  const availableActions = availableActionState.actions ?? [];
  const sellInProgress = Boolean(availableActionState.sellInProgress || turn.sellInProgress);
  const sellCount = Number(availableActionState.sellCount ?? turn.sellCount ?? 0);
  const stats = state.playerStats ?? {};
  const developments = state.developments ?? {};
  const playerBoards = state.playerBoards ?? {};
  const players = state.players ?? [];
  const initialTurnOrder = (state.initialTurnOrder ?? []).length > 0
    ? state.initialTurnOrder
    : state.turnOrder ?? [];
  const currentPlayer = players.find((player) => String(player.userId) === String(currentPlayerId));
  const currentPlayerColor = BRASS_PLAYER_COLORS[currentPlayer?.color]?.css ?? "#8d2f2f";
  const mainActions = ["build", "sell", "network", "develop", "loan", "scout", "skip"];
  const industries = (state.board ?? {}).industries ?? [];
  const links = (state.board ?? {}).links ?? [];
  const routes = (state.board ?? {}).availableRoutes ?? [];
  const buildableCities = (state.board ?? {}).buildableCities?.length ? (state.board ?? {}).buildableCities : BRASS_CITIES;
  const citySlots = (state.board ?? {}).citySlots ?? {};
  const buildOptions = availableActionState.buildOptions ?? [];
  const sellTileOptions = availableActionState.sellTiles ?? [];
  const networkRouteOptions = availableActionState.networkRoutes ?? [];
  const networkRoutePairOptions = availableActionState.networkRoutePairs ?? [];
  const buildableCitiesFromActions = [...new Set(buildOptions.map((option) => option.city).filter(Boolean))];
  const selectableBuildCities = buildableCitiesFromActions;
  const buildIndustryOptions = [...new Set((citySlots[buildCity] ?? []).flatMap((slot) => Array.isArray(slot) ? slot : [slot]))]
    .filter((industryType) => buildOptions.some((option) => option.city === buildCity && option.industryType === industryType))
    .filter(Boolean);
  const activeBuildIndustry = buildIndustryOptions.includes(buildIndustry)
    ? buildIndustry
    : buildIndustryOptions[0] ?? buildIndustry;
  const selectedBuildOption = buildOptions.find((option) => option.city === buildCity
    && option.industryType === activeBuildIndustry
    && (buildSlotIndex === "" || Number(option.slotIndex) === Number(buildSlotIndex)))
    ?? buildOptions.find((option) => option.city === buildCity && option.industryType === activeBuildIndustry);
  const buildCompatibleCardIds = selectedBuildOption?.cardIds ?? [];
  const merchants = state.market?.beerMerchants ?? [];
  const playerCount = players.length;
  const availableRoutesForSelect = networkRouteOptions;
  const selectedRoute = availableRoutesForSelect.find((route) => brassRouteKey(route) === networkRoute) ?? null;
  const selectedSecondRoute = availableRoutesForSelect.find((route) => brassRouteKey(route) === secondNetworkRoute) ?? null;
  const selectedRoutePair = networkRoutePairOptions.find((pair) => {
    const pairRoutes = pair.routes ?? [];
    return pairRoutes.length === 2
      && brassRouteKey(pairRoutes[0]) === brassRouteKey(selectedRoute)
      && brassRouteKey(pairRoutes[1]) === brassRouteKey(selectedSecondRoute);
  });
  const compatibleSecondRouteKeys = new Set();
  if (state.era === "rail" && selectedRoute) {
    for (const pair of networkRoutePairOptions) {
      const pairRoutes = pair.routes ?? [];
      if (pairRoutes.length !== 2
        || brassRouteKey(pairRoutes[0]) !== brassRouteKey(selectedRoute)) {
        continue;
      }
      compatibleSecondRouteKeys.add(brassRouteKey(pairRoutes[1]));
    }
  }
  const selectedTile = industries.find((tile) => tile.id === selectedTileId);
  const selectedSellOption = sellTileOptions.find((tile) => tile.id === selectedTileId);
  const selectedMerchant = merchants.find((merchant) => merchant.id === selectedMerchantId);
  const selectedMerchantDevelopReward = selectedMerchant && String(selectedMerchant.reward ?? "").includes("Develop");
  const availableSellTiles = sellTileOptions;
  const buildCoalSources = resourceSourcesForSelect(selectedBuildOption?.coalSources, industries, "coal_mine", "coal");
  const buildIronSources = resourceSourcesForSelect(selectedBuildOption?.ironSources, industries, "iron_works", "iron");
  const developOption = (availableActionState.developIndustries ?? []).find((option) => option.industryType === developIndustries[0]);
  const developIronSources = resourceSourcesForSelect(developOption?.ironSources, industries, "iron_works", "iron");
  const networkCoalSources = resourceSourcesForSelect(selectedRoute?.coalSources, industries, "coal_mine", "coal");
  const secondNetworkCoalSources = resourceSourcesForSelect(
    (selectedRoutePair?.routes ?? []).find((route) => brassRouteKey(route) === brassRouteKey(selectedSecondRoute))?.coalSources,
    industries,
    "coal_mine",
    "coal"
  );
  const networkBeerSources = resourceSourcesForSelect(selectedRoutePair?.beerSources, industries, "brewery", "beer");
  const sellMerchantSources = selectedSellOption?.merchantSources ?? [];
  const selectedSellMarket = sellMerchantSources.find((merchant) => String(merchant.id) === String(selectedMerchantId));
  const sellBeerSources = resourceSourcesForSelect(
    selectedSellMarket?.beerSources,
    industries,
    "brewery",
    "beer"
  );
  const cityNames = state.board?.map?.cityNames ?? {};
  const industryNames = { ...brassIndustryNames(cityNames), ...(state.board?.map?.industryNames ?? {}) };
  const mapCities = buildBrassMapCities(state.board?.map?.cities ?? [], routes, buildableCities, cityNames);
  const cityLabel = (city) => brassCityName(mapCities, city, cityNames);
  const routeLabel = (route) => `${cityLabel(route.from)} - ${cityLabel(route.to)}`;
  const buildNeedsCoal = Number(selectedBuildOption?.coalCost ?? 0) > 0;
  const buildNeedsIron = Number(selectedBuildOption?.ironCost ?? 0) > 0;
  const networkNeedsCoal = state.era === "rail" && Boolean(selectedRoute);
  const secondNetworkNeedsCoal = state.era === "rail" && Boolean(selectedSecondRoute);
  const developNeedsIron = developIndustries.length > 0;
  const canSellSelectedTile = Boolean(selectedSellOption);
  const selectedCardId = selectedCards[0] ?? "";
  const resourceRequirements = buildBrassResourceRequirements({
    activeAction,
    state,
    selectedBuildOption,
    buildCoalSources,
    buildIronSources,
    networkNeedsCoal,
    secondNetworkNeedsCoal,
    selectedRoute,
    selectedSecondRoute,
    networkCoalSources,
    secondNetworkCoalSources,
    networkBeerSources,
    developNeedsIron,
    developAmount: developIndustries.length,
    developIronSources,
    selectedSellOption,
    sellBeerSources,
    sellMerchantSources,
    selectedMerchantId,
    cityLabel
  });
  const resourceRequirementKey = resourceRequirements
    .map((requirement) => `${requirement.type}:${requirement.amount}:${requirement.label}:${(requirement.sources ?? []).map((source) => `${source.sourceType}:${source.id}:${source.available}`).join(",")}`)
    .join("|");
  const activeResourceRequirement = resourceRequirements[resourceRequirementIndex] ?? resourceRequirements[0] ?? null;
  const selectedResourceAmount = activeResourceRequirement
    ? resourceSelections
      .filter((item) => item.requirementIndex === resourceRequirementIndex)
      .reduce((sum, item) => sum + Number(item.amount ?? 0), 0)
    : 0;
  const resourceSelectionPayload = resourceRequirements.length > 0
    ? { resourceSelections: resourceSelections.filter((item) => Number(item.amount ?? 0) > 0) }
    : {};
  const resourceBlockReason = resourceRequirements.some((requirement, index) => {
    const selected = resourceSelections
      .filter((item) => item.requirementIndex === index)
      .reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
    return selected !== Number(requirement.amount ?? 0);
  }) ? "请在地图上选择足量的资源来源" : "";
  const selectedBuildSlotKey = buildSlotIndex === "" ? "" : brassSlotKey(buildCity, buildSlotIndex);
  const buildBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
    : selectedCards.length !== 1
      ? "请选择1张手牌"
      : !selectedBuildOption
        ? "请选择合法建造目标"
        : buildCompatibleCardIds.length > 0 && !buildCompatibleCardIds.includes(selectedCardId)
          ? "所选手牌不能用于该建造"
          : resourceBlockReason;
  const networkBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
    : selectedCards.length !== 1
      ? "请选择1张手牌"
        : !selectedRoute
        ? "请选择路线"
        : state.era === "rail" && selectedSecondRoute && !selectedRoutePair
          ? "请选择合法的双路线组合"
          : resourceBlockReason;
  const developBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
      : selectedCards.length !== 1
      ? "请选择1张手牌"
      : developIndustries.length < 1
        ? "请选择1到2个产业"
        : resourceBlockReason;
  const sellBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
    : !sellInProgress && selectedCards.length !== 1
      ? "请选择1张手牌"
    : !canSellSelectedTile
      ? "请选择一个可出售的自有产业"
        : !selectedMerchantId
          ? "请在地图上选择售卖市场"
          : !sellMerchantSources.some((merchant) => merchant.id === selectedMerchantId)
            ? "所选贸易商不可用于该产业"
          : resourceBlockReason;
  const simpleActionReason = (() => {
    if (!["loan", "scout", "skip", "restart_turn", "end_turn", "maintain_era"].includes(activeAction)) {
      return "";
    }
    if (!isCurrentPlayer) {
      return "等待当前玩家行动";
    }
    if (activeAction === "loan" || activeAction === "skip") {
      return selectedCards.length === 1 ? "" : "请选择1张手牌";
    }
    if (activeAction === "scout") {
      return selectedCards.length === 3 ? "" : "请选择3张非万能手牌";
    }
    if (activeAction === "restart_turn") {
      return state.canMaintainEra ? "时代维护时不能撤回本回合" : "";
    }
    if (activeAction === "maintain_era") {
      return state.canMaintainEra ? "" : "尚未满足时代维护条件";
    }
    return "";
  })();
  return {
    state, currentUserId, currentPlayerId, isCurrentPlayer,
    hand, turn, availableActionState, availableActions,
    sellInProgress, sellCount, stats, developments,
    playerBoards, players, initialTurnOrder, currentPlayer,
    currentPlayerColor, mainActions, industries, links,
    routes, buildableCities, citySlots, buildOptions,
    sellTileOptions, networkRouteOptions, networkRoutePairOptions, buildableCitiesFromActions,
    selectableBuildCities, buildIndustryOptions, activeBuildIndustry, selectedBuildOption,
    buildCompatibleCardIds, merchants, playerCount, availableRoutesForSelect,
    selectedRoute, selectedSecondRoute, selectedRoutePair, compatibleSecondRouteKeys,
    selectedTile, selectedSellOption, selectedMerchant, selectedMerchantDevelopReward,
    availableSellTiles, buildCoalSources, buildIronSources, developOption,
    developIronSources, networkCoalSources, secondNetworkCoalSources, networkBeerSources,
    sellMerchantSources, selectedSellMarket, sellBeerSources, cityNames,
    industryNames, mapCities, cityLabel, routeLabel,
    buildNeedsCoal, buildNeedsIron, networkNeedsCoal, secondNetworkNeedsCoal,
    developNeedsIron, canSellSelectedTile, selectedCardId, resourceRequirements,
    resourceRequirementKey, activeResourceRequirement, selectedResourceAmount, resourceSelectionPayload,
    resourceBlockReason, selectedBuildSlotKey, buildBlockReason, networkBlockReason,
    developBlockReason, sellBlockReason, simpleActionReason
  };
}

export { buildBrassActionModel };
