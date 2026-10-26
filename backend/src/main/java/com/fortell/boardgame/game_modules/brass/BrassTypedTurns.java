package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Era;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassFlowModel.*;

final class BrassTypedTurns {
    private BrassTypedTurns() {}
    static int roundsPerEra(int playerCount) { return 12 - playerCount; }
    static int actions(boolean canal, int round) { return canal && round == 1 ? 1 : 2; }
    static int actions(Round round) { return actions(round.era() == Era.CANAL, round.number()); }
    static boolean finalGameRound(Round round) { return round.era() == Era.RAIL && round.number() >= roundsPerEra(round.playerCount()); }
    static Counters consume(Counters current) {
        int remaining = Math.max(0, current.remaining() - 1);
        return new Counters(current.allowed(), remaining, current.taken() + 1, remaining == 0);
    }
    static TurnStart start(Round round, TurnKind kind) {
        int actions = kind == TurnKind.ACTIONS ? actions(round) : 0;
        return new TurnStart(kind, new Counters(actions, actions, 0, false));
    }
    static void ensureEnd(boolean awaitingEnd) {
        if (!awaitingEnd) throw new ApiException(HttpStatus.BAD_REQUEST, "当前还不能结束回合");
    }
    static void ensureAccepts(boolean awaitingEnd) {
        if (awaitingEnd) throw new ApiException(HttpStatus.BAD_REQUEST, "当前回合行动已完成，请先结束回合");
    }
    static int nextIndex(int index, int orderSize) { return (index + 1) % orderSize; }
    static List<Integer> spendingOrder(List<Long> previous, Map<Long, Integer> spending) {
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < previous.size(); i++) indices.add(i);
        // List.sort is stable: equal spending preserves the previous order, not seat order.
        indices.sort(Comparator.comparingInt(i -> spending.getOrDefault(previous.get(i), 0)));
        return List.copyOf(indices);
    }
    static RoundCompletion complete(Round round) {
        boolean ending = round.number() >= roundsPerEra(round.playerCount());
        return new RoundCompletion(ending, ending ? round.number() : round.number() + 1);
    }
}
