package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Industry;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import java.util.List;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

final class BrassTypedSale {
    private BrassTypedSale() {}
    record Session(boolean inProgress, int count) {
        Session sold() { return new Session(true, inProgress ? count + 1 : 1); }
    }
    static void validateTarget(Industry tile, long actor) {
        if (tile == null) throw new ApiException(HttpStatus.BAD_REQUEST, "产业不合法");
        if (tile.ownerId() != actor) throw new ApiException(HttpStatus.BAD_REQUEST, "找不到要出售的产业");
        if (tile.flipped()) throw new ApiException(HttpStatus.BAD_REQUEST, "该产业不能出售");
        if (!"sell".equals(tile.flipType())) throw new ApiException(HttpStatus.BAD_REQUEST, "该产业已翻面");
    }
    static List<ResourceRequirement> requirements(Industry tile, Merchant merchant) {
        int beer = BrassTypedIndustries.saleBeer(tile);
        return beer > 0 ? List.of(new ResourceRequirement("beer", beer, List.of(), merchant == null ? "" : merchant.city(), tile.type())) : List.of();
    }
    static void validateMerchants(List<ResourceSelection> selections, String merchant) {
        if (selections.stream().anyMatch(s -> s.sourceType() == SourceType.MERCHANT && !merchant.equals(s.sourceId())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "贸易商啤酒必须来自本次选择的售卖市场");
    }
    static void end(Session session) {
        if (!session.inProgress() || session.count() < 1)
            throw new ApiException(HttpStatus.BAD_REQUEST, "尚未完成任何产业售卖，不能结束出售行动");
    }
}
