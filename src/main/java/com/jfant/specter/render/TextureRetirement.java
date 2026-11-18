package com.jfant.specter.render;

import com.jfant.specter.core.SpecterCore;
import java.util.ArrayDeque;
import net.minecraft.resources.Identifier;

final class TextureRetirement {
   private static final long KEEP_NANOS = 1000000000L;
   private static final ArrayDeque<TextureRetirement.Retired> retired = new ArrayDeque<>();

   private TextureRetirement() {
   }

   static void retire(Identifier id) {
      retired.add(new TextureRetirement.Retired(id, System.nanoTime()));
   }

   static void sweep() {
      long now = System.nanoTime();

      while (!retired.isEmpty() && now - retired.peek().at() > 1000000000L) {
         SpecterCore.mc.getTextureManager().release(retired.poll().id());
      }
   }

   private record Retired(Identifier id, long at) {
   }
}
