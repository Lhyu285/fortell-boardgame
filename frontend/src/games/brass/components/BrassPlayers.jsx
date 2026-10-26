import React, { useState, useEffect } from "react";
import { BRASS_PLAYER_COLORS, BRASS_INDUSTRIES, orderBrassPlayers, brassIndustryName, brassBoardTileTooltip, groupBrassBoardTiles } from "../helpers.jsx";

function BrassPlayerTable({ players, stats, hands, currentPlayerId, currentUserId }) {
  return (
    <div className="brass-player-table-wrap">
      <table className="brass-player-table">
        <thead>
          <tr>
            <th>玩家</th>
            <th>颜色</th>
            <th>金钱</th>
            <th>收入等级</th>
            <th>收入</th>
            <th>本轮花费</th>
            <th>VP</th>
            <th>预计时代得分</th>
            <th>手牌</th>
          </tr>
        </thead>
        <tbody>
          {players.map((player) => {
            const playerId = String(player.userId);
            const playerStats = stats[playerId] ?? {};
            return (
              <tr className={playerId === String(currentPlayerId) ? "active" : ""} key={playerId}>
                <td>{player.seatIndex + 1}. {player.username}{playerId === currentUserId ? "（我）" : ""}</td>
                <td>
                  {player.color ? (
                    <span className="brass-player-color" style={{ color: BRASS_PLAYER_COLORS[player.color]?.css }}>
                      {BRASS_PLAYER_COLORS[player.color]?.label ?? player.color}
                    </span>
                  ) : "-"}
                </td>
                <td>{playerStats.money ?? 0}</td>
                <td>{playerStats.incomeLevel ?? 0}</td>
                <td>{playerStats.income ?? playerStats.lastIncome ?? 0}</td>
                <td>{playerStats.spentThisRound ?? 0}</td>
                <td>{playerStats.victoryPoints ?? 0}</td>
                <td>{playerStats.estimatedEraEndScore ?? 0}</td>
                <td>{(hands[playerId] ?? []).length}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function BrassPlayerBoards({
  players,
  playerBoards,
  stats,
  hands,
  currentPlayerId,
  currentUserId,
  initialTurnOrder,
  turnOrder,
  activeAction,
  selectedIndustries,
  industryNames = BRASS_INDUSTRIES,
  onDevelopTile
}) {
  const [touchTileInfo, setTouchTileInfo] = useState(null);
  const orderedPlayers = orderBrassPlayers(players, initialTurnOrder, currentUserId);

  useEffect(() => {
    if (!touchTileInfo) return undefined;
    const closeOnNextPointer = (event) => {
      if (!event.target.closest?.("[data-brass-board-tile-touch]")) {
        setTouchTileInfo(null);
      }
    };
    document.addEventListener("pointerdown", closeOnNextPointer, true);
    return () => document.removeEventListener("pointerdown", closeOnNextPointer, true);
  }, [touchTileInfo]);

  const handleTileTouch = (event, tile) => {
    if (!window.matchMedia?.("(pointer: coarse)").matches) return false;
    event.preventDefault();
    event.stopPropagation();
    setTouchTileInfo((current) => current ? null : {
      title: `${brassIndustryName(tile.industryType, tile.industryName)} ${tile.level ?? ""}级`,
      detail: brassBoardTileTooltip(tile)
    });
    return true;
  };

  const suppressTouchTileClick = (event) => {
    if (!window.matchMedia?.("(pointer: coarse)").matches) return;
    event.preventDefault();
    event.stopPropagation();
  };

  return (
    <>
      <div className="brass-player-board-list">
        {orderedPlayers.map((player) => {
        const playerId = String(player.userId);
        const board = playerBoards[playerId] ?? {};
        return (
          <div className="brass-player-board" key={playerId}>
            <BrassPlayerCard
              player={player}
              stats={stats}
              hands={hands}
              currentPlayerId={currentPlayerId}
              currentUserId={currentUserId}
              turnOrder={turnOrder}
            />
            <BrassPersonalBoard playerId={playerId} playerName={player.username} />
            <div className="brass-player-board-grid">
              {Object.entries(industryNames).map(([industryType, label]) => (
                <div className="brass-player-board-row" key={industryType}>
                  <span>{label}</span>
                  <div className="brass-player-board-tiles">
                    {groupBrassBoardTiles(board[industryType] ?? []).map((tile, index, groups) => {
                      const isOwnBoard = playerId === currentUserId;
                      const canDevelopTile = tile.canDevelop !== false;
                      const selectedCount = activeAction === "develop" && isOwnBoard
                        ? selectedIndustries.filter((item) => item === industryType).length
                        : 0;
                      const previousCapacity = groups
                        .slice(0, index)
                        .reduce((sum, group) => sum + Math.min(Number(group.count ?? 1), 2), 0);
                      const maxSelectable = Math.min(Number(tile.count ?? 1), 2);
                      const selectedInGroup = Math.max(0, Math.min(selectedCount - previousCapacity, maxSelectable));
                      const selected = activeAction === "develop" && selectedInGroup > 0;
                      const blockedUndevelopable = activeAction === "develop" && isOwnBoard && !canDevelopTile;
                      const canDevelopThisStep = activeAction === "develop"
                        && isOwnBoard
                        && canDevelopTile
                        && (selected || (selectedCount === previousCapacity && selectedInGroup < maxSelectable && selectedIndustries.length < 2));
                      return (
                        <span
                          data-brass-board-tile-touch
                          key={tile.groupKey}
                          onClickCapture={suppressTouchTileClick}
                          onPointerDown={(event) => handleTileTouch(event, tile)}
                        >
                          <button
                            className={`brass-board-tile ${selected ? "selected" : ""} ${blockedUndevelopable ? "unavailable" : ""}`}
                            disabled={!canDevelopThisStep && !blockedUndevelopable}
                            title={brassBoardTileTooltip(tile)}
                            onClick={() => {
                              if (blockedUndevelopable) {
                                if (industryType === "pottery") {
                                  window.alert(`你不能通过研发行动移除${tile.level}级陶瓷厂`);
                                } else {
                                  window.alert(`${label}${tile.level}级板块不能研发`);
                                }
                                return;
                              }
                              onDevelopTile(tile, selectedInGroup, maxSelectable);
                            }}
                          >
                            {tile.level}级 x{tile.count}{selectedInGroup > 0 ? `（已选${selectedInGroup}）` : ""}
                          </button>
                        </span>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </div>
        );
        })}
      </div>
      {touchTileInfo ? (
        <aside className="brass-touch-tile-info" role="status">
          <strong>{touchTileInfo.title}</strong>
          <span>{touchTileInfo.detail}</span>
        </aside>
      ) : null}
    </>
  );
}

function BrassPersonalBoard({ playerId, playerName }) {
  return (
    <div className="brass-personal-board-scroll">
      <div
        className="brass-personal-board-canvas"
        data-player-id={playerId}
        id={`brass-personal-board-${playerId}`}
      >
        <img
          src="/assets/BRASS_personal.jpg"
          alt={`${playerName}的个人面板`}
          decoding="async"
          draggable={false}
          loading="lazy"
        />
        <div className="brass-personal-board-overlay" data-player-board-overlay={playerId} aria-hidden="true" />
      </div>
    </div>
  );
}

function BrassPlayerCards({ players, stats, hands, currentPlayerId, currentUserId, initialTurnOrder, turnOrder }) {
  const orderedPlayers = orderBrassPlayers(players, initialTurnOrder, currentUserId);
  return (
    <div className="brass-player-cards">
      {orderedPlayers.map((player) => (
        <BrassPlayerCard
          key={String(player.userId)}
          player={player}
          stats={stats}
          hands={hands}
          currentPlayerId={currentPlayerId}
          currentUserId={currentUserId}
          turnOrder={turnOrder}
        />
      ))}
    </div>
  );
}

function BrassPlayerCard({ player, stats, hands, currentPlayerId, currentUserId, turnOrder }) {
  const playerId = String(player.userId);
  const playerStats = stats[playerId] ?? {};
  const active = String(currentPlayerId) === playerId;
  const color = BRASS_PLAYER_COLORS[player.color]?.css ?? "#665d50";
  const roundPosition = (turnOrder ?? []).findIndex((userId) => String(userId) === playerId);
  return (
    <article className={`brass-player-card ${active ? "active" : ""}`} style={{ "--player-color": color }}>
      <header>
        <span className="brass-player-card-marker" aria-hidden="true" />
        <strong style={{ color }}>{player.username}{playerId === String(currentUserId) ? "（我）" : ""}</strong>
        {active ? <span className="brass-player-turn-tag">行动中</span> : null}
      </header>
      <dl>
        <div><dt>本轮顺位</dt><dd>{roundPosition >= 0 ? roundPosition + 1 : "-"}</dd></div>
        <div><dt>金钱</dt><dd>£{playerStats.money ?? 0}</dd></div>
        <div><dt>收入等级</dt><dd>{playerStats.incomeLevel ?? 0}</dd></div>
        <div><dt>收入</dt><dd>{playerStats.income ?? playerStats.lastIncome ?? 0}</dd></div>
        <div><dt>本轮花费</dt><dd>£{playerStats.spentThisRound ?? 0}</dd></div>
        <div><dt>VP</dt><dd>{playerStats.victoryPoints ?? 0}</dd></div>
        <div><dt>预计得分</dt><dd>{playerStats.estimatedEraEndScore ?? 0}</dd></div>
        <div><dt>手牌</dt><dd>{(hands[playerId] ?? []).length}</dd></div>
      </dl>
    </article>
  );
}

export { BrassPlayerTable, BrassPlayerBoards, BrassPersonalBoard, BrassPlayerCards, BrassPlayerCard };
