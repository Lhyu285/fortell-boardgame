package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.consumeAction;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.ensureTurnAcceptsAction;

/** Scout Action rules; all working state is passed explicitly. */
final class BrassScoutAction {
    private BrassScoutAction() {}

    static Map<String, Object> scoutAction(Map<String, Object> state, UserSummary actor, BrassActionCommand command) {
        BrassActionInput.Scout input = (BrassActionInput.Scout) command.input();
        ensureTurnAcceptsAction(state);
        var cards = BrassPersonalAdapter.cards(state, actor.id());
        cards.apply(BrassTypedCards.scout(cards.value(), actor.id(), input.cardIds()));

        appendNotice(state, "玩家" + actor.username() + " 进行了侦查");
        consumeAction(state, actor);
        return state;
    }
}
