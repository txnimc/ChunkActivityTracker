package toni.chunkactivitytracker.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import toni.chunkactivitytracker.ChunkActivityTracker;

#if mc >= 211
import net.minecraft.network.codec.StreamCodec;
#else
import toni.lib.networking.codecs.StreamCodec;
#endif

public class ChunkActivityMap implements Serializable {
    public static ConcurrentHashMap<ResourceKey<Level>, ChunkActivityMap> instances = new ConcurrentHashMap<>();

    public static StreamCodec<ByteBuf, ChunkActivityMap> CODEC = new StreamCodec<>() {
        public ChunkActivityMap decode(ByteBuf buffer) {
            FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
            var entries = buf.readMap(FriendlyByteBuf::readLong, (a) -> ChunkActivityInfo.CODEC.decode(a));
            var dimension = buf.readUtf();
            return new ChunkActivityMap(entries, dimension);
        }

        public void encode(ByteBuf buffer, ChunkActivityMap map) {
            FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
            buf.writeMap(map.chunks, FriendlyByteBuf::writeLong, (a, b) -> ChunkActivityInfo.CODEC.encode(a, b));
            buf.writeUtf(map.dimension);
        }
    };

    @Getter private final ConcurrentHashMap<Long, ChunkActivityInfo> chunks;
    @Getter private String dimension;

    public static void clear() {
        instances.clear();
        ChunkActivityTracker.LOGGER.info("Server stopping, clearing chunk activity tracking map.");
    }

    public static ChunkActivityMap getOrCreateChunkMap(ResourceKey<Level> level) {
        var map = instances.getOrDefault(level, null);
        if (map != null)
            return map;

        var ret = ChunkActivityMap.load(level);
        ret.save();
        instances.put(level, ret);
        return ret;
    }

    public static ChunkActivityInfo getChunkInfo(ResourceKey<Level> level, ChunkPos chunkPos) {
        var map = ChunkActivityMap.getOrCreateChunkMap(level);
        return map.chunks.getOrDefault(chunkPos.toLong(), null);
    }

    public static ChunkActivityInfo getOrCreateChunkInfo(LevelChunk chunk) {
        var dimension = chunk.getLevel().dimension();
        var chunkInfo = getChunkInfo(dimension, chunk.getPos());
        if (chunkInfo != null)
            return chunkInfo;

        var map = ChunkActivityMap.getOrCreateChunkMap(dimension);
        chunkInfo = new ChunkActivityInfo(chunk);
        map.chunks.put(chunk.getPos().toLong(), chunkInfo);
        return chunkInfo;
    }

    public static ChunkActivityInfo createChunkInfo(ResourceKey<Level> dimension, LevelChunk chunk) {
        var map = ChunkActivityMap.getOrCreateChunkMap(dimension);
        var chunkInfo = new ChunkActivityInfo(chunk);
        map.chunks.put(chunk.getPos().toLong(), chunkInfo);
        return chunkInfo;
    }

    public static ChunkActivityInfo createChunkInfo(ResourceKey<Level> level, ChunkPos chunk) {
        var map = ChunkActivityMap.getOrCreateChunkMap(level);
        var chunkInfo = new ChunkActivityInfo(new HashMap<>(), new HashMap<>(), null);
        map.chunks.put(chunk.toLong(), chunkInfo);
        return chunkInfo;
    }


    public ChunkActivityMap(Map<Long, ChunkActivityInfo> chunks, String dimension) {
        this.chunks = chunks != null ? new ConcurrentHashMap<>(chunks) : new ConcurrentHashMap<>();
        this.dimension = dimension;
    }

    public void save() {
        long startTime = System.currentTimeMillis();

        Path filePath = datafile(dimension);
        if (filePath == null) {
            ChunkActivityTracker.LOGGER.error("Could not get chunk activity data location!");
            return;
        }

        try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile()); GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut)) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                CODEC.encode(buffer, this);
                gzipOut.write(buffer.array(), buffer.readerIndex(), buffer.readableBytes());
                gzipOut.flush();
            } finally {
                buffer.release();
            }
        } catch (Exception e) {
            ChunkActivityTracker.LOGGER.error("Error when saving chunk activity info: " + e.getMessage());
        }

        long endTime = System.currentTimeMillis();
        ChunkActivityTracker.LOGGER.info("Saving data for " + chunks.size() + " chunks for dimension '" + dimension + "' took " + (endTime - startTime) + " milliseconds");
    }

    public static ChunkActivityMap load(ResourceKey<Level> dimension) {
        long startTime = System.currentTimeMillis();

        var dimPath = dimension.location().getPath();
        Path filePath = datafile(dimPath);

        if (filePath == null || !Files.exists(filePath)) {
            System.out.println("No saved ChunkActivityMap found. Creating a new one!");
            var ret = new ChunkActivityMap(new HashMap<>(), dimPath);
            instances.put(dimension, ret);
            return ret;
        }

        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(Files.readAllBytes(filePath)); GZIPInputStream gzipIn = new GZIPInputStream(byteIn)) {
            byte[] decompressedData = gzipIn.readAllBytes();
            ByteBuf buffer = Unpooled.wrappedBuffer(decompressedData);

            try {
                ChunkActivityMap map = CODEC.decode(buffer);
                System.out.println("ChunkActivityMap loaded successfully (decompressed).");
                instances.put(dimension, map);

                map.dimension = dimPath;

                long endTime = System.currentTimeMillis();
                ChunkActivityTracker.LOGGER.info("Loading data for " + map.chunks.size() + " chunks for filePath '" + filePath + "' took " + (endTime - startTime) + " milliseconds");

                return map;
            } finally {
                buffer.release();
            }
        } catch (Exception e) {
            ChunkActivityTracker.LOGGER.error("Error when loading chunk activity info: " + e.getMessage());

            var ret = new ChunkActivityMap(new HashMap<>(), dimPath);
            instances.put(dimension, ret);
            return ret;
        }
    }

    public static Path datafile(String dimension) {
        var dir = ChunkActivityTracker.getWorldPath(new LevelResource("chunk_activity_info/"));
        if (dir == null)
            return null;

        dir.toFile().mkdirs();

        return ChunkActivityTracker.getWorldPath(new LevelResource("chunk_activity_info/" + dimension + ".dat"));
    }
}
