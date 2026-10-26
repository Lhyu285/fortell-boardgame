import React from "react";
import { objectCounts } from "../helpers.jsx";

function BrassMarket({ market }) {
  const coal = objectCounts(market.coal ?? []);
  const iron = objectCounts(market.iron ?? []);
  return (
    <div className="brass-market-board">
      <div className="brass-market-heading">
        <div>
          <strong>资源市场</strong>
          <small>价格从左到右逐步上升</small>
        </div>
        <span aria-hidden="true">◈</span>
      </div>
      <BrassMarketTrack title="煤市场" resource="coal" prices={[1, 2, 3, 4, 5, 6, 7]} counts={coal} fallback="£8" />
      <BrassMarketTrack title="铁市场" resource="iron" prices={[1, 2, 3, 4, 5]} counts={iron} fallback="£6" />
    </div>
  );
}

function BrassMarketTrack({ title, resource, prices, counts, fallback }) {
  return (
    <div className={`brass-market-track brass-market-${resource}`}>
      <div className="brass-market-track-heading">
        <span className="brass-market-resource-icon" aria-hidden="true">{resource === "coal" ? "◆" : "✦"}</span>
        <strong>{title}</strong>
      </div>
      <div className="brass-market-slots">
        {prices.flatMap((price) => [0, 1].map((slot) => {
          const available = slot >= 2 - (counts[String(price)] ?? 0);
          return (
            <span className={`brass-market-slot ${available ? "available" : "empty"}`} key={`${title}-${price}-${slot}`}>
              <i aria-hidden="true">◆</i>
              <strong>£{price}</strong>
            </span>
          );
        }))}
        <span className="brass-market-slot distant">
          <i aria-hidden="true">◆</i>
          <strong>{fallback}</strong>
        </span>
      </div>
    </div>
  );
}

export { BrassMarket, BrassMarketTrack };
