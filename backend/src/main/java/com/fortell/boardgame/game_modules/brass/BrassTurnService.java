package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.drawHandToLimit;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.findTile;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.objectList;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.ownedMapIndustries;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.describeOperation;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Compatibility flow coordinator; calculations and decisions live in typed rule components. */
final class BrassTurnService {
    private BrassTurnService() {}

    static Map<String, Object> restartTurn(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> snapshot = mapOf(state.get("turnStartSnapshot"));
        BrassTypedRestart.validate(!snapshot.isEmpty(), parseInteger(mapOf(state.get("turn")).get("actionsTaken"), 0));
        BrassTurnSnapshotAdapter.restore(state, snapshot);
        appendNotice(state, "玩家 " + actor.username() + " 重新开始本回合");
        describeOperation(state, actor, "restart_turn", "重新开始本回合");
        return state;
    }

    static Map<String, Object> endTurnAction(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> turn = mapOf(state.get("turn"));
        BrassTypedTurns.ensureEnd(Boolean.TRUE.equals(turn.get("awaitingEndTurn")));
        drawHandToLimit(state, actor.id(), STARTING_HAND_SIZE);
        appendNotice(state, "\u73a9\u5bb6 " + actor.username() + " \u7ed3\u675f\u4e86\u56de\u5408");
        advanceTurn(state);
        return state;
    }

