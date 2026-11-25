package com.jfant.specter.phone.core;

import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.gfx.Colors;
import com.jfant.specter.phone.render.PhoneMesh;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import java.time.LocalTime;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of the screen that belong to the phone, not to an app: status bar, navigation bar, notification shade
 * (with quick toggles), banners, toasts, the app switcher and the "app stopped" card.
 */
public final class PhoneSystemUi {
   /** Punch-hole camera, in phone points. */
   public static final float HOLE_Y = (PhoneAnimationController.SCREEN_H / 2 - PhoneMesh.HOLE_Y) / PhoneAnimationController.SCREEN_H * PhoneCanvas.HEIGHT;
   public static final float HOLE_R = PhoneMesh.HOLE_R / PhoneAnimationController.SCREEN_W * PhoneCanvas.WIDTH;

   /** Status bar, nav bar, banner and toast: always on top of the app. */
   public final PhoneUi bars = new PhoneUi();
   public final PhoneUi shadeUi = new PhoneUi();
   public final PhoneUi switcherUi = new PhoneUi();
   public final PhoneUi lockUi = new PhoneUi();
   private boolean shadeOpen;
   private float shadeAnim;
   private PhoneNotificationManager.Notification banner;
   private long bannerSince;
   private String toast;
   private long toastSince;

   public boolean shadeOpen() {
      return this.shadeOpen || this.shadeAnim > 0.02F;
   }

   public void openShade() {
      this.shadeOpen = true;
   }

   public void closeShade() {
      this.shadeOpen = false;
   }

   public void toast(String text) {
      this.toast = text;
      this.toastSince = System.currentTimeMillis();
   }

   // ============================================================================================== bars

   public void renderBars(PhoneCanvas c, PhoneOS os, boolean lightTop, float mx, float my, float dt) {
      PhoneTheme t = c.theme();
      this.bars.begin(c, mx, my, dt);
      int fg = lightTop && !this.shadeOpen() ? 0xFF111111 : 0xFFFFFFFF;
      // status bar
      LocalTime now = LocalTime.now();
      String time = os.config().use24h ? String.format("%02d:%02d", now.getHour(), now.getMinute())
         : String.format("%d:%02d", (now.getHour() + 11) % 12 + 1, now.getMinute());
      c.textMid(time, 15, HOLE_Y, PhoneTheme.SMALL, fg);
      this.signal(c, c.width - 40, HOLE_Y, fg);
      this.battery(c, c.width - 27, HOLE_Y, fg);
      int unread = os.notifications().unreadTotal();
      if (unread > 0) c.circle(52, HOLE_Y, 2.3F, t.accent());
      this.bars.onClick(0, 0, c.width, PhoneCanvas.SAFE_TOP - 4, () -> {
         if (this.shadeOpen) this.closeShade();
         else this.openShade();
      });
      // navigation bar
      float ny = c.height - PhoneCanvas.SAFE_BOTTOM / 2 - 1;
      int navFg = lightTop && os.navigation().layer() == PhoneNavigationManager.Layer.APP && !this.shadeOpen() ? 0x99000000 : 0xB0FFFFFF;
      this.bars.iconButton(40, ny, 9, Glyph.BACK, navFg, os::back);
      boolean homeHover = this.bars.hovered(c.width / 2 - 28, ny - 8, 56, 16);
      c.round(c.width / 2 - 22, ny - 2, 44, 4, 2, homeHover ? Colors.alpha(navFg, 1.0F) : navFg);
      this.bars.onClick(c.width / 2 - 28, ny - 8, 56, 16, os::goHome);
      this.bars.iconButton(c.width - 40, ny, 9, Glyph.GRID, navFg, os::switcher);
      // banner
      this.renderBanner(c, os);
      // toast
      if (this.toast != null) {
         long age = System.currentTimeMillis() - this.toastSince;
         if (age > 2400) {
            this.toast = null;
         } else {
            float a = Math.min(1, Math.min(age / 150.0F, (2400 - age) / 250.0F));
            float w = Math.min(c.width - 30, c.width(this.toast, PhoneTheme.SMALL) + 22);
            float y = c.height - PhoneCanvas.SAFE_BOTTOM - 30 + (1 - a) * 8;
            c.alpha(a);
            c.round((c.width - w) / 2, y, w, 20, 10, 0xEE24202C);
            c.textCentered(c.fit(this.toast, w - 16, PhoneTheme.SMALL), c.width / 2, y + 10 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, 0xFFFFFFFF);
            c.popAlpha();
         }
      }
      this.bars.end();
   }

