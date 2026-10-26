import React from "react";
import { brassMapKey, brassRouteKey, brassSlotKey, marketSlotsForLayout, findRouteForLayout, brassLayoutRouteKey, routeTooltip, routeColorStyle, shortPlayerName, brassCityName, industrySlotTooltip, brassIndustryShortName, marketSlotTooltip } from "../helpers.jsx";
import birminghamMapLayout from "../../../assets/config/birmingham-map-layout.json";

function BrassMap({
  cities,
  routes,
  links,
  industries,
  activeAction,
  buildOptions = [],
  sellOptions = [],
  selectedSellTileId = "",
  selectedMerchantId = "",
  networkRouteOptions = [],
  compatibleSecondRouteKeys = new Set(),
  selectedCardId = "",
  selectedCard = null,
  selectedCity,
  selectedSlotKey,
  selectedRouteKey,
  selectedSecondRouteKey,
  resourceRequirement = null,
  resourceRequirementIndex = 0,
  resourceSelections = [],
  onResourceSourceClick,
  onCityClick,
  onCitySlotClick,
  onRouteClick,
  merchants = [],
  playerCount = 0,
  cityNames = {}
}) {
  const cityByName = new Map(cities.map((city) => [brassMapKey(city.name), city]));
  const linkedKeys = new Set((links ?? []).map(brassRouteKey));
  const builtBySlot = new Map((industries ?? []).map((tile) => [brassSlotKey(tile.city, tile.slotIndex ?? 0), tile]));
  const sellableTileIds = new Set((sellOptions ?? []).map((tile) => String(tile.id)));
  const selectedSellOption = (sellOptions ?? []).find((tile) => String(tile.id) === String(selectedSellTileId));
  const buildableSlots = new Set();
  for (const option of buildOptions) {
    if (selectedCardId && !(option.cardIds ?? []).includes(selectedCardId)) {
      continue;
    }
    const cityKey = brassMapKey(option.city);
    for (const slot of birminghamMapLayout.citySlots ?? []) {
      if (brassMapKey(slot.city) === cityKey
        && Number(slot.slotIndex) === Number(option.slotIndex)
        && (slot.allowedIndustryTypesBackend ?? []).includes(option.industryType)) {
        buildableSlots.add(brassSlotKey(slot.city, slot.slotIndex));
      }
    }
  }
  const cardHintSlots = new Set();
  if (selectedCardId && selectedCard && !selectedCard.wild) {
    const cardType = String(selectedCard.type ?? "");
    const cardKey = brassMapKey(selectedCard.key);
    if (cardType === "location" || cardType === "place") {
      for (const slot of birminghamMapLayout.citySlots ?? []) {
        if (brassMapKey(slot.city) === cardKey) {
          cardHintSlots.add(brassSlotKey(slot.city, slot.slotIndex));
        }
      }
    } else if (cardType === "industry") {
      const industryKey = String(selectedCard.key ?? "");
      for (const slot of birminghamMapLayout.citySlots ?? []) {
        if ((slot.allowedIndustryTypesBackend ?? []).includes(industryKey)) {
          cardHintSlots.add(brassSlotKey(slot.city, slot.slotIndex));
        }
      }
    }
  }
  const networkKeys = new Set(selectedCardId ? (networkRouteOptions ?? []).map(brassRouteKey) : []);
  const imageWidth = birminghamMapLayout.image?.width ?? 1247;
  const imageHeight = birminghamMapLayout.image?.height ?? 1242;
  const marketSlots = marketSlotsForLayout(birminghamMapLayout, merchants, playerCount);
  const resourceIndustrySources = new Map((resourceRequirement?.sources ?? [])
    .filter((source) => source.sourceType === "industry")
    .map((source) => [String(source.id), source]));
  const resourceMerchantSources = new Map((resourceRequirement?.sources ?? [])
    .filter((source) => source.sourceType === "merchant")
    .map((source) => [String(source.id), source]));
  const selectedResourceAmount = (source) => resourceSelections
    .filter((item) => item.sourceId === String(source?.id)
      && item.sourceType === source?.sourceType
      && item.requirementIndex === resourceRequirementIndex)
    .reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
  return (
    <div className="brass-map">
      <div className="brass-map-image-wrap">
        <img src={birminghamMapLayout.image.src} alt="伯明翰地图" />
        <svg viewBox={`0 0 ${imageWidth} ${imageHeight}`} preserveAspectRatio="xMidYMid meet" role="img" aria-label="伯明翰真实地图交互层">
        {(birminghamMapLayout.routes ?? []).map((layoutRoute) => {
          const route = findRouteForLayout(routes, layoutRoute);
          const key = route ? brassRouteKey(route) : brassLayoutRouteKey(layoutRoute);
          const selected = key === selectedRouteKey || key === selectedSecondRouteKey;
          const built = linkedKeys.has(key);
          const available = activeAction === "network" && (networkKeys.has(key) || compatibleSecondRouteKeys.has(key));
          const routeClassName = ["brass-map-route", selected ? "selected" : "", built ? "built" : "", available ? "available" : ""].filter(Boolean).join(" ");
          const points = layoutRoute.path ?? [];
          if (points.length < 2) return null;
          const pointString = points.map((point) => `${point.x * imageWidth},${point.y * imageHeight}`).join(" ");
          const mid = points[Math.floor(points.length / 2)];
          const builtLink = (links ?? []).find((link) => brassRouteKey(link) === key);
          const routeTitle = routeTooltip(route, layoutRoute, builtLink, cityByName, cityNames);
          return (
            <g key={key}>
              <polyline
                className={routeClassName}
                style={routeColorStyle(links, key)}
                points={pointString}
              >
                <title>{routeTitle}</title>
              </polyline>
              <polyline
                className="brass-map-route-hit"
                points={pointString}
                strokeWidth={layoutRoute.clickWidth ?? 14}
                onClick={() => route && onRouteClick(route)}
              />
              <text
                className="brass-map-route-label"
                x={mid.x * imageWidth}
                y={mid.y * imageHeight}
                onClick={() => route && onRouteClick(route)}
              >
                <title>{routeTitle}</title>
                {builtLink ? (
                  <>
                    <tspan x={mid.x * imageWidth} dy="-0.35em">{shortPlayerName(builtLink.ownerName)}</tspan>
                    <tspan x={mid.x * imageWidth} dy="1.15em">{builtLink.currentScore ?? 0}分</tspan>
                  </>
                ) : key === selectedSecondRouteKey ? "2" : ""}
              </text>
            </g>
          );
        })}
        {(birminghamMapLayout.citySlots ?? []).map((slot) => {
          const city = cityByName.get(brassMapKey(slot.city)) ?? { name: slot.city, cnName: brassCityName(cities, slot.city, cityNames) };
          const rect = slot.rect;
          const key = brassSlotKey(slot.city, slot.slotIndex);
          const builtTile = builtBySlot.get(key);
          const selected = key === selectedSlotKey;
          const available = activeAction === "build" && buildableSlots.has(key) && !builtTile;
          const sellable = activeAction === "sell" && builtTile && sellableTileIds.has(String(builtTile.id));
          const sellSelected = activeAction === "sell" && builtTile && String(builtTile.id) === String(selectedSellTileId);
          const resourceSource = builtTile ? resourceIndustrySources.get(String(builtTile.id)) : null;
          const resourceSelected = Boolean(resourceSource && selectedResourceAmount(resourceSource) > 0);
          const resourceAvailable = Boolean(resourceSource && Number(resourceSource.available ?? 0) > 0);
          const hinted = cardHintSlots.has(key);
          const className = ["brass-map-slot", selected || sellSelected || resourceSelected ? "selected" : "", hinted ? "hinted" : "", available || sellable || resourceAvailable ? "available" : "", builtTile ? "built" : "", builtTile?.flipped ? "flipped" : ""].filter(Boolean).join(" ");
          const slotTitle = industrySlotTooltip(slot, city, builtTile, cityNames);
          return (
            <g key={key} className={className} onClick={() => resourceSource
              ? onResourceSourceClick(resourceSource)
              : onCitySlotClick(slot)}>
              <rect
                x={rect.x * imageWidth}
                y={rect.y * imageHeight}
                width={rect.w * imageWidth}
                height={rect.h * imageHeight}
                rx="5"
              >
                <title>{slotTitle}</title>
              </rect>
              {builtTile ? <text textAnchor="middle" x={(rect.x + rect.w / 2) * imageWidth} y={(rect.y + rect.h / 2) * imageHeight}>
                <title>{slotTitle}</title>
                <tspan x={(rect.x + rect.w / 2) * imageWidth} dy="-0.35em">{shortPlayerName(builtTile.ownerName)}</tspan>
                <tspan x={(rect.x + rect.w / 2) * imageWidth} dy="1.15em">{brassIndustryShortName(builtTile.industryType, builtTile.industryName)}</tspan>
                {resourceSource && selectedResourceAmount(resourceSource) > 0 ? (
                  <tspan x={(rect.x + rect.w / 2) * imageWidth} dy="1.15em">选{selectedResourceAmount(resourceSource)}</tspan>
                ) : null}
              </text> : null}
            </g>
          );
        })}
        {marketSlots.map((slot) => {
          const rect = slot.rect;
          const title = marketSlotTooltip(slot);
          const merchantSelected = selectedMerchantId && selectedMerchantId === slot.merchantId;
          const resourceSource = resourceMerchantSources.get(String(slot.merchantId));
          const resourceSelected = Boolean(resourceSource && selectedResourceAmount(resourceSource) > 0);
          const merchantAvailable = activeAction === "sell" && selectedSellOption
            && (selectedSellOption.merchantSources ?? []).some((merchant) => merchant.id === slot.merchantId);
          return (
            <g key={slot.key} className={`brass-market-slot-overlay ${slot.status} ${merchantAvailable || resourceSource ? "available" : ""} ${merchantSelected || resourceSelected ? "selected" : ""}`}>
              <rect
                x={rect.x * imageWidth}
                y={rect.y * imageHeight}
                width={rect.w * imageWidth}
                height={rect.h * imageHeight}
                rx="6"
                onClick={() => merchantAvailable && !merchantSelected
                  ? onCitySlotClick(slot)
                  : resourceSource
                    ? onResourceSourceClick(resourceSource)
                    : onCitySlotClick(slot)}
              >
                <title>{title}</title>
              </rect>
              <text textAnchor="middle" x={(rect.x + rect.w / 2) * imageWidth} y={(rect.y + rect.h / 2) * imageHeight}>
                市
              </text>
            </g>
          );
        })}
      </svg>
      </div>
      <p className="muted-note">游戏地图</p>
    </div>
  );
}

function BrassMapInfoPanel({ info }) {
  if (!info) {
    return (
      <div className="brass-map-info">
        <strong>地图信息</strong>
        <span className="muted-note">点击任意城市产业格查看城市和格位信息。</span>
      </div>
    );
  }
  return (
    <div className="brass-map-info">
      <strong>{info.title}</strong>
      <div className="brass-map-info-grid">
        {info.lines.map((line, index) => (
          <span key={`${index}-${line}`}>{line}</span>
        ))}
      </div>
    </div>
  );
}

export { BrassMap, BrassMapInfoPanel };
