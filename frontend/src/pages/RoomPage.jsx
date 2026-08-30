import React, { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import PageShell from "../components/PageShell";
import RuleModal from "../components/RuleModal";
import { apiDelete, apiGet, apiPost } from "../lib/api";
import { createRoomSocket } from "../lib/websocket";
import birminghamMapLayout from "../assets/config/birmingham-map-layout.json";

const TEXT = {
  rules: "\u67e5\u770b\u89c4\u5219",
  leave: "\u9000\u51fa\u623f\u95f4",
  dismiss: "\u89e3\u6563\u623f\u95f4",
  loading: "\u52a0\u8f7d\u623f\u95f4\u4e2d...",
  seatCount: "\u5ea7\u4f4d\u6570",
  addSeat: "+1\u5ea7\u4f4d",
  removeSeat: "-1\u5ea7\u4f4d",
  owner: "\u623f\u4e3b",
  me: "\u6211",
  kick: "\u00d7",
  switchSeat: "\u6362\u4f4d",
  standUp: "\u8d77\u8eab",
  startGame: "\u5f00\u59cb\u6e38\u620f",
  endGame: "\u7ed3\u675f\u6e38\u620f",
  confirmEndGame: "\u662f\u5426\u7ed3\u675f\u6e38\u620f\uff1f",
  proposeEnd: "\u63d0\u8bae\u7ed3\u675f",
  cancelProposeEnd: "\u53d6\u6d88\u63d0\u8bae\u7ed3\u675f",
  proposed: "\u5df2\u63d0\u8bae",
  seatCountTooSmall: "\u5ea7\u4f4d\u6570\u4e0d\u80fd\u5c11\u4e8e\u5f53\u524d\u5165\u5ea7\u4eba\u6570",
  seatCountOutOfRange: "\u652f\u6301\u5ea7\u4f4d\u6570\uff1a{min}-{max}",
  notices: "\u623f\u95f4\u6d88\u606f",
  rpsTitle: "\u731c\u62f3\u5bf9\u5c40",
  rpsWaiting: "\u7b49\u5f85\u73a9\u5bb6\u51fa\u62f3",
  rpsSubmitted: "\u4f60\u5df2\u51fa\u62f3",
  spectator: "\u89c2\u6218\u4e2d",
  reserved: "\u6e38\u620f\u754c\u9762\u9884\u7559\u4e2d",
  rpsSettings: "\u731c\u62f3\u8bbe\u7f6e",
  moveSet: "\u62f3\u6cd5\u79cd\u7c7b",
  moveSetThree: "\u4e09\u79cd",
  moveSetFive: "\u4e94\u79cd",
  rpsMode: "\u731c\u62f3\u6a21\u5f0f",
  freeForAll: "\u5927\u4e71\u6597",
  bracket: "\u6dd8\u6c70\u8d5b",
  relationTitle: "\u514b\u5236\u5173\u7cfb",
  battleArea: "\u5bf9\u6218\u533a",
  actionArea: "\u64cd\u4f5c\u533a",
  confirmMove: "\u786e\u5b9a",
  selectMove: "\u8bf7\u9009\u62e9\u4e00\u79cd\u62f3\u6cd5",
  waitingChoice: "\u7b49\u5f85\u9009\u62e9",
  completedChoice: "\u5df2\u5b8c\u6210\u9009\u62e9",
  candidate: "\u5019\u573a\u4e2d",
  wins: "\u80dc\u573a",
  round: "\u7b2c{round}\u8f6e",
  rps: "\u731c\u62f3",
  thingsInRings: "\u73af\u4e2d\u7269\u8bed",
  thingsSettings: "\u73af\u4e2d\u7269\u8bed\u8bbe\u7f6e",
  difficulty: "\u96be\u5ea6",
  easy: "\u7b80\u5355",
  medium: "\u4e2d\u7b49",
  hard: "\u56f0\u96be",
  custom: "\u81ea\u5b9a\u4e49",
  spectatorView: "\u89c2\u6218\u89c6\u89d2",
  godView: "\u4e0a\u5e1d\u89c6\u89d2",
  playerView: "\u73a9\u5bb6\u89c6\u89d2",
  sceneRule: "\u60c5\u5883\u89c4\u5219",
  wordRule: "\u8bcd\u6c47\u89c4\u5219",
  attributeRule: "\u5c5e\u6027\u89c4\u5219",
  selectHost: "\u8bbe\u7f6e\u4e3a\u4e3b\u6301\u4eba",
  randomHost: "\u968f\u673a\u8bbe\u7f6e\u4e3b\u6301\u4eba",
  host: "\u4e3b\u6301\u4eba",
  guesser: "\u7ade\u731c\u8005",
  handCount: "\u624b\u724c",
  currentTurn: "\u5f53\u524d\u56de\u5408",
  placeHere: "\u653e\u5230\u6b64\u533a\u57df",
  endTurn: "\u7ed3\u675f\u56de\u5408",
  hint: "\u63d0\u793a",
  skip: "\u8df3\u8fc7",
  returnPrepare: "\u8fd4\u56de\u51c6\u5907\u623f\u95f4",
  hostArea: "\u4e3b\u6301\u4eba\u533a",
  playerArea: "\u73a9\u5bb6\u533a",
  wordDisplayArea: "\u8bcd\u6c47\u5c55\u793a\u533a",
  pendingJudge: "\u7b49\u5f85\u4e3b\u6301\u4eba\u5224\u65ad",
  correct: "\u662f",
  incorrect: "\u5426",
  chooseCorrectArea: "\u8bf7\u9009\u62e9\u6b63\u786e\u7684\u533a\u57df",
  back: "\u8fd4\u56de",
  winner: "\u83b7\u80dc",
  camelUpCards: "\u72c2\u91ce\u9a86\u9a7c\u5361\u724c\u7248",
  camelSettings: "\u72c2\u91ce\u9a86\u9a7c\u8bbe\u7f6e",
  expansion: "\u6269\u5c55\u89c4\u5219",
  expansionNone: "\u65e0",
  expansionShortcut: "\u4ec5\u6377\u5f84",
  expansionFennec: "\u4ec5\u8033\u5ed3\u72d0",
  expansionBoth: "\u6377\u5f84+\u8033\u5ed3\u72d0",
  egyptPounds: "\u57c3\u53ca\u9551",
  trackArea: "\u8d5b\u9053\u533a",
  camelRankings: "\u7ade\u8d5b\u9a86\u9a7c\u6392\u540d",
  bettingCards: "\u7ade\u731c\u5361\u724c\u533a",
  drawRaceCard: "\u7ffb\u5f00\u7ade\u8d5b\u724c",
  playOwnHand: "\u6253\u51fa\u81ea\u5df1\u624b\u724c",
  playPlayerHand: "\u6253\u51fa{player}\u624b\u724c",
  skipBet: "\u8df3\u8fc7\u4e0b\u6ce8",
  placeShortcut: "\u653e\u7f6e\u6377\u5f84",
  placeFennec: "\u653e\u7f6e\u8033\u5ed3\u72d0",
  currentPlayer: "\u5f53\u524d\u73a9\u5bb6",
  raceDeck: "\u7ade\u8d5b\u724c\u5e93",
  temporaryDiscard: "\u4e34\u65f6\u5f03\u724c",
  legBets: "\u8d5b\u6bb5\u4e0b\u6ce8",
  finalBets: "\u6700\u7ec8\u4e0b\u6ce8",
  finalWinner: "\u5927\u8d62\u5bb6",
  finalLoser: "\u5927\u8f93\u5bb6",
  legWinner: "\u8d5b\u6bb5\u8d62\u5bb6",
  legMiddle: "\u8d5b\u6bb5\u4e2d\u95f4\u4f4d",
  sandstorm: "\u6c99\u5c18\u66b4",
  setupDiscardPrompt: "\u8bf7\u9009\u62e9{count}\u5f20\u5361\u724c\u79d8\u5bc6\u5f03\u7f6e",
  setupDeckPrompt: "\u8bf7\u9009\u62e9{count}\u5f20\u5361\u724c\u52a0\u5165\u672c\u8d5b\u6bb5\u7ade\u8d5b\u724c\u5e93",
  confirmSelection: "\u786e\u5b9a\u9009\u62e9",
  setupDone: "\u5df2\u5b8c\u6210\u672c\u8d5b\u6bb5\u6784\u7b51\uff0c\u7b49\u5f85\u5176\u4ed6\u73a9\u5bb6",
  gobang: "\u4e94\u5b50\u68cb",
  brass: "\u4f2f\u660e\u7ff0",
  brassEra: "\u65f6\u4ee3",
  brassRound: "\u8f6e\u6b21",
  brassActions: "\u5269\u4f59\u884c\u52a8",
  brassMoney: "\u91d1\u94b1",
  brassIncome: "\u6536\u5165\u7b49\u7ea7",
  brassLastIncome: "\u4e0a\u8f6e\u6536\u5165",
  brassSpent: "\u672c\u8f6e\u82b1\u8d39",
  brassVp: "\u80dc\u5229\u70b9",
  brassHand: "\u624b\u724c",
  brassLoan: "\u8d37\u6b3e",
  brassBuild: "\u5efa\u9020",
  brassSell: "\u51fa\u552e",
  brassNetwork: "\u8fd0\u8f93\u7f51",
  brassDevelop: "\u7814\u53d1",
  brassMaintainEra: "\u65f6\u4ee3\u7ef4\u62a4",
  brassEndTurn: "\u7ed3\u675f\u56de\u5408",
  brassScout: "\u4fa6\u67e5",
  brassSkip: "\u8df3\u8fc7\u884c\u52a8",
  brassRestartTurn: "\u91cd\u65b0\u5f00\u59cb\u672c\u56de\u5408",
  brassActionLog: "\u884c\u52a8\u8bb0\u5f55",
  brassDeck: "\u724c\u5e93",
  brassDiscard: "\u5f03\u724c",
  canalEra: "\u8fd0\u6cb3\u65f6\u4ee3",
  railEra: "\u94c1\u8def\u65f6\u4ee3"
};

const MOVE_LABELS = {
  stone: "\u77f3\u5934",
  scissors: "\u526a\u5200",
  paper: "\u5e03",
  lizard: "\u8725\u8734",
  spock: "\u53f2\u6ce2\u514b"
};

const THINGS_AREAS = [
  { key: "SCENE", label: "\u60c5\u5883", short: "\u7ea2", parts: ["scene"] },
  { key: "WORD", label: "\u8bcd\u6c47", short: "\u9ec4", parts: ["word"] },
  { key: "ATTRIBUTE", label: "\u5c5e\u6027", short: "\u84dd", parts: ["attribute"] },
  { key: "SCENE_WORD", label: "\u60c5\u5883+\u8bcd\u6c47", short: "\u7ea2\u9ec4", parts: ["scene", "word"] },
  { key: "SCENE_ATTRIBUTE", label: "\u60c5\u5883+\u5c5e\u6027", short: "\u7ea2\u84dd", parts: ["scene", "attribute"] },
  { key: "WORD_ATTRIBUTE", label: "\u8bcd\u6c47+\u5c5e\u6027", short: "\u9ec4\u84dd", parts: ["word", "attribute"] },
  { key: "SCENE_WORD_ATTRIBUTE", label: "\u60c5\u5883+\u8bcd\u6c47+\u5c5e\u6027", short: "\u7ea2\u9ec4\u84dd", parts: ["scene", "word", "attribute"] },
  { key: "NONE", label: "\u65e0\u5173", short: "\u65e0\u5173", parts: ["none"] }
];

const CAMEL_COLORS = {
  red: "\u7ea2",
  yellow: "\u9ec4",
  blue: "\u84dd",
  green: "\u7eff",
  purple: "\u7d2b",
  black: "\u9ed1"
};

const BRASS_CITIES = [
  "WARRINGTON", "STOKE-ON-TRENT", "STONE", "STAFFORD", "CANNOCK", "WOLVERHAMPTON",
  "COALBROOKDALE", "DUDLEY", "KIDDERMINSTER", "WORCESTER", "BIRMINGHAM", "COVENTRY",
  "NUNEATON", "TAMWORTH", "WALSALL", "BURTON-ON-TRENT", "DERBY", "NOTTINGHAM",
  "LEEK", "BELPER", "REDDITCH", "GLOUCESTER", "OXFORD", "SHREWSBURY"
];

const BRASS_INDUSTRIES = {
  cotton_mill: "棉纺厂",
  manufacturer: "加工厂",
  brewery: "酿酒厂",
  pottery: "陶瓷厂",
  iron_works: "钢铁厂",
  coal_mine: "煤矿场"
};

const BRASS_PLAYER_COLORS = {
  red: { label: "红色", css: "#b93a32" },
  yellow: { label: "黄色", css: "#d9a621" },
  blue: { label: "蓝色", css: "#2868b7" },
  purple: { label: "紫色", css: "#7b4bb3" }
};

const GAME_LABELS = {
  rps: "猜拳",
  thingsInRings: "环中物语",
  camel_up_cards: "狂野骆驼：卡牌版",
  gobang: "五子棋",
  brass: "工业革命：伯明翰"
};

const BRASS_CITY_CN = {
  WARRINGTON: "沃灵顿",
  SHREWSBURY: "舒兹伯利",
  NOTTINGHAM: "诺丁汉",
  GLOUCESTER: "格罗斯特",
  OXFORD: "牛津",
  LEEK: "利克",
  "STOKE-ON-TRENT": "斯托克",
  STONE: "斯通",
  UTTOXETER: "伍拓希特尔",
  BELPER: "贝柏",
  DERBY: "德比",
  STAFFORD: "斯坦福",
  "BURTON-ON-TRENT": "特伦河畔伯顿",
  CANNOCK: "坎诺克",
  TAMWORTH: "谭沃思",
  WALSALL: "沃尔索尔",
  WOLVERHAMPTON: "伍尔弗汉普顿",
  COALBROOKDALE: "柯尔布鲁德尔",
  DUDLEY: "达德利",
  KIDDERMINSTER: "基德明斯特",
  WORCESTER: "伍斯特",
  BIRMINGHAM: "伯明翰",
  NUNEATON: "纳尼顿",
  COVENTRY: "考文垂",
  REDDITCH: "雷迪奇",
  RURAL_BREWERY: "乡村酿酒厂",
  PERSONAL_BREWERY: "私人酿酒厂"
};

export default function RoomPage() {
  const navigate = useNavigate();
  const { gameType, roomId } = useParams();
  const [room, setRoom] = useState(null);
  const [error, setError] = useState("");
  const [rulesOpen, setRulesOpen] = useState(false);

  useEffect(() => {
    let active = true;

    const socket = createRoomSocket(gameType, roomId, (message) => {
      if (message.type === "room.snapshot" && active) {
        setRoom(message.payload);
      }
      if (message.type === "room.dismissed") {
        navigate(`/${gameType}`, { replace: true });
      }
    });

    apiGet(`/api/rooms/${gameType}/${roomId}`)
      .then((data) => {
        if (!active) return;
        setRoom(data);
        setError("");
      })
      .catch((exception) => {
        if (!active) return;
        setError(exception.message);
      });

    return () => {
      active = false;
      socket.close();
    };
  }, [gameType, roomId, navigate]);

  const seats = useMemo(() => seatListOf(room), [room]);
  const currentSeat = useMemo(
    () => seats.find((seat) => seat.currentUserSeat) ?? null,
    [seats]
  );
  const occupiedCount = useMemo(
    () => seats.filter((seat) => isSeatOccupied(seat)).length,
    [seats]
  );
  const seatCount = room?.roomState?.seatCount ?? room?.seatCount ?? seats.length;
  const allSeatsOccupied = seats.length > 0 && occupiedCount === seatCount && seats.every(isSeatOccupied);
  const owner = Boolean(room?.currentUser?.id === room?.owner?.id);
  const waiting = room?.status === "WAITING";
  const inProgress = room?.status === "IN_PROGRESS";
  const canStart = owner && waiting && allSeatsOccupied;
  const canLeaveRoom = room?.permissions?.canLeaveRoom === true;
  const gameFinished = isFinishedGameState(room?.gameState);

  useEffect(() => {
    const gameName = labelFor(gameType);
    const currentPlayerName = roomCurrentPlayerName(room, seats);
    document.title = currentPlayerName ? `${currentPlayerName}的回合 · ${gameName}` : gameName;
    return () => {
      document.title = "Boardgame";
    };
  }, [gameType, room, seats]);

  const seatBounds = useMemo(() => {
    if (gameType === "rps") return { min: 2, max: 8 };
    if (gameType === "thingsInRings") return { min: 2, max: 6 };
    if (gameType === "camel_up_cards") return { min: 2, max: 6 };
    if (gameType === "brass") return { min: 2, max: 4 };
    return { min: 2, max: 2 };
  }, [gameType]);

  const minSeatCount = Math.max(seatBounds.min, occupiedCount);
  const canAddSeat = owner && waiting && seatCount < seatBounds.max;
  const canRemoveSeat = owner && waiting && seatCount > minSeatCount;

  async function updateRoom(request) {
    try {
      const data = await request();
      setRoom(data);
      setError("");
    } catch (exception) {
      setError(exception.message);
    }
  }

  function moveSeat(seatIndex) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/seat`, { seatIndex }));
  }

  function standUp() {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/stand`, {}));
  }

  function forceStandUp(seatIndex) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/seats/${seatIndex}/stand`, {}));
  }

  function changeSeatCount(delta) {
    const nextValue = seatCount + delta;
    if (nextValue < occupiedCount) {
      setError(TEXT.seatCountTooSmall);
      return;
    }
    if (nextValue < seatBounds.min || nextValue > seatBounds.max) {
      setError(TEXT.seatCountOutOfRange
        .replace("{min}", String(seatBounds.min))
        .replace("{max}", String(seatBounds.max)));
      return;
    }

    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/config`, {
      config: { ...(room.config ?? {}), seatCount: nextValue }
    }));
  }

  function updateRpsConfig(field, value) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/config`, {
      config: { ...(room.config ?? {}), [field]: value }
    }));
  }

  function updateThingsConfig(field, value) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/config`, {
      config: { ...(room.config ?? {}), [field]: value }
    }));
  }

  function updateCamelConfig(field, value) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/config`, {
      config: { ...(room.config ?? {}), [field]: value }
    }));
  }

  function startGame() {
    if (!window.confirm("Start game?")) {
      return;
    }
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/start`, {}));
  }

  function submitMove(move) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/actions`, {
      type: "submit_move",
      payload: { move }
    }));
  }

  function submitThingsAction(type, payload = {}) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/actions`, {
      type,
      payload
    }));
  }

  function submitCamelAction(type, payload = {}) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/actions`, {
      type,
      payload
    }));
  }

  function submitBrassAction(type, payload = {}) {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/actions`, {
      type,
      payload,
      stateVersion: Number(room?.gameState?.version ?? 0),
      clientActionId: createClientActionId()
    }));
  }

  function proposeEndGame() {
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/propose-end`, {}));
  }

  function endGame() {
    if (!window.confirm(TEXT.confirmEndGame)) {
      return;
    }
    updateRoom(() => apiPost(`/api/rooms/${gameType}/${roomId}/end`, {}));
  }

  async function leaveRoom() {
    try {
      await apiPost(`/api/rooms/${gameType}/${roomId}/leave`, {});
      navigate(`/${gameType}`, { replace: true });
    } catch (exception) {
      setError(exception.message);
    }
  }

  async function dismissRoom() {
    try {
      await apiDelete(`/api/rooms/${gameType}/${roomId}`);
      navigate(`/${gameType}`, { replace: true });
    } catch (exception) {
      setError(exception.message);
    }
  }

  if (error && !room) {
    return <div className="center-page">{error}</div>;
  }

  if (!room) {
    return <div className="center-page">{TEXT.loading}</div>;
  }

  return (
    <PageShell
      className={`page-shell-room ${gameType === "brass" && inProgress ? "page-shell-brass" : ""}`.trim()}
      title={labelFor(gameType)}
      subtitle={`房间号 #${room.roomId}`}
      actions={
        <div className="button-inline">
          <button className="ghost-button" onClick={() => setRulesOpen(true)}>
            {TEXT.rules}
          </button>
          {inProgress && owner ? (
            <button className="danger-button" onClick={endGame}>
              {gameFinished ? TEXT.returnPrepare : TEXT.endGame}
            </button>
          ) : null}
          {inProgress && !gameFinished && !owner && currentSeat ? (
            <button className="secondary-button" onClick={proposeEndGame}>
              {hasCurrentUserProposed(room) ? TEXT.cancelProposeEnd : TEXT.proposeEnd}
            </button>
          ) : null}
          <button className="ghost-button" disabled={!canLeaveRoom} onClick={leaveRoom}>
            {TEXT.leave}
          </button>
          {owner ? (
            <button className="danger-button" onClick={dismissRoom}>
              {TEXT.dismiss}
            </button>
          ) : null}
        </div>
      }
    >
      {error ? <div className="form-error room-error">{error}</div> : null}

      {waiting ? (
        <PrepareRoom
          owner={owner}
          waiting={waiting}
          seats={seats}
          seatCount={seatCount}
          currentSeat={currentSeat}
          canAddSeat={canAddSeat}
          canRemoveSeat={canRemoveSeat}
          canStart={canStart}
          onAddSeat={() => changeSeatCount(1)}
          onRemoveSeat={() => changeSeatCount(-1)}
          onMoveSeat={moveSeat}
          onForceStandUp={forceStandUp}
          onStandUp={standUp}
          onStartGame={startGame}
          gameType={gameType}
          config={room.config ?? {}}
          onUpdateRpsConfig={updateRpsConfig}
          onUpdateThingsConfig={updateThingsConfig}
          onUpdateCamelConfig={updateCamelConfig}
        />
      ) : (
        <GameErrorBoundary gameType={gameType}>
          <GameRoom
            gameType={gameType}
            room={room}
            seats={seats}
            currentSeat={currentSeat}
            owner={owner}
            onSubmitMove={submitMove}
            onThingsAction={submitThingsAction}
            onCamelAction={submitCamelAction}
            onBrassAction={submitBrassAction}
          />
        </GameErrorBoundary>
      )}
      <RuleModal
        gameType={gameType}
        title={`${labelFor(gameType)} Rules`}
        open={rulesOpen}
        onClose={() => setRulesOpen(false)}
      />
    </PageShell>
  );
}