   /** Signal bars from the real server ping (no bars in singleplayer = not connected anywhere). */
   private void signal(PhoneCanvas c, float x, float cy, int fg) {
      Minecraft mc = Minecraft.getInstance();
      int bars = 0;
      if (mc.getConnection() != null && mc.player != null) {
         if (mc.isLocalServer()) {
            bars = 4;
         } else {
            PlayerInfo me = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            int ping = me == null ? -1 : me.getLatency();
            bars = ping < 0 ? 0 : ping < 80 ? 4 : ping < 150 ? 3 : ping < 300 ? 2 : 1;
         }
      }
      for (int i = 0; i < 4; i++) {
         float h = 2.5F + i * 1.5F;
         c.round(x + i * 2.6F, cy + 3.5F - h, 1.8F, h, 0.6F, i < bars ? fg : Colors.alpha(fg, 0.3F));
      }
   }

   /** A battery that shows your health (the only "battery" a Minecraft character has). */
   private void battery(PhoneCanvas c, float x, float cy, int fg) {
      Minecraft mc = Minecraft.getInstance();
      float level = mc.player == null ? 1 : Math.clamp(mc.player.getHealth() / Math.max(1, mc.player.getMaxHealth()), 0, 1);
      c.stroke(x, cy - 3.2F, 13, 6.4F, 1.8F, 0.6F, Colors.alpha(fg, 0.6F));
      c.round(x + 13.6F, cy - 1.2F, 1.1F, 2.4F, 0.5F, Colors.alpha(fg, 0.6F));
      int fill = level < 0.25F ? 0xFFFF453A : fg;
      c.round(x + 1.2F, cy - 2.0F, Math.max(0.8F, 10.6F * level), 4.0F, 1.0F, fill);
   }

   private void renderBanner(PhoneCanvas c, PhoneOS os) {
      PhoneNotificationManager.Notification n = os.notifications().banner(this.banner == null ? 0 : System.currentTimeMillis() - this.bannerSince);
      if (n != this.banner) {
         this.banner = n;
         this.bannerSince = System.currentTimeMillis();
      }
      if (n == null || this.shadeOpen() || os.locked()) return;
      long age = System.currentTimeMillis() - this.bannerSince;
      long life = PhoneNotificationManager.bannerMillis(n);
      float in = Math.min(1, age / 220.0F);
      float out = Math.min(1, (life - age) / 220.0F);
      float k = Math.max(0, Math.min(in, out));
      float ease = 1 - (1 - k) * (1 - k);
      float x = 7, w = c.width - 14, h = cardHeight(n);
      float y = PhoneCanvas.SAFE_TOP - 4 - (1 - ease) * (h + 10);
      c.alpha(ease);
      PhoneNotificationManager.Notification shown = n;
      this.bars.onClick(x, y, w, h, () -> {
         os.notifications().skipBanner();
         os.openFromNotification(shown);
      });
      this.card(c, this.bars, os, n, x, y, w, true);
      c.popAlpha();
   }

   /** One notification: app icon and name, when, title and text. */
   /** Height of a notification card (taller when it has live buttons). */
   static float cardHeight(PhoneNotificationManager.Notification n) {
      return n.actionsLive() ? 66 : 44;
   }

   /** One notification: app icon and name, when, title and text, and its buttons while they still work. */
   private void card(PhoneCanvas c, PhoneUi ui, PhoneOS os, PhoneNotificationManager.Notification n, float x, float y, float w, boolean floating) {
      float h = cardHeight(n);
      this.cardBody(c, os, n, x, y, w, h, floating);
      if (n.actionsLive()) {
         int count = n.actions.size();
         float bw = (w - 18 - (count - 1) * 6) / count;
         for (int i = 0; i < count; i++) {
            com.jfant.specter.phone.api.NotificationAction a = n.actions.get(i);
            ui.button(x + 9 + i * (bw + 6), y + 43, bw, 17, a.label(), a.primary() ? PhoneUi.Style.PRIMARY : PhoneUi.Style.SECONDARY, () -> os.runAction(n, a));
         }
      }
   }

