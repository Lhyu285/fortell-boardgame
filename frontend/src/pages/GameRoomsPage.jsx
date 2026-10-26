import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import PageShell from "../components/PageShell";
import { apiGet, apiPost } from "../lib/api";

const STATUS_TEXT = {
  WAITING: "准备中",
  IN_PROGRESS: "游戏中"
};

export default function GameRoomsPage() {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [leavingRoomId, setLeavingRoomId] = useState("");

  const loadRooms = useCallback(() => {
    return apiGet("/api/rooms/mine")
      .then((items) => {
        setRooms(items ?? []);
        setError("");
      })
      .catch((exception) => setError(exception.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadRooms();
    const timer = window.setInterval(loadRooms, 10000);
    return () => window.clearInterval(timer);
  }, [loadRooms]);

  async function leaveAsSpectator(room) {
    setLeavingRoomId(room.roomId);
    try {
      await apiPost(`/api/rooms/${room.gameType}/${room.roomId}/leave`, {});
      await loadRooms();
    } catch (exception) {
      setError(exception.message);
    } finally {
      setLeavingRoomId("");
    }
  }

  return (
    <PageShell title="游戏房间" subtitle="返回你正在准备或进行中的房间">
      <section className="catalog-page active-rooms-page">
        <div className="active-rooms-toolbar">
          <span>{rooms.length} 个活跃房间</span>
          <button className="ghost-button" type="button" onClick={loadRooms}>刷新</button>
        </div>
        {error ? <div className="form-error">{error}</div> : null}
        {loading ? <div className="panel page-loading">正在加载房间列表...</div> : null}
        {!loading && rooms.length === 0 ? (
          <div className="panel empty-state">
            <strong>还没有活跃房间</strong>
            <span>从游戏列表创建房间，或加入朋友分享给你的房间。</span>
            <button className="primary-button" type="button" onClick={() => navigate("/games")}>浏览游戏列表</button>
          </div>
        ) : null}
        <div className="room-list-grid">
          {rooms.map((room) => {
            const canLeaveFromList = room.status === "WAITING" || !room.currentUserSeated;
            const leaveLabel = room.status === "WAITING" ? "离开房间" : "退出观战";
            return (
              <article className="panel room-list-card" key={`${room.gameType}-${room.roomId}`}>
                <div className="room-list-card-heading">
                  <span className="room-list-icon" aria-hidden="true">▤</span>
                  <div>
                    <h2>{room.gameName}</h2>
                    <span>房间 #{room.roomId}</span>
                  </div>
                  <span className={`room-status-badge room-status-${room.status.toLowerCase()}`}>
                    {STATUS_TEXT[room.status] ?? room.status}
                  </span>
                </div>
                <div className="room-list-details">
                  <span>房主：{room.owner?.username ?? "未知"}</span>
                  <span>座位：{room.occupiedSeatCount} / {room.seatCount}</span>
                  <span>{room.currentUserOwner ? "你是房主" : room.currentUserSeated ? "你已入座" : "你在观战"}</span>
                </div>
                <div className="room-list-actions">
                  <button className="primary-button" type="button" onClick={() => navigate(`/${room.gameType}/${room.roomId}`)}>
                    返回房间
                  </button>
                  {canLeaveFromList ? (
                    <button
                      className="ghost-button"
                      type="button"
                      disabled={leavingRoomId === room.roomId}
                      onClick={() => leaveAsSpectator(room)}
                    >
                      {leavingRoomId === room.roomId ? "退出中..." : leaveLabel}
                    </button>
                  ) : null}
                </div>
              </article>
            );
          })}
        </div>
      </section>
    </PageShell>
  );
}