function PrepareRoom({
  owner,
  waiting,
  seats,
  seatCount,
  currentSeat,
  canAddSeat,
  canRemoveSeat,
  canStart,
  onAddSeat,
  onRemoveSeat,
  onMoveSeat,
  onForceStandUp,
  onStandUp,
  onStartGame,
  gameType,
  config,
  onUpdateRpsConfig,
  onUpdateThingsConfig,
  onUpdateCamelConfig
}) {
  return (
    <section className="panel room-seat-panel">
      {gameType === "rps" && owner && waiting ? (
        <RpsSettings config={config} onChange={onUpdateRpsConfig} />
      ) : null}
      {gameType === "thingsInRings" && owner && waiting ? (
        <ThingsSettings config={config} onChange={onUpdateThingsConfig} />
      ) : null}
      {gameType === "camel_up_cards" && owner && waiting ? (
        <CamelSettings config={config} onChange={onUpdateCamelConfig} />
      ) : null}

      {owner && waiting ? (
        <div className="seat-count-toolbar" aria-label={TEXT.seatCount}>
          <span>
            {TEXT.seatCount}: {seatCount}
          </span>
          <div className="button-inline">
            <button className="secondary-button small-button" disabled={!canRemoveSeat} onClick={onRemoveSeat}>
              {TEXT.removeSeat}
            </button>
            <button className="secondary-button small-button" disabled={!canAddSeat} onClick={onAddSeat}>
              {TEXT.addSeat}
            </button>
          </div>
        </div>
      ) : null}

      <SeatBoard
        owner={owner}
        waiting={waiting}
        seats={seats}
        showKick={owner && waiting}
        onMoveSeat={onMoveSeat}
        onForceStandUp={onForceStandUp}
      />

      {currentSeat && waiting ? (
        <button className="secondary-button stand-button" onClick={onStandUp}>
          {TEXT.standUp}
        </button>
      ) : null}

      <button className="primary-button start-button" disabled={!canStart} onClick={onStartGame}>
        {TEXT.startGame}
      </button>
    </section>
  );
}

function RpsSettings({ config, onChange }) {
  return (
    <div className="rps-settings">
      <strong>{TEXT.rpsSettings}</strong>
      <label>
        {TEXT.moveSet}
        <select value={String(config.moveSet ?? 3)} onChange={(event) => onChange("moveSet", Number(event.target.value))}>
          <option value="3">{TEXT.moveSetThree}</option>
          <option value="5">{TEXT.moveSetFive}</option>
        </select>
      </label>
      <label>
        {TEXT.rpsMode}
        <select value={config.mode ?? "free_for_all"} onChange={(event) => onChange("mode", event.target.value)}>
          <option value="free_for_all">{TEXT.freeForAll}</option>
          <option value="bracket">{TEXT.bracket}</option>
        </select>
      </label>
    </div>
  );
}

function ThingsSettings({ config, onChange }) {
  const customRules = config.customRules ?? {};

  function updateCustomRule(field, value) {
    onChange("customRules", { ...customRules, [field]: value });
  }

  return (
    <div className="things-settings">
      <strong>{TEXT.thingsSettings}</strong>
      <label>
        {TEXT.difficulty}
        <select value={config.difficulty ?? "hard"} onChange={(event) => onChange("difficulty", event.target.value)}>
          <option value="easy">{TEXT.easy}</option>
          <option value="medium">{TEXT.medium}</option>
          <option value="hard">{TEXT.hard}</option>
          <option value="custom">{TEXT.custom}</option>
        </select>
      </label>
      <label>
        {TEXT.spectatorView}
        <select value={config.spectatorView ?? "god"} onChange={(event) => onChange("spectatorView", event.target.value)}>
          <option value="god">{TEXT.godView}</option>
          <option value="player">{TEXT.playerView}</option>
        </select>
      </label>
      {config.difficulty === "custom" ? (
        <div className="things-custom-rules">
          <input value={customRules.SCENE ?? ""} onChange={(event) => updateCustomRule("SCENE", event.target.value)} placeholder={TEXT.sceneRule} />
          <input value={customRules.WORD ?? ""} onChange={(event) => updateCustomRule("WORD", event.target.value)} placeholder={TEXT.wordRule} />
          <input value={customRules.ATTRIBUTE ?? ""} onChange={(event) => updateCustomRule("ATTRIBUTE", event.target.value)} placeholder={TEXT.attributeRule} />
        </div>
      ) : null}
    </div>
  );
}

function CamelSettings({ config, onChange }) {
  return (
    <div className="camel-settings">
      <strong>{TEXT.camelSettings}</strong>
      <label>
        {TEXT.expansion}
        <select value={config.expansion ?? "shortcut_fennec"} onChange={(event) => onChange("expansion", event.target.value)}>
          <option value="none">{TEXT.expansionNone}</option>
          <option value="shortcut">{TEXT.expansionShortcut}</option>
          <option value="fennec">{TEXT.expansionFennec}</option>
          <option value="shortcut_fennec">{TEXT.expansionBoth}</option>
        </select>
      </label>
    </div>
  );
}

class GameErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error) {
    console.error("Game render failed", this.props.gameType, error);
  }

  render() {
    if (this.state.error) {
      return (
        <section className="panel form-error">
          游戏界面渲染失败：{this.state.error.message || "未知错误"}。请刷新页面；如果仍然出现，请保留当前房间状态用于排查。
        </section>
      );
    }
    return this.props.children;
  }
}

