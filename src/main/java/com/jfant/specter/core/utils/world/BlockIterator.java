package com.jfant.specter.core.utils.world;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.utils.PreInit;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.Pool;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class BlockIterator {
   private static final Pool<BlockIterator.Callback> callbackPool = new Pool<>(BlockIterator.Callback::new);
   private static final List<BlockIterator.Callback> callbacks = new ReferenceArrayList();
   private static final List<Runnable> afterCallbacks = new ReferenceArrayList();
   private static final MutableBlockPos blockPos = new MutableBlockPos();
   private static int hRadius;
   private static int vRadius;
   private static boolean disableCurrent;

   private BlockIterator() {
   }

   @PreInit
   public static void init() {
      SpecterCore.EVENT_BUS.subscribe(BlockIterator.class);
   }

   @EventHandler(
      priority = -201
   )
   private static void onTick(TickEvent.Pre event) {
      if (Utils.canUpdate()) {
         int px = SpecterCore.mc.player.getBlockX();
         int py = SpecterCore.mc.player.getBlockY();
         int pz = SpecterCore.mc.player.getBlockZ();

         for (int x = px - hRadius; x <= px + hRadius; x++) {
            for (int z = pz - hRadius; z <= pz + hRadius; z++) {
               for (int y = Math.max(SpecterCore.mc.level.getMinY(), py - vRadius); y <= py + vRadius && y <= SpecterCore.mc.level.getHeight(); y++) {
                  blockPos.set(x, y, z);
                  BlockState blockState = SpecterCore.mc.level.getBlockState(blockPos);
                  int dx = Math.abs(x - px);
                  int dy = Math.abs(y - py);
                  int dz = Math.abs(z - pz);
                  callbacks.removeIf(callbackx -> {
                     if (dx <= callbackx.hRadius && dy <= callbackx.vRadius && dz <= callbackx.hRadius) {
                        disableCurrent = false;
                        callbackx.function.accept(blockPos, blockState);
                        return disableCurrent;
                     } else {
                        return false;
                     }
                  });
               }
            }
         }

         hRadius = 0;
         vRadius = 0;
         callbackPool.freeAll(callbacks);
         callbacks.clear();

         for (Runnable callback : afterCallbacks) {
            callback.run();
         }

         afterCallbacks.clear();
      }
   }

   public static void register(int horizontalRadius, int verticalRadius, BiConsumer<BlockPos, BlockState> function) {
      hRadius = Math.max(hRadius, horizontalRadius);
      vRadius = Math.max(vRadius, verticalRadius);
      BlockIterator.Callback callback = callbackPool.get();
      callback.function = function;
      callback.hRadius = horizontalRadius;
      callback.vRadius = verticalRadius;
      callbacks.add(callback);
   }

   public static void disableCurrent() {
      disableCurrent = true;
   }

   public static void after(Runnable callback) {
      afterCallbacks.add(callback);
   }

   private static class Callback {
      public BiConsumer<BlockPos, BlockState> function;
      public int hRadius;
      public int vRadius;
   }
}
