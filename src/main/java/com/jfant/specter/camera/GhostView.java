package com.jfant.specter.camera;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.modules.GhostFreecam;
import com.jfant.specter.modules.GhostFreelook;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

/**
 * What Ghost Freecam and Ghost Freelook share: drawing everything around a camera that may sit inside
 * blocks, and keeping the keys the body was holding held.
 *
 * <p>Inside a block, cave culling hides every cave and Entity Culling hides every chest and mob,
 * because nothing is "visible" from in there. On 1.21.11 this rebuilt the chunks once with see-through
 * occlusion data, but Sodium 0.9 for 26.3 builds that data with its own DirectionalVisGraph, which
 * Specter's VisGraph hook never reaches - so caves vanished. Now cave culling is simply switched off
 * while a ghost camera can be inside blocks ({@link #seesThroughBlocks}, read by the camera mixin),
 * the same thing the game does for a spectator inside a block. Vanilla and Sodium both read that flag,
 * nothing has to be rebuilt, and normal play keeps full culling. Entity Culling's own switch is off
 * while a module is on.
 */
public final class GhostView {
   private static final String CULLING_CLASS = "dev.tr7zw.entityculling.versionless.EntityCullingVersionlessBase";
   private static int users;
   private static Boolean cullingWas;

   private GhostView() {
   }

   /** Kept for the modules' constructors; nothing needs setting up any more. */
   public static void init() {
   }

   /**
    * True while a ghost camera can sit inside blocks: Ghost Freecam, or Ghost Freelook with
    * see-through-walls. Cave culling is off then.
    */
   public static boolean seesThroughBlocks() {
      if (users <= 0) return false;
      if (GhostFreecam.on() != null) return true;
      GhostFreelook look = GhostFreelook.on();
      if (look != null && look.seeThroughWalls()) return true;
      com.jfant.specter.modules.AutoCliper clip = com.jfant.specter.modules.AutoCliper.on();
      return clip != null && clip.seesThroughBlocks();
   }

   public static void enter() {
      if (users++ <= 0) {
         cullingWas = setEntityCulling(false);
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

   private static Boolean setEntityCulling(boolean on) {
      try {
         Field enabled = Class.forName(CULLING_CLASS).getField("enabled");
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
}
