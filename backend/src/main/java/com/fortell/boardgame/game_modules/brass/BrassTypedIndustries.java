package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Industry;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Pure map-industry rules. Index results preserve the identity/order of legacy tile instances. */
final class BrassTypedIndustries {
    private BrassTypedIndustries() {}

    static int remaining(Industry tile) {
        return switch (tile.type()) {
            case "coal_mine" -> tile.coal();
            case "iron_works" -> tile.iron();
            case "brewery" -> tile.beer();
            default -> 0;
        };
    }

    static boolean resourceIndustry(String type) { return List.of("coal_mine", "iron_works", "brewery").contains(type); }
    static int saleBeer(Industry tile) { return tile.saleBeerCost() == null ? 1 : Math.max(0, tile.saleBeerCost()); }
    static boolean deplete(Industry tile) { return tile.flipTypeSpecified() ? "deplete".equals(tile.flipType()) : resourceIndustry(tile.type()); }

    static int firstIndex(List<Industry> tiles, Predicate<Industry> predicate) {
        for (int index = 0; index < tiles.size(); index++) if (predicate.test(tiles.get(index))) return index;
        return -1;
    }

    static List<Integer> ownedIndices(List<Industry> tiles, long actorId) {
        return java.util.stream.IntStream.range(0, tiles.size()).filter(i -> tiles.get(i).ownerId() == actorId).boxed().toList();
    }

    static int ownCover(List<Industry> tiles, long actor, String city, String type, Integer slot, int level) {
        // Personal supply already determines the next tile; covering has no face/level restriction.
        return firstIndex(tiles, tile -> city.equals(tile.city()) && type.equals(tile.type())
                && (slot == null || tile.slotIndex() == slot) && tile.ownerId() == actor);
    }

    static boolean factoriesHaveSupply(List<Industry> tiles, String type) {
        // The legacy service uses iron for any non-coal type; preserve this until resource migration.
        return tiles.stream().anyMatch(tile -> type.equals(tile.type()) && ("coal_mine".equals(type) ? tile.coal() : tile.iron()) > 0);
    }

    static int opponentCover(List<Industry> tiles, long actor, String city, String type, Integer slot, boolean marketEmpty) {
        if (!List.of("coal_mine", "iron_works").contains(type) || !marketEmpty || factoriesHaveSupply(tiles, type)) return -1;
        return firstIndex(tiles, tile -> city.equals(tile.city()) && type.equals(tile.type())
                && (slot == null || tile.slotIndex() == slot) && tile.ownerId() != actor);
    }

    static boolean dedicated(Map<String, List<List<String>>> citySlots, String city, String type, int slot) {
        List<List<String>> slots = citySlots.getOrDefault(city, List.of());
        return slot >= 0 && slot < slots.size() && slots.get(slot).size() == 1 && slots.get(slot).contains(type);
    }

    static boolean available(List<Industry> tiles, Map<String, List<List<String>>> citySlots, String city, String type, int slot) {
        List<List<String>> slots = citySlots.getOrDefault(city, List.of());
        return slot >= 0 && slot < slots.size() && slots.get(slot).contains(type)
                && tiles.stream().noneMatch(tile -> city.equals(tile.city()) && tile.slotIndex() == slot);
    }

    static int firstAvailable(List<Industry> tiles, Map<String, List<List<String>>> slots, String city, String type) {
        for (boolean dedicated : List.of(true, false)) {
            for (int index = 0; index < slots.getOrDefault(city, List.of()).size(); index++) {
                if (dedicated(slots, city, type, index) == dedicated && available(tiles, slots, city, type, index)) return index;
            }
        }
        return -1;
    }

    static int firstDedicated(List<Industry> tiles, List<Industry> boardTiles, Map<String, List<List<String>>> slots,
                              long actor, String city, String type, int nextLevel, boolean marketEmpty) {
        for (int index = 0; index < slots.getOrDefault(city, List.of()).size(); index++) {
            if (!dedicated(slots, city, type, index)) continue;
            if (available(tiles, slots, city, type, index) || ownCover(tiles, actor, city, type, index, nextLevel) >= 0
                    || (List.of("coal_mine", "iron_works").contains(type)
                    && opponentCover(boardTiles, actor, city, type, index, marketEmpty) >= 0)) return index;
        }
        return -1;
    }

    static int buildSlot(List<Industry> tiles, List<Industry> boardTiles, Map<String, List<List<String>>> slots,
                         long actor, String city, String type, int nextLevel, boolean marketEmpty, Integer requestedSlot) {
        int dedicated = firstDedicated(tiles, boardTiles, slots, actor, city, type, nextLevel, marketEmpty);
        if (requestedSlot != null) {
            var citySlots = slots.getOrDefault(city, List.of());
            if (requestedSlot < 0 || requestedSlot >= citySlots.size() || !citySlots.get(requestedSlot).contains(type)) return -1;
            // Dedicated-slot priority governs new placement, not replacing one's existing tile.
            if (ownCover(tiles, actor, city, type, requestedSlot, nextLevel) >= 0) return requestedSlot;
            if (!dedicated(slots, city, type, requestedSlot) && dedicated >= 0) return -1;
            if (available(tiles, slots, city, type, requestedSlot)) return requestedSlot;
            return List.of("coal_mine", "iron_works").contains(type)
                    && opponentCover(boardTiles, actor, city, type, requestedSlot, marketEmpty) >= 0 ? requestedSlot : -1;
        }
        if (dedicated >= 0) return dedicated;
        int available = firstAvailable(tiles, slots, city, type);
        if (available >= 0) return available;
        int own = ownCover(tiles, actor, city, type, null, nextLevel);
        if (own >= 0) return tiles.get(own).slotIndex();
        if (!List.of("coal_mine", "iron_works").contains(type)) return -1;
        int opponent = opponentCover(boardTiles, actor, city, type, null, marketEmpty);
        return opponent < 0 ? -1 : boardTiles.get(opponent).slotIndex();
    }
}
