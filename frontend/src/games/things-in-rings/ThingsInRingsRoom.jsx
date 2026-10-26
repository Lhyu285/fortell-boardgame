import React, { useEffect, useState } from "react";
import { TEXT } from "../../features/rooms/roomText.js";
import { SeatBoard, NoticePanel } from "../../features/rooms/RoomPanels.jsx";
import {
  THINGS_AREAS,
  thingsPhaseText,
  thingsPhaseIcon,
  thingsPhaseHint,
  coloredAreaLabel,
  coloredAreaShortLabel,
  wordText,
  ruleText,
  areaShort
} from "./helpers.jsx";

function ThingsInRingsRoom({ room, seats, currentSeat, owner, onAction }) {
  const state = room.gameState ?? {};
  const [selectedWordId, setSelectedWordId] = useState("");
  const [correcting, setCorrecting] = useState(false);
  const [correctArea, setCorrectArea] = useState("");

  const currentUserId = String(room.currentUser?.id ?? "");
  const hostId = String(state.hostPlayerId ?? "");
  const currentPlayerId = String(state.currentPlayerId ?? "");
  const isHost = currentUserId === hostId;
  const isCurrentGuesser = state.phase === "GUESSER_TURN" && currentPlayerId === currentUserId;
  const isSpectator = !currentSeat;
  const hands = state.playerHands ?? {};
  const currentHand = hands[currentUserId] ?? [];
  const selectedWord = currentHand.find((word) => String(word.id) === selectedWordId);
  const pendingGuess = state.pendingGuess ?? null;
  const canSeeRules = state.phase === "GAME_OVER"
    || isHost
    || (isSpectator && room.config?.spectatorView === "god");

  useEffect(() => {
    setSelectedWordId("");
  }, [state.currentPlayerId, state.phase]);

  useEffect(() => {
    setCorrecting(false);
    setCorrectArea("");
  }, [pendingGuess?.word?.id, pendingGuess?.area]);

  function confirmed(message, type, payload = {}) {
    if (window.confirm(message)) {
      onAction(type, payload);
    }
  }

  if (state.phase === "SELECTING_HOST") {
    return (
      <div className="things-layout">
        <section className="panel things-host-select">
          <h2>{TEXT.host}</h2>
          <div className="things-player-list">
            {(state.players ?? []).map((player) => (
              <div className="things-player-card" key={String(player.userId)}>
                <strong>{player.seatIndex + 1}. {player.username}</strong>
                {owner ? (
                  <button
                    className="secondary-button small-button"
                    onClick={() => confirmed(`Set ${player.username} as host?`, "set_host", { userId: player.userId })}
                  >
                    {TEXT.selectHost}
                  </button>
                ) : null}
              </div>
            ))}
          </div>
          {owner ? (
            <button className="primary-button" onClick={() => confirmed("Randomly set host?", "random_set_host")}>
              {TEXT.randomHost}
            </button>
          ) : null}
        </section>
        <NoticePanel notices={room.notices ?? []} />
      </div>
    );
  }

  return (
    <div className="things-layout">
      {state.endProposalText ? <div className="proposal-banner">{state.endProposalText}</div> : null}

      <ThingsRulesPanel rules={state.rules ?? {}} canSeeRules={canSeeRules} />

      <section className={`things-phase-banner things-phase-${String(state.phase ?? "").toLowerCase()}`} role="status">
        <span className="things-phase-icon" aria-hidden="true">{thingsPhaseIcon(state.phase)}</span>
        <div>
          <small>当前阶段</small>
          <strong>{thingsPhaseText(state.phase)}</strong>
          <span>{thingsPhaseHint(state.phase, currentPlayerId, state.players ?? [])}</span>
        </div>
      </section>

      {state.phase === "HOST_INITIAL_PLACEMENT" || state.phase === "HOST_TURN" ? (
        <HostActionPanel state={state} isHost={isHost} onAction={onAction} />
      ) : null}

      {pendingGuess && isHost ? (
        <section className="panel things-judge-panel">
          <div className="things-judge-heading">
            <span className="things-judge-icon" aria-hidden="true">?</span>
            <div>
              <h2>{TEXT.pendingJudge}</h2>
              <p>请确认这张词语卡应该放入哪个区域</p>
            </div>
          </div>
          {!correcting ? (
            <>
              <div className="things-judge-guess">
                <span>{pendingGuess.playerName} 的判断</span>
                <strong>{wordText(pendingGuess.word)}</strong>
                <small>尝试放入 {areaShort(pendingGuess.area)} 区域</small>
              </div>
              <div className="button-inline">
                <button
                  className="primary-button"
                  onClick={() => confirmed("Mark placement correct?", "host_judge", { correct: true })}
                >
                  {TEXT.correct}
                </button>
                <button className="secondary-button" onClick={() => setCorrecting(true)}>
                  {TEXT.incorrect}
                </button>
              </div>
            </>
          ) : (
            <>
              <p>{TEXT.chooseCorrectArea}</p>
              <div className="area-button-grid">
                {THINGS_AREAS.map((area) => (
                  <button
                    key={area.key}
                    className={correctArea === area.key ? "active" : ""}
                    disabled={area.key === pendingGuess.area}
                    onClick={() => setCorrectArea(area.key)}
                  >
                    {coloredAreaShortLabel(area)}
                  </button>
                ))}
              </div>
              <div className="button-inline">
                <button
                  className="primary-button"
                  disabled={!correctArea}
                  onClick={() => confirmed("Submit corrected area?", "host_judge", { correct: false, correctArea })}
                >
                  {TEXT.confirmMove}
                </button>
                <button className="ghost-button" onClick={() => setCorrecting(false)}>
                  {TEXT.back}
                </button>
              </div>
            </>
          )}
        </section>
      ) : null}

      <div className="things-main-grid">
        <ThingsPlayersPanel
          players={state.players ?? []}
          hands={hands}
          currentUserId={currentUserId}
          hostId={hostId}
          currentPlayerId={currentPlayerId}
          selectedWordId={selectedWordId}
          canSelect={isCurrentGuesser && !pendingGuess}
          revealAllHands={isSpectator}
          onSelectWord={setSelectedWordId}
          canEndTurn={isCurrentGuesser && state.hasCurrentGuesserPlacedCorrectly === true && !pendingGuess}
          onEndTurn={() => confirmed("End turn?", "end_turn")}
        />

        <ThingsAreaBoard
          placedWords={state.placedWords ?? {}}
          selectedWord={selectedWord}
          canPlace={isCurrentGuesser && !pendingGuess}
          recentNotice={room.notices?.[0]}
          onPlace={(area) => confirmed(`Place ${wordText(selectedWord)} to ${areaShort(area)}?`, "guesser_submit", {
            wordId: selectedWord?.id,
            area
          })}
        />

        <NoticePanel notices={room.notices ?? []} />
      </div>

      {state.phase === "GAME_OVER" ? (
        <section className="panel things-game-over">
          <h2>推理完成</h2>
          <p className="things-winner-line"><strong>{TEXT.winner}</strong>：{(state.winners ?? []).map((winner) => winner.username).join("、") || "无"}</p>
          <ThingsFinalSummary players={state.players ?? []} hostId={hostId} hands={hands} placedWords={state.placedWords ?? {}} />
        </section>
      ) : null}

      <section className="panel room-seat-panel compact">
        <SeatBoard owner={owner} waiting={false} seats={seats} showKick={false} />
      </section>
    </div>
  );
}

