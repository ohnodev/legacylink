package dev.ohno.legacylink.handler.rewrite;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Rewrites recipe-book entries so every item-bearing display path is safe for 26.1 item wire ids.
 */
public final class RecipeBookAddRewriter {

    private RecipeBookAddRewriter() {}

    public static ClientboundRecipeBookAddPacket rewrite(ClientboundRecipeBookAddPacket packet) {
        List<ClientboundRecipeBookAddPacket.Entry> entries = packet.entries();
        if (entries.isEmpty()) {
            return packet;
        }

        List<ClientboundRecipeBookAddPacket.Entry> rewritten = new ArrayList<>(entries.size());
        boolean changed = false;

        for (ClientboundRecipeBookAddPacket.Entry entry : entries) {
            RecipeDisplayEntry contents = entry.contents();
            RecipeDisplay remappedDisplay = remapDisplay(contents.display());
            Optional<List<Ingredient>> remappedRequirements = remapCraftingRequirements(contents.craftingRequirements());

            if (remappedDisplay != contents.display() || remappedRequirements != contents.craftingRequirements()) {
                changed = true;
                RecipeDisplayEntry remappedContents = new RecipeDisplayEntry(
                        contents.id(),
                        remappedDisplay,
                        contents.group(),
                        contents.category(),
                        remappedRequirements
                );
                rewritten.add(new ClientboundRecipeBookAddPacket.Entry(remappedContents, entry.flags()));
            } else {
                rewritten.add(entry);
            }
        }

        if (!changed) {
            return packet;
        }
        return new ClientboundRecipeBookAddPacket(rewritten, packet.replace());
    }

    private static Optional<List<Ingredient>> remapCraftingRequirements(Optional<List<Ingredient>> requirements) {
        if (requirements.isEmpty()) {
            return requirements;
        }

        List<Ingredient> original = requirements.get();
        List<Ingredient> remapped = new ArrayList<>(original.size());
        boolean changed = false;

        for (Ingredient ingredient : original) {
            Ingredient remappedIngredient = remapIngredient(ingredient);
            if (remappedIngredient != ingredient) {
                changed = true;
            }
            remapped.add(remappedIngredient);
        }

        if (!changed) {
            return requirements;
        }
        return Optional.of(remapped);
    }

    public static Ingredient remapIngredient(Ingredient ingredient) {
        List<Item> remappedItems = new ArrayList<>();
        boolean changed = false;

        for (var holder : ingredient.items().toList()) {
            Item mapped = ItemRewriter.remapItemForLegacyRegistryEncoding(holder.value());
            if (mapped != holder.value()) {
                changed = true;
            }
            remappedItems.add(mapped);
        }

        if (!changed) {
            return ingredient;
        }
        return Ingredient.of(remappedItems.stream());
    }

