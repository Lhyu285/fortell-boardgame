package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Debt;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Era;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassFlowModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Scoped legacy flow decoding and deliberate field writes. Never normalize the entire save. */
final class BrassFlowAdapter {
    private BrassFlowAdapter() {}
    static BrassGameState gameState(Map<String, Object> state) {
        BrassTurnState turn = turnState(state);
        var phase = "finished".equals(state.get("phase")) ? BrassGameState.Phase.FINISHED
                : !"playing".equals(state.get("phase")) ? BrassGameState.Phase.WAITING
                : !viewMap(state.get("incomeDebt")).isEmpty() ? BrassGameState.Phase.INCOME_DEBT
                : Boolean.TRUE.equals(state.get("eraEnding")) ? BrassGameState.Phase.ERA_MAINTENANCE
                : turn.awaitingEndTurn() ? BrassGameState.Phase.AWAITING_END_TURN
                : turn.sellInProgress() ? BrassGameState.Phase.SELLING : BrassGameState.Phase.ACTIONS;
        return new BrassGameState("rail".equals(state.get("era")) ? BrassGameState.Era.RAIL : BrassGameState.Era.CANAL,
                phase, parseInteger(state.get("round"), 0), state.get("players") instanceof List<?> players ? players.size() : 0,
                asLong(state.get("currentPlayerId")), asLong(state.get("version")), turn);
    }
    static BrassTurnState turnState(Map<String, Object> state) {
        var turn = viewMap(state.get("turn")); var counters = counters(turn);
        return new BrassTurnState(counters.allowed(), counters.remaining(), counters.taken(), counters.awaitingEndTurn(),
                Boolean.TRUE.equals(turn.get("sellInProgress")), parseInteger(turn.get("sellCount"), 0), asLong(turn.get("sellActionStartVersion")));
    }
    static void writeCounters(Map<String, Object> state, BrassTurnState next) {
        var turn = mapOf(state.get("turn")); turn.put("actionsRemaining", next.actionsRemaining());
        turn.put("actionsTaken", next.actionsTaken()); turn.put("awaitingEndTurn", next.awaitingEndTurn()); state.put("turn", turn);
    }
    static Round round(Map<String, Object> state, int number) {
        int players = state.get("players") instanceof List<?> list ? list.size() : 0;
        return new Round("rail".equals(state.get("era")) ? Era.RAIL : Era.CANAL, number, players);
    }
    static Counters counters(Map<String, Object> turn) {
        return new Counters(parseInteger(turn.get("actionsAllowed"), 0), parseInteger(turn.get("actionsRemaining"), 0),
                parseInteger(turn.get("actionsTaken"), 0), Boolean.TRUE.equals(turn.get("awaitingEndTurn")));
    }
    static void newTurn(Map<String, Object> state, TurnStart start) {
        var turn = new LinkedHashMap<String, Object>();
        if (start.kind() == TurnKind.INCOME_DEBT) turn.put("incomeDebtResolution", true);
        turn.put("actionsAllowed", start.counters().allowed()); turn.put("actionsRemaining", start.counters().remaining());
        turn.put("actionsTaken", start.counters().taken()); turn.put("awaitingEndTurn", start.counters().awaitingEndTurn());
        if (start.kind() == TurnKind.ERA_END) turn.put("eraEnding", true);
        if (start.kind() != TurnKind.INCOME_DEBT) turn.put("startedAtRound", state.get("round"));
        state.put("turn", turn);
    }
    static Debt debt(Map<String, Object> value) { return new Debt(asLong(value.get("playerId")), parseInteger(value.get("amount"), 0)); }
    static Map<String, Object> debtWire(Debt debt) {
        var value = new LinkedHashMap<String, Object>(); value.put("playerId", debt.playerId()); value.put("amount", debt.amount()); return value;
    }
    static Map<String, Object> debtWire(long playerId, int amount) { return debtWire(new Debt(playerId, amount)); }
    static DebtAsset debtAsset(Map<String, Object> tile) {
        return tile == null || tile.isEmpty() ? null : new DebtAsset(asLong(tile.get("ownerId")), parseInteger(tile.get("cost"), 0));
    }
    static void income(Map<String, Object> stats, Income result) {
        stats.put("money", result.money()); stats.put("lastIncome", result.amount()); stats.put("income", result.amount());
    }
    static ScoringIndustry industry(Map<String, Object> tile) {
        return new ScoringIndustry(asLong(tile.get("ownerId")), nullableText(tile, "city"), text(tile, "industryType"),
                text(tile, "era"), parseInteger(tile.get("level"), 1), Boolean.TRUE.equals(tile.get("flipped")),
                Boolean.TRUE.equals(tile.get("industryVpScored")), parseInteger(tile.get("victoryPoints"), 0), parseInteger(tile.get("roadPoints"), 0));
    }
    static List<ScoringIndustry> industries(List<Map<String, Object>> tiles) { return tiles.stream().map(BrassFlowAdapter::industry).toList(); }
    static List<ScoringLink> links(List<Map<String, Object>> values) {
        return values.stream().map(link -> new ScoringLink(asLong(link.get("ownerId")), text(link, "from"),
                text(link, "to"), text(link, "type"), Boolean.TRUE.equals(link.get("scored")))).toList();
    }
    static List<MarketRoad> markets() {
        return (MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS).stream()
                .map(m -> new MarketRoad(text(m, "city"), parseInteger(m.get("roadPoint"), 0))).toList();
    }
    static List<Integer> playerScores(Map<String, Object> state, List<Map<String, Object>> players) {
        var stats = viewMap(state.get("playerStats"));
        return players.stream().map(player -> parseInteger(viewMap(stats.get(String.valueOf(player.get("userId")))).get("victoryPoints"), 0)).toList();
    }
    static List<Long> order(Map<String, Object> state) { return objectList(state.get("turnOrder")).stream().map(BrassJsonCodec::asLong).toList(); }
    static String text(Map<String, Object> value, String key) { return Objects.toString(value.get(key), ""); }
    private static String nullableText(Map<String, Object> value, String key) { return Objects.toString(value.get(key), null); }
}
