package com.jfant.specter.core.events.world;

import net.minecraft.world.level.chunk.LevelChunk;

public record ChunkDataEvent(LevelChunk chunk) {
}
