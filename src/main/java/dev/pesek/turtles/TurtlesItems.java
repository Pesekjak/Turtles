package dev.pesek.turtles;

import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;

public interface TurtlesItems {

    ItemStack COMPUTER = ItemStackBuilder.rebar(Material.LOOM, TurtlesKeys.Items.COMPUTER)
            .editDataOrSet(DataComponentTypes.MAX_STACK_SIZE, _ -> 1)
            .editDataOrSet(DataComponentTypes.RARITY, _ -> ItemRarity.UNCOMMON)
            .build();

    ItemStack TURTLE = ItemStackBuilder.rebar(Material.DRIED_GHAST, TurtlesKeys.Items.TURTLE)
            .editDataOrSet(DataComponentTypes.MAX_STACK_SIZE, _ -> 1)
            .editDataOrSet(DataComponentTypes.RARITY, _ -> ItemRarity.RARE)
            .build();

    static void init() {
        RebarItem.register(RebarItem.class, COMPUTER, TurtlesKeys.Items.COMPUTER);
        TurtlesPages.COMPUTERS.addItem(COMPUTER);

        RebarItem.register(RebarItem.class, TURTLE, TurtlesKeys.Items.TURTLE);
        TurtlesPages.COMPUTERS.addItem(TURTLE);
    }

}
