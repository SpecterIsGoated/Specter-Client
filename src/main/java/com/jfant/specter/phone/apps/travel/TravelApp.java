package com.jfant.specter.phone.apps.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.api.PhoneSound;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.gfx.Colors;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * DonutTravel: pick a destination and the world changes around you (sky, stars, time, colour, fog, particles), all
 * built from Specter's visual modules. "Return home" undoes exactly what the trip changed, even after a restart.
 */
public final class TravelApp extends PhoneApplication {
   @Nullable
   private Destination current;
   @Nullable
   private Destination viewing;

   @Override
   protected void onCreate() {
      JsonObject s = this.phone().storage();
      if (s.has("current")) this.current = Destinations.get(s.get("current").getAsString());
   }

   @Override
   public void backgroundTick() {
      Destination d = this.current;
      if (d != null && d.ambience() != null) AmbientParticles.tick(d.ambience());
   }

   private void travel(Destination d) {
      this.returnHome(false);
      TravelContext ctx = new TravelContext();
      d.look().accept(ctx);
      JsonObject s = this.phone().storage();
      s.addProperty("current", d.id());
      s.add("undo", ctx.record());
      if (!s.has("visited") || !s.get("visited").isJsonArray()) s.add("visited", new JsonArray());
      JsonArray visited = s.getAsJsonArray("visited");
      boolean seen = false;
      for (var e : visited) seen |= e.getAsString().equals(d.id());
      if (!seen) visited.add(d.id());
      this.phone().saveStorage();
      this.current = d;
      this.phone().sound(PhoneSound.SUCCESS);
      this.phone().notify("Welcome to " + d.name(), d.tagline(), "");
   }

   private void returnHome(boolean announce) {
      JsonObject s = this.phone().storage();
      if (TravelContext.isRecord(s.get("undo"))) TravelContext.undo(s.getAsJsonArray("undo"));
      s.remove("undo");
      s.remove("current");
      this.phone().saveStorage();
      boolean was = this.current != null;
      this.current = null;
      if (announce && was) this.phone().toast("Back home - your settings are restored");
   }

   /** Route = a destination id: opens its page. */
   @Override
   protected void onRoute(String route) {
      Destination d = Destinations.get(route);
      if (d != null) this.viewing = d;
   }

   @Override
   public boolean back() {
      if (this.viewing == null) return false;
      this.viewing = null;
      return true;
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      if (this.viewing != null) {
         this.detail(c, ui, this.viewing);
         return;
      }
      PhoneTheme t = c.theme();
      float top = ui.header("DonutTravel", null, null, null);
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      List<Destination> all = Destinations.all();
      float cardW = (c.width - 30) / 2, cardH = 92;
      float banner = this.current != null ? 50 : 0;
      ScrollView sv = ui.scroll("grid");
      sv.begin(ui, 0, top, c.width, bottom - top, banner + ((all.size() + 1) / 2) * (cardH + 10) + 10);
      float y = top + 4;
      if (this.current != null) {
         Destination d = this.current;
         c.round(10, y, c.width - 20, 40, 12, d.top(), d.bottom());
         c.text("You're in", 20, y + 8, PhoneTheme.CAPTION, 0xCCFFFFFF);
         c.text(c.fit(d.name(), c.width - 110, PhoneTheme.HEADLINE), 20, y + 19, PhoneTheme.HEADLINE, 0xFFFFFFFF);
         ui.button(c.width - 86, y + 9, 68, 22, "Go home", Glyph.HOME, PhoneUi.Style.SECONDARY, () -> this.returnHome(true));
         y += banner;
      }
      for (int i = 0; i < all.size(); i++) {
         Destination d = all.get(i);
         float x = 10 + (i % 2) * (cardW + 10), cy = y + (i / 2) * (cardH + 10);
         boolean hover = ui.hovered(x, cy, cardW, cardH);
         c.shadow(x, cy, cardW, cardH, 14, 3, 0x50000000);
         c.round(x, cy, cardW, cardH, 14, hover ? Colors.shade(d.top(), 1.1F) : d.top(), d.bottom());
         c.clip(x, cy, cardW, cardH);
         c.circle(x + cardW - 22, cy + 22, 26, 0x18FFFFFF);
         c.unclip();
         c.glyphAt(d.glyph(), x + cardW - 22, cy + 22, 18, 0xEEFFFFFF);
         List<String> name = c.wrap(d.name(), cardW - 16, PhoneTheme.HEADLINE, 2);
         float ny = cy + cardH - 12 - name.size() * PhoneCanvas.line(PhoneTheme.HEADLINE);
         for (int k = 0; k < name.size(); k++) c.text(name.get(k), x + 9, ny + k * PhoneCanvas.line(PhoneTheme.HEADLINE), PhoneTheme.HEADLINE, 0xFFFFFFFF);
         if (d == this.current) {
            c.round(x + 8, cy + 8, 34, 12, 6, 0xEEFFFFFF);
            c.textCentered("HERE", x + 25, cy + 14 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, d.bottom());
         }
         ui.onClick(x, cy, cardW, cardH, () -> this.viewing = d);
      }
      sv.end(ui);
   }

   private void detail(PhoneCanvas c, PhoneUi ui, Destination d) {
      PhoneTheme t = c.theme();
      c.gradient(0, 0, c.width, 210, d.top(), d.bottom());
      c.gradient(0, 150, c.width, 60, 0x00000000, t.background());
      ui.iconButton(18, PhoneCanvas.SAFE_TOP + 14, 11, Glyph.BACK, 0xFFFFFFFF, this::back);
      c.circle(c.width / 2, 110, 38, 0x22FFFFFF);
      c.glyphAt(d.glyph(), c.width / 2, 110, 40, 0xFFFFFFFF);
      float y = 200;
      c.textCentered(d.name(), c.width / 2, y, PhoneTheme.TITLE, t.text());
      c.textCentered(d.tagline(), c.width / 2, y + 16, PhoneTheme.SMALL, t.textDim());
      y += 36;
      y += c.paragraph(d.description(), 18, y, c.width - 36, PhoneTheme.SMALL, t.textDim(), 7) + 10;
      float by = c.height - PhoneCanvas.SAFE_BOTTOM - 64;
      if (d == this.current) {
         ui.button(16, by, c.width - 32, 26, "Return home", Glyph.HOME, PhoneUi.Style.SECONDARY, () -> this.returnHome(true));
      } else {
         ui.button(16, by, c.width - 32, 26, "Travel here", Glyph.PLANE, PhoneUi.Style.PRIMARY, () -> {
            this.travel(d);
            this.viewing = null;
         });
      }
      c.textCentered("Uses Specter's sky, time and colour modules", c.width / 2, by + 34, PhoneTheme.CAPTION, t.textFaint());
   }
}
