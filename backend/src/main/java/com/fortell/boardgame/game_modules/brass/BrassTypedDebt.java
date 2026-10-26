package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Debt;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassFlowModel.*;

final class BrassTypedDebt {
    private BrassTypedDebt() {}
    static void ensureActor(Debt debt, long actor) {
        if (debt.playerId() != actor) throw new ApiException(HttpStatus.BAD_REQUEST, "当前不需要该玩家处理收入欠款");
    }
    static Liquidation liquidate(Debt debt, long actor, DebtAsset asset, int money) {
        ensureActor(debt, actor);
        if (asset == null || asset.ownerId() != actor)
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择地图上自己的产业板块");
        int proceeds = Math.max(0, asset.cost() / 2);
        int available = money + proceeds;
        int paid = Math.min(debt.amount(), available);
        return new Liquidation(proceeds, available - paid, debt.amount() - paid);
    }
    static DebtStep next(Debt debt, boolean ownsIndustries, boolean current) {
        if (current && debt.amount() <= 0) return DebtStep.COMPLETE;
        return ownsIndustries ? DebtStep.LIQUIDATE : DebtStep.LOSE_VP;
    }
    static int deduct(int victoryPoints, int amount) { return victoryPoints - amount; }
}
