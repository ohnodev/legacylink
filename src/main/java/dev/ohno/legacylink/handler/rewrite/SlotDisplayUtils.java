package dev.ohno.legacylink.handler.rewrite;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class SlotDisplayUtils {

    private SlotDisplayUtils() {}

    public static boolean anyMatch(SlotDisplay display, Predicate<SlotDisplay> predicate) {
        if (predicate.test(display)) {
            return true;
        }
        if (display instanceof SlotDisplay.Composite composite) {
            for (SlotDisplay nested : composite.contents()) {
                if (anyMatch(nested, predicate)) {
                    return true;
                }
            }
            return false;
        }
        if (display instanceof SlotDisplay.WithRemainder withRemainder) {
            return anyMatch(withRemainder.input(), predicate) || anyMatch(withRemainder.remainder(), predicate);
        }
        if (display instanceof SlotDisplay.SmithingTrimDemoSlotDisplay smithingTrimDemo) {
            return anyMatch(smithingTrimDemo.base(), predicate) || anyMatch(smithingTrimDemo.material(), predicate);
        }
        if (display instanceof SlotDisplay.DyedSlotDemo dyedDemo) {
            return anyMatch(dyedDemo.dye(), predicate) || anyMatch(dyedDemo.target(), predicate);
        }
        if (display instanceof SlotDisplay.OnlyWithComponent onlyWithComponent) {
            return anyMatch(onlyWithComponent.source(), predicate);
        }
        if (display instanceof SlotDisplay.WithAnyPotion withAnyPotion) {
            return anyMatch(withAnyPotion.display(), predicate);
        }
        return false;
    }

    public static SlotDisplay rewrite(SlotDisplay display, UnaryOperator<SlotDisplay> transformer) {
        SlotDisplay transformed = transformer.apply(display);
        if (transformed != display) {
            return transformed;
        }
        if (display instanceof SlotDisplay.Composite composite) {
            List<SlotDisplay> remapped = rewriteNested(composite.contents(), transformer);
            if (remapped == composite.contents()) {
                return display;
            }
            return new SlotDisplay.Composite(remapped);
        }
        if (display instanceof SlotDisplay.WithRemainder withRemainder) {
            SlotDisplay input = rewrite(withRemainder.input(), transformer);
            SlotDisplay remainder = rewrite(withRemainder.remainder(), transformer);
            if (input == withRemainder.input() && remainder == withRemainder.remainder()) {
                return display;
            }
            return new SlotDisplay.WithRemainder(input, remainder);
        }
        if (display instanceof SlotDisplay.SmithingTrimDemoSlotDisplay smithingTrimDemo) {
            SlotDisplay base = rewrite(smithingTrimDemo.base(), transformer);
            SlotDisplay material = rewrite(smithingTrimDemo.material(), transformer);
            if (base == smithingTrimDemo.base() && material == smithingTrimDemo.material()) {
                return display;
            }
            return new SlotDisplay.SmithingTrimDemoSlotDisplay(base, material, smithingTrimDemo.pattern());
        }
        if (display instanceof SlotDisplay.DyedSlotDemo dyedDemo) {
            SlotDisplay dye = rewrite(dyedDemo.dye(), transformer);
            SlotDisplay target = rewrite(dyedDemo.target(), transformer);
            if (dye == dyedDemo.dye() && target == dyedDemo.target()) {
                return display;
            }
            return new SlotDisplay.DyedSlotDemo(dye, target);
        }
        if (display instanceof SlotDisplay.OnlyWithComponent onlyWithComponent) {
            SlotDisplay source = rewrite(onlyWithComponent.source(), transformer);
            if (source == onlyWithComponent.source()) {
                return display;
            }
            DataComponentType<?> component = onlyWithComponent.component();
            return new SlotDisplay.OnlyWithComponent(source, component);
        }
        if (display instanceof SlotDisplay.WithAnyPotion withAnyPotion) {
            SlotDisplay nested = rewrite(withAnyPotion.display(), transformer);
            if (nested == withAnyPotion.display()) {
                return display;
            }
            return new SlotDisplay.WithAnyPotion(nested);
        }
        return display;
    }

    public static List<SlotDisplay> rewriteNested(List<SlotDisplay> displays, UnaryOperator<SlotDisplay> transformer) {
        List<SlotDisplay> remapped = new ArrayList<>(displays.size());
        boolean changed = false;
        for (SlotDisplay nested : displays) {
            SlotDisplay mapped = rewrite(nested, transformer);
            if (mapped != nested) {
                changed = true;
            }
            remapped.add(mapped);
        }
        return changed ? remapped : displays;
    }
}
