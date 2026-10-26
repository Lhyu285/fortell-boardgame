package com.fortell.boardgame.game_modules.brass;

import java.util.List;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassFlowModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.CANAL_ERA_REMOVED_LEVEL_ONE_INDUSTRIES;

final class BrassTypedEra {
    private BrassTypedEra() {}
    static boolean removed(ScoringIndustry tile) {
        return "canal".equals(tile.era()) && tile.level() == 1 && CANAL_ERA_REMOVED_LEVEL_ONE_INDUSTRIES.contains(tile.type());
    }
    static boolean canMaintain(boolean ending, boolean hasDebt) { return ending && !hasDebt; }
    static MaintenanceStep maintenance(boolean allowed, boolean canal) {
        if (!allowed) throw new ApiException(HttpStatus.BAD_REQUEST, "当前不能执行时代维护");
        return canal ? MaintenanceStep.START_RAIL : MaintenanceStep.FINISH_GAME;
    }
    static RailStart railStart(List<Long> turnOrder, List<Long> players) {
        return new RailStart((turnOrder.isEmpty() ? players : turnOrder).getFirst(), 1, 0);
    }
    static int merchantBeer(boolean providesBeer, boolean marketOpen, boolean blank) {
        return providesBeer && marketOpen && !blank ? 1 : 0;
    }
}
