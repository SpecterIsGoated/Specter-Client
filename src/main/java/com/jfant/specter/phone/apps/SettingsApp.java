package com.jfant.specter.phone.apps;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.modules.DonutPhone;
import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.api.AppPermission;
import com.jfant.specter.phone.api.AppSetting;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.core.PhoneApplicationManager;
import com.jfant.specter.phone.core.PhoneApplicationRegistry;
import com.jfant.specter.phone.core.PhoneConfig;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.core.Wallpapers;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** The phone's Settings: look, how it is held, notifications, every installed app (its permissions and settings), about. */
public final class SettingsApp extends PhoneApplication {
   private enum Page {
      MAIN,
      DISPLAY,
      HOLDING,
      NOTIFICATIONS,
      APPS,
      APP,
      ABOUT
   }

   private final Deque<Page> stack = new ArrayDeque<>();
   private Page page = Page.MAIN;
   private String appId;

   @Override
   protected void onRoute(String route) {
      for (Page p : Page.values()) {
         if (p.name().equalsIgnoreCase(route) && p != Page.APP) {
            this.stack.clear();
            this.page = Page.MAIN;
            if (p != Page.MAIN) this.go(p);
         }
      }
   }

   private void go(Page next) {
      this.stack.push(this.page);
      this.page = next;
   }

   @Override
   public boolean back() {
      if (this.stack.isEmpty()) return false;
      this.page = this.stack.pop();
      return true;
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      PhoneTheme t = c.theme();
      String title = switch (this.page) {
         case MAIN -> "Settings";
         case DISPLAY -> "Display";
         case HOLDING -> "Holding";
         case NOTIFICATIONS -> "Notifications";
         case APPS -> "Apps";
         case APP -> {
            PhoneApplicationRegistry.Entry e = PhoneOS.get().registry().get(this.appId);
            yield e == null ? "App" : e.info().name();
         }
         case ABOUT -> "About";
      };
      float top = ui.header(title, this.page == Page.MAIN ? null : this::back, null, null);
      ScrollView sv = ui.scroll(this.page + ":" + this.appId);
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      float h = this.contentHeight(c);
      sv.begin(ui, 0, top, c.width, bottom - top, h);
      float y = top + 4;
      switch (this.page) {
         case MAIN -> this.main(c, ui, y);
         case DISPLAY -> this.display(c, ui, y);
         case HOLDING -> this.holding(c, ui, y);
         case NOTIFICATIONS -> this.notifications(c, ui, y);
         case APPS -> this.apps(c, ui, y);
         case APP -> this.app(c, ui, y);
         case ABOUT -> this.about(c, ui, y);
      }
      sv.end(ui);
   }

   private float lastHeight = 400;

   private float contentHeight(PhoneCanvas c) {
      return this.lastHeight;
   }

   // ---------------------------------------------------------------------------------------------- pages

   private void main(PhoneCanvas c, PhoneUi ui, float y) {
      float x = 10, w = c.width - 20;
      Object[][] rows = {
         {Glyph.SUN, "Display", "Theme and wallpaper", Page.DISPLAY, 0xFF6366F1},
         {Glyph.USER, "Holding", "Size, position, chat key", Page.HOLDING, 0xFF0EA5E9},
         {Glyph.BELL, "Notifications", "Banners, sounds, per app", Page.NOTIFICATIONS, 0xFFF43F5E},
         {Glyph.GRID, "Apps", PhoneOS.get().registry().all().size() + " installed", Page.APPS, 0xFF10B981},
         {Glyph.INFO, "About", "Donut Phone", Page.ABOUT, 0xFF8B5CF6}
      };
      ui.card(x, y, w, rows.length * 34);
      for (int i = 0; i < rows.length; i++) {
         Object[] r = rows[i];
         float ry = y + i * 34;
         c.round(x + 8, ry + 8, 18, 18, 5, (int)r[4]);
         c.glyphAt((Glyph)r[0], x + 17, ry + 17, 11, 0xFFFFFFFF);
         Page target = (Page)r[3];
         ui.row(x + 24, ry, w - 24, (String)r[1], (String)r[2], () -> this.go(target));
         if (i > 0) ui.divider(x + 34, ry, w - 34);
      }
      this.lastHeight = rows.length * 34 + 30;
   }

