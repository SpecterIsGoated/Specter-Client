package com.jfant.specter.core.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientChunkCache.Storage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientChunkCache.class})
public interface ClientChunkManagerAccessor {
   @Accessor("storage")
   Storage meteor$getChunks();
}
