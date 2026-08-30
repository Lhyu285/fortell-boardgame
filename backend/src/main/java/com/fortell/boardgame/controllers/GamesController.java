package com.fortell.boardgame.controllers;

import com.fortell.boardgame.game_modules.GameModuleRegistry;
import com.fortell.boardgame.models.GameDescriptor;
import com.fortell.boardgame.models.GameType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GamesController {
    private final GameModuleRegistry gameModuleRegistry;

    public GamesController(GameModuleRegistry gameModuleRegistry) {
        this.gameModuleRegistry = gameModuleRegistry;
    }

    @GetMapping
    public List<GameDescriptor> listGames() {
        return gameModuleRegistry.descriptors();
    }

    @GetMapping(value = "/{gameType}/rule", produces = "text/markdown;charset=UTF-8")
    public String gameRule(@PathVariable String gameType) {
        GameType type = GameType.fromPath(gameType);
        Path rulePath = Path.of(
                "src",
                "main",
                "java",
                "com",
                "fortell",
                "boardgame",
                "game_modules",
                type.path(),
                type.displayName() + "-游戏规则.md"
        );
        if (!Files.exists(rulePath)) {
            return "";
        }
        try {
            return Files.readString(rulePath, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }
}
