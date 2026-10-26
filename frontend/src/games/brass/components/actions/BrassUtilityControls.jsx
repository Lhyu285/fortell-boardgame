import React from "react";
import { TEXT } from "../../../../features/rooms/roomText.js";

function BrassUtilityControls({ simpleActionReason, activeAction, confirmed, selectedCardId, selectedCards }) {
  return (<div className="brass-action-panel">
            <p className="muted-note">步骤：选择所需手牌 → 点击当前行动按钮 → 确认执行。</p>
            {simpleActionReason ? <div className="form-error">{simpleActionReason}</div> : null}
            {activeAction === "loan" ? (
              <button className="secondary-button" disabled={Boolean(simpleActionReason)} onClick={() => confirmed("是否贷款？弃1张牌，获得30金钱，并按贷款收入等级表降低收入等级。", "loan", { cardId: selectedCardId })}>
                {TEXT.brassLoan}
              </button>
            ) : null}
            {activeAction === "scout" ? (
              <button className="secondary-button" disabled={Boolean(simpleActionReason)} onClick={() => confirmed("是否侦查？弃3张非万能牌，获得2张万能牌。", "scout", { cardIds: selectedCards })}>
                {TEXT.brassScout}
              </button>
            ) : null}
            {activeAction === "skip" ? (
              <button className="ghost-button" disabled={Boolean(simpleActionReason)} onClick={() => confirmed("是否弃1张牌并跳过一个行动？", "skip", { cardId: selectedCardId })}>
                {TEXT.brassSkip}
              </button>
            ) : null}
          </div>);
}

export { BrassUtilityControls };
