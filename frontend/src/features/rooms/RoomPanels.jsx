import React from "react";
import { TEXT } from "./roomText.js";
import { isSeatOccupied, displayName } from "./roomView.js";

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

export { SeatBoard, NoticePanel };
