import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import PageShell from "../components/PageShell";
import { apiGet } from "../lib/api";

export default function GameListPage() {
  const navigate = useNavigate();
  const [games, setGames] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    apiGet("/api/games")
      .then((items) => setGames(items ?? []))
      .catch((exception) => setError(exception.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <PageShell title="游戏列表" subtitle="查看每款游戏的适合人数和玩法简介">
      <section className="catalog-page">
        {loading ? <div className="panel page-loading">正在加载游戏列表...</div> : null}
        {error ? <div className="form-error">{error}</div> : null}
        {!loading && games.length === 0 ? <div className="panel page-loading">暂无可用游戏。</div> : null}
        <div className="game-list-grid">
          {games.map((game) => (
            <article className="panel game-list-card" key={game.key}>
              <div className="game-list-card-heading">
                <span className="game-card-icon" aria-hidden="true">✦</span>
                <div>
                  <h2>{game.name}</h2>
                  <span className="game-list-english-name">{game.englishName}</span>
                </div>
              </div>
              <p>{game.summary}</p>
              <dl className="game-list-meta">
                <div>
                  <dt>推荐人数</dt>
                  <dd>{game.recommendedPlayers ?? "—"} 人</dd>
                </div>
                <div>
                  <dt>支持人数</dt>
                  <dd>{game.minPlayers}~{game.maxPlayers} 人</dd>
                </div>
              </dl>
              <button className="primary-button" type="button" onClick={() => navigate(game.path)}>
                创建或加入房间
              </button>
            </article>
          ))}
        </div>
      </section>
    </PageShell>
  );
}
