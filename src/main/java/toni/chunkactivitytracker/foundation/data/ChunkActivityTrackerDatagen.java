package toni.chunkactivitytracker.foundation.data;

#if FABRIC
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import toni.chunkactivitytracker.ChunkActivityTracker;

public class ChunkActivityTrackerDatagen  implements DataGeneratorEntrypoint {

    @Override
    public String getEffectiveModId() {
        return ChunkActivityTracker.ID;
    }

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        var pack = fabricDataGenerator.createPack();
        pack.addProvider(ConfigLangDatagen::new);
    }
}
#endif