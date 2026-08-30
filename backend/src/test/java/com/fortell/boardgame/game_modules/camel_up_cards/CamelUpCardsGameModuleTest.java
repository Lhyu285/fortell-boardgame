package com.fortell.boardgame.game_modules.camel_up_cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.RoomStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CamelUpCardsGameModuleTest {

    @Test
    void startsIntoSetupSelectionAndSerializesState() throws Exception {
        CamelUpCardsGameModule module = new CamelUpCardsGameModule(new ObjectMapper());
        RoomEntity room = new RoomEntity(
                1,
                GameType.CAMEL_UP_CARDS,
                "1234",
                1,
                null,
                4,
                RoomStatus.WAITING,
                "{}",
                "{}",
                Instant.now(),
                Instant.now()
        );
        List<RoomSeat> seats = List.of(
                new RoomSeat(0, 1L, "A"),
                new RoomSeat(1, 2L, "B"),
                new RoomSeat(2, 3L, "C"),
                new RoomSeat(3, 4L, "D")
        );

        var state = module.onStart(room, seats, module.defaultConfig());

        assertEquals("SETUP_SELECTION", state.get("phase"));
        assertNotNull(state.get("setup"));
        new ObjectMapper().writeValueAsString(state);
    }
}
