package com.fortell.boardgame.game_modules.brass;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BrassOptimizationBaselineTest {
    @Test
    void openingQueryMatchesFixedSnapshotWithoutMutatingState() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BrassGameModule module = new BrassGameModule();
        String fixturePath = System.getProperty("brass.baseline.fixture");
        Map<String, Object> state;
        if (fixturePath != null && Files.exists(Path.of(fixturePath))) {
            state = mapper.readValue(Files.readString(Path.of(fixturePath)), new TypeReference<>() {});
        } else {
            try (var fixture = getClass().getResourceAsStream("/brass/opening-query-baseline.json")) {
                assertNotNull(fixture, "Fixed opening baseline must be packaged with the tests");
                state = mapper.readValue(fixture, new TypeReference<>() {});
            }
        }
        String before = mapper.writeValueAsString(state);
        var query = BrassActionQueryService.class.getDeclaredMethod("availableActions", Map.class);
        query.setAccessible(true);
        double elapsedMillis = measure(query, module, state);
        Object actual = query.invoke(module, state);
        assertEquals(mapper.writeValueAsString(state.get("availableActions")), mapper.writeValueAsString(actual));
        assertEquals(before, mapper.writeValueAsString(state));
        assertFalse(before.contains("_candidateSimulation"));
        System.out.printf("BRASS_QUERY_PROFILE averageMs=%.3f stateBytes=%d%n", elapsedMillis, before.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        String reference = System.getProperty("brass.reference.source");
        if (reference != null) compareReference(reference, state, query, module, mapper);
    }

    private double measure(java.lang.reflect.Method query, Object module, Map<String, Object> state) throws Exception {
        for (int i = 0; i < 50; i++) query.invoke(module, state);
        double[] times = new double[5];
        for (int batch = 0; batch < times.length; batch++) {
            long start = System.nanoTime();
            for (int i = 0; i < 30; i++) query.invoke(module, state);
            times[batch] = (System.nanoTime() - start) / 1_000_000.0 / 30;
        }
        java.util.Arrays.sort(times);
        return times[times.length / 2];
    }

    private void compareReference(String reference, Map<String, Object> state, java.lang.reflect.Method currentQuery,
                                  Object currentModule, ObjectMapper mapper) throws Exception {
        Path output = Files.createTempDirectory("brass-reference-classes-");
        String className = BrassGameModule.class.getName();
        var source = new javax.tools.SimpleJavaFileObject(java.net.URI.create("string:///" + className.replace('.', '/') + ".java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) throws java.io.IOException { return Files.readString(Path.of(reference)); }
        };
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        try (var manager = compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            assertTrue(compiler.getTask(null, manager, null, List.of("--release", "21", "-classpath", System.getProperty("java.class.path"), "-d", output.toString()), null, List.of(source)).call());
        }
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(className) || name.startsWith(className + "$")) {
                    Class<?> existing = findLoadedClass(name);
                    Class<?> type = existing == null ? findClass(name) : existing;
                    if (resolve) resolveClass(type);
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> type = loader.loadClass(className);
            Object legacy = type.getConstructor().newInstance();
            var query = type.getDeclaredMethod("availableActions", Map.class); query.setAccessible(true);
            assertEquals(mapper.writeValueAsString(query.invoke(legacy, state)), mapper.writeValueAsString(currentQuery.invoke(currentModule, state)));
            double original = measure(query, legacy, state), optimized = measure(currentQuery, currentModule, state);
            System.out.printf("BRASS_REFERENCE_PROFILE originalMedianMs=%.3f optimizedMedianMs=%.3f%n", original, optimized);
        }
    }
}
