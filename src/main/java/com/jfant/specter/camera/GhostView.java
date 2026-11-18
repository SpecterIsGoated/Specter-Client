package com.jfant.specter.camera;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.ChunkOcclusionEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

public final class GhostView {
   private static final String CULLING_CLASS = "dev.tr7zw.entityculling.versionless.EntityCullingVersionlessBase";
   private static final GhostView.Listener LISTENER = new GhostView.Listener();
   private static boolean subscribed;
   private static int users;
   private static volatile boolean cavesOpen;
   private static Boolean cullingWas;

   private GhostView() {
   }

   public static void init() {
      if (!subscribed) {
         subscribed = true;
         SpecterCore.EVENT_BUS.subscribe(LISTENER);
      }
   }

   public static void enter() {
      if (users++ <= 0) {
         cullingWas = setEntityCulling(false);
         if (!cavesOpen) {
            cavesOpen = true;
            reloadChunks();
         }
      }
   }

   public static void exit() {
      if (users != 0 && --users <= 0) {
         if (cullingWas != null) {
            setEntityCulling(cullingWas);
         }

         cullingWas = null;
      }
   }

   private static void reloadChunks() {
      SpecterCore.mc.execute(() -> {
         if (SpecterCore.mc.levelRenderer != null && SpecterCore.mc.level != null) {
            SpecterCore.mc.levelRenderer.allChanged();
         }
      });
   }

   private static Boolean setEntityCulling(boolean on) {
      try {
         Field enabled = Class.forName("dev.tr7zw.entityculling.versionless.EntityCullingVersionlessBase").getField("enabled");
         boolean was = enabled.getBoolean(null);
         enabled.setBoolean(null, on);
         return was;
      } catch (ClassNotFoundException var3) {
         return null;
      } catch (RuntimeException | ReflectiveOperationException var4) {
         SpecterClientAddon.LOG.warn("Ghost camera: could not switch Entity Culling", var4);
         return null;
      }
   }

   public static final class KeyLatch {
      private final List<KeyMapping> held = new ArrayList<>();

      public void capture(boolean mining, boolean sneaking) {
         this.held.clear();
         Options o = SpecterCore.mc.options;
         if (mining && o.keyAttack.isDown()) {
            this.held.add(o.keyAttack);

            for (KeyMapping key : List.of(o.keyUp, o.keyDown, o.keyLeft, o.keyRight)) {
               if (key.isDown()) {
                  this.held.add(key);
               }
            }
         }

         if (sneaking && SpecterCore.mc.player != null && SpecterCore.mc.player.isShiftKeyDown()) {
            this.held.add(o.keyShift);
         }
      }

      public void copyFrom(GhostView.KeyLatch other) {
         this.held.clear();
         this.held.addAll(other.held);
      }

      public boolean holds(KeyMapping key) {
         return this.held.contains(key);
      }

      public void apply() {
         for (KeyMapping key : this.held) {
            if (!key.isDown()) {
               key.setDown(true);
            }
         }
      }

      public void release() {
         for (KeyMapping key : this.held) {
            key.setDown(false);
         }

         this.held.clear();
      }
   }

   private static final class Listener {
      @EventHandler
      private void onChunkOcclusion(ChunkOcclusionEvent event) {
         if (GhostView.cavesOpen) {
            event.cancel();
         }
      }

      @EventHandler
      private void onGameLeft(GameLeftEvent event) {
         GhostView.cavesOpen = false;
      }
   }
}