   private void display(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      PhoneOS os = PhoneOS.get();
      float x = 10, w = c.width - 20;
      ui.section("Theme", x + 4, y);
      y += 10;
      List<PhoneTheme> themes = os.themes().themes();
      float tw = (w - 8 * (themes.size() - 1)) / themes.size();
      for (int i = 0; i < themes.size(); i++) {
         PhoneTheme th = themes.get(i);
         float tx = x + i * (tw + 8);
         boolean sel = th.id().equals(t.id());
         c.round(tx, y, tw, 70, 12, th.background());
         c.round(tx + 8, y + 10, tw - 16, 12, 4, th.surface());
         c.round(tx + 8, y + 26, (tw - 16) * 0.6F, 8, 4, th.accent());
         c.round(tx + 8, y + 38, tw - 16, 8, 4, th.surfaceHigh());
         c.textCentered(th.name(), tx + tw / 2, y + 55, PhoneTheme.CAPTION, th.text());
         if (sel) c.stroke(tx - 1.5F, y - 1.5F, tw + 3, 73, 13, 1.4F, t.accent());
         ui.onClick(tx, y, tw, 70, () -> os.themes().set(th.id()));
      }
      y += 84;
      ui.section("Wallpaper", x + 4, y);
      y += 10;
      int i = 0;
      float ww = (w - 16) / 3;
      for (Wallpapers.Wallpaper wp : Wallpapers.all()) {
         float wx = x + (i % 3) * (ww + 8), wy = y + (i / 3) * 78;
         boolean sel = wp.id().equals(os.themes().wallpaper());
         c.round(wx, wy, ww, 64, 10, wp.swatchTop(), wp.swatchBottom());
         c.textCentered(wp.name(), wx + ww / 2, wy + 68, PhoneTheme.CAPTION, t.textDim());
         if (sel) c.stroke(wx - 1.5F, wy - 1.5F, ww + 3, 67, 11, 1.4F, t.accent());
         ui.onClick(wx, wy, ww, 64, () -> os.themes().setWallpaper(wp.id()));
         i++;
      }
      y += ((i + 2) / 3) * 78 + 4;
      // your own pictures
      ui.section("Your pictures", x + 4, y);
      y += 10;
      List<String> custom = com.jfant.specter.phone.core.CustomWallpapers.list();
      int k = 0;
      for (String id : custom) {
         float wx = x + (k % 3) * (ww + 8), wy = y + (k / 3) * 78;
         boolean sel = id.equals(os.themes().wallpaper());
         net.minecraft.resources.Identifier thumb = com.jfant.specter.phone.core.CustomWallpapers.thumb(id);
         if (thumb != null) {
            c.image(thumb, wx, wy, ww, 64);
            c.cornerMask(wx, wy, ww, 64, 10, t.background());
         } else {
            c.round(wx, wy, ww, 64, 10, t.surfaceHigh());
            ui.spinner(wx + ww / 2, wy + 32, 5, t.textFaint());
         }
         if (sel) c.stroke(wx - 1.5F, wy - 1.5F, ww + 3, 67, 11, 1.4F, t.accent());
         ui.onClick(wx, wy, ww, 64, () -> os.themes().setWallpaper(id));
         k++;
      }
      {
         float wx = x + (k % 3) * (ww + 8), wy = y + (k / 3) * 78;
         boolean hover = ui.hovered(wx, wy, ww, 64);
         c.round(wx, wy, ww, 64, 10, hover ? t.accentSoft() : t.surface());
         c.stroke(wx, wy, ww, 64, 10, 0.8F, t.separator());
         c.glyphAt(Glyph.PLUS, wx + ww / 2, wy + 26, 14, t.accent());
         c.textCentered("Add picture", wx + ww / 2, wy + 42, PhoneTheme.CAPTION, t.accent());
         ui.onClick(wx, wy, ww, 64, () -> com.jfant.specter.core.utils.misc.FileDialogs.openFile("png;jpg;jpeg;webp", file -> os.useWallpaperFile(file.toPath())));
         k++;
      }
      y += ((k + 2) / 3) * 78 + 2;
      String current = os.themes().wallpaper();
      if (com.jfant.specter.phone.core.CustomWallpapers.isCustom(current)) {
         ui.button(x, y, w, 22, "Remove this picture", Glyph.TRASH, PhoneUi.Style.GHOST, () -> {
            com.jfant.specter.phone.core.CustomWallpapers.remove(current);
            os.themes().setWallpaper(os.themes().theme().wallpaper());
         });
         y += 28;
      }
      y += c.paragraph("A 9:16 picture fits best. You can also drop a PNG onto the game window while the phone is out.", x + 4, y, w - 8,
         PhoneTheme.CAPTION, t.textFaint(), 3) + 8;
      PhoneConfig cfg = os.config();
      ui.card(x, y, w, 30);
      c.textMid("24-hour clock", x + 10, y + 15, PhoneTheme.BODY, t.text());
      ui.toggle("24h", x + w - 36, y + 7.5F, cfg.use24h, v -> {
         cfg.use24h = v;
         cfg.markDirty();
      });
      this.lastHeight = y + 40 - (PhoneCanvas.SAFE_TOP + 36);
   }

