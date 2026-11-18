package com.jfant.specter.anim;

public final class Spring {
   private final Channel channel;
   private double response;
   private double value;
   private double velocity;
   private double target;
   private double seen = -1.0;

   public Spring(double response, Channel channel, double initial) {
      this.response = response;
      this.channel = channel;
      this.value = this.target = initial;
   }

   public Spring response(double seconds) {
      this.response = seconds;
      return this;
   }

   public double follow(double target) {
      this.target = target;
      return this.get();
   }

   public double get() {
      double now = Motion.now();
      if (this.seen < 0.0) {
         this.seen = now;
      }

      double dt = now - this.seen;
      this.seen = now;
      if (dt <= 0.0) {
         return this.value;
      } else {
         double time = Motion.plays(this.channel) ? this.response / Motion.speed() : 0.0;
         if (Motion.reduced()) {
            time = Math.min(time, 0.09);
         }

         if (time <= 1.0E-4) {
            this.value = this.target;
            this.velocity = 0.0;
            return this.value;
         } else {
            double w = 4.744 / time;
            double offset = this.value - this.target;
            double decay = Math.exp(-w * dt);
            double b = this.velocity + w * offset;
            this.value = this.target + (offset + b * dt) * decay;
            this.velocity = (this.velocity - w * b * dt) * decay;
            if (Math.abs(this.value - this.target) < 1.0E-4 && Math.abs(this.velocity) < 0.001) {
               this.value = this.target;
               this.velocity = 0.0;
            }

            return this.value;
         }
      }
   }

   public float getF() {
      return (float)this.get();
   }

   public Spring set(double value) {
      this.value = this.target = value;
      this.velocity = 0.0;
      return this;
   }

   public Spring kick(double velocity) {
      this.velocity += velocity;
      return this;
   }

   public double target() {
      return this.target;
   }

   public boolean resting() {
      return this.value == this.target && this.velocity == 0.0;
   }
}
