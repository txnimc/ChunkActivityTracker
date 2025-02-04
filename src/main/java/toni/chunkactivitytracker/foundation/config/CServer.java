package toni.chunkactivitytracker.foundation.config;

import toni.lib.config.ConfigBase;

public class CServer extends ConfigBase {

    public final ConfigBool storeHeightmaps = b(false, "Store Heightmap Info", "Store initial chunk heightmap data. Useful for some mods, disabled by default as it takes up much more space.");

    @Override
    public String getName() {
        return "server";
    }
}
