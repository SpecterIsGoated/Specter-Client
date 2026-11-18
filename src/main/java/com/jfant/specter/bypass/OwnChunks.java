package com.jfant.specter.bypass;

import com.jfant.specter.ChunkMemory;

public final class OwnChunks {
   private final ChunkMemory memory;

   public OwnChunks(String folder) {
      this.memory = new ChunkMemory(folder, ".txt");
   }

   public void select(String scope) {
      this.memory.select(scope);
   }

   public boolean isProtected(long chunk) {
      return this.memory.contains(chunk);
   }

   public void protect(long chunk) {
      this.memory.put(chunk, 0);
   }

   public boolean flagAllowed(long chunk, boolean alreadyFlagged) {
      return alreadyFlagged || !this.isProtected(chunk);
   }

   public int size() {
      return this.memory.size();
   }

   public void tickAutosave() {
      this.memory.tickAutosave();
   }

   public void flush() {
      this.memory.flush();
   }
}