function ThingsRulesPanel({ rules, canSeeRules }) {
  return (
    <section className="panel things-rules-panel">
      <div className="things-panel-heading">
        <div>
          <h2>隐藏规则</h2>
          <p>三条规则决定词语应该进入哪些环</p>
        </div>
        <span className="things-rule-visibility">{canSeeRules ? "规则可见" : "规则隐藏"}</span>
      </div>
      <div className="things-rule-grid">
        <RuleBox type="scene" label={TEXT.sceneRule} value={canSeeRules ? ruleText(rules.SCENE) : ""} canSeeRules={canSeeRules} />
        <RuleBox type="word" label={TEXT.wordRule} value={canSeeRules ? ruleText(rules.WORD) : ""} canSeeRules={canSeeRules} />
        <RuleBox type="attribute" label={TEXT.attributeRule} value={canSeeRules ? ruleText(rules.ATTRIBUTE) : ""} canSeeRules={canSeeRules} />
      </div>
    </section>
  );
}

function RuleBox({ type, label, value, canSeeRules }) {
  return (
    <article className={`things-rule-box ${type}`}>
      <div className="things-rule-box-heading">
        <span className="things-rule-symbol" aria-hidden="true">{type === "scene" ? "情" : type === "word" ? "词" : "属"}</span>
        <strong>{label}</strong>
        <span className="things-rule-lock" aria-hidden="true">{canSeeRules ? "◉" : "⌕"}</span>
      </div>
      <p>{canSeeRules ? value || "尚未设置规则" : "规则隐藏"}</p>
    </article>
  );
}

