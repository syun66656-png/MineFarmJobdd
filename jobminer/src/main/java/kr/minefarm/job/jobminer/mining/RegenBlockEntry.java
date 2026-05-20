package kr.minefarm.job.jobminer.mining;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 리젠 광산에 등록된 단일 블록 스냅샷.
 */
public final class RegenBlockEntry {

    private final String worldId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final Material material;
    private final String blockDataString;

    public RegenBlockEntry(
            String worldId,
            String worldName,
            int x,
            int y,
            int z,
            Material material,
            String blockDataString
    ) {
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = Objects.requireNonNull(material, "material");
        this.blockDataString = Objects.requireNonNull(blockDataString, "blockDataString");
    }

    public static RegenBlockEntry from(Block block) {
        Location location = block.getLocation();
        World world = Objects.requireNonNull(location.getWorld(), "world");
        BlockData data = block.getBlockData();
        return new RegenBlockEntry(
                world.getUID().toString(),
                world.getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                data.getMaterial(),
                data.getAsString()
        );
    }

    public String getWorldId() {
        return worldId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Material getMaterial() {
        return material;
    }

    public String getBlockDataString() {
        return blockDataString;
    }

    public BlockKey key() {
        return new BlockKey(worldId, x, y, z);
    }

    public Optional<World> resolveWorld() {
        World byId = Bukkit.getWorld(java.util.UUID.fromString(worldId));
        if (byId != null) {
            return Optional.of(byId);
        }
        return Optional.ofNullable(Bukkit.getWorld(worldName));
    }

    public Block resolveBlock() {
        return resolveWorld()
                .map(world -> world.getBlockAt(x, y, z))
                .orElse(null);
    }

    public BlockData createBlockData() {
        return Bukkit.createBlockData(blockDataString);
    }

    public void applyTo(Block block) {
        block.setBlockData(createBlockData(), false);
    }

    public record BlockKey(String worldId, int x, int y, int z) {
        public static BlockKey of(Block block) {
            Location location = block.getLocation();
            return new BlockKey(
                    Objects.requireNonNull(location.getWorld()).getUID().toString(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
        }
    }

    public static Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Material.STONE;
        }
    }
}
