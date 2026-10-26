package com.fortell.boardgame.game_modules.brass.model;

import java.util.List;

/** Scoped immutable board values during migration; never serialized as a save document.
 * Missing legacy fields are decoded at the adapter, not filled back into the save.
 */
public final class BrassBoardView {
    private BrassBoardView() {}

    public record Site(long ownerId, String city) {}
    // Keep the wire type open: historical/unknown link types still carry public connectivity.
    public record Link(String from, String to, long ownerId, String type) {}
    public record Route(String from, String to, boolean hasBrewery) {}

    public record Industry(String id, String city, String type, long ownerId, int slotIndex,
                           int level, boolean flipped, int coal, int iron, int beer,
                           Integer saleBeerCost, String flipType, boolean flipTypeSpecified,
                           int incomeReward) {
        public Industry flip() {
            return flipped ? this : new Industry(id, city, type, ownerId, slotIndex, level,
                    true, 0, 0, 0, saleBeerCost, flipType, flipTypeSpecified, incomeReward);
        }
    }

    /** Defensive copy prevents a later railway from changing an earlier network stage. */
    public record Network(List<Link> links) {
        public Network { links = List.copyOf(links); }
        public Network withLink(Link link) {
            var next = new java.util.ArrayList<>(links);
            next.add(link);
            return new Network(next);
        }
    }
}
