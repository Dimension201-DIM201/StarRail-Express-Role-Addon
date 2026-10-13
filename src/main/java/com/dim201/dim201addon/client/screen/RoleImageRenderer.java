package com.dim201.dim201addon.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色介绍界面背景图片渲染（叠加层，不单独占 Tab）
 * <p>动态读取 PNG 真实尺寸 → UV 1:1 不变形，保持原始比例。
 * <p>透明度和羽化由原图自身的 alpha 通道处理。
 * <p>没有图片资源的角色不会渲染（避免紫黑方块）。
 */
public class RoleImageRenderer {

    /** 渲染缩放比例（原始尺寸的 1/3） */
    private static final float SCALE = 1.0f / 3.0f;

    /** 纹理尺寸缓存（资源路径 → [width, height]） */
    private static final ConcurrentHashMap<ResourceLocation, int[]> SIZE_CACHE = new ConcurrentHashMap<>();

    /** 已知不存在的资源缓存（避免重复 IO 检查） */
    private static final Set<ResourceLocation> MISSING_TEXTURES = ConcurrentHashMap.newKeySet();

    /**
     * 以原始尺寸的 1/3 渲染图片，右对齐紧贴边框，垂直居中。
     * 资源不存在时不渲染（避免紫黑方块）。
     */
    public static void render(GuiGraphics g, ResourceLocation texture,
                              int x, int y, int w, int h) {
        if (MISSING_TEXTURES.contains(texture)) return;

        int[] size = getTextureSize(texture);
        if (size == null) {
            MISSING_TEXTURES.add(texture);
            return;
        }
        int texW = size[0];
        int texH = size[1];

        // 缩小到 1/3，UV 保持完整纹理映射（不变形）
        int drawW = Math.round(texW * SCALE);
        int drawH = Math.round(texH * SCALE);
        int drawX = x + w - drawW;
        int drawY = drawH < h ? y + (h - drawH) / 2 : y;

        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture, drawX, drawY, drawW, drawH, 0, 0, texW, texH, texW, texH);
        g.disableScissor();
    }

    /**
     * 获取纹理实际尺寸。资源不存在或读取失败时返回 null。
     */
    private static int[] getTextureSize(ResourceLocation texture) {
        int[] cached = SIZE_CACHE.get(texture);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return null;

        try {
            Resource res = mc.getResourceManager().getResource(texture).orElse(null);
            if (res == null) return null;
            try (InputStream is = res.open()) {
                int[] size = readPngSize(is);
                if (size != null) {
                    SIZE_CACHE.put(texture, size);
                    return size;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 从 PNG 流读取 width/height（只读 IHDR 头，不解码像素）
     */
    private static int[] readPngSize(InputStream is) throws Exception {
        byte[] sig = new byte[8];
        if (is.read(sig) != 8) return null;
        if (sig[0] != (byte) 0x89 || sig[1] != 0x50 || sig[2] != 0x4E || sig[3] != 0x47)
            return null;

        byte[] lenBuf = new byte[4];
        if (is.read(lenBuf) != 4) return null;
        int len = ((lenBuf[0] & 0xFF) << 24) | ((lenBuf[1] & 0xFF) << 16)
                | ((lenBuf[2] & 0xFF) << 8) | (lenBuf[3] & 0xFF);
        byte[] typeBuf = new byte[4];
        if (is.read(typeBuf) != 4) return null;
        if (typeBuf[0] != 'I' || typeBuf[1] != 'H' || typeBuf[2] != 'D' || typeBuf[3] != 'R')
            return null;
        byte[] data = new byte[Math.min(len, 13)];
        if (is.read(data) != data.length) return null;

        int width = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int height = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16)
                | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        return new int[]{width, height};
    }

    private RoleImageRenderer() {}
}