function HostActionPanel({ state, isHost, onAction }) {
  const word = state.currentHostWord;
  const inInitial = state.phase === "HOST_INITIAL_PLACEMENT";

  function confirmAction(message, type, payload = {}) {
    if (window.confirm(message)) {
      onAction(type, payload);
    }
  }

  return (
    <section className="panel things-host-action">
      <div className="things-action-heading">
        <span className="things-action-icon" aria-hidden="true">✦</span>
        <div>
          <h2>{inInitial ? "主持人布置提示" : TEXT.currentTurn}</h2>
          <span>{inInitial ? `还需放置 ${state.initialPlacementsRemaining ?? 0} 张提示词` : "选择一个词语放入对应区域"}</span>
        </div>
      </div>
      {word ? (
        <>
          <div className="things-host-word-card">{wordText(word)}</div>
          {isHost ? (
            <div className="area-button-grid">
              {THINGS_AREAS.map((area) => (
                <button
                  key={area.key}
                  onClick={() => confirmAction(`确定将${wordText(word)}放入${area.label}区域吗？`, "host_place", { area: area.key })}
                >
                  {coloredAreaLabel(area)}
                </button>
              ))}
            </div>
          ) : <p className="muted-note">等待主持人放置提示词。</p>}
        </>
      ) : null}
      {!inInitial && isHost ? (
        <div className="button-inline">
          <button
            className="secondary-button"
            disabled={state.hostHintUsed === true || Boolean(word)}
            onClick={() => confirmAction("确定使用提示吗？", "host_hint")}
          >
            {TEXT.hint}
          </button>
          <button
            className="secondary-button"
            disabled={state.hostSkipUsed === true || Boolean(word)}
            onClick={() => confirmAction("确定结束主持人回合吗？", "host_skip")}
          >
            {TEXT.skip}
          </button>
        </div>
      ) : null}
    </section>
  );
}

