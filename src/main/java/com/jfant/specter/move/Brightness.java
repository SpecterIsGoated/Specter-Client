package com.jfant.specter.move;

import com.jfant.specter.anim.Spec;
import com.jfant.specter.anim.Tween;
import java.util.ArrayList;
import java.util.List;

public final class Brightness {
   public static final double FULL_GAMMA = 40.0;
   private static final List<Brightness.Layer> layers = new ArrayList<>();

   private Brightness() {
   }

   public static Brightness.Layer layer() {
      Brightness.Layer layer = new Brightness.Layer();
      layers.add(layer);
      return layer;
   }

   public static float nightVision() {
      double best = 0.0;

      for (int i = 0; i < layers.size(); i++) {
         best = Math.max(best, layers.get(i).nightVision.get());
      }

      return (float)best;
   }

   public static float gamma() {
      double best = 0.0;

      for (int i = 0; i < layers.size(); i++) {
         best = Math.max(best, layers.get(i).gamma.get());
      }

      return (float)best;
   }

   public static boolean fading() {
      for (int i = 0; i < layers.size(); i++) {
         if (!layers.get(i).nightVision.done() || !layers.get(i).gamma.done()) {
            return true;
         }
      }

      return false;
   }

   public static boolean idle() {
      for (int i = 0; i < layers.size(); i++) {
         Brightness.Layer layer = layers.get(i);
         if (layer.nightVision.target() != 0.0 || layer.gamma.target() != 0.0 || !layer.nightVision.done() || !layer.gamma.done()) {
            return false;
         }
      }

      return true;
   }

   public static final class Layer {
      private final Tween nightVision = new Tween(Spec.WORLD_FADE, 0.0).span(1.0);
      private final Tween gamma = new Tween(Spec.WORLD_FADE, 0.0).span(40.0);

      private Layer() {
      }

      public void set(double nightVision, double gamma, Spec fade) {
         this.nightVision.to(nightVision, fade);
         this.gamma.to(gamma, fade);
      }

      public void snapOff() {
         this.nightVision.set(0.0);
         this.gamma.set(0.0);
      }
   }
}
