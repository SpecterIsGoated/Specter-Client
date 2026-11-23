package com.jfant.specter.phone.apps.games;

import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Memory Match: find the 8 pairs. Score = moves (lower is better). */
public final class MemoryGame implements PhoneGame {
   public static final Info INFO = new Info("memory", "Memory", "Find all the pairs in as few moves as you can.", Glyph.STAR, 0xFF06B6D4, 0xFF155E75, "Fewest moves", true);
   private static final Glyph[] FACES = {Glyph.HEART, Glyph.STAR, Glyph.SUN, Glyph.MOON, Glyph.PIN, Glyph.BELL, Glyph.PLANE, Glyph.TROPHY};
   private static final int[] COLORS = {0xFFF43F5E, 0xFFFBBF24, 0xFFF97316, 0xFFA78BFA, 0xFF34D399, 0xFF38BDF8, 0xFF60A5FA, 0xFFFACC15};

   private final int[] cards = new int[16];
   private final boolean[] matched = new boolean[16];
   private int first = -1;
   private int second = -1;
   private long hideAt;
   private int moves;
   private boolean done;
   private Host host;

   @Override
   public void start(Host host) {
      this.host = host;
      List<Integer> deck = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
         deck.add(i);
         deck.add(i);
      }
      Collections.shuffle(deck);
      for (int i = 0; i < 16; i++) this.cards[i] = deck.get(i);
      java.util.Arrays.fill(this.matched, false);
      this.first = this.second = -1;
      this.moves = 0;
      this.done = false;
   }

   @Override
   public void tick() {
      if (this.second >= 0 && System.currentTimeMillis() >= this.hideAt) {
         this.first = this.second = -1;
      }
   }

   private void flip(int i) {
      if (this.done || this.matched[i] || i == this.first) return;
      if (this.second >= 0) {
         this.first = this.second = -1;
      }
      if (this.first < 0) {
         this.first = i;
         return;
      }
      this.second = i;
      this.moves++;
      if (this.cards[this.first] == this.cards[i]) {
         this.matched[this.first] = this.matched[i] = true;
         this.first = this.second = -1;
         this.host.sound(true);
         boolean all = true;
         for (boolean m : this.matched) all &= m;
         if (all) {
            this.done = true;
            this.host.finished(this.moves);
         }
      } else {
         this.hideAt = System.currentTimeMillis() + 750;
      }
   }

   @Override
   public String liveScore() {
      return this.moves + " moves";
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float x, float y, float w, float h, float mx, float my) {
      PhoneTheme t = c.theme();
      float size = Math.min((w - 6) / 4, (h - 50) / 4);
      float bx = x + (w - size * 4) / 2;
      for (int i = 0; i < 16; i++) {
         float cx = bx + i % 4 * size, cy = y + i / 4 * size;
         boolean up = this.matched[i] || i == this.first || i == this.second;
         int idx = i;
         if (up) {
            c.round(cx + 3, cy + 3, size - 6, size - 6, 10, this.matched[i] ? 0xFF1F2937 : 0xFFFFFFFF);
            c.glyphAt(FACES[this.cards[i]], cx + size / 2, cy + size / 2, size * 0.5F, this.matched[i] ? 0x88FFFFFF & COLORS[this.cards[i]] | 0x88000000 : COLORS[this.cards[i]]);
         } else {
            boolean hover = ui.hovered(cx + 3, cy + 3, size - 6, size - 6);
            c.round(cx + 3, cy + 3, size - 6, size - 6, 10, hover ? 0xFF2DD4BF : 0xFF0F766E, hover ? 0xFF0D9488 : 0xFF134E4A);
            c.circle(cx + size / 2, cy + size / 2, size * 0.12F, 0x33FFFFFF);
            ui.onClick(cx, cy, size, size, () -> this.flip(idx));
         }
      }
      float ry = y + size * 4 + 8;
      if (this.done) {
         c.textCentered("All pairs in " + this.moves + " moves!", x + w / 2, ry, PhoneTheme.BODY, t.text());
         ui.button(x + 40, ry + 14, w - 80, 22, "Play again", PhoneUi.Style.PRIMARY, () -> this.start(this.host));
      } else {
         c.textCentered("Best: " + (this.host.best() > 0 ? this.host.best() + " moves" : "none yet"), x + w / 2, ry, PhoneTheme.SMALL, t.textDim());
      }
   }
}
