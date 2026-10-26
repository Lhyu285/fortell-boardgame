import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import SiteHeader from "../components/SiteHeader";
import { apiGet } from "../lib/api";

export default function LobbyPage() {
  const navigate = useNavigate();
  const [games, setGames] = useState([]);

  useEffect(() => {
    apiGet("/api/games")
      .then((items) => setGames((items ?? []).filter((game) => game.key !== "gobang")))
      .catch(() => setGames([]));
  }, []);

  return (
    <div className="lobby-page">
      <SiteHeader />

      <main className="lobby-content">
        <section className="lobby-banner" aria-labelledby="lobby-title">
          <div>
            <span className="lobby-banner-kicker">FORTELL BOARDGAME</span>
            <h1 id="lobby-title">轻松开局</h1>
            <p>挑一款游戏，和朋友开始一局轻松的桌游。</p>
          </div>
          <span className="lobby-banner-orbit" aria-hidden="true">✦</span>
        </section>

        <section className="lobby-games-section" aria-labelledby="games-title">
          <div className="lobby-section-heading">
            <div>
              <span className="section-kicker">GAME LIBRARY</span>
              <h2 id="games-title">选择游戏</h2>
            </div>
            <span className="game-count">{games.length} 款游戏</span>
          </div>
          <div className="lobby-game-grid">
            {games.map((game) => (
              <button key={game.key} className="game-entry lobby-game-card lobby-game-card-simple" onClick={() => navigate(game.path)}>
                <span className="game-card-icon" aria-hidden="true">✦</span>
                <span className="game-card-copy">
                  <strong>{game.name}</strong>
                </span>
                <span className="game-card-arrow" aria-hidden="true">↗</span>
              </button>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
