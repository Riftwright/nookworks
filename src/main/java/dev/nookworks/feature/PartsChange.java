package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;

/**
 * What a change of parts takes with it, for the parts list (the user, 2026-11-01: 「「これをオフにしたら、これとコレが作れなくなるよ」
 * って警告を出したうえで、オフにすること自体はできる」): the items it leaves nothing to reach, and the parts left with nothing
 * of theirs to be had. Here, not in the screen, so the GameTests can ask it.
 */
public final class PartsChange {
    private PartsChange() {}

    /**
     * The items still on by their own parts after the change that nothing reaches any more, which could be had before
     * it: what switching off {@code before}'s parts that {@code after} has off takes besides their own items. Sorted.
     */
    public static List<String> newlyLost(Predicate<String> before, Predicate<String> after, Collection<String> kept) {
        Set<String> lostBefore = PartGraph.lost(before, kept);
        List<String> lost = new ArrayList<>();
        for (String id : PartGraph.lost(after, kept)) {
            if (!lostBefore.contains(id) && Features.enabledByPart(id, before)) {
                lost.add(id);
            }
        }
        return lost;
    }

    /**
     * Whether part {@code part}, on as {@code on} has it, is left with nothing of its own to be had - every item of it
     * in {@code lost} (the Printing Press and its posters and labels with the Electrolytic Cell off): shown off.
     */
    public static boolean taken(String part, Predicate<String> on, Set<String> lost) {
        List<Identifier> items = Features.PARTS.getOrDefault(part, List.of());
        return !items.isEmpty() && Features.enabled(part, on) && items.stream().allMatch(id -> lost.contains(id.toString()));
    }
}
