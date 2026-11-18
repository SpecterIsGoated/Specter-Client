package com.jfant.specter.anim;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Animator {
   public static final double IDLE_SECONDS = 8.0;
   private static final double SWEEP_EVERY = 2.0;
   private static final Map<Animator.Key, Animator.Entry> entries = new HashMap<>();
   private static double lastSweep;

   private Animator() {
   }

   public static float to(Object owner, Object slot, double target, Spec spec) {
      return to(owner, slot, target, target, spec);
   }

   public static float to(Object owner, Object slot, double target, double initial, Spec spec) {
      return tween(owner, slot, initial, spec).to(target, spec).getF();
   }

   public static float progress(Object owner, Object slot, boolean on, Spec in, Spec out) {
      Tween tween = tween(owner, slot, on ? 1.0 : 0.0, in).span(1.0);
      return tween.to(on ? 1.0 : 0.0, on ? in : out).getF();
   }

   public static float progress(Object owner, Object slot, boolean on, Spec spec) {
      return progress(owner, slot, on, spec, spec);
   }

   public static float follow(Object owner, Object slot, double target, double response, Channel channel) {
      return (float)spring(owner, slot, target, response, channel).follow(target);
   }

   public static Tween tween(Object owner, Object slot, double initial, Spec spec) {
      Animator.Entry entry = entry(owner, slot);
      if (entry.tween == null) {
         entry.tween = new Tween(spec, initial);
      }

      return entry.tween;
   }

   public static Spring spring(Object owner, Object slot, double initial, double response, Channel channel) {
      Animator.Entry entry = entry(owner, slot);
      if (entry.spring == null) {
         entry.spring = new Spring(response, channel, initial);
      }

      return entry.spring;
   }

   public static boolean known(Object owner, Object slot) {
      return entries.containsKey(new Animator.Key(owner, slot));
   }

   public static void fire(Object owner, Object slot, Spec spec) {
      Animator.Entry entry = entry(owner, slot);
      entry.tween = new Tween(spec, 1.0);
      entry.tween.to(0.0, spec);
   }

   public static float pulse(Object owner, Object slot) {
      Animator.Entry entry = entries.get(new Animator.Key(owner, slot));
      if (entry != null && entry.tween != null) {
         entry.touched = Motion.now();
         return entry.tween.getF();
      } else {
         return 0.0F;
      }
   }

   public static void forget(Object owner) {
      entries.keySet().removeIf(key -> Animator.Key.same(key.owner, owner));
   }

   private static Animator.Entry entry(Object owner, Object slot) {
      Animator.Entry entry = entries.computeIfAbsent(new Animator.Key(owner, slot), k -> new Animator.Entry());
      entry.touched = Motion.now();
      return entry;
   }

   public static int size() {
      return entries.size();
   }

   static void sweep() {
      double now = Motion.now();
      if (!(now - lastSweep < 2.0)) {
         lastSweep = now;
         Iterator<Animator.Entry> it = entries.values().iterator();

         while (it.hasNext()) {
            if (now - it.next().touched > 8.0) {
               it.remove();
            }
         }
      }
   }

   static void clear() {
      entries.clear();
      lastSweep = 0.0;
   }

   private static final class Entry {
      Tween tween;
      Spring spring;
      double touched;
   }

   private record Key(Object owner, Object slot) {
      @Override
      public boolean equals(Object o) {
         return o instanceof Animator.Key k && same(this.owner, k.owner) && this.slot.equals(k.slot);
      }

      @Override
      public int hashCode() {
         return 31
               * (
                  !(this.owner instanceof String) && !(this.owner instanceof Number) && !(this.owner instanceof Enum)
                     ? System.identityHashCode(this.owner)
                     : this.owner.hashCode()
               )
            + this.slot.hashCode();
      }

      private static boolean same(Object a, Object b) {
         return a == b ? true : (a instanceof String || a instanceof Number || a instanceof Enum) && a.equals(b);
      }
   }
}
