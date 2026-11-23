package com.jfant.specter.phone.apps.games;

import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import java.util.ArrayDeque;
import java.util.Random;

/** Minesweeper: left click opens, right click (or flag mode) flags. The first click is always safe. Score = seconds. */
public final class MinesweeperGame implements PhoneGame {
   public static final Info INFO = new Info("minesweeper", "Minesweeper", "Clear the field without a bang.", Glyph.FLAG, 0xFF64748B, 0xFF1E293B, "Best time (s)", true);
   private static final int COLS = 9;
   private static final int ROWS = 12;
   private static final int MINES = 16;
   private static final int[] NUMBER_COLORS = {0, 0xFF60A5FA, 0xFF4ADE80, 0xFFF87171, 0xFFC084FC, 0xFFFB923C, 0xFF22D3EE, 0xFFF472B6, 0xFFE5E7EB};

   private final boolean[] mine = new boolean[COLS * ROWS];
   private final boolean[] open = new boolean[COLS * ROWS];
   private final boolean[] flag = new boolean[COLS * ROWS];
   private final int[] count = new int[COLS * ROWS];
   private boolean placed;
   private boolean lost;
   private boolean won;
   private boolean flagMode;
   private long startedAt;
   private long endedAt;
   private Host host;

   @Override
   public void start(Host host) {
      this.host = host;
      java.util.Arrays.fill(this.mine, false);
      java.util.Arrays.fill(this.open, false);
      java.util.Arrays.fill(this.flag, false);
      this.placed = this.lost = this.won = false;
      this.startedAt = this.endedAt = 0;
   }

   private void place(int safe) {
      Random r = new Random();
      int sx = safe % COLS, sy = safe / COLS;
      int n = 0;
      while (n < MINES) {
         int i = r.nextInt(COLS * ROWS);
         int x = i % COLS, y = i / COLS;
         if (this.mine[i] || Math.abs(x - sx) <= 1 && Math.abs(y - sy) <= 1) continue;
         this.mine[i] = true;
         n++;
      }
      for (int i = 0; i < COLS * ROWS; i++) {
         int c = 0;
         for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int x = i % COLS + dx, y = i / COLS + dy;
            if ((dx != 0 || dy != 0) && x >= 0 && y >= 0 && x < COLS && y < ROWS && this.mine[y * COLS + x]) c++;
         }
         this.count[i] = c;
      }
      this.placed = true;
      this.startedAt = System.currentTimeMillis();
   }

   private void reveal(int i) {
      if (this.lost || this.won || this.flag[i] || this.open[i]) return;
      if (!this.placed) this.place(i);
      if (this.mine[i]) {
         this.lost = true;
         this.endedAt = System.currentTimeMillis();
         for (int k = 0; k < this.open.length; k++) if (this.mine[k]) this.open[k] = true;
         this.host.sound(false);
         return;
      }
      ArrayDeque<Integer> todo = new ArrayDeque<>();
      todo.add(i);
      while (!todo.isEmpty()) {
         int k = todo.poll();
         if (this.open[k] || this.flag[k]) continue;
         this.open[k] = true;
         if (this.count[k] == 0) {
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
               int x = k % COLS + dx, y = k / COLS + dy;
               if (x >= 0 && y >= 0 && x < COLS && y < ROWS && !this.open[y * COLS + x]) todo.add(y * COLS + x);
            }
         }
      }
      int closed = 0;
      for (int k = 0; k < this.open.length; k++) if (!this.open[k]) closed++;
      if (closed == MINES) {
         this.won = true;
         this.endedAt = System.currentTimeMillis();
         this.host.finished(Math.max(1, (this.endedAt - this.startedAt) / 1000));
         this.host.sound(true);
      }
   }

   private void toggleFlag(int i) {
      if (this.lost || this.won || this.open[i]) return;
      this.flag[i] = !this.flag[i];
   }

   @Override
   public String liveScore() {
      if (!this.placed) return "0s";
      long end = this.endedAt > 0 ? this.endedAt : System.currentTimeMillis();
      return (end - this.startedAt) / 1000 + "s";
   }

   private float bx, by, cell;

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float x, float y, float w, float h, float mx, float my) {
      PhoneTheme t = c.theme();
      this.cell = Math.min(w / COLS, (h - 40) / ROWS);
      float bw = this.cell * COLS, bh = this.cell * ROWS;
      this.bx = x + (w - bw) / 2;
      this.by = y;
      int flags = 0;
      for (boolean f : this.flag) if (f) flags++;
      for (int i = 0; i < COLS * ROWS; i++) {
         float cx = this.bx + i % COLS * this.cell, cy = this.by + i / COLS * this.cell;
         float pad = 0.8F;
         if (this.open[i]) {
            c.round(cx + pad, cy + pad, this.cell - pad * 2, this.cell - pad * 2, 3, this.mine[i] ? 0xFFB91C1C : t.surface());
            if (this.mine[i]) c.circle(cx + this.cell / 2, cy + this.cell / 2, this.cell * 0.22F, 0xFF111111);
            else if (this.count[i] > 0) c.textCentered(String.valueOf(this.count[i]), cx + this.cell / 2, cy + this.cell / 2 - PhoneTheme.BODY / 2, PhoneTheme.BODY, NUMBER_COLORS[this.count[i]]);
         } else {
            boolean hover = !this.lost && !this.won && ui.hovered(cx, cy, this.cell, this.cell);
            c.round(cx + pad, cy + pad, this.cell - pad * 2, this.cell - pad * 2, 3, hover ? 0xFF55607A : 0xFF3B4457);
            if (this.flag[i]) c.glyphAt(Glyph.FLAG, cx + this.cell / 2, cy + this.cell / 2, this.cell * 0.6F, 0xFFFF5A5F);
         }
      }
      float yy = this.by + bh + 8;
      ui.segmented(x + 8, yy, 110, 20, new String[]{"Open", "Flag"}, this.flagMode ? 1 : 0, i -> this.flagMode = i == 1);
      c.textRight((MINES - flags) + " mines left", x + w - 8, yy + 10 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, t.textDim());
      if (this.lost || this.won) {
         c.round(this.bx + 10, this.by + bh / 2 - 34, bw - 20, 68, 14, 0xEE15121D);
         c.textCentered(this.won ? "Cleared!" : "Boom!", x + w / 2, this.by + bh / 2 - 24, PhoneTheme.HEADLINE, 0xFFFFFFFF);
         c.textCentered(this.won ? this.liveScore() + "  ·  Best " + this.host.best() + "s" : "Better luck next time", x + w / 2, this.by + bh / 2 - 8, PhoneTheme.SMALL, 0xCCFFFFFF);
         ui.button(this.bx + 26, this.by + bh / 2 + 6, bw - 52, 20, "New game", PhoneUi.Style.PRIMARY, () -> this.start(this.host));
      }
   }

   @Override
   public boolean click(float x, float y, int button) {
      if (this.cell <= 0) return false;
      int gx = (int)Math.floor((x - this.bx) / this.cell), gy = (int)Math.floor((y - this.by) / this.cell);
      if (gx < 0 || gy < 0 || gx >= COLS || gy >= ROWS) return false;
      int i = gy * COLS + gx;
      if (button == 1 || this.flagMode) this.toggleFlag(i);
      else this.reveal(i);
      return true;
   }
}