   private void holding(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      DonutPhone m = PhoneOS.module();
      float x = 10, w = c.width - 20;
      if (m == null) return;
      ui.section("Position", x + 4, y);
      y += 10;
      ui.card(x, y, w, 150);
      y = this.slider(c, ui, x, y, w, "Size", m.scaleSetting(), 0.6, 1.3);
      y = this.slider(c, ui, x, y, w, "Left / right", m.offsetXSetting(), -1, 1);
      y = this.slider(c, ui, x, y, w, "Down / up", m.offsetYSetting(), -1, 1);
      y = this.slider(c, ui, x, y, w, "Animation speed", m.speedSetting(), 0.3, 3);
      y += 14;
      ui.section("Holding", x + 4, y);
      y += 10;
      ui.card(x, y, w, 62);
      c.textMid("Show my arm", x + 10, y + 15, PhoneTheme.BODY, t.text());
      ui.toggle("arm", x + w - 36, y + 7.5F, m.showArm(), v -> m.showArmSetting().set(v));
      ui.divider(x + 10, y + 31, w - 10);
      c.textMid("Open with chat key", x + 10, y + 46, PhoneTheme.BODY, t.text());
      ui.toggle("chatkey", x + w - 36, y + 38.5F, m.opensWithChatKey(), v -> m.chatKeySetting().set(v));
      y += 70;
      c.paragraph(m.opensWithChatKey()
         ? "Press your chat key for the phone. Press it again (while not typing) for regular chat. / still opens chat."
         : "The chat key opens chat as normal. Set an open key for the phone in the Donut Phone module.", x + 4, y, w - 8, PhoneTheme.CAPTION, t.textDim(), 4);
      this.lastHeight = y + 40 - (PhoneCanvas.SAFE_TOP + 36);
   }

   private float slider(PhoneCanvas c, PhoneUi ui, float x, float y, float w, String label, Setting<Double> setting, double min, double max) {
      PhoneTheme t = c.theme();
      c.text(label, x + 10, y + 9, PhoneTheme.SMALL, t.text());
      c.textRight(String.format("%.2f", setting.get()), x + w - 10, y + 9, PhoneTheme.SMALL, t.textDim());
      ui.slider(x + 12, y + 20, w - 24, setting.get(), min, max, v -> setting.set(Math.round(v * 100) / 100.0));
      return y + 37;
   }

