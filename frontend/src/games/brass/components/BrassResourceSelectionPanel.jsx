import React from "react";

function BrassResourceSelectionPanel({ requirements, activeIndex, selected, onSelectRequirement, onCycleSource, onClearRequirement }) {
  return (
    <ResourceSelectionPanel title="资源来源确认">
      <p className="muted-note">点击地图上的产业格，或点击下方来源，循环选择使用数量。</p>
      {requirements.map((requirement, index) => {
        const selectedItems = (selected ?? []).filter((item) => item.requirementIndex === index);
        const selectedTotal = selectedItems.reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
        const selectedFree = selectedItems
          .filter((item) => item.sourceType === "industry")
          .reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
        const priorFreeUsage = new Map();
        for (const item of selected ?? []) {
          if (item.requirementIndex >= index || item.type !== requirement.type || item.sourceType !== "industry") {
            continue;
          }
          priorFreeUsage.set(String(item.sourceId), (priorFreeUsage.get(String(item.sourceId)) ?? 0) + Number(item.amount ?? 0));
        }
        const remainingFreeAvailable = (requirement.sources ?? [])
          .filter((source) => source.sourceType === "industry")
          .reduce((sum, source) => sum + Math.max(0,
            Number(source.available ?? 0) - (priorFreeUsage.get(String(source.id)) ?? 0)), 0);
        const freeRequired = Math.min(Number(requirement.amount ?? 0), remainingFreeAvailable);
        const marketLocked = ["coal", "iron"].includes(requirement.type) && selectedFree < freeRequired;
        return (
          <div className="brass-resource-requirement" key={`${requirement.type}-${index}`}>
            <div className="brass-resource-requirement-head">
              <button
                type="button"
                className={`small-button ${activeIndex === index ? "secondary-button" : "ghost-button"}`}
                onClick={() => onSelectRequirement(index)}
              >
                {requirement.label}：{selectedTotal}/{requirement.amount}
              </button>
              <button
                type="button"
                className="small-button ghost-button"
                disabled={selectedItems.length === 0}
                onClick={() => onClearRequirement(index)}
              >
                清空本项
              </button>
            </div>
            <div className="brass-resource-source-buttons">
              {(requirement.sources ?? []).map((source) => {
                const selectedAmount = selectedItems.find((item) => String(item.sourceId) === String(source.id)
                  && item.sourceType === source.sourceType)?.amount ?? 0;
                const occupiedElsewhere = (selected ?? [])
                  .filter((item) => item.requirementIndex !== index
                    && item.sourceType === source.sourceType
                    && String(item.sourceId) === String(source.id)
                    && item.type === requirement.type)
                  .reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
                const disabled = index !== activeIndex
                  || (source.sourceType === "market" && marketLocked)
                  || Number(source.available ?? 0) <= 0
                  || (occupiedElsewhere >= Number(source.available ?? 0) && selectedAmount === 0);
                return (
                  <button
                    type="button"
                    key={`${index}-${source.sourceType}-${source.id}`}
                    className={`small-button ${selectedAmount > 0 ? "secondary-button" : "ghost-button"}`}
                    disabled={disabled}
                    onClick={() => onCycleSource(requirement, source)}
                  >
                    {source.label} {selectedAmount}/{source.available}
                    {occupiedElsewhere > 0 ? `（其他需求已选${occupiedElsewhere}）` : ""}
                  </button>
                );
              })}
            </div>
            {["coal", "iron"].includes(requirement.type) && marketLocked ? (
              <small className="muted-note">仍有免费{requirement.type === "coal" ? "煤" : "铁"}未选择，市场资源暂不可用。</small>
            ) : null}
          </div>
        );
      })}
    </ResourceSelectionPanel>
  );
}

function ResourceSelectionPanel({ title, children }) {
  return (
    <fieldset className="brass-resource-panel">
      <legend>{title}</legend>
      {children}
    </fieldset>
  );
}

export { BrassResourceSelectionPanel, ResourceSelectionPanel };
