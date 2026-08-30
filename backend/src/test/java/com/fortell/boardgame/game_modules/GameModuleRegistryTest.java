package com.fortell.boardgame.game_modules;

import com.fortell.boardgame.game_modules.gobang.GobangGameModule;
import com.fortell.boardgame.game_modules.rps.RpsGameModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameModuleRegistryTest {
    @Test
    void hidesGobangFromGameListWithoutRemovingItsModule() {
        GameModuleRegistry registry = new GameModuleRegistry(List.of(
                new GobangGameModule(),
                new RpsGameModule()
        ));

        assertFalse(registry.descriptors().stream().anyMatch(game -> "gobang".equals(game.key())));
        assertTrue(registry.descriptors().stream().anyMatch(game -> "rps".equals(game.key())));
    }
}
