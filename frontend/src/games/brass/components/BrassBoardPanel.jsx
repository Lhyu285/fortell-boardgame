import React from "react";
import { BrassMap, BrassMapInfoPanel } from "./BrassMap.jsx";
import { BrassMarket } from "./BrassMarket.jsx";

function BrassBoardPanel({
  mapCities, routes, links, industries,
  merchants, playerCount, activeAction, buildOptions,
  sellTileOptions, selectedTileId, selectedMerchantId, networkRouteOptions,
  compatibleSecondRouteKeys, selectedCardId, hand, buildCity,
  selectedBuildSlotKey, networkRoute, secondNetworkRoute, activeResourceRequirement,
  resourceRequirementIndex, resourceSelections, cycleResourceSource, selectMapCity,
  selectMapCitySlot, selectMapRoute, cityNames, mapInfo,
  state
}) {
  return (<section className="panel brass-board-panel">
          <h2>游戏地图</h2>
          <BrassMap
            cities={mapCities}
            routes={routes}
            links={links}
            industries={industries}
            merchants={merchants}
            playerCount={playerCount}
            activeAction={activeAction}
            buildOptions={buildOptions}
            sellOptions={sellTileOptions}
            selectedSellTileId={selectedTileId}
            selectedMerchantId={selectedMerchantId}
            networkRouteOptions={networkRouteOptions}
            compatibleSecondRouteKeys={compatibleSecondRouteKeys}
            selectedCardId={selectedCardId}
            selectedCard={hand.find((card) => card.id === selectedCardId) ?? null}
            selectedCity={buildCity}
            selectedSlotKey={selectedBuildSlotKey}
            selectedRouteKey={networkRoute}
            selectedSecondRouteKey={secondNetworkRoute}
            resourceRequirement={activeResourceRequirement}
            resourceRequirementIndex={resourceRequirementIndex}
            resourceSelections={resourceSelections}
            onResourceSourceClick={(source) => activeResourceRequirement && cycleResourceSource(activeResourceRequirement, source)}
            onCityClick={selectMapCity}
            onCitySlotClick={selectMapCitySlot}
            onRouteClick={selectMapRoute}
            cityNames={cityNames}
          />
          <BrassMapInfoPanel info={mapInfo} />
          <BrassMarket market={state.market ?? {}} />
        </section>);
}

export { BrassBoardPanel };
