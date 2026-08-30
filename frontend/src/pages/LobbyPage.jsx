import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ConfirmModal from "../components/ConfirmModal";
import PageShell from "../components/PageShell";
import { useAppContext } from "../hooks/useAppContext";
import { apiGet, apiPost } from "../lib/api";

export default function LobbyPage() {
  const navigate = useNavigate();
  const { currentUser, setCurrentUser } = useAppContext();
  const [games, setGames] = useState([]);
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);
  const [logoutPending, setLogoutPending] = useState(false);
  const [logoutError, setLogoutError] = useState("");

  useEffect(() => {
    apiGet("/api/games")
      .then((items) => setGames((items ?? []).filter((game) => game.key !== "gobang")))
      .catch(() => setGames([]));
  }, []);

  async function logout() {
    setLogoutPending(true);
    setLogoutError("");
    try {
      await apiPost("/api/auth/logout", {});
      setCurrentUser(null);
      navigate("/login", { replace: true });
    } catch (exception) {
      setLogoutError(exception.message);
      setLogoutPending(false);
    }
  }

  return (
    <PageShell
      title="游戏大厅"
      subtitle={`当前用户：${currentUser?.username ?? ""}`}
      actions={<button className="ghost-button" onClick={() => setLogoutConfirmOpen(true)}>退出登录</button>}
    >
      <div className="stack-list">
        {games.map((game) => (
          <button key={game.key} className="game-entry" onClick={() => navigate(game.path)}>
            <strong>{game.name}</strong>
            <span>{game.summary}</span>
          </button>
        ))}
      </div>
      <ConfirmModal
        open={logoutConfirmOpen}
        message="是否确认退出登录？"
        busy={logoutPending}
        error={logoutError}
        onCancel={() => {
          setLogoutConfirmOpen(false);
          setLogoutError("");
        }}
        onConfirm={logout}
      />
    </PageShell>
  );
}
