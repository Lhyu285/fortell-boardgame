package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import java.util.Map;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.currentHand;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.discardCard;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.findCard;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.describeOperation;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.consumeAction;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.ensureTurnAcceptsAction;

/** Loan Action rules; all working state is passed explicitly. */
final class BrassLoanAction {
    private BrassLoanAction() {}

    static Map<String, Object> loanAction(Map<String, Object> state, UserSummary actor, BrassActionCommand command) {
        BrassActionInput.Card input = (BrassActionInput.Card) command.input();
        ensureTurnAcceptsAction(state);
        String cardId = input.cardId();
        if (cardId.isBlank() || findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u6709\u6548\u624b\u724c");
        }
        var next = BrassTypedPlayers.loan(BrassPersonalAdapter.stats(state, actor.id(), STARTING_INCOME_LEVEL));
        discardCard(state, actor.id(), cardId);
        BrassPersonalAdapter.writeStats(state, actor.id(), next, java.util.Set.of(BrassTypedPlayers.Field.MONEY,
                BrassTypedPlayers.Field.INCOME_LEVEL, BrassTypedPlayers.Field.INCOME));
        describeOperation(state, actor, "loan", "\u83b7\u5f9730\u82f1\u9551\uff0c\u964d\u4f4e3\u7ea7\u6536\u5165");
        appendNotice(state, "玩家" + actor.username() + " 进行了贷款");
        consumeAction(state, actor);
        return state;
    }
}
