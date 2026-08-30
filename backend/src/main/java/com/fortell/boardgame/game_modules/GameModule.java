package com.fortell.boardgame.game_modules;

import com.fortell.boardgame.models.GameDescriptor;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.UserSummary;

import java.util.List;
import java.util.Map;

public interface GameModule {

    GameType gameType();

    GameDescriptor descriptor();

    Map<String, Object> defaultConfig();

    Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats);

    Map<String, Object> sanitizeConfig(Map<String, Object> requested, RoomEntity room, List<RoomSeat> seats);

    void validateCanStart(RoomEntity room, List<RoomSeat> seats);

    Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config);

    Map<String, Object> onAction(
            RoomEntity room,
            List<RoomSeat> seats,
            Map<String, Object> config,
            Map<String, Object> state,
            UserSummary actor,
            String actionType,
            Map<String, Object> payload,
            List<String> notices
    );

    List<String> rules();
}
