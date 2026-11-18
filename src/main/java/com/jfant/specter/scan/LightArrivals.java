package com.jfant.specter.scan;

import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

public final class LightArrivals {
   private static final CopyOnWriteArrayList<LightArrivals.Listener> LISTENERS = new CopyOnWriteArrayList<>();

   private LightArrivals() {
   }

   public static void listen(LightArrivals.Listener listener) {
      LISTENERS.addIfAbsent(listener);
   }

   public static void stop(LightArrivals.Listener listener) {
      LISTENERS.remove(listener);
   }

   public static void applied(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data) {
      for (LightArrivals.Listener listener : LISTENERS) {
         listener.applied(chunkX, chunkZ, data);
      }
   }

   public static boolean blockLightIn(ClientboundLightUpdatePacketData data, int bottomSection, int minY, int maxY) {
      for (int bit = data.getBlockYMask().nextSetBit(0); bit >= 0; bit = data.getBlockYMask().nextSetBit(bit + 1)) {
         int sectionY = bottomSection - 1 + bit;
         if ((sectionY << 4) + 15 >= minY && sectionY << 4 <= maxY) {
            return true;
         }
      }

      return false;
   }

   @FunctionalInterface
   public interface Listener {
      void applied(int var1, int var2, ClientboundLightUpdatePacketData var3);
   }
}
