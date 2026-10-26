package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.GameTransitionResult;
import com.fortell.boardgame.game_modules.TransitionGameModule;
import com.fortell.boardgame.models.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Platform adapter; rule execution, projection and audit records have dedicated owners. */
@Component
public class BrassGameModule implements TransitionGameModule {
    @Override
    public GameType gameType() { return GameType.BRASS; }
    @Override
    public GameDescriptor descriptor() {
        return new GameDescriptor("brass", "工业革命：伯明翰", "Brass: Birmingham", "/brass", "/brass/rule", 2, 4, "3~4", "BGG 高分德式游戏");
    }
    @Override
    public Map<String, Object> defaultConfig() { return new LinkedHashMap<>(Map.of("variant", "standard")); }
    @Override
    public Map<String, Object> sanitizeConfig(Map<String, Object> requested, RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> merged = defaultConfig();
        if (requested != null) merged.putAll(requested);
        return merged;
    }
    @Override
    public void validateCanStart(RoomEntity room, List<RoomSeat> seats) {
        int seated = BrassSetupData.playersOf(seats).size();
        if (seated < gameType().minPlayers()) throw new ApiException(HttpStatus.BAD_REQUEST, "人数不足");
        if (seated > gameType().maxPlayers()) throw new ApiException(HttpStatus.BAD_REQUEST, "人数过多");
    }
    @Override
    public Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
        return BrassSetupService.initialState(room, seats);
    }
    @Override
    public Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config) {
        Map<String, Object> state = BrassSetupService.onStart(room, seats, config);
        BrassStateProjection.updateMaintenanceFlag(state);
        return state;
    }
    @Override
    public Map<String, Object> stateForViewer(Map<String, Object> state, UserSummary viewer) {
        return BrassViewerProjection.forViewer(state, viewer);
    }
    @Override
    public GameTransitionResult onTransition(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                                       Map<String, Object> state, UserSummary actor, String actionType,
                                                       Map<String, Object> payload, List<String> notices) {
        BrassActionCommand command = BrassActionCommand.decode(actionType, payload);
        return BrassActionHistory.record(state, BrassActionExecutor.execute(state, actor, command), actor, command);
    }
    @Override
    public List<String> rules() {
        return List.of("每回合执行两次主要行动，运河时代第一回合仅执行一次行动。",
                "建造、出售、研发、运输网、贷款和侦查都由后端校验规则。", "玩家可以在回合结束抽牌前重新开始本回合。");
    }
}
