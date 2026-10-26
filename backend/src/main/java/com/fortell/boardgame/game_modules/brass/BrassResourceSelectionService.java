package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

/** Wire contract and effect coordination; allocation rules operate on immutable resource values. */
final class BrassResourceSelectionService {
    private BrassResourceSelectionService() {}
    static boolean hasExplicitResourceSelections(Map<String, Object> payload) { return payload.containsKey("resourceSelections"); }
    static boolean usesSimulationAutoPlanning(BrassExecutionMode mode, Map<String, Object> payload) {
        return mode == BrassExecutionMode.SIMULATE && !hasExplicitResourceSelections(payload);
    }
    static void requireResourceSelectionContract(Map<String, Object> state, Map<String, Object> payload,
                                                 List<ResourceRequirement> requirements, BrassExecutionMode mode) {
        if (usesSimulationAutoPlanning(mode, payload)) return;
        for (String legacy : List.of("coalSourceTileId", "coalSourceTileIds", "ironSourceTileId", "ironSourceTileIds", "beerSourceTileId", "beerSourceTileIds"))
            if (payload.containsKey(legacy)) throw new ApiException(HttpStatus.BAD_REQUEST, "旧版资源来源字段已停用，请使用resourceSelections");
        for (var route : listOf(payload.get("routes")))
            if (route.containsKey("coalSourceTileId") || route.containsKey("coalSourceTileIds"))
                throw new ApiException(HttpStatus.BAD_REQUEST, "旧版资源来源字段已停用，请使用resourceSelections");
        long required = requirements.stream().mapToLong(ResourceRequirement::amount).sum();
        if (required > 0 && !hasExplicitResourceSelections(payload))
            throw new ApiException(HttpStatus.BAD_REQUEST, "本次行动需要显式提交resourceSelections资源来源");
        if (required == 0 && hasExplicitResourceSelections(payload) && !parseResourceSelections(payload).isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "本次行动不需要资源选择");
    }
    static List<ResourceSelection> parseResourceSelections(Map<String, Object> payload) { return BrassResourceAdapter.selections(payload); }

    static List<String> consumeExplicitResources(Map<String, Object> state, long actorId, List<ResourceRequirement> requirements,
                                                 Map<String, Object> payload, String freeDevelopIndustryType) {
        var allocations = BrassTypedResources.validate(BrassResourceAdapter.context(state), actorId, requirements,
                parseResourceSelections(payload), BrassResourceAdapter.coalMarkets());
        return applyResourceAllocations(state, actorId, allocations, freeDevelopIndustryType);
    }

    static List<String> applyResourceAllocations(Map<String, Object> state, long actorId,
                                                  List<ResourceAllocation> allocations, String freeDevelopIndustryType) {
        var notices = new ArrayList<String>();
        for (var allocation : allocations) {
            var selection = allocation.selection();
            switch (selection.sourceType()) {
                case MARKET -> notices.add(BrassResourceService.consumeMarketResource(state, actorId, selection.type().key, selection.amount()));
                case MERCHANT -> notices.add(BrassResourceService.consumeMerchantBeer(state, actorId, selection.sourceId(),
                        allocation.requirement().saleCity(), allocation.requirement().saleIndustryType(), selection.amount(), freeDevelopIndustryType));
                case INDUSTRY -> notices.addAll(BrassResourceAdapter.consumeIndustry(state, allocation));
            }
        }
        return notices;
    }
}
