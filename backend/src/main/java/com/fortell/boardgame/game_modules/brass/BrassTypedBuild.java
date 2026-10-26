package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Card;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

/** Build validation and resource/cost planning; compatible JSON tile creation stays at the boundary. */
final class BrassTypedBuild {
    private BrassTypedBuild() {}
    record Definition(int cost, int coalCost, int ironCost, int stock) {}
    record Plan(int cost, int coalCost, int ironCost, int stock, List<ResourceRequirement> requirements) {
        Plan { requirements = List.copyOf(requirements); }
    }
    static void validateSite(String city, String type, Map<String, List<List<String>>> slots, List<String> types) {
        if (!slots.containsKey(city)) throw new ApiException(HttpStatus.BAD_REQUEST, "城市不合法");
        if (!types.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "产业不合法");
        if (slots.get(city).stream().noneMatch(slot -> slot.contains(type)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "该城市不能建造该产业");
    }
    static void validateCard(Card card, String city, String type, boolean inNetwork) {
        if (card == null) throw new ApiException(HttpStatus.BAD_REQUEST, "找不到要使用的手牌");
        if (!BrassTypedCards.canBuild(card, city, type, () -> inNetwork))
            throw new ApiException(HttpStatus.BAD_REQUEST, "这张牌不能用于本次建造");
        if (List.of("Personal_Brewery", "Rural_Brewery").contains(city) && !BrassTypedCards.canBuildAnonymousBrewery(card))
            throw new ApiException(HttpStatus.BAD_REQUEST, "无名酿酒厂只能通过酿酒厂产业牌或万能产业牌建造");
    }
    static void validateCityLimit(boolean canal, boolean alreadyBuilt, boolean coveringOwn) {
        if (canal && alreadyBuilt && !coveringOwn)
            throw new ApiException(HttpStatus.BAD_REQUEST, "运河时代同一玩家不能在同一城市建造多个产业");
    }
    static Plan plan(Definition tile, String city) {
        var needs = new java.util.ArrayList<ResourceRequirement>();
        if (tile.coalCost() > 0) needs.add(new ResourceRequirement("coal", tile.coalCost(), List.of(city), null, ""));
        if (tile.ironCost() > 0) needs.add(new ResourceRequirement("iron", tile.ironCost(), List.of(), null, ""));
        return new Plan(tile.cost(), tile.coalCost(), tile.ironCost(), tile.stock(), needs);
    }
}
