import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import PageShell from "../components/PageShell";
import RuleModal from "../components/RuleModal";
import { apiPost } from "../lib/api";
import { createGameDefaults, gameName } from "../games/registry.js";

const ROOM_ID_PATTERN = /^[0-9]{3,8}$/;
const PASSWORD_PATTERN = /^[A-Za-z0-9]{4,8}$/;

export default function GameLandingPage() {
  const navigate = useNavigate();
  const { gameType } = useParams();
  const [roomId, setRoomId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [rulesOpen, setRulesOpen] = useState(false);

  const roomIdValid = roomId === "" || ROOM_ID_PATTERN.test(roomId);
  const passwordValid = password === "" || PASSWORD_PATTERN.test(password);
  const canCreate = roomIdValid && passwordValid && !submitting;
  const canEnter = ROOM_ID_PATTERN.test(roomId) && passwordValid && !submitting;
  const enterText = useMemo(() => `进入房间${roomId}`, [roomId]);
  const validationError =
    roomId !== "" && !roomIdValid
      ? "房间号不合法"
      : password !== "" && !passwordValid
        ? "房间密码不合法"
        : "";

  function updateRoomId(value) {
    setRoomId(value.slice(0, 8));
    setError("");
  }

  function updatePassword(value) {
    setPassword(value.slice(0, 8));
    setError("");
  }

  function validateCreate() {
    if (!roomIdValid) {
      setError("房间号不合法");
      return false;
    }
    if (!passwordValid) {
      setError("房间密码不合法");
      return false;
    }
    return true;
  }

  async function createRoom() {
    if (!validateCreate()) {
      return;
    }
    setSubmitting(true);
    try {
      const defaults = createGameDefaults(gameType);
      const data = await apiPost("/api/rooms", {
        gameType,
        roomId,
        password,
        seatCount: defaults.seatCount,
        config: defaults.config
      });
      navigate(`/${gameType}/${data.roomId}`);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function enterRoom() {
    if (!ROOM_ID_PATTERN.test(roomId)) {
      setError("房间号不合法");
      return;
    }
    if (!passwordValid) {
      setError("房间密码不合法");
      return;
    }
    setSubmitting(true);
    try {
      const data = await apiPost("/api/rooms/join", {
        gameType,
        roomId,
        password
      });
      navigate(`/${gameType}/${data.roomId}`);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageShell
      title={labelFor(gameType)}
      subtitle="创建房间或加入已有房间"
      actions={
        <button className="ghost-button" onClick={() => navigate("/lobby")}>
          返回游戏列表
        </button>
      }
    >
      <section className="panel game-entry-panel">
        {error || validationError ? <div className="form-error">{error || validationError}</div> : null}

        <button className="ghost-button" onClick={() => setRulesOpen(true)}>
          查看规则
        </button>

        <button className="primary-button" onClick={createRoom} disabled={!canCreate}>
          创建房间
        </button>

        <input
          inputMode="numeric"
          value={roomId}
          onChange={(event) => updateRoomId(event.target.value)}
          placeholder="输入房间号以加入特定房间"
        />

        <input
          value={password}
          onChange={(event) => updatePassword(event.target.value)}
          placeholder="输入房间密码以加入特定房间"
        />

        <button className="secondary-button" onClick={enterRoom} disabled={!canEnter}>
          {enterText}
        </button>
      </section>
      <RuleModal
        gameType={gameType}
        title={`${labelFor(gameType)} 规则`}
        open={rulesOpen}
        onClose={() => setRulesOpen(false)}
      />
    </PageShell>
  );
}

function labelFor(gameType) {
  return gameName(gameType, true);
}
