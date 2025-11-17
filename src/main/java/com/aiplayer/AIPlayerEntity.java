// src/main/java/com/aiplayer/AIPlayerEntity.java
package com.aiplayer;

import com.aiplayer.sound.ModSoundEvents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;
import java.util.List;

public class AIPlayerEntity extends TamableAnimal {

    private static final EntityDataAccessor<String> DATA_SKIN_NAME =
            SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);

    private int eatingTicks = 0;
    private int fartTimer = 0;

    private BotMemory memory;
    private boolean shouldApproachOnce = false;

    public AIPlayerEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        resetFartTimer();
        if (!level.isClientSide) {
            try {
                this.memory = new BotMemory(this);
            } catch (Exception e) {
                AIPlayerMod.LOGGER.error("Error creating BotMemory in constructor", e);
                this.memory = null;
            }
        }
    }

    private void resetFartTimer() {
        this.fartTimer = 200 + this.random.nextInt(400);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 9999.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.5D, 5.0F, 9999.0F, false));
        this.goalSelector.addGoal(3, new PickupItemGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(7, new PanicGoal(this, 1.5D));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SKIN_NAME, "Steve");
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        return null;
    }

    public void setSkinName(String skinName) {
        String name = skinName != null ? skinName.toLowerCase() : "steve";
        this.entityData.set(DATA_SKIN_NAME, name);
    }

    public String getSkinName() {
        return this.entityData.get(DATA_SKIN_NAME);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinName", this.getSkinName());
        tag.putInt("EatingTicks", this.eatingTicks);
        tag.putInt("FartTimer", this.fartTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SkinName")) {
            this.setSkinName(tag.getString("SkinName"));
        }
        this.eatingTicks = tag.getInt("EatingTicks");
        this.fartTimer = tag.getInt("FartTimer");
        if (this.fartTimer <= 0) resetFartTimer();
        if (!this.level().isClientSide) {
            try {
                this.memory = new BotMemory(this);
            } catch (Exception e) {
                AIPlayerMod.LOGGER.error("Error loading BotMemory in readAdditionalSaveData", e);
                this.memory = null;
            }
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            if (this.fartTimer > 0) this.fartTimer--;
            if (this.fartTimer == 0) {
                AIPlayerMod.LOGGER.info("BOT FART at X={} Y={} Z={}", this.getX(), this.getY(), this.getZ());
                this.level().playSound(null, this.blockPosition(), ModSoundEvents.FART.get(),
                        SoundSource.PLAYERS, 3.0F, 0.8F + this.random.nextFloat() * 0.4F);
                resetFartTimer();
            }
        }

        if (this.eatingTicks > 0) {
            this.eatingTicks--;

            if (this.eatingTicks % 4 == 0) {
                this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }

            if (this.eatingTicks % 5 == 0 && !this.level().isClientSide) {
                ItemStack stack = this.getMainHandItem();
                ((ServerLevel) this.level()).sendParticles(
                        new ItemParticleOption(ParticleTypes.ITEM, stack),
                        this.getX(), this.getY() + 1.1, this.getZ(),
                        5, 0.1, 0.1, 0.1, 0.0);
            }

            if (this.eatingTicks == 0) {
                FoodProperties food = this.getMainHandItem().getFoodProperties(this);
                if (food != null) this.heal(food.getNutrition());
                else this.heal(4.0F);

                AIPlayerMod.LOGGER.info("BOT BURP!");
                this.level().playSound(null, this.blockPosition(), ModSoundEvents.BURP.get(),
                        SoundSource.PLAYERS, 3.0F, 0.9F + this.random.nextFloat() * 0.2F);

                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }

        // ← АВТОСООБЩЕНИЯ КАЖДЫЕ ~60 СЕКУНД
        if (!this.level().isClientSide && this.random.nextInt(1200) == 0) {
            ServerPlayer owner = (ServerPlayer) this.getOwner();
            if (owner != null && owner.level() == this.level()) {
                String[] phrases = {
                    "бля, голодный я",
                    "где мой стейк?",
                    "вижу крипера, пиздец",
                    "кто алмазы нашёл?",
                    "пукнул, сорри",
                    "ну чё, когда в Nether?"
                };
                String msg = phrases[this.random.nextInt(phrases.length)];

                Component chatMessage = Component.literal("<" + this.getCustomName().getString() + "> ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                        .append(Component.literal(msg).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));

                owner.getServer().getPlayerList().broadcastSystemMessage(chatMessage, false);
            }
        }

        // ← ЛОГИКА ПОДХОДА ОДИН РАЗ
        if (!this.level().isClientSide && this.shouldApproachOnce && !this.isOrderedToSit()) {
            ServerPlayer owner = (ServerPlayer) this.getOwner();
            if (owner != null && this.distanceToSqr(owner) > 4.0) {
                this.getNavigation().moveTo(owner, 1.5D);
            } else {
                this.shouldApproachOnce = false;
                if (this.memory != null) this.memory.addAction("подошёл к игроку");
            }
        }
    }

    // ← ЗВУК УРОНА
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        AIPlayerMod.LOGGER.info("BOT HURT!");
        this.level().playSound(null, this.blockPosition(), ModSoundEvents.HURT.get(),
                SoundSource.PLAYERS, 3.0F, 0.8F + this.random.nextFloat() * 0.2F);
        return ModSoundEvents.HURT.get();
    }

    // ← ЗВУК СМЕРТИ
    @Override
    protected SoundEvent getDeathSound() {
        AIPlayerMod.LOGGER.info("BOT KILLED - DEXTER!");
        this.level().playSound(null, this.blockPosition(), ModSoundEvents.DEXTER.get(),
                SoundSource.PLAYERS, 3.0F, 1.0F);
        return ModSoundEvents.DEXTER.get();
    }

    public BotMemory getMemory() { return memory; }

    public void startFollowing() {
        this.setOrderedToSit(false);
        memory.addAction("начал следовать за игроком");
    }

    public void stopFollowing() {
        this.setOrderedToSit(true);
        WrappedGoal followGoal = this.goalSelector.getAvailableGoals().stream()
                .filter(g -> g.getGoal() instanceof FollowOwnerGoal)
                .findFirst().orElse(null);
        if (followGoal != null) {
            this.goalSelector.removeGoal(followGoal.getGoal());
        }
        memory.addAction("остановился и сел по команде");
    }

    public void approachOnce() {
        this.shouldApproachOnce = true;
        this.setOrderedToSit(false);
        memory.addAction("получил команду подойти к игроку");
    }

    static class PickupItemGoal extends Goal {
        private final AIPlayerEntity bot;
        private ItemEntity targetItem;

        public PickupItemGoal(AIPlayerEntity bot) {
            this.bot = bot;
        }

        @Override
        public boolean canUse() {
            return this.bot.getMainHandItem().isEmpty() && this.bot.eatingTicks == 0 &&
                   !this.bot.level().getEntitiesOfClass(ItemEntity.class,
                           this.bot.getBoundingBox().inflate(8.0D), item -> !item.hasPickUpDelay()).isEmpty();
        }

        @Override
        public void tick() {
            List<ItemEntity> items = this.bot.level().getEntitiesOfClass(ItemEntity.class,
                    this.bot.getBoundingBox().inflate(8.0D), item -> !item.hasPickUpDelay());
            if (items.isEmpty()) {
                this.targetItem = null;
                return;
            }

            this.targetItem = items.get(0);
            this.bot.getNavigation().moveTo(this.targetItem, 1.2D);

            if (this.bot.distanceToSqr(this.targetItem) < 1.5D) {
                ItemStack stack = this.targetItem.getItem().copy();
                this.targetItem.discard();
                this.bot.setItemInHand(InteractionHand.MAIN_HAND, stack);
                if (stack.isEdible()) {
                    this.bot.eatingTicks = 60;
                }
                if (this.bot.memory != null) {
                    this.bot.memory.addAction("подобрал " + stack.getDisplayName().getString());
                }
            }
        }

        @Override
        public void stop() {
            this.targetItem = null;
        }
    }
}