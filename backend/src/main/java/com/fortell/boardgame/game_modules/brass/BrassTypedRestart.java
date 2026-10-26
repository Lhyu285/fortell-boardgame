package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;

final class BrassTypedRestart {
    private BrassTypedRestart() {}
    static void validate(boolean hasSnapshot, int actionsTaken) {
        if (!hasSnapshot) throw new ApiException(HttpStatus.BAD_REQUEST, "当前没有可恢复的回合快照");
        if (actionsTaken <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "尚未执行行动，不需要重新开始");
    }
}
