import React, { useEffect, useState } from "react";
import { TEXT } from "../../features/rooms/roomText.js";
import { SeatBoard, NoticePanel } from "../../features/rooms/RoomPanels.jsx";
import {
  incanCardTypeText,
  incanPhaseText,
  incanPhaseHint,
  incanDisasterText,
  incanCardText,
  incanCardIcon,
  incanEventIcon,
  incanEventClass,
  incanPlayerName
} from "./helpers.jsx";

function IncanGoldRoom({ room, seats, currentSeat, onAction, actionPending = false }) {
  const state = room.gameState ?? {};
  const currentUserId = String(room.currentUser?.id ?? "");
  const phase = String(state.phase ?? "");
  const active = (state.activePlayers ?? []).map(String).includes(currentUserId);
  const pendingDecisions = state.pendingDecisions ?? {};
  const pendingTargets = state.pendingTheftTargets ?? {};
  const submittedDecision = Boolean(pendingDecisions[currentUserId]);
  const isThief = (state.thieves ?? []).map(String).includes(currentUserId);
  const submittedTarget = Boolean(pendingTargets[currentUserId]);
  const [targetId, setTargetId] = useState("");
  const [deckStatusOpen, setDeckStatusOpen] = useState(false);
  const [autoDrawVersion, setAutoDrawVersion] = useState(null);
  const [revealToken, setRevealToken] = useState("");
  const [dismissedDisasterKey, setDismissedDisasterKey] = useState("");
  const [dismissedSettlementKey, setDismissedSettlementKey] = useState("");
  const roadScrollRef = React.useRef(null);
  const previousRevealTokenRef = React.useRef("");
  const previousDisasterKeyRef = React.useRef("");
  const previousDisasterRoundRef = React.useRef(null);
  const previousSettlementKeyRef = React.useRef("");
  const previousSettlementRoundRef = React.useRef(null);

  useEffect(() => {
    setTargetId("");
  }, [state.version, phase]);

  function send(type, payload = {}) {
    onAction(type, payload);
  }

  const card = state.lastRevealedCard;
  const currentCards = Array.isArray(state.currentCards) ? state.currentCards : [];
  const revealKey = card?.id ? `${state.round}-${card.id}-${currentCards.length}` : "";
  const isRevealingCard = revealKey !== "" && revealToken === revealKey;
  const disasterFeedback = state.lastDisaster;
  const disasterFeedbackKey = disasterFeedback?.disaster
    ? `${disasterFeedback.disaster}-${disasterFeedback.threshold}-${(disasterFeedback.players ?? []).map((item) => item.userId).join(",")}`
    : "";
  const settlementFeedback = state.lastSettlement;
  const settlementFeedbackKey = settlementFeedback?.players?.length
    ? JSON.stringify({
      players: settlementFeedback.players,
      artifacts: settlementFeedback.artifacts,
      roadTreasureRemaining: settlementFeedback.roadTreasureRemaining
    })
    : "";
  const roadArtifacts = state.roadArtifacts ?? [];
  const players = state.players ?? [];
  const thiefIds = (state.thieves ?? []).map(String);
  const rankings = state.rankings ?? [];
  const targetCandidates = (state.targetCandidates ?? []).map(String);
  const deckCards = Array.isArray(state.deck) ? state.deck : [];
  const disasterKeys = ["collapse", "trap", "serpent", "idol", "swarm"];
  const deckTreasureCount = deckCards.filter((item) => item?.type === "treasure").length;
  const deckArtifactCount = deckCards.filter((item) => item?.type === "artifact").length;
  const deckDisasterCounts = Object.fromEntries(
    disasterKeys.map((key) => [key, deckCards.filter((item) => item?.type === "disaster" && item.disaster === key).length])
  );
  const lastDecisionReady = Object.keys(state.lastDecision ?? {}).length > 0;
  const lastDecisionChoices = state.lastDecision?.choices ?? {};
  const activePlayerIds = (state.activePlayers ?? []).map(String).sort((left, right) => Number(left) - Number(right));
  const pendingDecisionCount = Object.keys(pendingDecisions).length;
  const activePlayerCount = activePlayerIds.length;
  const thiefCount = (state.thieves ?? []).length;
  const pendingTargetCount = Object.keys(pendingTargets).length;
  const disasterThreshold = state.currentEvent?.id === "triple_challenge" ? 3 : 2;
  const autoAdvancePending = phase === "EXPLORATION"
    && lastDecisionReady
    && Object.values(lastDecisionChoices).includes("return")
    && deckCards.length > 0;

  useEffect(() => {
    if (!revealKey || revealKey === previousRevealTokenRef.current) {
      return;
    }
    previousRevealTokenRef.current = revealKey;
    setRevealToken(revealKey);
  }, [revealKey]);

  useEffect(() => {
    const round = Number(state.round ?? 0);
    if (
      disasterFeedbackKey
      && previousDisasterKeyRef.current === disasterFeedbackKey
      && previousDisasterRoundRef.current !== null
      && previousDisasterRoundRef.current !== round
    ) {
      setDismissedDisasterKey(disasterFeedbackKey);
    }
    previousDisasterKeyRef.current = disasterFeedbackKey;
    previousDisasterRoundRef.current = round;
  }, [disasterFeedbackKey, state.round]);

  useEffect(() => {
    const round = Number(state.round ?? 0);
    if (
      settlementFeedbackKey
      && previousSettlementKeyRef.current === settlementFeedbackKey
      && previousSettlementRoundRef.current !== null
      && previousSettlementRoundRef.current !== round
    ) {
      setDismissedSettlementKey(settlementFeedbackKey);
    }
    previousSettlementKeyRef.current = settlementFeedbackKey;
    previousSettlementRoundRef.current = round;
  }, [settlementFeedbackKey, state.round]);

  useEffect(() => {
    if (!autoAdvancePending || actionPending || !active || activePlayerIds[0] !== currentUserId) {
      return;
    }
    const version = Number(state.version ?? 0);
    if (autoDrawVersion === version) {
      return;
    }
    setAutoDrawVersion(version);
    onAction("draw_exploration_card");
  }, [actionPending, active, activePlayerIds, autoAdvancePending, autoDrawVersion, currentUserId, onAction, state.version]);

  useEffect(() => {
    const roadScroll = roadScrollRef.current;
    if (roadScroll) {
      roadScroll.scrollLeft = roadScroll.scrollWidth;
    }
  }, [currentCards.length, state.round]);

  const canDraw = phase === "EXPLORATION" && active && !actionPending;
  const canChoose = phase === "DECISION" && active && !submittedDecision && !actionPending;
  const canTarget = phase === "THEFT_TARGETS" && isThief && !submittedTarget && !actionPending;
  const actionAvailable = canDraw || canChoose || canTarget || autoAdvancePending || actionPending;

  return (
    <div className="incan-layout">
      <section className="panel incan-status-panel">
        <div className="game-room-heading">
          <h2>{TEXT.incanGold}</h2>
          <div className="incan-status-actions">
            <span>{TEXT.incanRound}: {state.round ?? 0} / 5</span>
            <button
              className="ghost-button small-button"
              type="button"
              aria-expanded={deckStatusOpen}
              onClick={() => setDeckStatusOpen((open) => !open)}
            >
              {TEXT.incanDeckStatus}
            </button>
          </div>
        </div>
        <div className="incan-meta-row">
          <span>{TEXT.incanPhase}: {incanPhaseText(phase)}</span>
          <span>{TEXT.incanDeck}: {(state.deck ?? []).length}</span>
          <span>{TEXT.incanRoadTreasure}: {state.roadTreasure ?? 0}颗</span>
          <span>{TEXT.incanRoadArtifacts}: {roadArtifacts.map(incanCardText).join("、") || "无"}</span>
        </div>
        <div className="incan-phase-note">{incanPhaseHint(phase, state.currentEvent?.id)}</div>
        <div className="incan-round-disasters" role="status">
          <strong>本轮灾难</strong>
          {disasterKeys.map((key) => (
            <span
              className={(state.disasterSeen?.[key] ?? 0) >= disasterThreshold - 1 ? "is-danger" : (state.disasterSeen?.[key] ?? 0) > 0 ? "is-warning" : ""}
              key={key}
            >
              <i aria-hidden="true">{incanDisasterText(key).slice(0, 1)}</i>
              {incanDisasterText(key)} {state.disasterSeen?.[key] ?? 0} / {disasterThreshold}
            </span>
          ))}
        </div>
        {state.currentEvent ? (
          <div className={`incan-event-card ${incanEventClass(state.currentEvent.id)}`} role="status">
            <div className="incan-event-card-topline">
              <span>{TEXT.incanEvent}</span>
              <span className="incan-event-card-round">本轮生效</span>
            </div>
            <div className="incan-event-card-main">
              <span className="incan-event-card-icon" aria-hidden="true">{incanEventIcon(state.currentEvent.id)}</span>
              <div>
                <strong>{state.currentEvent.name}</strong>
                <p>{state.currentEvent.description}</p>
              </div>
            </div>
          </div>
        ) : null}
        {deckStatusOpen ? (
          <button
            className="incan-deck-scrim"
            type="button"
            aria-label="关闭牌堆状况"
            onClick={() => setDeckStatusOpen(false)}
          />
        ) : null}
        {deckStatusOpen ? (
          <div className="incan-deck-status" role="status">
            <div className="incan-deck-status-heading">
              <strong>{TEXT.incanDeckStatus}</strong>
              <button className="ghost-button small-button" type="button" onClick={() => setDeckStatusOpen(false)}>
                关闭
              </button>
            </div>
            <div className="incan-deck-status-item">
              <span>宝藏牌</span>
              <strong>{deckTreasureCount}张</strong>
            </div>
            <div className="incan-deck-status-item incan-deck-disasters">
              <span>灾难牌</span>
              <div>
                {disasterKeys.map((key) => (
                  <span key={key}>{incanDisasterText(key)} {deckDisasterCounts[key]}张</span>
                ))}
              </div>
            </div>
            <div className="incan-deck-status-item">
              <span>神器牌</span>
              <strong>{deckArtifactCount}张</strong>
            </div>
          </div>
        ) : null}
      </section>

      <section className={`panel incan-road-panel ${phase === "FINISHED" ? "is-finished" : ""}`}>
        <div className="incan-section-heading">
          <h2>探险道路</h2>
          <span>{currentCards.length} 个发现</span>
        </div>
        <div
          className={`incan-reveal-card ${card ? `incan-reveal-card-${card.type}` : "is-empty"} ${isRevealingCard ? "is-revealing" : ""}`}
          key={revealKey || "empty-reveal"}
          aria-live="polite"
        >
          {card ? (
            <>
              <div className="incan-reveal-card-mark" aria-hidden="true">{incanCardIcon(card)}</div>
              <small>最新发现</small>
              <strong>{incanCardText(card)}</strong>
              {card.type === "treasure" ? <IncanTreasureValue value={card.value} /> : null}
            </>
          ) : (
            <>
              <div className="incan-reveal-card-mark" aria-hidden="true">?</div>
              <small>等待探险</small>
              <strong>尚未翻牌</strong>
            </>
          )}
        </div>
        <div className="incan-road-scroll" ref={roadScrollRef}>
          <div className="incan-road-track">
            <div className="incan-road-camp">
              <span className="incan-road-node-icon" aria-hidden="true">⌂</span>
              <strong>营地</strong>
              <small>安全返回</small>
            </div>
            {currentCards.map((roadCard, index) => (
              <React.Fragment key={`${roadCard.id ?? roadCard.type}-${index}`}>
                <span className="incan-road-connector" aria-hidden="true" />
                <div className={`incan-road-card incan-road-card-${roadCard.type} ${roadCard.id === card?.id ? "is-latest" : ""}`}>
                  <div className="incan-road-card-topline">
                    <small>第{index + 1}格</small>
                    <span className="incan-road-card-icon" aria-hidden="true">{incanCardIcon(roadCard)}</span>
                  </div>
                  <span className="incan-road-card-type">{incanCardTypeText(roadCard)}</span>
                  <strong>{incanCardText(roadCard)}</strong>
                  {roadCard.type === "treasure" ? <IncanTreasureValue value={roadCard.value} /> : null}
                </div>
              </React.Fragment>
            ))}
            <span className="incan-road-connector" aria-hidden="true" />
            <div className="incan-road-frontier">
              <span className="incan-road-node-icon" aria-hidden="true">…</span>
              <strong>未知道路</strong>
              <small>继续前进</small>
            </div>
          </div>
        </div>
        <div className="incan-road-summary">
          <span>{TEXT.incanRoadTreasure}：{state.roadTreasure ?? 0}颗绿松石</span>
          <span>{TEXT.incanRoadArtifacts}：{roadArtifacts.map(incanCardText).join("、") || "无"}</span>
        </div>
        <IncanDisasterSummary
          disaster={disasterFeedback}
          players={players}
          dismissed={dismissedDisasterKey === disasterFeedbackKey}
          onConfirm={() => setDismissedDisasterKey(disasterFeedbackKey)}
        />
        {phase === "DISASTER" ? <div className="form-error">灾难阶段：仍在探险的玩家本轮收益已清零。</div> : null}
        {phase === "FINISHED" ? (
          <div className="incan-winners">
            <strong>{TEXT.incanWinner}</strong>
            <span>{(state.winners ?? []).map((id) => incanPlayerName(players, id)).join("、") || "无"}</span>
          </div>
        ) : null}
      </section>

      <div className={`incan-play-grid ${phase === "FINISHED" ? "is-finished" : ""}`}>
        <section className={`panel incan-action-panel ${actionAvailable ? "is-actionable" : ""}`}>
          <h2>操作区</h2>
          {phase === "DECISION" ? (
            <div className="incan-progress-card" role="status">
              <div>
                <strong>决策进度</strong>
                <span>{pendingDecisionCount} / {activePlayerCount} 名探险者已提交</span>
              </div>
              <div className="incan-progress-track" aria-hidden="true">
                <span style={{ width: `${activePlayerCount ? (pendingDecisionCount / activePlayerCount) * 100 : 0}%` }} />
              </div>
            </div>
          ) : null}
          {phase === "THEFT_TARGETS" ? (
            <div className="incan-progress-card" role="status">
              <div>
                <strong>盗窃目标进度</strong>
                <span>{pendingTargetCount} / {thiefCount} 名盗贼已提交</span>
              </div>
              <div className="incan-progress-track" aria-hidden="true">
                <span style={{ width: `${thiefCount ? (pendingTargetCount / thiefCount) * 100 : 0}%` }} />
              </div>
            </div>
          ) : null}
          {autoAdvancePending ? (
            <p className="muted-note">返回结算完成，正在自动翻开下一张探险卡。</p>
          ) : phase === "EXPLORATION" ? (
            <button className="primary-button incan-action-button incan-action-draw" disabled={!canDraw} onClick={() => send("draw_exploration_card")}>
              <span className="incan-action-icon" aria-hidden="true">◆</span>
              <span><strong>{TEXT.incanDrawCard}</strong><small>推进探险道路，发现下一张牌</small></span>
            </button>
          ) : null}
          {phase === "DECISION" ? (
            <div className="incan-action-buttons">
              <button className="primary-button incan-action-button incan-action-continue" disabled={!canChoose} onClick={() => send("choose_expedition_action", { choice: "continue" })}>
                <span className="incan-action-icon" aria-hidden="true">→</span>
                <span><strong>{TEXT.incanContinue}</strong><small>继续寻找更多宝藏</small></span>
              </button>
              <button className="secondary-button incan-action-button incan-action-return" disabled={!canChoose} onClick={() => send("choose_expedition_action", { choice: "return" })}>
                <span className="incan-action-icon" aria-hidden="true">⌂</span>
                <span><strong>{TEXT.incanReturn}</strong><small>带回本轮已获得的宝藏</small></span>
              </button>
              {room.config?.thievesExpansion === true ? (
                <button className="ghost-button incan-action-button incan-action-steal" disabled={!canChoose || Boolean(players.find((player) => String(player.userId) === currentUserId)?.stealUsed)} onClick={() => send("choose_expedition_action", { choice: "steal" })}>
                  <span className="incan-action-icon" aria-hidden="true">✦</span>
                  <span><strong>{TEXT.incanSteal}</strong><small>使用一次盗贼扩机会</small></span>
                </button>
              ) : null}
              {submittedDecision ? <p className="muted-note">{TEXT.incanSubmitted}，等待其他玩家。</p> : null}
            </div>
          ) : null}
          {phase === "THEFT_TARGETS" ? (
            <div className="incan-target-panel">
              {isThief ? (
                <>
                  <p>请选择一名盗窃目标：</p>
                  <select value={targetId} onChange={(event) => setTargetId(event.target.value)} disabled={!canTarget}>
                    <option value="">请选择玩家</option>
                    {players.filter((player) => targetCandidates.includes(String(player.userId))).map((player) => (
                      <option key={player.userId} value={player.userId}>{player.username}</option>
                    ))}
                  </select>
                  <button className="primary-button" disabled={!canTarget || !targetId} onClick={() => send("submit_theft_target", { targetUserId: Number(targetId) })}>
                    {TEXT.incanSubmit}
                  </button>
                </>
              ) : (
                <p className="muted-note">盗贼正在同时选择目标，请等待结算。</p>
              )}
            </div>
          ) : null}
          {phase !== "EXPLORATION" && phase !== "DECISION" && phase !== "THEFT_TARGETS" && phase !== "FINISHED" ? (
            <p className="muted-note">当前阶段将自动结算。</p>
          ) : null}
        </section>

        <IncanSettlementSummary
          settlement={settlementFeedback}
          players={players}
          dismissed={dismissedSettlementKey === settlementFeedbackKey}
          onConfirm={() => setDismissedSettlementKey(settlementFeedbackKey)}
        />

        <section className="panel incan-players-panel">
          <h2>探险队</h2>
          <div className="incan-player-list">
            {players.map((player, index) => (
              <div className={`incan-player-row ${String(player.userId) === currentUserId ? "is-current" : ""} ${player.active ? "is-active" : "is-returned"} ${thiefIds.includes(String(player.userId)) ? "is-thief" : ""}`} key={player.userId}>
                <div className="incan-player-heading">
                  <span className="incan-player-token" aria-hidden="true">{index + 1}</span>
                  <div className="incan-player-name">
                    <strong>{player.username}</strong>
                    {thiefIds.includes(String(player.userId)) ? <small>盗贼</small> : null}
                  </div>
                  <span className="incan-player-status">{player.active ? TEXT.incanActive : TEXT.incanReturned}</span>
                </div>
                <div className="incan-player-stat-grid">
                  <span><small>{TEXT.incanTreasure}</small><strong>{player.roundTreasure ?? 0}</strong></span>
                  <span><small>{TEXT.incanTotal}</small><strong>{player.totalTreasure ?? 0}</strong></span>
                  <span><small>{TEXT.incanArtifacts}</small><strong>{(player.artifacts ?? []).length}</strong></span>
                </div>
                <div className="incan-player-artifacts">
                  {(player.artifacts ?? []).map((artifact, artifactIndex) => (
                    <span key={`${artifact.id ?? artifact.value}-${artifactIndex}`} title={incanCardText(artifact)}>{artifact.value}</span>
                  ))}
                  {(player.artifacts ?? []).length === 0 ? <small>暂无神器</small> : null}
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>

        {phase === "FINISHED" ? (
          <section className="panel incan-ranking-panel">
            <div className="incan-final-heading">
              <div>
                <span className="incan-final-kicker">五轮探险完成</span>
                <h2>最终排名</h2>
              </div>
              <strong>{(state.winners ?? []).length > 1 ? "并列获胜" : "探险结束"}</strong>
            </div>
            <div className="incan-ranking-list">
              {rankings.map((player, index) => (
                <div className={`incan-ranking-row rank-${Math.min(index + 1, 3)} ${(state.winners ?? []).map(String).includes(String(player.userId)) ? "is-winner" : ""}`} key={player.userId}>
                  <span className="incan-ranking-rank">{index === 0 ? "冠" : index === 1 ? "亚" : index === 2 ? "季" : index + 1}</span>
                  <div className="incan-ranking-player">
                    <strong>{player.username}</strong>
                    {(state.winners ?? []).map(String).includes(String(player.userId)) ? <span>获胜者</span> : null}
                  </div>
                  <div className="incan-ranking-metrics">
                    <span><small>宝藏</small><strong>{player.totalTreasure}</strong></span>
                    <span><small>神器</small><strong>{player.artifactCount}</strong></span>
                    <span><small>最高神器</small><strong>{player.highestArtifact || "—"}</strong></span>
                  </div>
                </div>
              ))}
            </div>
            <p className="incan-ranking-note">排名依次比较宝藏总值、神器数量和单个神器最高价值。</p>
          </section>
        ) : null}

        <NoticePanel notices={room.notices ?? []} />

      <section className="panel room-seat-panel compact">
        <SeatBoard owner={false} waiting={false} seats={seats} showKick={false} />
      </section>
    </div>
  );
}

function IncanTreasureValue({ value }) {
  return (
    <span className="incan-treasure-value">
      <i aria-hidden="true">◆</i>
      <strong>{value}</strong>
      <small>颗绿松石</small>
    </span>
  );
}

function IncanSettlementSummary({ settlement, players, dismissed, onConfirm }) {
  const settlementPlayers = Array.isArray(settlement?.players) ? settlement.players : [];
  if (dismissed || settlementPlayers.length === 0) {
    return null;
  }
  const artifacts = Array.isArray(settlement.artifacts) ? settlement.artifacts : [];
  return (
    <div className="incan-settlement-card" role="status">
      <div className="incan-feedback-heading">
        <strong>最近返回结算</strong>
        <span>道路剩余 {settlement.roadTreasureRemaining ?? 0} 颗绿松石</span>
      </div>
      <div className="incan-settlement-list">
        {settlementPlayers.map((item) => (
          <div className={`incan-settlement-row ${item.attacked ? "is-attacked" : ""}`} key={item.userId}>
            <strong>{incanPlayerName(players, item.userId)}</strong>
            {item.attacked ? (
              <span>被袭击，损失 {item.lostTreasure ?? 0} 颗</span>
            ) : (
              <span>带回 {item.carriedTreasure ?? 0} 颗，分到道路 {item.roadTreasure ?? 0} 颗</span>
            )}
          </div>
        ))}
      </div>
      {artifacts.length > 0 ? (
        <div className="incan-feedback-artifacts">
          获得神器：{artifacts.map(incanCardText).join("、")}
        </div>
      ) : null}
      <button className="ghost-button small-button incan-feedback-confirm" type="button" onClick={onConfirm}>
        知道了
      </button>
    </div>
  );
}

function IncanDisasterSummary({ disaster, players, dismissed, onConfirm }) {
  const affectedPlayers = Array.isArray(disaster?.players) ? disaster.players : [];
  if (!disaster || dismissed || affectedPlayers.length === 0) {
    return null;
  }
  return (
    <div className="incan-disaster-card" role="alert">
      <div className="incan-feedback-heading">
        <strong><span className="incan-disaster-mark" aria-hidden="true">!</span> {incanDisasterText(disaster.disaster)}阶段</strong>
        <span>第 {disaster.threshold} 次出现</span>
      </div>
      <p>仍在探险的玩家本轮绿松石已清零，道路收益不计入任何玩家。</p>
      <div className="incan-disaster-players">
        {affectedPlayers.map((item) => (
          <span key={item.userId}>{incanPlayerName(players, item.userId)}：清除 {item.treasure ?? 0} 颗</span>
        ))}
      </div>
      <button className="ghost-button small-button incan-feedback-confirm" type="button" onClick={onConfirm}>
        知道了
      </button>
    </div>
  );
}

export { IncanGoldRoom, IncanTreasureValue, IncanSettlementSummary, IncanDisasterSummary };

export default IncanGoldRoom;