function ThingsPlayersPanel({
  players,
  hands,
  currentUserId,
  hostId,
  currentPlayerId,
  selectedWordId,
  canSelect,
  revealAllHands,
  onSelectWord,
  canEndTurn,
  onEndTurn
}) {
  return (
    <section className="panel things-players-panel">
      <h2>{TEXT.playerArea}</h2>
      <div className="things-player-list">
        {players.map((player) => {
          const playerId = String(player.userId);
          const hand = hands[playerId] ?? [];
          const showCards = playerId === currentUserId || revealAllHands;
          const isCurrent = playerId === currentPlayerId;
          return (
            <div className={`things-player-card ${isCurrent ? "active" : ""}`} key={playerId}>
              <div className="things-player-head">
                <div className="things-player-name">
                  <span className="things-player-avatar" aria-hidden="true">{player.username?.slice(0, 1) ?? "玩"}</span>
                  <strong>{player.seatIndex + 1}. {player.username}</strong>
                </div>
                <span>{playerId === hostId ? "主持人" : isCurrent ? "当前回合" : "竞猜者"}</span>
              </div>
              <div className="things-player-status-row">
                <small>{TEXT.handCount}：{hand.length} 张</small>
                <small className={isCurrent ? "is-current" : ""}>{isCurrent ? "行动中" : "等待中"}</small>
              </div>
              {showCards ? (
                <div className="things-hand">
                  {hand.map((word) => (
                    <button
                      key={word.id}
                      className={`things-word-card ${selectedWordId === String(word.id) ? "active" : ""}`}
                      disabled={playerId !== currentUserId || !canSelect}
                      onClick={() => onSelectWord(String(word.id))}
                    >
                      <strong>{wordText(word)}</strong>
                      <small>{selectedWordId === String(word.id) ? "已选择" : "词语卡"}</small>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
      <button className="primary-button" disabled={!canEndTurn} onClick={onEndTurn}>
        {TEXT.endTurn}
      </button>
    </section>
  );
}

function ThingsAreaBoard({ placedWords, selectedWord, canPlace, recentNotice, onPlace }) {
  const [viewingAreaKey, setViewingAreaKey] = useState("");
  const viewingArea = THINGS_AREAS.find((area) => area.key === viewingAreaKey);
  const viewingWords = viewingArea ? (placedWords[viewingArea.key] ?? []) : [];

  return (
    <section className="panel things-areas-panel">
      <div className="things-panel-heading">
        <div>
          <h2>{TEXT.wordDisplayArea}</h2>
          <p>{selectedWord ? `已选择：${wordText(selectedWord)}，请选择它所属的环` : "观察词语如何分布在三个环中"}</p>
          <small className="things-area-help">点击各区域的“查看全部”按钮，可以查看该区域内的全部词语</small>
        </div>
        <span className="things-board-legend"><i className="area-scene" />情境 <i className="area-word" />词汇 <i className="area-attribute" />属性</span>
      </div>
      <div className="things-ring-board">
        <div className="things-ring-orbit things-ring-orbit-scene" aria-hidden="true" />
        <div className="things-ring-orbit things-ring-orbit-word" aria-hidden="true" />
        <div className="things-ring-orbit things-ring-orbit-attribute" aria-hidden="true" />
        <div className="things-area-grid">
        {THINGS_AREAS.map((area) => {
          const words = placedWords[area.key] ?? [];
          const latestWord = words.at(-1);
          return (
            <div
              className={`things-area-card things-area-${area.key.toLowerCase()} ${words.length ? "has-words" : "is-empty"} ${selectedWord && canPlace ? "is-placing" : ""}`}
              key={area.key}
            >
              <div className="things-area-card-heading">
                <strong>{coloredAreaLabel(area)}</strong>
                <small>{words.length} 张</small>
              </div>
              <div className="placed-word-list">
                {latestWord ? (
                  <span className="things-placed-word">
                    {wordText(latestWord)}
                  </span>
                ) : null}
              </div>
              <div className="things-area-card-actions">
                <button
                  className="ghost-button small-button things-area-view-button"
                  disabled={!words.length}
                  onClick={() => setViewingAreaKey(area.key)}
                >
                  {words.length ? "查看全部" : "暂无词语"}
                </button>
                {selectedWord && canPlace ? (
                  <button className="ghost-button small-button" onClick={() => onPlace(area.key)}>
                    放入这里
                  </button>
                ) : null}
              </div>
            </div>
          );
        })}
        </div>
      </div>
      {viewingArea ? (
        <div className="things-area-modal-backdrop" role="presentation" onClick={() => setViewingAreaKey("")}>
          <div
            className="things-area-modal"
            role="dialog"
            aria-modal="true"
            aria-label={`${areaShort(viewingArea.key)}区域词语`}
            onClick={(event) => event.stopPropagation()}
          >
            <div className="things-area-modal-header">
              <div>
                <small>区域词语</small>
                <h3>{coloredAreaLabel(viewingArea)}</h3>
              </div>
              <button className="ghost-button small-button" onClick={() => setViewingAreaKey("")}>
                关闭
              </button>
            </div>
            <div className="things-area-modal-list">
              {viewingWords.map((word, index) => (
                <div className="things-area-modal-word" key={word.id}>
                  <i>{index + 1}</i>
                  <strong>{wordText(word)}</strong>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : null}
      {recentNotice ? (
        <div className="things-feedback-banner" role="status">
          <span className="things-feedback-icon" aria-hidden="true">↗</span>
          <div>
            <small>最近判定</small>
            <strong>{recentNotice}</strong>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function ThingsFinalSummary({ players, hostId, hands, placedWords }) {
  const placedCount = Object.values(placedWords ?? {}).reduce(
    (total, words) => total + (Array.isArray(words) ? words.length : 0),
    0
  );
  const activeAreaCount = Object.values(placedWords ?? {}).filter((words) => Array.isArray(words) && words.length > 0).length;
  const guesserCount = players.filter((player) => String(player.userId) !== String(hostId)).length;
  return (
    <div className="things-final-summary">
      <div className="things-final-metric"><strong>{placedCount}</strong><span>已归类词语</span></div>
      <div className="things-final-metric"><strong>{activeAreaCount}</strong><span>有词语区域</span></div>
      <div className="things-final-metric"><strong>{guesserCount}</strong><span>参与推理</span></div>
      <div className="things-final-players">
        {players.map((player) => (
          <span key={player.userId}>{player.username}：剩余 {Array.isArray(hands[String(player.userId)]) ? hands[String(player.userId)].length : 0} 张</span>
        ))}
      </div>
    </div>
  );
}

export { ThingsInRingsRoom, ThingsRulesPanel, RuleBox, HostActionPanel, ThingsPlayersPanel, ThingsAreaBoard, ThingsFinalSummary };

export default ThingsInRingsRoom;