function GameRoom({ gameType, room, seats, currentSeat, owner, onSubmitMove, onThingsAction, onCamelAction, onBrassAction }) {
  const [selectedMove, setSelectedMove] = useState("");
  const gameState = room.gameState ?? {};

  useEffect(() => {
    setSelectedMove("");
  }, [gameState.round]);

  if (gameType === "thingsInRings") {
    return (
      <ThingsInRingsRoom
        room={room}
        seats={seats}
        currentSeat={currentSeat}
        owner={owner}
        onAction={onThingsAction}
      />
    );
  }

  if (gameType === "camel_up_cards") {
    return (
      <CamelUpCardsRoom
        room={room}
        seats={seats}
        currentSeat={currentSeat}
        owner={owner}
        onAction={onCamelAction}
      />
    );
  }

  if (gameType === "brass") {
    return (
      <BrassRoom
        room={room}
        seats={seats}
        currentSeat={currentSeat}
        onAction={onBrassAction}
      />
    );
  }

  if (gameType !== "rps") {
    return (
      <section className="panel game-room-panel">
        <p className="muted-note">{TEXT.reserved}</p>
      </section>
    );
  }

  const submissions = gameState.submissions ?? {};
  const lastRoundSubmissions = gameState.lastRoundSubmissions ?? {};
  const wins = gameState.wins ?? {};
  const currentUserId = String(room.currentUser?.id ?? "");
  const activePlayerIds = (gameState.activePlayers ?? []).map((id) => String(id));
  const candidatePlayerIds = (gameState.candidatePlayers ?? []).map((id) => String(id));
  const active = activePlayerIds.includes(currentUserId);
  const candidate = candidatePlayerIds.includes(currentUserId);
  const submitted = Object.prototype.hasOwnProperty.call(submissions, currentUserId);
  const moves = Number(room.config?.moveSet) === 5
    ? ["stone", "scissors", "paper", "lizard", "spock"]
    : ["stone", "scissors", "paper"];
  const visibleGroups = visibleRpsGroups(gameState.groups ?? [], currentUserId, room.config?.mode);

  return (
    <div className="game-room-grid">
      <section className="panel relation-panel">
        <h2>{TEXT.relationTitle}</h2>
        <ul className="relation-list">
          {relationLines(Number(room.config?.moveSet) === 5).map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      </section>

      <section className="panel game-room-panel">
        <div className="game-room-heading">
          <h2>{TEXT.battleArea}</h2>
          <span>{TEXT.round.replace("{round}", String(gameState.round ?? 1))}</span>
        </div>

        {gameState.endProposalText ? (
          <div className="proposal-banner">{gameState.endProposalText}</div>
        ) : null}

        <div className="battle-groups">
          {visibleGroups.map((group) => (
            <div className="battle-group" key={group.groupId}>
              <div className="battle-group-title">
                <strong>{room.config?.mode === "bracket" ? `${group.groupId}\u7ec4` : TEXT.freeForAll}</strong>
                {group.resultText ? <span>{group.resultText}</span> : null}
              </div>
              <div className="battle-player-list">
                {(group.players ?? []).map((player) => (
                  <div className="battle-player" key={String(player.userId)}>
                    <span>{rpsPlayerLabel(player)}</span>
                    {room.config?.mode === "free_for_all" && !player.bot ? (
                      <small>{TEXT.wins}: {(gameState.scores ?? {})[String(player.userId)] ?? 0}</small>
                    ) : null}
                    <strong>{moveDisplayFor(player, submissions, lastRoundSubmissions, group)}</strong>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="panel action-panel">
        <div className="game-room-heading">
          <h2>{TEXT.actionArea}</h2>
          <span>{rpsStatusText({ currentSeat, active, candidate, submitted })}</span>
        </div>

        <div className="move-grid">
          {moves.map((move) => (
            <button
              key={move}
              className={selectedMove === move || submitted && submissions[currentUserId] === move ? "active" : ""}
              disabled={!currentSeat || !active || submitted || gameState.phase === "finished"}
              onClick={() => setSelectedMove(move)}
            >
              {MOVE_LABELS[move]}
            </button>
          ))}
        </div>

        <button
          className="primary-button"
          disabled={!currentSeat || !active || submitted || !selectedMove || gameState.phase === "finished"}
          onClick={() => onSubmitMove(selectedMove)}
        >
          {TEXT.confirmMove}
        </button>
      </section>

      <section className="panel room-seat-panel compact">
        <SeatBoard owner={owner} waiting={false} seats={seats} showKick={false} />
      </section>

      <NoticePanel notices={room.notices ?? []} />
    </div>
  );
}

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

      {state.phase === "HOST_INITIAL_PLACEMENT" || state.phase === "HOST_TURN" ? (
        <HostActionPanel state={state} isHost={isHost} onAction={onAction} />
      ) : null}

      {pendingGuess && isHost ? (
        <section className="panel things-judge-panel">
          <h2>{TEXT.pendingJudge}</h2>
          {!correcting ? (
            <>
              <p>
                {pendingGuess.playerName} 尝试将 {thingsWordText(pendingGuess.word)} 放到 {thingsAreaShort(pendingGuess.area)} 区域，是否正确？
              </p>
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
          onPlace={(area) => confirmed(`Place ${wordText(selectedWord)} to ${areaShort(area)}?`, "guesser_submit", {
            wordId: selectedWord?.id,
            area
          })}
        />

        <NoticePanel notices={room.notices ?? []} />
      </div>

      {state.phase === "GAME_OVER" ? (
        <section className="panel things-game-over">
          <h2>{TEXT.winner}</h2>
          <p>{(state.winners ?? []).map((winner) => winner.username).join(", ")}</p>
        </section>
      ) : null}

      <section className="panel room-seat-panel compact">
        <SeatBoard owner={owner} waiting={false} seats={seats} showKick={false} />
      </section>
    </div>
  );
}

function CamelUpCardsRoom({ room, seats, currentSeat, owner, onAction }) {
  const state = room.gameState ?? {};
  const currentUserId = String(room.currentUser?.id ?? "");
  const currentPlayerId = String(state.currentPlayerId ?? "");
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
          <h2>游戏状态</h2>
          <span>{TEXT.currentPlayer}: {camelPlayerName(state.players ?? [], currentPlayerId)}</span>
        </div>
        <div className="camel-meta-row">
          <span>Leg: {state.leg ?? 1}</span>
          <span>Finish: {state.finishPosition ?? "-"}</span>
          <span>{TEXT.raceDeck}: {asArray(state.raceDeck).length}</span>
          <span>{TEXT.temporaryDiscard}: {asArray(state.temporaryDiscard).map(camelRaceCardText).join(", ") || "none"}</span>
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
          onSubmit={() => confirmed("Confirm selection?", "submit_setup_selection", { cardIds: setupSelection })}
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
              Winner: {(state.winners ?? []).map((winner) => winner.username).join(", ")}
            </div>
          ) : (
            <div className="camel-action-panel">
              <button className="primary-button" disabled={!canTrack} onClick={() => confirmed("Draw top race card?", "draw_race_card")}>
                {TEXT.drawRaceCard}
              </button>
              <button className="secondary-button" disabled={!canTrack || !state.hands?.[currentUserId]} onClick={() => confirmed("Play your hand card?", "play_hand", { ownerUserId: room.currentUser?.id })}>
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
                      onClick={() => confirmed(`Play ${player.username} hand card?`, "play_hand", { ownerUserId: player.userId })}
                    >
                      {TEXT.playPlayerHand.replace("{player}", player.username)}
                    </button>
                  ))}
              </div>
              <div className="camel-token-actions">
                <label>
                  捷径位置
                  <input value={shortcutPos} inputMode="numeric" onChange={(event) => setShortcutPos(event.target.value)} />
                </label>
                <button className="secondary-button" disabled={!canTrack} onClick={() => confirmed(`Place shortcut at ${shortcutPos}?`, "place_token", { tokenType: "shortcut", position: Number(shortcutPos) })}>
                  {TEXT.placeShortcut}
                </button>
                <label>
                  耳廓狐位置
                  <input value={fennecPos} inputMode="numeric" onChange={(event) => setFennecPos(event.target.value)} />
                </label>
                <button className="secondary-button" disabled={!canTrack} onClick={() => confirmed(`Place fennec at ${fennecPos}?`, "place_token", { tokenType: "fennec", position: Number(fennecPos) })}>
                  {TEXT.placeFennec}
                </button>
              </div>
              <button className="ghost-button" disabled={!canSkipBet} onClick={() => confirmed("Skip betting action?", "skip_bet")}>
                {TEXT.skipBet}
              </button>
            </div>
          )}
        </section>

        <section className="panel camel-bet-panel">
          <h2>{TEXT.bettingCards}</h2>
          <CamelBetMarket market={state.betMarket ?? {}} canBet={canBet} onTake={(card) => confirmed(`Take ${camelBetCardText(card)}?`, "take_bet", { cardId: card.id })} />
        </section>

        <NoticePanel notices={room.notices ?? []} />
      </div>

      <section className="panel room-seat-panel compact">
        <SeatBoard owner={owner} waiting={false} seats={seats} showKick={false} />
      </section>
    </div>
  );
}

function BrassRoom({ room, seats, currentSeat, onAction }) {
  const state = room.gameState ?? {};
  const currentUserId = String(room.currentUser?.id ?? "");
  const currentPlayerId = String(state.currentPlayerId ?? "");
  const isCurrentPlayer = currentSeat && currentUserId === currentPlayerId && state.phase === "playing";
  const hand = state.hands?.[currentUserId] ?? [];
  const [selectedCards, setSelectedCards] = useState([]);
  const [selectedTileId, setSelectedTileId] = useState("");
  const [buildCity, setBuildCity] = useState("BIRMINGHAM");
  const [buildIndustry, setBuildIndustry] = useState("cotton_mill");
  const [buildSlotIndex, setBuildSlotIndex] = useState("");
  const [networkRoute, setNetworkRoute] = useState("");
  const [secondNetworkRoute, setSecondNetworkRoute] = useState("");
  const [selectedMerchantId, setSelectedMerchantId] = useState("");
  const [selectedCoalSourceId, setSelectedCoalSourceId] = useState("");
  const [selectedSecondCoalSourceId, setSelectedSecondCoalSourceId] = useState("");
  const [selectedIronSourceId, setSelectedIronSourceId] = useState("");
  const [selectedBeerSourceId, setSelectedBeerSourceId] = useState("");
  const [developIndustries, setDevelopIndustries] = useState([]);
  const [activeAction, setActiveAction] = useState("");
  const [cardHintsOpen, setCardHintsOpen] = useState(false);
  const [mapInfo, setMapInfo] = useState(null);

  useEffect(() => {
    setSelectedCards([]);
    setSelectedTileId("");
    setBuildSlotIndex("");
    setSecondNetworkRoute("");
    setSelectedMerchantId("");
    setSelectedCoalSourceId("");
    setSelectedSecondCoalSourceId("");
    setSelectedIronSourceId("");
    setSelectedBeerSourceId("");
    setDevelopIndustries([]);
    setActiveAction("");
  }, [state.round, state.currentPlayerId, state.turn?.actionsTaken]);

  function confirmed(message, type, payload = {}) {
    if (window.confirm(message)) {
      onAction(type, payload);
    }
  }

  function toggleCard(cardId) {
    const card = hand.find((item) => item.id === cardId);
    if (activeAction === "build" && card && card.type === "location" && !card.wild && card.key) {
      setBuildCity(card.key);
    }
    if (activeAction === "scout") {
      setSelectedCards((current) => current.includes(cardId)
        ? current.filter((id) => id !== cardId)
        : [...current, cardId]);
      return;
    }
    setSelectedCards((current) => current.includes(cardId) ? [] : [cardId]);
  }

  function toggleDevelopIndustry(industryType) {
    setDevelopIndustries((current) => {
      const count = current.filter((item) => item === industryType).length;
      if (count === 0) {
        return current.length >= 2 ? current : [...current, industryType];
      }
      if (count === 1) {
        return current.length >= 2 ? current.filter((item) => item !== industryType) : [...current, industryType];
      }
      return current.filter((item) => item !== industryType);
    });
  }

  function toggleDevelopTile(industryType, selectedInGroup = 0, maxSelectable = 2) {
    setDevelopIndustries((current) => {
      const currentCount = current.filter((item) => item === industryType).length;
      if (currentCount >= maxSelectable) {
        return current.filter((item) => item !== industryType);
      }
      if (selectedInGroup < maxSelectable && current.length < 2) {
        return [...current, industryType];
      }
      if (selectedInGroup > 0) {
        let removed = false;
        return current.filter((item) => {
          if (!removed && item === industryType) {
            removed = true;
            return false;
          }
          return true;
        });
      }
      return current;
    });
  }

  function chooseAction(action) {
    setActiveAction((current) => {
      const next = current === action ? "" : action;
      if (next !== "develop") {
        setDevelopIndustries([]);
      }
      return next;
    });
  }

  function clickBrassAction(action) {
    if (action === "restart_turn") {
      confirmed("是否重新开始本回合？本回合已执行的行动会被撤回。", "restart_turn");
      return;
    }
    if (action === "end_turn") {
      confirmed("是否结束本回合并将手牌补至8张？", "end_turn");
      return;
    }
    if (action === "maintain_era") {
      confirmed("是否执行时代维护？", "maintain_era");
      return;
    }
    chooseAction(action);
  }

  const turn = state.turn ?? {};
  const availableActionState = state.availableActions ?? {};
  const availableActions = availableActionState.actions ?? [];
  const sellInProgress = Boolean(availableActionState.sellInProgress || turn.sellInProgress);
  const sellCount = Number(availableActionState.sellCount ?? turn.sellCount ?? 0);
  const stats = state.playerStats ?? {};
  const developments = state.developments ?? {};
  const playerBoards = state.playerBoards ?? {};
  const players = state.players ?? [];
  const initialTurnOrder = (state.initialTurnOrder ?? []).length > 0
    ? state.initialTurnOrder
    : state.turnOrder ?? [];
  const currentPlayer = players.find((player) => String(player.userId) === String(currentPlayerId));
  const currentPlayerColor = BRASS_PLAYER_COLORS[currentPlayer?.color]?.css ?? "#8d2f2f";
  const mainActions = ["build", "sell", "network", "develop", "loan", "scout", "skip"];
  const industries = (state.board ?? {}).industries ?? [];
  const links = (state.board ?? {}).links ?? [];
  const routes = (state.board ?? {}).availableRoutes ?? [];
  const buildableCities = (state.board ?? {}).buildableCities?.length ? (state.board ?? {}).buildableCities : BRASS_CITIES;
  const citySlots = (state.board ?? {}).citySlots ?? {};
  const buildOptions = availableActionState.buildOptions ?? [];
  const sellTileOptions = availableActionState.sellTiles ?? [];
  const networkRouteOptions = availableActionState.networkRoutes ?? [];
  const networkRoutePairOptions = availableActionState.networkRoutePairs ?? [];
  const buildableCitiesFromActions = [...new Set(buildOptions.map((option) => option.city).filter(Boolean))];
  const selectableBuildCities = buildableCitiesFromActions;
  const buildIndustryOptions = [...new Set((citySlots[buildCity] ?? []).flatMap((slot) => Array.isArray(slot) ? slot : [slot]))]
    .filter((industryType) => buildOptions.some((option) => option.city === buildCity && option.industryType === industryType))
    .filter(Boolean);
  const activeBuildIndustry = buildIndustryOptions.includes(buildIndustry)
    ? buildIndustry
    : buildIndustryOptions[0] ?? buildIndustry;
  const selectedBuildOption = buildOptions.find((option) => option.city === buildCity
    && option.industryType === activeBuildIndustry
    && (buildSlotIndex === "" || Number(option.slotIndex) === Number(buildSlotIndex)))
    ?? buildOptions.find((option) => option.city === buildCity && option.industryType === activeBuildIndustry);
  const buildCompatibleCardIds = selectedBuildOption?.cardIds ?? [];
  const merchants = state.market?.beerMerchants ?? [];
  const playerCount = players.length;
  const availableRoutesForSelect = networkRouteOptions;
  const selectedRoute = availableRoutesForSelect.find((route) => brassRouteKey(route) === networkRoute) ?? null;
  const selectedSecondRoute = availableRoutesForSelect.find((route) => brassRouteKey(route) === secondNetworkRoute) ?? null;
  const selectedRoutePair = networkRoutePairOptions.find((pair) => {
    const pairRoutes = pair.routes ?? [];
    return pairRoutes.some((route) => brassRouteKey(route) === brassRouteKey(selectedRoute))
      && pairRoutes.some((route) => brassRouteKey(route) === brassRouteKey(selectedSecondRoute));
  });
  const compatibleSecondRouteKeys = new Set();
  if (state.era === "rail" && selectedRoute) {
    for (const pair of networkRoutePairOptions) {
      const pairRoutes = pair.routes ?? [];
      if (!pairRoutes.some((route) => brassRouteKey(route) === brassRouteKey(selectedRoute))) {
        continue;
      }
      for (const route of pairRoutes) {
        const key = brassRouteKey(route);
        if (key !== brassRouteKey(selectedRoute)) {
          compatibleSecondRouteKeys.add(key);
        }
      }
    }
  }
  const selectedTile = industries.find((tile) => tile.id === selectedTileId);
  const selectedSellOption = sellTileOptions.find((tile) => tile.id === selectedTileId);
  const selectedMerchant = merchants.find((merchant) => merchant.id === selectedMerchantId);
  const selectedMerchantDevelopReward = selectedMerchant && String(selectedMerchant.reward ?? "").includes("Develop");
  const availableSellTiles = sellTileOptions;
  const buildCoalSources = resourceSourcesForSelect(selectedBuildOption?.coalSources, industries, "coal_mine", "coal");
  const buildIronSources = resourceSourcesForSelect(selectedBuildOption?.ironSources, industries, "iron_works", "iron");
  const developOption = (availableActionState.developIndustries ?? []).find((option) => option.industryType === developIndustries[0]);
  const developIronSources = resourceSourcesForSelect(developOption?.ironSources, industries, "iron_works", "iron");
  const networkCoalSources = resourceSourcesForSelect(selectedRoute?.coalSources, industries, "coal_mine", "coal");
  const secondNetworkCoalSources = resourceSourcesForSelect(
    (selectedRoutePair?.routes ?? []).find((route) => brassRouteKey(route) === brassRouteKey(selectedSecondRoute))?.coalSources,
    industries,
    "coal_mine",
    "coal"
  );
  const networkBeerSources = resourceSourcesForSelect(selectedRoutePair?.beerSources, industries, "brewery", "beer");
  const sellBeerSources = resourceSourcesForSelect(selectedSellOption?.beerSources, industries, "brewery", "beer");
  const sellMerchantSources = selectedSellOption?.merchantSources ?? [];
  const cityNames = state.board?.map?.cityNames ?? {};
  const industryNames = { ...brassIndustryNames(cityNames), ...(state.board?.map?.industryNames ?? {}) };
  const mapCities = buildBrassMapCities(state.board?.map?.cities ?? [], routes, buildableCities, cityNames);
  const cityLabel = (city) => brassCityName(mapCities, city, cityNames);
  const routeLabel = (route) => `${cityLabel(route.from)} - ${cityLabel(route.to)}`;
  const buildNeedsCoal = Number(selectedBuildOption?.coalCost ?? 0) > 0;
  const buildNeedsIron = Number(selectedBuildOption?.ironCost ?? 0) > 0;
  const networkNeedsCoal = state.era === "rail" && Boolean(selectedRoute);
  const secondNetworkNeedsCoal = state.era === "rail" && Boolean(selectedSecondRoute);
  const developNeedsIron = developIndustries.length > 0;
  const canSellSelectedTile = Boolean(selectedSellOption);
  const selectedCardId = selectedCards[0] ?? "";
  const selectedBuildSlotKey = buildSlotIndex === "" ? "" : brassSlotKey(buildCity, buildSlotIndex);
  const buildBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
    : selectedCards.length !== 1
      ? "请选择1张手牌"
      : !selectedBuildOption
        ? "请选择合法建造目标"
        : buildCompatibleCardIds.length > 0 && !buildCompatibleCardIds.includes(selectedCardId)
          ? "所选手牌不能用于该建造"
          : "";
  const networkBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
    : selectedCards.length !== 1
      ? "请选择1张手牌"
      : !selectedRoute
        ? "请选择路线"
        : state.era === "rail" && selectedSecondRoute && !selectedRoutePair
          ? "请选择合法的双路线组合"
          : "";
  const developBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
    : selectedCards.length !== 1
      ? "请选择1张手牌"
      : developIndustries.length < 1
        ? "请选择1到2个产业"
        : "";
  const sellBlockReason = !isCurrentPlayer
    ? "等待当前玩家行动"
    : !sellInProgress && selectedCards.length !== 1
      ? "请选择1张手牌"
    : !canSellSelectedTile
      ? "请选择一个可出售的自有产业"
        : selectedMerchantId && !sellMerchantSources.some((merchant) => merchant.id === selectedMerchantId)
          ? "所选贸易商不可用于该产业"
          : "";
  const simpleActionReason = (() => {
    if (!["loan", "scout", "skip", "restart_turn", "end_turn", "maintain_era"].includes(activeAction)) {
      return "";
    }
    if (!isCurrentPlayer) {
      return "等待当前玩家行动";
    }
    if (activeAction === "loan" || activeAction === "skip") {
      return selectedCards.length === 1 ? "" : "请选择1张手牌";
    }
    if (activeAction === "scout") {
      return selectedCards.length === 3 ? "" : "请选择3张非万能手牌";
    }
    if (activeAction === "restart_turn") {
      return state.canMaintainEra ? "时代维护时不能撤回本回合" : "";
    }
    if (activeAction === "maintain_era") {
      return state.canMaintainEra ? "" : "尚未满足时代维护条件";
    }
    return "";
  })();

  useEffect(() => {
    if (selectableBuildCities.length > 0 && !selectableBuildCities.includes(buildCity)) {
      setBuildCity(selectableBuildCities[0]);
      setBuildSlotIndex("");
    }
  }, [selectableBuildCities, buildCity]);

  useEffect(() => {
    if (buildIndustryOptions.length > 0 && !buildIndustryOptions.includes(buildIndustry)) {
      setBuildIndustry(buildIndustryOptions[0]);
    }
  }, [buildIndustryOptions, buildIndustry]);

  useEffect(() => {
    if (activeAction !== "build") {
      setBuildSlotIndex("");
      return;
    }
    if (buildSlotIndex !== "" && !buildOptions.some((option) => option.city === buildCity
      && Number(option.slotIndex) === Number(buildSlotIndex)
      && option.industryType === activeBuildIndustry)) {
      setBuildSlotIndex("");
    }
  }, [activeAction, activeBuildIndustry, buildCity, buildOptions, buildSlotIndex]);

  useEffect(() => {
    if (activeAction !== "network") {
      setNetworkRoute("");
      setSecondNetworkRoute("");
      return;
    }
    if (networkRoute && !networkRouteOptions.some((route) => brassRouteKey(route) === networkRoute)) {
      setNetworkRoute("");
      setSecondNetworkRoute("");
      return;
    }
    if (secondNetworkRoute && !networkRoutePairOptions.some((pair) => (pair.routes ?? [])
      .some((route) => brassRouteKey(route) === secondNetworkRoute))) {
      setSecondNetworkRoute("");
    }
  }, [activeAction, networkRoute, networkRouteOptions, networkRoutePairOptions, secondNetworkRoute]);

  useEffect(() => {
    if (activeAction !== "sell") {
      setSelectedTileId("");
      setSelectedMerchantId("");
      setSelectedBeerSourceId("");
      return;
    }
    if (selectedTileId && !sellTileOptions.some((tile) => tile.id === selectedTileId)) {
      setSelectedTileId("");
      setSelectedMerchantId("");
      setSelectedBeerSourceId("");
      return;
    }
    if (selectedMerchantId && !sellMerchantSources.some((merchant) => merchant.id === selectedMerchantId)) {
      setSelectedMerchantId("");
    }
  }, [activeAction, selectedMerchantId, selectedTileId, sellMerchantSources, sellTileOptions]);

  function selectMapCity(city) {
    setBuildCity(city);
    setBuildSlotIndex("");
  }

  function selectMapCitySlot(slot) {
    if (slot.kind === "market") {
      setMapInfo({
        title: `${slot.cityName}#${Number(slot.index ?? 0)}`,
        lines: [
          `城市名称：${slot.cityName}`,
          `市场信息：${slot.message}`,
          `贸易商啤酒：${slot.beerStatus}`
        ]
      });
      if (activeAction === "sell" && selectedSellOption) {
        const merchant = (selectedSellOption.merchantSources ?? []).find((source) => source.id === slot.merchantId);
        if (merchant) {
          setSelectedMerchantId((current) => current === merchant.id ? "" : merchant.id);
          setSelectedBeerSourceId("");
        }
      }
      return;
    }
    const builtTile = industries.find((tile) => brassSlotKey(tile.city, tile.slotIndex ?? 0) === brassSlotKey(slot.city, slot.slotIndex));
    setMapInfo(buildCitySlotInfo(slot, builtTile, mapCities, industries, cityNames));
    if (activeAction === "sell") {
      const sellOption = builtTile ? sellTileOptions.find((tile) => tile.id === builtTile.id) : null;
      if (sellOption) {
        setSelectedTileId((current) => current === sellOption.id ? "" : sellOption.id);
        setSelectedMerchantId("");
        setSelectedBeerSourceId("");
      }
      return;
    }
    const matchingBuildOptions = buildOptions.filter((option) => brassMapKey(option.city) === brassMapKey(slot.city)
      && Number(option.slotIndex) === Number(slot.slotIndex)
      && (!selectedCardId || (option.cardIds ?? []).includes(selectedCardId)));
    if (activeAction === "build" && (builtTile || matchingBuildOptions.length === 0)) {
      return;
    }
    const firstAllowed = (slot.allowedIndustryTypesBackend ?? [])
      .find((industryType) => matchingBuildOptions.some((option) => option.industryType === industryType))
      ?? matchingBuildOptions[0]?.industryType
      ?? slot.allowedIndustryTypesBackend?.[0];
    if (firstAllowed) {
      setBuildIndustry(firstAllowed);
    }
    const option = matchingBuildOptions.find((item) => item.industryType === firstAllowed) ?? matchingBuildOptions[0];
    if (option) {
      setBuildCity(option.city);
      setBuildSlotIndex(option.slotIndex ?? "");
    }
    if (activeAction === "build" && isCurrentPlayer && selectedCards.length === 1 && option) {
      confirmed(buildConfirmText(option, industryNames, cityLabel), "build", {
        city: option.city,
        industryType: option.industryType,
        slotIndex: option.slotIndex,
        cardId: selectedCardId,
        coalSourceTileId: selectedCoalSourceId || undefined,
        ironSourceTileId: selectedIronSourceId || undefined
      });
    }
  }

  function selectMapRoute(route, second = false) {
    const key = brassRouteKey(route);
    setMapInfo(buildRouteInfo(route, links, mapCities, cityNames));
    if (activeAction !== "network") {
      return;
    }
    const isBuildableRoute = networkRouteOptions.some((item) => brassRouteKey(item) === key);
    if (!isCurrentPlayer || selectedCards.length !== 1 || !isBuildableRoute) {
      return;
    }
    if (networkRoute || secondNetworkRoute) {
      setNetworkRoute("");
      setSecondNetworkRoute("");
      return;
    }
    if (state.era === "rail" && networkRoute) {
      if (networkRoute === key) {
        setNetworkRoute("");
        setSecondNetworkRoute("");
        return;
      }
      if (secondNetworkRoute === key) {
        setSecondNetworkRoute("");
        return;
      }
      if (second || compatibleSecondRouteKeys.has(key)) {
        setSecondNetworkRoute(key);
        return;
      }
      setNetworkRoute(key);
      setSecondNetworkRoute("");
      return;
    }
    setNetworkRoute((current) => {
      const next = current === key ? "" : key;
      if (!next) {
        setSecondNetworkRoute("");
      }
      return next;
    });
  }

  return (
    <div className="brass-layout">
      {state.endProposalText ? <div className="proposal-banner">{state.endProposalText}</div> : null}

      <div className="brass-main-grid">
      <main className="brass-primary-column">
      <section className="brass-action-banner" aria-label="行动选择">
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
      </section>

        <section className="panel brass-board-panel">
          <h2>游戏地图</h2>
          <BrassMap
            cities={mapCities}
            routes={routes}
            links={links}
            industries={industries}
            merchants={merchants}
            playerCount={playerCount}
            activeAction={activeAction}
            buildOptions={buildOptions}
            sellOptions={sellTileOptions}
            selectedSellTileId={selectedTileId}
            selectedMerchantId={selectedMerchantId}
            networkRouteOptions={networkRouteOptions}
            compatibleSecondRouteKeys={compatibleSecondRouteKeys}
            selectedCardId={selectedCardId}
            selectedCard={hand.find((card) => card.id === selectedCardId) ?? null}
            selectedCity={buildCity}
            selectedSlotKey={selectedBuildSlotKey}
            selectedRouteKey={networkRoute}
            selectedSecondRouteKey={secondNetworkRoute}
            onCityClick={selectMapCity}
            onCitySlotClick={selectMapCitySlot}
            onRouteClick={selectMapRoute}
            cityNames={cityNames}
          />
          <BrassMapInfoPanel info={mapInfo} />
          <BrassMarket market={state.market ?? {}} />
        </section>

        <section className="panel brass-control-panel">
          <h2>手牌区</h2>
          <div className="brass-hand-grid">
            {hand.map((card) => (
              (() => {
                const disabledByAction = false;
                return (
              <button
                key={card.id}
                className={`brass-card ${selectedCards.includes(card.id) ? "selected" : ""}`}
                disabled={!isCurrentPlayer || disabledByAction}
                onClick={() => toggleCard(card.id)}
              >
                <strong style={brassCardTitleStyle(card, mapCities)}>{brassCardShortName(card, mapCities, cityNames)}</strong>
              </button>
                );
              })()
            ))}
          </div>
          {activeAction === "build" ? <div className="brass-build-controls">
            <p className="muted-note">步骤：选择1张手牌 → 选择城市和产业 → 选择资源来源 → 确认建造。</p>
            <label>
              城市
              <select value={buildCity} onChange={(event) => {
                setBuildCity(event.target.value);
                setBuildSlotIndex("");
              }}>
                {selectableBuildCities.map((city) => <option value={city} key={city}>{cityLabel(city)}</option>)}
              </select>
            </label>
            <label>
              产业
              <select value={activeBuildIndustry} onChange={(event) => setBuildIndustry(event.target.value)}>
              {buildIndustryOptions.map((key) => (
                  <option value={key} key={key}>{industryNames[key] ?? key}</option>
                ))}
              </select>
            </label>
            {buildNeedsCoal ? (
              <ResourceSelectionPanel title="资源来源确认">
                <ResourceSourceSelect label="煤" value={selectedCoalSourceId} onChange={setSelectedCoalSourceId} sources={buildCoalSources} />
                {buildNeedsIron ? (
                  <ResourceSourceSelect label="铁" value={selectedIronSourceId} onChange={setSelectedIronSourceId} sources={buildIronSources} />
                ) : null}
              </ResourceSelectionPanel>
            ) : buildNeedsIron ? (
              <ResourceSelectionPanel title="资源来源确认">
                <ResourceSourceSelect label="铁" value={selectedIronSourceId} onChange={setSelectedIronSourceId} sources={buildIronSources} />
              </ResourceSelectionPanel>
            ) : null}
            {buildCompatibleCardIds.length ? (
              <span className="muted-note">可用手牌：{buildCompatibleCardIds.length}</span>
            ) : null}
            <span className="muted-note">
              目标：{cityLabel(buildCity)} / {industryNames[activeBuildIndustry] ?? activeBuildIndustry}
              {selectedBuildOption?.slotIndex !== undefined ? ` / 槽位${Number(selectedBuildOption.slotIndex) + 1}` : ""}
            </span>
            {buildBlockReason ? <div className="form-error">{buildBlockReason}</div> : null}
            <button
              className="primary-button"
              disabled={Boolean(buildBlockReason)}
              onClick={() => confirmed(buildConfirmText(selectedBuildOption, industryNames, cityLabel), "build", {
                city: buildCity,
                industryType: activeBuildIndustry,
                slotIndex: selectedBuildOption?.slotIndex,
                cardId: selectedCardId,
                coalSourceTileId: selectedCoalSourceId || undefined,
                ironSourceTileId: selectedIronSourceId || undefined
              })}
            >
              {TEXT.brassBuild}
            </button>
          </div> : null}
          {activeAction === "network" ? <div className="brass-build-controls">
            <p className="muted-note">步骤：选择1张手牌 → 选择路线 → 铁路时代可选择第二条路线 → 选择资源来源 → 确认修路。</p>
            <label>
              路线
              <select value={networkRoute} onChange={(event) => setNetworkRoute(event.target.value)}>
                <option value="">请选择路线</option>
                {availableRoutesForSelect.map((route) => (
                  <option value={brassRouteKey(route)} key={brassRouteKey(route)}>
                    {routeLabel(route)}
                  </option>
                ))}
              </select>
            </label>
            {state.era === "rail" ? (
              <label>
                第二条路线
                <select value={secondNetworkRoute} onChange={(event) => setSecondNetworkRoute(event.target.value)}>
                  <option value="">不修第二条路线</option>
                  {availableRoutesForSelect
                    .filter((route) => !networkRoute || compatibleSecondRouteKeys.has(brassRouteKey(route)))
                    .map((route) => (
                    <option value={brassRouteKey(route)} key={brassRouteKey(route)}>
                      {routeLabel(route)}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <span className="muted-note">铁路需要煤；同一次修两条铁路还需要啤酒。</span>
            <span className="muted-note">
              目标：{selectedRoute ? routeLabel(selectedRoute) : "-"}
              {selectedSecondRoute ? ` / ${routeLabel(selectedSecondRoute)}` : ""}
            </span>
            {networkNeedsCoal ? (
              <ResourceSelectionPanel title="资源来源确认">
                <ResourceSourceSelect label="煤" value={selectedCoalSourceId} onChange={setSelectedCoalSourceId} sources={networkCoalSources} />
                {secondNetworkNeedsCoal ? (
                  <ResourceSourceSelect label="第二条路的煤" value={selectedSecondCoalSourceId} onChange={setSelectedSecondCoalSourceId} sources={secondNetworkCoalSources} />
                ) : null}
                {state.era === "rail" && selectedSecondRoute ? (
                  <ResourceSourceSelect
                    label="啤酒"
                    value={selectedBeerSourceId}
                    onChange={setSelectedBeerSourceId}
                    sources={networkBeerSources}
                  />
                ) : null}
              </ResourceSelectionPanel>
            ) : null}
            <button
              className="primary-button"
              disabled={Boolean(networkBlockReason)}
              onClick={() => {
                const networkPayload = {
                  cardId: selectedCardId,
                  from: selectedRoute?.from,
                  to: selectedRoute?.to,
                  coalSourceTileId: selectedCoalSourceId || undefined
                };
                if (state.era === "rail" && selectedSecondRoute) {
                  networkPayload.routes = [
                    { from: selectedRoute?.from, to: selectedRoute?.to, coalSourceTileId: selectedCoalSourceId || undefined },
                    { from: selectedSecondRoute.from, to: selectedSecondRoute.to, coalSourceTileId: selectedSecondCoalSourceId || undefined }
                  ];
                  networkPayload.linkIds = [brassRouteKey(selectedRoute), brassRouteKey(selectedSecondRoute)];
                  networkPayload.beerSourceTileId = selectedBeerSourceId || undefined;
                } else {
                  networkPayload.linkIds = selectedRoute ? [brassRouteKey(selectedRoute)] : [];
                }
                confirmed("是否修建所选路线？", "network", networkPayload);
              }}
            >
              {TEXT.brassNetwork}
            </button>
            {networkBlockReason ? <div className="form-error">{networkBlockReason}</div> : null}
          </div> : null}
          {activeAction === "develop" ? <div className="brass-develop-panel">
            <strong>研发</strong>
            <p className="muted-note">步骤：选择1张手牌 → 选择1到2个产业 → 选择铁来源 → 确认研发。</p>
            <div className="brass-develop-options">
              {Object.entries(industryNames).map(([key, label]) => (
                <button
                  key={key}
                  className={`small-button ${developIndustries.includes(key) ? "secondary-button" : "ghost-button"}`}
                  disabled={!isCurrentPlayer}
                  onClick={() => toggleDevelopIndustry(key)}
                >
                  {label}{developIndustries.filter((item) => item === key).length > 0 ? ` x${developIndustries.filter((item) => item === key).length}` : ""}
                </button>
              ))}
              <button className="small-button ghost-button" disabled={!isCurrentPlayer || developIndustries.length === 0} onClick={() => setDevelopIndustries([])}>
                清空
              </button>
            </div>
            {developNeedsIron ? (
              <ResourceSelectionPanel title="资源来源确认">
                <ResourceSourceSelect label="铁" value={selectedIronSourceId} onChange={setSelectedIronSourceId} sources={developIronSources} />
              </ResourceSelectionPanel>
            ) : null}
            <span className="muted-note">
              目标：{developIndustries.map((industry) => industryNames[industry] ?? industry).join(" / ") || "-"} / 铁源 {developIronSources.length}
            </span>
            {developBlockReason ? <div className="form-error">{developBlockReason}</div> : null}
            <button
              className="primary-button"
              disabled={Boolean(developBlockReason)}
              onClick={() => confirmed("是否研发所选产业？", "develop", {
                cardId: selectedCardId,
                industryTypes: developIndustries,
                ironSourceTileId: selectedIronSourceId || undefined
              })}
            >
              {TEXT.brassDevelop}
            </button>
          </div> : null}
          {activeAction === "sell" ? <div className="brass-action-panel">
            <div className="proposal-banner brass-sell-banner">
              <span>
                出售行动：{sellInProgress ? `已售卖 ${sellCount} 个产业，可继续选择下一个产业。` : "请选择1张手牌后开始售卖。"}
              </span>
              <div className="inline-actions">
                <button
                  className="secondary-button"
                  disabled={Boolean(sellBlockReason)}
                  onClick={() => confirmed(sellConfirmText(selectedSellOption, selectedMerchant, selectedBeerSourceId, sellBeerSources, cityLabel), "sell", {
                    cardId: sellInProgress ? undefined : selectedCardId,
                    tileId: selectedTileId,
                    merchantId: selectedMerchantId || undefined,
                    beerSourceTileId: selectedMerchantId ? undefined : selectedBeerSourceId || undefined,
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
            <label>
              贸易商啤酒
              <select value={selectedMerchantId} onChange={(event) => setSelectedMerchantId(event.target.value)}>
                <option value="">不使用贸易商啤酒</option>
                {sellMerchantSources.map((merchant) => (
                  <option value={merchant.id} key={merchant.id}>
                    {cityLabel(merchant.city)}（奖励：{brassMerchantRewardText(merchant.reward)}，啤酒 {merchant.beer ?? 0}，接受 {(merchant.acceptedIndustryTypes ?? []).map((industry) => BRASS_INDUSTRIES[industry] ?? industry).join("/") || "任意"}）
                  </option>
                ))}
              </select>
            </label>
            {sellBeerSources.length > 0 ? (
              <ResourceSelectionPanel title="资源来源确认">
                <ResourceSourceSelect
                  label="酿酒厂啤酒"
                  value={selectedBeerSourceId}
                  onChange={setSelectedBeerSourceId}
                  sources={sellBeerSources}
                />
              </ResourceSelectionPanel>
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
                    setSelectedBeerSourceId("");
                  }}
                >
                  <strong>{cityLabel(tile.city)} {brassIndustryName(tile.industryType, tile.industryName)}</strong>
                </button>
              ))}
            </div>
          </div> : null}
          {["loan", "scout", "skip", "restart_turn", "end_turn", "maintain_era"].includes(activeAction) ? <div className="brass-action-panel">
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
          </div> : null}
        </section>

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

      <aside className="brass-sidebar-column">
        <section className="panel brass-game-info-panel">
          <h2>游戏信息</h2>
          <dl className="brass-game-info-list">
            <div><dt>当前时代</dt><dd>{brassEraText(state.era)}</dd></div>
            <div><dt>当前轮次</dt><dd>第{Math.max(1, Number(state.round ?? 1))}轮</dd></div>
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
      </aside>
      </div>

      {cardHintsOpen ? (
        <CardHintModal cardHints={state.cardHints ?? {}} onClose={() => setCardHintsOpen(false)} />
      ) : null}

    </div>
  );
}

function BrassMap({
  cities,
  routes,
  links,
  industries,
  activeAction,
  buildOptions = [],
  sellOptions = [],
  selectedSellTileId = "",
  selectedMerchantId = "",
  networkRouteOptions = [],
  compatibleSecondRouteKeys = new Set(),
  selectedCardId = "",
  selectedCard = null,
  selectedCity,
  selectedSlotKey,
  selectedRouteKey,
  selectedSecondRouteKey,
  onCityClick,
  onCitySlotClick,
  onRouteClick,
  merchants = [],
  playerCount = 0,
  cityNames = {}
}) {
  const cityByName = new Map(cities.map((city) => [brassMapKey(city.name), city]));
  const linkedKeys = new Set((links ?? []).map(brassRouteKey));
  const builtBySlot = new Map((industries ?? []).map((tile) => [brassSlotKey(tile.city, tile.slotIndex ?? 0), tile]));
  const sellableTileIds = new Set((sellOptions ?? []).map((tile) => String(tile.id)));
  const selectedSellOption = (sellOptions ?? []).find((tile) => String(tile.id) === String(selectedSellTileId));
  const buildableSlots = new Set();
  for (const option of buildOptions) {
    if (selectedCardId && !(option.cardIds ?? []).includes(selectedCardId)) {
      continue;
    }
    const cityKey = brassMapKey(option.city);
    for (const slot of birminghamMapLayout.citySlots ?? []) {
      if (brassMapKey(slot.city) === cityKey
        && Number(slot.slotIndex) === Number(option.slotIndex)
        && (slot.allowedIndustryTypesBackend ?? []).includes(option.industryType)) {
        buildableSlots.add(brassSlotKey(slot.city, slot.slotIndex));
      }
    }
  }
  const cardHintSlots = new Set();
  if (selectedCardId && selectedCard && !selectedCard.wild) {
    const cardType = String(selectedCard.type ?? "");
    const cardKey = brassMapKey(selectedCard.key);
    if (cardType === "location" || cardType === "place") {
      for (const slot of birminghamMapLayout.citySlots ?? []) {
        if (brassMapKey(slot.city) === cardKey) {
          cardHintSlots.add(brassSlotKey(slot.city, slot.slotIndex));
        }
      }
    } else if (cardType === "industry") {
      const industryKey = String(selectedCard.key ?? "");
      for (const slot of birminghamMapLayout.citySlots ?? []) {
        if ((slot.allowedIndustryTypesBackend ?? []).includes(industryKey)) {
          cardHintSlots.add(brassSlotKey(slot.city, slot.slotIndex));
        }
      }
    }
  }
  const networkKeys = new Set(selectedCardId ? (networkRouteOptions ?? []).map(brassRouteKey) : []);
  const imageWidth = birminghamMapLayout.image?.width ?? 1247;
  const imageHeight = birminghamMapLayout.image?.height ?? 1242;
  const marketSlots = marketSlotsForLayout(birminghamMapLayout, merchants, playerCount);
  return (
    <div className="brass-map">
      <div className="brass-map-image-wrap">
        <img src={birminghamMapLayout.image.src} alt="伯明翰地图" />
        <svg viewBox={`0 0 ${imageWidth} ${imageHeight}`} preserveAspectRatio="xMidYMid meet" role="img" aria-label="伯明翰真实地图交互层">
        {(birminghamMapLayout.routes ?? []).map((layoutRoute) => {
          const route = findRouteForLayout(routes, layoutRoute);
          const key = route ? brassRouteKey(route) : brassLayoutRouteKey(layoutRoute);
          const selected = key === selectedRouteKey || key === selectedSecondRouteKey;
          const built = linkedKeys.has(key);
          const available = activeAction === "network" && (networkKeys.has(key) || compatibleSecondRouteKeys.has(key));
          const routeClassName = ["brass-map-route", selected ? "selected" : "", built ? "built" : "", available ? "available" : ""].filter(Boolean).join(" ");
          const points = layoutRoute.path ?? [];
          if (points.length < 2) return null;
          const pointString = points.map((point) => `${point.x * imageWidth},${point.y * imageHeight}`).join(" ");
          const mid = points[Math.floor(points.length / 2)];
          const builtLink = (links ?? []).find((link) => brassRouteKey(link) === key);
          const routeTitle = routeTooltip(route, layoutRoute, builtLink, cityByName, cityNames);
          return (
            <g key={key}>
              <polyline
                className={routeClassName}
                style={routeColorStyle(links, key)}
                points={pointString}
              >
                <title>{routeTitle}</title>
              </polyline>
              <polyline
                className="brass-map-route-hit"
                points={pointString}
                strokeWidth={layoutRoute.clickWidth ?? 14}
                onClick={() => route && onRouteClick(route)}
              />
              <text
                className="brass-map-route-label"
                x={mid.x * imageWidth}
                y={mid.y * imageHeight}
                onClick={() => route && onRouteClick(route)}
              >
                <title>{routeTitle}</title>
                {builtLink ? (
                  <>
                    <tspan x={mid.x * imageWidth} dy="-0.35em">{shortPlayerName(builtLink.ownerName)}</tspan>
                    <tspan x={mid.x * imageWidth} dy="1.15em">{builtLink.currentScore ?? 0}分</tspan>
                  </>
                ) : key === selectedSecondRouteKey ? "2" : ""}
              </text>
            </g>
          );
        })}
        {(birminghamMapLayout.citySlots ?? []).map((slot) => {
          const city = cityByName.get(brassMapKey(slot.city)) ?? { name: slot.city, cnName: brassCityName(cities, slot.city, cityNames) };
          const rect = slot.rect;
          const key = brassSlotKey(slot.city, slot.slotIndex);
          const builtTile = builtBySlot.get(key);
          const selected = key === selectedSlotKey;
          const available = activeAction === "build" && buildableSlots.has(key) && !builtTile;
          const sellable = activeAction === "sell" && builtTile && sellableTileIds.has(String(builtTile.id));
          const sellSelected = activeAction === "sell" && builtTile && String(builtTile.id) === String(selectedSellTileId);
          const hinted = cardHintSlots.has(key);
          const className = ["brass-map-slot", selected || sellSelected ? "selected" : "", hinted ? "hinted" : "", available || sellable ? "available" : "", builtTile ? "built" : "", builtTile?.flipped ? "flipped" : ""].filter(Boolean).join(" ");
          const slotTitle = industrySlotTooltip(slot, city, builtTile, cityNames);
          return (
            <g key={key} className={className} onClick={() => onCitySlotClick(slot)}>
              <rect
                x={rect.x * imageWidth}
                y={rect.y * imageHeight}
                width={rect.w * imageWidth}
                height={rect.h * imageHeight}
                rx="5"
              >
                <title>{slotTitle}</title>
              </rect>
              {builtTile ? <text textAnchor="middle" x={(rect.x + rect.w / 2) * imageWidth} y={(rect.y + rect.h / 2) * imageHeight}>
                <title>{slotTitle}</title>
                <tspan x={(rect.x + rect.w / 2) * imageWidth} dy="-0.35em">{shortPlayerName(builtTile.ownerName)}</tspan>
                <tspan x={(rect.x + rect.w / 2) * imageWidth} dy="1.15em">{brassIndustryShortName(builtTile.industryType, builtTile.industryName)}</tspan>
              </text> : null}
            </g>
          );
        })}
        {marketSlots.map((slot) => {
          const rect = slot.rect;
          const title = marketSlotTooltip(slot);
          const merchantSelected = selectedMerchantId && selectedMerchantId === slot.merchantId;
          const merchantAvailable = activeAction === "sell" && selectedSellOption
            && (selectedSellOption.merchantSources ?? []).some((merchant) => merchant.id === slot.merchantId);
          return (
            <g key={slot.key} className={`brass-market-slot-overlay ${slot.status} ${merchantAvailable ? "available" : ""} ${merchantSelected ? "selected" : ""}`}>
              <rect
                x={rect.x * imageWidth}
                y={rect.y * imageHeight}
                width={rect.w * imageWidth}
                height={rect.h * imageHeight}
                rx="6"
                onClick={() => onCitySlotClick(slot)}
              >
                <title>{title}</title>
              </rect>
              <text textAnchor="middle" x={(rect.x + rect.w / 2) * imageWidth} y={(rect.y + rect.h / 2) * imageHeight}>
                市
              </text>
            </g>
          );
        })}
      </svg>
      </div>
      <p className="muted-note">游戏地图</p>
    </div>
  );
}

function BrassMapInfoPanel({ info }) {
  if (!info) {
    return (
      <div className="brass-map-info">
        <strong>地图信息</strong>
        <span className="muted-note">点击任意城市产业格查看城市和格位信息。</span>
      </div>
    );
  }
  return (
    <div className="brass-map-info">
      <strong>{info.title}</strong>
      <div className="brass-map-info-grid">
        {info.lines.map((line, index) => (
          <span key={`${index}-${line}`}>{line}</span>
        ))}
      </div>
    </div>
  );
}

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

function BrassMarket({ market }) {
  const coal = objectCounts(market.coal ?? []);
  const iron = objectCounts(market.iron ?? []);
  return (
    <div className="brass-market-board">
      <BrassMarketTrack title="煤市场" prices={[1, 2, 3, 4, 5, 6, 7]} counts={coal} fallback="£8" />
      <BrassMarketTrack title="铁市场" prices={[1, 2, 3, 4, 5]} counts={iron} fallback="£6" />
    </div>
  );
}

function BrassMarketTrack({ title, prices, counts, fallback }) {
  return (
    <div className="brass-market-track">
      <strong>{title}</strong>
      <div className="brass-market-slots">
        {prices.flatMap((price) => [0, 1].map((slot) => {
          const available = slot >= 2 - (counts[String(price)] ?? 0);
          return (
            <span className={`brass-market-slot ${available ? "available" : "empty"}`} key={`${title}-${price}-${slot}`}>
              £{price}
            </span>
          );
        }))}
        <span className="brass-market-slot distant">{fallback}</span>
      </div>
    </div>
  );
}

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

function CamelPlayersPanel({ state, currentUserId }) {
  const money = state.money ?? {};
  const tokens = state.playerTokens ?? {};
  const bets = state.bets ?? {};
  const hands = state.hands ?? {};
  return (
    <section className="panel camel-player-panel">
      <h2>{TEXT.playerArea}</h2>
      <div className="things-player-list">
        {(state.players ?? []).map((player) => {
          const playerId = String(player.userId);
          const playerBets = bets[playerId] ?? {};
          return (
            <div className={`things-player-card ${playerId === String(state.currentPlayerId) ? "active" : ""}`} key={playerId}>
              <div className="things-player-head">
                <strong>{player.seatIndex + 1}. {player.username}{playerId === currentUserId ? " (me)" : ""}</strong>
                <span>{money[playerId] ?? 0}{TEXT.egyptPounds}</span>
              </div>
              <small>标记：捷径 {camelTokenStatusText(tokens[playerId]?.shortcut)} / 耳廓狐 {camelTokenStatusText(tokens[playerId]?.fennec)}</small>
              <small>Hand: {hands[playerId] ? camelRaceCardText(hands[playerId]) : "none"}</small>
              <small>{TEXT.legBets}: {(playerBets.leg ?? []).map(camelBetCardText).join(", ") || "none"}</small>
              <small>{TEXT.finalBets}: {[playerBets.final_winner, playerBets.final_loser].filter(Boolean).map(camelBetCardText).join(", ") || "none"}</small>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function ResourceSourceSelect({ label, value, onChange, sources }) {
  return (
    <label>
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">自动选择</option>
        {sources.map((source) => (
          <option value={source.id} key={source.id}>
            {source.ownerName} - {source.city} {source.industryName} ({source.coal ?? source.iron ?? source.beer ?? 0})
          </option>
        ))}
      </select>
    </label>
  );
}

function ResourceSelectionPanel({ title, children }) {
  return (
    <fieldset className="brass-resource-panel">
      <legend>{title}</legend>
      {children}
    </fieldset>
  );
}

function resourceSourcesForSelect(actionSources, industries, industryType, resourceField) {
  if (Array.isArray(actionSources)) {
    return actionSources.map((source) => ({
      id: source.id,
      ownerName: source.ownerName,
      city: source.city,
      industryName: BRASS_INDUSTRIES[industryType] ?? industryType,
      [resourceField]: source.amount
    }));
  }
  return industries.filter((tile) => tile.industryType === industryType && (tile[resourceField] ?? 0) > 0);
}

function buildConfirmText(option = {}, industryNames = BRASS_INDUSTRIES, cityLabel = (city) => city) {
  const industryName = industryNames[option.industryType] ?? option.industryName ?? option.industryType ?? "-";
  const parts = [
    `是否在${cityLabel(option.city)}城市建造${option.level ? `${option.level}级` : ""}${industryName}？`,
    `花费：${option.cost ?? 0}英镑`
  ];
  const resourceCosts = [];
  if (Number(option.coalCost ?? 0) > 0) resourceCosts.push(`煤x${option.coalCost}`);
  if (Number(option.ironCost ?? 0) > 0) resourceCosts.push(`铁x${option.ironCost}`);
  parts.push(`资源需求：${resourceCosts.length ? resourceCosts.join("、") : "无"}`);
  if (option.coversOpponent) {
    parts.push("该建造会覆盖对手资源产业。");
  }
  return parts.join("\n");
}

function sellConfirmText(option = {}, merchant = null, beerSourceId = "", beerSources = [], cityLabel = (city) => city) {
  const industryName = brassIndustryName(option.industryType, option.industryName);
  const parts = [
    `是否出售${cityLabel(option.city)}城市的${option.level ? `${option.level}级` : ""}${industryName}？`
  ];
  if (merchant) {
    parts.push(`使用${cityLabel(merchant.city)}市场的贸易商啤酒。`);
    parts.push(`市场奖励：${brassMerchantRewardText(merchant.reward)}`);
  } else {
    const beerSource = (beerSources ?? []).find((source) => source.id === beerSourceId);
    parts.push(beerSource
      ? `使用${beerSource.ownerName ?? "-"}在${cityLabel(beerSource.city)}的酿酒厂啤酒。`
      : "啤酒来源：自动选择。");
  }
  return parts.join("\n");
}

function CamelRankings({ rankings }) {
  const racing = rankings.filter((rank) => rank.color !== "black");
  return (
    <div className="camel-ranking-panel">
      <strong>{TEXT.camelRankings}</strong>
      <div className="camel-ranking-list">
        {racing.map((rank, index) => (
          <span key={rank.color} className={`camel-rank-item ${rank.color}`}>
            {index + 1}. {CAMEL_COLORS[rank.color] ?? rank.color} - {rank.position}</span>
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
        <h2>Setup selection</h2>
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
                {camelRaceCardText(card)}
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
  return (
    <div className="camel-track">
      {Array.from({ length: finish + 1 }, (_, position) => (
        <div className={`camel-cell ${sandstormPositions.has(position) ? "sandstorm" : ""}`} key={position}>
          <strong>{position}{position === finish ? " Finish" : ""}</strong>
          {sandstormPositions.has(position) ? <em>{TEXT.sandstorm}</em> : null}
          <div className="camel-stack">
            {asArray(stacks[String(position)]).slice().reverse().map((color) => (
              <span className={`camel-chip ${color}`} key={`${position}-${color}`}>{CAMEL_COLORS[color] ?? color}</span>
            ))}
          </div>
          <small>{camelTokenAt(tokens, position)}</small>
        </div>
      ))}
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
        <button key={`winner-${color}`} className="ghost-button small-button" disabled={!canBet || cards.length === 0} onClick={() => onTake(cards[0])}>
          {CAMEL_COLORS[color] ?? color}: {cards[0] ? camelBetCardText(cards[0]) : "empty"}
        </button>
        );
      })}
      <h3>{TEXT.legMiddle}</h3>
      {Object.entries(legMiddle).map(([color, rawCards]) => {
        const cards = asArray(rawCards);
        return (
        <button key={`middle-${color}`} className="ghost-button small-button" disabled={!canBet || cards.length === 0} onClick={() => onTake(cards[0])}>
          {CAMEL_COLORS[color] ?? color}: {cards[0] ? camelBetCardText(cards[0]) : "empty"}
        </button>
        );
      })}
      <h3>{TEXT.finalBets}</h3>
      <div className="camel-final-market">
        {asArray(market.finalWinner).map((card) => (
          <button key={card.id} className="ghost-button small-button" disabled={!canBet} onClick={() => onTake(card)}>
            {camelBetCardText(card)}
          </button>
        ))}
        {asArray(market.finalLoser).map((card) => (
          <button key={card.id} className="ghost-button small-button" disabled={!canBet} onClick={() => onTake(card)}>
            {camelBetCardText(card)}
          </button>
        ))}
      </div>
    </div>
  );
}

function ThingsRulesPanel({ rules, canSeeRules }) {
  return (
    <section className="panel things-rules-panel">
      <h2>{TEXT.hostArea}</h2>
      <div className="things-rule-grid">
        <RuleBox type="scene" label={TEXT.sceneRule} value={canSeeRules ? ruleText(rules.SCENE) : ""} />
        <RuleBox type="word" label={TEXT.wordRule} value={canSeeRules ? ruleText(rules.WORD) : ""} />
        <RuleBox type="attribute" label={TEXT.attributeRule} value={canSeeRules ? ruleText(rules.ATTRIBUTE) : ""} />
      </div>
    </section>
  );
}

function RuleBox({ type, label, value }) {
  return (
    <div className={`things-rule-box ${type}`}>
      <input value={value ?? ""} placeholder={label} readOnly />
    </div>
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
      <h2>{inInitial ? `Initial host placements remaining: ${state.initialPlacementsRemaining ?? 0}` : TEXT.currentTurn}</h2>
      {word ? (
        <>
          <p>{wordText(word)}</p>
          {isHost ? (
            <div className="area-button-grid">
              {THINGS_AREAS.map((area) => (
                <button
                  key={area.key}
                  onClick={() => confirmAction(`Place ${wordText(word)} to ${area.short}?`, "host_place", { area: area.key })}
                >
                  {coloredAreaShortLabel(area)}
                </button>
              ))}
            </div>
          ) : <p className="muted-note">Waiting for host placement.</p>}
        </>
      ) : null}
      {!inInitial && isHost ? (
        <div className="button-inline">
          <button
            className="secondary-button"
            disabled={state.hostHintUsed === true || Boolean(word)}
            onClick={() => confirmAction("Use hint?", "host_hint")}
          >
            {TEXT.hint}
          </button>
          <button
            className="secondary-button"
            disabled={state.hostSkipUsed === true || Boolean(word)}
            onClick={() => confirmAction("End turn?", "host_skip")}
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
          return (
            <div className={`things-player-card ${playerId === currentPlayerId ? "active" : ""}`} key={playerId}>
              <div className="things-player-head">
                <strong>{player.seatIndex + 1}. {player.username}</strong>
                <span>{playerId === hostId ? TEXT.host : TEXT.guesser}</span>
              </div>
              <small>{TEXT.handCount}: {hand.length}</small>
              {showCards ? (
                <div className="things-hand">
                  {hand.map((word) => (
                    <button
                      key={word.id}
                      className={selectedWordId === String(word.id) ? "active" : ""}
                      disabled={playerId !== currentUserId || !canSelect}
                      onClick={() => onSelectWord(String(word.id))}
                    >
                      {wordText(word)}
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

function ThingsAreaBoard({ placedWords, selectedWord, canPlace, onPlace }) {
  return (
    <section className="panel things-areas-panel">
      <h2>{TEXT.wordDisplayArea}</h2>
      <div className="things-area-grid">
        {THINGS_AREAS.map((area) => (
          <div className="things-area-card" key={area.key}>
            <strong>{coloredAreaLabel(area)}</strong>
            <div className="placed-word-list">
              {(placedWords[area.key] ?? []).map((word) => (
                <span key={word.id}>{wordText(word)}</span>
              ))}
            </div>
            {selectedWord && canPlace ? (
              <button className="ghost-button small-button" onClick={() => onPlace(area.key)}>
                {TEXT.placeHere}
              </button>
            ) : null}
          </div>
        ))}
      </div>
    </section>
  );
}

function coloredAreaLabel(area) {
  if (area.key === "NONE") {
    return <span>{area.label}</span>;
  }
  const labels = { scene: "Scene", word: "Word", attribute: "Attribute" };
  return area.parts.map((part, index) => (
    <span key={`${area.key}-${part}`}>
      {index > 0 ? "+" : ""}
      <span className={`area-${part}`}>{labels[part]}</span>
    </span>
  ));
}

function coloredAreaShortLabel(area) {
  if (area.key === "NONE") {
    return <span>{area.short}</span>;
  }
  const classByChar = { "R": "area-scene", "Y": "area-word", "B": "area-attribute" };
  return Array.from(area.short).map((char, index) => (
    <span key={`${area.key}-${char}-${index}`} className={classByChar[char] ?? ""}>
      {char}
    </span>
  ));
}

function SeatBoard({ owner, waiting, seats, showKick, onMoveSeat, onForceStandUp }) {
  return (
    <div className="seat-board">
      {seats.map((seat) => {
        const occupied = isSeatOccupied(seat);
        return (
          <div key={seat.seatIndex} className={`table-seat ${seat.currentUserSeat ? "mine" : ""}`}>
            {seat.ownerSeat ? <span className="seat-owner">{TEXT.owner}</span> : null}
            {showKick && occupied ? (
              <button
                className="seat-kick"
                type="button"
                title="\u8f6c\u4e3a\u89c2\u6218"
                aria-label="\u8f6c\u4e3a\u89c2\u6218"
                onClick={() => onForceStandUp(seat.seatIndex)}
              >
                {TEXT.kick}
              </button>
            ) : null}
            <div className="seat-center">
              {occupied ? (
                <strong>{displayName(seat.user)}</strong>
              ) : waiting ? (
                <button
                  className="ghost-button seat-switch"
                  onClick={() => onMoveSeat(seat.seatIndex)}
                >
                  {TEXT.switchSeat}
                </button>
              ) : null}
            </div>
            {seat.currentUserSeat ? <span className="seat-me">{TEXT.me}</span> : null}
            <span className="seat-number">{seat.seatIndex + 1}</span>
          </div>
        );
      })}
    </div>
  );
}

function NoticePanel({ notices, title = TEXT.notices, className = "" }) {
  return (
    <section className={`panel notice-panel ${className}`.trim()}>
      <h2>{title}</h2>
      <ul className="notice-list">
        {notices.map((notice, index) => (
          <li key={`${index}-${notice}`}>{notice}</li>
        ))}
      </ul>
    </section>
  );
}

function labelFor(gameType) {
  return GAME_LABELS[gameType] ?? gameType;
}

function roomCurrentPlayerName(room, seats = []) {
  const state = room?.gameState ?? {};
  const currentPlayerId = state.currentPlayerId;
  if (currentPlayerId === undefined || currentPlayerId === null || currentPlayerId === "") return "";
  const players = Array.isArray(state.players) ? state.players : [];
  const player = players.find((item) => String(item.userId ?? item.id) === String(currentPlayerId));
  if (player?.username) return player.username;
  const seat = seats.find((item) => String(item.userId ?? item.playerId) === String(currentPlayerId));
  return seat?.username ?? seat?.playerName ?? "";
}

function createClientActionId() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function camelPlayerName(players, userId) {
  return players.find((player) => String(player.userId) === String(userId))?.username ?? "";
}

function brassPlayerName(players, userId) {
  return players.find((player) => String(player.userId) === String(userId))?.username ?? "";
}

function brassEraText(era) {
  if (era === "canal") return TEXT.canalEra;
  if (era === "rail") return TEXT.railEra;
  return era ?? "";
}

function brassActionText(action) {
  const labels = {
    build: TEXT.brassBuild,
    sell: TEXT.brassSell,
    network: TEXT.brassNetwork,
    develop: TEXT.brassDevelop,
    loan: TEXT.brassLoan,
    scout: TEXT.brassScout,
    skip: TEXT.brassSkip,
    restart_turn: TEXT.brassRestartTurn,
    end_turn: TEXT.brassEndTurn,
    end_sell: "结束售卖行动",
    maintain_era: TEXT.brassMaintainEra,
    resolve_income_debt: "支付收入欠款"
  };
  return labels[action] ?? action;
}

function brassCardTypeText(card) {
  if (card?.wild) return "万能牌";
  if (card?.type === "location") return "地点牌";
  if (card?.type === "industry") {
    const industries = Array.isArray(card.industryTypes) && card.industryTypes.length > 0 ? card.industryTypes : [card.key];
    return `产业牌：${industries.map((industry) => BRASS_INDUSTRIES[industry] ?? industry).join("/")}`;
  }
  return card?.type ?? "";
}

function brassCardName(card) {
  if (!card) return "";
  if (card.displayName) return card.displayName;
  if (card.type === "wild_location" || card.key === "wild_location") return "万能地点牌";
  if (card.type === "wild_industry" || card.key === "wild_industry") return "万能产业牌";
  if (card.wild) return "万能牌";
  if (card.type === "location") return `地点牌：${card.city ?? card.key ?? ""}`;
  if (card.type === "industry") {
    const industries = Array.isArray(card.industryTypes) && card.industryTypes.length > 0 ? card.industryTypes : [card.key].filter(Boolean);
    return `产业牌：${industries.map((industry) => BRASS_INDUSTRIES[industry] ?? industry).join("/")}`;
  }
  return card.name ?? "";
}

function brassCardShortName(card, cities = [], cityNames = {}) {
  if (!card) return "";
  if (card.type === "wild_location" || card.key === "wild_location") return "万能地点牌";
  if (card.type === "wild_industry" || card.key === "wild_industry") return "万能产业牌";
  if (card.wild) return "万能牌";
  if (card.type === "location") return brassCityName(cities, card.city ?? card.key ?? "", cityNames);
  if (card.type === "industry") {
    const industries = Array.isArray(card.industryTypes) && card.industryTypes.length > 0 ? card.industryTypes : [card.key].filter(Boolean);
    return industries.map((industry) => BRASS_INDUSTRIES[industry] ?? industry).join("/");
  }
  return card.displayName ?? card.name ?? "";
}

function brassCardTitleStyle(card, cities = []) {
  if (!card || card.type !== "location") return undefined;
  const city = cities.find((item) => brassMapKey(item.name) === brassMapKey(card.city ?? card.key));
  const color = brassMapColorToCss(city?.color);
  return color ? { color } : undefined;
}

function brassMapColorToCss(color) {
  const key = String(color ?? "").toLowerCase();
  return {
    blue: "#2868b7",
    cyan: "#188d93",
    green: "#2f7d45",
    red: "#9b2d2d",
    "light red": "#b85a58",
    purple: "#7b4bb3",
    lilac: "#7b4bb3",
    orange: "#b56b1d",
    yellow: "#9a7200",
    "light yellow": "#9a7200",
    white: "#1f1a14"
  }[key] ?? "";
}

function brassDevelopmentText(development = {}) {
  const entries = Object.entries(BRASS_INDUSTRIES)
    .map(([key, label]) => `${label}${development[key] ?? 0}`);
  return entries.join(" / ");
}

function brassTurnOrderText(players, turnOrder) {
  if (!turnOrder || turnOrder.length === 0) return "";
  return turnOrder
    .map((userId) => brassPlayerName(players, userId))
    .filter(Boolean)
    .join(" → ");
}

function orderBrassPlayers(players = [], initialTurnOrder = [], currentUserId = "") {
  const playersById = new Map(players.map((player) => [String(player.userId), player]));
  const ordered = [];
  const included = new Set();
  for (const userId of initialTurnOrder ?? []) {
    const key = String(userId);
    const player = playersById.get(key);
    if (player && !included.has(key)) {
      ordered.push(player);
      included.add(key);
    }
  }
  for (const player of players) {
    const key = String(player.userId);
    if (!included.has(key)) {
      ordered.push(player);
      included.add(key);
    }
  }
  const viewerId = String(currentUserId ?? "");
  const viewer = playersById.get(viewerId);
  return viewer
    ? [viewer, ...ordered.filter((player) => String(player.userId) !== viewerId)]
    : ordered;
}

function brassIndustryName(industryType, fallback = "") {
  return BRASS_INDUSTRIES[industryType] ?? fallback ?? industryType;
}

function brassIndustryNames(cityNames = {}) {
  return {
    cotton_mill: BRASS_INDUSTRIES.cotton_mill ?? cityNames.Cotton,
    manufacturer: BRASS_INDUSTRIES.manufacturer ?? cityNames.Manufacture,
    brewery: BRASS_INDUSTRIES.brewery ?? cityNames.Brewery,
    pottery: BRASS_INDUSTRIES.pottery ?? cityNames.Pottery,
    iron_works: BRASS_INDUSTRIES.iron_works ?? cityNames.Iron,
    coal_mine: BRASS_INDUSTRIES.coal_mine ?? cityNames.Coal
  };
}

function brassCityName(cities = [], city, cityNames = {}) {
  const raw = String(city ?? "");
  const upper = raw.toUpperCase();
  return BRASS_CITY_CN[upper]
    ?? BRASS_CITY_CN[raw]
    ?? cityNames[upper]
    ?? cityNames[raw]
    ?? cities.find((item) => item.name === raw || String(item.name).toUpperCase() === upper)?.cnName
    ?? raw;
}

function brassMerchantRewardText(reward = "") {
  const text = String(reward);
  if (text.includes("Income")) return `收入等级 +${text.match(/\d+/)?.[0] ?? 2}`;
  if (text.includes("VP")) return `${text.match(/\d+/)?.[0] ?? 0}VP`;
  if (text.includes("Develop")) return "免费研发1个板块";
  if (text.includes("5")) return "获得5金钱";
  return text || "无";
}

function brassBoardTileTooltip(tile = {}) {
  const period = Number(tile.period ?? 0);
  const eraLimit = period === 1 ? "仅运河时代" : period === 2 ? "仅铁路时代" : "不限时代";
  const details = [
    `${brassIndustryName(tile.industryType, tile.industryName)} ${tile.level ?? ""}级`,
    `建造价格：£${tile.cost ?? 0}`,
    `煤需求：${tile.coalCost ?? 0}`,
    `铁需求：${tile.ironCost ?? 0}`,
    `建造时代限制：${eraLimit}`
  ];
  if (tile.flipType === "deplete") {
    const amounts = Array.isArray(tile.resourceAmounts) ? tile.resourceAmounts : [0, 0];
    const canalCapacity = period === 2 ? 0 : Number(amounts[0] ?? 0);
    const railCapacity = period === 1 ? 0 : Number(amounts[1] ?? amounts[0] ?? 0);
    details.push(`产能：${canalCapacity}/${railCapacity}`);
  }
  details.push(
    `可被研发：${tile.canDevelop === false ? "否" : "是"}`,
    `售卖啤酒需求：${tile.saleBeerCost ?? 0}`,
    `翻面后VP：${tile.victoryPoints ?? 0}`,
    `翻面后路分：${tile.roadPoints ?? 0}`,
    `翻面后收入等级增长：${tile.incomeReward ?? 0}`
  );
  return details.join("\n");
}

function buildBrassMapCities(metadata = [], routes = [], fallbackCities = [], cityNames = {}) {
  const fixedLayout = {
    WARRINGTON: [14, 14],
    "STOKE-ON-TRENT": [30, 12],
    STONE: [37, 25],
    STAFFORD: [45, 38],
    CANNOCK: [56, 51],
    WOLVERHAMPTON: [70, 43],
    BIRMINGHAM: [76, 61],
    COVENTRY: [101, 66],
    NUNEATON: [94, 48],
    TAMWORTH: [78, 39],
    WALSALL: [66, 32],
    "BURTON-ON-TRENT": [65, 19],
    DERBY: [78, 10],
    NOTTINGHAM: [104, 13],
    LEEK: [42, 7],
    UTTOXETER: [54, 16],
    BELPER: [88, 6],
    DUDLEY: [58, 63],
    COALBROOKDALE: [38, 61],
    SHREWSBURY: [18, 61],
    KIDDERMINSTER: [48, 76],
    WORCESTER: [67, 82],
    GLOUCESTER: [80, 91],
    REDDITCH: [88, 77],
    OXFORD: [106, 86],
    PERSONAL_BREWERY: [57, 88],
    RURAL_BREWERY: [48, 50],
    Personal_Brewery: [57, 88],
    Rural_Brewery: [48, 50]
  };
  const normalizedCityName = (name) => {
    const raw = String(name ?? "");
    const upper = raw.toUpperCase();
    return BRASS_CITY_CN[upper] ?? BRASS_CITY_CN[raw] ?? cityNames[upper] ?? cityNames[raw] ?? raw;
  };
  const metaByName = new Map((metadata ?? []).map((city) => [
    city.name,
    { ...city, cnName: normalizedCityName(city.name) ?? city.cnName ?? city.name }
  ]));
  const names = [...new Set([
    ...(metadata ?? []).map((city) => city.name),
    ...fallbackCities,
    ...routes.flatMap((route) => [route.from, route.to]).filter(Boolean)
  ])];
  const colorOrder = ["blue", "cyan", "green", "red", "purple", "orange", "yellow", "white"];
  const colorCenters = {
    blue: [18, 18],
    cyan: [45, 18],
    green: [72, 20],
    red: [22, 48],
    purple: [50, 50],
    orange: [78, 50],
    yellow: [48, 75],
    white: [12, 78]
  };
  const grouped = new Map();
  for (const name of names) {
    const meta = metaByName.get(name) ?? { name, cnName: normalizedCityName(name), color: "white", type: "Industry" };
    const color = meta.color || "white";
    if (!grouped.has(color)) grouped.set(color, []);
    grouped.get(color).push(meta);
  }
  const positions = [];
  for (const color of colorOrder) {
    const cities = grouped.get(color) ?? [];
    const [cx, cy] = colorCenters[color] ?? [50, 50];
    cities.forEach((city, index) => {
      const cols = Math.max(1, Math.ceil(Math.sqrt(cities.length)));
      const row = Math.floor(index / cols);
      const col = index % cols;
      positions.push({
        name: city.name,
        cnName: city.cnName ?? city.name,
        color,
        type: city.type ?? "",
        x: (fixedLayout[city.name] ?? fixedLayout[String(city.name).toUpperCase()])?.[0] ?? cx + (col - (cols - 1) / 2) * 13,
        y: (fixedLayout[city.name] ?? fixedLayout[String(city.name).toUpperCase()])?.[1] ?? cy + (row - Math.floor(cities.length / cols) / 2) * 10
      });
    });
  }
  const byName = new Map(positions.map((city) => [city.name, city]));
  for (const city of positions) {
    if (city.type !== "Market" || city.color !== "white") continue;
    if (fixedLayout[city.name] ?? fixedLayout[String(city.name).toUpperCase()]) continue;
    const neighbors = routes
      .filter((route) => route.from === city.name || route.to === city.name)
      .map((route) => byName.get(route.from === city.name ? route.to : route.from))
      .filter(Boolean);
    if (neighbors.length > 0) {
      city.x = neighbors.reduce((sum, item) => sum + item.x, 0) / neighbors.length + 8;
      city.y = neighbors.reduce((sum, item) => sum + item.y, 0) / neighbors.length + 4;
    }
  }
  return positions;
}

function routeColorStyle(links = [], key) {
  const link = links.find((item) => brassRouteKey(item) === key);
  const color = BRASS_PLAYER_COLORS[link?.color]?.css ?? link?.color;
  return color ? { stroke: color } : undefined;
}

function groupBrassBoardTiles(tiles = []) {
  const groups = new Map();
  for (const tile of tiles) {
    const key = [
      tile.industryType,
      tile.level,
      tile.cost,
      tile.coalCost,
      tile.ironCost,
      tile.period,
      tile.flipType,
      Array.isArray(tile.resourceAmounts) ? tile.resourceAmounts.join("/") : "",
      tile.canDevelop,
      tile.saleBeerCost,
      tile.victoryPoints,
      tile.roadPoints,
      tile.incomeReward
    ].join("|");
    const current = groups.get(key);
    if (current) {
      current.count += 1;
    } else {
      groups.set(key, { ...tile, groupKey: key, count: 1 });
    }
  }
  return [...groups.values()].sort((left, right) => Number(left.level ?? 0) - Number(right.level ?? 0));
}

function objectCounts(values = []) {
  return values.reduce((counts, value) => {
    const key = String(value);
    counts[key] = (counts[key] ?? 0) + 1;
    return counts;
  }, {});
}

function brassRouteKey(route) {
  if (!route) return "";
  const ends = [brassMapKey(route.from), brassMapKey(route.to)].sort();
  return `${ends[0]}|${ends[1]}`;
}

function brassSlotKey(city, slotIndex) {
  return `${brassMapKey(city)}:${Number(slotIndex ?? 0)}`;
}

function brassMapKey(value) {
  const raw = String(value ?? "");
  if (raw.toUpperCase() === "BREWERY") return "PERSONAL_BREWERY";
  return raw.toUpperCase();
}

function brassLayoutRouteKey(route) {
  return brassRouteKey({ from: route.src, to: route.dst });
}

function findRouteForLayout(routes = [], layoutRoute = {}) {
  const src = brassMapKey(layoutRoute.src);
  const dst = brassMapKey(layoutRoute.dst);
  return (routes ?? []).find((route) => {
    const from = brassMapKey(route.from);
    const to = brassMapKey(route.to);
    return (from === src && to === dst) || (from === dst && to === src);
  }) ?? null;
}

function routeTooltip(route, layoutRoute, builtLink, cityByName = new Map(), cityNames = {}) {
  const from = route?.from ?? layoutRoute.src;
  const to = route?.to ?? layoutRoute.dst;
  const fromName = brassCityName([...cityByName.values()], from, cityNames);
  const toName = brassCityName([...cityByName.values()], to, cityNames);
  const lines = [
    `路线：${fromName} - ${toName}`,
    `建造状态：${builtLink ? `玩家${builtLink.ownerName ?? "-"}建造` : "未建造"}`
  ];
  if (layoutRoute?.hasBrewery === true || route?.hasBrewery === true) {
    lines.push("附属酿酒厂：私人酿酒厂");
  }
  return lines.join("\n");
}

function industrySlotTooltip(slot, city, builtTile, cityNames = {}) {
  const cityName = brassCityName([city], slot.city, cityNames);
  if (builtTile) {
    const lines = [
      `玩家：${builtTile.ownerName ?? "-"}`,
      `城市：${cityName}`,
      `产业：${brassIndustryName(builtTile.industryType, builtTile.industryName)}`,
      `等级：${builtTile.level ?? "-"}`,
      `状态：${builtTile.flipped ? "已翻面" : "未翻面"}`
    ];
    if (builtTile.flipped) {
      lines.push(`产业VP：${builtTile.victoryPoints ?? builtTile.vp ?? 0}`);
      lines.push(`路分：${builtTile.roadPoints ?? builtTile.roadPoint ?? 0}`);
    } else if (["coal_mine", "iron_works", "brewery"].includes(builtTile.industryType)) {
      const resourceName = builtTile.industryType === "coal_mine" ? "煤" : builtTile.industryType === "iron_works" ? "铁" : "啤酒";
      const resourceValue = builtTile.remainingResource ?? (builtTile.industryType === "coal_mine" ? builtTile.coal : builtTile.industryType === "iron_works" ? builtTile.iron : builtTile.beer);
      lines.push(`剩余${resourceName}：${resourceValue ?? 0}`);
    } else {
      lines.push(`出售需要啤酒：${builtTile.saleWine ?? builtTile.saleBeerCost ?? 0}`);
    }
    return lines.join("\n");
  }
  const allowed = (slot.allowedIndustryTypesBackend ?? [])
    .map((industryType) => BRASS_INDUSTRIES[industryType] ?? industryType)
    .join("/");
  return [
    `城市：${cityName}`,
    `格位：${Number(slot.slotIndex ?? 0) + 1}号格`,
    `允许产业：${allowed || (slot.allowedIndustryTypes ?? []).join("/") || "-"}`
  ].join("\n");
}

function buildCitySlotInfo(slot, builtTile, cities = [], allIndustries = [], cityNames = {}) {
  const city = cities.find((item) => brassMapKey(item.name) === brassMapKey(slot.city)) ?? { name: slot.city, cnName: slot.city };
  const cityName = brassCityName(cities, slot.city, cityNames);
  const lines = [
    `城市名称：${cityName}`,
    `城市当前路分：${cityCurrentRoadPoint(slot.city, allIndustries)}`,
    `地图格：${Number(slot.slotIndex ?? 0) + 1}号格`
  ];
  if (builtTile) {
    lines.push(`建造情况：玩家${builtTile.ownerName ?? "-"} 的 ${brassIndustryName(builtTile.industryType, builtTile.industryName)}`);
    lines.push(`状态：${builtTile.flipped ? "已翻面" : "未翻面"}`);
    if (builtTile.flipped) {
      lines.push(`产业价值：${builtTile.victoryPoints ?? builtTile.vp ?? 0} VP`);
      lines.push(`提供路分：${builtTile.roadPoints ?? builtTile.roadPoint ?? 0}`);
    } else if (["coal_mine", "iron_works", "brewery"].includes(builtTile.industryType)) {
      const resourceName = builtTile.industryType === "coal_mine" ? "煤" : builtTile.industryType === "iron_works" ? "铁" : "酒";
      const resourceValue = builtTile.remainingResource ?? (builtTile.industryType === "coal_mine" ? builtTile.coal : builtTile.industryType === "iron_works" ? builtTile.iron : builtTile.beer);
      lines.push(`剩余资源：${resourceValue ?? 0} ${resourceName}`);
    } else {
      lines.push(`翻面后预计价值：${builtTile.victoryPoints ?? builtTile.vp ?? 0} VP`);
      lines.push(`翻面后预计路分：${builtTile.roadPoints ?? builtTile.roadPoint ?? 0}`);
    }
  } else {
    const allowed = (slot.allowedIndustryTypesBackend ?? [])
      .map((industryType) => BRASS_INDUSTRIES[industryType] ?? industryType)
      .join(" / ");
    lines.push(`建造情况：可建造${allowed || "-"}`);
  }
  return {
    title: `${cityName}#${Number(slot.slotIndex ?? 0) + 1}`,
    lines
  };
}

function buildRouteInfo(route, links = [], cities = [], cityNames = {}) {
  const builtLink = links.find((link) => brassRouteKey(link) === brassRouteKey(route));
  const from = brassCityName(cities, route.from, cityNames);
  const to = brassCityName(cities, route.to, cityNames);
  const lines = [
    `路线：${from} - ${to}`,
    `建造状态：${builtLink ? `玩家${builtLink.ownerName ?? "-"}建造` : "未建造"}`
  ];
  if (route?.hasBrewery === true) {
    lines.push("附属酿酒厂：私人酿酒厂");
  }
  return { title: "道路信息", lines };
}

function marketSlotsForLayout(layout, merchants = [], playerCount = 0) {
  const configured = layout.marketSlots ?? [];
  const inferred = configured.length ? configured : inferMarketSlotsFromRoutes(layout);
  const merchantsBySlot = new Map((merchants ?? []).map((merchant) => [`${brassMapKey(merchant.city)}:${Number(merchant.slotIndex ?? 0)}`, merchant]));
  const tradableSlotIndexByKey = new Map();
  const tradableCounters = new Map();
  for (const slot of inferred) {
    if (Number(slot.availablePlayers ?? 0) === 0) continue;
    const cityKey = brassMapKey(slot.city);
    const nextIndex = tradableCounters.get(cityKey) ?? 0;
    tradableSlotIndexByKey.set(`${cityKey}:${Number(slot.index ?? 0)}`, nextIndex);
    tradableCounters.set(cityKey, nextIndex + 1);
  }
  return inferred.map((slot) => {
    const cityKey = brassMapKey(slot.city);
    const isDisplayOnly = Number(slot.availablePlayers ?? 0) === 0;
    const isOpenForPlayerCount = isDisplayOnly || Number(slot.availablePlayers ?? 0) <= Number(playerCount ?? 0);
    const merchantSlotIndex = tradableSlotIndexByKey.get(`${cityKey}:${Number(slot.index ?? 0)}`);
    const merchant = isDisplayOnly ? null : merchantsBySlot.get(`${cityKey}:${Number(merchantSlotIndex ?? -1)}`);
    const accepted = (merchant?.acceptedIndustryTypes ?? [])
      .map((industryType) => BRASS_INDUSTRIES[industryType] ?? industryType)
      .join("、");
    const status = isDisplayOnly
      ? "display"
      : !isOpenForPlayerCount || !merchant || merchant.marketOpen === false
      ? "closed"
      : merchant.blank
        ? "blank"
        : "sellable";
    const message = status === "display"
      ? "市场位置展示"
      : status === "closed"
      ? "未开放"
      : status === "blank"
        ? "无可售卖产业"
        : `可以售卖${accepted || "任意产业"}`;
    return {
      ...slot,
      key: `market-${slot.city}-${slot.index}`,
      kind: "market",
      cityName: BRASS_CITY_CN[brassMapKey(slot.city)] ?? slot.cnName ?? slot.city,
      displayOnly: isDisplayOnly,
      merchantId: merchant?.id ?? "",
      merchantSlotIndex,
      status,
      beerStatus: status === "sellable" ? (Number(merchant?.beer ?? 0) > 0 ? "未消耗" : "已消耗") : "未提供",
      playerCount,
      message
    };
  });
}

function inferMarketSlotsFromRoutes(layout) {
  const marketCities = {
    WARRINGTON: { cnName: "沃灵顿", count: 2, availablePlayers: 3 },
    SHREWSBURY: { cnName: "舒兹伯利", count: 1, availablePlayers: 2 },
    NOTTINGHAM: { cnName: "诺丁汉", count: 2, availablePlayers: 4 },
    GLOUCESTER: { cnName: "格罗斯特", count: 2, availablePlayers: 2 },
    OXFORD: { cnName: "牛津", count: 2, availablePlayers: 2 }
  };
  const result = [];
  const byCity = new Map();
  for (const route of layout.routes ?? []) {
    for (const city of [route.src, route.dst]) {
      if (!marketCities[city]) continue;
      const point = route.path?.[route.src === city ? 0 : route.path.length - 1];
      if (point && !byCity.has(city)) byCity.set(city, point);
    }
  }
  for (const [city, meta] of Object.entries(marketCities)) {
    const base = byCity.get(city);
    if (!base) continue;
    for (let index = 0; index < meta.count; index++) {
      result.push({
        city,
        cnName: meta.cnName,
        index,
        availablePlayers: meta.availablePlayers,
        rect: {
          x: Math.max(0.01, Math.min(0.95, base.x + (index - (meta.count - 1) / 2) * 0.028 - 0.012)),
          y: Math.max(0.01, Math.min(0.95, base.y - 0.012)),
          w: 0.024,
          h: 0.024
        }
      });
    }
  }
  return result;
}

function marketSlotTooltip(slot) {
  return [
    `城市名称：${slot.cityName}`,
    `市场信息：${slot.message}`,
    `贸易商啤酒：${slot.beerStatus}`
  ].join("\n");
}

function cityCurrentRoadPoint(city, industries = []) {
  return (industries ?? [])
    .filter((tile) => brassMapKey(tile.city) === brassMapKey(city) && tile.flipped)
    .reduce((sum, tile) => sum + Number(tile.roadPoints ?? tile.roadPoint ?? 0), 0);
}

function shortPlayerName(name = "") {
  const text = String(name ?? "").trim();
  if (text.length <= 5) return text || "-";
  return text.slice(0, 5);
}

function brassIndustryShortName(industryType, fallback = "") {
  const names = {
    cotton_mill: "棉",
    manufacturer: "加",
    brewery: "酒",
    pottery: "陶",
    iron_works: "铁",
    coal_mine: "煤"
  };
  return names[industryType] ?? brassIndustryName(industryType, fallback).slice(0, 1);
}

function camelRaceCardText(card) {
  if (!card) return "";
  return `${CAMEL_COLORS[card.color] ?? card.color}${card.steps}`;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function camelBetCardText(card) {
  if (!card) return "";
  const color = CAMEL_COLORS[card.color] ?? card.color;
  const payouts = Array.isArray(card.payouts) ? card.payouts.join("/") : "";
  if (card.type === "leg_winner") return `${color}${TEXT.legWinner}(${payouts})`;
  if (card.type === "leg_middle") return `${color}${TEXT.legMiddle}(${payouts})`;
  if (card.type === "final_winner") return `${color}${TEXT.finalWinner}(${payouts})`;
  if (card.type === "final_loser") return `${color}${TEXT.finalLoser}(${payouts})`;
  return `${color}(${payouts})`;
}

function camelTokenStatusText(status) {
  if (status === "disabled") return "未启用";
  if (status === "available") return "未使用";
  if (status === "used") return "已使用";
  return status ?? "未启用";
}

function camelTokenAt(tokens, position) {
  const names = [];
  if (tokens.shortcut?.status === "placed" && Number(tokens.shortcut.position) === position) {
    names.push("Shortcut");
  }
  if (tokens.fennec?.status === "placed" && Number(tokens.fennec.position) === position) {
    names.push("Fennec");
  }
  return names.join(", ");
}

function wordText(word) {
  if (!word) return "";
  const chinese = String(word.Chinese ?? "").trim();
  const english = String(word.English ?? "").trim();
  const note = String(word.note ?? "").trim();
  const base = english ? `${chinese}/${english}` : chinese;
  return note ? `${base}[${note}]` : base;
}

function ruleText(rule) {
  if (!rule) return "";
  const description = String(rule.description ?? rule.describe ?? "").trim();
  const note = String(rule.note ?? "").trim();
  return note ? `${description}[${note}]` : description;
}

function areaShort(areaKey) {
  return THINGS_AREAS.find((area) => area.key === areaKey)?.short ?? areaKey;
}

function isSeatOccupied(seat) {
  return seat?.isOccupied === true && displayName(seat?.user) !== "";
}

function displayName(user) {
  return String(user?.username ?? user?.name ?? "").trim();
}

function seatListOf(room) {
  return room?.roomState?.seats ?? room?.seats ?? [];
}

function hasCurrentUserProposed(room) {
  const currentUserId = room?.currentUser?.id;
  const proposals = room?.gameState?.endProposals ?? [];
  return proposals.some((proposal) => String(proposal.userId) === String(currentUserId));
}

function isFinishedGameState(state) {
  const phase = String(state?.phase ?? "").toLowerCase();
  return phase === "finished" || phase === "game_over";
}

function relationLines(fiveMoves) {
  const lines = [
    "\u77f3\u5934 > \u526a\u5200",
    "\u526a\u5200 > \u5e03",
    "\u5e03 > \u77f3\u5934"
  ];
  if (!fiveMoves) {
    return lines;
  }
  return [
    "\u77f3\u5934 > \u526a\u5200\u3001\u8725\u8734",
    "\u526a\u5200 > \u5e03\u3001\u8725\u8734",
    "\u5e03 > \u77f3\u5934\u3001\u53f2\u6ce2\u514b",
    "\u8725\u8734 > \u53f2\u6ce2\u514b\u3001\u5e03",
    "\u53f2\u6ce2\u514b > \u77f3\u5934\u3001\u526a\u5200"
  ];
}

function visibleRpsGroups(groups, currentUserId, mode) {
  if (mode !== "bracket") {
    return groups;
  }
  const currentGroup = groups.find((group) =>
    (group.players ?? []).some((player) => String(player.userId) === String(currentUserId))
    && group.status !== "finished"
  );
  return currentGroup ? [currentGroup] : groups;
}

function rpsPlayerLabel(player) {
  if (player.bot) {
    return "\u673a\u5668\u4eba";
  }
  if (typeof player.seatIndex === "number") {
    return `${player.seatIndex + 1}\u53f7\u73a9\u5bb6`;
  }
  return String(player.username ?? "");
}

function moveDisplayFor(player, submissions, lastRoundSubmissions, group) {
  const key = String(player.userId);
  const currentMove = submissions[key];
  if (currentMove) {
    if (group.status === "finished") {
      return MOVE_LABELS[currentMove] ?? currentMove;
    }
    return player.bot ? TEXT.waitingChoice : TEXT.completedChoice;
  }
  const lastMove = lastRoundSubmissions[key];
  if (lastMove) {
    return MOVE_LABELS[lastMove] ?? lastMove;
  }
  return TEXT.waitingChoice;
}

function rpsStatusText({ currentSeat, active, candidate, submitted }) {
  if (!currentSeat) {
    return TEXT.spectator;
  }
  if (candidate || !active) {
    return TEXT.candidate;
  }
  if (submitted) {
    return TEXT.rpsSubmitted;
  }
  return TEXT.selectMove;
}
