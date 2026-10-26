package com.fortell.boardgame.game_modules;

import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.UserSummary;
import java.util.List;
import java.util.Map;

/** Optional structured transition capability; existing modules keep their interface. */
public interface TransitionGameModule extends VersionedGameModule {
    GameTransitionResult onTransition(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                      Map<String, Object> state, UserSummary actor, String actionType,
                                      Map<String, Object> payload, List<String> notices);

    @Override
    default Map<String, Object> onAction(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                         Map<String, Object> state, UserSummary actor, String actionType,
                                         Map<String, Object> payload, List<String> notices) {
        return onTransition(room, seats, config, state, actor, actionType, payload, notices).state();
    }
}
