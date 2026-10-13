package com.dim201.dim201addon;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.role.WhiteSnakeRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WhiteSnakePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<WhiteSnakePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "whitesnake"), 
            WhiteSnakePlayerComponent.class);

    private final Player player;

    public enum Skill {
        ABILITY_DISSOLVE("能力溶解"),
        SPIRIT_DISSOLVE("精神溶解");
        
        public final String displayName;
        
        Skill(String displayName) {
            this.displayName = displayName;
        }
    }
    
    public Skill currentSkill = Skill.ABILITY_DISSOLVE;

    public static final double SAN_AURA_RANGE = 35.0;
    public static final double SAN_AURA_BASE_EROSION = 0.0004;
    public static final double SAN_AURA_GAZE_MULT = 1.8;
    public static final float LOW_SAN_THRESHOLD = 0.15f;

    public int abilityDissolveCooldown = 0;
    public static final int ABILITY_DISSOLVE_COOLDOWN = 2400;

    public int spiritDissolveCooldown = 0;
    public static final int SPIRIT_DISSOLVE_COOLDOWN = 600;

    public Map<UUID, Boolean> spiritDissolvedTargets = new HashMap<>();

    // 客户端缓存的低 san 玩家列表（从服务端同步）
    public List<String> lowSanPlayerNames = new ArrayList<>();

    // 护盾系统
    public int lastArmor = -1;
    private boolean armorManuallyAdjusted = false;

    private float sanLossAccumulator = 0f;
    private long lastTickGameTime = -1;

    public WhiteSnakePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return this.player == sp;
    }

    @Override
    public void init() {
        currentSkill = Skill.ABILITY_DISSOLVE;
        abilityDissolveCooldown = 0;
        spiritDissolveCooldown = 0;
        spiritDissolvedTargets.clear();
        lowSanPlayerNames.clear();
        lastArmor = -1;
        armorManuallyAdjusted = false;
        sanLossAccumulator = 0f;
        lastTickGameTime = -1;
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            var server = sp.getServer();
            if (server != null) {
                for (UUID targetId : spiritDissolvedTargets.keySet()) {
                    ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                    if (target != null) {
                        target.removeEffect(MobEffects.BLINDNESS);
                    }
                }
            }
        }
        spiritDissolvedTargets.clear();
        lowSanPlayerNames.clear();
        lastArmor = -1;
        armorManuallyAdjusted = false;
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void switchSkill(ServerPlayer player) {
        Skill[] skills = Skill.values();
        int nextIndex = (currentSkill.ordinal() + 1) % skills.length;
        currentSkill = skills[nextIndex];
        sendActionBar(player, "§5§l「白蛇」 §7| §f切换技能: " + currentSkill.displayName);
        sync();
    }

    // ==================== 技能1：能力溶解 ====================

    public boolean useAbilityDissolve(ServerPlayer player) {
        if (abilityDissolveCooldown > 0) {
            int cd = (int) Math.ceil(abilityDissolveCooldown / 20.0);
            sendActionBar(player, "§c§l能力溶解冷却中 §f" + cd + "秒");
            return false;
        }

        EntityHitResult entityHit = getEntityHitResult(player);
        if (entityHit != null) {
            Entity entity = entityHit.getEntity();
            if (!(entity instanceof Player)) {
                CorpseData corpseData = extractCorpseData(entity);
                if (corpseData != null) {
                    return performCorpseAbilityDissolve(player, corpseData, entity.position(), entity.blockPosition());
                }
            }
        }

        var target = getTargetPlayer(player);
        if (target == null) {
            sendActionBar(player, "§c§l请对准玩家或尸体！");
            return false;
        }

        if (!isSanLowEnough(target)) {
            sendActionBar(player, "§c§l目标 san 值过高！需要 ≤15");
            return false;
        }

        return performAbilityDissolve(player, target);
    }

    private EntityHitResult getEntityHitResult(ServerPlayer player) {
        double reach = 5.0;
        var eyePos = player.getEyePosition(1.0f);
        var lookVec = player.getLookAngle();
        var endPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        
        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (entity instanceof Player) continue;
            Vec3 toEntity = entity.position().subtract(eyePos).normalize();
            if (toEntity.dot(lookVec) > 0.5) {
                return new EntityHitResult(entity, entity.position());
            }
        }
        return null;
    }

    public static class CorpseData {
        public final String playerName;
        public final String roleId;
        
        public CorpseData(String playerName, String roleId) {
            this.playerName = playerName;
            this.roleId = roleId;
        }
    }

    private CorpseData extractCorpseData(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        
        String playerName = null;
        String roleId = "starrailexpress:civilian";
        
        if (tag.contains("BodyComponent", CompoundTag.TAG_COMPOUND)) {
            CompoundTag bodyComponent = tag.getCompound("BodyComponent");
            if (bodyComponent.contains("playerRole")) {
                roleId = bodyComponent.getString("playerRole");
            }
        }
        
        if (tag.contains("Player", CompoundTag.TAG_INT_ARRAY)) {
            int[] uuidArray = tag.getIntArray("Player");
            if (uuidArray.length == 4) {
                UUID playerUUID = new UUID(
                    ((long)uuidArray[0] << 32) | (uuidArray[1] & 0xFFFFFFFFL),
                    ((long)uuidArray[2] << 32) | (uuidArray[3] & 0xFFFFFFFFL)
                );
                
                if (player.level().getServer() != null) {
                    ServerPlayer serverPlayer = player.level().getServer().getPlayerList().getPlayer(playerUUID);
                    if (serverPlayer != null) {
                        playerName = serverPlayer.getName().getString();
                    } else {
                        var profile = player.level().getServer().getProfileCache().get(playerUUID);
                        if (profile.isPresent()) {
                            playerName = profile.get().getName();
                        }
                    }
                }
            }
        }
        
        if (playerName == null && tag.contains("CustomName")) {
            String customName = tag.getString("CustomName");
            if (customName.contains("\"")) {
                int lastQuote = customName.lastIndexOf('"');
                int secondLastQuote = customName.lastIndexOf('"', lastQuote - 1);
                if (secondLastQuote != -1 && lastQuote != -1) {
                    playerName = customName.substring(secondLastQuote + 1, lastQuote);
                }
            }
        }
        
        if (playerName != null) {
            playerName = playerName.replace("\"", "");
        }
        
        if (playerName != null && !playerName.isEmpty()) {
            return new CorpseData(playerName, roleId);
        }
        return null;
    }

    private boolean performCorpseAbilityDissolve(ServerPlayer player, CorpseData corpse, Vec3 pos, net.minecraft.core.BlockPos blockPos) {
        ItemStack disc = createAbilityDisc(corpse.playerName, corpse.roleId);
        player.getInventory().add(disc);
        abilityDissolveCooldown = ABILITY_DISSOLVE_COOLDOWN;

        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 30, 0.5, 0.5, 0.5, 0.03);
            sl.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 1.0, pos.z, 20, 0.3, 0.3, 0.3, 0.02);
            sl.playSound(null, blockPos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
        }

        sendActionBar(player, "§5§l能力溶解！§f从尸体获得 " + corpse.playerName + " 的能力DISC");
        sync();
        return true;
    }

    private boolean performAbilityDissolve(ServerPlayer player, ServerPlayer target) {
        var gameWorld = SREGameWorldComponent.KEY.get(target.level());
        if (gameWorld == null) {
            sendActionBar(player, "§c§l游戏世界错误！");
            return false;
        }

        var originalRole = gameWorld.getRole(target);
        String originalRoleId = originalRole != null ? originalRole.identifier().toString() : "starrailexpress:civilian";

        var server = player.getServer();
        if (server != null) {
            var commandSource = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
            server.getCommands().performPrefixedCommand(
                commandSource,
                "changeRole " + target.getName().getString() + " starrailexpress:civilian"
            );
        }

        ItemStack disc = createAbilityDisc(target.getName().getString(), originalRoleId);
        player.getInventory().add(disc);
        abilityDissolveCooldown = ABILITY_DISSOLVE_COOLDOWN;

        if (target.level() instanceof ServerLevel sl) {
            Vec3 pos = target.position();
            sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 30, 0.5, 0.5, 0.5, 0.03);
            sl.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 1.0, pos.z, 20, 0.3, 0.3, 0.3, 0.02);
            sl.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
        }

        sendActionBar(player, "§5§l能力溶解！§f获得 " + target.getName().getString() + " 的能力DISC");
        sync();
        return true;
    }

    private ItemStack createAbilityDisc(String playerName, String roleId) {
        ItemStack disc = new ItemStack(Dim201ModItems.ABILITY_DISC);
        disc.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
            Component.literal("§5§l能力DISC §7| §f" + playerName + " §8§k[WS:" + roleId + "]"));
        return disc;
    }

    // ==================== 技能2：精神溶解 ====================

    public boolean useSpiritDissolve(ServerPlayer player) {
        if (spiritDissolveCooldown > 0) {
            int cd = (int) Math.ceil(spiritDissolveCooldown / 20.0);
            sendActionBar(player, "§c§l精神溶解冷却中 §f" + cd + "秒");
            return false;
        }

        var target = getTargetPlayer(player);
        if (target == null) {
            sendActionBar(player, "§c§l请对准玩家！");
            return false;
        }

        if (!isSanLowEnough(target)) {
            sendActionBar(player, "§c§l目标 san 值过高！需要 ≤15");
            return false;
        }

        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 999999, 0, false, false, false));
        spiritDissolvedTargets.put(target.getUUID(), true);
        spiritDissolveCooldown = SPIRIT_DISSOLVE_COOLDOWN;

        if (target.level() instanceof ServerLevel sl) {
            Vec3 pos = target.position();
            sl.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 1.0, pos.z, 40, 0.4, 0.4, 0.4, 0.03);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.5, pos.z, 15, 0.3, 0.3, 0.3, 0.02);
            sl.playSound(null, target.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0F, 0.7F);
        }

        sendActionBar(player, "§5§l精神溶解！§f" + target.getName().getString() + " 已失明");
        sync();
        return true;
    }

    // ==================== 心智溶解（被动降san光环） ====================

    private void processSanAura() {
        double range = SAN_AURA_RANGE;
        double base = SAN_AURA_BASE_EROSION;
        if (range <= 0 || base <= 0) return;

        Level world = player.level();
        Vec3 playerPos = player.position();

        for (Player target : world.players()) {
            if (target.equals(player)) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
            if (isKiller(target)) continue;

            double distance = playerPos.distanceTo(target.position());
            if (distance > range) continue;

            double t = distance / range;
            double prox = Math.pow(1.0 - t, 1.5);
            double gaze = target.hasLineOfSight(player) ? SAN_AURA_GAZE_MULT : 1.0;

            float mood = SREPlayerMoodComponent.KEY.get(target).getMood();
            double lowSanBonus = 1.0;
            if (mood < LOW_SAN_THRESHOLD) {
                lowSanBonus = 1.0 + 0.5 * ((LOW_SAN_THRESHOLD - mood) / LOW_SAN_THRESHOLD);
            }

            float rate = (float) (base * prox * gaze * lowSanBonus);
            if (rate <= 0f) continue;
            SREPlayerMoodComponent.KEY.get(target).addMood(-rate);
        }
    }

    // ==================== 辅助方法 ====================

    private ServerPlayer getTargetPlayer(ServerPlayer player) {
        double reach = 10.0;
        var eyePos = player.getEyePosition(1.0f);
        var lookVec = player.getLookAngle();
        
        var server = player.getServer();
        if (server == null) return null;
        
        ServerPlayer closest = null;
        double closestDist = reach;
        
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == player) continue;
            if (!p.isAlive()) continue;
            
            var playerPos = p.position().add(0, p.getEyeHeight() * 0.5, 0);
            double dist = playerPos.distanceTo(eyePos);
            if (dist > reach) continue;
            
            var toPlayer = playerPos.subtract(eyePos).normalize();
            double dot = toPlayer.dot(lookVec);
            if (dot > 0.7) {
                if (dist < closestDist) {
                    closest = p;
                    closestDist = dist;
                }
            }
        }
        return closest;
    }

    private boolean isSanLowEnough(Player target) {
        float mood = SREPlayerMoodComponent.KEY.get(target).getMood();
        return mood <= LOW_SAN_THRESHOLD;
    }

    private boolean isKiller(Player target) {
        return SREGameWorldComponent.KEY.get(target.level()).isKillerTeam(target);
    }

    // ==================== 获取 san<=15 的玩家列表（用于HUD） ====================

    public List<String> getLowSanPlayerNames() {
        // 服务端：实时计算；客户端：返回从服务端同步的缓存
        return lowSanPlayerNames;
    }

    // ==================== 护盾系统 ====================

    public void syncShieldsWithDiscs() {
        if (!(player instanceof ServerPlayer sp)) return;
        
        int discCount = countDiscsInInventory(sp);
        int maxLayers = 5;
        int targetShields = Math.min(discCount, maxLayers);
        
        var armorComp = SREArmorPlayerComponent.KEY.get(sp);
        if (armorComp == null) return;
        
        int currentArmor = armorComp.getArmor();
        
        if (targetShields > currentArmor) {
            for (int i = 0; i < targetShields - currentArmor; i++) {
                armorComp.addArmor();
            }
        } else if (targetShields < currentArmor) {
            armorComp.removeArmor(currentArmor - targetShields);
            armorManuallyAdjusted = true;
        }
    }

    public void onArmorConsumed() {
        if (!(player instanceof ServerPlayer sp)) return;
        
        var armorComp = SREArmorPlayerComponent.KEY.get(sp);
        if (armorComp == null) return;
        
        int currentArmor = armorComp.getArmor();
        
        if (armorManuallyAdjusted) {
            lastArmor = currentArmor;
            armorManuallyAdjusted = false;
            return;
        }
        
        if (lastArmor > currentArmor) {
            int consumed = lastArmor - currentArmor;
            for (int i = 0; i < consumed; i++) {
                removeOneDisc(sp);
            }
            if (consumed > 0) {
                sendActionBar(sp, "§5§l护盾破碎！§c消耗了 §f" + consumed + " §c个能力DISC");
            }
        }
        lastArmor = currentArmor;
    }

    private int countDiscsInInventory(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Dim201ModItems.ABILITY_DISC)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeOneDisc(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Dim201ModItems.ABILITY_DISC)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                player.inventoryMenu.broadcastChanges();
                return;
            }
        }
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        if (!isActiveWhiteSnake()) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        if (!(player instanceof ServerPlayer sp)) return;

        long currentTime = sp.serverLevel().getGameTime();
        if (currentTime == lastTickGameTime) return;
        lastTickGameTime = currentTime;

        processSanAura();

        // 同步护盾与 DISC（每2秒检查一次）
        if (player.tickCount % 40 == 0) {
            syncShieldsWithDiscs();
        }
        onArmorConsumed();

        // 每2秒更新低 san 玩家列表并同步到客户端
        if (player.tickCount % 40 == 0) {
            updateLowSanPlayerList();
            sync();
        }

        if (abilityDissolveCooldown > 0) {
            abilityDissolveCooldown--;
            if (abilityDissolveCooldown % 20 == 0 || abilityDissolveCooldown == 0) {
                sync();
            }
        }

        if (spiritDissolveCooldown > 0) {
            spiritDissolveCooldown--;
            if (spiritDissolveCooldown % 20 == 0 || spiritDissolveCooldown == 0) {
                sync();
            }
        }
    }

    @Override
    public void clientTick() {
        if (!isActiveWhiteSnake()) return;
        if (abilityDissolveCooldown > 0) abilityDissolveCooldown--;
        if (spiritDissolveCooldown > 0) spiritDissolveCooldown--;
    }

    public boolean isActiveWhiteSnake() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return false;
        return gameWorld.isRole(player, WhiteSnakeRole.WHITESNAKE);
    }

    private void updateLowSanPlayerList() {
        if (!(player instanceof ServerPlayer sp)) return;
        
        List<String> names = new ArrayList<>();
        for (ServerPlayer other : sp.server.getPlayerList().getPlayers()) {
            if (other == sp) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(other)) continue;
            if (SREPlayerMoodComponent.KEY.get(other).getMood() <= LOW_SAN_THRESHOLD) {
                names.add(other.getName().getString());
            }
        }
        lowSanPlayerNames = names;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("CurrentSkill")) {
            try {
                currentSkill = Skill.valueOf(tag.getString("CurrentSkill"));
            } catch (IllegalArgumentException ignored) {}
        }
        abilityDissolveCooldown = tag.getInt("AbilityDissolveCooldown");
        spiritDissolveCooldown = tag.getInt("SpiritDissolveCooldown");
        
        if (tag.contains("SpiritDissolvedTargets")) {
            CompoundTag targetsTag = tag.getCompound("SpiritDissolvedTargets");
            for (String key : targetsTag.getAllKeys()) {
                spiritDissolvedTargets.put(UUID.fromString(key), targetsTag.getBoolean(key));
            }
        }
        lastArmor = tag.getInt("LastArmor");
        
        // 反序列化低 san 玩家列表
        lowSanPlayerNames.clear();
        if (tag.contains("LowSanPlayerNames")) {
            CompoundTag lowSanTag = tag.getCompound("LowSanPlayerNames");
            for (String key : lowSanTag.getAllKeys()) {
                lowSanPlayerNames.add(lowSanTag.getString(key));
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putString("CurrentSkill", currentSkill.name());
        tag.putInt("AbilityDissolveCooldown", abilityDissolveCooldown);
        tag.putInt("SpiritDissolveCooldown", spiritDissolveCooldown);
        
        CompoundTag targetsTag = new CompoundTag();
        for (Map.Entry<UUID, Boolean> entry : spiritDissolvedTargets.entrySet()) {
            targetsTag.putBoolean(entry.getKey().toString(), entry.getValue());
        }
        tag.put("SpiritDissolvedTargets", targetsTag);
        tag.putInt("LastArmor", lastArmor);
        
        // 序列化低 san 玩家列表（用于客户端 HUD 显示）
        CompoundTag lowSanTag = new CompoundTag();
        for (int i = 0; i < lowSanPlayerNames.size(); i++) {
            lowSanTag.putString(String.valueOf(i), lowSanPlayerNames.get(i));
        }
        tag.put("LowSanPlayerNames", lowSanTag);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    // ==================== 工具方法 ====================

    private void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}