package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;

public class WhereAreYou extends Module {
   private static final int CELL_NUMBER = -1;
   private static final int CURRENT_NUMBER = -15071962;
   private static final int CURRENT_OUTLINE = 15916543;
   private static final int UNTAGGED = 9208982;
   private static final Identifier PLAYER_MARKER = Identifier.withDefaultNamespace("textures/map/decorations/player.png");
   private static final Identifier PLAYER_OFF_MAP = Identifier.withDefaultNamespace("textures/map/decorations/player_off_map.png");
   private static final float MARKER_PIVOT_X = 4.5F;
   private static final float MARKER_PIVOT_Y = 3.5F;
   private static final float OFF_MAP_PIVOT = 4.0F;
   private static final String TITLE_TEXT = "Where Are You?";
   private static final int SIZE = 9;
   private static final String[] REGION_NUMBERS = new String[81];
   private static final int PAD = 7;
   private static final int CELL = 14;
   private static final int MAP = 127;
   private static final int GAP = 8;
   private static final int DIVIDER_Y = 11;
   private static final int HEADER = 15;
   private static final int LINE = 11;
   private static final int LEGEND_LINE = 10;
   private static final int MIN_INFO_WIDTH = 80;
   private static final int COLUMN_GAP = 8;
   private static final int LEGEND_GAP = 6;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final Setting<Boolean> showMap = this.sgGeneral
      .add(new BoolSetting.Builder().name("map").description("Show the DonutSMP region map with you marked on it.").defaultValue(true).build());
   private final Setting<Boolean> regionNumbers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("region-numbers")
            .description("Write each region's number on the map.")
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
               "Colour the map by what regions are known for - media/baltop bases, other bases, no RTP spots - instead of by server, and show your region's tag. Tags are passed around by players, not detected."
            )
            .defaultValue(true)
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
      super(SpecterClientAddon.HUD, "where-are-you", "Shows your coordinates and which DonutSMP region you are in, on the region map.");
   }

   @Override
   public void onActivate() {
      this.infoWidth = 0;
      this.lastLayout = -1;
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (!this.mc.options.hideGui && !this.mc.debugEntries.isOverlayVisible()) {
            GuiGraphics ctx = event.drawContext;
            Font tr = this.mc.font;
            boolean nether = this.mc.level.dimension() == Level.NETHER;
            boolean end = this.mc.level.dimension() == Level.END;
            double mapX = nether ? this.mc.player.getX() * 8.0 : this.mc.player.getX();
            double mapZ = nether ? this.mc.player.getZ() * 8.0 : this.mc.player.getZ();
            int column = DonutRegions.cell(mapX);
            int row = DonutRegions.cell(mapZ);
            DonutRegions.Group here = !end && DonutRegions.onMap(column, row) ? DonutRegions.group(column, row) : null;
            DonutRegions.Tag hereTag = here != null ? DonutRegions.tag(column, row) : null;
            this.buildRows(nether, end, column, row, here, hereTag);
            boolean map = this.showMap.get();
            boolean drawLegend = map && this.legend.get();
            int layout = this.rows.size() * 4 + (drawLegend ? 2 : 0) + (this.tags.get() ? 1 : 0) + (this.otherTitle.isEmpty() ? 0 : 64);
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

            int needed = CardText.width("Where Are You?") + (this.otherTitle.isEmpty() ? 0 : 8 + CardText.width(this.otherTitle));

            for (WhereAreYou.Row rx : this.rows) {
               needed = Math.max(needed, this.rowWidth(tr, rx));
            }

            if (drawLegend) {
               needed = Math.max(needed, this.legendWidth(tr));
            }

            this.infoWidth = Math.max(this.infoWidth, Math.max(needed, 80));
            int infoHeight = 15 + this.rows.size() * 11 - 3;
            int legendTop = 15 + this.rows.size() * 11 + 4;
            if (drawLegend && this.tags.get()) {
               legendTop = Math.max(legendTop, (map ? 127 : 0) - 8);
               infoHeight = legendTop + 8;
            } else if (drawLegend) {
               infoHeight = legendTop + 30 - 2;
            }

            int width = map ? 135 + this.infoWidth : this.infoWidth;
            int height = map ? Math.max(127, infoHeight) : infoHeight;
            int boxWidth = width + 14;
            int boxHeight = height + 14;
            float s = this.scale.get().floatValue();
            this.cardPixels = s * this.mc.getWindow().getGuiScale();
            this.spot.place(boxWidth * s, boxHeight * s);
            float x = this.spot.x();
            float y = this.spot.y();
            Matrix3x2fStack matrices = ctx.pose();
            matrices.pushMatrix();
            matrices.translate(Math.round(x), Math.round(y));
            matrices.scale(s, s);
            PurpleCard.card(ctx, 0, 0, boxWidth, boxHeight);
            matrices.translate(7.0F, 7.0F);
            int mapTop = (height - 127) / 2;
            int infoX = map ? 135 : 0;
            if (map) {
               this.drawMap(ctx, tr, mapTop, column, row, here != null);
            }

            this.drawInfo(ctx, tr, infoX, legendTop, drawLegend, here, hereTag);
            if (map && !end) {
               this.drawMarker(ctx, mapTop, mapX, mapZ, here != null);
            }

            matrices.popMatrix();
         }
      }
   }

   private void buildRows(boolean nether, boolean end, int column, int row, DonutRegions.Group here, DonutRegions.Tag hereTag) {
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
         this.rows.add(new WhereAreYou.Row("Region", "None in the End", -4149795, "", 0));
      } else if (here == null) {
         this.rows.add(new WhereAreYou.Row(regionLabel, "Off the map", -4149795, "", 0));
      } else {
         this.rows.add(new WhereAreYou.Row(regionLabel, "#" + DonutRegions.id(column, row), -1, here.title, 0xFF000000 | here.textRgb));
      }

      if (this.tags.get() && here != null) {
         if (hereTag == null) {
            this.rows.add(new WhereAreYou.Row("Tag", "None", -4149795, "", 0));
         } else {
            this.rows.add(new WhereAreYou.Row("Tag", hereTag.shortTitle, 0xFF000000 | hereTag.textRgb, "", 0));
         }
      }

      if (this.facing.get()) {
         float yaw = this.mc.player.getYRot();
         this.rows.add(new WhereAreYou.Row("Facing", Facing.name(yaw), -1, Facing.axes(yaw), -4149795));
      }
   }

   private void drawMap(GuiGraphics ctx, Font tr, int top, int column, int row, boolean onMap) {
      PurpleCard.fillRounded(ctx, -1, top - 1, 128, top + 127 + 1, 4, 15, -1333837902, -1333837902);
      PurpleCard.fillRounded(ctx, 0, top, 127, top + 127, 3, 15, -2145973978, -2145973978);

      for (int r = 0; r < 9; r++) {
         for (int c = 0; c < 9; c++) {
            int x = 1 + c * 14;
            int y = top + 1 + r * 14;
            int rgb = this.squareColour(c, r);
            boolean current = onMap && c == column && r == row;
            PurpleCard.fillRounded(
               ctx,
               x,
               y,
               x + 14 - 1,
               y + 14 - 1,
               2,
               outerCorners(c, r),
               0xFF000000 | (current ? rgb : shade(rgb, 0.62F)),
               0xFF000000 | shade(rgb, current ? 0.85F : 0.5F)
            );
         }
      }

      if (onMap) {
         double phase = System.currentTimeMillis() % 1600L / 1600.0 * 2.0 * 3.141592653589793;
         int alpha = (int)(150.0 + 105.0 * (0.5 + 0.5 * Math.sin(phase)));
         int color = alpha << 24 | 15916543;
         PurpleCard.strokeRounded(ctx, column * 14, top + row * 14, (column + 1) * 14 + 1, top + (row + 1) * 14 + 1, 2, color, color);
      }

      if (this.regionNumbers.get()) {
         int numberPixels = Math.max(1, Math.round(this.cardPixels * 0.5F));
         float size = numberPixels / this.cardPixels;
         if (!(CardText.width("113") * size > 13.0F)) {
            Matrix3x2fStack matrices = ctx.pose();

            for (int r = 0; r < 9; r++) {
               for (int c = 0; c < 9; c++) {
                  String number = REGION_NUMBERS[r * 9 + c];
                  boolean current = onMap && c == column && r == row;
                  float left = this.onPixel(1 + c * 14 + (13.0F - CardText.width(number) * size) / 2.0F);
                  float numberTop = this.onPixel(top + 1 + r * 14 + (13.0F - 7.0F * size) / 2.0F);
                  matrices.pushMatrix();
                  matrices.translate(left, numberTop);
                  matrices.scale(size, size);
                  CardText.draw(ctx, number, 0.0F, 0.0F, current ? -15071962 : -1, false);
                  matrices.popMatrix();
               }
            }
         }
      }
   }

   private void drawInfo(GuiGraphics ctx, Font tr, int left, int legendTop, boolean drawLegend, DonutRegions.Group here, DonutRegions.Tag hereTag) {
      CardText.draw(ctx, "Where Are You?", (float)left, 0.0F, -1065473, true);
      if (!this.otherTitle.isEmpty()) {
         CardText.draw(ctx, this.otherTitle, (float)(left + this.infoWidth - CardText.width(this.otherTitle)), 0.0F, -4149795, true);
      }

      ctx.fill(left, 11, left + this.infoWidth, 12, 1440926975);
      int y = 15;

      for (WhereAreYou.Row r : this.rows) {
         CardText.draw(ctx, r.label, (float)left, (float)y, -1911561, true);
         int right = left + this.infoWidth;
         if (r.coord() && this.otherColumn > 0) {
            if (!r.other().isEmpty()) {
               CardText.draw(ctx, r.other(), (float)(right - CardText.width(r.other())), (float)y, -4149795, true);
            }

            right -= this.otherColumn + 8;
         }

         int valueX = right - r.valueWidth(tr);
         CardText.draw(ctx, r.value, (float)valueX, (float)y, r.valueColor, true);
         if (!r.extra.isEmpty()) {
            CardText.draw(ctx, r.extra, (float)(valueX + CardText.width(r.value) + 4), (float)y, r.extraColor, true);
         }

         y += 11;
      }

      if (drawLegend) {
         ctx.fill(left, legendTop - 4, left + this.infoWidth, legendTop - 3, 1440926975);
         if (this.tags.get()) {
            int x = left;

            for (DonutRegions.Tag t : DonutRegions.Tag.values()) {
               legendEntry(ctx, x, legendTop, t.rgb, t.legendTitle, t == hereTag ? 0xFF000000 | t.textRgb : -1911561);
               x += 10 + CardText.width(t.legendTitle) + 6;
            }
         } else {
            DonutRegions.Group[] groups = DonutRegions.Group.values();

            for (int i = 0; i < groups.length; i++) {
               DonutRegions.Group g = groups[i];
               int x = left + (i % 2 == 0 ? 0 : this.infoWidth / 2 + 4);
               int ly = legendTop + i / 2 * 10;
               legendEntry(ctx, x, ly, g.rgb, g.title, g == here ? 0xFF000000 | g.textRgb : -1911561);
            }
         }
      }
   }

   private static void legendEntry(GuiGraphics ctx, int x, int y, int rgb, String title, int textColor) {
      PurpleCard.fillRounded(ctx, x, y, x + 7, y + 7, 2, 15, 0xFF000000 | rgb, 0xFF000000 | darken(rgb));
      CardText.draw(ctx, title, (float)(x + 10), (float)y, textColor, true);
   }

   private void drawMarker(GuiGraphics ctx, int top, double mapX, double mapZ, boolean onMap) {
      double fx = Math.clamp((mapX + 225000.0) / 50000.0, 0.0, 9.0);
      double fz = Math.clamp((mapZ + 225000.0) / 50000.0, 0.0, 9.0);
      ctx.nextStratum();
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(1.0F + (float)fx * 14.0F, top + 1 + (float)fz * 14.0F);
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

   private static int outerCorners(int column, int row) {
      int last = 8;
      int corners = 0;
      if (row == 0 && column == 0) {
         corners |= 1;
      }

      if (row == 0 && column == last) {
         corners |= 2;
      }

      if (row == last && column == 0) {
         corners |= 4;
      }

      if (row == last && column == last) {
         corners |= 8;
      }

      return corners;
   }

   private int legendWidth(Font tr) {
      int widest = 0;
      if (this.tags.get()) {
         int line = -6;

         for (DonutRegions.Tag t : DonutRegions.Tag.values()) {
            line += 10 + CardText.width(t.legendTitle) + 6;
         }

         return line;
      } else {
         for (DonutRegions.Group g : DonutRegions.Group.values()) {
            widest = Math.max(widest, CardText.width(g.title));
         }

         return 2 * (10 + widest) + 8;
      }
   }

   private int squareColour(int column, int row) {
      if (!this.tags.get()) {
         return DonutRegions.group(column, row).rgb;
      } else {
         DonutRegions.Tag tag = DonutRegions.tag(column, row);
         return tag != null ? tag.rgb : 9208982;
      }
   }

   private float onPixel(float cardPixelsFromOrigin) {
      return Math.round(cardPixelsFromOrigin * this.cardPixels) / this.cardPixels;
   }

   private static int darken(int rgb) {
      return shade(rgb, 0.75F);
   }

   private static int shade(int rgb, float factor) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      return Math.round(r * factor) << 16 | Math.round(g * factor) << 8 | Math.round(b * factor);
   }

   private static String format(int value) {
      return String.format(Locale.ROOT, "%,d", value);
   }

   private int rowWidth(Font tr, WhereAreYou.Row r) {
      return !r.coord()
         ? CardText.width(r.label()) + 10 + r.valueWidth(tr)
         : CardText.width(r.label()) + 10 + this.coordColumn + (this.otherColumn > 0 ? 8 + this.otherColumn : 0);
   }

   static {
      for (int row = 0; row < 9; row++) {
         for (int column = 0; column < 9; column++) {
            REGION_NUMBERS[row * 9 + column] = Integer.toString(DonutRegions.id(column, row));
         }
      }
   }

   private record Row(String label, String value, int valueColor, String extra, int extraColor, String other, boolean coord) {
      Row(String label, String value, int valueColor, String extra, int extraColor) {
         this(label, value, valueColor, extra, extraColor, "", false);
      }

      static WhereAreYou.Row coord(String label, String value, String other) {
         return new WhereAreYou.Row(label, value, -1, "", 0, other, true);
      }

      int valueWidth(Font tr) {
         return CardText.width(this.value) + (this.extra.isEmpty() ? 0 : 4 + CardText.width(this.extra));
      }
   }
}
