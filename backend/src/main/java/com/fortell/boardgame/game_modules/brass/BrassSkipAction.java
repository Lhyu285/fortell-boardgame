package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.discardActionCard;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.consumeAction;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.ensureTurnAcceptsAction;

/** Skip Action rules; all working state is passed explicitly. */
final class BrassSkipAction {
    private BrassSkipAction() {}

    static Map<String, Object> skipAction(Map<String, Object> state, UserSummary actor, BrassActionCommand command) {
        BrassActionInput.Card input = (BrassActionInput.Card) command.input();
        ensureTurnAcceptsAction(state);
        discardActionCard(state, actor.id(), input.cardId());
        appendNotice(state, "玩家" + actor.username() + " 跳过了一个行动");
        consumeAction(state, actor);
        return state;
    }
}
