package com.dim201.dim201addon.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * 恶魔契约的代价。
 *
 * <p>抽取规则（见 {@link DemonContractManager#drawCosts}）：
 * <ul>
 *   <li>优先抽取永久烙印，剩余点数用即时代价补足</li>
 *   <li>同 {@code group} 的代价一场契约中只抽一次（三档扣金币互斥）</li>
 *   <li>已烙印的永久代价不会再次出现</li>
 * </ul>
 */
public enum ContractCost {

    // ---------- 即时代价（签订瞬间结算） ----------
    GOLD_50("gold_50", Kind.INSTANT, "gold", 1, "吐出 50 金币"),
    GOLD_100("gold_100", Kind.INSTANT, "gold", 2, "吐出 100 金币"),
    GOLD_150("gold_150", Kind.INSTANT, "gold", 3, "吐出 150 金币"),
    MIND_CRASH("mind_crash", Kind.INSTANT, "mind", 2, "理智崩溃（san 立即清零）"),
    DEMON_GAZE("demon_gaze", Kind.INSTANT, "glow", 2, "恶魔注视（全图发光 10 秒）"),
    BLIND_SHORT("blind_short", Kind.INSTANT, "blind", 2, "短暂盲目（失明 30 秒）"),
    EXHAUSTION("exhaustion", Kind.INSTANT, "weak", 1, "虚脱（缓慢与挖掘疲劳 30 秒）"),

    // ---------- 永久烙印（本局持续） ----------
    FRAIL_MIND("frail_mind", Kind.PERMANENT, "frail", 2, "脆弱心智（san 上限永久 -20%）"),
    FOG_CURSE("fog_curse", Kind.PERMANENT, "fog", 4, "迷雾诅咒（永久失明）"),
    BOUNTY("bounty", Kind.PERMANENT, "bounty", 4, "恶魔悬赏（杀手击杀你额外获得 100 金币，你的轮廓在杀手眼中变为金色）"),
    DEMON_FEVER("demon_fever", Kind.PERMANENT, "fever", 3, "恶魔低烧（每 60 秒发作一次：反胃与虚弱 8 秒）"),
    INTEREST("interest", Kind.PERMANENT, "interest", 3, "恶魔利息（每 60 秒损失 50 金币）"),
    SLOW_FEET("slow_feet", Kind.PERMANENT, "slow", 3, "滞重（移动速度永久 -10%）"),
    EXPOSURE("exposure", Kind.PERMANENT, "exposure", 3, "周期性暴露（每 60 秒全图发光 5 秒）"),
    BLOOD_OATH("blood_oath", Kind.PERMANENT, "oath", 2, "血誓（下一次契约的代价倍率额外 ×1.5）");

    public enum Kind {
        /** 即时：签订瞬间一次性结算 */
        INSTANT,
        /** 永久：烙印持续整局 */
        PERMANENT
    }

    private final String id;
    private final Kind kind;
    private final String group;
    private final int points;
    private final String displayName;

    ContractCost(String id, Kind kind, String group, int points, String displayName) {
        this.id = id;
        this.kind = kind;
        this.group = group;
        this.points = points;
        this.displayName = displayName;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public String group() { return group; }
    public int points() { return points; }
    public String displayName() { return displayName; }

    /** 按 id 反查，非法 id 返回 null */
    public static ContractCost byId(String id) {
        for (ContractCost c : values()) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    /** 永久烙印列表（抽取代价时优先池） */
    public static List<ContractCost> permanents() {
        List<ContractCost> list = new ArrayList<>();
        for (ContractCost c : values()) {
            if (c.kind == Kind.PERMANENT) list.add(c);
        }
        return list;
    }

    /** 即时代价列表（补足点数池） */
    public static List<ContractCost> instants() {
        List<ContractCost> list = new ArrayList<>();
        for (ContractCost c : values()) {
            if (c.kind == Kind.INSTANT) list.add(c);
        }
        return list;
    }
}
