package com.goldmike.fertiliserforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = FertiliserForBlockheads.MODID)
public final class FertiliserRightClickHandler
{
    private static final int RICH = 1;    // green
    private static final int HEALTHY = 2; // red
    private static final int STABLE = 4;  // yellow

    private static final ResourceLocation GREEN = rl("farmingforblockheads", "green_fertilizer");
    private static final ResourceLocation RED = rl("farmingforblockheads", "red_fertilizer");
    private static final ResourceLocation YELLOW = rl("farmingforblockheads", "yellow_fertilizer");

    private static final ResourceLocation VANILLA_FARMLAND = rl("minecraft", "farmland");
    private static final ResourceLocation FFB_RICH = rl("farmingforblockheads", "fertilized_farmland_rich");
    private static final ResourceLocation FFB_HEALTHY = rl("farmingforblockheads", "fertilized_farmland_healthy");
    private static final ResourceLocation FFB_STABLE = rl("farmingforblockheads", "fertilized_farmland_stable");
    private static final ResourceLocation FFB_RICH_STABLE = rl("farmingforblockheads", "fertilized_farmland_rich_stable");
    private static final ResourceLocation FFB_HEALTHY_STABLE = rl("farmingforblockheads", "fertilized_farmland_healthy_stable");
    private static final ResourceLocation OUR_RICH_HEALTHY = rl(FertiliserForBlockheads.MODID, "fertilized_farmland_rich_healthy");
    private static final ResourceLocation OUR_RICH_HEALTHY_STABLE = rl(FertiliserForBlockheads.MODID, "fertilized_farmland_rich_healthy_stable");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e)
    {
        Level level = e.getLevel();
        if (e.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = e.getItemStack();
        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(held.getItem());
        int add;
        if (heldId.equals(GREEN)) add = RICH;
        else if (heldId.equals(RED)) add = HEALTHY;
        else if (heldId.equals(YELLOW)) add = STABLE;
        else return;

        BlockPos pos = e.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FarmBlock))
        {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.getBlock() instanceof FarmBlock)
            {
                pos = below;
                state = belowState;
            }
            else
            {
                return;
            }
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        int current = flagsFromBlock(blockId);
        if (current < 0) return;

        int next = current | add;
        if (next == current)
        {
            if (isOurComboBlock(blockId))
            {
                cancelInteraction(e, InteractionResult.FAIL);
            }
            return;
        }

        ResourceLocation targetId = blockFromFlags(next);
        if (targetId == null) return;

        cancelInteraction(e, InteractionResult.SUCCESS);
        if (level.isClientSide()) return;

        var targetBlock = BuiltInRegistries.BLOCK.get(targetId);
        BlockState newState = targetBlock.defaultBlockState();
        if (state.hasProperty(FarmBlock.MOISTURE) && newState.hasProperty(FarmBlock.MOISTURE))
        {
            newState = newState.setValue(FarmBlock.MOISTURE, state.getValue(FarmBlock.MOISTURE));
        }

        level.setBlock(pos, newState, 3);
        if (!e.getEntity().getAbilities().instabuild)
        {
            held.shrink(1);
        }
    }

    private static void cancelInteraction(PlayerInteractEvent.RightClickBlock e, InteractionResult result)
    {
        e.setUseBlock(TriState.FALSE);
        e.setUseItem(TriState.FALSE);
        e.setCanceled(true);
        e.setCancellationResult(result);
    }

    private static int flagsFromBlock(ResourceLocation id)
    {
        if (id.equals(VANILLA_FARMLAND)) return 0;
        if (id.equals(FFB_RICH)) return RICH;
        if (id.equals(FFB_HEALTHY)) return HEALTHY;
        if (id.equals(FFB_STABLE)) return STABLE;
        if (id.equals(FFB_RICH_STABLE)) return RICH | STABLE;
        if (id.equals(FFB_HEALTHY_STABLE)) return HEALTHY | STABLE;
        if (id.equals(OUR_RICH_HEALTHY)) return RICH | HEALTHY;
        if (id.equals(OUR_RICH_HEALTHY_STABLE)) return RICH | HEALTHY | STABLE;
        return -1;
    }

    private static boolean isOurComboBlock(ResourceLocation id)
    {
        return id.equals(OUR_RICH_HEALTHY) || id.equals(OUR_RICH_HEALTHY_STABLE);
    }

    private static ResourceLocation blockFromFlags(int flags)
    {
        return switch (flags)
        {
            case 0 -> VANILLA_FARMLAND;
            case RICH -> FFB_RICH;
            case HEALTHY -> FFB_HEALTHY;
            case STABLE -> FFB_STABLE;
            case (RICH | STABLE) -> FFB_RICH_STABLE;
            case (HEALTHY | STABLE) -> FFB_HEALTHY_STABLE;
            case (RICH | HEALTHY) -> OUR_RICH_HEALTHY;
            case (RICH | HEALTHY | STABLE) -> OUR_RICH_HEALTHY_STABLE;
            default -> null;
        };
    }

    private static ResourceLocation rl(String namespace, String path)
    {
        ResourceLocation id = ResourceLocation.tryBuild(namespace, path);
        if (id == null) throw new IllegalArgumentException("Invalid ResourceLocation: " + namespace + ":" + path);
        return id;
    }

    private FertiliserRightClickHandler() {}
}