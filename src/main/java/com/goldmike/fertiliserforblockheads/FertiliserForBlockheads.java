package com.goldmike.fertiliserforblockheads;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.ParametersAreNonnullByDefault;
import net.blay09.mods.farmingforblockheads.block.FertilizedFarmlandBlock;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
@Mod(FertiliserForBlockheads.MODID)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FertiliserForBlockheads
{
    public static final String MODID = "fertiliserforblockheads";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    // New combo blocks (missing from Farming for Blockheads)
    public static final DeferredBlock<Block> FERTILIZED_FARMLAND_RICH_HEALTHY = BLOCKS.register("fertilized_farmland_rich_healthy", () -> new ComboFarmlandBlock(false));
    public static final DeferredBlock<Block> FERTILIZED_FARMLAND_RICH_HEALTHY_STABLE = BLOCKS.register("fertilized_farmland_rich_healthy_stable", () -> new ComboFarmlandBlock(true));
    // BlockItems so they can be crafted / held + show FFB-style tooltips
    public static final DeferredItem<Item> FERTILIZED_FARMLAND_RICH_HEALTHY_ITEM = ITEMS.register("fertilized_farmland_rich_healthy", () -> new BlockItem(FERTILIZED_FARMLAND_RICH_HEALTHY.get(), new Item.Properties()));
    public static final DeferredItem<Item> FERTILIZED_FARMLAND_RICH_HEALTHY_STABLE_ITEM = ITEMS.register("fertilized_farmland_rich_healthy_stable", () -> new BlockItem(FERTILIZED_FARMLAND_RICH_HEALTHY_STABLE.get(), new Item.Properties()));
    @SuppressWarnings("unused")
    private static final Supplier<MapCodec<? extends IGlobalLootModifier>> RICH_BONUS = LOOT_MODIFIERS.register("rich_bonus", () -> RichBonusLootModifier.CODEC);
    public FertiliserForBlockheads(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        modBus.addListener(FertiliserForBlockheads::addToCreativeTabs);
        FarmingForBlockheadsConfigBridge.load();
    }
    private static int rollExtraCount(RandomSource rand, double chance)
    {
        if (chance <= 0) return 0;
        int guaranteed = (int) Math.floor(chance);
        double remainder = chance - guaranteed;
        return guaranteed + (rand.nextDouble() < remainder ? 1 : 0);
    }
    static final class ComboFarmlandBlock extends FertilizedFarmlandBlock
    {
        private final boolean stable;

        ComboFarmlandBlock(boolean stable)
        {
            super(Properties.ofFullCopy(Blocks.FARMLAND));
            this.stable = stable;
        }
        @Override
        public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction facing, BlockState plant)
        {
            TriState parent = super.canSustainPlant(state, level, pos, facing, plant);
            if (parent != TriState.DEFAULT) return parent;
            return facing == Direction.UP && plant.is(BlockTags.CROPS) ? TriState.TRUE : TriState.DEFAULT;
        }
        @Override
        public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float distance)
        {
            if (stable)
            {
                if (!level.isClientSide) entity.causeFallDamage(distance, 1.0F, level.damageSources().fall());
                return;
            }
            super.fallOn(level, state, pos, entity, distance);
        }
        @Override
        public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
        {
            super.randomTick(state, level, pos, random);
            if (!level.getBlockState(pos).is(this)) return;
            if (state.is(FERTILIZED_FARMLAND_RICH_HEALTHY_STABLE.get())) return;
            double chance = FarmingForBlockheadsConfigBridge.regressionChance;
            if (chance <= 0) return;
            if (random.nextDouble() < chance)
            {
                BlockState target = Blocks.FARMLAND.defaultBlockState();
                if (state.hasProperty(MOISTURE) && target.hasProperty(MOISTURE)) target = target.setValue(MOISTURE, state.getValue(MOISTURE));
                level.setBlock(pos, target, 3);
            }
        }
    }
    @EventBusSubscriber(modid = FertiliserForBlockheads.MODID)
    public static final class ComboGrowthHandler
    {
        @SubscribeEvent
        public static void onCropGrowPost(CropGrowEvent.Post event)
        {
            LevelAccessor acc = event.getLevel();
            if (!(acc instanceof ServerLevel level)) return;
            BlockPos cropPos = event.getPos();
            BlockState cropState = event.getState();
            if (!(cropState.getBlock() instanceof CropBlock crop)) return;
            if (!cropState.is(BlockTags.CROPS)) return;
            BlockState soil = level.getBlockState(cropPos.below());
            boolean onOurSoil = soil.is(FertiliserForBlockheads.FERTILIZED_FARMLAND_RICH_HEALTHY.get()) || soil.is(FertiliserForBlockheads.FERTILIZED_FARMLAND_RICH_HEALTHY_STABLE.get());
            if (!onOurSoil) return;
            double chance = FarmingForBlockheadsConfigBridge.bonusGrowthChance;
            int extra = rollExtraCount(level.random, chance);
            if (extra <= 0) return;
            int age = crop.getAge(cropState);
            int maxAge = crop.getMaxAge();
            if (age >= maxAge) return;
            int newAge = Math.min(maxAge, age + extra);
            if (newAge == age) return;
            level.setBlock(cropPos, crop.getStateForAge(newAge), 2);
        }
    }
    static final class RichBonusLootModifier extends LootModifier
    {
        public static final MapCodec<RichBonusLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, RichBonusLootModifier::new));
        private RichBonusLootModifier(LootItemCondition[] conditions) { super(conditions); }
        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
        {
            BlockState harvested = context.getParamOrNull(LootContextParams.BLOCK_STATE);
            Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
            if (harvested == null || origin == null) return generatedLoot;
            if (!(harvested.getBlock() instanceof CropBlock) && !harvested.is(BlockTags.CROPS)) return generatedLoot;
            BlockPos pos = BlockPos.containing(origin);
            BlockState soil = context.getLevel().getBlockState(pos.below());
            boolean onOurSoil = soil.is(FERTILIZED_FARMLAND_RICH_HEALTHY.get()) || soil.is(FERTILIZED_FARMLAND_RICH_HEALTHY_STABLE.get());
            if (!onOurSoil) return generatedLoot;
            double chance = FarmingForBlockheadsConfigBridge.bonusCropChance;
            if (chance <= 0) return generatedLoot;
            RandomSource rand = context.getRandom();
            int baseSize = generatedLoot.size();
            for (int idx = 0; idx < baseSize; idx++)
            {
                ItemStack stack = generatedLoot.get(idx);
                if (stack.isEmpty()) continue;
                if (stack.is(Tags.Items.SEEDS)) continue;
                int add = rollExtraCount(rand, chance);
                applyExtraToDrop(generatedLoot, stack, add);
            }
            return generatedLoot;
        }
        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    }
    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event)
    {
        ResourceLocation tabId = event.getTabKey().location();
        if (!"farmingforblockheads".equals(tabId.getNamespace())) return;
        var visibility = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        Item richAnchor = BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmingforblockheads:fertilized_farmland_rich"));
        Item stableAnchor = BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmingforblockheads:fertilized_farmland_stable"));
        ItemStack richHealthy = new ItemStack(FERTILIZED_FARMLAND_RICH_HEALTHY_ITEM.get());
        ItemStack richHealthyStable = new ItemStack(FERTILIZED_FARMLAND_RICH_HEALTHY_STABLE_ITEM.get());
        event.insertAfter(new ItemStack(richAnchor), richHealthy, visibility);
        event.insertAfter(new ItemStack(stableAnchor), richHealthyStable, visibility);
    }
    static void applyExtraToDrop(List<ItemStack> drops, ItemStack stack, int add)
    {
        if (add <= 0) return;
        int room = stack.getMaxStackSize() - stack.getCount();
        int grow = Math.min(room, add);
        if (grow > 0) { stack.grow(grow); add -= grow; }
        while (add > 0)
        {
            int take = Math.min(add, stack.getMaxStackSize());
            ItemStack extra = stack.copy();
            extra.setCount(take);
            drops.add(extra);
            add -= take;
        }
    }
}
@EventBusSubscriber(modid = FertiliserForBlockheads.MODID)
final class WorldDatapackCleanup
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BOTANYPOTS_PACK_ID = FertiliserForBlockheads.MODID + "_generated_botanypots_soils";
    private WorldDatapackCleanup() {}
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event)
    {
        Path datapacksDir=event.getServer().getWorldPath(LevelResource.DATAPACK_DIR);
        if (!Files.isDirectory(datapacksDir))return;
        boolean changed=false;
        changed|=deleteRecursive(datapacksDir.resolve(BOTANYPOTS_PACK_ID));
        try { changed |= Files.deleteIfExists(datapacksDir.resolve(BOTANYPOTS_PACK_ID+".zip")); } catch (Exception ignored) {}
        if (changed) LOGGER.warn("[FertilizerForBlockheads] Removed stale world datapack '{}' so the generated config pack is used for this save.",BOTANYPOTS_PACK_ID);
    }
    private static boolean deleteRecursive(Path root) {
        if (!Files.exists(root)) return false;
        try (var walk=Files.walk(root)) {  walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(java.io.IOException ignored){}}); return true; }
        catch (Exception e)  {  LOGGER.warn("[FertilizerForBlockheads] Failed deleting {}",root.toAbsolutePath(),e); return false; }
    }
}