package com.jfant.specter.phone.apps.games;

import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;

/**
 * A mini-game inside Donut Games. Games are registered in {@link GameRegistry} (like apps are registered with the
 * phone), so adding one is: implement this, register it, done. The Games app gives each game a rectangle to draw in,
 * forwards input, shows the score and keeps the best one.
 */
public interface PhoneGame {
   /** Static facts about a game, shown in the lobby. {@code lowerIsBetter} for times and move counts. */
   record Info(String id, String name, String tagline, Glyph glyph, int top, int bottom, String scoreLabel, boolean lowerIsBetter) {
   }

   /** What the Games app offers a running game. */
   interface Host {
      /** Reports a finished round's score; the app keeps the best. */
      void finished(long score);

      long best();

      void sound(boolean good);
   }

   void start(Host host);

   /** 20 times a second while playing. */
   default void tick() {
   }

   /** Draws the game inside (x, y, w, h). Register clicks with {@code ui} or use the raw handlers below. */
   void render(PhoneCanvas c, PhoneUi ui, float x, float y, float w, float h, float mx, float my);

   /** Raw click in phone points (0 left, 1 right); return true if used. */
   default boolean click(float x, float y, int button) {
      return false;
   }

   default boolean key(int key) {
      return false;
   }

   /** The score to show live in the header (or null to hide it). */
   default String liveScore() {
      return null;
   }
}
