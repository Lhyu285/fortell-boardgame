package com.fortell.boardgame.game_modules.brass.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable business state. Not yet connected to the live execution path.
 * Display caches, UI logs and unknown save fields belong to the codec envelope, not this model.
 */
public record BrassSnapshot(long version, Phase phase, Era era, int round, int turnIndex, Long currentPlayerId,
        List<Player> players, List<Long> turnOrder, List<Long> initialTurnOrder,
        Map<String, PlayerStats> playerStats, Map<String, List<Card>> hands,
        Map<String, Map<String, Integer>> developments,
        Map<String, Map<String, List<IndustryDefinition>>> playerBoards,
        List<Card> deck, List<Card> scoutPool, List<Card> discardPile, int hiddenDiscardCount,
        CardHints cardHints, Board board, Market market, Turn turn, BrassSnapshot turnStartSnapshot,
        boolean eraEnding, Debt incomeDebt, List<Debt> incomeDebtQueue, Integer pendingFinishedRound,
        List<EraScore> eraScores, List<Winner> winners) {

    public BrassSnapshot {
        phase = phase == null ? Phase.WAITING : phase;
        era = era == null ? Era.CANAL : era;
        players = list(players); turnOrder = list(turnOrder); initialTurnOrder = list(initialTurnOrder);
        playerStats = map(playerStats); hands = lists(hands);
        Map<String, Map<String, Integer>> counters = new LinkedHashMap<>();
        if (developments != null) developments.forEach((id, values) -> counters.put(id, map(values)));
        developments = map(counters);
        Map<String, Map<String, List<IndustryDefinition>>> boards = new LinkedHashMap<>();
        if (playerBoards != null) playerBoards.forEach((id, values) -> boards.put(id, lists(values)));
        playerBoards = map(boards);
        deck = list(deck); scoutPool = list(scoutPool); discardPile = list(discardPile);
        incomeDebtQueue = list(incomeDebtQueue); eraScores = list(eraScores); winners = list(winners);
    }

    public enum Era { @JsonProperty("canal") CANAL, @JsonProperty("rail") RAIL }
    public enum Phase { @JsonProperty("waiting") WAITING, @JsonProperty("playing") PLAYING, @JsonProperty("finished") FINISHED }

    public record Player(long userId, String username, int seatIndex, String color) {}
    public record PlayerStats(int money, int incomeLevel, int income, int victoryPoints, int spentThisRound,
                              int estimatedEraEndScore, Integer lastIncome) {
        public PlayerStats withMoney(int value) {
            return new PlayerStats(value, incomeLevel, income, victoryPoints, spentThisRound, estimatedEraEndScore, lastIncome);
        }
    }
    public record Turn(int actionsAllowed, int actionsRemaining, int actionsTaken, boolean awaitingEndTurn,
                       int startedAtRound, boolean sellInProgress, int sellCount, long sellActionStartVersion,
                       boolean eraEnding, boolean incomeDebtResolution) {}
    public record Debt(long playerId, int amount) {}
    public record Card(String id, String type, String key, String name, String sourceType, String city,
                       String displayName, List<String> industryTypes, boolean wild) {
        public Card { industryTypes = list(industryTypes); }
    }
    public record CardHint(String key, String name, int total, int remaining) {}
    public record CardHints(List<CardHint> cards) { public CardHints { cards = list(cards); } }

    /** A tile on a player's supply board, distinct from a built industry instance. */
    public record IndustryDefinition(String id, String industryType, String industryName, int level, Era era,
            int period, Integer cost, int coalCost, int ironCost, int saleBeerCost, List<Integer> resourceAmounts,
            int coal, int iron, int beer, int incomeReward, int victoryPoints, int roadPoints,
            Boolean canDevelop, String flipType) {
        public IndustryDefinition {
            resourceAmounts = list(resourceAmounts);
            canDevelop = canDevelop == null ? Boolean.TRUE : canDevelop;
        }
    }
    public record Industry(String id, String sourceTileId, long ownerId, String ownerName, String city,
            int slotIndex, String industryType, int level, Era era, boolean flipped, int cost,
            int coal, int iron, int beer, int coalCost, int ironCost, int saleBeerCost, int roadPoints,
            String flipType, int incomeReward, int victoryPoints, boolean industryVpScored) {
        public Industry withCoal(int amount) {
            return new Industry(id, sourceTileId, ownerId, ownerName, city, slotIndex, industryType, level, era,
                    flipped, cost, amount, iron, beer, coalCost, ironCost, saleBeerCost, roadPoints,
                    flipType, incomeReward, victoryPoints, industryVpScored);
        }
    }
    public record Link(String id, long ownerId, String ownerName, String from, String to, Era type,
                       String color, boolean scored, int score) {}
    public record RouteDefinition(String from, String to, boolean canal, boolean rail, boolean hasBrewery) {}
    public record Board(List<Industry> industries, List<Link> links, List<RouteDefinition> availableRoutes,
                        List<String> buildableCities, Map<String, List<List<String>>> citySlots,
                        Map<String, Integer> merchantBeer, boolean eraScored) {
        public Board {
            industries = list(industries); links = list(links); availableRoutes = list(availableRoutes);
            buildableCities = list(buildableCities); merchantBeer = map(merchantBeer);
            Map<String, List<List<String>>> slots = new LinkedHashMap<>();
            if (citySlots != null) citySlots.forEach((city, values) -> slots.put(city,
                    list(values).stream().map(BrassSnapshot::list).toList()));
            citySlots = map(slots);
        }
        public Board withIndustries(List<Industry> value) {
            return new Board(value, links, availableRoutes, buildableCities, citySlots, merchantBeer, eraScored);
        }
    }
    public record Merchant(String id, String city, int slotIndex, String reward, boolean marketOpen,
            String merchantTileId, List<String> acceptedIndustryTypes, boolean wild, boolean blank,
            boolean providesBeer, int beer, boolean used) {
        public Merchant { acceptedIndustryTypes = list(acceptedIndustryTypes); }
    }
    public record Market(List<Integer> coal, List<Integer> iron, List<Merchant> beerMerchants) {
        public Market { coal = list(coal); iron = list(iron); beerMerchants = list(beerMerchants); }
    }
    public record EraScore(Era era, long ownerId, String ownerName, String type, String summary, int points) {}
    public record Winner(long userId, String username, int victoryPoints) {}

    public BrassSnapshot withPlayerStats(Map<String, PlayerStats> value) {
        return new BrassSnapshot(version, phase, era, round, turnIndex, currentPlayerId, players, turnOrder,
                initialTurnOrder, value, hands, developments, playerBoards, deck, scoutPool, discardPile,
                hiddenDiscardCount, cardHints, board, market, turn, turnStartSnapshot, eraEnding, incomeDebt,
                incomeDebtQueue, pendingFinishedRound, eraScores, winners);
    }
    public BrassSnapshot withBoard(Board value) {
        return new BrassSnapshot(version, phase, era, round, turnIndex, currentPlayerId, players, turnOrder,
                initialTurnOrder, playerStats, hands, developments, playerBoards, deck, scoutPool, discardPile,
                hiddenDiscardCount, cardHints, value, market, turn, turnStartSnapshot, eraEnding, incomeDebt,
                incomeDebtQueue, pendingFinishedRound, eraScores, winners);
    }

    static <T> List<T> list(List<T> value) { return value == null ? List.of() : List.copyOf(value); }
    static <K, V> Map<K, V> map(Map<K, V> value) {
        return Collections.unmodifiableMap(value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value));
    }
    static <K, V> Map<K, List<V>> lists(Map<K, List<V>> value) {
        Map<K, List<V>> result = new LinkedHashMap<>();
        if (value != null) value.forEach((key, items) -> result.put(key, list(items)));
        return map(result);
    }
}
