package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.objectList;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.addIncome;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Compatibility facade coordinating typed map-industry and personal-supply rules. */
final class BrassIndustryService {
    private BrassIndustryService() {}

    static int remainingIndustryResource(Map<String, Object> tile) {
        return BrassTypedIndustries.remaining(BrassBoardAdapter.industry(tile));
    }

    static Map<String, Object> nextBuildableBoardTile(Map<String, Object> state, long playerId, String industryType) {
        try {
            return nextAvailableBoardTile(state, playerId, industryType);
        } catch (ApiException ignored) {
            return new LinkedHashMap<>();
        }
    }

    static int buildSlotIndexForOption(Map<String, Object> state, List<Map<String, Object>> industries, long playerId,
                                        String city, String industryType) {
        int nextLevel = parseInteger(nextBuildableBoardTile(state, playerId, industryType).get("level"), 0);
        return BrassTypedIndustries.buildSlot(BrassBoardAdapter.industries(industries),
                BrassBoardAdapter.industries(listOf(mapOf(state.get("board")).get("industries"))), citySlotOptions(),
                playerId, city, industryType, nextLevel, resourceMarketEmpty(state, industryType), null);
    }

    static int buildSlotIndexForOption(Map<String, Object> state, List<Map<String, Object>> industries, long playerId,
                                        String city, String industryType, int slotIndex) {
        int nextLevel = parseInteger(nextBuildableBoardTile(state, playerId, industryType).get("level"), 0);
        return BrassTypedIndustries.buildSlot(BrassBoardAdapter.industries(industries),
                BrassBoardAdapter.industries(listOf(mapOf(state.get("board")).get("industries"))), citySlotOptions(),
                playerId, city, industryType, nextLevel, resourceMarketEmpty(state, industryType), slotIndex);
    }

    static int saleBeerRequired(Map<String, Object> tile) {
        return BrassTypedIndustries.saleBeer(BrassBoardAdapter.industry(tile));
    }

    static boolean isDepleteTile(Map<String, Object> tile) {
        return BrassTypedIndustries.deplete(BrassBoardAdapter.industry(tile));
    }

    static Map<String, Object> findTile(List<Map<String, Object>> tiles, String tileId) {
        int index = BrassTypedIndustries.firstIndex(BrassBoardAdapter.industries(tiles), tile -> tileId.equals(tile.id()));
        return index < 0 ? null : tiles.get(index);
    }

    static boolean playerAlreadyBuiltInCity(List<Map<String, Object>> industries, long playerId, String city) {
        return BrassTypedIndustries.firstIndex(BrassBoardAdapter.industries(industries),
                tile -> tile.ownerId() == playerId && city.equals(tile.city())) >= 0;
    }

    static int firstAvailableSlot(List<Map<String, Object>> industries, String city, String industryType) {
        return BrassTypedIndustries.firstAvailable(BrassBoardAdapter.industries(industries), citySlotOptions(), city, industryType);
    }

    static int firstBuildableDedicatedSlot(Map<String, Object> state, List<Map<String, Object>> industries, long playerId,
                                            String city, String industryType) {
        int nextLevel = parseInteger(nextBuildableBoardTile(state, playerId, industryType).get("level"), 0);
        return BrassTypedIndustries.firstDedicated(BrassBoardAdapter.industries(industries),
                BrassBoardAdapter.industries(listOf(mapOf(state.get("board")).get("industries"))), citySlotOptions(),
                playerId, city, industryType, nextLevel, resourceMarketEmpty(state, industryType));
    }

    // Historical helper name/signature retained internally; neither face nor level restricts own covers.
    static Map<String, Object> coverableOwnFlippedTile(List<Map<String, Object>> industries, long actorId, String city,
                                                        String industryType, int newLevel) {
        return industryAt(industries, BrassTypedIndustries.ownCover(BrassBoardAdapter.industries(industries),
                actorId, city, industryType, null, newLevel));
    }

    static Map<String, Object> coverableOwnFlippedTile(List<Map<String, Object>> industries, long actorId, String city,
                                                        String industryType, int slotIndex, int newLevel) {
        return industryAt(industries, BrassTypedIndustries.ownCover(BrassBoardAdapter.industries(industries),
                actorId, city, industryType, slotIndex, newLevel));
    }

    static boolean slotHasDedicatedIndustry(String city, String industryType, int slotIndex) {
        return BrassTypedIndustries.dedicated(citySlotOptions(), city, industryType, slotIndex);
    }

    static boolean slotAvailable(List<Map<String, Object>> industries, String city, String industryType, int slotIndex) {
        return BrassTypedIndustries.available(BrassBoardAdapter.industries(industries), citySlotOptions(), city, industryType, slotIndex);
    }

    static Map<String, Object> coverableOpponentResourceTile(Map<String, Object> state, long actorId, String city,
                                                              String industryType) {
        List<Map<String, Object>> industries = listOf(mapOf(state.get("board")).get("industries"));
        return industryAt(industries, BrassTypedIndustries.opponentCover(BrassBoardAdapter.industries(industries),
                actorId, city, industryType, null, resourceMarketEmpty(state, industryType)));
    }

    static Map<String, Object> coverableOpponentResourceTile(Map<String, Object> state, long actorId, String city,
                                                              String industryType, int slotIndex) {
        List<Map<String, Object>> industries = listOf(mapOf(state.get("board")).get("industries"));
        return industryAt(industries, BrassTypedIndustries.opponentCover(BrassBoardAdapter.industries(industries),
                actorId, city, industryType, slotIndex, resourceMarketEmpty(state, industryType)));
    }

