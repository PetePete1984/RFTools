package com.mcjty.rftools.dimension.world.terrain;

import com.mcjty.rftools.dimension.world.GenericChunkProvider;
import net.minecraft.block.Block;

public class IslandTerrainGenerator implements BaseTerrainGenerator {
    @Override
    public void generate(GenericChunkProvider provider, int chunkX, int chunkZ, Block[] aBlock) {
        for (int i = 0 ; i < 65536 ; i++) {
            aBlock[i] = null;
        }
    }
}