package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.RouteDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Local action input/definition boundary; no whole-save normalization. */
final class BrassActionAdapter {
    private BrassActionAdapter() {}
    static BrassTypedBuild.Definition buildDefinition(Map<String, Object> tile, String type, String era) {
        return new BrassTypedBuild.Definition(parseInteger(tile.get("cost"), INDUSTRY_COSTS.get(type)),
                parseInteger(tile.get("coalCost"), 0), parseInteger(tile.get("ironCost"), 0),
                "deplete".equals(tile.get("flipType")) ? resourceAmountForEra(tile, era) : 0);
    }
    static List<BrassTypedNetwork.RouteSpec> routeSpecs(List<Map<String, Object>> routes) {
        return routes.stream().map(r -> new BrassTypedNetwork.RouteSpec(Objects.toString(r.get("from"), ""), Objects.toString(r.get("to"), ""))).toList();
    }
    static List<RouteDefinition> routeRules() {
        return routes().stream().map(r -> new RouteDefinition(Objects.toString(r.get("from"), ""), Objects.toString(r.get("to"), ""),
                Boolean.TRUE.equals(r.get("canal")), Boolean.TRUE.equals(r.get("rail")), Boolean.TRUE.equals(r.get("hasBrewery")))).toList();
    }
}
