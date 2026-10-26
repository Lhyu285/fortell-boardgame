package com.fortell.boardgame.game_modules.brass;

import static com.fortell.boardgame.game_modules.brass.BrassFlowModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.incomeForLevel;

final class BrassTypedIncome {
    private BrassTypedIncome() {}
    static Income settle(int money, int incomeLevel) {
        int income = incomeForLevel(incomeLevel);
        if (income >= 0 || money + income >= 0) return new Income(money + income, income, 0);
        return new Income(0, income, -(money + income));
    }
}
