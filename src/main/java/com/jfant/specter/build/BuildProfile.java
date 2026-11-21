package com.jfant.specter.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jfant.specter.core.SpecterCore;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class BuildProfile {
   public static final int FORMAT = 1;
   public static final int[] GAP_BINS = new int[]{0, 3, 4, 5, 6, 9, 15, 30, 60};
   public static final int PAUSE_TICKS = 60;
   public static final int HOLD_TICKS = 4;
   public static final int HOLD_JITTER = 1;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
   public int format = 1;
   public String name;
   public long fittedAt;
   public List<String> sources;
   public int placements;
   public double activeMinutes;
   public com.jfant.specter.build.BuildProfile.Timing timing = new com.jfant.specter.build.BuildProfile.Timing();
   public com.jfant.specter.build.BuildProfile.Aim aim = new com.jfant.specter.build.BuildProfile.Aim();
   public com.jfant.specter.build.BuildProfile.Stance stance = new com.jfant.specter.build.BuildProfile.Stance();
   public com.jfant.specter.build.BuildProfile.Ordering ordering = new com.jfant.specter.build.BuildProfile.Ordering();
   public com.jfant.specter.build.BuildProfile.Restock restock = new com.jfant.specter.build.BuildProfile.Restock();

   public static boolean isHold(int ticks) {
      return Math.abs(ticks - 4) <= 1;
   }

   public void save(File file) throws IOException {
      File parent = file.getParentFile();
      if (parent != null) {
         parent.mkdirs();
      }

      try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
         GSON.toJson(this, out);
      }
   }

   public static File profileFile(String name) {
      return new File(new File(new File(SpecterCore.FOLDER, "specter"), "profiles"), name + ".json");
   }

   public static com.jfant.specter.build.BuildProfile load(File file) throws IOException {
      com.jfant.specter.build.BuildProfile var3;
      try (Reader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
         com.jfant.specter.build.BuildProfile profile = (com.jfant.specter.build.BuildProfile)GSON.fromJson(in, com.jfant.specter.build.BuildProfile.class);
         if (profile == null) {
            throw new IOException("empty profile");
         }

         var3 = profile;
      }

      return var3;
   }

   public static final class Aim {
      public double turningAtClick;
      public com.jfant.specter.build.BuildProfile.Dist settleTicks;
      public com.jfant.specter.build.BuildProfile.Dist clickTurnSpeed;
      public com.jfant.specter.build.BuildProfile.Dist turnSpeed;
      public double overshootRate;
      public com.jfant.specter.build.BuildProfile.Dist overshootDegrees;
      public double faceTop;
      public double faceBottom;
      public double faceSide;
      public com.jfant.specter.build.BuildProfile.Dist topU;
      public com.jfant.specter.build.BuildProfile.Dist topV;
      public com.jfant.specter.build.BuildProfile.Dist sideU;
      public com.jfant.specter.build.BuildProfile.Dist sideV;
   }

   public static final class Dist {
      public int n;
      public double[] q;

      public static com.jfant.specter.build.BuildProfile.Dist of(List<Double> values) {
         com.jfant.specter.build.BuildProfile.Dist dist = new com.jfant.specter.build.BuildProfile.Dist();
         dist.n = values.size();
         if (dist.n == 0) {
            return dist;
         } else {
            double[] sorted = new double[dist.n];

            for (int i = 0; i < dist.n; i++) {
               sorted[i] = values.get(i);
            }

            Arrays.sort(sorted);
            dist.q = new double[101];

            for (int p = 0; p <= 100; p++) {
               dist.q[p] = at(sorted, p / 100.0);
            }

            return dist;
         }
      }

      private static double at(double[] sorted, double f) {
         double pos = f * (sorted.length - 1);
         int lo = (int)Math.floor(pos);
         int hi = Math.min(sorted.length - 1, lo + 1);
         return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
      }

      public boolean empty() {
         return this.n == 0 || this.q == null;
      }

      public double p(double percentile) {
         if (this.empty()) {
            return Double.NaN;
         } else {
            double pos = Math.max(0.0, Math.min(100.0, percentile));
            int lo = (int)Math.floor(pos);
            int hi = Math.min(100, lo + 1);
            return this.q[lo] + (this.q[hi] - this.q[lo]) * (pos - lo);
         }
      }

      public double sample(Random random) {
         return this.p(random.nextDouble() * 100.0);
      }
   }

   public static final class Ordering {
      public com.jfant.specter.build.BuildProfile.Dist stepDistance;
      public double adjacent;
      public double sameLayer;
      public double upward;
      public double axisX;
      public double axisY;
      public double axisZ;
      public double straight;
      public double reverse;
   }

   public static final class Pause {
      public double per100;
      public com.jfant.specter.build.BuildProfile.Dist seconds;
   }

   public static final class Restock {
      public double swapsPer100;
      public com.jfant.specter.build.BuildProfile.Dist swapLeadTicks;
      public com.jfant.specter.build.BuildProfile.Screens auction = new com.jfant.specter.build.BuildProfile.Screens();
      public com.jfant.specter.build.BuildProfile.Screens inventory = new com.jfant.specter.build.BuildProfile.Screens();
      public com.jfant.specter.build.BuildProfile.Dist placesBetweenAuction;
      public double breaksPer100;
      public double undoPer100;
   }

   public static final class Screens {
      public double perHour;
      public com.jfant.specter.build.BuildProfile.Dist seconds;
      public com.jfant.specter.build.BuildProfile.Dist clicks;
   }

   public static final class Stance {
      public com.jfant.specter.build.BuildProfile.Dist reach;
      public com.jfant.specter.build.BuildProfile.Dist horizontal;
      public com.jfant.specter.build.BuildProfile.Dist targetDy;
      public double movingAtClick;
      public double airborneAtClick;
      public double sneakAtClick;
      public com.jfant.specter.build.BuildProfile.Dist spotRun;
   }

   public static final class Timing {
      public double perHour;
      public com.jfant.specter.build.BuildProfile.Dist gapTicks;
      public int[] shortGapCounts;
      public int[][] transitions;
      public double holdShare;
      public com.jfant.specter.build.BuildProfile.Dist holdRun;
      public com.jfant.specter.build.BuildProfile.Pause screenPause = new com.jfant.specter.build.BuildProfile.Pause();
      public com.jfant.specter.build.BuildProfile.Pause movePause = new com.jfant.specter.build.BuildProfile.Pause();
      public com.jfant.specter.build.BuildProfile.Pause idlePause = new com.jfant.specter.build.BuildProfile.Pause();
   }
}