   private void cardBody(PhoneCanvas c, PhoneOS os, PhoneNotificationManager.Notification n, float x, float y, float w, float h, boolean floating) {
      PhoneTheme t = c.theme();
      if (floating) c.shadow(x, y, w, h, 14, 4, 0x80000000);
      c.round(x, y, w, h, 14, t.dark() ? 0xF2241F2E : 0xF8FFFFFF);
      PhoneApplicationRegistry.Entry e = os.registry().get(n.app);
      if (e != null) c.icon(e.info().icon(), x + 8, y + 8, 14);
      String app = e != null ? e.info().name() : n.app;
      c.textMid(c.fit(app.toUpperCase(java.util.Locale.ROOT), w - 80, PhoneTheme.CAPTION), x + 27, y + 15, PhoneTheme.CAPTION, t.textDim());
      c.textRight(ago(n.time), x + w - 9, y + 15 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, t.textFaint());
      c.text(c.fit(n.title, w - 18, PhoneTheme.SMALL), x + 9, y + 25, PhoneTheme.SMALL, t.text());
      c.text(c.fit(n.text, w - 18, PhoneTheme.CAPTION), x + 9, y + 35, PhoneTheme.CAPTION, t.textDim());
      if (!n.read) c.circle(x + w - 6, y + 6, 2, t.accent());
   }

   public static String ago(long time) {
      long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
      if (s < 45) return "now";
      if (s < 3600) return s / 60 + "m";
      if (s < 86400) return s / 3600 + "h";
      return s / 86400 + "d";
   }

   // ============================================================================================== shade

   public void renderShade(PhoneCanvas c, PhoneOS os, float mx, float my, float dt) {
      this.shadeAnim += ((this.shadeOpen ? 1 : 0) - this.shadeAnim) * (1 - (float)Math.exp(-dt * 16));
      if (this.shadeAnim < 0.01F) {
         this.shadeAnim = 0;
         return;
      }
      PhoneTheme t = c.theme();
      float k = this.shadeAnim;
      this.shadeUi.begin(c, mx, my, dt);
      c.alpha(k);
      // solid: at 90% the lock screen's big clock and date showed through behind the toggles and "Notifications"
      c.rect(0, 0, c.width, c.height, 0xFF0B0910);
      c.push(0, (1 - k) * -40);
      float y = PhoneCanvas.SAFE_TOP + 4;
      // quick toggles
      PhoneConfig cfg = os.config();
      float tw = (c.width - 30) / 3;
      this.quick(c, 10, y, tw, Glyph.BELL, "Banners", cfg.banners, () -> {
         cfg.banners = !cfg.banners;
         cfg.markDirty();
      });
      this.quick(c, 15 + tw, y, tw, Glyph.SPARK, "Sounds", os.soundsOn(), os::toggleSounds);
      boolean dark = t.dark();
      this.quick(c, 20 + tw * 2, y, tw, dark ? Glyph.MOON : Glyph.SUN, dark ? "Dark" : "Light", true, () -> {
         os.themes().set(dark ? "daylight" : "midnight");
      });
      y += 50;
      c.textMid("Notifications", 12, y + 6, PhoneTheme.HEADLINE, 0xFFFFFFFF);
      List<PhoneNotificationManager.Notification> all = os.notifications().all();
      if (!all.isEmpty()) {
         float cw = c.width("Clear", PhoneTheme.SMALL);
         c.textMid("Clear", c.width - 12 - cw, y + 6, PhoneTheme.SMALL, t.accent());
         this.shadeUi.onClick(c.width - 20 - cw, y - 3, cw + 16, 18, () -> os.notifications().clear());
      }
      y += 16;
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM - 4;
      if (all.isEmpty()) {
         c.textCentered("No notifications", c.width / 2, y + 60, PhoneTheme.BODY, 0x99FFFFFF);
      } else {
         ScrollView sv = this.shadeUi.scroll("shade");
         float total = 0;
         for (PhoneNotificationManager.Notification n : all) total += cardHeight(n) + 6;
         sv.begin(this.shadeUi, 0, y, c.width, bottom - y, total);
         float cy = y;
         for (PhoneNotificationManager.Notification n : all) {
            float h = cardHeight(n);
            this.shadeUi.onClick(8, cy, c.width - 16, h, () -> {
               this.closeShade();
               os.openFromNotification(n);
            });
            this.card(c, this.shadeUi, os, n, 8, cy, c.width - 16, false);
            cy += h + 6;
         }
         sv.end(this.shadeUi);
      }
      c.pop();
      c.popAlpha();
      this.shadeUi.end();
   }

