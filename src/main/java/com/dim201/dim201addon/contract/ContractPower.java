package com.dim201.dim201addon.contract;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 恶魔契约可索取的力量（全部为信息/生存/机动/经济类，无进攻能力）。
 *
 * <p>{@code value} 是隐藏价值：玩家在契约 UI 中看不到，
 * 总代价 = Σvalue × 贪婪倍率 × 随机浮动(0.8~1.2)。
 */
public enum ContractPower {

    WHISPER("whisper", 3,
            "死者低语", "获知死者击杀者的身份，以及其名字中的一个字"),
    DANGER_SENSE("danger_sense", 3,
            "危险感知", "透视 15 格内所有玩家的轮廓（不区分阵营）"),
    NIGHT_VISION("night_vision", 2,
            "夜视之瞳", "获得永久夜视"),
    SHADOW_STEP("shadow_step", 3,
            "暗影步", "移动速度永久 +15%"),
    SILENCE("silence", 3,
            "无声者", "不会被任何直觉、透视或高亮效果标记"),
    IRON_SKIN("iron_skin", 5,
            "硬化皮肤", "获得一层护盾（被击破后不再恢复）"),
    IMMORTAL("immortal", 6,
            "不灭", "死亡时原地复活一次（一次性）"),
    FORTUNE("fortune", 2,
            "财富之手", "完成任务额外获得 15 金币"),
    DASH("dash", 2,
            "冲刺", "按技能键：向前瞬移 5 格，60 秒冷却"),
    CLARITY("clarity", 2,
            "清心", "免疫一切理智值（san）降低效果");

    private final String id;
    private final int value;
    private final String displayName;
    private final String description;

    ContractPower(String id, int value, String displayName, String description) {
        this.id = id;
        this.value = value;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() { return id; }
    public int value() { return value; }
    public String displayName() { return displayName; }
    public String description() { return description; }

    /** 本地化名称（带紫色力量前缀） */
    public MutableComponent styledName() {
        return Component.literal("§d" + displayName);
    }

    /** 按 id 反查，非法 id 返回 null */
    public static ContractPower byId(String id) {
        for (ContractPower p : values()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }
}
