package com.jfant.specter.phone.apps.games;

import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import java.util.Random;

/** Tic-Tac-Toe against the phone: Easy makes mistakes, Hard never loses (minimax). Score = your wins in a row. */
public final class TicTacToeGame implements PhoneGame {
   public static final Info INFO = new Info("tictactoe", "Tic-Tac-Toe", "Beat the phone. Hard mode never loses.", Glyph.GRID, 0xFFF472B6, 0xFF9D174D, "Win streak", false);
   private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

   private final int[] board = new int[9];
   private final Random random = new Random();
   private boolean hard;
   private int result;
   private int streak;
   private int aiAt = -1;
   private Host host;

   @Override
   public void start(Host host) {
      this.host = host;
      java.util.Arrays.fill(this.board, 0);
      this.result = 0;
      this.aiAt = -1;
   }

   private static int winner(int[] b) {
      for (int[] l : LINES) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
      for (int v : b) if (v == 0) return 0;
      return 3;
   }

   private static int minimax(int[] b, boolean ai, int depth) {
      int w = winner(b);
      if (w == 2) return 10 - depth;
      if (w == 1) return depth - 10;
      if (w == 3) return 0;
      int best = ai ? Integer.MIN_VALUE : Integer.MAX_VALUE;
      for (int i = 0; i < 9; i++) {
         if (b[i] != 0) continue;
         b[i] = ai ? 2 : 1;
         int s = minimax(b, !ai, depth + 1);
         b[i] = 0;
         best = ai ? Math.max(best, s) : Math.min(best, s);
      }
      return best;
   }

   private void aiMove() {
      int move = -1;
      if (!this.hard && this.random.nextFloat() < 0.45F) {
         int free = 0;
         for (int v : this.board) if (v == 0) free++;
         int pick = this.random.nextInt(Math.max(1, free));
         for (int i = 0; i < 9; i++) if (this.board[i] == 0 && pick-- == 0) move = i;
      } else {
         int best = Integer.MIN_VALUE;
         for (int i = 0; i < 9; i++) {
            if (this.board[i] != 0) continue;
            this.board[i] = 2;
            int s = minimax(this.board, false, 0);
            this.board[i] = 0;
            if (s > best) {
               best = s;
               move = i;
            }
         }
      }
      if (move >= 0) this.board[move] = 2;
      this.check();
   }

   private void check() {
      this.result = winner(this.board);
      if (this.result == 1) {
         this.streak++;
         this.host.finished(this.streak);
         this.host.sound(true);
      } else if (this.result == 2) {
         this.streak = 0;
         this.host.sound(false);
      }
   }

   private void play(int i) {
      if (this.result != 0 || this.board[i] != 0 || this.aiAt >= 0) return;
      this.board[i] = 1;
      this.check();
      if (this.result == 0) this.aiAt = 8;
   }

   @Override
   public void tick() {
      if (this.aiAt > 0 && --this.aiAt == 0) {
         this.aiAt = -1;
         this.aiMove();
      }
   }

   @Override
   public String liveScore() {
      return "Streak " + this.streak;
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float x, float y, float w, float h, float mx, float my) {
      PhoneTheme t = c.theme();
      ui.segmented(x + 20, y, w - 40, 20, new String[]{"Easy", "Hard"}, this.hard ? 1 : 0, i -> {
         this.hard = i == 1;
         this.streak = 0;
         this.start(this.host);
      });
      float size = Math.min(w - 20, h - 90);
      float cell = size / 3, bx = x + (w - size) / 2, by = y + 34;
      for (int i = 0; i < 9; i++) {
         float cx = bx + i % 3 * cell, cy = by + i / 3 * cell;
         int idx = i;
         boolean hover = this.board[i] == 0 && this.result == 0 && ui.hovered(cx + 3, cy + 3, cell - 6, cell - 6);
         c.round(cx + 3, cy + 3, cell - 6, cell - 6, 12, hover ? t.surfaceHigh() : t.surface());
         if (this.board[i] == 1) c.glyphAt(Glyph.CLOSE, cx + cell / 2, cy + cell / 2, cell * 0.55F, t.accent());
         else if (this.board[i] == 2) c.ring(cx + cell / 2, cy + cell / 2, cell * 0.22F, cell * 0.08F, 0xFFFBBF24);
         if (this.board[i] == 0 && this.result == 0) ui.onClick(cx, cy, cell, cell, () -> this.play(idx));
      }
      float ry = by + size + 10;
      String status = switch (this.result) {
         case 1 -> "You win!";
         case 2 -> "The phone wins.";
         case 3 -> "Draw.";
         default -> this.aiAt >= 0 ? "Thinking..." : "Your move (X)";
      };
      c.textCentered(status, x + w / 2, ry, PhoneTheme.HEADLINE, t.text());
      if (this.result != 0) ui.button(x + 40, ry + 16, w - 80, 22, "Play again", PhoneUi.Style.PRIMARY, () -> this.start(this.host));
   }
}