   private void quick(PhoneCanvas c, float x, float y, float w, Glyph glyph, String label, boolean on, Runnable action) {
      PhoneTheme t = c.theme();
      boolean hover = this.shadeUi.hovered(x, y, w, 42);
      c.round(x, y, w, 42, 12, on ? t.accent() : hover ? 0x40FFFFFF : 0x2EFFFFFF);
      c.glyphAt(glyph, x + w / 2, y + 15, 12, 0xFFFFFFFF);
      c.textCentered(c.fit(label, w - 6, PhoneTheme.CAPTION), x + w / 2, y + 28, PhoneTheme.CAPTION, 0xFFFFFFFF);
      this.shadeUi.onClick(x, y, w, 42, action);
   }

   // ============================================================================================== lock screen

   /**
    * The lock screen: the time, the date and your unread notifications (with Accept/Deny on teleport requests).
    * Click a notification to open it, anywhere else to unlock.
    */
   public void renderLock(PhoneCanvas c, PhoneOS os, float mx, float my, float dt) {
      PhoneUi ui = this.lockUi;
      ui.begin(c, mx, my, dt);
      c.gradient(0, 0, c.width, c.height, 0x40000000, 0xA0000000);
      ui.onClick(0, PhoneCanvas.SAFE_TOP, c.width, c.height - PhoneCanvas.SAFE_TOP, os::unlock);
      LocalTime now = LocalTime.now();
      String time = os.config().use24h ? String.format("%02d:%02d", now.getHour(), now.getMinute())
         : String.format("%d:%02d", (now.getHour() + 11) % 12 + 1, now.getMinute());
      c.textCentered(time, c.width / 2, 44, 23.0F, 0xFFFFFFFF);
      c.textCentered(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.ENGLISH).format(java.time.LocalDate.now()),
         c.width / 2, 78, PhoneTheme.SMALL, 0xDDFFFFFF);
      List<PhoneNotificationManager.Notification> list = os.notifications().unreadList();
      float y = 100, limit = c.height - PhoneCanvas.SAFE_BOTTOM - 44;
      int shown = 0;
      for (PhoneNotificationManager.Notification n : list) {
         float h = cardHeight(n);
         if (y + h > limit) break;
         ui.onClick(8, y, c.width - 16, h, () -> os.openFromNotification(n));
         this.card(c, ui, os, n, 8, y, c.width - 16, false);
         y += h + 6;
         shown++;
      }
      if (list.isEmpty()) {
         c.textCentered("No new notifications", c.width / 2, c.height / 2 - 4, PhoneTheme.SMALL, 0x99FFFFFF);
      } else {
         if (list.size() > shown) c.textCentered("+" + (list.size() - shown) + " more", c.width / 2, y + 2, PhoneTheme.CAPTION, 0xBBFFFFFF);
         float cw = c.width("Clear all", PhoneTheme.CAPTION) + 14;
         ui.button(c.width / 2 - cw / 2, limit + 4, cw, 14, "Clear all", PhoneUi.Style.GHOST, () -> os.notifications().markAllRead());
      }
      float pulse = 0.6F + 0.4F * (float)Math.sin(System.nanoTime() / 2.5E8);
      c.glyphAt(Glyph.UP, c.width / 2, c.height - PhoneCanvas.SAFE_BOTTOM - 16, 9, com.jfant.specter.phone.gfx.Colors.alpha(0xFFFFFFFF, pulse));
      c.textCentered("Click to unlock", c.width / 2, c.height - PhoneCanvas.SAFE_BOTTOM - 9, PhoneTheme.CAPTION, 0xCCFFFFFF);
      ui.end();
   }

   // ============================================================================================== switcher

   public void renderSwitcher(PhoneCanvas c, PhoneOS os, float mx, float my, float dt) {
      PhoneTheme t = c.theme();
      this.switcherUi.begin(c, mx, my, dt);
      c.rect(0, 0, c.width, c.height, 0xB0000000);
      List<PhoneApplicationManager.Running> recents = os.apps().recents();
      float y0 = PhoneCanvas.SAFE_TOP + 8;
      c.textMid("Recent apps", 12, y0 + 6, PhoneTheme.HEADLINE, 0xFFFFFFFF);
      if (recents.isEmpty()) {
         c.textCentered("Nothing open", c.width / 2, c.height / 2, PhoneTheme.BODY, 0x99FFFFFF);
      } else {
         float cw = c.width("Close all", PhoneTheme.SMALL);
         c.textMid("Close all", c.width - 12 - cw, y0 + 6, PhoneTheme.SMALL, t.accent());
         this.switcherUi.onClick(c.width - 20 - cw, y0 - 3, cw + 16, 18, os::closeAllApps);
         ScrollView sv = this.switcherUi.scroll("recents");
         float top = y0 + 20, h = 62, bottom = c.height - PhoneCanvas.SAFE_BOTTOM - 4;
         sv.begin(this.switcherUi, 0, top, c.width, bottom - top, recents.size() * (h + 8));
         for (int i = 0; i < recents.size(); i++) {
            PhoneApplicationManager.Running r = recents.get(i);
            float y = top + i * (h + 8);
            int accent = r.info().accent();
            c.shadow(10, y, c.width - 20, h, 16, 3, 0x70000000);
            c.round(10, y, c.width - 20, h, 16, Colors.shade(accent, 0.55F), Colors.shade(accent, 0.25F));
            c.icon(r.info().icon(), 20, y + 14, 34);
            c.text(c.fit(r.info().name(), c.width - 110, PhoneTheme.HEADLINE), 62, y + 18, PhoneTheme.HEADLINE, 0xFFFFFFFF);
            c.text(r.crash != null ? "Stopped" : "Open " + ago(r.lastUsedMillis()) + (r.lastUsedMillis() > 0 && ago(r.lastUsedMillis()).equals("now") ? "" : " ago"),
               62, y + 34, PhoneTheme.CAPTION, 0xBBFFFFFF);
            String id = r.info().id();
            this.switcherUi.iconButton(c.width - 26, y + h / 2, 9, Glyph.CLOSE, 0xDDFFFFFF, () -> os.killApp(id));
            this.switcherUi.onClick(10, y, c.width - 50, h, () -> os.openApp(id, null, c.width / 2, y + h / 2));
         }
         sv.end(this.switcherUi);
      }
      this.switcherUi.end();
   }

   // ============================================================================================== crash card

   public void renderCrash(PhoneCanvas c, PhoneUi ui, PhoneOS os, PhoneApplicationManager.Running r) {
      PhoneTheme t = c.theme();
      c.rect(0, 0, c.width, c.height, t.background());
      float cy = c.height / 2 - 60;
      c.icon(r.info().icon(), c.width / 2 - 22, cy, 44);
      c.textCentered(r.info().name() + " stopped", c.width / 2, cy + 58, PhoneTheme.HEADLINE, t.text());
      String why = r.crash == null ? "" : r.crash.getClass().getSimpleName() + (r.crash.getMessage() != null ? ": " + r.crash.getMessage() : "");
      List<String> lines = c.wrap(why, c.width - 40, PhoneTheme.CAPTION, 3);
      for (int i = 0; i < lines.size(); i++) c.textCentered(lines.get(i), c.width / 2, cy + 74 + i * 8, PhoneTheme.CAPTION, t.textDim());
      c.textCentered("The rest of the phone is fine.", c.width / 2, cy + 104, PhoneTheme.CAPTION, t.textFaint());
      String id = r.info().id();
      ui.button(24, cy + 122, c.width - 48, 26, "Reopen", PhoneUi.Style.PRIMARY, () -> os.restartApp(id));
      ui.button(24, cy + 154, c.width - 48, 26, "Close", PhoneUi.Style.SECONDARY, () -> {
         os.killApp(id);
         os.goHome();
      });
   }

   @Nullable
   public PhoneNotificationManager.Notification banner() {
      return this.banner;
   }
}
