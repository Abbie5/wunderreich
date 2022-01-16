package de.ambertation.wunderreich.recipes;

import de.ambertation.wunderreich.Wunderreich;
import de.ambertation.wunderreich.advancements.AdvancementsJsonBuilder;
import de.ambertation.wunderreich.config.Configs;
import de.ambertation.wunderreich.registries.WunderreichRecipes;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

public class RecipeJsonBuilder {
    private final ResourceLocation ID;
    private boolean canBuild;

    private RecipeJsonBuilder(ResourceLocation ID) {
        this.ID = ID;
        canBuild = Configs.RECIPE_CONFIG.newBooleanFor(ID.getPath(), ID).get();
    }

    private static boolean isEnabled(ItemLike item) {
        if (item instanceof Block bl) {
            return Configs.BLOCK_CONFIG.isEnabled(bl);
        } else if (item instanceof Item itm) {
            return Configs.ITEM_CONFIG.isEnabled(itm);
        }
        return false;
    }

    private static ResourceLocation getKey(ItemLike item) {
        if (item instanceof Block bl) {
            return Registry.BLOCK.getKey(bl);
        } else if (item instanceof Item itm) {
            return Registry.ITEM.getKey(itm);
        }
        return new ResourceLocation("failed");
    }


    public static RecipeJsonBuilder create(String name) {
        ResourceLocation id = Wunderreich.ID(name);
        RecipeJsonBuilder b = new RecipeJsonBuilder(id);
        return b;
    }

    private ItemLike resultItem;

    public RecipeJsonBuilder result(ItemLike item) {
        canBuild &= isEnabled(item);
        this.resultItem = item;
        return this;
    }

    private String[] pattern = new String[3];

    public RecipeJsonBuilder pattern(String row1, String row2, String row3) {
        this.pattern = new String[]{row1, row2, row3};
        return this;
    }

    public RecipeJsonBuilder pattern(String row1, String row2) {
        this.pattern = new String[]{row1, row2};
        return this;
    }

    public RecipeJsonBuilder pattern(String row1) {
        this.pattern = new String[]{row1};
        return this;
    }

    private Map<Character, Ingredient> materials = new HashMap<>();

    public RecipeJsonBuilder material(Character c, ItemLike... items) {
        return material(c, Ingredient.of(items));
    }

    public RecipeJsonBuilder material(Character c, ItemStack... items) {
        return material(c, Ingredient.of(items));
    }

    public RecipeJsonBuilder material(Character c, Ingredient ing) {
        canBuild &= Arrays
                .stream(ing.getItems())
                .map(ItemStack::getItem)
                .map(RecipeJsonBuilder::isEnabled)
                .noneMatch(v -> v == false);

        materials.put(c, ing);
        return this;
    }

    private int count = 1;

    public RecipeJsonBuilder count(int count) {
        this.count = count;
        return this;
    }

    public boolean canBuild() {
        return canBuild;
    }

    public JsonElement registerAndCreateAdvancement(AdvancementsJsonBuilder.AdvancementType type) {
        List<Item> items = new ArrayList<>(materials.size());
        for (var mat : materials.values()) {
            for (var item : mat.getItems()) {
                items.add(item.getItem());
            }
        }
        return registerAndCreateAdvancement(type, items);
    }

    public JsonElement registerAndCreateAdvancement(AdvancementsJsonBuilder.AdvancementType type, List<Item> items) {
        JsonElement res = register();
        if (res == null) return null;

        AdvancementsJsonBuilder b = null;
        if (resultItem instanceof Block bl) {
            b = AdvancementsJsonBuilder.createRecipe(bl.asItem(), type);
        } else if (resultItem instanceof Item itm) {
            b = AdvancementsJsonBuilder.createRecipe(itm, type);
        }

        if (b != null) {
            int ct = 0;
            for (var item : items) {
                final String name = "has_" + ct++;
                b.inventoryChangedCriteria(name, item);
            }

            b.register();
        }
        return res;
    }

    public JsonElement register() {
        if (!canBuild) return null;

        JsonElement res = build();
        WunderreichRecipes.RECIPES.put(ID, res);

        return res;
    }


    public JsonElement build() {
        if (!canBuild) return null;

        if (resultItem == null) {
            throw new IllegalStateException("A Recipe needs a Result (" + ID + ")");
        }

        JsonObject json = new JsonObject();

        json.addProperty("type", "minecraft:crafting_shaped");


        JsonArray jsonArray = new JsonArray();
        for (int i = 0; i < pattern.length; i++)
            jsonArray.add(pattern[i]);
        json.add("pattern", jsonArray);

        JsonObject individualKey;
        JsonArray individualContainer;
        JsonObject keyList = new JsonObject();

        for (var mat : materials.entrySet()) {
            Ingredient ing = mat.getValue();
            ItemStack[] items = ing.getItems();

            individualContainer = new JsonArray();
            for (ItemStack stack : items) {
                individualKey = new JsonObject();
                individualKey.addProperty("item", getKey(stack.getItem()).toString());
                if (stack.getCount() > 1) {
                    individualKey.addProperty("count", stack.getCount());
                }
                individualContainer.add(individualKey);
            }
            keyList.add(mat.getKey() + "", individualContainer);
        }
        json.add("key", keyList);

        JsonObject result = new JsonObject();
        result.addProperty("item", getKey(resultItem).toString());
        result.addProperty("count", count);
        json.add("result", result);

        //System.out.println(json);
        return json;
    }
}
