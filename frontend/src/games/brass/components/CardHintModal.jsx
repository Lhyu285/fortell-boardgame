import React from "react";

function CardHintModal({ cardHints, onClose }) {
  const cards = cardHints.cards ?? [];
  return (
    <div className="rule-modal-backdrop" role="presentation">
      <div className="rule-modal" role="dialog" aria-modal="true" aria-label="卡牌提示表">
        <div className="rule-modal-header">
          <h2>卡牌提示表</h2>
          <button className="ghost-button small-button" onClick={onClose}>关闭</button>
        </div>
        <div className="rule-modal-body">
          <table className="brass-player-table">
            <thead>
              <tr>
                <th>卡牌名称</th>
                <th>卡牌总数</th>
                <th>当前剩余数量</th>
              </tr>
            </thead>
            <tbody>
              {cards.map((card) => (
                <tr key={card.name}>
                  <td>{card.name}</td>
                  <td>{card.total}</td>
                  <td>{card.remaining}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export { CardHintModal };
