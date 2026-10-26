package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Era;
import com.fortell.boardgame.models.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.industryNameCn;

/** Personal supply order is authoritative: never skip a blocked lowest tile. */
final class BrassTypedSupply {
    private BrassTypedSupply() {}
    /** Rule-specific view: deliberately excludes prices/resources rather than inventing defaults. */
    record Position(String id, int level, int period, boolean canDevelop) {}
    static boolean buildable(Position tile, Era era) {
        return tile.period() == 1 ? era == Era.CANAL : tile.period() != 2 || era == Era.RAIL;
    }
    static Position next(List<Position> tiles, String type, Era era) {
        if (tiles.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(type) + "没有可建造的板块");
        if (!buildable(tiles.getFirst(), era)) throw new ApiException(HttpStatus.BAD_REQUEST, "该板块不能在当前时代建造");
        return tiles.getFirst();
    }
    static Position developable(List<Position> tiles, String type, boolean failIfBlocked) {
        if (tiles.isEmpty()) return null;
        Position first = tiles.getFirst();
        if (first.canDevelop()) return first;
        if (!failIfBlocked) return null;
        if ("pottery".equals(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "你不能通过研发行动移除" + first.level() + "级陶瓷厂");
        throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(type) + "当前最低等级板块不能研发");
    }
    static List<Position> remove(List<Position> tiles, String id) {
        List<Position> remaining = tiles.stream().filter(tile -> !id.equals(tile.id())).toList();
        if (tiles.size() == remaining.size()) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "未能移除玩家板块");
        return remaining;
    }
    static int increment(int count) { return count + 1; }
}
