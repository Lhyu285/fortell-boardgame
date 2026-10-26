import { useState, useEffect } from "react";
import { buildBrassActionModel } from "./buildBrassActionModel.js";
import { brassRouteKey, brassSlotKey, buildCitySlotInfo, brassMapKey, buildRouteInfo } from "../helpers.jsx";

function useBrassActionDraft({ room, currentSeat, onAction, setMapInfo }) {
  const [selectedCards, setSelectedCards] = useState([]);
  const [selectedTileId, setSelectedTileId] = useState("");
  const [buildCity, setBuildCity] = useState("BIRMINGHAM");
  const [buildIndustry, setBuildIndustry] = useState("cotton_mill");
  const [buildSlotIndex, setBuildSlotIndex] = useState("");
  const [networkRoute, setNetworkRoute] = useState("");
  const [secondNetworkRoute, setSecondNetworkRoute] = useState("");
  const [selectedMerchantId, setSelectedMerchantId] = useState("");
  const [resourceSelections, setResourceSelections] = useState([]);
  const [resourceRequirementIndex, setResourceRequirementIndex] = useState(0);
  const [developIndustries, setDevelopIndustries] = useState([]);
  const [activeAction, setActiveAction] = useState("");
  const {
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
  } = buildBrassActionModel({ room, currentSeat, draft: { selectedCards, selectedTileId, buildCity, buildIndustry, buildSlotIndex, networkRoute, secondNetworkRoute, selectedMerchantId, resourceSelections, resourceRequirementIndex, developIndustries, activeAction } });
  useEffect(() => {
    setSelectedCards([]);
    setSelectedTileId("");
    setBuildSlotIndex("");
    setSecondNetworkRoute("");
    setSelectedMerchantId("");
    setResourceSelections([]);
    setResourceRequirementIndex(0);
    setDevelopIndustries([]);
    setActiveAction("");
  }, [state.round, state.currentPlayerId, state.turn?.actionsTaken]);

  function confirmed(message, type, payload = {}) {
    if (window.confirm(message)) {
      onAction(type, payload);
    }
  }

  function toggleCard(cardId) {
    const card = hand.find((item) => item.id === cardId);
    if (activeAction === "build" && card && card.type === "location" && !card.wild && card.key) {
      setBuildCity(card.key);
    }
    if (activeAction === "scout") {
      setSelectedCards((current) => current.includes(cardId)
        ? current.filter((id) => id !== cardId)
        : [...current, cardId]);
      return;
    }
    setSelectedCards((current) => current.includes(cardId) ? [] : [cardId]);
  }

  function toggleDevelopIndustry(industryType) {
    setDevelopIndustries((current) => {
      const count = current.filter((item) => item === industryType).length;
      if (count === 0) {
        return current.length >= 2 ? current : [...current, industryType];
      }
      if (count === 1) {
        return current.length >= 2 ? current.filter((item) => item !== industryType) : [...current, industryType];
      }
      return current.filter((item) => item !== industryType);
    });
  }

  function toggleDevelopTile(industryType, selectedInGroup = 0, maxSelectable = 2) {
    setDevelopIndustries((current) => {
      const currentCount = current.filter((item) => item === industryType).length;
      if (currentCount >= maxSelectable) {
        return current.filter((item) => item !== industryType);
      }
      if (selectedInGroup < maxSelectable && current.length < 2) {
        return [...current, industryType];
      }
      if (selectedInGroup > 0) {
        let removed = false;
        return current.filter((item) => {
          if (!removed && item === industryType) {
            removed = true;
            return false;
          }
          return true;
        });
      }
      return current;
    });
  }

  function chooseAction(action) {
    setActiveAction((current) => {
      const next = current === action ? "" : action;
      if (next !== "develop") {
        setDevelopIndustries([]);
      }
      return next;
    });
  }

  function clickBrassAction(action) {
    if (action === "restart_turn") {
      confirmed("是否重新开始本回合？本回合已执行的行动会被撤回。", "restart_turn");
      return;
    }
    if (action === "end_turn") {
      confirmed("是否结束本回合并将手牌补至8张？", "end_turn");
      return;
    }
    if (action === "maintain_era") {
      confirmed("是否执行时代维护？", "maintain_era");
      return;
    }
    chooseAction(action);
  }

  function cycleResourceSource(requirement, source) {
    if (!requirement || !source || source.disabled) return;
    const requirementIndex = resourceRequirementIndex;
    setResourceSelections((current) => {
      const matchesSource = (item) => item.sourceType === source.sourceType
        && String(item.sourceId) === String(source.id)
        && item.type === requirement.type;
      const currentItem = current.find((item) => item.requirementIndex === requirementIndex && matchesSource(item));
      const occupiedElsewhere = current
        .filter((item) => item.requirementIndex !== requirementIndex && matchesSource(item))
        .reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
      const selectedFromOtherSources = current
        .filter((item) => item.requirementIndex === requirementIndex && item.type === requirement.type && !matchesSource(item))
        .reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
      // Other requirements consume stock; other sources satisfy this requirement.
      const max = Math.min(
        Math.max(0, Number(source.available ?? 0) - occupiedElsewhere),
        Math.max(0, Number(requirement.amount ?? 0) - selectedFromOtherSources)
      );
      const currentAmount = Number(currentItem?.amount ?? 0);
      const nextAmount = currentAmount >= max ? 0 : currentAmount + 1;
      const next = current.filter((item) => !(item.requirementIndex === requirementIndex && matchesSource(item)));
      if (nextAmount > 0) {
        next.push({
          type: requirement.type,
          sourceType: source.sourceType,
          sourceId: source.id,
          requirementIndex,
          amount: nextAmount
        });
      }
      return next;
    });
  }

  function clearResourceRequirement(requirementIndex) {
    setResourceSelections((current) => current.filter((item) => item.requirementIndex !== requirementIndex));
  }

  useEffect(() => {
    if (selectableBuildCities.length > 0 && !selectableBuildCities.includes(buildCity)) {
      setBuildCity(selectableBuildCities[0]);
      setBuildSlotIndex("");
    }
  }, [selectableBuildCities, buildCity]);

  useEffect(() => {
    if (buildIndustryOptions.length > 0 && !buildIndustryOptions.includes(buildIndustry)) {
      setBuildIndustry(buildIndustryOptions[0]);
    }
  }, [buildIndustryOptions, buildIndustry]);

  useEffect(() => {
    if (activeAction !== "build") {
      setBuildSlotIndex("");
      return;
    }
    if (buildSlotIndex !== "" && !buildOptions.some((option) => option.city === buildCity
      && Number(option.slotIndex) === Number(buildSlotIndex)
      && option.industryType === activeBuildIndustry)) {
      setBuildSlotIndex("");
    }
  }, [activeAction, activeBuildIndustry, buildCity, buildOptions, buildSlotIndex]);

  useEffect(() => {
    if (activeAction !== "network") {
      setNetworkRoute("");
      setSecondNetworkRoute("");
      return;
    }
    if (networkRoute && !networkRouteOptions.some((route) => brassRouteKey(route) === networkRoute)) {
      setNetworkRoute("");
      setSecondNetworkRoute("");
      return;
    }
    if (secondNetworkRoute && !networkRoutePairOptions.some((pair) => (pair.routes ?? [])
      .some((route) => brassRouteKey(route) === secondNetworkRoute))) {
      setSecondNetworkRoute("");
    }
  }, [activeAction, networkRoute, networkRouteOptions, networkRoutePairOptions, secondNetworkRoute]);

  useEffect(() => {
    if (activeAction !== "sell") {
      setSelectedTileId("");
      setSelectedMerchantId("");
      return;
    }
    if (selectedTileId && !sellTileOptions.some((tile) => tile.id === selectedTileId)) {
      setSelectedTileId("");
      setSelectedMerchantId("");
      return;
    }
    if (selectedMerchantId && !sellMerchantSources.some((merchant) => merchant.id === selectedMerchantId)) {
      setSelectedMerchantId("");
    }
  }, [activeAction, selectedMerchantId, selectedTileId, sellMerchantSources, sellTileOptions]);

  useEffect(() => {
    setResourceSelections([]);
    setResourceRequirementIndex(0);
  }, [activeAction, resourceRequirementKey]);

  function selectMapCity(city) {
    setBuildCity(city);
    setBuildSlotIndex("");
  }

  function selectMapCitySlot(slot) {
    if (slot.kind === "market") {
      setMapInfo({
        title: `${slot.cityName}#${Number(slot.index ?? 0)}`,
        lines: [
          `城市名称：${slot.cityName}`,
          `市场信息：${slot.message}`,
          `贸易商啤酒：${slot.beerStatus}`
        ]
      });
      if (activeAction === "sell" && selectedSellOption) {
        const merchant = (selectedSellOption.merchantSources ?? []).find((source) => source.id === slot.merchantId);
        if (merchant) {
          setSelectedMerchantId((current) => current === merchant.id ? "" : merchant.id);
        }
      }
      return;
    }
    const builtTile = industries.find((tile) => brassSlotKey(tile.city, tile.slotIndex ?? 0) === brassSlotKey(slot.city, slot.slotIndex));
    setMapInfo(buildCitySlotInfo(slot, builtTile, mapCities, industries, cityNames));
    if (activeAction === "sell") {
      const sellOption = builtTile ? sellTileOptions.find((tile) => tile.id === builtTile.id) : null;
      if (sellOption) {
        setSelectedTileId((current) => current === sellOption.id ? "" : sellOption.id);
        setSelectedMerchantId("");
      }
      return;
    }
    const matchingBuildOptions = buildOptions.filter((option) => brassMapKey(option.city) === brassMapKey(slot.city)
      && Number(option.slotIndex) === Number(slot.slotIndex)
      && (!selectedCardId || (option.cardIds ?? []).includes(selectedCardId)));
    if (activeAction === "build" && (builtTile || matchingBuildOptions.length === 0)) {
      return;
    }
    const firstAllowed = (slot.allowedIndustryTypesBackend ?? [])
      .find((industryType) => matchingBuildOptions.some((option) => option.industryType === industryType))
      ?? matchingBuildOptions[0]?.industryType
      ?? slot.allowedIndustryTypesBackend?.[0];
    if (firstAllowed) {
      setBuildIndustry(firstAllowed);
    }
    const option = matchingBuildOptions.find((item) => item.industryType === firstAllowed) ?? matchingBuildOptions[0];
    if (option) {
      setBuildCity(option.city);
      setBuildSlotIndex(option.slotIndex ?? "");
    }
  }

  function selectMapRoute(route, second = false) {
    const key = brassRouteKey(route);
    setMapInfo(buildRouteInfo(route, links, mapCities, cityNames));
    if (activeAction !== "network") {
      return;
    }
    const isBuildableRoute = networkRouteOptions.some((item) => brassRouteKey(item) === key);
    if (!isCurrentPlayer || selectedCards.length !== 1 || !isBuildableRoute) {
      return;
    }
    if (networkRoute || secondNetworkRoute) {
      setNetworkRoute("");
      setSecondNetworkRoute("");
      return;
    }
    if (state.era === "rail" && networkRoute) {
      if (networkRoute === key) {
        setNetworkRoute("");
        setSecondNetworkRoute("");
        return;
      }
      if (secondNetworkRoute === key) {
        setSecondNetworkRoute("");
        return;
      }
      if (second || compatibleSecondRouteKeys.has(key)) {
        setSecondNetworkRoute(key);
        return;
      }
      setNetworkRoute(key);
      setSecondNetworkRoute("");
      return;
    }
    setNetworkRoute((current) => {
      const next = current === key ? "" : key;
      if (!next) {
        setSecondNetworkRoute("");
      }
      return next;
    });
  }
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
    developBlockReason, sellBlockReason, simpleActionReason, selectedCards,
    selectedTileId, buildCity, buildIndustry, buildSlotIndex,
    networkRoute, secondNetworkRoute, selectedMerchantId, resourceSelections,
    resourceRequirementIndex, developIndustries, activeAction, setSelectedCards,
    setSelectedTileId, setBuildCity, setBuildIndustry, setBuildSlotIndex,
    setNetworkRoute, setSecondNetworkRoute, setSelectedMerchantId, setResourceSelections,
    setResourceRequirementIndex, setDevelopIndustries, setActiveAction, confirmed,
    toggleCard, toggleDevelopIndustry, toggleDevelopTile, chooseAction,
    clickBrassAction, cycleResourceSource, clearResourceRequirement, selectMapCity,
    selectMapCitySlot, selectMapRoute
  };
}

export { useBrassActionDraft };
