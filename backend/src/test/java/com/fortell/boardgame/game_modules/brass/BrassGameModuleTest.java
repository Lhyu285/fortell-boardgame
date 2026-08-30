package com.fortell.boardgame.game_modules.brass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.RoomStatus;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrassGameModuleTest {

    @Test
    void startsCanalEraWithHandsAndTurnSnapshot() throws Exception {
        BrassGameModule module = new BrassGameModule();
        var state = startPlaying(module);

        assertEquals("playing", state.get("phase"));
        assertEquals("canal", state.get("era"));
        assertEquals(1, state.get("round"));
        assertNotNull(state.get("turnStartSnapshot"));
        assertEquals(2, ((List<?>) state.get("players")).size());
        assertEquals(8, handSize(state, "1"));
        assertEquals(22, ((List<?>) state.get("deck")).size());
        assertEquals(2, state.get("hiddenDiscardCount"));
        assertEquals(8, ((List<?>) state.get("scoutPool")).size());
        assertEquals(11, playerBoardCount(state, "1", "cotton_mill"));
        assertFalse(availableActions(state).contains("restart_turn"));
        assertTrue(availableActions(state).contains("skip"));
        assertTrue(availableActions(state).contains("loan"));
        assertTrue(availableActions(state).contains("build"));
        Map<?, ?> available = (Map<?, ?>) state.get("availableActions");
        assertTrue(((List<?>) available.get("buildOptions")).size() > 0);
        assertTrue(((List<?>) available.get("networkRoutes")).size() > 0);
        assertTrue(((List<?>) available.get("developIndustries")).size() > 0);
        assertNotNull(available.get("resourceSources"));
        new ObjectMapper().writeValueAsString(state);
    }

    @Test
    void randomlyChoosesFirstCanalRoundTurnOrder() {
        BrassGameModule module = new BrassGameModule();
        boolean sawPlayerOneFirst = false;
        boolean sawPlayerTwoFirst = false;

        for (int attempt = 0; attempt < 40 && !(sawPlayerOneFirst && sawPlayerTwoFirst); attempt++) {
            Map<String, Object> state = module.onStart(room(), seats(), module.defaultConfig());
            List<?> turnOrder = (List<?>) state.get("turnOrder");
            assertEquals(2, turnOrder.size());
            assertTrue(turnOrder.containsAll(List.of(1L, 2L)));
            assertEquals(turnOrder, state.get("initialTurnOrder"));
            assertEquals(turnOrder.getFirst(), state.get("currentPlayerId"));
            sawPlayerOneFirst |= Long.valueOf(1L).equals(turnOrder.getFirst());
            sawPlayerTwoFirst |= Long.valueOf(2L).equals(turnOrder.getFirst());
        }

        assertTrue(sawPlayerOneFirst && sawPlayerTwoFirst);
    }

    @Test
    void firstCanalRoundAllowsOneActionThenSecondRoundAllowsRestart() {
        BrassGameModule module = new BrassGameModule();
        var state = startPlaying(module);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", firstCardId(state, "1")), List.of());
        assertEquals(1, state.get("round"));
        assertEquals(1L, state.get("currentPlayerId"));
        assertEquals(0, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
        assertEquals(true, ((Map<?, ?>) state.get("turn")).get("awaitingEndTurn"));
        assertTrue(availableActions(state).contains("restart_turn"));
        assertTrue(availableActions(state).contains("end_turn"));
        assertFalse(availableActions(state).contains("loan"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "restart_turn", Map.of(), List.of());
        assertEquals(1L, state.get("currentPlayerId"));
        assertEquals(1, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
        assertFalse(availableActions(state).contains("restart_turn"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", firstCardId(state, "1")), List.of());
        assertTrue(availableActions(state).contains("restart_turn"));
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "restart_turn", Map.of(), List.of());
        assertEquals(1, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
        assertEquals(8, handSize(state, "1"));
        assertFalse(availableActions(state).contains("restart_turn"));
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", firstCardId(state, "1")), List.of());
        state = endTurn(module, state, 1L, "A");
        assertEquals(2L, state.get("currentPlayerId"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(2, "B"), "skip", Map.of("cardId", firstCardId(state, "2")), List.of());
        state = endTurn(module, state, 2L, "B");
        assertEquals(2, state.get("round"));
        assertEquals(1L, state.get("currentPlayerId"));
        assertEquals(2, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(0, playerStat(state, "1", "lastIncome"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "loan", Map.of("cardId", firstCardId(state, "1")), List.of());
        assertEquals(47, playerStat(state, "1", "money"));
        assertEquals(1, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
        assertTrue(availableActions(state).contains("restart_turn"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "restart_turn", Map.of(), List.of());
        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(2, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
        assertFalse(availableActions(state).contains("restart_turn"));
    }

    @Test
    void rejectsActionFromNonCurrentPlayerWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startPlaying(module);
        String playerOneCard = firstCardId(state, "1");

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState,
                new UserSummary(2, "B"), "skip", Map.of("cardId", playerOneCard), List.of()));

        assertEquals(1L, state.get("currentPlayerId"));
        assertEquals(8, handSize(state, "1"));
    }

    @Test
    void rejectsUnavailableMainActionWhileAwaitingEndTurn() {
        BrassGameModule module = new BrassGameModule();
        var state = startPlaying(module);
        String cardId = firstCardId(state, "1");
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", cardId), List.of());
        assertEquals(true, ((Map<?, ?>) state.get("turn")).get("awaitingEndTurn"));

        var targetState = state;
        String loanCardId = firstCardId(state, "1");
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState,
                new UserSummary(1, "A"), "loan", Map.of("cardId", loanCardId), List.of()));

        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(true, ((Map<?, ?>) state.get("turn")).get("awaitingEndTurn"));
    }

    @Test
    void rejectsEraMaintenanceBeforeItIsAvailable() {
        BrassGameModule module = new BrassGameModule();
        var state = startPlaying(module);

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState,
                new UserSummary(1, "A"), "maintain_era", Map.of(), List.of()));

        assertEquals("canal", state.get("era"));
        assertFalse(Boolean.TRUE.equals(state.get("eraEnding")));
    }

    @Test
    void loanIsAvailableInRailEraAsMainAction() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        setPlayerStat(state, "1", "incomeLevel", 3);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "loan", Map.of("cardId", "card-a"), List.of());

        assertEquals(47, playerStat(state, "1", "money"));
        assertEquals(0, playerStat(state, "1", "incomeLevel"));
        assertEquals(0, handSize(state, "1"));
        assertEquals(1, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
    }

    @Test
    void loanUsesConfiguredIncomeLevelAndRecalculatesIncome() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        setPlayerStat(state, "1", "incomeLevel", 26);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "loan", Map.of("cardId", "card-a"), List.of());

        assertEquals(20, playerStat(state, "1", "incomeLevel"));
        assertEquals(5, playerStat(state, "1", "income"));
        assertEquals(47, playerStat(state, "1", "money"));
    }

    @Test
    void loanRejectsIncomeLevelsZeroOneAndTwoWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        for (int incomeLevel = 0; incomeLevel <= 2; incomeLevel++) {
            var state = startSecondRound(module);
            replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
            setPlayerStat(state, "1", "incomeLevel", incomeLevel);

            var targetState = state;
            assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "loan", Map.of("cardId", "card-a"), List.of()));

            assertEquals(17, playerStat(state, "1", "money"));
            assertEquals(incomeLevel, playerStat(state, "1", "incomeLevel"));
            assertEquals(1, handSize(state, "1"));
        }
    }

    @Test
    void loanRejectsCardNotInHandWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "loan", Map.of("cardId", "missing-card"), List.of()));

        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(10, playerStat(state, "1", "incomeLevel"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void usedWildCardReturnsToScoutPoolInsteadOfDiscardPile() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> wildLocation = card("wild-location", "wild_location", "wild_location", "Wild Location");
        wildLocation.put("wild", true);
        replaceHand(state, "1", List.of(wildLocation));
        int initialDiscardCount = ((List<?>) state.get("discardPile")).size();
        int initialScoutPoolCount = ((List<?>) state.get("scoutPool")).size();

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of(
                "cardId", "wild-location"
        ), List.of());

        assertEquals(0, handSize(state, "1"));
        assertEquals(initialDiscardCount, ((List<?>) state.get("discardPile")).size());
        assertEquals(initialScoutPoolCount + 1, ((List<?>) state.get("scoutPool")).size());
    }

    @Test
    void buildPlacesIndustryConsumesCardAndMoney() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "cotton_mill"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(1, industries.size());
        assertEquals(5, playerStat(state, "1", "money"));
        assertEquals(0, handSize(state, "1"));
        assertEquals(10, playerBoardCount(state, "1", "cotton_mill"));
        assertEquals(1, ((Map<?, ?>) state.get("turn")).get("actionsRemaining"));
    }

    @Test
    void buildCanUsePreferredCoalSource() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Derby", "Derby")));
        replaceIndustries(state, List.of(
                tile("coal-a", 2L, "B", "Derby", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Derby",
                "industryType", "manufacturer",
                "coalSourceTileId", "coal-a"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
    }

    @Test
    void canalEraRejectsSecondIndustryInSameCityBySamePlayer() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "industry", "manufacturer", "Industry: Manufacturer")
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "cotton_mill"
        ), List.of());

        var afterFirstBuild = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), afterFirstBuild, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-b",
                "city", "Birmingham",
                "industryType", "manufacturer"
        ), List.of()));
    }

    @Test
    void buildRejectsLocationCardForDifferentCityWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Derby",
                "industryType", "manufacturer"
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void buildRejectsIndustryUnsupportedByCityWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "pottery"
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void breweryIndustryCardCanBuildPersonalBreweryAfterOwningKidderminsterWorcesterRoute() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> breweryCard = card("brewery-card", "industry", "brewery", "Industry: Brewery");
        breweryCard.put("industryTypes", List.of("brewery"));
        replaceHand(state, "1", List.of(breweryCard));
        replaceLinks(state, List.of(link("private-brewery-route", 1L, "A", "Kidderminster", "Worcester", "canal")));

        Map<?, ?> available = invokeAvailableActions(module, state);
        assertTrue(((List<?>) available.get("buildOptions")).stream()
                .map(Map.class::cast)
                .anyMatch(option -> "Personal_Brewery".equals(option.get("city"))
                        && "brewery".equals(option.get("industryType"))
                        && ((List<?>) option.get("cardIds")).contains("brewery-card")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "brewery-card",
                "city", "Personal_Brewery",
                "industryType", "brewery"
        ), List.of());

        assertTrue(((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).stream()
                .map(Map.class::cast)
                .anyMatch(tile -> "Personal_Brewery".equals(tile.get("city"))
                        && "brewery".equals(tile.get("industryType"))
                        && Long.valueOf(1L).equals(tile.get("ownerId"))));
    }

    @Test
    void resourceIndustryUsesCurrentEraResourceAmountWhenBuilt() {
        BrassGameModule module = new BrassGameModule();
        var canalState = startSecondRound(module);
        Map<String, Object> breweryCard = card("brewery-card", "industry", "brewery", "Industry: Brewery");
        breweryCard.put("industryTypes", List.of("brewery"));
        replaceHand(canalState, "1", List.of(breweryCard));
        Map<String, Object> breweryTile = boardTile("brewery-variable", "brewery", 2, 0);
        breweryTile.put("resourceAmounts", List.of(1, 2));
        replacePlayerBoardTiles(canalState, "1", "brewery", List.of(breweryTile));

        canalState = module.onAction(room(), seats(), module.defaultConfig(), canalState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "brewery-card",
                "city", "Stone",
                "industryType", "brewery"
        ), List.of());
        assertEquals(1, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) canalState.get("board")).get("industries")).getFirst()).get("beer"));

        var railState = startSecondRound(module);
        railState.put("era", "rail");
        Map<String, Object> railBreweryCard = card("brewery-card", "industry", "brewery", "Industry: Brewery");
        railBreweryCard.put("industryTypes", List.of("brewery"));
        replaceHand(railState, "1", List.of(railBreweryCard));
        Map<String, Object> railBreweryTile = boardTile("brewery-variable", "brewery", 2, 0);
        railBreweryTile.put("resourceAmounts", List.of(1, 2));
        replacePlayerBoardTiles(railState, "1", "brewery", List.of(railBreweryTile));

        railState = module.onAction(room(), seats(), module.defaultConfig(), railState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "brewery-card",
                "city", "Stone",
                "industryType", "brewery"
        ), List.of());
        assertEquals(2, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) railState.get("board")).get("industries")).getFirst()).get("beer"));
    }

    @Test
    void sellFlipTypeNeverProvidesConfiguredResourcesWhenBuilt() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        Map<String, Object> manufacturer = boardTile("manufacturer-with-invalid-resource", "manufacturer", 1, 0);
        manufacturer.put("resourceAmounts", List.of(4, 6));
        manufacturer.put("flipType", "sell");
        replacePlayerBoardTiles(state, "1", "manufacturer", List.of(manufacturer));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "manufacturer"
        ), List.of());

        Map<?, ?> built = (Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).getFirst();
        assertEquals(0, built.get("coal"));
        assertEquals(0, built.get("iron"));
        assertEquals(0, built.get("beer"));
        assertEquals(false, built.get("flipped"));
    }

    @Test
    void depleteFlipTypeCannotBeSoldEvenIfIndustryTypeIsNormallySellable() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        Map<String, Object> manufacturer = tile("manufacturer-a", 1L, "A", "Birmingham", "manufacturer", "Manufacturer", 0, 0, 1, 0, 0);
        manufacturer.put("flipType", "deplete");
        manufacturer.put("saleBeerCost", 0);
        replaceIndustries(state, List.of(manufacturer));

        Map<?, ?> available = invokeAvailableActions(module, state);
        assertTrue(((List<?>) available.get("sellTiles")).isEmpty());

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState,
                new UserSummary(1, "A"), "sell", Map.of(
                        "cardId", "card-a",
                        "tileId", "manufacturer-a"
                ), List.of()));
        assertEquals(false, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).getFirst()).get("flipped"));
    }

    @Test
    void industryCardBuildRequiresOwnNetworkAfterNetworkExists() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "industry", "manufacturer", "Industry: Manufacturer")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Derby",
                "industryType", "manufacturer"
        ), List.of()));
    }

    @Test
    void industryCardBuildAllowsCityInOwnNetwork() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "industry", "manufacturer", "Industry: Manufacturer")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(
                link("link-a", 1L, "A", "Birmingham", "Walsall", "canal"),
                link("market-link", 2L, "B", "Birmingham", "Oxford", "canal")
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Walsall",
                "industryType", "manufacturer"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(2, industries.size());
        assertEquals("Walsall", ((Map<?, ?>) industries.get(1)).get("city"));
    }

    @Test
    void wildLocationBuildAllowsCityOutsideOwnNetwork() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> wildLocation = card("card-a", "wild_location", "wild_location", "Wild Location");
        wildLocation.put("wild", true);
        replaceHand(state, "1", List.of(wildLocation));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("market-link", 2L, "B", "Derby", "Nottingham", "canal")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Derby",
                "industryType", "manufacturer"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(2, industries.size());
        assertEquals("Derby", ((Map<?, ?>) industries.get(1)).get("city"));
        assertEquals(0, handSize(state, "1"));
    }

    @Test
    void wildIndustryBuildRequiresOwnNetworkAfterNetworkExists() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> wildIndustry = card("card-a", "wild_industry", "wild_industry", "Wild Industry");
        wildIndustry.put("wild", true);
        replaceHand(state, "1", List.of(wildIndustry));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Derby",
                "industryType", "manufacturer"
        ), List.of()));

        assertEquals(1, ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void wildIndustryBuildAllowsAnyIndustryInsideOwnNetwork() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> wildIndustry = card("card-a", "wild_industry", "wild_industry", "Wild Industry");
        wildIndustry.put("wild", true);
        replaceHand(state, "1", List.of(wildIndustry));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(
                link("link-a", 1L, "A", "Birmingham", "Walsall", "canal"),
                link("market-link", 2L, "B", "Birmingham", "Oxford", "canal")
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Walsall",
                "industryType", "manufacturer"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(2, industries.size());
        assertEquals("manufacturer", ((Map<?, ?>) industries.get(1)).get("industryType"));
        assertEquals(0, handSize(state, "1"));
    }

    @Test
    void sellConsumesBeerAndFlipsSellableIndustry() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 1L, "A", "Nottingham", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(true, ((Map<?, ?>) industries.get(1)).get("flipped"));
        assertEquals(19, playerStat(state, "1", "incomeLevel"));
        assertEquals(0, playerStat(state, "1", "victoryPoints"));
        assertEquals(0, handSize(state, "1"));
    }

    @Test
    void sellSessionCanSellMultipleIndustriesWithDifferentBeerSourcesBeforeEndingAction() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("manufacturer-a", 1L, "A", "Birmingham", "manufacturer", "Manufacturer", 0, 0, 0, 4, 4),
                tile("beer-a", 1L, "A", "Nottingham", "brewery", "Brewery", 0, 0, 1, 4, 4),
                tile("beer-b", 1L, "A", "Oxford", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "beerSourceTileId", "beer-a"
        ), List.of());

        assertEquals(0, handSize(state, "1"));
        assertEquals(true, ((Map<?, ?>) state.get("turn")).get("sellInProgress"));
        assertEquals(1, ((Number) ((Map<?, ?>) state.get("turn")).get("sellCount")).intValue());
        assertTrue(((List<?>) ((Map<?, ?>) state.get("availableActions")).get("actions")).contains("end_sell"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "tileId", "manufacturer-a",
                "beerSourceTileId", "beer-b"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(true, ((Map<?, ?>) industries.get(1)).get("flipped"));
        assertEquals(true, ((Map<?, ?>) industries.get(2)).get("flipped"));
        assertEquals(true, ((Map<?, ?>) industries.get(3)).get("flipped"));
        assertEquals(0, handSize(state, "1"));
        assertEquals(2, ((Number) ((Map<?, ?>) state.get("turn")).get("sellCount")).intValue());

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "end_sell", Map.of(), List.of());
        Map<?, ?> turn = (Map<?, ?>) state.get("turn");
        assertEquals(1, ((Number) turn.get("actionsRemaining")).intValue());
        assertEquals(null, turn.get("sellInProgress"));
    }

    @Test
    void sellRejectsBatchTileIdsWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 1L, "A", "Birmingham", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileIds", List.of("cotton-a")
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(false, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void networkBuildsCanalAndRequiresConnectionAfterFirstLink() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Derby", "Derby")
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Birmingham",
                "to", "Walsall"
        ), List.of());

        List<?> links = (List<?>) ((Map<?, ?>) state.get("board")).get("links");
        assertEquals(1, links.size());
        assertEquals(14, playerStat(state, "1", "money"));

        var afterFirstLink = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), afterFirstLink, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-b",
                "from", "Derby",
                "to", "Nottingham"
        ), List.of()));
    }

    @Test
    void availableActionsHideNetworkWhenPlayerHasBuiltFourteenLinks() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        List<Map<String, Object>> links = new java.util.ArrayList<>();
        for (int index = 0; index < 14; index++) {
            links.add(link("link-" + index, 1L, "A", "Birmingham", "Walsall", "canal"));
        }
        replaceLinks(state, links);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Coventry", "Coventry")
        ));
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", "card-a"), List.of());

        Map<?, ?> available = (Map<?, ?>) state.get("availableActions");
        assertEquals(false, ((List<?>) available.get("actions")).contains("network"));
        assertEquals(0, ((List<?>) available.get("networkRoutes")).size());
        assertEquals(0, ((Number) available.get("remainingLinks")).intValue());
    }

    @Test
    void mapAllowsCitySlotOptionsFromBirminghamMap() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Leek", "Leek")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Leek",
                "industryType", "coal_mine"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals("coal_mine", ((Map<?, ?>) industries.get(0)).get("industryType"));
        assertEquals(1, ((Map<?, ?>) industries.get(0)).get("slotIndex"));
    }

    @Test
    void mapAllowsRailOnlyRouteFromBirminghamMap() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Burton-on-Trent", "Burton-on-Trent")));
        replaceIndustries(state, List.of(
                tile("coal-a", 2L, "B", "Burton-on-Trent", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Burton-on-Trent",
                "to", "Cannock"
        ), List.of());

        List<?> links = (List<?>) ((Map<?, ?>) state.get("board")).get("links");
        assertEquals(1, links.size());
        assertEquals("rail", ((Map<?, ?>) links.get(0)).get("type"));
    }

    @Test
    void sellCanConsumeMerchantBeerAndApplyMapReward() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("pottery-a", 1L, "A", "Birmingham", "pottery", "Pottery", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Oxford", "canal")));
        replaceMerchant(state, "merchant_oxford", List.of("pottery"), 2, false);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "pottery-a",
                "merchantId", "merchant_oxford"
        ), List.of());

        assertEquals(17, playerStat(state, "1", "incomeLevel"));
        List<?> merchants = (List<?>) ((Map<?, ?>) state.get("market")).get("beerMerchants");
        Map<?, ?> oxford = merchants.stream()
                .map(Map.class::cast)
                .filter(merchant -> "merchant_oxford".equals(merchant.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals(1, ((Number) oxford.get("beer")).intValue());
    }

    @Test
    void merchantDevelopRewardCanUseSelectedIndustry() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Gloucester", "canal")));
        replaceMerchant(state, "merchant_gloucester", List.of("cotton_mill"), 1, false);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "merchantId", "merchant_gloucester",
                "freeDevelopIndustryType", "iron_works"
        ), List.of());

        assertEquals(1, developmentValue(state, "1", "iron_works"));
        assertEquals(0, developmentValue(state, "1", "cotton_mill"));
    }

    @Test
    void merchantDevelopRewardRejectsInvalidIndustryWithoutConsumingMerchantBeer() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Gloucester", "canal")));
        replaceMerchant(state, "merchant_gloucester", List.of("cotton_mill"), 1, false);

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "merchantId", "merchant_gloucester",
                "freeDevelopIndustryType", "invalid"
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        List<?> merchants = (List<?>) ((Map<?, ?>) state.get("market")).get("beerMerchants");
        Map<?, ?> gloucester = merchants.stream()
                .map(Map.class::cast)
                .filter(merchant -> "merchant_gloucester".equals(merchant.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals(false, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(1, gloucester.get("beer"));
        assertEquals(1, handSize(state, "1"));
        assertEquals(0, developmentValue(state, "1", "iron_works"));
    }

    @Test
    void merchantVpRewardsApplyImmediately() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(
                link("link-a", 1L, "A", "Birmingham", "Walsall", "canal"),
                link("link-b", 1L, "A", "Walsall", "Nottingham", "canal")
        ));
        replaceMerchant(state, "merchant_nottingham", List.of("cotton_mill"), 1, false);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "merchantId", "merchant_nottingham"
        ), List.of());

        assertEquals(3, playerStat(state, "1", "victoryPoints"));
    }

    @Test
    void merchantMoneyRewardAppliesImmediately() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Warrington", "canal")));
        replaceMerchant(state, "merchant_warrington", List.of("cotton_mill"), 1, false);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "merchantId", "merchant_warrington"
        ), List.of());

        assertEquals(22, playerStat(state, "1", "money"));
    }

    @Test
    void sellRejectsMerchantThatDoesNotAcceptIndustryWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Oxford", "canal")));
        replaceMerchant(state, "merchant_oxford", List.of("pottery"), 1, false);

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "merchantId", "merchant_oxford"
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(false, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(1, handSize(state, "1"));
        assertEquals(10, playerStat(state, "1", "incomeLevel"));
    }

    @Test
    void sellRejectsMerchantWithoutBeerWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Oxford", "canal")));
        replaceMerchant(state, "merchant_oxford", List.of("cotton_mill"), 0, false);

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "merchantId", "merchant_oxford"
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(false, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(1, handSize(state, "1"));
        assertEquals(10, playerStat(state, "1", "incomeLevel"));
    }

    @Test
    void sellRejectsCardNotInHandWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 1L, "A", "Birmingham", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "missing-card",
                "tileId", "cotton-a"
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(false, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void sellRejectsDisconnectedMerchantBeer() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "merchantId", "merchant_oxford"
        ), List.of()));
    }

    @Test
    void sellRejectsDisconnectedOtherPlayerBeer() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 2L, "B", "Derby", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "beerSourceTileId", "beer-a"
        ), List.of()));
    }

    @Test
    void sellCanConsumeConnectedOtherPlayerBeer() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 2L, "B", "Walsall", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Walsall", "canal")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "beerSourceTileId", "beer-a"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(1)).get("flipped"));
    }

    @Test
    void sellCanConsumeOwnBeerWithoutConnection() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 1L, "A", "Derby", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "beerSourceTileId", "beer-a"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(true, ((Map<?, ?>) industries.get(1)).get("flipped"));
    }

    @Test
    void sellRejectsPreferredBeerWithInsufficientAmountWithoutFallback() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        Map<String, Object> cotton = tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5);
        cotton.put("saleBeerCost", 2);
        replaceIndustries(state, List.of(
                cotton,
                tile("beer-a", 1L, "A", "Derby", "brewery", "Brewery", 0, 0, 1, 4, 4),
                tile("beer-b", 1L, "A", "Nottingham", "brewery", "Brewery", 0, 0, 2, 4, 4)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "beerSourceTileId", "beer-a"
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(false, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(1, ((Map<?, ?>) industries.get(1)).get("beer"));
        assertEquals(2, ((Map<?, ?>) industries.get(2)).get("beer"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void incomeLevelDoesNotRiseAboveNinetyNine() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        setPlayerStat(state, "1", "incomeLevel", 99);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 1L, "A", "Derby", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a"
        ), List.of());

        assertEquals(99, playerStat(state, "1", "incomeLevel"));
    }

    @Test
    void sellingZeroBeerManufacturerDoesNotConsumeMerchantBeerOrGrantReward() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        Map<String, Object> manufacturer = tile("manufacturer-a", 1L, "A", "Birmingham", "manufacturer", "Manufacturer", 0, 0, 0, 0, 0);
        manufacturer.put("saleBeerCost", 0);
        replaceIndustries(state, List.of(manufacturer));
        replaceLinks(state, List.of(link("market-link", 1L, "A", "Birmingham", "Nottingham", "canal")));
        replaceMerchant(state, "merchant_nottingham", List.of("manufacturer"), 1, false);
        int victoryPointsBefore = playerStat(state, "1", "victoryPoints");

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "manufacturer-a",
                "merchantId", "merchant_nottingham"
        ), List.of());

        Map<?, ?> merchant = ((List<?>) ((Map<?, ?>) state.get("market")).get("beerMerchants")).stream()
                .map(Map.class::cast)
                .filter(item -> "merchant_nottingham".equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals(1, merchant.get("beer"));
        assertEquals(victoryPointsBefore, playerStat(state, "1", "victoryPoints"));
        assertEquals(true, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).getFirst()).get("flipped"));
    }

    @Test
    void incomeCollectionUsesIncomeConfig() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        setPlayerStat(state, "1", "incomeLevel", 99);
        replaceHand(state, "1", List.of(
                card("a-skip-1", "location", "Birmingham", "Birmingham"),
                card("a-skip-2", "location", "Coventry", "Coventry")
        ));
        replaceHand(state, "2", List.of(
                card("b-skip-1", "location", "Derby", "Derby"),
                card("b-skip-2", "location", "Nottingham", "Nottingham")
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", "a-skip-1"), List.of());
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", "a-skip-2"), List.of());
        state = endTurn(module, state, 1L, "A");
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(2, "B"), "skip", Map.of("cardId", "b-skip-1"), List.of());
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(2, "B"), "skip", Map.of("cardId", "b-skip-2"), List.of());
        state = endTurn(module, state, 2L, "B");

        assertEquals(30, playerStat(state, "1", "lastIncome"));
        assertEquals(47, playerStat(state, "1", "money"));
    }

    @Test
    void finalRoundCollectsNegativeIncomeBeforeEraMaintenanceAndResolvesDebt() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        forceTestTurnOrder(state, List.of(1L, 2L));
        state.put("turnIndex", 1);
        state.put("currentPlayerId", 2L);
        state.put("deck", List.of());
        replaceHand(state, "1", List.of());
        replaceHand(state, "2", List.of());
        setPlayerStat(state, "2", "incomeLevel", 0);
        setPlayerStat(state, "2", "money", 1);
        setPlayerStat(state, "2", "victoryPoints", 3);
        Map<String, Object> iron = tile("iron-a", 2L, "B", "Birmingham", "iron_works", "Iron Works", 0, 0, 0, 0, 0);
        iron.put("cost", 5);
        replaceIndustries(state, List.of(iron));
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("awaitingEndTurn", true);
        turn.put("actionsTaken", 2);
        state.put("turn", turn);

        state = endTurn(module, state, 2L, "B");

        assertFalse(Boolean.TRUE.equals(state.get("eraEnding")));
        assertEquals(2L, state.get("currentPlayerId"));
        assertEquals(9, ((Map<?, ?>) state.get("incomeDebt")).get("amount"));
        assertEquals(List.of("resolve_income_debt"), availableActions(state));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(2, "B"),
                "resolve_income_debt", Map.of("tileId", "iron-a"), List.of());

        assertEquals(true, state.get("eraEnding"));
        assertEquals(true, state.get("canMaintainEra"));
        assertEquals(0, playerStat(state, "2", "money"));
        assertEquals(-4, playerStat(state, "2", "victoryPoints"));
        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).size());
    }

    @Test
    void playersReceiveRandomColorsAndNetworkLinkUsesPlayerColor() {
        BrassGameModule module = new BrassGameModule();
        var state = module.onStart(room(), seats(), module.defaultConfig());
        forceTestTurnOrder(state, List.of(1L, 2L));
        assertEquals("playing", state.get("phase"));
        assertEquals(1L, state.get("currentPlayerId"));
        String playerColor = String.valueOf(((Map<?, ?>) ((List<?>) state.get("players")).getFirst()).get("color"));
        assertTrue(List.of("red", "yellow", "blue", "purple").contains(playerColor));

        state = startSecondRoundFromState(module, state);
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", firstCardId(state, "1"),
                "from", "Birmingham",
                "to", "Walsall"
        ), List.of());

        Map<?, ?> board = (Map<?, ?>) state.get("board");
        Map<?, ?> link = (Map<?, ?>) ((List<?>) board.get("links")).getFirst();
        assertEquals(playerColor, link.get("color"));
    }

    @Test
    void endTurnEntersEraEndingWhenDeckAndHandsAreEmpty() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        forceTestTurnOrder(state, List.of(2L, 1L));
        state.put("turnIndex", 1);
        state.put("currentPlayerId", 1L);
        state.put("deck", List.of());
        replaceHand(state, "1", List.of(
                card("last-card-1", "location", "Birmingham", "Birmingham"),
                card("last-card-2", "location", "Coventry", "Coventry")
        ));
        replaceHand(state, "2", List.of());

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", "last-card-1"), List.of());
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", "last-card-2"), List.of());
        assertEquals(true, ((Map<?, ?>) state.get("turn")).get("awaitingEndTurn"));
        assertTrue(availableActions(state).contains("restart_turn"));
        assertTrue(availableActions(state).contains("end_turn"));
        assertFalse(availableActions(state).contains("maintain_era"));

        state = endTurn(module, state, 1L, "A");

        assertEquals(true, state.get("eraEnding"));
        assertEquals(true, state.get("canMaintainEra"));
        assertEquals(1L, state.get("currentPlayerId"));
        assertEquals(List.of("maintain_era"), availableActions(state));
    }

    @Test
    void emptyCurrentPlayerHandDoesNotSkipRemainingRoundTurns() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("deck", List.of());
        replaceHand(state, "1", List.of());
        replaceHand(state, "2", List.of(card("b-card", "location", "Derby", "Derby")));
        state.put("currentPlayerId", 1L);
        state.put("availableActions", invokeAvailableActions(module, state));

        assertFalse(availableActions(state).contains("maintain_era"));
    }

    @Test
    void railNetworkConsumesCoalFromMarket() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceLinks(state, List.of(link("market-link", 2L, "B", "Birmingham", "Oxford", "canal")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Birmingham",
                "to", "Walsall"
        ), List.of());

        List<?> links = (List<?>) ((Map<?, ?>) state.get("board")).get("links");
        assertTrue(links.stream().map(Map.class::cast).anyMatch(link -> "rail".equals(link.get("type"))));
        assertEquals(11, playerStat(state, "1", "money"));
    }

    @Test
    void railNetworkCanBuyDistantCoalWhenCoalMarketEmpty() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceLinks(state, List.of(link("market-link", 2L, "B", "Birmingham", "Oxford", "canal")));
        ((Map<String, Object>) state.get("market")).put("coal", new java.util.ArrayList<>());

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Birmingham",
                "to", "Walsall"
        ), List.of());

        List<?> links = (List<?>) ((Map<?, ?>) state.get("board")).get("links");
        assertEquals(2, links.size());
        assertTrue(links.stream().map(Map.class::cast).anyMatch(link -> "rail".equals(link.get("type"))));
        assertEquals(4, playerStat(state, "1", "money"));
        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("market")).get("coal")).size());
    }

    @Test
    void railNetworkCanConsumeReachableCoalMine() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("coal-a", 1L, "A", "Birmingham", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Birmingham",
                "to", "Walsall",
                "coalSourceTileId", "coal-a"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(12, playerStat(state, "1", "money"));
    }

    @Test
    void railNetworkCanConsumeCoalMadeReachableByNewLink() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("coal-a", 2L, "B", "Walsall", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Birmingham",
                "to", "Walsall",
                "coalSourceTileId", "coal-a"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
    }

    @Test
    void railNetworkCanBuildTwoLinksAndConsumeBeerMadeReachableBySecondLink() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("coal-a", 2L, "B", "Dudley", "coal_mine", "Coal Mine", 2, 0, 0, 4, 3),
                tile("beer-a", 2L, "B", "Wolverhampton", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "routes", List.of(
                        Map.of("from", "Birmingham", "to", "Dudley", "coalSourceTileId", "coal-a"),
                        Map.of("from", "Dudley", "to", "Wolverhampton", "coalSourceTileId", "coal-a")
                ),
                "beerSourceTileId", "beer-a"
        ), List.of());

        List<?> links = (List<?>) ((Map<?, ?>) state.get("board")).get("links");
        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(2, links.size());
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(true, ((Map<?, ?>) industries.get(1)).get("flipped"));
        assertEquals(2, playerStat(state, "1", "money"));
    }

    @Test
    void railNetworkFirstLinkCannotUseCoalOnlyMadeReachableBySecondLink() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("coal-a", 2L, "B", "Wolverhampton", "coal_mine", "Coal Mine", 2, 0, 0, 4, 3),
                tile("beer-a", 1L, "A", "Birmingham", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState,
                new UserSummary(1, "A"), "network", Map.of(
                        "cardId", "card-a",
                        "routes", List.of(
                                Map.of("from", "Birmingham", "to", "Dudley", "coalSourceTileId", "coal-a"),
                                Map.of("from", "Dudley", "to", "Wolverhampton", "coalSourceTileId", "coal-a")
                        ),
                        "beerSourceTileId", "beer-a"
                ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(2, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).get(0)).get("coal"));
        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void railNetworkRejectsDisconnectedOtherPlayerBeer() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("beer-a", 2L, "B", "Derby", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "routes", List.of(
                        Map.of("from", "Birmingham", "to", "Walsall"),
                        Map.of("from", "Walsall", "to", "Dudley")
                ),
                "beerSourceTileId", "beer-a"
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(1, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).get(0)).get("beer"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void railNetworkRejectsDisconnectedPreferredCoalMine() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("coal-a", 1L, "A", "Derby", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Birmingham",
                "to", "Walsall",
                "coalSourceTileId", "coal-a"
        ), List.of()));
        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(1, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).get(0)).get("coal"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void railNetworkRejectsDepletedPreferredCoalMineWithoutBuyingMarketCoal() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("coal-a", 1L, "A", "Birmingham", "coal_mine", "Coal Mine", 0, 0, 0, 4, 3)
        ));
        int marketCoalCount = ((List<?>) ((Map<?, ?>) state.get("market")).get("coal")).size();

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "from", "Birmingham",
                "to", "Walsall",
                "coalSourceTileId", "coal-a"
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(marketCoalCount, ((List<?>) ((Map<?, ?>) state.get("market")).get("coal")).size());
        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void networkRejectsCardNotInHandWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "missing-card",
                "from", "Birmingham",
                "to", "Walsall"
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void canalNetworkRejectsTwoRoutesWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "routes", List.of(
                        Map.of("from", "Birmingham", "to", "Walsall"),
                        Map.of("from", "Birmingham", "to", "Coventry")
                )
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void railNetworkRejectsDuplicateRoutesWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "routes", List.of(
                        Map.of("from", "Birmingham", "to", "Walsall"),
                        Map.of("from", "Walsall", "to", "Birmingham")
                )
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void railNetworkRejectsTwoRoutesWhenOnlyOneLinkPieceRemains() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        List<Map<String, Object>> links = new java.util.ArrayList<>();
        for (int index = 0; index < 13; index++) {
            links.add(link("existing-" + index, 1L, "A", "Existing-" + index, "Existing-" + (index + 1), "rail"));
        }
        replaceLinks(state, links);

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "network", Map.of(
                "cardId", "card-a",
                "routes", List.of(
                        Map.of("from", "Birmingham", "to", "Walsall"),
                        Map.of("from", "Birmingham", "to", "Coventry")
                )
        ), List.of()));

        assertEquals(13, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void developConsumesIronAndRaisesNextBuiltIndustryLevel() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Leek", "Leek")
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("coal_mine")
        ), List.of());

        assertEquals(1, developmentValue(state, "1", "coal_mine"));
        assertEquals(15, playerStat(state, "1", "money"));
        assertEquals(6, playerBoardCount(state, "1", "coal_mine"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-b",
                "city", "Leek",
                "industryType", "coal_mine"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(2, ((Map<?, ?>) industries.get(0)).get("level"));
        assertEquals(8, playerStat(state, "1", "money"));
        assertEquals(5, playerBoardCount(state, "1", "coal_mine"));
    }

    @Test
    void developCanUsePreferredIronSource() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("iron-a", 2L, "B", "Birmingham", "iron_works", "Iron Works", 0, 1, 0, 3, 3)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("coal_mine"),
                "ironSourceTileId", "iron-a"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
    }

    @Test
    void developRejectsDepletedPreferredIronWithoutBuyingMarketIron() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("iron-a", 2L, "B", "Birmingham", "iron_works", "Iron Works", 0, 0, 0, 3, 3)
        ));
        int marketIronCount = ((List<?>) ((Map<?, ?>) state.get("market")).get("iron")).size();

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("coal_mine"),
                "ironSourceTileId", "iron-a"
        ), List.of()));

        assertEquals(0, developmentValue(state, "1", "coal_mine"));
        assertEquals(marketIronCount, ((List<?>) ((Map<?, ?>) state.get("market")).get("iron")).size());
        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void developCanBuyDistantIronWhenIronMarketEmpty() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        ((Map<String, Object>) state.get("market")).put("iron", new java.util.ArrayList<>());

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("coal_mine")
        ), List.of());

        assertEquals(1, developmentValue(state, "1", "coal_mine"));
        assertEquals(11, playerStat(state, "1", "money"));
        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("market")).get("iron")).size());
    }

    @Test
    void developCanAdvanceSameIndustryTwiceInOrder() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("iron_works", "iron_works")
        ), List.of());

        assertEquals(2, developmentValue(state, "1", "iron_works"));
        assertEquals(2, playerBoardCount(state, "1", "iron_works"));
        assertEquals(13, playerStat(state, "1", "money"));
    }

    @Test
    void developRejectsCardNotInHandWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "missing-card",
                "industryTypes", List.of("coal_mine")
        ), List.of()));

        assertEquals(1, handSize(state, "1"));
        assertEquals(0, developmentValue(state, "1", "coal_mine"));
        assertEquals(7, playerBoardCount(state, "1", "coal_mine"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void developRejectsMoreThanTwoIndustriesWithoutChangingState() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("coal_mine", "iron_works", "brewery")
        ), List.of()));

        assertEquals(1, handSize(state, "1"));
        assertEquals(0, developmentValue(state, "1", "coal_mine"));
        assertEquals(0, developmentValue(state, "1", "iron_works"));
        assertEquals(0, developmentValue(state, "1", "brewery"));
        assertEquals(17, playerStat(state, "1", "money"));
    }

    @Test
    void developFailureOnSecondIndustryDoesNotKeepFirstDevelopment() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        ((Map<String, Object>) state.get("market")).put("iron", new java.util.ArrayList<>());
        setPlayerStat(state, "1", "money", 6);

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("coal_mine", "iron_works")
        ), List.of()));

        assertEquals(1, handSize(state, "1"));
        assertEquals(0, developmentValue(state, "1", "coal_mine"));
        assertEquals(0, developmentValue(state, "1", "iron_works"));
        assertEquals(7, playerBoardCount(state, "1", "coal_mine"));
        assertEquals(4, playerBoardCount(state, "1", "iron_works"));
        assertEquals(6, playerStat(state, "1", "money"));
    }

    @Test
    void developRejectsLevelOnePotteryInsteadOfSkippingToLevelTwo() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("pottery")
        ), List.of()));

        assertEquals(0, developmentValue(state, "1", "pottery"));
        assertEquals(5, playerBoardCount(state, "1", "pottery"));
        assertEquals("pottery_level_1_1", firstPlayerBoardTileId(state, "1", "pottery"));
    }

    @Test
    void developRejectsLevelThreePotteryWhenItIsLowestRemainingTile() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        Map<String, Object> levelThreePottery = boardTile("pottery-level-3", "pottery", 3, 0);
        levelThreePottery.put("canDevelop", false);
        replacePlayerBoardTiles(state, "1", "pottery", List.of(levelThreePottery));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "develop", Map.of(
                "cardId", "card-a",
                "industryTypes", List.of("pottery")
        ), List.of()));

        assertEquals(0, developmentValue(state, "1", "pottery"));
        assertEquals(1, playerBoardCount(state, "1", "pottery"));
        assertEquals("pottery-level-3", firstPlayerBoardTileId(state, "1", "pottery"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void scoutRejectsDuplicateCardIdsWithoutChangingHand() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Leek", "Leek"),
                card("card-c", "industry", "manufacturer", "Industry: Manufacturer")
        ));
        int initialDiscardCount = ((List<?>) state.get("discardPile")).size();

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "scout", Map.of(
                "cardIds", List.of("card-a", "card-a", "card-b")
        ), List.of()));

        assertEquals(3, handSize(state, "1"));
        assertEquals(initialDiscardCount, ((List<?>) state.get("discardPile")).size());
        assertEquals(8, ((List<?>) state.get("scoutPool")).size());
    }

    @Test
    void scoutExchangesThreeCardsForOneWildLocationAndOneWildIndustry() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Leek", "Leek"),
                card("card-c", "industry", "manufacturer", "Industry: Manufacturer")
        ));
        int initialDiscardCount = ((List<?>) state.get("discardPile")).size();

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "scout", Map.of(
                "cardIds", List.of("card-a", "card-b", "card-c")
        ), List.of());

        List<?> hand = (List<?>) ((Map<?, ?>) state.get("hands")).get("1");
        assertEquals(2, hand.size());
        assertEquals(1, hand.stream().map(Map.class::cast).filter(card -> "wild_location".equals(card.get("key"))).count());
        assertEquals(1, hand.stream().map(Map.class::cast).filter(card -> "wild_industry".equals(card.get("key"))).count());
        assertEquals(6, ((List<?>) state.get("scoutPool")).size());
        assertEquals(initialDiscardCount + 3, ((List<?>) state.get("discardPile")).size());
    }

    @Test
    void scoutRejectsExhaustedScoutPoolWithoutChangingHand() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Leek", "Leek"),
                card("card-c", "industry", "manufacturer", "Industry: Manufacturer")
        ));
        state.put("scoutPool", List.of(card("wild-a", "wild_industry", "wild_industry", "Wild Industry")));
        int initialDiscardCount = ((List<?>) state.get("discardPile")).size();

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "scout", Map.of(
                "cardIds", List.of("card-a", "card-b", "card-c")
        ), List.of()));

        assertEquals(3, handSize(state, "1"));
        assertEquals(initialDiscardCount, ((List<?>) state.get("discardPile")).size());
        assertEquals(1, ((List<?>) state.get("scoutPool")).size());
    }

    @Test
    void scoutRejectsWildCardsWithoutChangingHand() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> wildIndustry = card("wild-industry", "wild_industry", "wild_industry", "Wild Industry");
        wildIndustry.put("wild", true);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Leek", "Leek"),
                wildIndustry
        ));
        int initialDiscardCount = ((List<?>) state.get("discardPile")).size();

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "scout", Map.of(
                "cardIds", List.of("card-a", "card-b", "wild-industry")
        ), List.of()));

        assertEquals(3, handSize(state, "1"));
        assertEquals(initialDiscardCount, ((List<?>) state.get("discardPile")).size());
    }

    @Test
    void scoutUnavailableWithFewerThanThreeNonWildCards() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> wildLocation = card("wild-location", "wild_location", "wild_location", "Wild Location");
        wildLocation.put("wild", true);
        replaceHand(state, "1", List.of(
                card("card-a", "location", "Birmingham", "Birmingham"),
                card("card-b", "location", "Leek", "Leek"),
                wildLocation
        ));
        state.put("availableActions", invokeAvailableActions(module, state));

        assertFalse(availableActions(state).contains("scout"));
    }

    @Test
    void canalEraRejectsRailOnlyBoardTile() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replacePlayerBoardTiles(state, "1", "manufacturer", List.of(boardTile("manufacturer_rail_only", "manufacturer", 2, 2)));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "manufacturer"
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).size());
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void failedBuildResourceConsumptionDoesNotSpendMoneyPlaceTileOrDiscardCard() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        Map<String, Object> expensiveTile = boardTile("manufacturer_needs_coal", "manufacturer", 1, 1);
        expensiveTile.put("cost", 10);
        expensiveTile.put("coalCost", 1);
        replacePlayerBoardTiles(state, "1", "manufacturer", List.of(expensiveTile));
        ((Map<String, Object>) state.get("market")).put("coal", new java.util.ArrayList<>());

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "manufacturer"
        ), List.of()));

        assertEquals(17, playerStat(state, "1", "money"));
        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).size());
        assertEquals(1, handSize(state, "1"));
        assertEquals(1, playerBoardCount(state, "1", "manufacturer"));
    }

    @Test
    void buildCoalMineSellsToConnectedMarketAndFlipsWhenEmpty() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Leek", "Leek")));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Leek", "Oxford", "canal")));
        ((Map<String, Object>) state.get("market")).put("coal", new java.util.ArrayList<>());

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Leek",
                "industryType", "coal_mine"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(2, ((List<?>) ((Map<?, ?>) state.get("market")).get("coal")).size());
        assertEquals(26, playerStat(state, "1", "money"));
        assertFalse(((List<?>) ((Map<?, ?>) state.get("market")).get("coal")).contains(0));
    }

    @Test
    void buildIronWorksSellsToMarketWithoutConnection() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceLinks(state, List.of(link("market-link", 2L, "B", "Birmingham", "Oxford", "canal")));
        ((Map<String, Object>) state.get("market")).put("iron", new java.util.ArrayList<>());

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "iron_works"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(true, ((Map<?, ?>) industries.get(0)).get("flipped"));
        assertEquals(4, ((List<?>) ((Map<?, ?>) state.get("market")).get("iron")).size());
        assertEquals(29, playerStat(state, "1", "money"));
        assertFalse(((List<?>) ((Map<?, ?>) state.get("market")).get("iron")).contains(0));

        List<String> notices = ((List<?>) state.get("notices")).stream().map(String::valueOf).toList();
        int buildIndex = firstNoticeIndex(notices, "建造了", "钢铁厂");
        int marketIndex = firstNoticeIndex(notices, "向市场卖出", "铁");
        int flipIndex = firstNoticeIndex(notices, "产能消耗完毕", "翻面");
        assertTrue(buildIndex >= 0);
        assertTrue(marketIndex >= 0);
        assertTrue(flipIndex >= 0);
        assertTrue(buildIndex < marketIndex);
        assertTrue(marketIndex < flipIndex);
    }

    @Test
    void buildUsesDedicatedIndustrySlotBeforeSharedSlotInSameCity() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceLinks(state, List.of(link("market-link", 2L, "B", "Birmingham", "Oxford", "canal")));

        Map<?, ?> available = invokeAvailableActions(module, state);
        List<?> birminghamManufacturerOptions = ((List<?>) available.get("buildOptions")).stream()
                .map(option -> (Map<?, ?>) option)
                .filter(option -> "Birmingham".equals(option.get("city")))
                .filter(option -> "manufacturer".equals(option.get("industryType")))
                .toList();
        assertTrue(birminghamManufacturerOptions.stream()
                .noneMatch(option -> ((Number) ((Map<?, ?>) option).get("slotIndex")).intValue() == 0));
        assertTrue(birminghamManufacturerOptions.stream()
                .anyMatch(option -> ((Number) ((Map<?, ?>) option).get("slotIndex")).intValue() == 1));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "manufacturer"
        ), List.of());

        Map<?, ?> builtTile = (Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).get(0);
        assertEquals("manufacturer", builtTile.get("industryType"));
        assertEquals(1, ((Number) builtTile.get("slotIndex")).intValue());
    }

    @Test
    void buildRejectsSharedIndustrySlotWhenDedicatedSlotIsAvailable() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Birmingham",
                "industryType", "manufacturer",
                "slotIndex", 0
        ), List.of()));

        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("industries")).size());
    }

    @Test
    void buildCanCoverOpponentEmptyResourceFactoryOnlyWhenMarketAndFactoriesEmpty() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Stoke-on-Trent", "Stoke-on-Trent")));
        ((Map<String, Object>) state.get("market")).put("iron", new java.util.ArrayList<>());
        replaceLinks(state, List.of(link("market-link", 2L, "B", "Stoke-on-Trent", "Warrington", "canal")));
        replaceIndustries(state, List.of(
                tileWithSlot("iron-b", 2L, "B", "Stoke-on-Trent", "iron_works", "Iron Works", 1, 0, 0, 0, 3, 3)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Stoke-on-Trent",
                "industryType", "iron_works"
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(1, industries.size());
        assertEquals(1L, ((Map<?, ?>) industries.get(0)).get("ownerId"));
    }

    @Test
    void buildCanCoverOwnFlippedLowerLevelIndustryAndRemovesOldTileFromGame() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> breweryCard = card("brewery-card", "industry", "brewery", "Industry: Brewery");
        breweryCard.put("industryTypes", List.of("brewery"));
        replaceHand(state, "1", List.of(breweryCard));
        Map<String, Object> levelThreeBrewery = boardTile("brewery-level-3", "brewery", 3, 0);
        replacePlayerBoardTiles(state, "1", "brewery", List.of(levelThreeBrewery));
        Map<String, Object> oldBrewery = tileWithSlot(
                "brewery-level-2-built", 1L, "A", "Stone", "brewery", "Brewery",
                0, 0, 0, 0, 5, 0
        );
        oldBrewery.put("level", 2);
        oldBrewery.put("flipped", true);
        replaceIndustries(state, List.of(oldBrewery));

        Map<?, ?> available = invokeAvailableActions(module, state);
        Map<?, ?> option = ((List<?>) available.get("buildOptions")).stream()
                .map(Map.class::cast)
                .filter(item -> "Stone".equals(item.get("city")) && "brewery".equals(item.get("industryType")))
                .findFirst()
                .orElseThrow();
        assertEquals(true, option.get("coversOwn"));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "brewery-card",
                "city", "Stone",
                "industryType", "brewery",
                "slotIndex", 0
        ), List.of());

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(1, industries.size());
        assertEquals(3, ((Map<?, ?>) industries.getFirst()).get("level"));
        assertFalse(industries.stream().map(Map.class::cast)
                .anyMatch(tile -> "brewery-level-2-built".equals(tile.get("id"))));
        assertEquals(0, playerBoardCount(state, "1", "brewery"));
    }

    @Test
    void buildCannotCoverOwnUnflippedOrSameLevelIndustry() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        Map<String, Object> breweryCard = card("brewery-card", "industry", "brewery", "Industry: Brewery");
        breweryCard.put("industryTypes", List.of("brewery"));
        replaceHand(state, "1", List.of(breweryCard));
        replacePlayerBoardTiles(state, "1", "brewery", List.of(boardTile("brewery-level-2", "brewery", 2, 0)));
        Map<String, Object> sameLevelBrewery = tileWithSlot(
                "brewery-level-2-built", 1L, "A", "Stone", "brewery", "Brewery",
                0, 0, 0, 0, 5, 0
        );
        sameLevelBrewery.put("level", 2);
        sameLevelBrewery.put("flipped", true);
        replaceIndustries(state, List.of(sameLevelBrewery));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState,
                new UserSummary(1, "A"), "build", Map.of(
                        "cardId", "brewery-card",
                        "city", "Stone",
                        "industryType", "brewery",
                        "slotIndex", 0
                ), List.of()));
    }

    @Test
    void availableBuildOptionsIncludeCoverableOpponentResourceFactory() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Stoke-on-Trent", "Stoke-on-Trent")));
        ((Map<String, Object>) state.get("market")).put("iron", new java.util.ArrayList<>());
        replaceLinks(state, List.of(link("market-link", 2L, "B", "Stoke-on-Trent", "Warrington", "canal")));
        replaceIndustries(state, List.of(
                tileWithSlot("iron-b", 2L, "B", "Stoke-on-Trent", "iron_works", "Iron Works", 1, 0, 0, 0, 3, 3)
        ));

        Map<?, ?> available = invokeAvailableActions(module, state);
        Map<?, ?> coverOption = ((List<?>) available.get("buildOptions")).stream()
                .map(Map.class::cast)
                .filter(option -> "Stoke-on-Trent".equals(option.get("city"))
                        && "iron_works".equals(option.get("industryType")))
                .findFirst()
                .orElseThrow();

        assertEquals(true, coverOption.get("coversOpponent"));
        assertTrue(((List<?>) coverOption.get("cardIds")).contains("card-a"));
    }

    @Test
    void buildOptionsExposeOnlyReachableCoalSourcesForTargetCity() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("coal-a", 2L, "B", "Birmingham", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3),
                tile("coal-b", 2L, "B", "Derby", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3)
        ));

        Map<?, ?> available = invokeAvailableActions(module, state);
        Map<?, ?> buildOption = ((List<?>) available.get("buildOptions")).stream()
                .map(Map.class::cast)
                .filter(option -> "Birmingham".equals(option.get("city"))
                        && "manufacturer".equals(option.get("industryType")))
                .findFirst()
                .orElseThrow();
        List<?> coalSources = (List<?>) buildOption.get("coalSources");

        assertEquals(1, coalSources.size());
        assertEquals("coal-a", ((Map<?, ?>) coalSources.get(0)).get("id"));
    }

    @Test
    void sellTilesExposeOnlyUsableBeerSourcesForSaleCity() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("own-beer", 1L, "A", "Derby", "brewery", "Brewery", 0, 0, 1, 4, 4),
                tile("connected-beer", 2L, "B", "Walsall", "brewery", "Brewery", 0, 0, 1, 4, 4),
                tile("disconnected-beer", 2L, "B", "Derby", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));
        replaceLinks(state, List.of(link("link-a", 2L, "B", "Birmingham", "Walsall", "canal")));

        Map<?, ?> available = invokeAvailableActions(module, state);
        Map<?, ?> sellTile = ((List<?>) available.get("sellTiles")).stream()
                .map(Map.class::cast)
                .filter(tile -> "cotton-a".equals(tile.get("id")))
                .findFirst()
                .orElseThrow();
        List<String> beerSourceIds = ((List<?>) sellTile.get("beerSources")).stream()
                .map(Map.class::cast)
                .map(source -> String.valueOf(source.get("id")))
                .toList();

        assertEquals(List.of("own-beer", "connected-beer"), beerSourceIds);
    }

    @Test
    void sellTilesExposeOnlyConnectedAcceptingMerchantSources() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Oxford", "canal")));
        replaceMerchant(state, "merchant_oxford", List.of("cotton_mill"), 1, false);
        replaceMerchant(state, "merchant_warrington", List.of("cotton_mill"), 1, false);
        replaceMerchant(state, "merchant_nottingham", List.of("pottery"), 1, false);

        Map<?, ?> available = invokeAvailableActions(module, state);
        Map<?, ?> sellTile = ((List<?>) available.get("sellTiles")).stream()
                .map(Map.class::cast)
                .filter(tile -> "cotton-a".equals(tile.get("id")))
                .findFirst()
                .orElseThrow();
        List<String> merchantIds = ((List<?>) sellTile.get("merchantSources")).stream()
                .map(Map.class::cast)
                .map(source -> String.valueOf(source.get("id")))
                .toList();

        assertEquals(List.of("merchant_oxford"), merchantIds);
    }

    @Test
    void railNetworkRoutesExposeCoalSourcesAfterHypotheticalLink() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceIndustries(state, List.of(
                tile("coal-a", 2L, "B", "Walsall", "coal_mine", "Coal Mine", 1, 0, 0, 4, 3)
        ));

        Map<?, ?> available = invokeAvailableActions(module, state);
        Map<?, ?> route = ((List<?>) available.get("networkRoutes")).stream()
                .map(Map.class::cast)
                .filter(item -> "Birmingham".equals(item.get("from")) && "Walsall".equals(item.get("to")))
                .findFirst()
                .orElseThrow();
        List<?> coalSources = (List<?>) route.get("coalSources");

        assertEquals(1, coalSources.size());
        assertEquals("coal-a", ((Map<?, ?>) coalSources.get(0)).get("id"));
    }

    @Test
    void railNetworkRoutePairsExposeBeerSourcesAfterBothHypotheticalLinks() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        replaceIndustries(state, List.of(
                tile("beer-a", 2L, "B", "Wolverhampton", "brewery", "Brewery", 0, 0, 1, 4, 4),
                tile("coal-a", 2L, "B", "Dudley", "coal_mine", "Coal Mine", 2, 0, 0, 4, 3)
        ));

        Map<?, ?> available = invokeAvailableActions(module, state);
        Map<?, ?> pair = ((List<?>) available.get("networkRoutePairs")).stream()
                .map(Map.class::cast)
                .filter(item -> {
                    List<?> routes = (List<?>) item.get("routes");
                    return routes.stream().map(Map.class::cast).anyMatch(route -> "Birmingham".equals(route.get("from")) && "Dudley".equals(route.get("to")))
                            && routes.stream().map(Map.class::cast).anyMatch(route -> "Dudley".equals(route.get("from")) && "Wolverhampton".equals(route.get("to")));
                })
                .findFirst()
                .orElseThrow();
        List<?> beerSources = (List<?>) pair.get("beerSources");

        assertEquals(1, beerSources.size());
        assertEquals("beer-a", ((Map<?, ?>) beerSources.get(0)).get("id"));
    }

    @Test
    void unavailableActionsAndCandidatesAreHiddenWhenMoneyOrResourcesAreInsufficient() {
        BrassGameModule module = new BrassGameModule();

        var buildState = startSecondRound(module);
        setPlayerStat(buildState, "1", "money", 0);
        Map<?, ?> buildAvailable = invokeAvailableActions(module, buildState);
        assertFalse(((List<?>) buildAvailable.get("actions")).contains("build"));
        assertTrue(((List<?>) buildAvailable.get("buildOptions")).isEmpty());

        var sellState = startSecondRound(module);
        replaceHand(sellState, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(sellState, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5)
        ));
        Map<?, ?> sellAvailable = invokeAvailableActions(module, sellState);
        assertFalse(((List<?>) sellAvailable.get("actions")).contains("sell"));
        assertTrue(((List<?>) sellAvailable.get("sellTiles")).isEmpty());

        var networkState = startSecondRound(module);
        networkState.put("era", "rail");
        setPlayerStat(networkState, "1", "money", 4);
        replaceIndustries(networkState, List.of());
        Map<?, ?> networkAvailable = invokeAvailableActions(module, networkState);
        assertFalse(((List<?>) networkAvailable.get("actions")).contains("network"));
        assertTrue(((List<?>) networkAvailable.get("networkRoutes")).isEmpty());
        assertTrue(((List<?>) networkAvailable.get("networkRoutePairs")).isEmpty());
    }

    @Test
    void sellSessionWithNoRemainingSellableIndustryOnlyOffersEndSell() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Birmingham", "Birmingham")));
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5),
                tile("beer-a", 1L, "A", "Birmingham", "brewery", "Brewery", 0, 0, 1, 4, 4)
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "sell", Map.of(
                "cardId", "card-a",
                "tileId", "cotton-a",
                "beerSourceTileId", "beer-a"
        ), List.of());

        List<?> actions = availableActions(state);
        assertFalse(actions.contains("sell"));
        assertTrue(actions.contains("end_sell"));
        assertTrue(((List<?>) ((Map<?, ?>) state.get("availableActions")).get("sellTiles")).isEmpty());
    }

    @Test
    void buildCannotCoverOpponentResourceFactoryWhenMarketHasResource() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Stoke-on-Trent", "Stoke-on-Trent")));
        replaceIndustries(state, List.of(
                tileWithSlot("iron-b", 2L, "B", "Stoke-on-Trent", "iron_works", "Iron Works", 1, 0, 0, 0, 3, 3)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Stoke-on-Trent",
                "industryType", "iron_works"
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(1, industries.size());
        assertEquals(2L, ((Map<?, ?>) industries.get(0)).get("ownerId"));
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void buildCannotCoverOpponentResourceFactoryWhenAnyFactoryHasSupply() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Stoke-on-Trent", "Stoke-on-Trent")));
        ((Map<String, Object>) state.get("market")).put("iron", new java.util.ArrayList<>());
        replaceIndustries(state, List.of(
                tileWithSlot("iron-b", 2L, "B", "Stoke-on-Trent", "iron_works", "Iron Works", 1, 0, 0, 0, 3, 3),
                tile("iron-c", 2L, "B", "Derby", "iron_works", "Iron Works", 0, 1, 0, 3, 3)
        ));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Stoke-on-Trent",
                "industryType", "iron_works"
        ), List.of()));

        List<?> industries = (List<?>) ((Map<?, ?>) state.get("board")).get("industries");
        assertEquals(2, industries.size());
        assertEquals(1, handSize(state, "1"));
    }

    @Test
    void anonymousBreweryRejectsLocationCard() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        replaceHand(state, "1", List.of(card("card-a", "location", "Brewery", "Brewery")));

        var targetState = state;
        assertThrows(RuntimeException.class, () -> module.onAction(room(), seats(), module.defaultConfig(), targetState, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "card-a",
                "city", "Brewery",
                "industryType", "brewery"
        ), List.of()));
    }

    @Test
    void endOfRoundCollectsIncomeAndOrdersByLeastSpending() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        List<?> initialTurnOrder = List.copyOf((List<?>) state.get("initialTurnOrder"));
        replaceHand(state, "1", List.of(
                card("a-build", "location", "Birmingham", "Birmingham"),
                card("a-skip", "location", "Coventry", "Coventry")
        ));
        replaceHand(state, "2", List.of(
                card("b-skip-1", "location", "Derby", "Derby"),
                card("b-skip-2", "location", "Nottingham", "Nottingham")
        ));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "build", Map.of(
                "cardId", "a-build",
                "city", "Birmingham",
                "industryType", "cotton_mill"
        ), List.of());
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", "a-skip"), List.of());
        state = endTurn(module, state, 1L, "A");
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(2, "B"), "skip", Map.of("cardId", "b-skip-1"), List.of());
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(2, "B"), "skip", Map.of("cardId", "b-skip-2"), List.of());
        state = endTurn(module, state, 2L, "B");

        assertEquals(3, state.get("round"));
        assertEquals(2L, state.get("currentPlayerId"));
        assertEquals(List.of(2L, 1L), state.get("turnOrder"));
        assertEquals(initialTurnOrder, state.get("initialTurnOrder"));
        assertEquals(0, playerStat(state, "1", "spentThisRound"));
        assertEquals(0, playerStat(state, "2", "spentThisRound"));
        assertEquals(5, playerStat(state, "1", "money"));
        assertEquals(17, playerStat(state, "2", "money"));
    }

    @Test
    void canalEraMaintenanceScoresLinksAndStartsRailEra() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        List<?> initialTurnOrder = List.copyOf((List<?>) state.get("initialTurnOrder"));
        emptyDeckAndHands(state);
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5, true)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Walsall", "canal")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "maintain_era", Map.of(), List.of());

        assertEquals("rail", state.get("era"));
        assertEquals(1, state.get("round"));
        assertEquals(initialTurnOrder, state.get("initialTurnOrder"));
        assertEquals(6, playerStat(state, "1", "victoryPoints"));
        List<?> eraScores = (List<?>) state.get("eraScores");
        assertEquals("link", ((Map<?, ?>) eraScores.get(0)).get("type"));
        assertEquals("industry", ((Map<?, ?>) eraScores.get(1)).get("type"));
        assertEquals(0, ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).size());
        assertEquals(8, handSize(state, "1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void canalEraMaintenanceRemovesAllBuiltLevelOneIndustriesIncludingPottery() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        emptyDeckAndHands(state);
        Map<String, Object> coal = tile("coal-a", 1L, "A", "Birmingham", "coal_mine", "Coal Mine", 0, 0, 0, 0, 0, true);
        Map<String, Object> iron = tile("iron-a", 1L, "A", "Birmingham", "iron_works", "Iron Works", 0, 0, 0, 0, 0, true);
        Map<String, Object> cotton = tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 0, 0, true);
        Map<String, Object> manufacturer = tile("manufacturer-a", 1L, "A", "Birmingham", "manufacturer", "Manufacturer", 0, 0, 0, 0, 0, true);
        Map<String, Object> brewery = tile("brewery-a", 1L, "A", "Birmingham", "brewery", "Brewery", 0, 0, 0, 0, 0, true);
        Map<String, Object> pottery = tile("pottery-a", 1L, "A", "Birmingham", "pottery", "Pottery", 0, 0, 0, 0, 0, true);
        Map<String, Object> levelTwoCotton = tile("cotton-b", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 0, 0, true);
        levelTwoCotton.put("level", 2);
        Map<String, Object> railBrewery = tile("brewery-b", 1L, "A", "Birmingham", "brewery", "Brewery", 0, 0, 0, 0, 0, true);
        railBrewery.put("era", "rail");
        replaceIndustries(state, List.of(coal, iron, cotton, manufacturer, brewery, pottery, levelTwoCotton, railBrewery));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "maintain_era", Map.of(), List.of());

        List<String> remainingIds = ((List<Map<String, Object>>) ((Map<String, Object>) state.get("board")).get("industries")).stream()
                .map(tile -> (String) tile.get("id"))
                .toList();
        assertFalse(remainingIds.contains("coal-a"));
        assertFalse(remainingIds.contains("iron-a"));
        assertFalse(remainingIds.contains("cotton-a"));
        assertFalse(remainingIds.contains("manufacturer-a"));
        assertFalse(remainingIds.contains("brewery-a"));
        assertFalse(remainingIds.contains("pottery-a"));
        assertTrue(remainingIds.contains("cotton-b"));
        assertTrue(remainingIds.contains("brewery-b"));
        assertEquals(5, playerBoardCount(state, "1", "pottery"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void canalEraMaintenanceRefillsSellableMerchantBeerOnce() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        emptyDeckAndHands(state);
        replaceMerchant(state, "merchant_oxford", List.of("pottery"), 0, false);
        List<Map<String, Object>> beforeMerchants = (List<Map<String, Object>>) ((Map<String, Object>) state.get("market")).get("beerMerchants");
        Map<String, Object> oxfordBefore = beforeMerchants.stream()
                .filter(merchant -> "merchant_oxford".equals(merchant.get("id")))
                .findFirst()
                .orElseThrow();
        oxfordBefore.put("providesBeer", true);
        oxfordBefore.put("used", true);
        replaceMerchant(state, "merchant_blank", List.of("cotton_mill"), 0, true);

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "maintain_era", Map.of(), List.of());

        List<Map<String, Object>> merchants = (List<Map<String, Object>>) ((Map<String, Object>) state.get("market")).get("beerMerchants");
        Map<String, Object> oxford = merchants.stream()
                .filter(merchant -> "merchant_oxford".equals(merchant.get("id")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> blank = merchants.stream()
                .filter(merchant -> "merchant_blank".equals(merchant.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals(1, oxford.get("beer"));
        assertEquals(false, oxford.get("used"));
        assertEquals(0, blank.get("beer"));
    }

    @Test
    void routeScoringUsesBuildingAndMarketRoadPoints() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        emptyDeckAndHands(state);
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5, true)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Oxford", "canal")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "maintain_era", Map.of(), List.of());

        assertEquals(8, playerStat(state, "1", "victoryPoints"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void derivedMapScoresRefreshWhenFlippedIndustriesChange() {
        BrassGameModule module = new BrassGameModule();
        var state = startPlaying(module);
        Map<String, Object> birminghamTile = tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 0, 5, true);
        birminghamTile.put("roadPoints", 2);
        Map<String, Object> coventryTile = tile("iron-b", 2L, "B", "Coventry", "iron_works", "Iron Works", 0, 0, 0, 0, 6, true);
        coventryTile.put("roadPoints", 4);
        replaceIndustries(state, List.of(birminghamTile));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Coventry", "canal")));

        invokeRefreshDerivedState(module, state);

        Map<String, Object> link = (Map<String, Object>) ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).get(0);
        assertEquals(2, link.get("currentScore"));
        assertEquals(7, playerStat(state, "1", "estimatedEraEndScore"));

        replaceIndustries(state, List.of(birminghamTile, coventryTile));
        invokeRefreshDerivedState(module, state);

        link = (Map<String, Object>) ((List<?>) ((Map<?, ?>) state.get("board")).get("links")).get(0);
        assertEquals(6, link.get("currentScore"));
        assertEquals(11, playerStat(state, "1", "estimatedEraEndScore"));
    }

    @Test
    void railEraMaintenanceFinishesGameAndFindsWinners() {
        BrassGameModule module = new BrassGameModule();
        var state = startSecondRound(module);
        state.put("era", "rail");
        emptyDeckAndHands(state);
        replaceIndustries(state, List.of(
                tile("cotton-a", 1L, "A", "Birmingham", "cotton_mill", "Cotton Mill", 0, 0, 0, 5, 5, true)
        ));
        replaceLinks(state, List.of(link("link-a", 1L, "A", "Birmingham", "Walsall", "rail")));

        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "maintain_era", Map.of(), List.of());

        assertEquals("finished", state.get("phase"));
        assertEquals(1, ((List<?>) state.get("winners")).size());
        assertEquals("A", ((Map<?, ?>) ((List<?>) state.get("winners")).get(0)).get("username"));
    }

    private RoomEntity room() {
        return new RoomEntity(
                1,
                GameType.BRASS,
                "1234",
                1,
                null,
                2,
                RoomStatus.WAITING,
                "{}",
                "{}",
                Instant.now(),
                Instant.now()
        );
    }

    private List<RoomSeat> seats() {
        return List.of(
                new RoomSeat(0, 1L, "A"),
                new RoomSeat(1, 2L, "B")
        );
    }

    private Map<String, Object> startSecondRound(BrassGameModule module) {
        var state = startPlaying(module);
        return startSecondRoundFromState(module, state);
    }

    private Map<String, Object> startSecondRoundFromState(BrassGameModule module, Map<String, Object> state) {
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(1, "A"), "skip", Map.of("cardId", firstCardId(state, "1")), List.of());
        state = endTurn(module, state, 1L, "A");
        state = module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(2, "B"), "skip", Map.of("cardId", firstCardId(state, "2")), List.of());
        return endTurn(module, state, 2L, "B");
    }

    private Map<String, Object> startPlaying(BrassGameModule module) {
        Map<String, Object> state = module.onStart(room(), seats(), module.defaultConfig());
        forceTestTurnOrder(state, List.of(1L, 2L));
        return state;
    }

    @SuppressWarnings("unchecked")
    private void forceTestTurnOrder(Map<String, Object> state, List<Long> turnOrder) {
        state.put("turnOrder", new java.util.ArrayList<>(turnOrder));
        state.put("turnIndex", 0);
        state.put("currentPlayerId", turnOrder.getFirst());
        Map<String, Object> snapshot = (Map<String, Object>) state.get("turnStartSnapshot");
        if (snapshot != null) {
            snapshot.put("turnOrder", new java.util.ArrayList<>(turnOrder));
            snapshot.put("turnIndex", 0);
            snapshot.put("currentPlayerId", turnOrder.getFirst());
        }
    }

    private Map<String, Object> endTurn(BrassGameModule module, Map<String, Object> state, long playerId, String username) {
        return module.onAction(room(), seats(), module.defaultConfig(), state, new UserSummary(playerId, username), "end_turn", Map.of(), List.of());
    }

    @SuppressWarnings("unchecked")
    private void replaceHand(Map<String, Object> state, String playerId, List<Map<String, Object>> cards) {
        ((Map<String, Object>) state.get("hands")).put(playerId, cards);
    }

    @SuppressWarnings("unchecked")
    private void replaceIndustries(Map<String, Object> state, List<Map<String, Object>> tiles) {
        ((Map<String, Object>) state.get("board")).put("industries", tiles);
    }

    @SuppressWarnings("unchecked")
    private void replaceLinks(Map<String, Object> state, List<Map<String, Object>> links) {
        ((Map<String, Object>) state.get("board")).put("links", links);
    }

    @SuppressWarnings("unchecked")
    private void replacePlayerBoardTiles(Map<String, Object> state, String playerId, String industryType,
                                         List<Map<String, Object>> tiles) {
        ((Map<String, Object>) ((Map<String, Object>) state.get("playerBoards")).get(playerId)).put(industryType, tiles);
    }

    @SuppressWarnings("unchecked")
    private void emptyDeckAndHands(Map<String, Object> state) {
        state.put("deck", List.of());
        ((Map<String, Object>) state.get("hands")).put("1", List.of());
        ((Map<String, Object>) state.get("hands")).put("2", List.of());
        state.put("eraEnding", true);
        state.put("canMaintainEra", true);
    }

    @SuppressWarnings("unchecked")
    private void replaceMerchant(Map<String, Object> state, String merchantId, List<String> acceptedIndustryTypes,
                                 int beer, boolean blank) {
        List<Map<String, Object>> merchants = (List<Map<String, Object>>) ((Map<String, Object>) state.get("market")).get("beerMerchants");
        String city = merchantCityFromId(merchantId);
        boolean replaced = false;
        for (Map<String, Object> merchant : merchants) {
            String currentId = String.valueOf(merchant.get("id"));
            String currentCity = String.valueOf(merchant.get("city"));
            if (!replaced && (merchantId.equals(currentId) || currentId.startsWith(merchantId + "_"))) {
                merchant.put("id", merchantId);
                merchant.put("city", city);
                merchant.put("acceptedIndustryTypes", acceptedIndustryTypes);
                merchant.put("beer", beer);
                merchant.put("blank", blank);
                merchant.put("providesBeer", !blank);
                merchant.put("used", beer <= 0);
                replaced = true;
            } else if (replaced && city.equals(currentCity)) {
                merchant.put("acceptedIndustryTypes", List.of());
                merchant.put("beer", 0);
                merchant.put("blank", true);
                merchant.put("providesBeer", false);
                merchant.put("used", true);
            }
        }
        if (replaced) {
            return;
        }
        Map<String, Object> merchant = new LinkedHashMap<>();
        merchant.put("id", merchantId);
        merchant.put("city", city);
        merchant.put("reward", merchantRewardFromId(merchantId));
        merchant.put("acceptedIndustryTypes", acceptedIndustryTypes);
        merchant.put("beer", beer);
        merchant.put("blank", blank);
        merchant.put("wild", false);
        merchant.put("providesBeer", !blank);
        merchant.put("used", beer <= 0);
        merchants.add(merchant);
    }

    private String merchantCityFromId(String merchantId) {
        String suffix = merchantId.replace("merchant_", "");
        return java.util.Arrays.stream(suffix.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase() + part.substring(1))
                .reduce((left, right) -> left + "-" + right)
                .orElse("");
    }

    private String merchantRewardFromId(String merchantId) {
        return switch (merchantId) {
            case "merchant_nottingham" -> "3 VP";
            case "merchant_shrewsbury" -> "4 VP";
            case "merchant_gloucester" -> "Develop 1";
            case "merchant_oxford" -> "2 IncomeLevel";
            case "merchant_warrington" -> "5鑻遍晳";
            default -> "";
        };
    }

    private Map<String, Object> card(String id, String type, String key, String name) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("type", type);
        card.put("key", key);
        card.put("name", name);
        return card;
    }

    private Map<String, Object> boardTile(String id, String industryType, int level, int period) {
        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("id", id);
        tile.put("industryType", industryType);
        tile.put("industryName", industryType);
        tile.put("level", level);
        tile.put("period", period);
        tile.put("cost", 0);
        tile.put("coal", 0);
        tile.put("iron", 0);
        tile.put("beer", 0);
        tile.put("coalCost", 0);
        tile.put("ironCost", 0);
        tile.put("saleBeerCost", 0);
        tile.put("roadPoints", 0);
        tile.put("canDevelop", true);
        tile.put("flipType", List.of("coal_mine", "iron_works", "brewery").contains(industryType) ? "deplete" : "sell");
        tile.put("incomeReward", 0);
        tile.put("victoryPoints", 0);
        return tile;
    }

    private Map<String, Object> tile(String id, long ownerId, String ownerName, String city, String industryType,
                                     String industryName, int coal, int iron, int beer, int income, int vp) {
        return tile(id, ownerId, ownerName, city, industryType, industryName, coal, iron, beer, income, vp, false);
    }

    private Map<String, Object> tile(String id, long ownerId, String ownerName, String city, String industryType,
                                     String industryName, int coal, int iron, int beer, int income, int vp,
                                     boolean flipped) {
        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("id", id);
        tile.put("ownerId", ownerId);
        tile.put("ownerName", ownerName);
        tile.put("city", city);
        tile.put("slotIndex", 0);
        tile.put("industryType", industryType);
        tile.put("industryName", industryName);
        tile.put("level", 1);
        tile.put("era", "canal");
        tile.put("flipped", flipped);
        tile.put("industryVpScored", false);
        tile.put("coal", coal);
        tile.put("iron", iron);
        tile.put("beer", beer);
        tile.put("incomeReward", income);
        tile.put("victoryPoints", vp);
        tile.put("roadPoints", 1);
        tile.put("flipType", List.of("coal_mine", "iron_works", "brewery").contains(industryType) ? "deplete" : "sell");
        return tile;
    }

    private Map<String, Object> tileWithSlot(String id, long ownerId, String ownerName, String city, String industryType,
                                             String industryName, int slotIndex, int coal, int iron, int beer,
                                             int income, int vp) {
        Map<String, Object> tile = tile(id, ownerId, ownerName, city, industryType, industryName, coal, iron, beer, income, vp);
        tile.put("slotIndex", slotIndex);
        return tile;
    }

    private Map<String, Object> link(String id, long ownerId, String ownerName, String from, String to, String type) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("id", id);
        link.put("ownerId", ownerId);
        link.put("ownerName", ownerName);
        link.put("from", from);
        link.put("to", to);
        link.put("type", type);
        return link;
    }

    private int handSize(Map<String, Object> state, String playerId) {
        return ((List<?>) ((Map<?, ?>) state.get("hands")).get(playerId)).size();
    }

    private String firstCardId(Map<String, Object> state, String playerId) {
        return String.valueOf(((Map<?, ?>) ((List<?>) ((Map<?, ?>) state.get("hands")).get(playerId)).get(0)).get("id"));
    }

    private int firstNoticeIndex(List<String> notices, String firstToken, String secondToken) {
        for (int index = 0; index < notices.size(); index++) {
            String notice = notices.get(index);
            if (notice.contains(firstToken) && notice.contains(secondToken)) {
                return index;
            }
        }
        return -1;
    }

    private int playerStat(Map<String, Object> state, String playerId, String field) {
        return ((Number) ((Map<?, ?>) ((Map<?, ?>) state.get("playerStats")).get(playerId)).get(field)).intValue();
    }

    @SuppressWarnings("unchecked")
    private void setPlayerStat(Map<String, Object> state, String playerId, String field, int value) {
        ((Map<String, Object>) ((Map<String, Object>) state.get("playerStats")).get(playerId)).put(field, value);
    }

    private int developmentValue(Map<String, Object> state, String playerId, String industryType) {
        return ((Number) ((Map<?, ?>) ((Map<?, ?>) state.get("developments")).get(playerId)).get(industryType)).intValue();
    }

    private List<?> availableActions(Map<String, Object> state) {
        return (List<?>) ((Map<?, ?>) state.get("availableActions")).get("actions");
    }

    private Map<?, ?> invokeAvailableActions(BrassGameModule module, Map<String, Object> state) {
        try {
            java.lang.reflect.Method method = BrassGameModule.class.getDeclaredMethod("availableActions", Map.class);
            method.setAccessible(true);
            return (Map<?, ?>) method.invoke(module, state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void invokeRefreshDerivedState(BrassGameModule module, Map<String, Object> state) {
        try {
            java.lang.reflect.Method method = BrassGameModule.class.getDeclaredMethod("refreshBrassDerivedState", Map.class);
            method.setAccessible(true);
            method.invoke(module, state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private int playerBoardCount(Map<String, Object> state, String playerId, String industryType) {
        return ((List<?>) ((Map<?, ?>) ((Map<?, ?>) state.get("playerBoards")).get(playerId)).get(industryType)).size();
    }

    private String firstPlayerBoardTileId(Map<String, Object> state, String playerId, String industryType) {
        return String.valueOf(((Map<?, ?>) ((List<?>) ((Map<?, ?>) ((Map<?, ?>) state.get("playerBoards")).get(playerId)).get(industryType)).get(0)).get("id"));
    }
}
