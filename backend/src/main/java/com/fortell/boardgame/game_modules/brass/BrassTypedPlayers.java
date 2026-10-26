package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.PlayerStats;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Asset arithmetic only. Era income/debt scheduling remains in the existing flow service. */
final class BrassTypedPlayers {
    private BrassTypedPlayers() {}
    enum Field { MONEY, INCOME_LEVEL, INCOME, SPENT, VP }
    static PlayerStats initial() {
        return new PlayerStats(STARTING_MONEY, STARTING_INCOME_LEVEL, incomeForLevel(STARTING_INCOME_LEVEL), 0, 0, 0, null);
    }
    static PlayerStats pay(PlayerStats player, int amount) {
        if (player.money() < amount) throw new ApiException(HttpStatus.BAD_REQUEST, "金钱不足");
        return new PlayerStats(player.money() - amount, player.incomeLevel(), player.income(), player.victoryPoints(),
                player.spentThisRound() + amount, player.estimatedEraEndScore(), player.lastIncome());
    }
    static PlayerStats gain(PlayerStats player, int amount) { return player.withMoney(player.money() + amount); }
    static PlayerStats income(PlayerStats player, int reward) {
        int level = Math.min(MAX_INCOME_LEVEL, player.incomeLevel() + Math.max(0, reward));
        return new PlayerStats(player.money(), level, incomeForLevel(level), player.victoryPoints(), player.spentThisRound(), player.estimatedEraEndScore(), player.lastIncome());
    }
    static PlayerStats victoryPoints(PlayerStats player, int amount) {
        return new PlayerStats(player.money(), player.incomeLevel(), player.income(), player.victoryPoints() + amount,
                player.spentThisRound(), player.estimatedEraEndScore(), player.lastIncome());
    }
    static PlayerStats loan(PlayerStats player) {
        if (player.incomeLevel() < 3) throw new ApiException(HttpStatus.BAD_REQUEST, "收入等级为0、1或2时不能进行贷款");
        Integer level = INCOME_LEVEL_AFTER_LOAN.get(player.incomeLevel());
        if (level == null) throw new ApiException(HttpStatus.BAD_REQUEST, "当前收入等级不能进行贷款");
        return new PlayerStats(player.money() + 30, level, incomeForLevel(level), player.victoryPoints(),
                player.spentThisRound(), player.estimatedEraEndScore(), player.lastIncome());
    }
}
