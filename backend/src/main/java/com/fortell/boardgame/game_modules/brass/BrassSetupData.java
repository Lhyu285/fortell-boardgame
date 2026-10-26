package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Player;
import com.fortell.boardgame.models.RoomSeat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listCopy;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseStaticInteger;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.staticMapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.staticObjectList;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.stringList;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Setup Data rules; all working state is passed explicitly. */
final class BrassSetupData {
    private BrassSetupData() {}

    static List<Map<String, Object>> playersOf(List<RoomSeat> seats) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (RoomSeat seat : seats) {
            if (!seat.occupied()) {
                continue;
            }
            players.add(BrassPersonalAdapter.player(new Player(
                    seat.userId(), seat.username(), seat.seatIndex(), "")));
        }
        return players;
    }

    static void assignRandomPlayerColors(List<Map<String, Object>> players) {
        List<String> colors = new ArrayList<>(PLAYER_COLORS);
        Collections.shuffle(colors);
        for (int index = 0; index < players.size(); index++) {
            players.get(index).put("color", colors.get(index % colors.size()));
        }
    }

    static Map<String, Object> initialPlayerStats(List<Map<String, Object>> players) {
        Map<String, Object> stats = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            stats.put(String.valueOf(player.get("userId")), BrassPersonalAdapter.initialStats(BrassTypedPlayers.initial()));
        }
        return stats;
    }

    static Map<String, Object> initialDevelopments(List<Map<String, Object>> players) {
        Map<String, Object> developments = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            Map<String, Object> playerDevelopments = new LinkedHashMap<>();
            for (String industryType : INDUSTRY_TYPES) {
                playerDevelopments.put(industryType, 0);
            }
            developments.put(String.valueOf(player.get("userId")), playerDevelopments);
        }
        return developments;
    }

    static Map<String, Object> initialPlayerBoards(List<Map<String, Object>> players) {
        Map<String, Object> playerBoards = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            Map<String, Object> board = new LinkedHashMap<>();
            for (String industryType : INDUSTRY_TYPES) {
                board.put(industryType, industryBoardTiles(industryType));
            }
            playerBoards.put(String.valueOf(player.get("userId")), board);
        }
        return playerBoards;
    }

    static List<Map<String, Object>> industryBoardTiles(String industryType) {
        if (!BUILDING_TILES.getOrDefault(industryType, List.of()).isEmpty()) {
            return listCopy(BUILDING_TILES.get(industryType));
        }
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (int level = 1; level <= 4; level++) {
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("id", industryType + "_level_" + level);
            tile.put("industryType", industryType);
            tile.put("industryName", industryNameCn(industryType));
            tile.put("level", level);
            tile.put("era", level <= 1 ? "canal" : "rail");
            tile.put("cost", INDUSTRY_COSTS.get(industryType));
            tile.put("coal", "coal_mine".equals(industryType) ? Math.min(5, level + 1) : 0);
            tile.put("iron", "iron_works".equals(industryType) ? 4 : 0);
            tile.put("beer", "brewery".equals(industryType) ? (level >= 3 ? 2 : 1) : 0);
            tile.put("coalCost", 0);
            tile.put("ironCost", 0);
            tile.put("saleBeerCost", SELLABLE_INDUSTRIES.contains(industryType) ? 1 : 0);
            tile.put("roadPoints", 1);
            tile.put("canDevelop", true);
            tile.put("flipType", SELLABLE_INDUSTRIES.contains(industryType) ? "sell" : "deplete");
            tile.put("incomeReward", INDUSTRY_INCOME_REWARDS.get(industryType));
            tile.put("victoryPoints", INDUSTRY_VP_REWARDS.get(industryType));
            tiles.add(tile);
        }
        return tiles;
    }

    static Map<String, Object> initialMarket(int playerCount) {
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("coal", new ArrayList<>(INITIAL_COAL_MARKET));
        market.put("iron", new ArrayList<>(INITIAL_IRON_MARKET));
        market.put("beerMerchants", initialBeerMerchants(playerCount));
        return market;
    }

    static Map<String, Object> initialBoard() {
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("industries", new ArrayList<>());
        board.put("links", new ArrayList<>());
        board.put("availableRoutes", routes());
        board.put("buildableCities", new ArrayList<>(citySlotOptions().keySet()));
        board.put("citySlots", citySlotOptions());
        board.put("map", mapSummary());
        board.put("merchantBeer", new LinkedHashMap<>());
        board.put("eraScored", false);
        return board;
    }

    static List<Map<String, Object>> initialBeerMerchants(int playerCount) {
        List<Map<String, Object>> merchants = new ArrayList<>();
        List<Map<String, Object>> source = MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS;
        List<Map<String, Object>> merchantTiles = merchantTilesForPlayerCount(playerCount);
        Collections.shuffle(merchantTiles);
        int tileIndex = 0;
        for (Map<String, Object> market : source) {
            int availablePlayers = parseInteger(market.get("availablePlayers"), 0);
            String city = Objects.toString(market.get("city"), "");
            int marketCount = Math.max(1, parseInteger(market.get("marketCount"), 1));
            for (int slotIndex = 0; slotIndex < marketCount; slotIndex++) {
                Map<String, Object> merchant = new LinkedHashMap<>();
                merchant.put("id", "merchant_" + city.toLowerCase().replaceAll("[^a-z0-9]+", "_") + "_" + slotIndex);
                merchant.put("city", city);
                merchant.put("slotIndex", slotIndex);
                merchant.put("reward", Objects.toString(market.get("reward"), ""));
                merchant.put("marketOpen", !(playerCount > 0 && availablePlayers > 0 && playerCount < availablePlayers));
                if (Boolean.TRUE.equals(merchant.get("marketOpen"))) {
                    Map<String, Object> merchantTile = tileIndex < merchantTiles.size() ? merchantTiles.get(tileIndex) : new LinkedHashMap<>();
                    tileIndex++;
                    merchant.put("merchantTileId", Objects.toString(merchantTile.get("id"), ""));
                    merchant.put("acceptedIndustryTypes", stringList(merchantTile.get("industryTypes")));
                    merchant.put("wild", Boolean.TRUE.equals(merchantTile.get("wild")));
                    merchant.put("blank", Boolean.TRUE.equals(merchantTile.get("blank")));
                    merchant.put("providesBeer", !Boolean.FALSE.equals(merchantTile.getOrDefault("providesBeer", true)));
                    merchant.put("beer", Boolean.TRUE.equals(merchant.get("providesBeer")) ? 1 : 0);
                    merchant.put("used", false);
                } else {
                    merchant.put("merchantTileId", "");
                    merchant.put("acceptedIndustryTypes", List.of());
                    merchant.put("wild", false);
                    merchant.put("blank", true);
                    merchant.put("providesBeer", false);
                    merchant.put("beer", 0);
                    merchant.put("used", true);
                }
                merchants.add(merchant);
            }
        }
        return merchants;
    }

    static List<Map<String, Object>> merchantTilesForPlayerCount(int playerCount) {
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (Map<String, Object> rawTile : MERCHANT_TILE_CONFIG) {
            Map<String, Object> info = staticMapOf(rawTile.get("info"));
            List<Integer> availablePlayers = staticObjectList(info.get("available_players")).stream()
                    .map(value -> parseStaticInteger(value, 0))
                    .filter(value -> value > 0)
                    .toList();
            if (!availablePlayers.isEmpty() && !availablePlayers.contains(playerCount)) {
                continue;
            }
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("id", Objects.toString(rawTile.get("id"), ""));
            tile.put("industryTypes", configuredIndustryTypes(info.get("factory")));
            tile.put("wild", Boolean.TRUE.equals(info.get("is_wild")));
            tile.put("blank", Boolean.TRUE.equals(info.get("is_blank")));
            tile.put("providesBeer", !Boolean.FALSE.equals(info.get("provides_beer")));
            tiles.add(tile);
        }
        return tiles;
    }
}
