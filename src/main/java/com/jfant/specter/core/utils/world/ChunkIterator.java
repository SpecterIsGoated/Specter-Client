package com.jfant.specter.core.utils.world;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.mixin.ClientChunkManagerAccessor;
import com.jfant.specter.core.mixin.ClientChunkMapAccessor;
import java.util.Iterator;
import net.minecraft.world.level.chunk.ChunkAccess;

public class ChunkIterator implements Iterator<ChunkAccess> {
   private final ClientChunkMapAccessor map = (ClientChunkMapAccessor)((ClientChunkManagerAccessor)SpecterCore.mc.level.getChunkSource()).meteor$getChunks();
   private final boolean onlyWithLoadedNeighbours;
   private int i = 0;
   private ChunkAccess chunk;

   public ChunkIterator(boolean onlyWithLoadedNeighbours) {
      this.onlyWithLoadedNeighbours = onlyWithLoadedNeighbours;
      this.getNext();
   }

   private ChunkAccess getNext() {
      ChunkAccess prev = this.chunk;
      this.chunk = null;

      while (this.i < this.map.meteor$getChunks().length()) {
         this.chunk = this.map.meteor$getChunks().get(this.i++);
         if (this.chunk != null && (!this.onlyWithLoadedNeighbours || this.isInRadius(this.chunk))) {
            break;
         }
      }

      return prev;
   }

   private boolean isInRadius(ChunkAccess chunk) {
      int x = chunk.getPos().x;
      int z = chunk.getPos().z;
      return SpecterCore.mc.level.getChunkSource().hasChunk(x + 1, z)
         && SpecterCore.mc.level.getChunkSource().hasChunk(x - 1, z)
         && SpecterCore.mc.level.getChunkSource().hasChunk(x, z + 1)
         && SpecterCore.mc.level.getChunkSource().hasChunk(x, z - 1);
   }

   @Override
   public boolean hasNext() {
      return this.chunk != null;
   }

   public ChunkAccess next() {
      return this.getNext();
   }
}