   private void notifications(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      PhoneOS os = PhoneOS.get();
      PhoneConfig cfg = os.config();
      float x = 10, w = c.width - 20;
      ui.card(x, y, w, 30);
      c.textMid("Lock screen", x + 10, y + 15, PhoneTheme.BODY, t.text());
      ui.toggle("lockscreen", x + w - 36, y + 7.5F, cfg.lockScreen, v -> {
         cfg.lockScreen = v;
         cfg.markDirty();
      });
      y += 38;
      ui.card(x, y, w, 62);
      c.textMid("Banners", x + 10, y + 15, PhoneTheme.BODY, t.text());
      ui.toggle("banners", x + w - 36, y + 7.5F, cfg.banners, v -> {
         cfg.banners = v;
         cfg.markDirty();
      });
      ui.divider(x + 10, y + 31, w - 10);
      c.textMid("Toasts while put away", x + 10, y + 46, PhoneTheme.BODY, t.text());
      ui.toggle("toasts", x + w - 36, y + 38.5F, cfg.toastsWhenClosed, v -> {
         cfg.toastsWhenClosed = v;
         cfg.markDirty();
      });
      y += 72;
      ui.section("Allow notifications", x + 4, y);
      y += 10;
      List<PhoneApplicationRegistry.Entry> apps = os.registry().all().stream().filter(e -> e.info().may(AppPermission.NOTIFICATIONS)).toList();
      ui.card(x, y, w, Math.max(1, apps.size()) * 30);
      for (int i = 0; i < apps.size(); i++) {
         PhoneApplicationRegistry.Entry e = apps.get(i);
         float ry = y + i * 30;
         if (i > 0) ui.divider(x + 32, ry, w - 32);
         c.icon(e.info().icon(), x + 8, ry + 7, 16);
         c.textMid(e.info().name(), x + 32, ry + 15, PhoneTheme.BODY, t.text());
         PhoneConfig.NotifyPrefs p = cfg.prefs(e.info().id());
         ui.toggle("n:" + e.info().id(), x + w - 36, ry + 7.5F, p.enabled, v -> {
            p.enabled = v;
            cfg.markDirty();
         });
      }
      this.lastHeight = y + apps.size() * 30 + 20 - (PhoneCanvas.SAFE_TOP + 36);
   }

   private void apps(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      List<PhoneApplicationRegistry.Entry> apps = PhoneOS.get().registry().all();
      float x = 10, w = c.width - 20;
      ui.card(x, y, w, apps.size() * 34);
      for (int i = 0; i < apps.size(); i++) {
         PhoneApplicationRegistry.Entry e = apps.get(i);
         float ry = y + i * 34;
         if (i > 0) ui.divider(x + 34, ry, w - 34);
         c.icon(e.info().icon(), x + 8, ry + 8, 18);
         String id = e.info().id();
         ui.row(x + 24, ry, w - 24, e.info().name(), e.info().category().title + " · v" + e.info().version(), () -> {
            this.appId = id;
            this.go(Page.APP);
         });
      }
      this.lastHeight = apps.size() * 34 + 20;
   }

   private void app(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      PhoneOS os = PhoneOS.get();
      PhoneApplicationRegistry.Entry e = os.registry().get(this.appId);
      if (e == null) return;
      float x = 10, w = c.width - 20, y0 = y;
      c.icon(e.info().icon(), x + 4, y, 40);
      c.text(c.fit(e.info().name(), w - 56, PhoneTheme.HEADLINE), x + 54, y + 8, PhoneTheme.HEADLINE, t.text());
      c.text("Version " + e.info().version() + " · " + e.info().category().title, x + 54, y + 22, PhoneTheme.CAPTION, t.textDim());
      y += 50;
      y += c.paragraph(e.info().description(), x + 4, y, w - 8, PhoneTheme.SMALL, t.textDim(), 6) + 8;
      if (!e.info().permissions().isEmpty()) {
         ui.section("Uses", x + 4, y);
         y += 10;
         for (AppPermission p : e.info().permissions()) {
            ui.card(x, y, w, 30);
            c.text(p.title, x + 10, y + 7, PhoneTheme.SMALL, t.text());
            c.text(c.fit(p.about, w - 20, PhoneTheme.CAPTION), x + 10, y + 18, PhoneTheme.CAPTION, t.textDim());
            y += 34;
         }
         y += 6;
      }
      PhoneApplicationManager.Running r = os.apps().launch(this.appId);
      List<AppSetting<?>> settings = r == null || r.app() == null ? List.of() : r.app().settings();
      if (!settings.isEmpty()) {
         ui.section("Settings", x + 4, y);
         y += 10;
         for (AppSetting<?> s : settings) {
            y = this.appSetting(c, ui, r, s, x, y, w);
         }
         y += 6;
      }
      if (!e.info().system()) {
         PhoneConfig cfg = os.config();
         boolean hidden = cfg.hidden.contains(this.appId);
         ui.card(x, y, w, 30);
         c.textMid("Show on home screen", x + 10, y + 15, PhoneTheme.BODY, t.text());
         String id = this.appId;
         ui.toggle("home:" + id, x + w - 36, y + 7.5F, !hidden, v -> {
            if (v) cfg.hidden.remove(id);
            else if (!cfg.hidden.contains(id)) cfg.hidden.add(id);
            cfg.markDirty();
         });
         y += 38;
      }
      if (r != null && r.lastUsedMillis() > 0 && !e.info().system()) {
         String id = this.appId;
         ui.button(x, y, w, 26, "Force stop", PhoneUi.Style.SECONDARY, () -> {
            os.killApp(id);
            os.toast("Stopped");
         });
         y += 32;
      }
      this.lastHeight = y - y0 + 20;
   }

