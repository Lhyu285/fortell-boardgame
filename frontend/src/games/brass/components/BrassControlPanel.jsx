import React from "react";
import { brassCardKind, brassCardIcon, brassCardKindLabel, brassCardTitleStyle, brassCardShortName } from "../helpers.jsx";
import { BrassBuildControls } from "./actions/BrassBuildControls.jsx";
import { BrassNetworkControls } from "./actions/BrassNetworkControls.jsx";
import { BrassDevelopControls } from "./actions/BrassDevelopControls.jsx";
import { BrassSellControls } from "./actions/BrassSellControls.jsx";
import { BrassUtilityControls } from "./actions/BrassUtilityControls.jsx";

function BrassControlPanel({
  hand, selectedCards, isCurrentPlayer, toggleCard,
  mapCities, cityNames, activeAction, buildCity,
  setBuildCity, setBuildSlotIndex, selectableBuildCities, cityLabel,
  activeBuildIndustry, setBuildIndustry, buildIndustryOptions, industryNames,
  resourceRequirements, resourceRequirementIndex, resourceSelections, setResourceRequirementIndex,
  cycleResourceSource, clearResourceRequirement, buildCompatibleCardIds, selectedBuildOption,
  buildBlockReason, confirmed, selectedCardId, resourceSelectionPayload,
  networkRoute, setNetworkRoute, availableRoutesForSelect, routeLabel,
  state, secondNetworkRoute, setSecondNetworkRoute, compatibleSecondRouteKeys,
  selectedRoute, selectedSecondRoute, networkBlockReason, developIndustries,
  toggleDevelopIndustry, setDevelopIndustries, developIronSources, developBlockReason,
  sellInProgress, sellCount, sellBlockReason, selectedSellOption,
  selectedMerchant, selectedTileId, selectedMerchantId, selectedMerchantDevelopReward,
  availableActions, sellBeerSources, sellMerchantSources, availableSellTiles,
  setSelectedTileId, setSelectedMerchantId, simpleActionReason
}) {
  return (<section className="panel brass-control-panel">
          <h2>手牌区</h2>
          <div className="brass-hand-grid">
            {hand.map((card) => (
              (() => {
                const disabledByAction = false;
                return (
              <button
                key={card.id}
                className={`brass-card ${selectedCards.includes(card.id) ? "selected" : ""}`}
                disabled={!isCurrentPlayer || disabledByAction}
                onClick={() => toggleCard(card.id)}
              >
                <span className={`brass-card-icon brass-card-icon-${brassCardKind(card)}`} aria-hidden="true">
                  {brassCardIcon(card)}
                </span>
                <small className="brass-card-type">{brassCardKindLabel(card)}</small>
                <strong style={brassCardTitleStyle(card, mapCities)}>{brassCardShortName(card, mapCities, cityNames)}</strong>
              </button>
                );
              })()
            ))}
          </div>
          {activeAction === "build" ? <BrassBuildControls
          buildCity={buildCity}
          setBuildCity={setBuildCity}
          setBuildSlotIndex={setBuildSlotIndex}
          selectableBuildCities={selectableBuildCities}
          cityLabel={cityLabel}
          activeBuildIndustry={activeBuildIndustry}
          setBuildIndustry={setBuildIndustry}
          buildIndustryOptions={buildIndustryOptions}
          industryNames={industryNames}
          resourceRequirements={resourceRequirements}
          resourceRequirementIndex={resourceRequirementIndex}
          resourceSelections={resourceSelections}
          setResourceRequirementIndex={setResourceRequirementIndex}
          cycleResourceSource={cycleResourceSource}
          clearResourceRequirement={clearResourceRequirement}
          buildCompatibleCardIds={buildCompatibleCardIds}
          selectedBuildOption={selectedBuildOption}
          buildBlockReason={buildBlockReason}
          confirmed={confirmed}
          selectedCardId={selectedCardId}
          resourceSelectionPayload={resourceSelectionPayload}
        /> : null}
          {activeAction === "network" ? <BrassNetworkControls
          networkRoute={networkRoute}
          setNetworkRoute={setNetworkRoute}
          availableRoutesForSelect={availableRoutesForSelect}
          routeLabel={routeLabel}
          state={state}
          secondNetworkRoute={secondNetworkRoute}
          setSecondNetworkRoute={setSecondNetworkRoute}
          compatibleSecondRouteKeys={compatibleSecondRouteKeys}
          selectedRoute={selectedRoute}
          selectedSecondRoute={selectedSecondRoute}
          resourceRequirements={resourceRequirements}
          resourceRequirementIndex={resourceRequirementIndex}
          resourceSelections={resourceSelections}
          setResourceRequirementIndex={setResourceRequirementIndex}
          cycleResourceSource={cycleResourceSource}
          clearResourceRequirement={clearResourceRequirement}
          networkBlockReason={networkBlockReason}
          selectedCardId={selectedCardId}
          resourceSelectionPayload={resourceSelectionPayload}
          confirmed={confirmed}
        /> : null}
          {activeAction === "develop" ? <BrassDevelopControls
          industryNames={industryNames}
          developIndustries={developIndustries}
          isCurrentPlayer={isCurrentPlayer}
          toggleDevelopIndustry={toggleDevelopIndustry}
          setDevelopIndustries={setDevelopIndustries}
          resourceRequirements={resourceRequirements}
          resourceRequirementIndex={resourceRequirementIndex}
          resourceSelections={resourceSelections}
          setResourceRequirementIndex={setResourceRequirementIndex}
          cycleResourceSource={cycleResourceSource}
          clearResourceRequirement={clearResourceRequirement}
          developIronSources={developIronSources}
          developBlockReason={developBlockReason}
          confirmed={confirmed}
          selectedCardId={selectedCardId}
          resourceSelectionPayload={resourceSelectionPayload}
        /> : null}
          {activeAction === "sell" ? <BrassSellControls
          sellInProgress={sellInProgress}
          sellCount={sellCount}
          sellBlockReason={sellBlockReason}
          confirmed={confirmed}
          selectedSellOption={selectedSellOption}
          selectedMerchant={selectedMerchant}
          cityLabel={cityLabel}
          selectedCardId={selectedCardId}
          selectedTileId={selectedTileId}
          selectedMerchantId={selectedMerchantId}
          resourceSelectionPayload={resourceSelectionPayload}
          selectedMerchantDevelopReward={selectedMerchantDevelopReward}
          developIndustries={developIndustries}
          isCurrentPlayer={isCurrentPlayer}
          availableActions={availableActions}
          resourceRequirements={resourceRequirements}
          resourceRequirementIndex={resourceRequirementIndex}
          resourceSelections={resourceSelections}
          setResourceRequirementIndex={setResourceRequirementIndex}
          cycleResourceSource={cycleResourceSource}
          clearResourceRequirement={clearResourceRequirement}
          setDevelopIndustries={setDevelopIndustries}
          industryNames={industryNames}
          sellBeerSources={sellBeerSources}
          sellMerchantSources={sellMerchantSources}
          availableSellTiles={availableSellTiles}
          setSelectedTileId={setSelectedTileId}
          setSelectedMerchantId={setSelectedMerchantId}
        /> : null}
          {["loan", "scout", "skip", "restart_turn", "end_turn", "maintain_era"].includes(activeAction) ? <BrassUtilityControls
          simpleActionReason={simpleActionReason}
          activeAction={activeAction}
          confirmed={confirmed}
          selectedCardId={selectedCardId}
          selectedCards={selectedCards}
        /> : null}
        </section>);
}

export { BrassControlPanel };
