package com.jfant.specter.core.events.world;

import com.jfant.specter.core.events.Cancellable;

public class ChunkOcclusionEvent extends Cancellable {
   private static final ChunkOcclusionEvent INSTANCE = new ChunkOcclusionEvent();

   public static ChunkOcclusionEvent get() {
      INSTANCE.setCancelled(false);
      return INSTANCE;
   }
}
