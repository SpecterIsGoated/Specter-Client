package com.jfant.specter.anim;

@FunctionalInterface
public interface Easing {
   Easing LINEAR = t -> t;
   Easing SMOOTH = t -> t * t * (3.0 - 2.0 * t);
   Easing OUT_QUAD = t -> 1.0 - (1.0 - t) * (1.0 - t);
   Easing OUT_CUBIC = t -> 1.0 - Math.pow(1.0 - t, 3.0);
   Easing OUT_QUART = t -> 1.0 - Math.pow(1.0 - t, 4.0);
   Easing OUT_EXPO = t -> t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
   Easing IN_CUBIC = t -> t * t * t;
   Easing IN_OUT_CUBIC = t -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
   Easing IN_OUT_SINE = t -> -(Math.cos(3.141592653589793 * t) - 1.0) / 2.0;
   Easing STANDARD = bezier(0.2, 0.0, 0.0, 1.0);
   Easing ENTER = bezier(0.05, 0.7, 0.1, 1.0);
   Easing EXIT = bezier(0.3, 0.0, 0.8, 0.15);
   Easing SOFT_BACK = t -> {
      double c = 1.05;
      double u = t - 1.0;
      return 1.0 + (c + 1.0) * u * u * u + c * u * u;
   };

   double ease(double var1);

   static Easing bezier(double x1, double y1, double x2, double y2) {
      double cx = 3.0 * x1;
      double bx = 3.0 * (x2 - x1) - cx;
      double ax = 1.0 - cx - bx;
      double cy = 3.0 * y1;
      double by = 3.0 * (y2 - y1) - cy;
      double ay = 1.0 - cy - by;
      return t -> {
         if (t <= 0.0) {
            return 0.0;
         } else if (t >= 1.0) {
            return 1.0;
         } else {
            double s = t;

            for (int i = 0; i < 6; i++) {
               double x = ((ax * s + bx) * s + cx) * s - t;
               if (Math.abs(x) < 1.0E-5) {
                  break;
               }

               double slope = (3.0 * ax * s + 2.0 * bx) * s + cx;
               if (Math.abs(slope) < 1.0E-6) {
                  break;
               }

               s -= x / slope;
            }

            if (s < 0.0 || s > 1.0 || Math.abs(((ax * s + bx) * s + cx) * s - t) > 1.0E-4) {
               double lo = 0.0;
               double hi = 1.0;
               s = t;

               for (int i = 0; i < 24; i++) {
                  double xx = ((ax * s + bx) * s + cx) * s;
                  if (Math.abs(xx - t) < 1.0E-5) {
                     break;
                  }

                  if (xx < t) {
                     lo = s;
                  } else {
                     hi = s;
                  }

                  s = (lo + hi) / 2.0;
               }
            }

            return ((ay * s + by) * s + cy) * s;
         }
      };
   }
}
