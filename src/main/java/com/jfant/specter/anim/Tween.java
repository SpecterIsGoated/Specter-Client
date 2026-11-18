package com.jfant.specter.anim;

public final class Tween {
   private Spec spec;
   private double from;
   private double target;
   private double value;
   private double start;
   private double duration;
   private double span;
   private boolean moving;

   public Tween(Spec spec, double initial) {
      this.spec = spec;
      this.from = this.target = this.value = initial;
   }

   public static Tween progress(Spec spec, boolean on) {
      return new Tween(spec, on ? 1.0 : 0.0).span(1.0);
   }

   public Tween span(double span) {
      this.span = Math.abs(span);
      return this;
   }

   public Tween spec(Spec spec) {
      this.spec = spec;
      return this;
   }

   public Tween to(double target) {
      return this.to(target, this.spec, 0.0);
   }

   public Tween to(double target, Spec spec) {
      return this.to(target, spec, 0.0);
   }

   public Tween to(double target, Spec spec, double delay) {
      if (target != this.target || !this.moving && this.value != target) {
         double current = this.get();
         this.spec = spec;
         this.from = current;
         this.target = target;
         double full = Motion.seconds(spec);
         double share = this.span > 0.0 ? Math.min(1.0, Math.abs(target - current) / this.span) : 1.0;
         this.duration = full * (share >= 1.0 ? 1.0 : Math.max(0.6, share));
         this.start = Motion.now() + Math.max(0.0, delay);
         this.moving = this.duration > 0.0 && current != target;
         if (!this.moving) {
            this.value = target;
         }

         return this;
      } else {
         return this;
      }
   }

   public Tween set(double value) {
      this.from = this.target = this.value = value;
      this.moving = false;
      return this;
   }

   public double get() {
      if (!this.moving) {
         return this.value;
      } else {
         double t = (Motion.now() - this.start) / this.duration;
         if (t >= 1.0) {
            this.moving = false;
            this.value = this.target;
            return this.value;
         } else if (t <= 0.0) {
            return this.value = this.from;
         } else {
            this.value = this.from + (this.target - this.from) * this.spec.easing().ease(t);
            return this.value;
         }
      }
   }

   public float getF() {
      return (float)this.get();
   }

   public double target() {
      return this.target;
   }

   public boolean done() {
      this.get();
      return !this.moving;
   }

   public boolean moving() {
      return !this.done();
   }
}
