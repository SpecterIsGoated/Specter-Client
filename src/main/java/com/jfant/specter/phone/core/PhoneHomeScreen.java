package com.jfant.specter.phone.core;

import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The home screen: a glass widget with the time and where you are, the app grid (pages of 4 x 4, built from the
 * registry, so new apps appear by themselves), page dots and the dock. Icons show unread badges.
 */
public final class PhoneHomeScreen {
   private static final int COLUMNS = 4;
   private static final int ROWS = 4;
   private static final float ICON = 33.0F;
   private static final float CELL_H = 54.0F;
   private static final float GRID_TOP = 112.0F;
   private static final float DOCK_TOP = 318.0F;
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);

   public final PhoneUi ui = new PhoneUi();
   private int page;
   private float pageAnim;
   private int lastWheelPage;

   public void render(PhoneCanvas c, PhoneOS os, float mx, float my, float dt) {
      PhoneTheme t = c.theme();
      this.ui.begin(c, mx, my, dt);
      this.widget(c, os);
      List<PhoneApplicationRegistry.Entry> grid = new ArrayList<>();
      List<PhoneApplicationRegistry.Entry> dock = new ArrayList<>();
      PhoneConfig config = os.config();
      for (String id : config.dock) {
         PhoneApplicationRegistry.Entry e = os.registry().get(id);
         if (e != null && dock.size() < 4) dock.add(e);
      }
      for (PhoneApplicationRegistry.Entry e : os.registry().all()) {
         if (dock.contains(e) || config.hidden.contains(e.info().id()) && !e.info().system()) continue;
         grid.add(e);
      }
      int perPage = COLUMNS * ROWS;
      int pages = Math.max(1, (grid.size() + perPage - 1) / perPage);
      this.page = Math.clamp(this.page, 0, pages - 1);
      this.pageAnim += (this.page - this.pageAnim) * (1 - (float)Math.exp(-dt * 14));
      float cellW = (c.width - 12) / COLUMNS;
      c.clip(0, GRID_TOP - 6, c.width, DOCK_TOP - GRID_TOP - 2);
      for (int i = 0; i < grid.size(); i++) {
         int p = i / perPage, k = i % perPage;
         float x = 6 + (p - this.pageAnim) * c.width + (k % COLUMNS) * cellW;
         float y = GRID_TOP + (k / COLUMNS) * CELL_H;
         if (x < -cellW || x > c.width) continue;
         this.icon(c, os, grid.get(i), x + (cellW - ICON) / 2, y, true);
      }
      c.unclip();
      if (pages > 1) {
         float dotsW = pages * 8;
         for (int p = 0; p < pages; p++) {
            int pi = p;
            float dx = c.width / 2 - dotsW / 2 + p * 8 + 4;
            c.circle(dx, DOCK_TOP - 9, 2.2F, p == this.page ? 0xF0FFFFFF : 0x66FFFFFF);
            this.ui.onClick(dx - 4, DOCK_TOP - 14, 8, 10, () -> this.page = pi);
         }
      }
      // dock
      c.round(7, DOCK_TOP, c.width - 14, 48, 18, t.dark() ? 0x3CFFFFFF : 0x55FFFFFF);
      c.stroke(7, DOCK_TOP, c.width - 14, 48, 18, 0.6F, 0x22FFFFFF);
      float dockCell = (c.width - 14) / 4;
      float dockStart = 7 + (c.width - 14 - dock.size() * dockCell) / 2;
      for (int i = 0; i < dock.size(); i++) {
         this.icon(c, os, dock.get(i), dockStart + i * dockCell + (dockCell - ICON) / 2, DOCK_TOP + 7.5F, false);
      }
      this.ui.end();
   }

   private void icon(PhoneCanvas c, PhoneOS os, PhoneApplicationRegistry.Entry e, float x, float y, boolean label) {
      boolean hover = this.ui.hovered(x - 3, y - 3, ICON + 6, ICON + 6);
      boolean down = this.ui.held(x - 3, y - 3, ICON + 6, ICON + 6);
      float lift = this.ui.ease("lift:" + e.info().id(), hover ? 1 : 0, 16);
      c.shadow(x, y + 1, ICON, ICON, 8, 2.5F + lift * 1.5F, 0x60000000);
      c.push(0, -lift * 1.2F);
      c.icon(e.info().icon(), x, y, ICON);
      if (down) c.round(x, y, ICON, ICON, 8, 0x40000000);
      int unread = os.notifications().unread(e.info().id());
      this.ui.badge(x + ICON - 3, y + 3, unread);
      c.pop();
      if (label) {
         String name = c.fit(e.info().name(), 44, PhoneTheme.CAPTION);
         c.textCentered(name, x + ICON / 2 + 0.3F, y + ICON + 6.3F, PhoneTheme.CAPTION, 0x90000000);
         c.textCentered(name, x + ICON / 2, y + ICON + 6, PhoneTheme.CAPTION, 0xFFFFFFFF);
      }
      float cx = x + ICON / 2, cy = y + ICON / 2;
      this.ui.onClick(x - 3, y - 3, ICON + 6, ICON + 6, () -> os.openApp(e.info().id(), null, cx, cy));
   }

   /** Time, date, and where you are in the world. Everything shown is read live from the game or the computer clock. */
   private void widget(PhoneCanvas c, PhoneOS os) {
      float x = 10, y = PhoneCanvas.SAFE_TOP + 6, w = c.width - 20, h = 72;
      c.round(x, y, w, h, 18, 0x2EFFFFFF, 0x14FFFFFF);
      c.stroke(x, y, w, h, 18, 0.6F, 0x26FFFFFF);
      LocalDateTime now = LocalDateTime.now();
      String time = os.config().use24h ? String.format("%02d:%02d", now.getHour(), now.getMinute())
         : String.format("%d:%02d", (now.getHour() + 11) % 12 + 1, now.getMinute());
      c.text(time, x + 12, y + 12, 17.0F, 0xFFFFFFFF);
      c.text(DATE.format(now), x + 13, y + 40, PhoneTheme.SMALL, 0xDDFFFFFF);
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         String where = switch (mc.level.dimension().identifier().getPath()) {
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            case "overworld" -> "Overworld";
            default -> mc.level.dimension().identifier().getPath();
         };
         long day = mc.level.getOverworldClockTime() / 24000L + 1;
         c.text(where + "  ·  Day " + day, x + 13, y + 52, PhoneTheme.CAPTION, 0xBBFFFFFF);
      }
      int unread = os.notifications().unreadTotal();
      if (unread > 0) {
         String s = unread + (unread == 1 ? " notification" : " notifications");
         float sw = c.width(s, PhoneTheme.CAPTION) + 12;
         c.round(x + w - sw - 10, y + 12, sw, 13, 6.5F, 0x33FFFFFF);
         c.text(s, x + w - sw - 4, y + 12 + 6.5F - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, 0xFFFFFFFF);
         this.ui.onClick(x + w - sw - 10, y + 12, sw, 13, os::openShade);
      }
   }

   public boolean wheel(double amount) {
      int before = this.page;
      this.page += amount < 0 ? 1 : -1;
      this.page = Math.max(0, this.page);
      return before != this.page;
   }

   public void firstPage() {
      this.page = 0;
   }

}
