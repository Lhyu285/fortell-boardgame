import React from "react";
import { BrassResourceSelectionPanel } from "../BrassResourceSelectionPanel.jsx";
import { buildConfirmText } from "../../helpers.jsx";
import { TEXT } from "../../../../features/rooms/roomText.js";

function BrassBuildControls({
  buildCity, setBuildCity, setBuildSlotIndex, selectableBuildCities,
  cityLabel, activeBuildIndustry, setBuildIndustry, buildIndustryOptions,
  industryNames, resourceRequirements, resourceRequirementIndex, resourceSelections,
  setResourceRequirementIndex, cycleResourceSource, clearResourceRequirement, buildCompatibleCardIds,
  selectedBuildOption, buildBlockReason, confirmed, selectedCardId,
  resourceSelectionPayload
}) {
  return (<div className="brass-build-controls">
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
                ...resourceSelectionPayload
              })}
            >
              {TEXT.brassBuild}
            </button>
          </div>);
}

export { BrassBuildControls };
