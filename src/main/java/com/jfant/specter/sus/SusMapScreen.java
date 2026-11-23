package com.jfant.specter.sus;

import com.jfant.specter.modules.SusFinds;
import com.jfant.specter.region.DonutGoliaths;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.PurpleCard;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Full-screen map of the Sus Finds list.
 *
 * <p>Left: a pannable, zoomable top-down map (drag to move, scroll to zoom around the cursor) with the DonutSMP goliath
 * borders underneath, every find drawn over the chunks it covers in its confidence colour, and you as an arrow. Right:
 * the finds, Confirmed first then nearest, and the details of the selected one. Click a find (on the map or in the list)
 * to select and centre it; Delete forgets it, C centres on you, F cycles which confidences are listed.
 */
public final class SusMapScreen extends Screen {
   private static final int PANEL = 210;
   private static final int ROW = 22;
   private static final int MARGIN = 8;
   private static final double MIN_SCALE = 0.25;   // blocks per pixel
   private static final double MAX_SCALE = 2000.0;

   private final SusFinds module;
   private double centreX;
   private double centreZ;
   private double scale = 8.0;
   private int selected = -1;
   private int filter; // 0 all, 1 likely+, 2 confirmed
   private int listScroll;
   private boolean dragging;
   private double dragX;
   private double dragY;
   private List<SusFind> rows = List.of();

   public SusMapScreen(SusFinds module) {
      super(Component.literal("Sus Finds"));
      this.module = module;
   }

   @Override
   protected void init() {
      if (this.minecraft.player != null && this.centreX == 0.0 && this.centreZ == 0.0) {
         this.centreX = this.minecraft.player.getX();
         this.centreZ = this.minecraft.player.getZ();
         this.fitAll();
      }
   }

   private boolean nether() {
      return this.minecraft.level != null && this.minecraft.level.dimension() == Level.NETHER;
   }

   private void fitAll() {
      double px = this.centreX, pz = this.centreZ;
      double far = 1000.0;
      for (SusFind f : this.module.store().snapshot()) {
         far = Math.max(far, Math.max(Math.abs(f.blockX() - px), Math.abs(f.blockZ() - pz)));
      }
      int mapW = Math.max(100, this.width - PANEL - MARGIN * 3);
      int mapH = Math.max(100, this.height - MARGIN * 2);
      this.scale = clampScale(far * 2.2 / Math.min(mapW, mapH));
   }

   private static double clampScale(double s) {
      return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float deltaTicks) {
      ctx.fill(0, 0, this.width, this.height, 0xB8100818);
   }

   private int mapLeft() {
      return MARGIN;
   }

   private int mapTop() {
      return MARGIN;
   }

   private int mapRight() {
      return this.width - PANEL - MARGIN * 2;
   }

   private int mapBottom() {
      return this.height - MARGIN;
   }

   private double screenX(double worldX) {
      return (this.mapLeft() + this.mapRight()) / 2.0 + (worldX - this.centreX) / this.scale;
   }

   private double screenZ(double worldZ) {
      return (this.mapTop() + this.mapBottom()) / 2.0 + (worldZ - this.centreZ) / this.scale;
   }

   private double worldX(double sx) {
      return this.centreX + (sx - (this.mapLeft() + this.mapRight()) / 2.0) * this.scale;
   }

   private double worldZ(double sy) {
      return this.centreZ + (sy - (this.mapTop() + this.mapBottom()) / 2.0) * this.scale;
   }

