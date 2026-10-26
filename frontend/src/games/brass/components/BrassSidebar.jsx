import React from "react";
import { brassEraText, brassTurnOrderText } from "../helpers.jsx";
import { BrassPlayerCards } from "./BrassPlayers.jsx";
import { NoticePanel } from "../../../features/rooms/RoomPanels.jsx";

function BrassSidebar({ state, players, stats, currentPlayerId, currentUserId, initialTurnOrder, room }) {
  return (<aside className="brass-sidebar-column">
        <section className="panel brass-game-info-panel">
          <h2>游戏信息</h2>
          <dl className="brass-game-info-list">
            <div><dt>当前时代</dt><dd>{brassEraText(state.era)}</dd></div>
            <div><dt>时代轮次</dt><dd>第{Math.max(1, Number(state.round ?? 1))}/{Math.max(1, 12 - players.length)}轮</dd></div>
            <div><dt>本轮行动顺位</dt><dd>{brassTurnOrderText(players, state.turnOrder ?? []) || "-"}</dd></div>
          </dl>
          {state.phase === "finished" ? (
            <div className="proposal-banner">
              获胜者：{(state.winners ?? []).map((winner) => `${winner.username} (${winner.victoryPoints}VP)`).join("、")}
            </div>
          ) : null}
        </section>

        <section className="panel brass-player-summary-panel">
          <h2>玩家信息</h2>
          <BrassPlayerCards
            players={players}
            stats={stats}
            hands={state.hands ?? {}}
            currentPlayerId={currentPlayerId}
            currentUserId={currentUserId}
            initialTurnOrder={initialTurnOrder}
            turnOrder={state.turnOrder ?? []}
          />
        </section>

        <NoticePanel notices={room.notices ?? []} title="历史行动" className="brass-history-panel" />
      </aside>);
}

export { BrassSidebar };
