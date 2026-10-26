import React from "react";
import { BrassResourceSelectionPanel } from "../BrassResourceSelectionPanel.jsx";
import { TEXT } from "../../../../features/rooms/roomText.js";

function BrassDevelopControls({
  industryNames, developIndustries, isCurrentPlayer, toggleDevelopIndustry,
  setDevelopIndustries, resourceRequirements, resourceRequirementIndex, resourceSelections,
  setResourceRequirementIndex, cycleResourceSource, clearResourceRequirement, developIronSources,
  developBlockReason, confirmed, selectedCardId, resourceSelectionPayload
}) {
  return (<div className="brass-develop-panel">
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
                ...resourceSelectionPayload
              })}
            >
              {TEXT.brassDevelop}
            </button>
          </div>);
}

export { BrassDevelopControls };
