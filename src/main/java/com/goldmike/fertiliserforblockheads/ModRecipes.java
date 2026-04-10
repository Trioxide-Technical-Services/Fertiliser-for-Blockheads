package com.goldmike.fertiliserforblockheads;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ModRecipes
{
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FertiliserForBlockheads.MODID);
    public static final Supplier<RecipeSerializer<?>> FARMLAND = SERIALIZERS.register("farmland", () -> new SimpleCraftingRecipeSerializer<>(farmland::new));
    private ModRecipes() {}
}
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
final class farmland extends CustomRecipe
{
    public farmland(CraftingBookCategory category) { super(category); }
    @Override
    public boolean matches(CraftingInput inv, Level level)
    {
        boolean foundDirt = false;
        boolean foundHoe = false;
        for (int i = 0; i < inv.size(); i++)
        {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (!foundDirt && s.is(Items.DIRT))  { foundDirt = true; continue; }
            if (!foundHoe && s.is(ItemTags.HOES)) { foundHoe = true; continue; }
            return false;
        }
        return foundDirt && foundHoe;
    }
    @Override
    public ItemStack assemble(CraftingInput inv, HolderLookup.Provider regs) { return new ItemStack(Items.FARMLAND); }
    @Override
    public boolean canCraftInDimensions(int w, int h) { return w * h >= 2; }
    @Override
    public NonNullList<Ingredient> getIngredients()
    {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.DIRT));
        ingredients.add(Ingredient.of(ItemTags.HOES));
        return ingredients;
    }
    @Override
    public ItemStack getResultItem(HolderLookup.Provider regs) {
        return new ItemStack(Blocks.FARMLAND);
    }
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput inv)
    {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.size(), ItemStack.EMPTY);
        for (int i = 0; i < inv.size(); i++)
        {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ItemTags.HOES))
            {
                ItemStack copy = s.copy();
                copy.setDamageValue(copy.getDamageValue() + 1);
                boolean broke = copy.getDamageValue() >= copy.getMaxDamage();
                remaining.set(i, broke ? ItemStack.EMPTY : copy);
            }
            else if (s.hasCraftingRemainingItem()) { remaining.set(i, s.getCraftingRemainingItem().copy()); }
        }
        return remaining;
    }
    @Override
    public RecipeSerializer<?> getSerializer() { return ModRecipes.FARMLAND.get(); }
}