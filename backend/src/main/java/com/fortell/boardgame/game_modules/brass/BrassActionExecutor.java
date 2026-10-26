package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Orchestrates one operation. Rule handlers never call this executor or its queries. */
final class BrassActionExecutor {
    private BrassActionExecutor() {}

    static Map<String, Object> execute(Map<String, Object> original, UserSummary actor, BrassActionCommand command) {
        Map<String, Object> state = BrassStateDocument.executionCopy(original);
        ensureActor(BrassFlowAdapter.gameState(state), actor);
        ensureActionAvailable(state, command.type());
        state = dispatch(state, actor, command, BrassExecutionMode.EXECUTE);
        BrassStateProjection.updateMaintenanceFlag(state);
        return state;
    }

    private static Map<String, Object> dispatch(Map<String, Object> state, UserSummary actor,
                                                BrassActionCommand command, BrassExecutionMode mode) {
        return switch (command.type()) {
            case RESTART_TURN -> BrassTurnService.restartTurn(state, actor);
            case END_TURN -> BrassTurnService.endTurnAction(state, actor);
            case SKIP -> BrassSkipAction.skipAction(state, actor, command);
            case LOAN -> BrassLoanAction.loanAction(state, actor, command);
            case SCOUT -> BrassScoutAction.scoutAction(state, actor, command);
            case BUILD -> BrassBuildAction.buildAction(state, actor, command, mode);
            case SELL -> BrassSellAction.sellAction(state, actor, command, mode);
            case END_SELL -> BrassSellAction.endSellAction(state, actor);
            case NETWORK -> BrassNetworkAction.networkAction(state, actor, command, mode);
            case DEVELOP -> BrassDevelopAction.developAction(state, actor, command, mode);
            case RESOLVE_INCOME_DEBT -> BrassTurnService.resolveIncomeDebtAction(state, actor, (BrassActionInput.Debt) command.input());
            case MAINTAIN_ERA -> BrassEraService.maintainEraAction(state, actor);
        };
    }

    private static void ensureActionAvailable(Map<String, Object> state, BrassActionType type) {
        var available = BrassActionQueryService.query(state);
        if (!available.actions().contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前不能执行" + type.label + "行动，请检查行动次数、金钱、资源及连通性");
        }
    }

    private static void ensureActor(BrassGameState state, UserSummary actor) {
        if (state.phase() == BrassGameState.Phase.WAITING || state.phase() == BrassGameState.Phase.FINISHED)
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前不在游戏进行阶段");
        if (state.currentPlayerId() != actor.id()) throw new ApiException(HttpStatus.BAD_REQUEST, "当前不是你的回合");
    }
}