   @SuppressWarnings("unchecked")
   private float appSetting(PhoneCanvas c, PhoneUi ui, PhoneApplicationManager.Running r, AppSetting<?> s, float x, float y, float w) {
      PhoneTheme t = c.theme();
      switch (s.kind) {
         case TOGGLE -> {
            AppSetting<Boolean> b = (AppSetting<Boolean>)s;
            ui.card(x, y, w, 30);
            c.textMid(c.fit(s.title, w - 50, PhoneTheme.BODY), x + 10, y + 15, PhoneTheme.BODY, t.text());
            ui.toggle(r.info().id() + ":" + s.key, x + w - 36, y + 7.5F, b.get(r.context()), v -> b.set(r.context(), v));
            return y + 34;
         }
         case CHOICE -> {
            AppSetting<String> ch = (AppSetting<String>)s;
            ui.card(x, y, w, 46);
            c.text(s.title, x + 10, y + 8, PhoneTheme.SMALL, t.text());
            String[] opts = s.options.toArray(new String[0]);
            int sel = Math.max(0, s.options.indexOf(ch.get(r.context())));
            ui.segmented(x + 8, y + 22, w - 16, 17, opts, sel, idx -> ch.set(r.context(), opts[idx]));
            return y + 50;
         }
         default -> {
            AppSetting<Double> n = (AppSetting<Double>)s;
            ui.card(x, y, w, 40);
            double v = n.get(r.context());
            c.text(s.title, x + 10, y + 8, PhoneTheme.SMALL, t.text());
            c.textRight(s.step >= 1 ? String.valueOf((long)Math.round(v)) : String.format("%.2f", v), x + w - 10, y + 8, PhoneTheme.SMALL, t.textDim());
            ui.slider(x + 12, y + 22, w - 24, v, s.min, s.max, nv -> n.set(r.context(), Math.round(nv / s.step) * s.step));
            return y + 44;
         }
      }
   }

   private void about(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      float x = 10, w = c.width - 20;
      c.icon(AppIcons.SPECTER, c.width / 2 - 24, y + 6, 48);
      c.textCentered("Donut Phone", c.width / 2, y + 64, PhoneTheme.TITLE, t.text());
      c.textCentered("Specter " + SpecterCore.VERSION, c.width / 2, y + 80, PhoneTheme.SMALL, t.textDim());
      y += 100;
      ui.card(x, y, w, 64);
      c.textMid("Apps installed", x + 10, y + 16, PhoneTheme.BODY, t.text());
      c.textRight(String.valueOf(PhoneOS.get().registry().all().size()), x + w - 10, y + 16 - PhoneTheme.BODY / 2, PhoneTheme.BODY, t.textDim());
      ui.divider(x + 10, y + 32, w - 10);
      c.textMid("Notifications kept", x + 10, y + 48, PhoneTheme.BODY, t.text());
      c.textRight(String.valueOf(PhoneOS.get().notifications().all().size()), x + w - 10, y + 48 - PhoneTheme.BODY / 2, PhoneTheme.BODY, t.textDim());
      y += 74;
      c.paragraph("Apps for Donut Phone can come from other mods through the specter:phone entrypoint.", x + 4, y, w - 8, PhoneTheme.CAPTION, t.textFaint(), 3);
      this.lastHeight = 220;
   }
}