   private boolean listed(SusFind f) {
      return switch (this.filter) {
         case 1 -> f.confidence() != SusFind.Confidence.UNCERTAIN;
         case 2 -> f.confidence() == SusFind.Confidence.CONFIRMED;
         default -> true;
      };
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float deltaTicks) {
      this.extractBackground(ctx, mouseX, mouseY, deltaTicks);
      this.rows = this.module.sorted().stream().filter(this::listed).toList();
      int l = this.mapLeft(), t = this.mapTop(), r = this.mapRight(), b = this.mapBottom();
      PurpleCard.fillRounded(ctx, l - 1, t - 1, r + 1, b + 1, 4, 15, PurpleCard.INSET_EDGE, PurpleCard.INSET_EDGE);
      PurpleCard.fillRounded(ctx, l, t, r, b, 3, 15, 0xF0120A1C, 0xF0120A1C);
      ctx.enableScissor(l, t, r, b);
      this.drawGrid(ctx, l, t, r, b);
      if (!this.nether()) this.drawGoliaths(ctx);
      SusFind hover = this.drawFinds(ctx, mouseX, mouseY);
      this.drawPlayer(ctx);
      ctx.disableScissor();
      this.drawScaleBar(ctx, l, b);
      if (hover != null) this.tooltip(ctx, hover, mouseX, mouseY);
      this.drawPanel(ctx, mouseX, mouseY);
   }

   private void drawGrid(GuiGraphicsExtractor ctx, int l, int t, int r, int b) {
      double step = 16.0;
      while (step / this.scale < 28.0) step *= 2.0;
      double x0 = Math.floor(this.worldX(l) / step) * step;
      for (double wx = x0; this.screenX(wx) < r; wx += step) {
         int sx = (int)Math.round(this.screenX(wx));
         ctx.fill(sx, t, sx + 1, b, wx == 0.0 ? 0x40FFFFFF : 0x14FFFFFF);
      }
      double z0 = Math.floor(this.worldZ(t) / step) * step;
      for (double wz = z0; this.screenZ(wz) < b; wz += step) {
         int sy = (int)Math.round(this.screenZ(wz));
         ctx.fill(l, sy, r, sy + 1, wz == 0.0 ? 0x40FFFFFF : 0x14FFFFFF);
      }
   }

