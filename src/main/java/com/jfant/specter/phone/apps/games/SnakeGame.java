package com.jfant.specter.phone.apps.games;

import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.Random;

/** Snake: arrow keys / WASD or the on-screen pad. Gets faster as it grows. */
public final class SnakeGame implements PhoneGame {
   public static final Info INFO = new Info("snake", "Snake", "Eat, grow, don't bite yourself.", Glyph.PLAY, 0xFF22C55E, 0xFF166534, "Length", false);
   private static final int COLS = 14;
   private static final int ROWS = 18;

   private final ArrayDeque<int[]> body = new ArrayDeque<>();
   private final Random random = new Random();
   private int dx = 1;
   private int dy;
   private int nextDx = 1;
   private int nextDy;
   private int foodX;
   private int foodY;
   private int clock;
   private boolean over;
   private boolean started;
   private Host host;

   @Override
   public void start(Host host) {
      this.host = host;
      this.body.clear();
      for (int i = 0; i < 3; i++) this.body.addFirst(new int[]{3 + i, ROWS / 2});
      this.dx = this.nextDx = 1;
      this.dy = this.nextDy = 0;
      this.over = false;
      this.started = false;
      this.food();
   }

   private void food() {
      do {
         this.foodX = this.random.nextInt(COLS);
         this.foodY = this.random.nextInt(ROWS);
      } while (this.occupied(this.foodX, this.foodY));
   }

   private boolean occupied(int x, int y) {
      for (int[] p : this.body) if (p[0] == x && p[1] == y) return true;
      return false;
   }

   @Override
   public void tick() {
      if (this.over || !this.started) return;
      int every = Math.max(2, 5 - this.body.size() / 6);
      if (++this.clock < every) return;
      this.clock = 0;
      this.dx = this.nextDx;
      this.dy = this.nextDy;
      int[] head = this.body.peekFirst();
      int nx = head[0] + this.dx, ny = head[1] + this.dy;
      boolean eat = nx == this.foodX && ny == this.foodY;
      if (!eat) this.body.pollLast();
      if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS || this.occupied(nx, ny)) {
         this.over = true;
         this.host.finished(this.body.size());
         this.host.sound(false);
         return;
      }
      this.body.addFirst(new int[]{nx, ny});
      if (eat) {
         this.host.sound(true);
         if (this.body.size() < COLS * ROWS) this.food();
      }
   }

   private void steer(int x, int y) {
      if (this.over) return;
      if (x == -this.dx && y == -this.dy) return;
      this.nextDx = x;
      this.nextDy = y;
      this.started = true;
   }

   @Override
   public boolean key(int key) {
      if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) this.steer(0, -1);
      else if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) this.steer(0, 1);
      else if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_A) this.steer(-1, 0);
      else if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_D) this.steer(1, 0);
      else return false;
      return true;
   }

   @Override
   public String liveScore() {
      return String.valueOf(this.body.size());
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float x, float y, float w, float h, float mx, float my) {
      PhoneTheme t = c.theme();
      float cell = Math.min(w / COLS, (h - 76) / ROWS);
      float bw = cell * COLS, bh = cell * ROWS;
      float bx = x + (w - bw) / 2, by = y;
      c.round(bx - 3, by - 3, bw + 6, bh + 6, 8, t.surface());
      for (int gy = 0; gy < ROWS; gy++) {
         for (int gx = 0; gx < COLS; gx++) {
            if ((gx + gy) % 2 == 0) c.rect(bx + gx * cell, by + gy * cell, cell, cell, 0x08FFFFFF);
         }
      }
      c.circle(bx + (this.foodX + 0.5F) * cell, by + (this.foodY + 0.5F) * cell, cell * 0.36F, 0xFFFF4D6D);
      int i = 0, n = this.body.size();
      for (int[] p : this.body) {
         float k = 1 - (float)i / Math.max(1, n) * 0.45F;
         int col = i == 0 ? 0xFF4ADE80 : com.jfant.specter.phone.gfx.Colors.shade(0xFF22C55E, k);
         c.round(bx + p[0] * cell + 0.8F, by + p[1] * cell + 0.8F, cell - 1.6F, cell - 1.6F, cell * 0.3F, col);
         i++;
      }
      if (!this.started && !this.over) c.textCentered("Arrow keys or the pad to start", x + w / 2, by + bh * 0.75F, PhoneTheme.SMALL, t.textDim());
      if (this.over) {
         c.round(bx + 14, by + bh / 2 - 34, bw - 28, 68, 14, 0xEE15121D);
         c.textCentered("Game over", x + w / 2, by + bh / 2 - 24, PhoneTheme.HEADLINE, 0xFFFFFFFF);
         c.textCentered("Length " + this.body.size() + "  ·  Best " + this.host.best(), x + w / 2, by + bh / 2 - 8, PhoneTheme.SMALL, 0xCCFFFFFF);
         ui.button(bx + 30, by + bh / 2 + 6, bw - 60, 20, "Play again", PhoneUi.Style.PRIMARY, () -> this.start(this.host));
      }
      // d-pad
      float pc = x + w / 2, py = by + bh + 40, s = 22;
      ui.button(pc - s / 2, py - s * 1.5F, s, s - 2, "", Glyph.UP, PhoneUi.Style.SECONDARY, () -> this.steer(0, -1));
      ui.button(pc - s / 2, py + s * 0.5F + 2, s, s - 2, "", Glyph.DOWN, PhoneUi.Style.SECONDARY, () -> this.steer(0, 1));
      ui.button(pc - s * 1.6F, py - s / 2, s, s - 2, "", Glyph.BACK, PhoneUi.Style.SECONDARY, () -> this.steer(-1, 0));
      ui.button(pc + s * 0.6F, py - s / 2, s, s - 2, "", Glyph.CHEVRON, PhoneUi.Style.SECONDARY, () -> this.steer(1, 0));
   }
}
