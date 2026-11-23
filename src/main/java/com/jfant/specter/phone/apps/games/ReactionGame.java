package com.jfant.specter.phone.apps.games;

import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import java.util.Random;

/** Reaction Time: wait for green, then click as fast as you can. Score = milliseconds (frame-time limited). */
public final class ReactionGame implements PhoneGame {
   public static final Info INFO = new Info("reaction", "Reaction", "Click the moment it turns green.", Glyph.SPARK, 0xFFFBBF24, 0xFFC2410C, "Best (ms)", true);

   private enum Phase {
      READY,
      WAITING,
      GO,
      RESULT,
      EARLY
   }

   private final Random random = new Random();
   private Phase phase = Phase.READY;
   private long goAt;
   private long result;
   private Host host;

   @Override
   public void start(Host host) {
      this.host = host;
      this.phase = Phase.READY;
   }

   @Override
   public void tick() {
      if (this.phase == Phase.WAITING && System.nanoTime() >= this.goAt) this.phase = Phase.GO;
   }

   private void press() {
      switch (this.phase) {
         case READY, RESULT, EARLY -> {
            this.phase = Phase.WAITING;
            this.goAt = System.nanoTime() + (long)((1.4 + this.random.nextDouble() * 2.8) * 1.0E9);
         }
         case WAITING -> {
            if (System.nanoTime() >= this.goAt) {
               this.finish();
            } else {
               this.phase = Phase.EARLY;
               this.host.sound(false);
            }
         }
         case GO -> this.finish();
      }
   }

   private void finish() {
      this.result = Math.max(1, (System.nanoTime() - this.goAt) / 1_000_000L);
      this.phase = Phase.RESULT;
      this.host.finished(this.result);
      this.host.sound(true);
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float x, float y, float w, float h, float mx, float my) {
      // the click is checked against the real clock (render may lag a frame behind tick)
      if (this.phase == Phase.WAITING && System.nanoTime() >= this.goAt) this.phase = Phase.GO;
      int top, bottom;
      String title, sub;
      switch (this.phase) {
         case WAITING -> {
            top = 0xFFDC2626;
            bottom = 0xFF7F1D1D;
            title = "Wait for green...";
            sub = "";
         }
         case GO -> {
            top = 0xFF22C55E;
            bottom = 0xFF14532D;
            title = "CLICK!";
            sub = "";
         }
         case RESULT -> {
            top = 0xFF3B82F6;
            bottom = 0xFF1E3A8A;
            title = this.result + " ms";
            sub = "Best " + this.host.best() + " ms  ·  click to go again";
         }
         case EARLY -> {
            top = 0xFFF97316;
            bottom = 0xFF7C2D12;
            title = "Too soon!";
            sub = "Click to try again";
         }
         default -> {
            top = 0xFF6366F1;
            bottom = 0xFF312E81;
            title = "Reaction Time";
            sub = "Click anywhere to start";
         }
      }
      c.round(x, y, w, h - 6, 16, top, bottom);
      c.textCentered(title, x + w / 2, y + h / 2 - 22, this.phase == Phase.RESULT ? PhoneTheme.LARGE * 1.4F : PhoneTheme.LARGE, 0xFFFFFFFF);
      if (!sub.isEmpty()) c.textCentered(sub, x + w / 2, y + h / 2 + 8, PhoneTheme.SMALL, 0xDDFFFFFF);
   }

   /** Raw press (not release, like a widget would): reaction time is measured when the button goes down. */
   @Override
   public boolean click(float x, float y, int button) {
      if (button != 0) return false;
      this.press();
      return true;
   }
}
