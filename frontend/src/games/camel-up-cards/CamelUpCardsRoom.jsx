import React, { useEffect, useState } from "react";
import { TEXT } from "../../features/rooms/roomText.js";
import { SeatBoard, NoticePanel } from "../../features/rooms/RoomPanels.jsx";
import {
  CAMEL_COLORS,
  camelPlayerName,
  camelRaceCardText,
  camelBetCardText,
  camelTokenStatusText,
  camelTokenAt,
  asArray
} from "./helpers.jsx";

function CamelUpCardsRoom({ room, seats, currentSeat, owner, onAction }) {
  const state = room.gameState ?? {};
  const currentUserId = String(room.currentUser?.id ?? "");
  const currentPlayerId = String(state.currentPlayerId ?? "");
  const currentPlayerName = camelPlayerName(state.players ?? [], currentPlayerId);
  const isCurrentPlayer = currentSeat && currentUserId === currentPlayerId && state.phase === "PLAYING";
  const turn = state.turn ?? {};
  const trackDone = turn.trackActionDone === true;
  const betDone = turn.betActionDone === true;
  const canTrack = isCurrentPlayer && !trackDone;
  const canBet = isCurrentPlayer && !betDone;
  const canSkipBet = isCurrentPlayer && trackDone && !betDone;
  const [shortcutPos, setShortcutPos] = useState("");
  const [fennecPos, setFennecPos] = useState("");
  const [setupSelection, setSetupSelection] = useState([]);

  const currentSetup = state.setup?.selections?.[currentUserId] ?? null;
  const setupStatus = currentSetup?.status ?? "";
  useEffect(() => {
    setSetupSelection([]);
  }, [state.leg, setupStatus]);

  function confirmed(message, type, payload = {}) {
    if (window.confirm(message)) {
      onAction(type, payload);
    }
  }

  return (
    <div className="camel-layout">
      {state.endProposalText ? <div className="proposal-banner">{state.endProposalText}</div> : null}
      <section className="panel camel-status-panel">
        <div className="game-room-heading">
          <h2>沙漠赛程</h2>
          <span>{TEXT.currentPlayer}：{currentPlayerName}</span>
        </div>
        <div className={`camel-turn-banner ${isCurrentPlayer ? "is-current" : ""}`}>
          <span className="camel-turn-kicker">当前回合</span>
          <strong>{currentPlayerName}</strong>
          <span>{isCurrentPlayer ? "轮到你行动了" : "等待当前玩家完成行动"}</span>
        </div>
        <div className="camel-meta-row">
          <span>第 {state.leg ?? 1} 赛段</span>
          <span>终点位置：{state.finishPosition ?? "-"}</span>
          <span>{TEXT.raceDeck}: {asArray(state.raceDeck).length}</span>
          <span>{TEXT.temporaryDiscard}：{asArray(state.temporaryDiscard).map(camelRaceCardText).join("、") || "无"}</span>
        </div>
      </section>

      {state.phase === "SETUP_SELECTION" ? (
        <CamelSetupPanel
          setup={state.setup ?? {}}
          currentSetup={currentSetup}
          selectedIds={setupSelection}
          onToggle={(cardId) => setSetupSelection((current) => current.includes(cardId)
            ? current.filter((id) => id !== cardId)
            : [...current, cardId])}
          onSubmit={() => confirmed("确定提交当前选择吗？", "submit_setup_selection", { cardIds: setupSelection })}
        />
      ) : null}

      <div className="camel-main-grid">
        <CamelPlayersPanel state={state} currentUserId={currentUserId} />

        <section className="panel camel-track-panel">
          <h2>{TEXT.trackArea}</h2>
          <CamelRankings rankings={state.rankings ?? []} />
          <CamelTrack state={state} />
          {state.phase === "FINISHED" ? (
            <div className="proposal-banner">
              获胜者：{(state.winners ?? []).map((winner) => winner.username).join("、")}
            </div>
          ) : (
            <div className="camel-action-panel">
              <div className="camel-action-section">
                <div className="camel-action-section-heading">
                  <strong>推进赛道</strong>
                  <span>每回合先执行一个赛道行动</span>
                </div>
                <button className="primary-button camel-primary-action" disabled={!canTrack} onClick={() => confirmed("确定翻开顶部竞赛牌吗？", "draw_race_card")}>
                  {TEXT.drawRaceCard}
                </button>
              </div>
              <div className="camel-action-section">
                <div className="camel-action-section-heading">
                  <strong>使用手牌</strong>
                  <span>可以使用自己的手牌，或替其他玩家使用</span>
                </div>
                <button className="secondary-button" disabled={!canTrack || !state.hands?.[currentUserId]} onClick={() => confirmed("确定使用自己的手牌吗？", "play_hand", { ownerUserId: room.currentUser?.id })}>
                  {TEXT.playOwnHand}
                </button>
                <div className="camel-hand-buttons">
                  {(state.players ?? [])
                    .filter((player) => String(player.userId) !== currentUserId)
                    .map((player) => (
                      <button
                        key={String(player.userId)}
                        className="ghost-button small-button"
                        disabled={!canTrack || !state.hands?.[String(player.userId)]}
                        onClick={() => confirmed(`确定使用${player.username}的手牌吗？`, "play_hand", { ownerUserId: player.userId })}
                      >
                        {TEXT.playPlayerHand.replace("{player}", player.username)}
                      </button>
                    ))}
                </div>
              </div>
              <div className="camel-action-section">
                <div className="camel-action-section-heading">
                  <strong>放置扩展标记</strong>
                  <span>输入要放置的赛道位置</span>
                </div>
                <div className="camel-token-actions">
                  <label>
                    捷径位置
                    <input value={shortcutPos} inputMode="numeric" onChange={(event) => setShortcutPos(event.target.value)} />
                  </label>
                  <button className="secondary-button" disabled={!canTrack} onClick={() => confirmed(`确定在${shortcutPos}号位置放置捷径吗？`, "place_token", { tokenType: "shortcut", position: Number(shortcutPos) })}>
                    {TEXT.placeShortcut}
                  </button>
                  <label>
                    耳廓狐位置
                    <input value={fennecPos} inputMode="numeric" onChange={(event) => setFennecPos(event.target.value)} />
                  </label>
                  <button className="secondary-button" disabled={!canTrack} onClick={() => confirmed(`确定在${fennecPos}号位置放置耳廓狐吗？`, "place_token", { tokenType: "fennec", position: Number(fennecPos) })}>
                    {TEXT.placeFennec}
                  </button>
                </div>
              </div>
              <button className="ghost-button" disabled={!canSkipBet} onClick={() => confirmed("确定跳过下注行动吗？", "skip_bet")}>
                {TEXT.skipBet}
              </button>
            </div>
          )}
        </section>

        <section className="panel camel-bet-panel">
          <h2>{TEXT.bettingCards}</h2>
          <CamelBetMarket market={state.betMarket ?? {}} canBet={canBet} onTake={(card) => confirmed(`确定拿取${camelBetCardText(card)}吗？`, "take_bet", { cardId: card.id })} />
        </section>

        <NoticePanel notices={room.notices ?? []} />
      </div>

      <section className="panel room-seat-panel compact">
        <SeatBoard owner={owner} waiting={false} seats={seats} showKick={false} />
      </section>
    </div>
  );
}

