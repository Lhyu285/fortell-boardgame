package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.*;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.RouteDefinition;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.List;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

final class BrassTypedNetwork {
    private BrassTypedNetwork() {}
    record RouteSpec(String from, String to) {}
    static int cost(String type, int count, long existing) {
        if (count < 1 || count > ("rail".equals(type) ? 2 : 1)) throw new ApiException(HttpStatus.BAD_REQUEST, "路线数量不合法");
        if (existing + count > 14) throw new ApiException(HttpStatus.BAD_REQUEST, "每名玩家最多修建14条路");
        return "rail".equals(type) ? (count == 2 ? 15 : 5) : 3;
    }
    static Link step(RouteSpec request, String type, long actor, Network board, List<Site> sites, List<RouteDefinition> rules) {
        var route = rules.stream().filter(r -> BrassTypedConnectivity.sameRoute(request.from(), request.to(), r.from(), r.to())).findFirst().orElse(null);
        if (route == null) throw new ApiException(HttpStatus.BAD_REQUEST, "路线不合法");
        if (!("rail".equals(type) ? route.rail() : route.canal())) throw new ApiException(HttpStatus.BAD_REQUEST, "这条路已经被建造");
        if (BrassTypedConnectivity.occupied(board.links(), request.from(), request.to(), type))
            throw new ApiException(HttpStatus.BAD_REQUEST, "这条路已经被修建");
        var definitions = rules.stream().map(r -> new Route(r.from(), r.to(), r.hasBrewery())).toList();
        if (!BrassTypedConnectivity.touches(BrassTypedConnectivity.playerCities(sites, board.links(), definitions, actor), request.from(), request.to()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "修路必须连接到自己的网络");
        var ends = BrassTypedConnectivity.normalizedEnds(request.from(), request.to());
        return new Link(ends.get(0), ends.get(1), actor, type);
    }
    static List<ResourceRequirement> requirements(String type, List<RouteSpec> routes, List<ResourceBoard> stages) {
        if (!"rail".equals(type)) return List.of();
        var needs = new ArrayList<ResourceRequirement>();
        for (int i = 0; i < routes.size(); i++) {
            var route = routes.get(i);
            needs.add(new ResourceRequirement(ResourceType.COAL, 1, List.of(route.from(), route.to()), null, "", i < stages.size() ? stages.get(i) : null));
        }
        if (routes.size() == 2) {
            var cities = routes.stream().flatMap(r -> java.util.stream.Stream.of(r.from(), r.to())).filter(c -> !c.isBlank()).distinct().toList();
            needs.add(new ResourceRequirement(ResourceType.BEER, 1, cities, null, "", stages.isEmpty() ? null : stages.getLast()));
        }
        return List.copyOf(needs);
    }
}
