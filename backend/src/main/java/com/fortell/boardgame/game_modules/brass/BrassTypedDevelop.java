package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import java.util.List;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

final class BrassTypedDevelop {
    private BrassTypedDevelop() {}
    record Plan(List<String> industryTypes, List<ResourceRequirement> requirements) {
        Plan { industryTypes = List.copyOf(industryTypes); requirements = List.copyOf(requirements); }
    }
    static Plan plan(List<String> requested, List<String> allowed) {
        var types = requested.stream().filter(type -> !type.isBlank()).toList();
        if (types.isEmpty() || types.size() > 2) throw new ApiException(HttpStatus.BAD_REQUEST, "研发行动需要选择1到2个板块");
        for (String type : types) if (!allowed.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "产业不合法");
        return new Plan(types, List.of(new ResourceRequirement("iron", types.size(), List.of(), null, "")));
    }
}
