import React, { useState } from "react";
import { useBrassActionDraft } from "./draft/useBrassActionDraft.js";
import { BrassActionBanner } from "./components/BrassActionBanner.jsx";
import { BrassBoardPanel } from "./components/BrassBoardPanel.jsx";
import { BrassControlPanel } from "./components/BrassControlPanel.jsx";
import { BrassPlayerBoards } from "./components/BrassPlayers.jsx";
import { SeatBoard } from "../../features/rooms/RoomPanels.jsx";
import { BrassSidebar } from "./components/BrassSidebar.jsx";
import { CardHintModal } from "./components/CardHintModal.jsx";

function BrassRoom({ room, seats, currentSeat, onAction }) {
  const [cardHintsOpen, setCardHintsOpen] = useState(false);
  const [mapInfo, setMapInfo] = useState(null);
  const {
    state, availableActions, currentPlayerColor, players,
    currentPlayerId, availableActionState, isCurrentPlayer, confirmed,
    cityLabel, mainActions, activeAction, clickBrassAction,
    mapCities, routes, links, industries,
    merchants, playerCount, buildOptions, sellTileOptions,
    selectedTileId, selectedMerchantId, networkRouteOptions, compatibleSecondRouteKeys,
    selectedCardId, hand, buildCity, selectedBuildSlotKey,
    networkRoute, secondNetworkRoute, activeResourceRequirement, resourceRequirementIndex,
    resourceSelections, cycleResourceSource, selectMapCity, selectMapCitySlot,
    selectMapRoute, cityNames, selectedCards, toggleCard,
    setBuildCity, setBuildSlotIndex, selectableBuildCities, activeBuildIndustry,
    setBuildIndustry, buildIndustryOptions, industryNames, resourceRequirements,
    setResourceRequirementIndex, clearResourceRequirement, buildCompatibleCardIds, selectedBuildOption,
    buildBlockReason, resourceSelectionPayload, setNetworkRoute, availableRoutesForSelect,
    routeLabel, setSecondNetworkRoute, selectedRoute, selectedSecondRoute,
    networkBlockReason, developIndustries, toggleDevelopIndustry, setDevelopIndustries,
    developIronSources, developBlockReason, sellInProgress, sellCount,
    sellBlockReason, selectedSellOption, selectedMerchant, selectedMerchantDevelopReward,
    sellBeerSources, sellMerchantSources, availableSellTiles, setSelectedTileId,
    setSelectedMerchantId, simpleActionReason, playerBoards, stats,
    currentUserId, initialTurnOrder, toggleDevelopTile
  } = useBrassActionDraft({ room, currentSeat, onAction, setMapInfo });
  return (
    <div className="brass-layout">
      {state.endProposalText ? <div className="proposal-banner">{state.endProposalText}</div> : null}

      <div className="brass-main-grid">
      <main className="brass-primary-column">
      <BrassActionBanner
          availableActions={availableActions}
          currentPlayerColor={currentPlayerColor}
          players={players}
          currentPlayerId={currentPlayerId}
          availableActionState={availableActionState}
          isCurrentPlayer={isCurrentPlayer}
          confirmed={confirmed}
          cityLabel={cityLabel}
          setCardHintsOpen={setCardHintsOpen}
          mainActions={mainActions}
          activeAction={activeAction}
          clickBrassAction={clickBrassAction}
        />

        <BrassBoardPanel
          mapCities={mapCities}
          routes={routes}
          links={links}
          industries={industries}
          merchants={merchants}
          playerCount={playerCount}
          activeAction={activeAction}
          buildOptions={buildOptions}
          sellTileOptions={sellTileOptions}
          selectedTileId={selectedTileId}
          selectedMerchantId={selectedMerchantId}
          networkRouteOptions={networkRouteOptions}
          compatibleSecondRouteKeys={compatibleSecondRouteKeys}
          selectedCardId={selectedCardId}
          hand={hand}
          buildCity={buildCity}
          selectedBuildSlotKey={selectedBuildSlotKey}
          networkRoute={networkRoute}
          secondNetworkRoute={secondNetworkRoute}
          activeResourceRequirement={activeResourceRequirement}
          resourceRequirementIndex={resourceRequirementIndex}
          resourceSelections={resourceSelections}
          cycleResourceSource={cycleResourceSource}
          selectMapCity={selectMapCity}
          selectMapCitySlot={selectMapCitySlot}
          selectMapRoute={selectMapRoute}
          cityNames={cityNames}
          mapInfo={mapInfo}
          state={state}
        />

        <BrassControlPanel
          hand={hand}
          selectedCards={selectedCards}
          isCurrentPlayer={isCurrentPlayer}
          toggleCard={toggleCard}
          mapCities={mapCities}
          cityNames={cityNames}
          activeAction={activeAction}
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
          networkBlockReason={networkBlockReason}
          developIndustries={developIndustries}
          toggleDevelopIndustry={toggleDevelopIndustry}
          setDevelopIndustries={setDevelopIndustries}
          developIronSources={developIronSources}
          developBlockReason={developBlockReason}
          sellInProgress={sellInProgress}
          sellCount={sellCount}
          sellBlockReason={sellBlockReason}
          selectedSellOption={selectedSellOption}
          selectedMerchant={selectedMerchant}
          selectedTileId={selectedTileId}
          selectedMerchantId={selectedMerchantId}
          selectedMerchantDevelopReward={selectedMerchantDevelopReward}
          availableActions={availableActions}
          sellBeerSources={sellBeerSources}
          sellMerchantSources={sellMerchantSources}
          availableSellTiles={availableSellTiles}
          setSelectedTileId={setSelectedTileId}
          setSelectedMerchantId={setSelectedMerchantId}
          simpleActionReason={simpleActionReason}
        />

        <section className="panel brass-player-panel">
          <h2>玩家版图</h2>
          <BrassPlayerBoards
            players={players}
            playerBoards={playerBoards}
            stats={stats}
            hands={state.hands ?? {}}
            currentPlayerId={currentPlayerId}
            currentUserId={currentUserId}
            initialTurnOrder={initialTurnOrder}
            turnOrder={state.turnOrder ?? []}
            activeAction={activeAction}
            selectedIndustries={developIndustries}
            industryNames={industryNames}
            onDevelopTile={(tile, selectedInGroup, maxSelectable) => toggleDevelopTile(tile.industryType, selectedInGroup, maxSelectable)}
          />
        </section>

        <section className="panel room-seat-panel compact brass-seat-panel">
          <SeatBoard owner={false} waiting={false} seats={seats} showKick={false} />
        </section>
      </main>

      <BrassSidebar
          state={state}
          players={players}
          stats={stats}
          currentPlayerId={currentPlayerId}
          currentUserId={currentUserId}
          initialTurnOrder={initialTurnOrder}
          room={room}
        />
      </div>

      {cardHintsOpen ? (
        <CardHintModal cardHints={state.cardHints ?? {}} onClose={() => setCardHintsOpen(false)} />
      ) : null}

    </div>
  );
}

export { BrassRoom };
export { BrassMap, BrassMapInfoPanel } from "./components/BrassMap.jsx";
export { CardHintModal } from "./components/CardHintModal.jsx";
export { BrassMarket, BrassMarketTrack } from "./components/BrassMarket.jsx";
export { BrassPlayerTable, BrassPlayerBoards, BrassPersonalBoard, BrassPlayerCards, BrassPlayerCard } from "./components/BrassPlayers.jsx";
export { BrassResourceSelectionPanel, ResourceSelectionPanel } from "./components/BrassResourceSelectionPanel.jsx";
export default BrassRoom;
