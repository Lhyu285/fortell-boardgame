import React from "react";
import { brassRouteKey } from "../../helpers.jsx";
import { BrassResourceSelectionPanel } from "../BrassResourceSelectionPanel.jsx";
import { TEXT } from "../../../../features/rooms/roomText.js";

function BrassNetworkControls({
  networkRoute, setNetworkRoute, availableRoutesForSelect, routeLabel,
  state, secondNetworkRoute, setSecondNetworkRoute, compatibleSecondRouteKeys,
  selectedRoute, selectedSecondRoute, resourceRequirements, resourceRequirementIndex,
  resourceSelections, setResourceRequirementIndex, cycleResourceSource, clearResourceRequirement,
  networkBlockReason, selectedCardId, resourceSelectionPayload, confirmed
}) {
  return (<div className="brass-build-controls">
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
            {resourceRequirements.length > 0 ? (
              <BrassResourceSelectionPanel
                requirements={resourceRequirements}
                activeIndex={resourceRequirementIndex}
                selected={resourceSelections}
                onSelectRequirement={setResourceRequirementIndex}
                onCycleSource={cycleResourceSource}
                onClearRequirement={clearResourceRequirement}
              />
            ) : null}
            <button
              className="primary-button"
              disabled={Boolean(networkBlockReason)}
              onClick={() => {
                const networkPayload = {
                  cardId: selectedCardId,
                  from: selectedRoute?.from,
                  to: selectedRoute?.to,
                  ...resourceSelectionPayload
                };
                if (state.era === "rail" && selectedSecondRoute) {
                  networkPayload.routes = [
                    { from: selectedRoute?.from, to: selectedRoute?.to },
                    { from: selectedSecondRoute.from, to: selectedSecondRoute.to }
                  ];
                  networkPayload.linkIds = [brassRouteKey(selectedRoute), brassRouteKey(selectedSecondRoute)];
                } else {
                  networkPayload.linkIds = selectedRoute ? [brassRouteKey(selectedRoute)] : [];
                }
                confirmed("是否修建所选路线？", "network", networkPayload);
              }}
            >
              {TEXT.brassNetwork}
            </button>
            {networkBlockReason ? <div className="form-error">{networkBlockReason}</div> : null}
          </div>);
}

export { BrassNetworkControls };
