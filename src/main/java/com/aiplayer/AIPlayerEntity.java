package com.aiplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class AIPlayerEntity extends TamableAnimal {

    // Синхронизируемое имя скина
    private static final EntityDataAccessor<String> DATA_SKIN_NAME =
            SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);

    public AIPlayerEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    // === АТРИБУТЫ ===
    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    // === ЦЕЛИ AI ===
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.2D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // === СИНХРОНИЗАЦИЯ ДАННЫХ ===
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SKIN_NAME, "Steve"); // Дефолт
    }

    // === РАЗМНОЖЕНИЕ ===
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        return null;
    }

    // === СКИН ===
    public void setSkinName(String skinName) {
        String name = skinName != null ? skinName : "Steve";
        this.entityData.set(DATA_SKIN_NAME, name);
    }

    public String getSkinName() {
        return this.entityData.get(DATA_SKIN_NAME);
    }

    // === СОХРАНЕНИЕ В МИР ===
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinName", this.getSkinName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SkinName")) {
            this.setSkinName(tag.getString("SkinName"));
        }
    }

    // === ЕДА ===
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }
}