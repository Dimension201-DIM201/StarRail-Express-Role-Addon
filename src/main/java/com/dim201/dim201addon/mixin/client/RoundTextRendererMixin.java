package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.TeacherZhangRole;
import com.dim201.dim201addon.role.TeacherZhangAllGameMode;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleAnnouncementTexts;
import io.wifi.starrailexpress.client.gui.RoundTextRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/**
 * 拦截欢迎界面的前提文本：
 * 仅在全员张老师模式中，将默认的"快车上有 X 名杀手"替换为自定义文本。
 * 模仿亡命徒模式通过 isLooseEnds 走固定文本分支的实现方式。
 */
@Mixin(RoundTextRenderer.class)
public class RoundTextRendererMixin {

    @Shadow
    private static RoleAnnouncementTexts.RoleAnnouncementText roleTexts;

    /**
     * 重定向 premiseText.apply(killers) 调用（ordinal=0 对应第一个 Function.apply）。
     * 仅当游戏模式是全员张老师模式且当前角色是张老师时，返回自定义前提文本。
     * 普通谋杀模式中即使出现张老师角色也不会触发，避免误改。
     */
    @Redirect(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private static Object dim201$redirectPremiseText(Function<Object, Object> function, Object count) {
        if (roleTexts != null
                && roleTexts.getId().equals(TeacherZhangRole.TEACHER_ZHANG_ID)
                && SREClient.gameComponent != null
                && SREClient.gameComponent.getGameMode().identifier.equals(TeacherZhangAllGameMode.ID)) {
            return Component.translatable("announcement.star.premise.teacher_zhang_all");
        }
        return function.apply(count);
    }
}
