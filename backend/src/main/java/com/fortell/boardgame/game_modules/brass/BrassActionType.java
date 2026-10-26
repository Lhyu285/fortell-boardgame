package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;

enum BrassActionType {
    BUILD("build", "建造", true), DEVELOP("develop", "研发", true),
    SELL("sell", "出售", true), NETWORK("network", "运输网", true),
    LOAN("loan", "贷款", true), SCOUT("scout", "侦查", true), SKIP("skip", "跳过行动", true),
    END_SELL("end_sell", "结束出售行动", true), END_TURN("end_turn", "结束回合", false),
    RESTART_TURN("restart_turn", "重新开始本回合", false),
    RESOLVE_INCOME_DEBT("resolve_income_debt", "处理收入欠款", false),
    MAINTAIN_ERA("maintain_era", "时代维护", false);

    final String key;
    final String label;
    final boolean businessAction;
    BrassActionType(String key, String label, boolean businessAction) {
        this.key = key; this.label = label; this.businessAction = businessAction;
    }
    static BrassActionType parse(String key) {
        return Arrays.stream(values()).filter(type -> type.key.equals(key)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "未知的行动"));
    }
}