    private static RecipeDisplay remapDisplay(RecipeDisplay display) {
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            List<SlotDisplay> ingredients = remapSlotDisplays(shapeless.ingredients());
            SlotDisplay result = remapSlotDisplay(shapeless.result());
            SlotDisplay station = remapSlotDisplay(shapeless.craftingStation());
            if (ingredients == shapeless.ingredients() && result == shapeless.result() && station == shapeless.craftingStation()) {
                return display;
            }
            return new ShapelessCraftingRecipeDisplay(ingredients, result, station);
        }
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            List<SlotDisplay> ingredients = remapSlotDisplays(shaped.ingredients());
            SlotDisplay result = remapSlotDisplay(shaped.result());
            SlotDisplay station = remapSlotDisplay(shaped.craftingStation());
            if (ingredients == shaped.ingredients() && result == shaped.result() && station == shaped.craftingStation()) {
                return display;
            }
            return new ShapedCraftingRecipeDisplay(shaped.width(), shaped.height(), ingredients, result, station);
        }
        if (display instanceof FurnaceRecipeDisplay furnace) {
            SlotDisplay ingredient = remapSlotDisplay(furnace.ingredient());
            SlotDisplay fuel = remapSlotDisplay(furnace.fuel());
            SlotDisplay result = remapSlotDisplay(furnace.result());
            SlotDisplay station = remapSlotDisplay(furnace.craftingStation());
            if (ingredient == furnace.ingredient()
                    && fuel == furnace.fuel()
                    && result == furnace.result()
                    && station == furnace.craftingStation()) {
                return display;
            }
            return new FurnaceRecipeDisplay(ingredient, fuel, result, station, furnace.duration(), furnace.experience());
        }
        if (display instanceof StonecutterRecipeDisplay stonecutter) {
            SlotDisplay input = remapSlotDisplay(stonecutter.input());
            SlotDisplay result = remapSlotDisplay(stonecutter.result());
            SlotDisplay station = remapSlotDisplay(stonecutter.craftingStation());
            if (input == stonecutter.input() && result == stonecutter.result() && station == stonecutter.craftingStation()) {
                return display;
            }
            return new StonecutterRecipeDisplay(input, result, station);
        }
        if (display instanceof SmithingRecipeDisplay smithing) {
            SlotDisplay template = remapSlotDisplay(smithing.template());
            SlotDisplay base = remapSlotDisplay(smithing.base());
            SlotDisplay addition = remapSlotDisplay(smithing.addition());
            SlotDisplay result = remapSlotDisplay(smithing.result());
            SlotDisplay station = remapSlotDisplay(smithing.craftingStation());
            if (template == smithing.template()
                    && base == smithing.base()
                    && addition == smithing.addition()
                    && result == smithing.result()
                    && station == smithing.craftingStation()) {
                return display;
            }
            return new SmithingRecipeDisplay(template, base, addition, result, station);
        }
        return display;
    }

    private static List<SlotDisplay> remapSlotDisplays(List<SlotDisplay> displays) {
        List<SlotDisplay> remapped = new ArrayList<>(displays.size());
        boolean changed = false;

        for (SlotDisplay display : displays) {
            SlotDisplay mapped = remapSlotDisplay(display);
            if (mapped != display) {
                changed = true;
            }
            remapped.add(mapped);
        }

        if (!changed) {
            return displays;
        }
        return remapped;
    }

    public static SlotDisplay remapSlotDisplay(SlotDisplay display) {
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            Item mapped = ItemRewriter.remapItemForLegacyRegistryEncoding(itemDisplay.item().value());
            if (mapped == itemDisplay.item().value()) {
                return display;
            }
            return new SlotDisplay.ItemSlotDisplay(mapped);
        }
        if (display instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            ItemStackTemplate remapped = ItemRewriter.remapTemplate(stackDisplay.stack());
            if (remapped == stackDisplay.stack()) {
                return display;
            }
            return new SlotDisplay.ItemStackSlotDisplay(remapped);
        }
        if (display instanceof SlotDisplay.Composite composite) {
            List<SlotDisplay> remapped = remapSlotDisplays(composite.contents());
            if (remapped == composite.contents()) {
                return display;
            }
            return new SlotDisplay.Composite(remapped);
        }
        if (display instanceof SlotDisplay.WithRemainder withRemainder) {
            SlotDisplay input = remapSlotDisplay(withRemainder.input());
            SlotDisplay remainder = remapSlotDisplay(withRemainder.remainder());
            if (input == withRemainder.input() && remainder == withRemainder.remainder()) {
                return display;
            }
            return new SlotDisplay.WithRemainder(input, remainder);
        }
        if (display instanceof SlotDisplay.SmithingTrimDemoSlotDisplay smithingTrimDemo) {
            SlotDisplay base = remapSlotDisplay(smithingTrimDemo.base());
            SlotDisplay material = remapSlotDisplay(smithingTrimDemo.material());
            if (base == smithingTrimDemo.base() && material == smithingTrimDemo.material()) {
                return display;
            }
            return new SlotDisplay.SmithingTrimDemoSlotDisplay(base, material, smithingTrimDemo.pattern());
        }
        if (display instanceof SlotDisplay.DyedSlotDemo dyedDemo) {
            SlotDisplay dye = remapSlotDisplay(dyedDemo.dye());
            SlotDisplay target = remapSlotDisplay(dyedDemo.target());
            if (dye == dyedDemo.dye() && target == dyedDemo.target()) {
                return display;
            }
            return new SlotDisplay.DyedSlotDemo(dye, target);
        }
        if (display instanceof SlotDisplay.OnlyWithComponent onlyWithComponent) {
            SlotDisplay source = remapSlotDisplay(onlyWithComponent.source());
            DataComponentType<?> component = onlyWithComponent.component();
            if (source == onlyWithComponent.source()) {
                return display;
            }
            return new SlotDisplay.OnlyWithComponent(source, component);
        }
        if (display instanceof SlotDisplay.WithAnyPotion withAnyPotion) {
            SlotDisplay nested = remapSlotDisplay(withAnyPotion.display());
            if (nested == withAnyPotion.display()) {
                return display;
            }
            return new SlotDisplay.WithAnyPotion(nested);
        }
        return display;
    }
}
