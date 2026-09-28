package dev.pesek.turtles.util;

import org.bukkit.Bukkit;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;

public final class PersistentDataContainerFactory {

    public static PersistentDataContainer empty() {
        PersistentDataAdapterContext adapterContext = Bukkit.getItemFactory()
                .getItemMeta(org.bukkit.Material.STONE).getPersistentDataContainer().getAdapterContext();
        return adapterContext.newPersistentDataContainer();
    }

    private PersistentDataContainerFactory() {
        throw new UnsupportedOperationException();
    }

}
