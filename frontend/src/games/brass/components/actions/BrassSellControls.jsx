import React from "react";
import { sellConfirmText, brassIndustryName } from "../../helpers.jsx";
import { BrassResourceSelectionPanel } from "../BrassResourceSelectionPanel.jsx";

function BrassSellControls({
  sellInProgress, sellCount, sellBlockReason, confirmed,
  selectedSellOption, selectedMerchant, cityLabel, selectedCardId,
  selectedTileId, selectedMerchantId, resourceSelectionPayload, selectedMerchantDevelopReward,
  developIndustries, isCurrentPlayer, availableActions, resourceRequirements,
  resourceRequirementIndex, resourceSelections, setResourceRequirementIndex, cycleResourceSource,
  clearResourceRequirement, setDevelopIndustries, industryNames, sellBeerSources,
  sellMerchantSources, availableSellTiles, setSelectedTileId, setSelectedMerchantId
}) {
  return (<div className="brass-action-panel">
            <div className="proposal-banner brass-sell-banner">
              <span>
                出售行动：{sellInProgress ? `已售卖 ${sellCount} 个产业，可继续选择下一个产业。` : "请选择1张手牌后开始售卖。"}
              </span>
              <div className="inline-actions">
                <button
                  className="secondary-button"
                  disabled={Boolean(sellBlockReason)}
                  onClick={() => confirmed(sellConfirmText(selectedSellOption, selectedMerchant, cityLabel), "sell", {
                    cardId: sellInProgress ? undefined : selectedCardId,
                    tileId: selectedTileId,
                    merchantId: selectedMerchantId || undefined,
                    ...resourceSelectionPayload,
                    freeDevelopIndustryType: selectedMerchantDevelopReward ? developIndustries[0] || undefined : undefined
                  })}
                >
                  售卖选中产业
                </button>
                <button
                  className="primary-button"
                  disabled={!isCurrentPlayer || !sellInProgress || sellCount < 1 || !availableActions.includes("end_sell")}
                  onClick={() => confirmed("是否结束出售行动？", "end_sell", {})}
                >
                  结束售卖行动
                </button>
              </div>
            </div>
            <p className="muted-note">步骤：选择手牌启动出售行动 → 每次选择1个产业 → 选择啤酒或贸易商 → 售卖选中产业；可继续售卖，最后结束售卖行动。</p>
            {resourceRequirements.length > 0 ? (
              <BrassResourceSelectionPanel
                requirements={resourceRequirements}
                activeIndex={resourceRequirementIndex}
                selected={resourceSelections}
                onSelectRequirement={setResourceRequirementIndex}
                onCycleSource={cycleResourceSource}
                onClearRequirement={clearResourceRequirement}
              />
            ) : null}
            {selectedMerchantDevelopReward ? (
              <label>
                免费研发
                <select value={developIndustries[0] ?? ""} onChange={(event) => setDevelopIndustries(event.target.value ? [event.target.value] : [])}>
                  <option value="">自动选择</option>
                  {Object.entries(industryNames).map(([key, label]) => (
                    <option value={key} key={key}>{label}</option>
                  ))}
                </select>
              </label>
            ) : null}
            <span className="muted-note">
              目标：{selectedSellOption ? `${cityLabel(selectedSellOption.city)} ${brassIndustryName(selectedSellOption.industryType, selectedSellOption.industryName)}` : "-"} / 啤酒源 {sellBeerSources.length} / 贸易商 {sellMerchantSources.length}
            </span>
            {sellBlockReason ? <div className="form-error">{sellBlockReason}</div> : null}
            <div className="brass-tile-list">
              {availableSellTiles.map((tile) => (
                <button
                  className={`brass-tile ${selectedTileId === tile.id ? "selected" : ""}`}
                  key={tile.id}
                  onClick={() => {
                    setSelectedTileId((current) => current === tile.id ? "" : tile.id);
                    setSelectedMerchantId("");
                  }}
                >
                  <strong>{cityLabel(tile.city)} {brassIndustryName(tile.industryType, tile.industryName)}</strong>
                </button>
              ))}
            </div>
          </div>);
}

export { BrassSellControls };
