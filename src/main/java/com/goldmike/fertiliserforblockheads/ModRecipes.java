package com.goldmike.fertiliserforblockheads;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import javax.annotation.ParametersAreNonnullByDefault;
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ModRecipes
{
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, FertiliserForBlockheads.MODID);
    public static final RegistryObject<RecipeSerializer<?>> FARMLAND = SERIALIZERS.register("farmland", () -> new SimpleCraftingRecipeSerializer<>(farmland::new));
    private ModRecipes() {}
}
/**
 * Dirt + any hoe -> 1x minecraft:farmland
 * Hoe is returned with 1 durability damage.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
final class farmland extends CustomRecipe
{
    public farmland(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }
    @Override
    public boolean matches(CraftingContainer inv, Level level)
    {
        boolean foundDirt = false;
        boolean foundHoe = false;
        for (int i = 0; i < inv.getContainerSize(); i++)
        {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (!foundDirt && s.is(Items.DIRT))
            {
                foundDirt = true;
                continue;
            }
            if (!foundHoe && s.is(ItemTags.HOES))
            {
                foundHoe = true;
                continue;
            }
            // Any extra junk in the grid invalidates the recipe.
            return false;
        }
        return foundDirt && foundHoe;
    }
    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess regs) {
        return new ItemStack(Blocks.FARMLAND);
    }
    @Override
    public NonNullList<Ingredient> getIngredients()
    {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.DIRT));
        ingredients.add(Ingredient.of(ItemTags.HOES));
        return ingredients;
    }
    @Override
    public ItemStack getResultItem(RegistryAccess regs) {
        return new ItemStack(Blocks.FARMLAND);
    }
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv)
    {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inv.getContainerSize(); i++)
        {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ItemTags.HOES))
            {
                ItemStack copy = s.copy();
                // Damage by 1. If it breaks, it vanishes like any other tool.
                boolean broke = copy.hurt(1, RandomSource.create(), null);
                remaining.set(i, broke ? ItemStack.EMPTY : copy);
            }
            else if (s.hasCraftingRemainingItem()) { remaining.set(i, s.getCraftingRemainingItem().copy()); }
        }
        return remaining;
    }
    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }
    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FARMLAND.get();
    }
    @Override
    public boolean isSpecial() {
        // Show in recipe book / JEI.
        return false;
    }
}