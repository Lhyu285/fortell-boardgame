package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.objectList;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Record Support rules; all working state is passed explicitly. */
final class BrassRecordSupport {
    private BrassRecordSupport() {}

    static void appendNotice(Map<String, Object> state, String notice) {
        if (BrassStateDocument.isSimulation(state)) return;
        List<String> notices = new ArrayList<>();
        for (Object item : objectList(state.get("notices"))) {
            notices.add(String.valueOf(item));
        }
        notices.add(0, notice);
        state.put("notices", notices.stream().limit(MAX_NOTICE_COUNT).toList());
    }

    static void appendResourceNotices(Map<String, Object> state, List<String> notices) {
        for (int index = notices.size() - 1; index >= 0; index--) {
            String notice = notices.get(index);
            if (notice != null && !notice.isBlank()) {
                appendNotice(state, notice);
            }
        }
    }

    static String resourceCnName(String type) {
        return switch (type) {
            case "coal" -> "煤";
            case "iron" -> "铁";
            case "beer" -> "酒";
            default -> type;
        };
    }

    static void describeOperation(Map<String, Object> state, UserSummary actor, String type, String summary) {
        if (state instanceof BrassStateDocument document) document.describeOperation(summary);
    }
}
