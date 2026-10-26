package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassCompatibilityBaselineTest {
    @Test void existingActionNamesAndResourceSelectionContractRemainExplicit() {
        assertEquals(List.of("build", "develop", "sell", "network", "loan", "scout", "skip", "end_sell", "end_turn", "restart_turn", "resolve_income_debt", "maintain_era"),
                Arrays.stream(BrassActionType.values()).map(type -> type.key).toList());
        var canonical = BrassResourceSelectionService.parseResourceSelections(Map.of("resourceSelections", List.of(Map.of(
                "type", "coal", "sourceType", "industry", "sourceId", "existing-tile-id", "requirementIndex", 0, "amount", 1))));
        var alias = BrassResourceSelectionService.parseResourceSelections(Map.of("resourceSelections", List.of(Map.of(
                "resourceType", "coal", "sourceType", "industry", "sourceId", "existing-tile-id", "amount", 1))));
        assertEquals(canonical, alias);
        List<BrassResourceModel.ResourceRequirement> required = List.of(new BrassResourceModel.ResourceRequirement("coal", 1, List.of("Birmingham"), null, ""));
        assertThrows(com.fortell.boardgame.models.ApiException.class, () -> BrassResourceSelectionService.requireResourceSelectionContract(
                Map.of(), Map.of(), required, BrassExecutionMode.EXECUTE));
        assertDoesNotThrow(() -> BrassResourceSelectionService.requireResourceSelectionContract(Map.of(), Map.of(), List.of(), BrassExecutionMode.EXECUTE));
        for (String field : List.of("coalSourceTileId", "ironSourceTileIds", "beerSourceTileId")) {
            assertThrows(com.fortell.boardgame.models.ApiException.class, () -> BrassResourceSelectionService.requireResourceSelectionContract(
                    Map.of(), Map.of(field, "old-id"), required, BrassExecutionMode.EXECUTE));
        }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures#scenarios")
    void fixedSavesRetainQueriesCommandsEventsAndViewerPrivacyAfterJsonReload(Scenario scenario) throws Exception {
        Map<String, Object> original = scenario.state(), restored = reload(original);
        assertJsonEquals(original, restored);
        assertJsonEquals(BrassActionQueryService.availableActions(original), BrassActionQueryService.availableActions(restored));
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("query", restored.get("availableActions"));
        List<Object> transitions = new ArrayList<>();
        String businessAction = null;
        for (Map<String, Object> command : scenario.commands()) {
            String before = JSON.writeValueAsString(original);
            var expected = act(original, command);
            var actual = act(restored, command);
            assertEquals(before, JSON.writeValueAsString(original), "Action must not mutate its input");
            assertJsonEquals(expected.state(), actual.state());
            assertJsonEquals(expected.event(), actual.event());
            assertEquals(expected.message(), actual.message());
            assertEquals(command.get("type"), actual.event().get("type"));
            assertEquals(1, actual.event().get("schemaVersion"));
            assertEquals(asLong(original.get("version")) + 1, actual.event().get("afterVersion"));
            assertSubset(mapOf(command.get("expected")), actual.state());
            transitions.add(Map.of("event", actual.event(), "message", actual.message()));
            if (scenario.name().equals("selling")) {
                String group = (String) actual.event().get("businessActionId");
                if (businessAction == null) businessAction = group;
                assertEquals(businessAction, group);
                assertEquals(8, parseInteger(viewMap(actual.state().get("turn")).get("sellActionStartVersion"), 8));
            }
            original = expected.state(); restored = reload(actual.state());
        }
        contract.put("transitions", transitions);
        contract.put("finalState", restored);
        List<Object> viewers = new ArrayList<>();
        for (long viewer : List.of(1L, 2L, 99L)) {
            Map<String, Object> visible = MODULE.stateForViewer(restored, new UserSummary(viewer, "Viewer"));
            viewers.add(visible);
            assertFalse(visible.containsKey("turnStartSnapshot"));
            viewMap(visible.get("hands")).forEach((id, hand) -> {
                if (!id.equals(String.valueOf(viewer))) assertTrue(listOf(hand).stream().allMatch(card -> card.equals(Map.of("hidden", true))));
            });
        }
        contract.put("viewers", viewers);
        String actualDigest = digest(contract);
        if (Boolean.getBoolean("brass.compatibility.printHashes")) {
            System.out.println("BRASS_COMPATIBILITY_DIGEST " + scenario.name() + "=" + actualDigest);
        } else {
            try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/brass/compatibility/golden-digests.json"))) {
                Map<?, ?> golden = JSON.readValue(input, Map.class);
                assertEquals(golden.get(scenario.name()), actualDigest, "Fixed contract changed; investigate the structured difference before updating a golden digest");
            }
        }
    }
}
