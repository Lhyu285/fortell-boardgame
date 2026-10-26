package com.fortell.boardgame.game_modules.brass;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class BrassArchitectureTest {
    @Test void domainDependenciesHaveNoCyclesAndQueriesAreIsolated() throws Exception {
        Path root = Path.of("src/main/java/com/fortell/boardgame/game_modules/brass");
        Map<String, String> sources = new TreeMap<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList())
                sources.put(file.getFileName().toString().replace(".java", ""), Files.readString(file));
        }
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (var entry : sources.entrySet()) {
            String code = entry.getValue().replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*", "").replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "");
            Set<String> dependencies = new TreeSet<>();
            for (String name : sources.keySet()) if (!name.equals(entry.getKey()) && Pattern.compile("\\b" + name + "\\b").matcher(code).find()) dependencies.add(name);
            graph.put(entry.getKey(), dependencies);
        }
        for (String name : graph.keySet()) visit(name, graph, new HashSet<>(), new ArrayList<>());
        assertFalse(sources.get("BrassActionQueryService").contains("_candidateSimulation"));
        assertFalse(sources.get("BrassActionQueryService").contains("updateMaintenanceFlag("));
        assertFalse(sources.get("BrassGameModule").contains("private static final"));
        assertFalse(sources.values().stream().anyMatch(source -> source.contains("finishRoundLegacy(")));
        assertFalse(sources.values().stream().anyMatch(source -> source.contains("simulateRuleAction(")));
        assertFalse(sources.values().stream().anyMatch(source -> source.contains("boardWithHypotheticalLinks(")));
    }
    private void visit(String name, Map<String, Set<String>> graph, Set<String> visited, List<String> path) {
        assertFalse(path.contains(name), () -> "Cyclic dependencies: " + path + " -> " + name);
        if (!visited.add(name)) return;
        List<String> next = new ArrayList<>(path); next.add(name);
        for (String dependency : graph.get(name)) visit(dependency, graph, visited, next);
    }
}
