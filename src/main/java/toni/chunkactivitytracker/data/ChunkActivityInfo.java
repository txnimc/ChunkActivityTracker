package toni.chunkactivitytracker.data;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import toni.chunkactivitytracker.foundation.config.AllConfigs;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

#if mc >= 211
import net.minecraft.network.codec.StreamCodec;
#else
import toni.lib.networking.codecs.StreamCodec;
#endif

public class ChunkActivityInfo implements Serializable {
    public static int CODEC_VERSION = 1;

    static StreamCodec<ByteBuf, ChunkActivityInfo> CODEC = new StreamCodec<>() {
        public ChunkActivityInfo decode(ByteBuf buffer) {
            FriendlyByteBuf buf = new FriendlyByteBuf(buffer);

            var verson = buf.readInt();
            var time = buf.readMap((key) -> key.readUUID(), FriendlyByteBuf::readLong);
            var blocks = buf.readMap((key) -> key.readUUID(), FriendlyByteBuf::readInt);

            var hasHeightMap = buf.readBoolean();
            if (hasHeightMap)
            {
                var height = buf.readLongArray();
                return new ChunkActivityInfo(time, blocks, height);
            } else {
                return new ChunkActivityInfo(time, blocks, null);
            }
        }

        public void encode(ByteBuf buffer, ChunkActivityInfo info) {
            FriendlyByteBuf buf = new FriendlyByteBuf(buffer);

            buf.writeInt(CODEC_VERSION);
            buf.writeMap(info.playerTimeMap, (a, b) -> a.writeUUID(b), FriendlyByteBuf::writeLong);
            buf.writeMap(info.blocksPlacedMap, (a, b) -> a.writeUUID(b), FriendlyByteBuf::writeInt);

            if (info.initialHeightmap != null) {
                buf.writeBoolean(true);
                buf.writeLongArray(info.initialHeightmap);
            } else {
                buf.writeBoolean(false);
            }
        }
    };


    @Getter private final ConcurrentHashMap<UUID, Long> playerTimeMap;
    @Getter private final ConcurrentHashMap<UUID, Integer> blocksPlacedMap;

    @Getter private long[] initialHeightmap;

    // Constructor
    public ChunkActivityInfo(Map<UUID, Long> playerTimeInSeconds, Map<UUID, Integer> blocksPlacedByPlayer, long[] initialHeightmap) {
        this.playerTimeMap = new ConcurrentHashMap<>(playerTimeInSeconds);
        this.blocksPlacedMap = new ConcurrentHashMap<>(blocksPlacedByPlayer);
        this.initialHeightmap = initialHeightmap;
    }

    public ChunkActivityInfo(LevelChunk chunk) {
        this.playerTimeMap = new ConcurrentHashMap<>();
        this.blocksPlacedMap = new ConcurrentHashMap<>();

        if (AllConfigs.server().storeHeightmaps.get())
        {
            var heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
            initialHeightmap = heightmap.getRawData();
        }
    }

    // Update player time in the chunk
    public void updatePlayerTime(UUID player) {
        playerTimeMap.merge(player, 1L, Long::sum); // Increment by 1 second per tick
    }

    public Long getPlayerTime(UUID player) {
        return playerTimeMap.getOrDefault(player, 0L);
    }

    // Increment block placement counter for a player
    public void incrementBlocksPlaced(UUID player) {
        blocksPlacedMap.merge(player, 1, Integer::sum);
    }
}

