import React from "react";
import { brassPlayerName, brassIndustryName, brassActionText } from "../helpers.jsx";

function BrassActionBanner({
  availableActions, currentPlayerColor, players, currentPlayerId,
  availableActionState, isCurrentPlayer, confirmed, cityLabel,
  setCardHintsOpen, mainActions, activeAction, clickBrassAction
}) {
  return (<section className="brass-action-banner" aria-label="行动选择">
        {availableActions.includes("resolve_income_debt") ? (
          <>
            <div className="brass-action-banner-copy">
              <span className="brass-action-kicker">收入结算</span>
              <strong>
                <span style={{ color: currentPlayerColor }}>{brassPlayerName(players, currentPlayerId)}</span>
                需要支付 {availableActionState.incomeDebt?.amount ?? 0} 英镑欠款
              </strong>
            </div>
            <div className="brass-action-banner-actions">
              <div className="brass-action-banner-buttons">
                {(availableActionState.incomeDebtTiles ?? []).map((tile) => (
                  <button
                    className="brass-action-choice"
                    key={tile.id}
                    disabled={!isCurrentPlayer}
                    onClick={() => confirmed(
                      `是否移除${cityLabel(tile.city)}的${tile.level ?? ""}级${brassIndustryName(tile.industryType, tile.industryName)}用于支付收入欠款？`,
                      "resolve_income_debt",
                      { tileId: tile.id }
                    )}
                  >
                    移除 {cityLabel(tile.city)} {tile.level ?? ""}级{brassIndustryName(tile.industryType, tile.industryName)}
                  </button>
                ))}
              </div>
              <div className="brass-action-banner-buttons brass-action-utility-buttons">
                <button className="brass-action-utility" onClick={() => setCardHintsOpen(true)}>卡牌提示表</button>
              </div>
            </div>
          </>
        ) : (
          <>
            <div className="brass-action-banner-copy">
              <span className="brass-action-kicker">当前回合</span>
              <strong>
                轮到 <span style={{ color: currentPlayerColor }}>{brassPlayerName(players, currentPlayerId) || "-"}</span> 行动，请选择：
              </strong>
            </div>
            <div className="brass-action-banner-actions">
              <div className="brass-action-banner-buttons">
                {mainActions.map((action) => (
                  <button
                    className={`brass-action-choice ${activeAction === action ? "selected" : ""}`}
                    disabled={!isCurrentPlayer || !availableActions.includes(action)}
                    key={action}
                    onClick={() => clickBrassAction(action)}
                  >
                    {brassActionText(action)}
                  </button>
                ))}
              </div>
              <div className="brass-action-banner-buttons brass-action-utility-buttons">
                <button
                  className="brass-action-utility"
                  disabled={!isCurrentPlayer || !availableActions.includes("restart_turn")}
                  onClick={() => clickBrassAction("restart_turn")}
                >
                  重新开始回合
                </button>
                <button
                  className="brass-action-utility"
                  disabled={!isCurrentPlayer || !availableActions.includes("end_turn")}
                  onClick={() => clickBrassAction("end_turn")}
                >
                  结束回合
                </button>
                {availableActions.includes("maintain_era") ? (
                  <button
                    className="brass-action-utility"
                    disabled={!isCurrentPlayer}
                    onClick={() => clickBrassAction("maintain_era")}
                  >
                    时代维护
                  </button>
                ) : null}
                <button className="brass-action-utility" onClick={() => setCardHintsOpen(true)}>卡牌提示表</button>
              </div>
            </div>
          </>
        )}
      </section>);
}

export { BrassActionBanner };
