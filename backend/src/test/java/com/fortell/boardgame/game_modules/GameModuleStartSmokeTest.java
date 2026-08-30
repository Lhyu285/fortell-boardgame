package com.fortell.boardgame.game_modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.game_modules.camel_up_cards.CamelUpCardsGameModule;
import com.fortell.boardgame.game_modules.rps.RpsGameModule;
import com.fortell.boardgame.game_modules.thingsInRings.ThingsInRingsGameModule;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.RoomStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameModuleStartSmokeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void startsRpsThingsAndCamelWithSerializableState() throws Exception {
        List<GameModule> modules = List.of(
                new RpsGameModule(),
                new ThingsInRingsGameModule(objectMapper),
                new CamelUpCardsGameModule(objectMapper)
        );

        for (GameModule module : modules) {
            RoomEntity room = room(module.gameType());
            List<RoomSeat> seats = seats(module.gameType().defaultSeats());
            Map<String, Object> config = module.defaultConfig();

            module.validateCanStart(room, seats);
            Map<String, Object> state = module.onStart(room, seats, config);

            assertNotNull(state.get("phase"), module.gameType().path());
            objectMapper.writeValueAsString(state);
        }
    }

    private RoomEntity room(GameType gameType) {
        return new RoomEntity(
                1,
                gameType,
                "1234",
                1,
                null,
                gameType.defaultSeats(),
                RoomStatus.WAITING,
                "{}",
                "{}",
                Instant.now(),
                Instant.now()
        );
    }

    private List<RoomSeat> seats(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new RoomSeat(index, (long) index + 1, "P" + (index + 1)))
                .toList();
    }
}
