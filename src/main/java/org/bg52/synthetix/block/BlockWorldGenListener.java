package org.bg52.synthetix.block;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.List;
import java.util.Random;

/**
 * Natural world generation & ore vein generator for custom blocks.
 * Intercepts newly generated chunks and spawns custom ore veins seamlessly.
 */
public class BlockWorldGenListener implements Listener {

    private final SynthetixPlugin plugin;

    public BlockWorldGenListener(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // Check each custom block with world-gen enabled
        for (CustomBlockDefinition def : plugin.getCustomBlockRegistry().getAll()) {
            BlockWorldGenSpec gen = def.getWorldGen();
            if (!gen.isEnabled()) continue;

            // Pseudo-random seed derived from world seed, chunk coords and block ID
            long seed = world.getSeed() ^ ((long) chunk.getX() * 341873128712L) ^ ((long) chunk.getZ() * 132897987541L) ^ def.getId().hashCode();
            Random random = new Random(seed);

            int veins = gen.getVeinsPerChunk();
            int minY = Math.max(world.getMinHeight(), gen.getMinHeight());
            int maxY = Math.min(world.getMaxHeight() - 1, gen.getMaxHeight());
            if (minY >= maxY) continue;

            List<String> allowedBiomes = gen.getBiomes();
            Material target = gen.getReplaceTarget();
            int veinSize = gen.getVeinSize();

            for (int v = 0; v < veins; v++) {
                int startX = (chunk.getX() << 4) + random.nextInt(16);
                int startZ = (chunk.getZ() << 4) + random.nextInt(16);
                int startY = minY + random.nextInt(maxY - minY + 1);

                Location origin = new Location(world, startX, startY, startZ);

                // Check biome constraint if specified
                if (!allowedBiomes.isEmpty()) {
                    String biomeName = origin.getBlock().getBiome().name();
                    boolean biomeMatch = false;
                    for (String b : allowedBiomes) {
                        if (biomeName.equalsIgnoreCase(b)) {
                            biomeMatch = true;
                            break;
                        }
                    }
                    if (!biomeMatch) continue;
                }

                // Generate random cluster vein
                generateVein(origin, def, target, veinSize, random);
            }
        }
    }

    private void generateVein(Location origin, CustomBlockDefinition def, Material target, int size, Random random) {
        World world = origin.getWorld();
        int curX = origin.getBlockX();
        int curY = origin.getBlockY();
        int curZ = origin.getBlockZ();

        for (int i = 0; i < size; i++) {
            Block block = world.getBlockAt(curX, curY, curZ);
            if (block.getType() == target) {
                plugin.getCustomBlockManager().placeCustomBlock(block.getLocation(), def, BlockFace.NORTH, null);
            }

            // Step randomly in 3D
            curX += random.nextInt(3) - 1;
            curY += random.nextInt(3) - 1;
            curZ += random.nextInt(3) - 1;
        }
    }
}