function CamelPlayersPanel({ state, currentUserId }) {
  const money = state.money ?? {};
  const tokens = state.playerTokens ?? {};
  const bets = state.bets ?? {};
  const hands = state.hands ?? {};
  return (
    <section className="panel camel-player-panel">
      <h2>{TEXT.playerArea}</h2>
      <div className="camel-player-list">
        {(state.players ?? []).map((player) => {
          const playerId = String(player.userId);
          const playerBets = bets[playerId] ?? {};
          return (
            <div className={`camel-player-card ${playerId === String(state.currentPlayerId) ? "active" : ""}`} key={playerId}>
              <div className="camel-player-head">
                <strong>{player.seatIndex + 1}. {player.username}{playerId === currentUserId ? "（我）" : ""}</strong>
                <span>{money[playerId] ?? 0} {TEXT.egyptPounds}</span>
              </div>
              <small>标记：捷径 {camelTokenStatusText(tokens[playerId]?.shortcut)} / 耳廓狐 {camelTokenStatusText(tokens[playerId]?.fennec)}</small>
              {playerId === String(state.currentPlayerId) ? <span className="camel-player-turn-tag">当前回合</span> : null}
              <small>手牌：{hands[playerId] ? camelRaceCardText(hands[playerId]) : "无"}</small>
              <small>{TEXT.legBets}：{(playerBets.leg ?? []).map(camelBetCardText).join("、") || "无"}</small>
              <small>{TEXT.finalBets}：{[playerBets.final_winner, playerBets.final_loser].filter(Boolean).map(camelBetCardText).join("、") || "无"}</small>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function CamelRankings({ rankings }) {
  const racing = rankings.filter((rank) => rank.color !== "black");
  return (
    <div className="camel-ranking-panel">
      <strong>{TEXT.camelRankings}</strong>
      <div className="camel-ranking-list">
        {racing.map((rank, index) => (
          <span key={rank.color} className={`camel-rank-item ${rank.color}`}>
            第{index + 1}名 · {CAMEL_COLORS[rank.color] ?? rank.color} · {rank.position}格</span>
        ))}
      </div>
    </div>
  );
}

function CamelSetupPanel({ setup, currentSetup, selectedIds, onToggle, onSubmit }) {
  const status = currentSetup?.status ?? "waiting";
  const required = status === "discard"
    ? Number(setup.discardRequired ?? 0)
    : Number(setup.deckRequired ?? 0);
  const prompt = status === "discard"
    ? TEXT.setupDiscardPrompt.replace("{count}", String(required))
    : status === "deck"
      ? TEXT.setupDeckPrompt.replace("{count}", String(required))
      : TEXT.setupDone;
  const cards = asArray(currentSetup?.cards);
  const canSubmit = (status === "discard" || status === "deck") && selectedIds.length === required;

  return (
    <section className="panel camel-setup-panel">
      <div className="game-room-heading">
        <h2>赛段设置</h2>
        <span>{prompt}</span>
      </div>
      {status === "discard" || status === "deck" ? (
        <>
          <div className="camel-setup-cards">
            {cards.map((card) => (
              <button
                key={card.id}
                className={selectedIds.includes(String(card.id)) ? "active" : ""}
                onClick={() => onToggle(String(card.id))}
              >
                <span className="camel-setup-card-label">{camelRaceCardText(card)}</span>
                <small>{selectedIds.includes(String(card.id)) ? "已选择" : "点击选择"}</small>
              </button>
            ))}
          </div>
          <button className="primary-button" disabled={!canSubmit} onClick={onSubmit}>
            {TEXT.confirmSelection}
          </button>
        </>
      ) : (
        <p className="muted-note">{TEXT.setupDone}</p>
      )}
    </section>
  );
}

function CamelTrack({ state }) {
  const stacks = state.stacks ?? {};
  const finish = Number(state.finishPosition ?? 0);
  const tokens = state.trackTokens ?? {};
  const sandstormPositions = new Set((state.sandstormPairs ?? []).map((pair) => Number(pair.to)));
  const trackScrollRef = React.useRef(null);

  useEffect(() => {
    const trackScroll = trackScrollRef.current;
    if (trackScroll) {
      trackScroll.scrollLeft = trackScroll.scrollWidth;
    }
  }, [finish, JSON.stringify(stacks), JSON.stringify(tokens)]);

  return (
    <div className="camel-track-scroll" ref={trackScrollRef}>
      <div className="camel-track">
        <div className="camel-track-start" aria-label="起点">
          <span>起点</span>
          <strong>营地</strong>
        </div>
        {Array.from({ length: finish + 1 }, (_, position) => (
          <div className={`camel-cell ${sandstormPositions.has(position) ? "sandstorm" : ""} ${position === finish ? "finish" : ""}`} key={position}>
            <strong>{position}{position === finish ? " · 终点" : ""}</strong>
            {sandstormPositions.has(position) ? <em>{TEXT.sandstorm}</em> : null}
            <div className="camel-stack">
              {asArray(stacks[String(position)]).slice().reverse().map((color, index) => (
                <span className={`camel-chip ${color}`} key={`${position}-${color}-${index}`}>
                  <span className="camel-chip-glyph" aria-hidden="true">♟</span>
                  {CAMEL_COLORS[color] ?? color}
                </span>
              ))}
            </div>
            <small>{camelTokenAt(tokens, position)}</small>
          </div>
        ))}
      </div>
    </div>
  );
}

function CamelBetMarket({ market, canBet, onTake }) {
  const legWinner = market.legWinner ?? {};
  const legMiddle = market.legMiddle ?? {};
  return (
    <div className="camel-market">
      <h3>{TEXT.legWinner}</h3>
      {Object.entries(legWinner).map(([color, rawCards]) => {
        const cards = asArray(rawCards);
        return (
        <button key={`winner-${color}`} className={`camel-bet-card ${color}`} disabled={!canBet || cards.length === 0} onClick={() => onTake(cards[0])}>
          <strong>{CAMEL_COLORS[color] ?? color}</strong>
          <span>赛段赢家</span>
          <small>{cards[0] ? `赔率 ${cards[0].payouts?.join(" / ")}` : "已取走"}</small>
        </button>
        );
      })}
      <h3>{TEXT.legMiddle}</h3>
      {Object.entries(legMiddle).map(([color, rawCards]) => {
        const cards = asArray(rawCards);
        return (
        <button key={`middle-${color}`} className={`camel-bet-card ${color}`} disabled={!canBet || cards.length === 0} onClick={() => onTake(cards[0])}>
          <strong>{CAMEL_COLORS[color] ?? color}</strong>
          <span>赛段中间</span>
          <small>{cards[0] ? `赔率 ${cards[0].payouts?.join(" / ")}` : "已取走"}</small>
        </button>
        );
      })}
      <h3>{TEXT.finalBets}</h3>
      <div className="camel-final-market">
        {asArray(market.finalWinner).map((card) => (
          <button key={card.id} className={`camel-bet-card ${card.color}`} disabled={!canBet} onClick={() => onTake(card)}>
            <strong>{CAMEL_COLORS[card.color] ?? card.color}</strong>
            <span>最终赢家</span>
            <small>赔率 {card.payouts?.join(" / ")}</small>
          </button>
        ))}
        {asArray(market.finalLoser).map((card) => (
          <button key={card.id} className={`camel-bet-card ${card.color}`} disabled={!canBet} onClick={() => onTake(card)}>
            <strong>{CAMEL_COLORS[card.color] ?? card.color}</strong>
            <span>最终输家</span>
            <small>赔率 {card.payouts?.join(" / ")}</small>
          </button>
        ))}
      </div>
    </div>
  );
}

export { CamelUpCardsRoom, CamelPlayersPanel, CamelRankings, CamelSetupPanel, CamelTrack, CamelBetMarket };

export default CamelUpCardsRoom;
