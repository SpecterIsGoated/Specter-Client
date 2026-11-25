package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.region.DonutGoliaths;
import com.jfant.specter.region.DonutRegions;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import com.jfant.specter.render.Facing;
import com.jfant.specter.render.PurpleCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;

/**
 * DonutSMP region map.
 *
 * <p>The map is the real goliath layout: a 36 x 36 grid of 12,500-block cells (x/z from -225,000 to 225,000). Each
 * goliath is a rectangle of one to eight cells, so goliaths are not all the same size or shape, and each of the seven
 * regions holds between 13 and 57 of them, numbered from 1 inside its region ({@link DonutGoliaths}). The old map used a
 * 9 x 9 grid of 50,000-block squares numbered 1-113, which matched neither the borders nor the numbers players use.
 *
 * <p>The player-passed region tags (media/baltop bases, other bases, no RTP) were recorded against those old squares, so
 * they are still looked up by world position ({@link DonutRegions#tag}) - a goliath takes the tag of the old square its
 * middle lies in, and the Tag row uses the old square you are standing in.
 */
public class WhereAreYou extends Module {
   private static final int CURRENT_NUMBER = 0xFF1A0A26;
   private static final int CURRENT_OUTLINE = 0xF2DCFF;
   private static final int UNTAGGED = 0x8C8496;
   private static final int REGION_BORDER = 0xB4FFFFFF;
   private static final Identifier PLAYER_MARKER = Identifier.withDefaultNamespace("textures/map/decorations/player.png");
   private static final Identifier PLAYER_OFF_MAP = Identifier.withDefaultNamespace("textures/map/decorations/player_off_map.png");
   private static final String TITLE_TEXT = "Where Are You?";
   private static final int GRID = DonutGoliaths.GRID;
   private static final int PAD = 7;
   private static final int GAP = 8;
   private static final int HEADER = 15;
   private static final int LINE = 11;
   private static final int LEGEND_LINE = 10;
   private static final int MIN_INFO_WIDTH = 80;
   private static final int COLUMN_GAP = 8;
   /** Region borders as runs {vertical ? 1 : 0, line, from, to} in grid cells, worked out once from the table. */
   private static final List<int[]> BORDERS = new ArrayList<>();
   /** Legacy tag of each goliath ({@link DonutRegions.Tag} ordinal, or -1), by the old square its middle is in. */
   private static final int[][] GOLIATH_TAGS = new int[DonutGoliaths.CELLS.length][];
   private static final String[][] NUMBERS = new String[DonutGoliaths.CELLS.length][];

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final Setting<Boolean> showMap = this.sgGeneral
      .add(new BoolSetting.Builder().name("map").description("Show the DonutSMP goliath map with you marked on it.").defaultValue(true).build());
   private final Setting<Integer> cellSize = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("map-detail")
            .description("Pixels per 12,500 blocks on the map. Bigger shows goliath numbers on more of the small goliaths.")
            .defaultValue(4)
            .range(3, 8)
            .sliderRange(3, 8)
            .visible(this.showMap::get)
            .build()
      );
   private final Setting<Boolean> regionNumbers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("region-numbers")
            .description("Write each goliath's number (inside its region) on the map, where it fits.")
            .defaultValue(true)
            .visible(this.showMap::get)
            .build()
      );
   private final Setting<Boolean> regionBorders = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("region-borders")
            .description("Draw a bright line where one region ends and the next begins.")
            .defaultValue(true)
            .visible(this.showMap::get)
            .build()
      );
   private final Setting<Boolean> legend = this.sgGeneral
      .add(new BoolSetting.Builder().name("legend").description("List what each map colour means.").defaultValue(true).visible(this.showMap::get).build());
   private final Setting<Boolean> tags = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("tags")
            .description(
               "Colour the map by what areas are known for - media/baltop bases, other bases, no RTP spots - instead of by region, and show the tag where you are. Tags are passed around by players, not detected."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> otherDimension = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("other-dimension")
            .description("Also show the matching coords in the other dimension: Nether coords in the Overworld, Overworld coords in the Nether.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> facing = this.sgGeneral
      .add(new BoolSetting.Builder().name("facing").description("Show which way you are looking.").defaultValue(true).build());
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.TopLeft, 4, 4);
   private final Setting<Double> scale = this.sgPosition
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .build()
      );
   private final List<WhereAreYou.Row> rows = new ArrayList<>();
   private String otherTitle = "";
   private int coordColumn;
   private int otherColumn;
   private int infoWidth;
   private int lastLayout = -1;
   private float cardPixels = 1.0F;

   public WhereAreYou() {
      super(SpecterClientAddon.HUD, "where-are-you", "Shows your coordinates and which DonutSMP region and goliath you are in, on the goliath map.");
   }

   @Override
   public void onActivate() {
      this.infoWidth = 0;
      this.lastLayout = -1;
   }

   private int mapPixels() {
      return GRID * this.cellSize.get() + 2;
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player == null || this.mc.level == null) return;
      if (this.mc.gui.hud.isHidden() || this.mc.debugEntries.isOverlayVisible()) return;
      GuiGraphicsExtractor ctx = event.graphics;
      Font tr = this.mc.font;
      boolean nether = this.mc.level.dimension() == Level.NETHER;
      boolean end = this.mc.level.dimension() == Level.END;
      double mapX = nether ? this.mc.player.getX() * 8.0 : this.mc.player.getX();
      double mapZ = nether ? this.mc.player.getZ() * 8.0 : this.mc.player.getZ();
      int found = end ? -1 : DonutGoliaths.number(mapX, mapZ);
      DonutGoliaths.Goliath here = found < 0 ? null : new DonutGoliaths.Goliath(found / 1000, found % 1000);
      DonutRegions.Tag hereTag = here == null ? null : legacyTag(mapX, mapZ);
      this.buildRows(nether, end, here, hereTag);
      boolean map = this.showMap.get();
      int mapSize = this.mapPixels();
      boolean drawLegend = map && this.legend.get();
      int layout = this.rows.size() * 4 + (drawLegend ? 2 : 0) + (this.tags.get() ? 1 : 0) + (this.otherTitle.isEmpty() ? 0 : 64) + mapSize * 256;
      if (layout != this.lastLayout) {
         this.infoWidth = 0;
         this.lastLayout = layout;
      }

      this.coordColumn = 0;
      this.otherColumn = 0;
      for (WhereAreYou.Row r : this.rows) {
         if (r.coord()) {
            this.coordColumn = Math.max(this.coordColumn, CardText.width(r.value()));
            this.otherColumn = Math.max(this.otherColumn, CardText.width(r.other()));
         }
      }

      int needed = CardText.width(TITLE_TEXT) + (this.otherTitle.isEmpty() ? 0 : COLUMN_GAP + CardText.width(this.otherTitle));
      for (WhereAreYou.Row r : this.rows) needed = Math.max(needed, this.rowWidth(r));
      if (drawLegend) needed = Math.max(needed, this.legendWidth());
      this.infoWidth = Math.max(this.infoWidth, Math.max(needed, MIN_INFO_WIDTH));

      int legendRows = drawLegend ? this.legendRows() : 0;
      int legendTop = HEADER + this.rows.size() * LINE + 4;
      int infoHeight = drawLegend ? legendTop + legendRows * LEGEND_LINE - 2 : HEADER + this.rows.size() * LINE - 3;
      int width = map ? mapSize + GAP + this.infoWidth : this.infoWidth;
      int height = map ? Math.max(mapSize, infoHeight) : infoHeight;
      int boxWidth = width + PAD * 2;
      int boxHeight = height + PAD * 2;
      float s = this.scale.get().floatValue();
      this.cardPixels = s * this.mc.getWindow().getGuiScale();
      this.spot.place(boxWidth * s, boxHeight * s);
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(Math.round(this.spot.x()), Math.round(this.spot.y()));
      matrices.scale(s, s);
      PurpleCard.card(ctx, 0, 0, boxWidth, boxHeight);
      matrices.translate(PAD, PAD);
      int mapTop = (height - mapSize) / 2;
      int infoX = map ? mapSize + GAP : 0;
      if (map) this.drawMap(ctx, mapTop, here);
      this.drawInfo(ctx, infoX, legendTop, drawLegend, here, hereTag);
      if (map && !end) this.drawMarker(ctx, mapTop, mapX, mapZ, here != null);
      matrices.popMatrix();
   }

   private void buildRows(boolean nether, boolean end, DonutGoliaths.Goliath here, DonutRegions.Tag hereTag) {
      this.rows.clear();
      int bx = this.mc.player.getBlockX();
      int bz = this.mc.player.getBlockZ();
      String otherX = "";
      String otherZ = "";
      this.otherTitle = "";
      if (this.otherDimension.get() && !end) {
         if (nether) {
            otherX = format((int)Math.floor(this.mc.player.getX() * 8.0));
            otherZ = format((int)Math.floor(this.mc.player.getZ() * 8.0));
            this.otherTitle = "Overworld";
         } else {
            otherX = format(Math.floorDiv(bx, 8));
            otherZ = format(Math.floorDiv(bz, 8));
            this.otherTitle = "Nether";
         }
      }

      this.rows.add(WhereAreYou.Row.coord("X", format(bx), otherX));
      this.rows.add(WhereAreYou.Row.coord("Y", format(this.mc.player.getBlockY()), ""));
      this.rows.add(WhereAreYou.Row.coord("Z", format(bz), otherZ));
      String regionLabel = nether ? "Above" : "Region";
      if (end) {
         this.rows.add(new WhereAreYou.Row("Region", "None in the End", PurpleCard.DIM, "", 0));
      } else if (here == null) {
         this.rows.add(new WhereAreYou.Row(regionLabel, "Off the map", PurpleCard.DIM, "", 0));
      } else {
         int rgb = DonutGoliaths.REGION_RGB[here.region()];
         this.rows.add(new WhereAreYou.Row(regionLabel, DonutGoliaths.REGIONS[here.region()], 0xFF000000 | lighten(rgb), "", 0));
         String size = (here.width() / 1000) + "k x " + (here.length() / 1000) + "k";
         this.rows.add(new WhereAreYou.Row("Goliath", "#" + (here.index() + 1), PurpleCard.VALUE, size, PurpleCard.DIM));
      }

      if (this.tags.get() && here != null) {
         if (hereTag == null) this.rows.add(new WhereAreYou.Row("Tag", "None", PurpleCard.DIM, "", 0));
         else this.rows.add(new WhereAreYou.Row("Tag", hereTag.shortTitle, 0xFF000000 | hereTag.textRgb, "", 0));
      }

      if (this.facing.get()) {
         float yaw = this.mc.player.getYRot();
         this.rows.add(new WhereAreYou.Row("Facing", Facing.name(yaw), PurpleCard.VALUE, Facing.axes(yaw), PurpleCard.DIM));
      }
   }

   private void drawMap(GuiGraphicsExtractor ctx, int top, DonutGoliaths.Goliath here) {
      int cell = this.cellSize.get();
      int size = this.mapPixels();
      PurpleCard.fillRounded(ctx, -1, top - 1, size + 1, top + size + 1, 4, 15, PurpleCard.INSET_EDGE, PurpleCard.INSET_EDGE);
      PurpleCard.fillRounded(ctx, 0, top, size, top + size, 3, 15, PurpleCard.INSET, PurpleCard.INSET);
      int gap = cell >= 4 ? 1 : 0;
      int radius = cell >= 6 ? 2 : 1;
      boolean byTag = this.tags.get();

      for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
         int[][] goliaths = DonutGoliaths.CELLS[region];
         for (int index = 0; index < goliaths.length; index++) {
            int[] c = goliaths[index];
            int rgb = byTag ? tagColour(GOLIATH_TAGS[region][index]) : DonutGoliaths.REGION_RGB[region];
            boolean current = here != null && here.region() == region && here.index() == index;
            int x1 = 1 + c[0] * cell;
            int y1 = top + 1 + c[1] * cell;
            int x2 = 1 + c[2] * cell - gap;
            int y2 = top + 1 + c[3] * cell - gap;
            PurpleCard.fillRounded(
               ctx, x1, y1, x2, y2, radius, 15, 0xFF000000 | (current ? rgb : shade(rgb, 0.6F)), 0xFF000000 | shade(rgb, current ? 0.85F : 0.46F)
            );
         }
      }

      if (this.regionBorders.get()) {
         for (int[] run : BORDERS) {
            int line = 1 + run[1] * cell - (gap > 0 ? 1 : 0);
            int from = 1 + run[2] * cell;
            int to = 1 + run[3] * cell - gap;
            if (run[0] == 1) ctx.fill(line, top + from, line + 1, top + to, REGION_BORDER);
            else ctx.fill(from, top + line, to, top + line + 1, REGION_BORDER);
         }
      }

      if (here != null) {
         int[] c = DonutGoliaths.CELLS[here.region()][here.index()];
         double phase = System.currentTimeMillis() % 1600L / 1600.0 * 2.0 * Math.PI;
         int alpha = (int)(150.0 + 105.0 * (0.5 + 0.5 * Math.sin(phase)));
         int color = alpha << 24 | CURRENT_OUTLINE;
         PurpleCard.strokeRounded(ctx, c[0] * cell, top + c[1] * cell, c[2] * cell + 1, top + c[3] * cell + 1, radius, color, color);
      }

      if (this.regionNumbers.get()) this.drawNumbers(ctx, top, cell, here);
   }

   private void drawNumbers(GuiGraphicsExtractor ctx, int top, int cell, DonutGoliaths.Goliath here) {
      int numberPixels = Math.max(1, Math.round(this.cardPixels * 0.5F));
      float size = numberPixels / this.cardPixels;
      float glyphHeight = 7.0F * size;
      Matrix3x2fStack matrices = ctx.pose();
      for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
         int[][] goliaths = DonutGoliaths.CELLS[region];
         for (int index = 0; index < goliaths.length; index++) {
            int[] c = goliaths[index];
            String number = NUMBERS[region][index];
            float w = (c[2] - c[0]) * cell - 1;
            float h = (c[3] - c[1]) * cell - 1;
            float textW = CardText.width(number) * size;
            if (textW + 1.0F > w || glyphHeight + 1.0F > h) continue;
            boolean current = here != null && here.region() == region && here.index() == index;
            float left = this.onPixel(1 + c[0] * cell + (w - textW) / 2.0F);
            float numberTop = this.onPixel(top + 1 + c[1] * cell + (h - glyphHeight) / 2.0F);
            matrices.pushMatrix();
            matrices.translate(left, numberTop);
            matrices.scale(size, size);
            CardText.draw(ctx, number, 0.0F, 0.0F, current ? CURRENT_NUMBER : 0xFFFFFFFF, false);
            matrices.popMatrix();
         }
      }
   }

   private void drawInfo(GuiGraphicsExtractor ctx, int left, int legendTop, boolean drawLegend, DonutGoliaths.Goliath here, DonutRegions.Tag hereTag) {
      CardText.draw(ctx, TITLE_TEXT, (float)left, 0.0F, PurpleCard.TITLE, true);
      if (!this.otherTitle.isEmpty()) {
         CardText.draw(ctx, this.otherTitle, (float)(left + this.infoWidth - CardText.width(this.otherTitle)), 0.0F, PurpleCard.DIM, true);
      }

      ctx.fill(left, 11, left + this.infoWidth, 12, PurpleCard.DIVIDER);
      int y = HEADER;
      for (WhereAreYou.Row r : this.rows) {
         CardText.draw(ctx, r.label, (float)left, (float)y, PurpleCard.LABEL, true);
         int right = left + this.infoWidth;
         if (r.coord() && this.otherColumn > 0) {
            if (!r.other().isEmpty()) CardText.draw(ctx, r.other(), (float)(right - CardText.width(r.other())), (float)y, PurpleCard.DIM, true);
            right -= this.otherColumn + COLUMN_GAP;
         }

         int valueX = right - r.valueWidth();
         CardText.draw(ctx, r.value, (float)valueX, (float)y, r.valueColor, true);
         if (!r.extra.isEmpty()) CardText.draw(ctx, r.extra, (float)(valueX + CardText.width(r.value) + 4), (float)y, r.extraColor, true);
         y += LINE;
      }

      if (!drawLegend) return;
      ctx.fill(left, legendTop - 4, left + this.infoWidth, legendTop - 3, PurpleCard.DIVIDER);
      if (this.tags.get()) {
         DonutRegions.Tag[] all = DonutRegions.Tag.values();
         for (int i = 0; i < all.length; i++) {
            DonutRegions.Tag t = all[i];
            legendEntry(ctx, left, legendTop + i * LEGEND_LINE, t.rgb, t.title, t == hereTag ? 0xFF000000 | t.textRgb : PurpleCard.LABEL);
         }
      } else {
         int half = this.infoWidth / 2 + 4;
         for (int i = 0; i < DonutGoliaths.REGIONS.length; i++) {
            int x = left + (i % 2 == 0 ? 0 : half);
            int ly = legendTop + i / 2 * LEGEND_LINE;
            int rgb = DonutGoliaths.REGION_RGB[i];
            boolean mine = here != null && here.region() == i;
            legendEntry(ctx, x, ly, rgb, DonutGoliaths.REGIONS[i], mine ? 0xFF000000 | lighten(rgb) : PurpleCard.LABEL);
         }
      }
   }

   private int legendRows() {
      return this.tags.get() ? DonutRegions.Tag.values().length : (DonutGoliaths.REGIONS.length + 1) / 2;
   }

   private static void legendEntry(GuiGraphicsExtractor ctx, int x, int y, int rgb, String title, int textColor) {
      PurpleCard.fillRounded(ctx, x, y, x + 7, y + 7, 2, 15, 0xFF000000 | rgb, 0xFF000000 | shade(rgb, 0.75F));
      CardText.draw(ctx, title, (float)(x + 10), (float)y, textColor, true);
   }

   private void drawMarker(GuiGraphicsExtractor ctx, int top, double mapX, double mapZ, boolean onMap) {
      int cell = this.cellSize.get();
      double fx = Math.clamp((mapX + DonutGoliaths.HALF) / DonutGoliaths.SQUARE, 0.0, GRID);
      double fz = Math.clamp((mapZ + DonutGoliaths.HALF) / DonutGoliaths.SQUARE, 0.0, GRID);
      ctx.nextStratum();
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(1.0F + (float)fx * cell, top + 1 + (float)fz * cell);
      if (onMap) {
         float turns = Math.round((this.mc.player.getYRot() + 180.0F) / 22.5F) * 22.5F;
         matrices.rotate((float)Math.toRadians(turns));
         matrices.translate(-4.5F, -3.5F);
         ctx.blit(RenderPipelines.GUI_TEXTURED, PLAYER_MARKER, 0, 0, 0.0F, 0.0F, 8, 8, 8, 8);
      } else {
         matrices.translate(-4.0F, -4.0F);
         ctx.blit(RenderPipelines.GUI_TEXTURED, PLAYER_OFF_MAP, 0, 0, 0.0F, 0.0F, 8, 8, 8, 8);
      }

      matrices.popMatrix();
   }

   private int legendWidth() {
      int widest = 0;
      if (this.tags.get()) {
         for (DonutRegions.Tag t : DonutRegions.Tag.values()) widest = Math.max(widest, 10 + CardText.width(t.title));
         return widest;
      }

      for (String name : DonutGoliaths.REGIONS) widest = Math.max(widest, CardText.width(name));
      return 2 * (10 + widest) + 8;
   }

   /** The player-passed tag of the old 50k square a world position lies in. */
   static DonutRegions.Tag legacyTag(double x, double z) {
      int column = DonutRegions.cell(x);
      int row = DonutRegions.cell(z);
      return DonutRegions.onMap(column, row) ? DonutRegions.tag(column, row) : null;
   }

   private static int tagColour(int tag) {
      return tag < 0 ? UNTAGGED : DonutRegions.Tag.values()[tag].rgb;
   }

   private float onPixel(float cardPixelsFromOrigin) {
      return Math.round(cardPixelsFromOrigin * this.cardPixels) / this.cardPixels;
   }

   private static int lighten(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      return (r + Math.round((255 - r) * 0.25F)) << 16 | (g + Math.round((255 - g) * 0.25F)) << 8 | (b + Math.round((255 - b) * 0.25F));
   }

   private static int shade(int rgb, float factor) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      return Math.round(r * factor) << 16 | Math.round(g * factor) << 8 | Math.round(b * factor);
   }

   /** 12,345 style (same as %,d in Locale.ROOT) without String.format, which was a tenth of this card's frame time. */
   private static String format(int value) {
      if (value == Integer.MIN_VALUE) return String.format(Locale.ROOT, "%,d", value);
      String digits = Integer.toString(Math.abs(value));
      StringBuilder out = new StringBuilder(digits.length() + 5);
      if (value < 0) out.append('-');
      int lead = digits.length() % 3;
      if (lead == 0) lead = 3;
      out.append(digits, 0, lead);
      for (int i = lead; i < digits.length(); i += 3) out.append(',').append(digits, i, i + 3);
      return out.toString();
   }

   private int rowWidth(WhereAreYou.Row r) {
      return !r.coord()
         ? CardText.width(r.label()) + 10 + r.valueWidth()
         : CardText.width(r.label()) + 10 + this.coordColumn + (this.otherColumn > 0 ? COLUMN_GAP + this.otherColumn : 0);
   }

   /** Region id of a grid cell, or -1 off the map / unassigned. */
   private static int regionOf(int u, int v) {
      int square = DonutGoliaths.square(u, v);
      return square < 0 ? -1 : square / 1000;
   }

   private static void borderRuns(int line, boolean vertical) {
      int start = -1;
      for (int along = 0; along <= GRID; along++) {
         boolean border = false;
         if (along < GRID) {
            int a = vertical ? regionOf(line - 1, along) : regionOf(along, line - 1);
            int b = vertical ? regionOf(line, along) : regionOf(along, line);
            border = a >= 0 && b >= 0 && a != b;
         }

         if (border && start < 0) {
            start = along;
         } else if (!border && start >= 0) {
            BORDERS.add(new int[]{vertical ? 1 : 0, line, start, along});
            start = -1;
         }
      }
   }

   static {
      for (int line = 1; line < GRID; line++) {
         borderRuns(line, true);
         borderRuns(line, false);
      }

      for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
         int count = DonutGoliaths.CELLS[region].length;
         GOLIATH_TAGS[region] = new int[count];
         NUMBERS[region] = new String[count];
         for (int index = 0; index < count; index++) {
            DonutRegions.Tag tag = legacyTag(DonutGoliaths.centreX(region, index), DonutGoliaths.centreZ(region, index));
            GOLIATH_TAGS[region][index] = tag == null ? -1 : tag.ordinal();
            NUMBERS[region][index] = Integer.toString(index + 1);
         }
      }
   }

   private record Row(String label, String value, int valueColor, String extra, int extraColor, String other, boolean coord) {
      Row(String label, String value, int valueColor, String extra, int extraColor) {
         this(label, value, valueColor, extra, extraColor, "", false);
      }

      static WhereAreYou.Row coord(String label, String value, String other) {
         return new WhereAreYou.Row(label, value, PurpleCard.VALUE, "", 0, other, true);
      }

      int valueWidth() {
         return CardText.width(this.value) + (this.extra.isEmpty() ? 0 : 4 + CardText.width(this.extra));
      }
   }
}
