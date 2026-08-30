package com.fortell.boardgame.game_modules;

import com.fortell.boardgame.models.GameDescriptor;
import com.fortell.boardgame.models.GameType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class GameModuleRegistry {
    private final Map<GameType, GameModule> modules = new EnumMap<>(GameType.class);

    public GameModuleRegistry(List<GameModule> discoveredModules) {
        for (GameModule module : discoveredModules) {
            modules.put(module.gameType(), module);
        }
    }

    public GameModule get(GameType gameType) {
        GameModule module = modules.get(gameType);
        if (module == null) {
            throw new IllegalArgumentException("Missing game module for " + gameType);
        }
        return module;
    }

    public List<GameDescriptor> descriptors() {
        return modules.values().stream()
                .filter(module -> module.gameType() != GameType.GOBANG)
                .map(GameModule::descriptor)
                .sorted((left, right) -> left.path().compareTo(right.path()))
                .toList();
    }
}
