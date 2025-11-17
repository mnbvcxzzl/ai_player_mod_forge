package com.aiplayer;

import com.aiplayer.sound.ModSoundEvents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
    private int inventoryCheckTimer = 0;

    private BotMemory memory;
    private boolean shouldApproachOnce = false;
    private final SimpleContainer inventory = new SimpleContainer(36);

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
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
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

    public SimpleContainer getInventory() {
        return inventory;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinName", this.getSkinName());
        tag.putInt("EatingTicks", this.eatingTicks);
        tag.putInt("FartTimer", this.fartTimer);
        
        // Сохраняем инвентарь
        ListTag inventoryList = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stack.save(itemTag);
                inventoryList.add(itemTag);
            }
        }
        tag.put("Inventory", inventoryList);
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

        // Загружаем инвентарь
        if (tag.contains("Inventory")) {
            ListTag inventoryList = tag.getList("Inventory", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < inventoryList.size(); i++) {
                CompoundTag itemTag = inventoryList.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, ItemStack.of(itemTag));
                }
            }
        }

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
            // Проверяем инвентарь каждые 20 тиков (1 секунда)
            inventoryCheckTimer++;
            if (inventoryCheckTimer >= 20) {
                inventoryCheckTimer = 0;
                manageInventory();
            }

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
                ItemStack foodStack = this.getMainHandItem();
                FoodProperties food = foodStack.getFoodProperties(this);
                if (food != null) {
                    this.heal(food.getNutrition());
                } else {
                    this.heal(4.0F);
                }

                AIPlayerMod.LOGGER.info("BOT BURP!");
                this.level().playSound(null, this.blockPosition(), ModSoundEvents.BURP.get(),
                        SoundSource.PLAYERS, 3.0F, 0.9F + this.random.nextFloat() * 0.2F);

                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }

        // Автосообщения каждые ~60 секунд
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

        // Логика подхода один раз
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

    private void manageInventory() {
        // Если здоровье не полное - ищем еду в инвентаре
        if (this.getHealth() < this.getMaxHealth()) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEdible() && this.eatingTicks == 0 && !stack.isEmpty()) {
                    // Начинаем есть - берем 1 предмет из стека
                    ItemStack foodToEat = stack.split(1);
                    this.setItemInHand(InteractionHand.MAIN_HAND, foodToEat);
                    this.eatingTicks = 60;
                    if (memory != null) memory.addAction("съел " + stack.getDisplayName().getString());
                    break;
                }
            }
        }

        // Проверяем переполнение инвентаря
        if (isInventoryFull()) {
            dropExcessItems();
        }
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void dropExcessItems() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && canDropItem(stack)) {
                // Выкидываем предмет
                ItemEntity itemEntity = new ItemEntity(level(), getX(), getY() + 1, getZ(), stack.copy());
                level().addFreshEntity(itemEntity);
                inventory.setItem(i, ItemStack.EMPTY);
                
                if (memory != null) memory.addAction("выкинул " + stack.getDisplayName().getString() + " (места нет)");
                break;
            }
        }
    }

    private boolean canDropItem(ItemStack stack) {
        // Никогда не выкидываем еду
        if (stack.isEdible()) return false;
        
        // Никогда не выкидываем инструменты
        if (stack.getItem() instanceof net.minecraft.world.item.SwordItem) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.AxeItem) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.ShovelItem) return false;
        
        // Никогда не выкидываем руды
        if (stack.is(net.minecraft.tags.ItemTags.COAL_ORES)) return false;
        if (stack.is(net.minecraft.tags.ItemTags.IRON_ORES)) return false;
        if (stack.is(net.minecraft.tags.ItemTags.GOLD_ORES)) return false;
        if (stack.is(net.minecraft.tags.ItemTags.DIAMOND_ORES)) return false;
        if (stack.is(net.minecraft.tags.ItemTags.REDSTONE_ORES)) return false;
        if (stack.is(net.minecraft.tags.ItemTags.LAPIS_ORES)) return false;
        if (stack.is(net.minecraft.tags.ItemTags.COPPER_ORES)) return false;
        if (stack.is(net.minecraft.tags.ItemTags.EMERALD_ORES)) return false;
        
        // Никогда не выкидываем ценные ресурсы
        if (stack.is(net.minecraft.tags.ItemTags.COALS)) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.IRON_INGOT) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.GOLD_INGOT) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.DIAMOND) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.EMERALD) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.REDSTONE) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.LAPIS_LAZULI) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.COPPER_INGOT) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.NETHERITE_INGOT) return false;
        if (stack.getItem() == net.minecraft.world.item.Items.NETHERITE_SCRAP) return false;
        
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        AIPlayerMod.LOGGER.info("BOT HURT!");
        this.level().playSound(null, this.blockPosition(), ModSoundEvents.HURT.get(),
                SoundSource.PLAYERS, 3.0F, 0.8F + this.random.nextFloat() * 0.2F);
        return ModSoundEvents.HURT.get();
    }

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
        if (memory != null) memory.addAction("начал следовать за игроком");
    }

    public void stopFollowing() {
        this.setOrderedToSit(true);
        WrappedGoal followGoal = this.goalSelector.getAvailableGoals().stream()
                .filter(g -> g.getGoal() instanceof FollowOwnerGoal)
                .findFirst().orElse(null);
        if (followGoal != null) {
            this.goalSelector.removeGoal(followGoal.getGoal());
        }
        if (memory != null) memory.addAction("остановился и сел по команде");
    }

    public void approachOnce() {
        this.shouldApproachOnce = true;
        this.setOrderedToSit(false);
        if (memory != null) memory.addAction("получил команду подойти к игроку");
    }

    static class PickupItemGoal extends Goal {
        private final AIPlayerEntity bot;
        private ItemEntity targetItem;

        public PickupItemGoal(AIPlayerEntity bot) {
            this.bot = bot;
        }

        @Override
        public boolean canUse() {
            return bot.eatingTicks == 0 && 
                   !bot.level().getEntitiesOfClass(ItemEntity.class,
                           bot.getBoundingBox().inflate(8.0D), item -> !item.hasPickUpDelay()).isEmpty();
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
                
                // Пытаемся добавить в инвентарь
                boolean added = false;
                for (int i = 0; i < bot.inventory.getContainerSize(); i++) {
                    if (bot.inventory.getItem(i).isEmpty()) {
                        bot.inventory.setItem(i, stack);
                        added = true;
                        break;
                    }
                }
                
                if (added && this.bot.memory != null) {
                    this.bot.memory.addAction("подобрал " + stack.getDisplayName().getString());
                } else if (!added) {
                    // Если не поместилось - выкидываем обратно
                    this.bot.level().addFreshEntity(new ItemEntity(
                        this.bot.level(), this.bot.getX(), this.bot.getY(), this.bot.getZ(), stack));
                }
            }
        }

        @Override
        public void stop() {
            this.targetItem = null;
        }
    }
}