    static Map<String, Object> resolveIncomeDebtAction(Map<String, Object> state, UserSummary actor,
                                                        BrassActionInput.Debt input) {
        Map<String, Object> debt = mapOf(state.get("incomeDebt"));
        var debtValue = BrassFlowAdapter.debt(debt);
        BrassTypedDebt.ensureActor(debtValue, actor.id());
        String tileId = input.tileId();
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> tile = findTile(industries, tileId);
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(actor.id())));
        var result = BrassTypedDebt.liquidate(debtValue, actor.id(), BrassFlowAdapter.debtAsset(tile), parseInteger(stats.get("money"), 0));
        industries.removeIf(item -> tileId.equals(Objects.toString(item.get("id"), "")));
        board.put("industries", industries);
        state.put("board", board);
        stats.put("money", result.money());
        statsByPlayer.put(String.valueOf(actor.id()), stats);
        state.put("playerStats", statsByPlayer);
        debt.put("amount", result.remainingDebt());
        state.put("incomeDebt", debt);
        appendNotice(state, "玩家" + actor.username() + " 移除了 "
                + cityCnName(Objects.toString(tile.get("city"), "")) + " 的 "
                + parseInteger(tile.get("level"), 0) + "级"
                + industryNameCn(Objects.toString(tile.get("industryType"), ""))
                + "，获得" + result.proceeds() + "英镑用于支付收入欠款");
        continueIncomeDebtResolution(state);
        if (!Boolean.TRUE.equals(state.get("eraEnding")) && mapOf(state.get("incomeDebt")).isEmpty()) {
            long nextPlayerId = asLong(state.get("currentPlayerId"));
            beginTurn(state, "轮到 " + playerName(listOf(state.get("players")), nextPlayerId) + " 行动");
        }
        return state;
    }

    static void consumeAction(Map<String, Object> state, UserSummary actor) {
        BrassFlowAdapter.writeCounters(state, BrassFlowAdapter.turnState(state).consumeAction());
    }

    static void advanceTurn(Map<String, Object> state) {
        List<Object> order = objectList(state.get("turnOrder"));
        int turnIndex = parseInteger(state.get("turnIndex"), 0);
        int nextIndex = BrassTypedTurns.nextIndex(turnIndex, order.size());
        if (nextIndex == 0) {
            finishRound(state);
            if (Boolean.TRUE.equals(state.get("eraEnding")) || !mapOf(state.get("incomeDebt")).isEmpty()) {
                return;
            }
            order = objectList(state.get("turnOrder"));
            nextIndex = 0;
        }
        long nextPlayerId = asLong(order.get(nextIndex));
        state.put("turnIndex", nextIndex);
        state.put("currentPlayerId", nextPlayerId);
        beginTurn(state, "\u8f6e\u5230 " + playerName(listOf(state.get("players")), nextPlayerId) + " \u884c\u52a8");
    }

    static void enterEraEnding(Map<String, Object> state) {
        BrassFlowAdapter.newTurn(state, BrassTypedTurns.start(BrassFlowAdapter.round(state, parseInteger(state.get("round"), 0)), BrassFlowModel.TurnKind.ERA_END));
        state.put("turnStartSnapshot", null);
        state.put("eraEnding", true);
        appendNotice(state, eraName(Objects.toString(state.get("era"), "")) + "结束，进入时代维护");
    }

    static void finishRound(Map<String, Object> state) {
        int finishedRound = parseInteger(state.get("round"), 1);
        reorderTurnOrderBySpending(state);
        appendNotice(state, "第" + finishedRound + "轮结束，下轮行动顺位为：" + turnOrderNotice(state));
        state.put("pendingFinishedRound", finishedRound);
        if (!isFinalGameRound(state, finishedRound)) {
            collectIncome(state);
        }
        continueIncomeDebtResolution(state);
    }

    static void beginTurn(Map<String, Object> state, String notice) {
        Map<String, Object> previousTurn = mapOf(state.get("turn"));
        Object previousStartedRound = previousTurn.get("startedAtRound");
        if (!Objects.equals(previousStartedRound, state.get("round"))) {
            appendNotice(state, "第" + parseInteger(state.get("round"), 1) + "轮开始");
        }
        BrassFlowAdapter.newTurn(state, BrassTypedTurns.start(BrassFlowAdapter.round(state, parseInteger(state.get("round"), 0)), BrassFlowModel.TurnKind.ACTIONS));
        state.put("turnStartSnapshot", snapshotOf(state));
        appendNotice(state, notice);
    }

    static String turnOrderNotice(Map<String, Object> state) {
        List<Map<String, Object>> players = listOf(state.get("players"));
        return objectList(state.get("turnOrder")).stream()
                .map(id -> "玩家" + playerName(players, asLong(id)))
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    static int actionsPerTurn(Map<String, Object> state) {
        return BrassTypedTurns.actions(BrassFlowAdapter.round(state, parseInteger(state.get("round"), 0)));
    }

    static Map<String, Object> snapshotOf(Map<String, Object> state) {
        return BrassTurnSnapshotAdapter.capture(state);
    }

    static boolean canMaintainEra(Map<String, Object> state) {
        return BrassTypedEra.canMaintain(Boolean.TRUE.equals(state.get("eraEnding")), !mapOf(state.get("incomeDebt")).isEmpty());
    }

    static void resetRoundSpending(Map<String, Object> state) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        for (String playerId : new ArrayList<>(statsByPlayer.keySet())) {
            Map<String, Object> stats = mapOf(statsByPlayer.get(playerId));
            stats.put("spentThisRound", 0);
            statsByPlayer.put(playerId, stats);
        }
        state.put("playerStats", statsByPlayer);
    }

    static void collectIncome(Map<String, Object> state) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        List<Map<String, Object>> debts = new ArrayList<>();
        for (String playerId : new ArrayList<>(statsByPlayer.keySet())) {
            Map<String, Object> stats = mapOf(statsByPlayer.get(playerId));
            var result = BrassTypedIncome.settle(parseInteger(stats.get("money"), 0), parseInteger(stats.get("incomeLevel"), 0));
            BrassFlowAdapter.income(stats, result);
            if (result.debt() > 0) debts.add(BrassFlowAdapter.debtWire(Long.parseLong(playerId), result.debt()));
            statsByPlayer.put(playerId, stats);
        }
        state.put("playerStats", statsByPlayer);
        state.put("incomeDebtQueue", debts);
    }

    static void continueIncomeDebtResolution(Map<String, Object> state) {
        Map<String, Object> currentDebt = mapOf(state.get("incomeDebt"));
        if (!currentDebt.isEmpty()) {
            var value = BrassFlowAdapter.debt(currentDebt);
            var step = BrassTypedDebt.next(value, !ownedMapIndustries(state, value.playerId()).isEmpty(), true);
            if (step == BrassFlowModel.DebtStep.LOSE_VP) {
                deductVictoryPointsForDebt(state, value.playerId(), value.amount());
                state.put("incomeDebt", new LinkedHashMap<>());
            } else if (step == BrassFlowModel.DebtStep.LIQUIDATE) {
                prepareIncomeDebtTurn(state, currentDebt);
                return;
            }
        }
        List<Map<String, Object>> queue = listOf(state.get("incomeDebtQueue"));
        while (!queue.isEmpty()) {
            Map<String, Object> debt = queue.remove(0);
            state.put("incomeDebtQueue", queue);
            var value = BrassFlowAdapter.debt(debt);
            if (BrassTypedDebt.next(value, !ownedMapIndustries(state, value.playerId()).isEmpty(), false) == BrassFlowModel.DebtStep.LOSE_VP) {
                deductVictoryPointsForDebt(state, value.playerId(), value.amount());
                continue;
            }
            state.put("incomeDebt", debt);
            prepareIncomeDebtTurn(state, debt);
            return;
        }
        state.put("incomeDebt", new LinkedHashMap<>());
        completeRoundAfterIncome(state);
    }

    static void prepareIncomeDebtTurn(Map<String, Object> state, Map<String, Object> debt) {
        var value = BrassFlowAdapter.debt(debt);
        state.put("currentPlayerId", value.playerId());
        BrassFlowAdapter.newTurn(state, BrassTypedTurns.start(BrassFlowAdapter.round(state, parseInteger(state.get("round"), 0)), BrassFlowModel.TurnKind.INCOME_DEBT));
        state.put("turnStartSnapshot", null);
        appendNotice(state, "玩家" + playerName(listOf(state.get("players")), value.playerId())
                + " 需要支付" + value.amount() + "英镑收入欠款");
    }

    static void deductVictoryPointsForDebt(Map<String, Object> state, long playerId, int amount) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
        stats.put("victoryPoints", BrassTypedDebt.deduct(parseInteger(stats.get("victoryPoints"), 0), amount));
        statsByPlayer.put(String.valueOf(playerId), stats);
        state.put("playerStats", statsByPlayer);
        appendNotice(state, "玩家" + playerName(listOf(state.get("players")), playerId)
                + " 无产业可移除，未支付的" + amount + "英镑转为扣除" + amount + " VP");
    }

    static void completeRoundAfterIncome(Map<String, Object> state) {
        int finishedRound = parseInteger(state.get("pendingFinishedRound"), parseInteger(state.get("round"), 1));
        state.remove("pendingFinishedRound");
        resetRoundSpending(state);
        var completion = BrassTypedTurns.complete(BrassFlowAdapter.round(state, finishedRound));
        if (completion.eraEnding()) {
            enterEraEnding(state);
            return;
        }
        state.put("round", completion.nextRound());
        state.put("turnIndex", 0);
        long nextPlayerId = asLong(objectList(state.get("turnOrder")).getFirst());
        state.put("currentPlayerId", nextPlayerId);
    }

    static int roundsPerEra(Map<String, Object> state) {
        return BrassTypedTurns.roundsPerEra(BrassFlowAdapter.round(state, 0).playerCount());
    }

    static boolean isFinalGameRound(Map<String, Object> state, int round) {
        return BrassTypedTurns.finalGameRound(BrassFlowAdapter.round(state, round));
    }

    static void reorderTurnOrderBySpending(Map<String, Object> state) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        List<Object> previous = objectList(state.get("turnOrder"));
        Map<Long, Integer> spending = new LinkedHashMap<>();
        for (Object id : previous) spending.put(asLong(id), parseInteger(mapOf(statsByPlayer.get(String.valueOf(asLong(id)))).get("spentThisRound"), 0));
        var indices = BrassTypedTurns.spendingOrder(previous.stream().map(BrassJsonCodec::asLong).toList(), spending);
        state.put("turnOrder", new ArrayList<>(indices.stream().map(previous::get).toList()));
    }

    static void ensurePlaying(Map<String, Object> state) {
        if (!"playing".equals(state.get("phase"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6e38\u620f\u5c1a\u672a\u5f00\u59cb");
        }
    }

    static void ensureCurrentPlayer(Map<String, Object> state, UserSummary actor) {
        if (asLong(state.get("currentPlayerId")) != actor.id()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8fd8\u6ca1\u6709\u8f6e\u5230\u4f60");
        }
    }

    static void ensureTurnAcceptsAction(Map<String, Object> state) {
        BrassTypedTurns.ensureAccepts(Boolean.TRUE.equals(mapOf(state.get("turn")).get("awaitingEndTurn")));
    }
}
