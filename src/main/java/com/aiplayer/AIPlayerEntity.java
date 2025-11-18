package com.aiplayer;

import com.aiplayer.sound.ModSoundEvents;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;
import java.util.*;

public class AIPlayerEntity extends TamableAnimal {

    private static final EntityDataAccessor<String> DATA_SKIN_NAME =
            SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);

    private int eatingTicks = 0;
    private int fartTimer = 0;
    private int inventoryCheckTimer = 0;
    private int barrelCheckTimer = 0;
    private int mobReactionTimer = 0;

    private BotMemory memory;
    private boolean shouldApproachOnce = false;
    private final SimpleContainer inventory = new SimpleContainer(36);
    private final Set<BlockPos> processedBarrels = new HashSet<>();
    private final Set<UUID> alertedMobs = new HashSet<>();
    private boolean isFleeing = false;
    private int fleeTimer = 0;

    public AIPlayerEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        resetFartTimer();
        
        // Устанавливаем имя по умолчанию если оно null
        if (this.getCustomName() == null) {
            this.setCustomName(Component.literal("AI_Bot"));
        }
        
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
                .add(Attributes.FOLLOW_RANGE, 9999.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.1D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.5D, 5.0F, 9999.0F, false));
        this.goalSelector.addGoal(4, new PickupItemGoal(this));
        this.goalSelector.addGoal(5, new BarrelInteractionGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(9, new PanicGoal(this, 1.5D));
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

        // Устанавливаем имя по умолчанию если оно null
        if (this.getCustomName() == null) {
            this.setCustomName(Component.literal("AI_Bot"));
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
                tryEquipArmor();
                
                // Возвращаем оружие в инвентарь если нет цели и не бежим
                if (this.getTarget() == null && !isFleeing) {
                    returnWeaponToInventory();
                }
            }

            // Проверяем бочки каждые 100 тиков (5 секунд)
            barrelCheckTimer++;
            if (barrelCheckTimer >= 100) {
                barrelCheckTimer = 0;
                processedBarrels.clear();
            }

            // Проверяем мобов каждые 40 тиков (2 секунды)
            mobReactionTimer++;
            if (mobReactionTimer >= 40) {
                mobReactionTimer = 0;
                checkForHostileMobs();
            }

            // Логика бегства
            if (isFleeing) {
                fleeTimer++;
                if (fleeTimer >= 200) { // Бежит 10 секунд
                    isFleeing = false;
                    fleeTimer = 0;
                    // Возвращаем оружие в инвентарь после бегства
                    returnWeaponToInventory();
                }
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

    private void checkForHostileMobs() {
        if (this.isOrderedToSit() || isFleeing) return;

        List<Monster> hostileMobs = this.level().getEntitiesOfClass(Monster.class, 
                this.getBoundingBox().inflate(16.0D));

        if (!hostileMobs.isEmpty()) {
            // Фильтруем мобов, которых еще не оповещали
            for (Monster mob : hostileMobs) {
                if (!alertedMobs.contains(mob.getUUID())) {
                    String mobName = getMobRussianName(mob);
                    
                    // Сообщение как обычное (белый цвет)
                    Component message = Component.literal("<" + this.getCustomName().getString() + "> вижу " + mobName + ", пиздец");
                    
                    // Отправляем сообщение всем игрокам в радиусе
                    for (Player player : this.level().players()) {
                        if (player.distanceToSqr(this) < 256.0D) {
                            player.sendSystemMessage(message);
                        }
                    }
                    
                    alertedMobs.add(mob.getUUID());
                    if (memory != null) memory.addAction("увидел " + mobName);
                    break; // Оповещаем об одном мобе за раз
                }
            }

            // Принимаем решение: атаковать или бежать
            boolean shouldAttack = shouldAttackMobs();
            
            if (shouldAttack) {
                // Находим ближайшего моба для атаки
                Monster target = null;
                double closestDistance = Double.MAX_VALUE;
                
                for (Monster mob : hostileMobs) {
                    double distance = this.distanceToSqr(mob);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        target = mob;
                    }
                }
                
                if (target != null) {
                    equipBestWeapon();
                    this.setTarget(target);
                    if (memory != null) memory.addAction("атакует " + getMobRussianName(target));
                }
            } else {
                // Бежим от мобов
                fleeFromMobs(hostileMobs);
            }
        } else {
            // Нет мобов - очищаем список оповещенных
            alertedMobs.clear();
            // Возвращаем оружие в инвентарь если нет цели
            if (this.getTarget() == null) {
                returnWeaponToInventory();
            }
        }
    }

    private boolean shouldAttackMobs() {
        // Условия для атаки:
        // 1. Здоровье больше 5
        // 2. Есть оружие в инвентаре
        // 3. Не в режиме бегства
        
        if (isFleeing) return false;
        if (this.getHealth() <= 5) return false;
        
        return hasWeaponInInventory();
    }

    private boolean hasWeaponInInventory() {
        // Проверяем оружие в руке
        ItemStack handItem = this.getMainHandItem();
        if (!handItem.isEmpty() && (handItem.getItem() instanceof SwordItem || handItem.getItem() instanceof AxeItem)) {
            return true;
        }
        
        // Проверяем оружие в инвентаре
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
                return true;
            }
        }
        return false;
    }

    private void equipBestWeapon() {
        // Если уже держим оружие - не меняем
        ItemStack handItem = this.getMainHandItem();
        if (!handItem.isEmpty() && (handItem.getItem() instanceof SwordItem || handItem.getItem() instanceof AxeItem)) {
            return;
        }
        
        ItemStack bestWeapon = null;
        
        // Ищем оружие в инвентаре
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
                bestWeapon = stack;
                break;
            }
        }
        
        if (bestWeapon != null) {
            // Берем оружие из инвентаря в руку (без копирования)
            this.setItemInHand(InteractionHand.MAIN_HAND, bestWeapon);
            // Удаляем из инвентаря
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (inventory.getItem(i) == bestWeapon) {
                    inventory.setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
            
            if (memory != null) {
                memory.addAction("вооружился " + bestWeapon.getDisplayName().getString());
            }
        }
    }

    private void returnWeaponToInventory() {
        ItemStack handItem = this.getMainHandItem();
        if (!handItem.isEmpty() && (handItem.getItem() instanceof SwordItem || handItem.getItem() instanceof AxeItem)) {
            // Пытаемся вернуть оружие в инвентарь
            boolean added = false;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (inventory.getItem(i).isEmpty()) {
                    inventory.setItem(i, handItem);
                    added = true;
                    break;
                }
            }
            
            if (added) {
                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            // Если не добавилось - оставляем в руке
        }
    }   

    private void fleeFromMobs(List<Monster> hostileMobs) {
        isFleeing = true;
        fleeTimer = 0;
        
        // Сбрасываем цель атаки
        this.setTarget(null);
        
        // Возвращаем оружие в инвентарь при бегстве
        returnWeaponToInventory();
        
        // Сообщение о бегстве
        Component message = Component.literal("<" + this.getCustomName().getString() + "> ааа идите нахуй");
        for (Player player : this.level().players()) {
            if (player.distanceToSqr(this) < 256.0D) {
                player.sendSystemMessage(message);
            }
        }
        
        // Вычисляем вектор бегства (противоположно от всех мобов)
        double fleeX = 0;
        double fleeZ = 0;
        
        for (Monster mob : hostileMobs) {
            double dx = this.getX() - mob.getX();
            double dz = this.getZ() - mob.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance > 0) {
                fleeX += dx / distance;
                fleeZ += dz / distance;
            }
        }
        
        // Нормализуем вектор
        double length = Math.sqrt(fleeX * fleeX + fleeZ * fleeZ);
        if (length > 0) {
            fleeX = fleeX / length * 10; // Бежим на 10 блоков
            fleeZ = fleeZ / length * 10;
        } else {
            // Случайное направление если мобы со всех сторон
            fleeX = (this.random.nextDouble() - 0.5) * 10;
            fleeZ = (this.random.nextDouble() - 0.5) * 10;
        }
        
        BlockPos fleePos = new BlockPos(
            (int)(this.getX() + fleeX),
            (int)this.getY(),
            (int)(this.getZ() + fleeZ)
        );
        
        this.getNavigation().moveTo(fleePos.getX(), fleePos.getY(), fleePos.getZ(), 1.5D);
        if (memory != null) memory.addAction("убегает от мобов");
    }

    private String getMobRussianName(Monster mob) {
        String typeName = mob.getType().getDescriptionId().toLowerCase();
        
        if (typeName.contains("zombie")) return "зомби";
        if (typeName.contains("skeleton")) return "скелета";
        if (typeName.contains("creeper")) return "крипера";
        if (typeName.contains("spider")) return "паука";
        if (typeName.contains("enderman")) return "эндермена";
        if (typeName.contains("witch")) return "ведьму";
        if (typeName.contains("phantom")) return "фантома";
        if (typeName.contains("drowned")) return "утопленника";
        if (typeName.contains("husk")) return "кадавра";
        if (typeName.contains("stray")) return "бродягу";
        if (typeName.contains("vex")) return "демона";
        if (typeName.contains("vindicator")) return "защитника";
        if (typeName.contains("pillager")) return "разбойника";
        if (typeName.contains("ravager")) return "разорителя";
        if (typeName.contains("evoker")) return "заклинателя";
        if (typeName.contains("warden")) return "стража";
        if (typeName.contains("ghast")) return "гаста";
        if (typeName.contains("blaze")) return "иссушителя";
        if (typeName.contains("magma_cube")) return "магмового куба";
        if (typeName.contains("silverfish")) return "черепашку";
        if (typeName.contains("slime")) return "слизь";
        if (typeName.contains("guardian")) return "стража древних";
        if (typeName.contains("elder_guardian")) return "старшего стража";
        if (typeName.contains("shulker")) return "шалкера";
        if (typeName.contains("wither_skeleton")) return "визер-скелета";
        if (typeName.contains("hoglin")) return "хоглина";
        if (typeName.contains("zoglin")) return "зоглина";
        if (typeName.contains("piglin")) return "пиглина";
        
        return "монстра";
    }

    private void manageInventory() {
        // Если здоровье меньше 10 и не в бою (нет цели и не бежит) - ищем еду в инвентаре
        if (this.getHealth() < 10 && this.getTarget() == null && !isFleeing) {
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

        // Автоматическое стакивание предметов
        stackItemsInInventory();

        // Проверяем переполнение инвентаря
        if (isInventoryFull()) {
            dropExcessItems();
        }
    }

    private void tryEquipArmor() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof ArmorItem armorItem && !stack.isEmpty()) {
                EquipmentSlot slot = armorItem.getEquipmentSlot();
                ItemStack currentArmor = getItemBySlot(slot);
                
                // Если слот пустой, надеваем броню
                if (currentArmor.isEmpty()) {
                    setItemSlot(slot, stack.copy());
                    inventory.setItem(i, ItemStack.EMPTY);
                    if (memory != null) memory.addAction("надел " + stack.getDisplayName().getString());
                    break;
                }
            }
        }
    }

    private void stackItemsInInventory() {
        // Создаем временный список для объединения стеков
        Map<String, ItemStack> itemMap = new HashMap<>();
        List<Integer> emptySlots = new ArrayList<>();

        // Сначала собираем информацию о всех предметах
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                emptySlots.add(i);
                continue;
            }

            String key = stack.getItem().toString() + stack.getTag();
            if (itemMap.containsKey(key)) {
                ItemStack existing = itemMap.get(key);
                if (existing.getCount() < existing.getMaxStackSize()) {
                    int canAdd = existing.getMaxStackSize() - existing.getCount();
                    int toAdd = Math.min(canAdd, stack.getCount());
                    existing.grow(toAdd);
                    stack.shrink(toAdd);
                    
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                        emptySlots.add(i);
                    }
                }
            } else {
                itemMap.put(key, stack);
            }
        }

        // Перераспределяем оставшиеся предметы
        List<ItemStack> remainingItems = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                remainingItems.add(stack);
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }

        // Заполняем инвентарь заново
        int currentSlot = 0;
        for (ItemStack stack : remainingItems) {
            while (currentSlot < inventory.getContainerSize() && !inventory.getItem(currentSlot).isEmpty()) {
                currentSlot++;
            }
            if (currentSlot < inventory.getContainerSize()) {
                inventory.setItem(currentSlot, stack);
                currentSlot++;
            }
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
        // Находим самый бесполезный предмет для выкидывания
        int worstSlot = -1;
        int worstPriority = Integer.MAX_VALUE;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                int priority = getItemPriority(stack);
                if (priority < worstPriority) {
                    worstPriority = priority;
                    worstSlot = i;
                }
            }
        }

        // Выкидываем самый бесполезный предмет
        if (worstSlot != -1) {
            ItemStack toDrop = inventory.getItem(worstSlot).copy();
            inventory.setItem(worstSlot, ItemStack.EMPTY);
            
            ItemEntity itemEntity = new ItemEntity(level(), getX(), getY() + 1, getZ(), toDrop);
            level().addFreshEntity(itemEntity);
            
            if (memory != null) memory.addAction("выкинул " + toDrop.getDisplayName().getString() + " (места нет)");
        }
    }

    private int getItemPriority(ItemStack stack) {
        // Чем ниже приоритет, тем проще выкинуть предмет
        // 0: Мусор (земля, камень, гравий и т.д.)
        // 1: Обычные блоки
        // 2: Еда (низкий приоритет, но не мусор)
        // 3: Инструменты и оружие
        // 4: Броня
        // 5: Ценные ресурсы (алмазы, изумруды и т.д.)
        // 6: Руды

        // Мусор
        if (stack.getItem() == net.minecraft.world.item.Items.DIRT) return 0;
        if (stack.getItem() == net.minecraft.world.item.Items.COBBLESTONE) return 0;
        if (stack.getItem() == net.minecraft.world.item.Items.GRAVEL) return 0;
        if (stack.getItem() == net.minecraft.world.item.Items.SAND) return 0;

        // Еда (но не восстанавливающая здоровье)
        if (stack.isEdible()) return 2;

        // Инструменты и оружие
        if (stack.getItem() instanceof net.minecraft.world.item.SwordItem) return 3;
        if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem) return 3;
        if (stack.getItem() instanceof net.minecraft.world.item.AxeItem) return 3;
        if (stack.getItem() instanceof net.minecraft.world.item.ShovelItem) return 3;

        // Броня
        if (stack.getItem() instanceof ArmorItem) return 4;

        // Ценные ресурсы
        if (stack.getItem() == net.minecraft.world.item.Items.DIAMOND) return 5;
        if (stack.getItem() == net.minecraft.world.item.Items.EMERALD) return 5;
        if (stack.getItem() == net.minecraft.world.item.Items.NETHERITE_INGOT) return 5;
        if (stack.getItem() == net.minecraft.world.item.Items.GOLD_INGOT) return 5;
        if (stack.getItem() == net.minecraft.world.item.Items.IRON_INGOT) return 5;

        // Руды
        if (stack.is(net.minecraft.tags.ItemTags.COAL_ORES)) return 6;
        if (stack.is(net.minecraft.tags.ItemTags.IRON_ORES)) return 6;
        if (stack.is(net.minecraft.tags.ItemTags.GOLD_ORES)) return 6;
        if (stack.is(net.minecraft.tags.ItemTags.DIAMOND_ORES)) return 6;
        if (stack.is(net.minecraft.tags.ItemTags.REDSTONE_ORES)) return 6;
        if (stack.is(net.minecraft.tags.ItemTags.LAPIS_ORES)) return 6;
        if (stack.is(net.minecraft.tags.ItemTags.COPPER_ORES)) return 6;
        if (stack.is(net.minecraft.tags.ItemTags.EMERALD_ORES)) return 6;

        // Обычные блоки
        return 1;
    }

    private boolean canDropItem(ItemStack stack) {
        return getItemPriority(stack) <= 2; // Можно выкидывать только мусор и обычные блоки
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
        // Восстанавливаем цель следования если она была удалена
        boolean hasFollowGoal = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof FollowOwnerGoal);
        if (!hasFollowGoal) {
            this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.5D, 5.0F, 9999.0F, false));
        }
        if (memory != null) memory.addAction("начал следовать за игроком");
    }

    public void stopFollowing() {
        // НЕ сажаем бота, просто удаляем цель следования
        WrappedGoal followGoal = this.goalSelector.getAvailableGoals().stream()
                .filter(g -> g.getGoal() instanceof FollowOwnerGoal)
                .findFirst().orElse(null);
        if (followGoal != null) {
            this.goalSelector.removeGoal(followGoal.getGoal());
        }
        if (memory != null) memory.addAction("перестал следовать за игроком");
    }

    public void approachOnce() {
        this.shouldApproachOnce = true;
        this.setOrderedToSit(false);
        if (memory != null) memory.addAction("получил команду подойти к игроку");
    }

    // Классы PickupItemGoal и BarrelInteractionGoal остаются без изменений
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

    static class BarrelInteractionGoal extends Goal {
        private final AIPlayerEntity bot;
        private BlockPos targetBarrel;
        private int interactionCooldown = 0;

        public BarrelInteractionGoal(AIPlayerEntity bot) {
            this.bot = bot;
        }

        @Override
        public boolean canUse() {
            if (interactionCooldown > 0) {
                interactionCooldown--;
                return false;
            }

            if (bot.processedBarrels.size() > 10) {
                bot.processedBarrels.clear(); // Очищаем если слишком много записей
            }

            this.targetBarrel = findNearbyBarrel();
            return this.targetBarrel != null && !bot.processedBarrels.contains(targetBarrel);
        }

        @Override
        public void start() {
            if (targetBarrel != null) {
                bot.getNavigation().moveTo(targetBarrel.getX() + 0.5, targetBarrel.getY(), targetBarrel.getZ() + 0.5, 1.0D);
            }
        }

        @Override
        public void tick() {
            if (targetBarrel == null) return;

            if (bot.distanceToSqr(targetBarrel.getX() + 0.5, targetBarrel.getY(), targetBarrel.getZ() + 0.5) < 4.0D) {
                interactWithBarrel();
                bot.processedBarrels.add(targetBarrel);
                interactionCooldown = 100; // 5 секунд коoldown
                targetBarrel = null;
            }
        }

        private BlockPos findNearbyBarrel() {
            BlockPos botPos = bot.blockPosition();
            int radius = 16;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos checkPos = botPos.offset(x, y, z);
                        if (bot.level().getBlockState(checkPos).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock) {
                            return checkPos;
                        }
                    }
                }
            }
            return null;
        }

        private void interactWithBarrel() {
            BlockEntity blockEntity = bot.level().getBlockEntity(targetBarrel);
            if (!(blockEntity instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel)) {
                return;
            }

            boolean didSomething = false;

            // 1. Берем ценные предметы из бочки
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                ItemStack barrelStack = barrel.getItem(i);
                if (!barrelStack.isEmpty() && isValuable(barrelStack)) {
                    if (tryAddToBotInventory(barrelStack.copy())) {
                        barrel.setItem(i, ItemStack.EMPTY);
                        didSomething = true;
                        if (bot.memory != null) bot.memory.addAction("взял " + barrelStack.getDisplayName().getString() + " из бочки");
                    }
                }
            }

            // 2. Складываем мусор в бочку
            for (int i = 0; i < bot.inventory.getContainerSize(); i++) {
                ItemStack botStack = bot.inventory.getItem(i);
                if (!botStack.isEmpty() && !isValuable(botStack) && bot.canDropItem(botStack)) {
                    if (tryAddToBarrel(barrel, botStack.copy())) {
                        bot.inventory.setItem(i, ItemStack.EMPTY);
                        didSomething = true;
                        if (bot.memory != null) bot.memory.addAction("положил " + botStack.getDisplayName().getString() + " в бочку");
                    }
                }
            }

            if (didSomething && bot.memory != null) {
                bot.memory.addAction("взаимодействовал с бочкой");
            }
        }

        private boolean isValuable(ItemStack stack) {
            return bot.getItemPriority(stack) >= 3; // Ценные предметы имеют приоритет 3 и выше
        }

        private boolean tryAddToBotInventory(ItemStack stack) {
            // Пытаемся добавить к существующим стекам
            for (int i = 0; i < bot.inventory.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack existing = bot.inventory.getItem(i);
                if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                    int canAdd = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
                    if (canAdd > 0) {
                        existing.grow(canAdd);
                        stack.shrink(canAdd);
                        bot.inventory.setItem(i, existing);
                    }
                }
            }

            // Пытаемся добавить в свободный слот
            if (!stack.isEmpty()) {
                for (int i = 0; i < bot.inventory.getContainerSize(); i++) {
                    if (bot.inventory.getItem(i).isEmpty()) {
                        bot.inventory.setItem(i, stack);
                        return true;
                    }
                }
            } else {
                return true;
            }

            return false;
        }

        private boolean tryAddToBarrel(net.minecraft.world.level.block.entity.BarrelBlockEntity barrel, ItemStack stack) {
            // Пытаемся добавить к существующим стекам в бочке
            for (int i = 0; i < barrel.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack existing = barrel.getItem(i);
                if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                    int canAdd = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
                    if (canAdd > 0) {
                        existing.grow(canAdd);
                        stack.shrink(canAdd);
                        barrel.setItem(i, existing);
                    }
                }
            }

            // Пытаемся добавить в свободный слот в бочке
            if (!stack.isEmpty()) {
                for (int i = 0; i < barrel.getContainerSize(); i++) {
                    if (barrel.getItem(i).isEmpty()) {
                        barrel.setItem(i, stack);
                        return true;
                    }
                }
            } else {
                return true;
            }

            return false;
        }

        @Override
        public void stop() {
            targetBarrel = null;
        }
    }
}