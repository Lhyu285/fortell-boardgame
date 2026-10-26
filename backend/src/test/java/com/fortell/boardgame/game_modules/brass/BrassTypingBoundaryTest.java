package com.fortell.boardgame.game_modules.brass;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BrassTypingBoundaryTest {
    @Test void typedRulesHaveOnlyScopedAdaptersAndModelsHaveNoUntypedBusinessMaps() throws Exception {
        Path root = Path.of("src/main/java/com/fortell/boardgame/game_modules/brass");
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Set<String> migrated = Set.of("BrassPersonalAdapter.java", "BrassTypedCards.java", "BrassTypedPlayers.java", "BrassTypedSupply.java", "BrassSetupData.java", "BrassCardService.java",
                        "BrassBoardAdapter.java", "BrassTypedConnectivity.java", "BrassTypedIndustries.java",
                        "BrassResourceModel.java", "BrassResourceAdapter.java", "BrassTypedResources.java",
                        "BrassActionAdapter.java", "BrassTypedBuild.java", "BrassTypedSale.java", "BrassTypedNetwork.java",
                        "BrassFlowModel.java", "BrassFlowAdapter.java", "BrassTypedTurns.java", "BrassTypedDebt.java",
                        "BrassTypedIncome.java", "BrassTypedEra.java", "BrassTypedScoring.java", "BrassTypedRestart.java",
                        "BrassQueryModel.java", "BrassTypedQueries.java", "BrassQueryAdapter.java");
                if (!migrated.contains(file.getFileName().toString())) {
                    assertFalse(source.contains("brass.codec.") || source.contains("brass.model."), "Service outside the current migration must not switch models: " + file);
                }
                if (file.getFileName().toString().startsWith("BrassTyped")) {
                    assertFalse(source.matches("(?s).*Map\\s*<\\s*String\\s*,\\s*Object\\s*>.*"));
                    assertFalse(source.contains("BrassPersonalAdapter") || source.contains("BrassBoardAdapter") || source.contains("BrassResourceAdapter")
                            || source.contains("BrassActionAdapter") || source.contains("BrassFlowAdapter") || source.contains("BrassTurnSnapshotAdapter") || source.contains("BrassJsonCodec")
                            || source.contains("BrassQueryAdapter") || source.contains("BrassSimulationProbe") || source.contains("BrassViewerProjection") || source.contains("BrassEventAdapter")
                            || source.contains("BrassActionQueryService") || source.contains("BrassActionExecutor"));
                }
            }
        }
        try (var files = Files.list(root.resolve("model"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.matches("(?s).*Map\\s*<\\s*String\\s*,\\s*Object\\s*>.*"));
                assertFalse(source.contains("BrassSaveCodec") || source.contains("BrassGameModule"));
            }
        }
        String codec = Files.readString(root.resolve("codec/BrassSaveCodec.java"));
        assertFalse(codec.contains("BrassActionExecutor") || codec.contains("RoomRepository") || codec.contains("BrassStateProjection"));
        assertFalse(Files.readString(root.resolve("BrassResourceModel.java")).matches("(?s).*Map\\s*<\\s*String\\s*,\\s*Object\\s*>.*"));
        assertFalse(Files.readString(root.resolve("BrassFlowModel.java")).matches("(?s).*Map\\s*<\\s*String\\s*,\\s*Object\\s*>.*"));
        for (String name : Set.of("BrassGameState", "BrassTurnState", "BrassQueryModel", "BrassEventModel", "BrassProjectionModel")) {
            String model = Files.readString(root.resolve(name + ".java"));
            assertFalse(model.matches("(?s).*Map\\s*<\\s*String\\s*,\\s*Object\\s*>.*"), name);
            assertFalse(model.contains("BrassJsonCodec") || model.contains("Adapter"), name);
        }
        assertFalse(Files.readString(root.resolve("BrassActionExecutor.java")).contains("availableActions(state)"));
        String probe = Files.readString(root.resolve("BrassSimulationProbe.java"));
        assertFalse(probe.contains("BrassActionExecutor") || probe.contains("BrassActionHistory") || probe.contains("BrassStateProjection"));
    }
}
