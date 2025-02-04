package toni.chunkactivitytracker;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import toni.chunkactivitytracker.data.ChunkActivityMap;
import toni.chunkactivitytracker.foundation.config.AllConfigs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


#if FABRIC
    import net.fabricmc.api.ClientModInitializer;
    import net.fabricmc.api.ModInitializer;
    #if after_21_1
    import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
    import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
    import net.neoforged.neoforge.client.gui.ConfigurationScreen;
    #endif

    #if current_20_1
    import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
    #endif
#endif

#if FORGE
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
#endif


#if NEO
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
#endif

import java.nio.file.Path;


#if FORGELIKE
@Mod("chunkactivitytracker")
#endif
public class ChunkActivityTracker #if FABRIC implements ModInitializer, ClientModInitializer #endif
{
    public static final String MODNAME = "Chunk Activity Tracker";
    public static final String ID = "chunkactivitytracker";
    public static final Logger LOGGER = LogManager.getLogger(MODNAME);
    private static MinecraftServer currentServer;

    private static long lastTime = System.nanoTime();
    private static final float NANOSECONDS_PER_TICK = 1000000000.0f / 20; // 50 million ns per tick for 20 ticks per second

    public ChunkActivityTracker(#if NEO IEventBus modEventBus, ModContainer modContainer #endif) {
        #if FORGE
        var context = FMLJavaModLoadingContext.get();
        var modEventBus = context.getModEventBus();
        #endif

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
        });

        #if FORGELIKE
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        AllConfigs.register((type, spec) -> {
            #if FORGE
            ModLoadingContext.get().registerConfig(type, spec);
            #elif NEO
            modContainer.registerConfig(type, spec);
            //modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
            #endif
        });
        #endif
    }

    public static long getSecondsInCurrentChunk(Player player) {
        var level = player.level();
        if (level == null)
            return 0L;

        var chunkInfo = ChunkActivityMap.getChunkInfo(level.dimension(), player.chunkPosition());
        if (chunkInfo == null)
            return 0L;

        return chunkInfo.getPlayerTime(player.getUUID());
    }

    public static long getTotalTimeInChunk(Level level, ChunkPos chunkPos) {
        if (level == null)
            return 0L;

        var chunkInfo = ChunkActivityMap.getChunkInfo(level.dimension(), chunkPos);
        if (chunkInfo == null)
            return 0L;

        return chunkInfo.getPlayerTimeMap().values().stream().mapToLong(Long::longValue).sum();
    }


    public static Path getWorldPath(LevelResource resource) {
        if (currentServer == null) {
            System.out.println("No MinecraftServer instance available.");
            return null;
        }

        return currentServer.getWorldPath(resource);
    }


    #if FABRIC @Override #endif
    public void onInitialize() {
        #if FABRIC
            AllConfigs.register((type, spec) -> {
                #if AFTER_21_1
                NeoForgeConfigRegistry.INSTANCE.register(ChunkActivityTracker.ID, type, spec);
                #else
                ForgeConfigRegistry.INSTANCE.register(ChunkActivityTracker.ID, type, spec);
                #endif
            });
        #endif

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            ChunkActivityMap.clear();
        });

        UseBlockCallback.EVENT.register((player, block, hand, hit) -> {
            if (player.level().isClientSide)
                return InteractionResult.PASS;

            var chunkInfo = ChunkActivityMap.getChunkInfo(player.level().dimension(), player.chunkPosition());
            if (chunkInfo != null) {
                chunkInfo.incrementBlocksPlaced(player.getUUID());
            }

            return InteractionResult.PASS;
        });

        ServerTickEvents.START_SERVER_TICK.register(event -> {
            long currentTime = System.nanoTime();
            var delta = (currentTime - lastTime) / NANOSECONDS_PER_TICK;
            if (delta < 20)
                return;

            lastTime = currentTime;

            event.getPlayerList().getPlayers().forEach(player -> {
                var level = player.level();
                if (level == null)
                    return;

                var chunkInfo = ChunkActivityMap.getChunkInfo(level.dimension(), player.chunkPosition());
                if (chunkInfo == null) {
                    if (AllConfigs.server().storeHeightmaps.get()) {
                        var chunk = level.getChunkAt(player.blockPosition());
                        if (chunk == null)
                            return;
                        chunkInfo = ChunkActivityMap.createChunkInfo(level.dimension(), chunk);
                    } else {
                        chunkInfo = ChunkActivityMap.createChunkInfo(level.dimension(), player.chunkPosition());
                    }
                }

                chunkInfo.updatePlayerTime(player.getUUID());
            });
        });
    }

    #if FABRIC @Override #endif
    public void onInitializeClient() {
        #if AFTER_21_1
            #if FABRIC
            ConfigScreenFactoryRegistry.INSTANCE.register(ChunkActivityTracker.ID, ConfigurationScreen::new);
            #endif
        #endif
    }

    // Forg event stubs to call the Fabric initialize methods, and set up cloth config screen
    #if FORGELIKE
    public void commonSetup(FMLCommonSetupEvent event) { onInitialize(); }
    public void clientSetup(FMLClientSetupEvent event) { onInitializeClient(); }
    #endif
}
