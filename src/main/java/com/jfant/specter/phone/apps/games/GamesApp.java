package com.jfant.specter.phone.apps.games;

import com.google.gson.JsonObject;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.api.PhoneSound;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.gfx.Colors;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import java.util.List;

/** Donut Games: a lobby of every registered {@link PhoneGame} with your best scores, and the game screen. */
public final class GamesApp extends PhoneApplication {
   private GameRegistry.Entry playing;
   private PhoneGame game;
   private long newBestAt;

   private JsonObject bests() {
      JsonObject root = this.phone().storage();
      if (!root.has("best")) root.add("best", new JsonObject());
      return root.getAsJsonObject("best");
   }

   private long best(PhoneGame.Info info) {
      JsonObject b = this.bests();
      return b.has(info.id()) ? b.get(info.id()).getAsLong() : 0;
   }

   private void play(GameRegistry.Entry entry) {
      this.playing = entry;
      this.game = entry.factory().get();
      PhoneGame.Info info = entry.info();
      this.game.start(new PhoneGame.Host() {
         @Override
         public void finished(long score) {
            long old = GamesApp.this.best(info);
            boolean better = old == 0 || (info.lowerIsBetter() ? score < old : score > old);
            if (better) {
               GamesApp.this.bests().addProperty(info.id(), score);
               GamesApp.this.phone().saveStorage();
               GamesApp.this.newBestAt = System.currentTimeMillis();
            }
         }

         @Override
         public long best() {
            return GamesApp.this.best(info);
         }

         @Override
         public void sound(boolean good) {
            GamesApp.this.phone().sound(good ? PhoneSound.SUCCESS : PhoneSound.ERROR);
         }
      });
   }

   /** Route = a game id: starts that game. */
   @Override
   protected void onRoute(String route) {
      for (GameRegistry.Entry e : GameRegistry.all()) if (e.info().id().equals(route)) this.play(e);
   }

   @Override
   public boolean back() {
      if (this.game == null) return false;
      this.game = null;
      this.playing = null;
      return true;
   }

   @Override
   public void tick() {
      if (this.game != null) this.game.tick();
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      if (this.game != null) {
         this.renderGame(c, ui, mx, my);
         return;
      }
      PhoneTheme t = c.theme();
      float top = ui.header("Games", null, null, null);
      List<GameRegistry.Entry> games = GameRegistry.all();
      ScrollView sv = ui.scroll("lobby");
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      float cardH = 64;
      sv.begin(ui, 0, top, c.width, bottom - top, games.size() * (cardH + 8) + 8);
      for (int i = 0; i < games.size(); i++) {
         GameRegistry.Entry e = games.get(i);
         PhoneGame.Info info = e.info();
         float y = top + 4 + i * (cardH + 8), x = 10, w = c.width - 20;
         boolean hover = ui.hovered(x, y, w, cardH);
         c.shadow(x, y, w, cardH, 14, 3, 0x50000000);
         c.round(x, y, w, cardH, 14, hover ? Colors.shade(info.top(), 1.1F) : info.top(), info.bottom());
         c.circle(x + 30, y + cardH / 2, 19, 0x33FFFFFF);
         c.glyphAt(info.glyph(), x + 30, y + cardH / 2, 18, 0xFFFFFFFF);
         c.text(info.name(), x + 58, y + 14, PhoneTheme.HEADLINE, 0xFFFFFFFF);
         c.text(c.fit(info.tagline(), w - 70, PhoneTheme.CAPTION), x + 58, y + 29, PhoneTheme.CAPTION, 0xDDFFFFFF);
         long best = this.best(info);
         String bestText = best > 0 ? info.scoreLabel() + ": " + best : "Not played yet";
         c.round(x + 58, y + 41, c.width(bestText, PhoneTheme.CAPTION) + 10, 12, 6, 0x33000000);
         c.text(bestText, x + 63, y + 41 + 6 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, 0xFFFFFFFF);
         ui.onClick(x, y, w, cardH, () -> this.play(e));
      }
      sv.end(ui);
   }

   private void renderGame(PhoneCanvas c, PhoneUi ui, float mx, float my) {
      PhoneTheme t = c.theme();
      PhoneGame.Info info = this.playing.info();
      float top = ui.header(info.name(), this::back, null, null);
      String live = this.game.liveScore();
      if (live != null) c.textRight(live, c.width - 14, PhoneCanvas.SAFE_TOP + 15 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, t.textDim());
      long sinceBest = System.currentTimeMillis() - this.newBestAt;
      if (sinceBest < 2500) {
         float a = Math.min(1, (2500 - sinceBest) / 400.0F);
         c.alpha(a);
         c.round(c.width / 2 - 34, top - 2, 68, 14, 7, 0xFFFBBF24);
         c.textCentered("New best!", c.width / 2, top + 5 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, 0xFF1F1300);
         c.popAlpha();
      }
      float y = top + 16, h = c.height - PhoneCanvas.SAFE_BOTTOM - 6 - y;
      this.game.render(c, ui, 8, y, c.width - 16, h, mx, my);
   }

   @Override
   public boolean mouseClicked(float x, float y, int button) {
      return this.game != null && this.game.click(x, y, button);
   }

   @Override
   public boolean keyPressed(int key, int modifiers) {
      return this.game != null && this.game.key(key);
   }
}
