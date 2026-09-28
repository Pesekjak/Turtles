package dev.pesek.turtles;

import com.google.common.base.Preconditions;
import org.bukkit.NamespacedKey;

public interface TurtlesKeys {

    static NamespacedKey key(String key) {
        Preconditions.checkNotNull(key, "key");
        return new NamespacedKey(Turtles.getInstance(), key);
    }

    interface Items {

        NamespacedKey COMPUTER = key("computer");
        NamespacedKey MONITOR = key("monitor");
        NamespacedKey DISK_DRIVE = key("disk_drive");
        NamespacedKey FLOPPY_DISK = key("floppy_disk");
        NamespacedKey WIRELESS_MODEM = key("wireless_modem");
        NamespacedKey TURTLE = key("turtle");

    }

    interface Pages {

        NamespacedKey COMPUTERS = key("computers");

    }

}
