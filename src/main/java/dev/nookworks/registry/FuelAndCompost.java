package dev.nookworks.registry;

/**
 * Which of our items burn in a furnace, and which a composter takes. In 26.3 both are components an item carries
 * (minecraft:cooking_fuel, minecraft:compostable) against vanilla's named amounts, as its own items are given them;
 * until then they were NeoForge's furnace_fuels and compostables data maps, and for the wood the vanilla tags it is in.
 * The amounts are the ones those gave: the wood as vanilla's own pieces of each kind, a support as a fence, a bucket of
 * oil or biodiesel 16,000 ticks (a block of coal's), the compost chances 30, 50, 65 and 85 in a hundred. The wooden
 * furniture burns as a crafting table does (r21: it caught fire but no furnace took it); a wall shelf, on iron
 * brackets, does not, nor the Crate, which keeps what it holds when broken: a furnace would burn it with its contents.
 *
 * PORT: 1.21.1 has no such item components. The same values are in the data maps
 * data/neoforge/data_maps/item/furnace_fuels.json and compostables.json (written from 26.3's list here: the wood
 * pieces, the supports and furniture but crimson and warped, the two oil buckets; the four compost chances), so this
 * class sets nothing any more. It is kept, empty, so the 26.3 line's changes to the lists have a place to land: a
 * change there goes into those two JSON files.
 */
public final class FuelAndCompost {
    private FuelAndCompost() {}
}
