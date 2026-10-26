package com.fortell.boardgame.game_modules.brass;

import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

/** Compatibility adapter for callers still supplying wire board/route values. */
final class BrassResourceRequirements {
    private BrassResourceRequirements() {}
    static List<ResourceRequirement> networkRequirements(String type, List<Map<String, Object>> links) {
        return networkRequirements(type, links, List.of());
    }
    static List<ResourceRequirement> networkRequirements(String type, List<Map<String, Object>> links,
                                                         List<Map<String, Object>> stages) {
        return BrassTypedNetwork.requirements(type, BrassActionAdapter.routeSpecs(links), stages.stream().map(BrassResourceAdapter::board).toList());
    }
}