    static boolean resourceMarketEmpty(Map<String, Object> state, String industryType) {
        Map<String, Object> market = mapOf(state.get("market"));
        if ("coal_mine".equals(industryType)) {
            return objectList(market.get("coal")).isEmpty();
        }
        if ("iron_works".equals(industryType)) {
            return objectList(market.get("iron")).isEmpty();
        }
        return false;
    }

    static boolean resourceFactoriesHaveSupply(Map<String, Object> state, String industryType) {
        return BrassTypedIndustries.factoriesHaveSupply(
                BrassBoardAdapter.industries(listOf(mapOf(state.get("board")).get("industries"))), industryType);
    }

    static Map<String, Object> nextAvailableBoardTile(Map<String, Object> state, long playerId, String industryType) {
        var supply = BrassPersonalAdapter.supply(state, playerId, industryType);
        return supply.wire(BrassTypedSupply.next(supply.leading(), industryType, BrassPersonalAdapter.era(Objects.toString(state.get("era"), "canal"))));
    }

    static boolean boardTileBuildableInEra(Map<String, Object> tile, String era) {
        return BrassTypedSupply.buildable(BrassPersonalAdapter.position(tile), BrassPersonalAdapter.era(era));
    }

    static Map<String, Object> lowestDevelopableBoardTile(Map<String, Object> state, long playerId, String industryType) {
        var supply = BrassPersonalAdapter.supply(state, playerId, industryType);
        return supply.wire(BrassTypedSupply.developable(supply.leading(), industryType, false));
    }

    static Map<String, Object> lowestDevelopableBoardTileOrThrow(Map<String, Object> state, long playerId, String industryType) {
        var supply = BrassPersonalAdapter.supply(state, playerId, industryType);
        return supply.wire(BrassTypedSupply.developable(supply.leading(), industryType, true));
    }

    static List<Map<String, Object>> playerBoardTiles(Map<String, Object> state, long playerId, String industryType) {
        Map<String, Object> playerBoards = mapOf(state.get("playerBoards"));
        Map<String, Object> playerBoard = mapOf(playerBoards.get(String.valueOf(playerId)));
        return listOf(playerBoard.get(industryType));
    }

    static void removeBoardTile(Map<String, Object> state, long playerId, String industryType, String tileId) {
        var supply = BrassPersonalAdapter.supply(state, playerId, industryType);
        supply.replace(BrassTypedSupply.remove(supply.tiles(), tileId));
    }

    static void incrementDevelopment(Map<String, Object> state, long playerId, String industryType) {
        BrassPersonalAdapter.writeDevelopment(state, playerId, industryType, BrassTypedSupply.increment(BrassPersonalAdapter.development(state, playerId, industryType)));
    }

    static String freeDevelopSelected(Map<String, Object> state, long actorId, String industryType) {
        if (!INDUSTRY_TYPES.contains(industryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4ea7\u4e1a\u7c7b\u578b\u4e0d\u5408\u6cd5");
        }
        Map<String, Object> tile = lowestDevelopableBoardTile(state, actorId, industryType);
        if (tile.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(industryType) + "\u6ca1\u6709\u53ef\u514d\u8d39\u7814\u53d1\u7684\u677f\u5757");
        }
        removeBoardTile(state, actorId, industryType, Objects.toString(tile.get("id"), ""));
        incrementDevelopment(state, actorId, industryType);
        return industryType;
    }

    static String freeDevelopFirstAvailable(Map<String, Object> state, long actorId) {
        for (String industryType : INDUSTRY_TYPES) {
            Map<String, Object> tile = lowestDevelopableBoardTile(state, actorId, industryType);
            if (!tile.isEmpty()) {
                return freeDevelopSelected(state, actorId, industryType);
            }
        }
        return "";
    }

    static void flipTile(Map<String, Object> state, Map<String, Object> tile) {
        var industry = BrassBoardAdapter.industry(tile);
        if (!industry.flipped() && BrassTypedIndustries.resourceIndustry(industry.type())) {
            appendNotice(state, "玩家" + tile.get("ownerName") + " 在 " + cityCnName(Objects.toString(tile.get("city"), ""))
                    + " 的 " + industryNameCn(Objects.toString(tile.get("industryType"), ""))
                    + " 因产能消耗完毕而翻面");
        }
        flipTileByOwnerMap(tile);
        addIncome(state, industry.ownerId(), industry.incomeReward());
    }

    static String flipTileWithoutNotice(Map<String, Object> state, Map<String, Object> tile) {
        var industry = BrassBoardAdapter.industry(tile);
        if (industry.flipped()) {
            return "";
        }
        String notice = "";
        if (BrassTypedIndustries.resourceIndustry(industry.type())) {
            notice = "玩家" + tile.get("ownerName") + " 在 " + cityCnName(Objects.toString(tile.get("city"), ""))
                    + " 的 " + industryNameCn(Objects.toString(tile.get("industryType"), ""))
                    + " 因产能消耗完毕而翻面";
        }
        flipTileByOwnerMap(tile);
        addIncome(state, industry.ownerId(), industry.incomeReward());
        return notice;
    }

    static void flipTileByOwnerMap(Map<String, Object> tile) {
        var industry = BrassBoardAdapter.industry(tile);
        if (industry.flipped()) {
            return;
        }
        BrassBoardAdapter.writeFlip(tile, industry.flip());
    }

    private static Map<String, Object> industryAt(List<Map<String, Object>> industries, int index) {
        return index < 0 ? new LinkedHashMap<>() : industries.get(index);
    }
}