   private void drawGoliaths(GuiGraphicsExtractor ctx) {
      for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
         int rgb = DonutGoliaths.REGION_RGB[region];
         for (int[] c : DonutGoliaths.CELLS[region]) {
            int x1 = (int)Math.round(this.screenX(c[0] * (double)DonutGoliaths.SQUARE - DonutGoliaths.HALF));
            int y1 = (int)Math.round(this.screenZ(c[1] * (double)DonutGoliaths.SQUARE - DonutGoliaths.HALF));
            int x2 = (int)Math.round(this.screenX(c[2] * (double)DonutGoliaths.SQUARE - DonutGoliaths.HALF));
            int y2 = (int)Math.round(this.screenZ(c[3] * (double)DonutGoliaths.SQUARE - DonutGoliaths.HALF));
            if (x2 < this.mapLeft() || y2 < this.mapTop() || x1 > this.mapRight() || y1 > this.mapBottom()) continue;
            ctx.fill(x1, y1, x2, y2, 0x0C000000 | rgb);
            int edge = 0x40000000 | rgb;
            ctx.fill(x1, y1, x2, y1 + 1, edge);
            ctx.fill(x1, y2 - 1, x2, y2, edge);
            ctx.fill(x1, y1, x1 + 1, y2, edge);
            ctx.fill(x2 - 1, y1, x2, y2, edge);
         }
      }
   }

   /** Draws every find; returns the one under the mouse. */
   private SusFind drawFinds(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      SusFind hover = null;
      double chunkPx = 16.0 / this.scale;
      for (SusFind f : this.module.store().snapshot()) {
         if (!this.listed(f)) continue;
         int rgb = f.confidence().rgb;
         boolean sel = f.id == this.selected;
         if (chunkPx >= 2.0) {
            for (long key : f.chunks) {
               int x1 = (int)Math.floor(this.screenX(ChunkPos.getX(key) * 16.0));
               int y1 = (int)Math.floor(this.screenZ(ChunkPos.getZ(key) * 16.0));
               int x2 = Math.max(x1 + 1, (int)Math.floor(this.screenX(ChunkPos.getX(key) * 16.0 + 16.0)));
               int y2 = Math.max(y1 + 1, (int)Math.floor(this.screenZ(ChunkPos.getZ(key) * 16.0 + 16.0)));
               ctx.fill(x1, y1, x2, y2, (sel ? 0xC0000000 : 0x80000000) | rgb);
            }
         }

         int cx = (int)Math.round(this.screenX(f.blockX()));
         int cy = (int)Math.round(this.screenZ(f.blockZ()));
         int dot = sel ? 4 : 3;
         ctx.fill(cx - dot - 1, cy - dot - 1, cx + dot + 1, cy + dot + 1, 0xFF000000);
         ctx.fill(cx - dot, cy - dot, cx + dot, cy + dot, 0xFF000000 | rgb);
         if (sel) {
            int ring = 0xFFFFFFFF;
            ctx.fill(cx - 7, cy - 7, cx + 7, cy - 6, ring);
            ctx.fill(cx - 7, cy + 6, cx + 7, cy + 7, ring);
            ctx.fill(cx - 7, cy - 7, cx - 6, cy + 7, ring);
            ctx.fill(cx + 6, cy - 7, cx + 7, cy + 7, ring);
         }
         String number = "#" + f.id;
         CardText.draw(ctx, number, cx + 6, cy - 4, 0xFFFFFFFF, true);
         if (Math.abs(mouseX - cx) <= 6 && Math.abs(mouseY - cy) <= 6) hover = f;
      }
      return hover;
   }

   private void drawPlayer(GuiGraphicsExtractor ctx) {
      if (this.minecraft.player == null) return;
      int px = (int)Math.round(this.screenX(this.minecraft.player.getX()));
      int py = (int)Math.round(this.screenZ(this.minecraft.player.getZ()));
      double yaw = Math.toRadians(this.minecraft.player.getYRot());
      double fx = -Math.sin(yaw), fz = Math.cos(yaw);
      for (int i = 0; i <= 8; i++) {
         int ax = (int)Math.round(px + fx * i);
         int az = (int)Math.round(py + fz * i);
         ctx.fill(ax - 1, az - 1, ax + 1, az + 1, 0xFFFFFFFF);
      }
      ctx.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000);
      ctx.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF);
   }

   private void drawScaleBar(GuiGraphicsExtractor ctx, int l, int b) {
      double blocks = 16.0;
      while (blocks / this.scale < 60.0) blocks *= 2.0;
      int px = (int)Math.round(blocks / this.scale);
      int x = l + 8, y = b - 12;
      ctx.fill(x, y, x + px, y + 2, 0xFFFFFFFF);
      CardText.draw(ctx, SusFinds.metres((int)blocks), x, y - 10, 0xFFDDD6F0, true);
   }

   private void tooltip(GuiGraphicsExtractor ctx, SusFind f, int mx, int my) {
      String a = "#" + f.id + "  " + f.confidence().title;
      String b2 = f.blockX() + ", " + f.blockZ() + "  " + f.size() + " chunks";
      int w = Math.max(CardText.width(a), CardText.width(b2)) + 10;
      int x = Math.min(mx + 10, this.mapRight() - w), y = my + 10;
      PurpleCard.bubble(ctx, x, y, x + w, y + 26);
      CardText.draw(ctx, a, x + 5, y + 4, 0xFF000000 | f.confidence().rgb, true);
      CardText.draw(ctx, b2, x + 5, y + 15, PurpleCard.DIM, true);
   }

   private int panelLeft() {
      return this.width - PANEL - MARGIN;
   }

   private int listTop() {
      return MARGIN + 30;
   }

   private int detailsTop() {
      return this.height - MARGIN - 112;
   }

   private void drawPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int l = this.panelLeft(), t = MARGIN, r = this.width - MARGIN, b = this.height - MARGIN;
      PurpleCard.card(ctx, l, t, r, b);
      CardText.draw(ctx, "Sus Finds", l + 8, t + 7, PurpleCard.TITLE, true);
      String count = this.rows.size() + (this.filter == 0 ? " finds" : this.filter == 1 ? " likely+" : " confirmed");
      CardText.draw(ctx, count, r - 8 - CardText.width(count), t + 7, PurpleCard.DIM, true);
      ctx.fill(l + 6, t + 19, r - 6, t + 20, PurpleCard.DIVIDER);

      int top = this.listTop(), bottom = this.detailsTop() - 6;
      int visible = Math.max(1, (bottom - top) / ROW);
      this.listScroll = Math.max(0, Math.min(this.listScroll, Math.max(0, this.rows.size() - visible)));
      if (this.rows.isEmpty()) {
         CardText.draw(ctx, "Nothing yet.", l + 10, top + 4, PurpleCard.LABEL, true);
         CardText.draw(ctx, "Turn on Sus Chunks or V2", l + 10, top + 16, PurpleCard.DIM, true);
         CardText.draw(ctx, "and fly around.", l + 10, top + 27, PurpleCard.DIM, true);
      }
      for (int i = 0; i < visible && i + this.listScroll < this.rows.size(); i++) {
         SusFind f = this.rows.get(i + this.listScroll);
         int y = top + i * ROW;
         boolean sel = f.id == this.selected;
         boolean over = mouseX >= l + 4 && mouseX < r - 4 && mouseY >= y && mouseY < y + ROW - 2;
         if (sel || over) PurpleCard.fillRounded(ctx, l + 4, y, r - 4, y + ROW - 2, 3, 15, sel ? 0x50FFFFFF : 0x24FFFFFF, sel ? 0x40FFFFFF : 0x18FFFFFF);
         ctx.fill(l + 8, y + 3, l + 11, y + ROW - 5, 0xFF000000 | f.confidence().rgb);
         CardText.draw(ctx, "#" + f.id + "  " + f.confidence().title, l + 15, y + 2, 0xFF000000 | f.confidence().rgb, true);
         String sub = f.blockX() + ", " + f.blockZ() + "  " + this.distance(f);
         CardText.draw(ctx, CardText.fit(sub, PANEL - 30), l + 15, y + 11, PurpleCard.DIM, true);
      }

      int dt = this.detailsTop();
      ctx.fill(l + 6, dt - 3, r - 6, dt - 2, PurpleCard.DIVIDER);
      SusFind f = this.selected < 0 ? null : this.module.store().byId(this.selected);
      if (f == null) {
         CardText.draw(ctx, "Click a find for details.", l + 8, dt + 4, PurpleCard.DIM, true);
         CardText.draw(ctx, "Drag: move   Scroll: zoom", l + 8, dt + 26, PurpleCard.DIM, true);
         CardText.draw(ctx, "C: centre on you   F: filter", l + 8, dt + 37, PurpleCard.DIM, true);
         CardText.draw(ctx, "Del: forget the selected find", l + 8, dt + 48, PurpleCard.DIM, true);
         return;
      }

      int y = dt + 2;
      CardText.draw(ctx, "#" + f.id + "  " + f.confidence().title, l + 8, y, 0xFF000000 | f.confidence().rgb, true);
      y += 12;
      y = this.detail(ctx, l, r, y, "Why", this.why(f));
      y = this.detail(ctx, l, r, y, "Centre", f.blockX() + ", " + f.blockZ());
      y = this.detail(ctx, l, r, y, "Area", f.size() + " chunks, " + ((f.maxX - f.minX + 1) * 16) + " x " + ((f.maxZ - f.minZ + 1) * 16));
      y = this.detail(ctx, l, r, y, "From", f.sourceText());
      y = this.detail(ctx, l, r, y, "Seen", ago(f.firstSeen) + " first, " + ago(f.lastSeen) + " last");
      y = this.detail(ctx, l, r, y, "Joins", f.joins + (f.joins == 1 ? " join" : " joins"));
      DonutGoliaths.Goliath g = this.nether() ? null : DonutGoliaths.at(f.blockX(), f.blockZ());
      this.detail(ctx, l, r, y, "Goliath", g == null ? "-" : g.label());
   }

   private int detail(GuiGraphicsExtractor ctx, int l, int r, int y, String label, String value) {
      CardText.draw(ctx, label, l + 8, y, PurpleCard.LABEL, true);
      String fitted = CardText.fit(value, r - l - 62);
      CardText.draw(ctx, fitted, r - 8 - CardText.width(fitted), y, PurpleCard.VALUE, true);
      return y + 11;
   }

   private String why(SusFind f) {
      if (f.both()) return "both detectors agree";
      if (f.joins >= 2) return "flagged on " + f.joins + " joins";
      if (f.strength >= 2) return "strongest V2 evidence";
      if (f.size() >= 3) return f.size() + "-chunk area";
      return "one small flag so far";
   }

   private String distance(SusFind f) {
      if (this.minecraft.player == null) return "";
      double dx = f.blockX() - this.minecraft.player.getX(), dz = f.blockZ() - this.minecraft.player.getZ();
      return SusFinds.metres((int)Math.sqrt(dx * dx + dz * dz));
   }

   private static String ago(long when) {
      long s = Math.max(0L, (System.currentTimeMillis() - when) / 1000L);
      if (s < 90) return s + "s ago";
      if (s < 5400) return (s / 60) + "m ago";
      if (s < 172800) return (s / 3600) + "h ago";
      return String.format(Locale.ROOT, "%dd ago", s / 86400);
   }

   private void select(SusFind f, boolean centre) {
      this.selected = f.id;
      if (centre) {
         this.centreX = f.blockX();
         this.centreZ = f.blockZ();
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x(), my = click.y();
      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && mx >= this.panelLeft()) {
         int top = this.listTop();
         int index = (int)((my - top) / ROW) + this.listScroll;
         if (my >= top && my < this.detailsTop() - 6 && index >= 0 && index < this.rows.size()) this.select(this.rows.get(index), true);
         return true;
      }

      if (mx >= this.mapLeft() && mx < this.mapRight() && my >= this.mapTop() && my < this.mapBottom()) {
         for (SusFind f : this.rows) {
            if (Math.abs(mx - this.screenX(f.blockX())) <= 6 && Math.abs(my - this.screenZ(f.blockZ())) <= 6) {
               this.select(f, false);
               return true;
            }
         }
         this.dragging = true;
         this.dragX = mx;
         this.dragY = my;
         return true;
      }
      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.dragging) {
         this.centreX -= (event.x() - this.dragX) * this.scale;
         this.centreZ -= (event.y() - this.dragY) * this.scale;
         this.dragX = event.x();
         this.dragY = event.y();
         return true;
      }
      return super.mouseDragged(event, dx, dy);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      this.dragging = false;
      return super.mouseReleased(click);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      if (mouseX >= this.panelLeft()) {
         this.listScroll -= (int)Math.signum(vertical);
         return true;
      }
      double wx = this.worldX(mouseX), wz = this.worldZ(mouseY);
      this.scale = clampScale(this.scale * (vertical > 0 ? 0.8 : 1.25));
      // keep the point under the cursor where it is
      this.centreX += wx - this.worldX(mouseX);
      this.centreZ += wz - this.worldZ(mouseY);
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      int key = input.key();
      if (key == InputConstants.KEY_C && this.minecraft.player != null) {
         this.centreX = this.minecraft.player.getX();
         this.centreZ = this.minecraft.player.getZ();
         return true;
      }
      if (key == InputConstants.KEY_F) {
         this.filter = (this.filter + 1) % 3;
         return true;
      }
      if ((key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) && this.selected >= 0) {
         this.module.store().remove(this.selected);
         this.module.save();
         this.selected = -1;
         return true;
      }
      return super.keyPressed(input);
   }

   @Override
   public void removed() {
      this.module.save();
   }
}
