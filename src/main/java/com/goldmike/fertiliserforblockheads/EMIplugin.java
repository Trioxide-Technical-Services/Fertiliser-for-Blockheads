package com.goldmike.fertiliserforblockheads;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
@SuppressWarnings("unused")
@EmiEntrypoint
public final class EMIplugin implements EmiPlugin
{
    @Override
    public void register(EmiRegistry registry)
    {
        for (RecipeHolder<CraftingRecipe> holder : registry.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))
        {
            CraftingRecipe recipe = holder.value();
            if (recipe.getSerializer() != ModRecipes.FARMLAND.get()) continue;
            List<EmiIngredient> inputs = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).map(EmiIngredient::of).toList();
            registry.addRecipe(new EmiCraftingRecipe(inputs, EmiStack.of(Blocks.FARMLAND), holder.id()));
        }
    }
}