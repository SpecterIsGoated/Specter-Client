package com.jfant.specter.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.config.Config;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

public final class CardText {
   private static final Identifier CARD_FONT = Identifier.fromNamespaceAndPath("specter", "font/card.json");
   private static final Identifier FONT = Identifier.withDefaultNamespace("font/include/default.json");
   private static final int BASELINE = 7;
   private static final int ATLAS_WIDTH = 512;
   private static final int MAX_ATLASES = 48;
   private static final Int2IntMap advances = new Int2IntOpenHashMap();
   private static final CardText.Face PIXELIFY = new CardText.Face(CARD_FONT, FONT);
   private static final CardText.Face GAME = new CardText.Face(FONT);
   private static final CardText.Face SMOOTH = new CardText.Face("/assets/specter/font/plus-jakarta-sans-semibold.ttf", FONT);
   private static int nextAtlas;
   private static final IntArrayList codePoints = new IntArrayList();
   private static final IntArrayList colors = new IntArrayList();
   private static Config config;
   private static final Map<String, String> fits = new HashMap<>();
   private static CardText.Face fitFace;

   private CardText() {
   }

   public static void draw(GuiGraphicsExtractor ctx, String text, float x, float y, int color, boolean shadow) {
      draw(ctx, Component.literal(text).getVisualOrderText(), x, y, color, shadow);
   }

   public static void draw(GuiGraphicsExtractor ctx, Component text, float x, float y, int color, boolean shadow) {
      draw(ctx, text.getVisualOrderText(), x, y, color, shadow);
   }

   public static void draw(GuiGraphicsExtractor ctx, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
      if (!GuiProfile.active()) {
         drawText(ctx, text, x, y, color, shadow);
      } else {
         long started = System.nanoTime();
         drawText(ctx, text, x, y, color, shadow);
         GuiProfile.text(System.nanoTime() - started, codePoints.size());
      }
   }

   private static void drawText(GuiGraphicsExtractor ctx, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
      Matrix3x2fStack m = ctx.pose();
      int gui = SpecterCore.mc.getWindow().getGuiScale();
      float scale = m.m00 * gui;
      if (!(scale <= 0.0F)) {
         float screenX = (m.m00 * x + m.m10 * y + m.m20) * gui;
         float screenY = (m.m01 * x + m.m11 * y + m.m21) * gui;
         codePoints.clear();
         colors.clear();
         text.accept((index, style, codePoint) -> {
            TextColor styled = style.getColor();
            codePoints.add(codePoint);
            colors.add(styled == null ? color : color & 0xFF000000 | styled.getValue());
            return true;
         });
         if (!codePoints.isEmpty()) {
            CardText.Face face = face();
            CardText.Atlas atlas = ready(face, scale);
            if (atlas != null && !face.glyphs().isEmpty()) {
               m.pushMatrix();
               m.identity();
               m.scale(1.0F / gui, 1.0F / gui);
               QuadBatch batch = QuadBatch.textured(ctx, atlas.id);
               if (batch != null) {
                  if (shadow) {
                     pass(
                        batch,
                        face,
                        atlas,
                        screenX,
                        screenY,
                        scale,
                        face.smooth() != null ? Math.max(1, Math.round(scale * 0.5F)) : Math.max(1, Math.round(scale)),
                        true
                     );
                  }

                  pass(batch, face, atlas, screenX, screenY, scale, 0, false);
                  batch.submit(ctx);
               }

               m.popMatrix();
            } else {
               ctx.text(SpecterCore.mc.font, text, Math.round(x), Math.round(y), color, shadow);
            }
         }
      }
   }

   private static void pass(QuadBatch batch, CardText.Face face, CardText.Atlas atlas, float screenX, float screenY, float scale, int offset, boolean shadow) {
      int baseY = Math.round(screenY) + offset;
      SmoothFont smooth = face.smooth();
      float pen = 0.0F;

      for (int i = 0; i < codePoints.size(); i++) {
         int codePoint = codePoints.getInt(i);
         if (smooth != null && i > 0) {
            pen += kern(smooth, codePoints.getInt(i - 1), codePoint);
         }

         CardText.Placed g = (CardText.Placed)atlas.placed.get(codePoint);
         if (g != null) {
            int color = colors.getInt(i);
            if (shadow) {
               color = smooth != null ? (color >>> 24) * 3 / 5 << 24 | (color & 16316664) >> 3 : color & 0xFF000000 | (color & 16579836) >> 2;
            }

            int gx = Math.round(screenX + pen * scale) + g.dx + offset;
            int gy = baseY + g.dy;
            batch.textured(
               gx,
               gy,
               gx + g.width,
               gy + g.height,
               g.u / 512.0F,
               (float)g.v / atlas.height,
               (g.u + g.width) / 512.0F,
               (float)(g.v + g.height) / atlas.height,
               color,
               color
            );
         }

         pen += advanceF(codePoint);
      }
   }

   private static float kern(SmoothFont smooth, int first, int second) {
      return smooth.has(first) && smooth.has(second) ? smooth.kern(first, second) : 0.0F;
   }

   private static CardText.Face face() {
      if (config == null) {
         config = Config.get();
      }

      if (Theme.clean() && SMOOTH.smooth() != null) {
         return SMOOTH;
      } else {
         return config != null && !config.customFont.get() ? GAME : PIXELIFY;
      }
   }

   private static float advanceF(int codePoint) {
      CardText.Face face = face();
      SmoothFont smooth = face.smooth();
      return smooth != null && smooth.has(codePoint) ? smooth.advance(codePoint) : advance(face, codePoint);
   }

   private static int advance(CardText.Face face, int codePoint) {
      CardText.Glyph g = (CardText.Glyph)face.glyphs().get(codePoint);
      if (g != null && g.advance >= 0) {
         return g.advance;
      } else {
         int known = advances.getOrDefault(codePoint, -1);
         if (known >= 0) {
            return known;
         } else {
            int width = SpecterCore.mc.font.width(Character.toString(codePoint));
            advances.put(codePoint, width);
            return width;
         }
      }
   }

   public static int width(String text) {
      float width = 0.0F;
      int i = 0;

      while (i < text.length()) {
         int codePoint = text.codePointAt(i);
         width += advanceF(codePoint);
         i += Character.charCount(codePoint);
      }

      return (int)Math.ceil(width);
   }

   public static String fit(String text, int maxWidth) {
      String key = maxWidth + "\u0000" + text;
      CardText.Face face = face();
      if (fitFace == face) {
         String known = fits.get(key);
         if (known != null) {
            return known;
         }
      } else {
         fits.clear();
         fitFace = face;
      }

      String result;
      if (width(text) <= maxWidth) {
         result = text;
      } else {
         float room = maxWidth - width("...");
         if (room <= 0.0F) {
            result = "";
         } else {
            float used = 0.0F;
            int end = 0;

            for (int i = 0; i < text.length(); end = i) {
               int codePoint = text.codePointAt(i);
               float w = advanceF(codePoint);
               if (used + w > room) {
                  break;
               }

               used += w;
               i += Character.charCount(codePoint);
            }

            result = text.substring(0, end).stripTrailing() + "...";
         }
      }

      if (fits.size() > 2048) {
         fits.clear();
      }

      fits.put(key, result);
      return result;
   }

   public static int width(Component text) {
      return width(text.getVisualOrderText());
   }

   public static int width(FormattedCharSequence text) {
      float[] width = new float[]{0.0F};
      text.accept((index, style, codePoint) -> {
         width[0] += advanceF(codePoint);
         return true;
      });
      return (int)Math.ceil(width[0]);
   }

   public static FormattedCharSequence clip(Component text, int maxWidth) {
      if (width(text) <= maxWidth) {
         return text.getVisualOrderText();
      } else {
         int room = Math.max(0, maxWidth - width("..."));
         IntArrayList kept = new IntArrayList();
         List<Style> styles = new ArrayList<>();
         float[] used = new float[]{0.0F};
         text.getVisualOrderText().accept((index, style, codePoint) -> {
            float w = advanceF(codePoint);
            if (used[0] + w > room) {
               return false;
            } else {
               used[0] += w;
               kept.add(codePoint);
               styles.add(style);
               return true;
            }
         });

         while (!kept.isEmpty() && kept.getInt(kept.size() - 1) == 32) {
            kept.removeInt(kept.size() - 1);
            styles.removeLast();
         }

         return visitor -> {
            for (int i = 0; i < kept.size(); i++) {
               if (!visitor.accept(i, styles.get(i), kept.getInt(i))) {
                  return false;
               }
            }

            for (int ix = 0; ix < 3; ix++) {
               if (!visitor.accept(kept.size() + ix, Style.EMPTY, 46)) {
                  return false;
               }
            }

            return true;
         };
      }
   }

   private static CardText.Atlas ready(CardText.Face face, float scale) {
      Int2ObjectMap<CardText.Glyph> glyphs = face.glyphs();
      TextureRetirement.sweep();
      if (!face.seeded) {
         face.seeded = true;

         for (int c = 32; c < 127; c++) {
            if (face.has(c)) {
               face.seen.add(c);
            }
         }

         for (int cx = 160; cx < 256; cx++) {
            if (face.has(cx)) {
               face.seen.add(cx);
            }
         }
      }

      for (int i = 0; i < codePoints.size(); i++) {
         int codePoint = codePoints.getInt(i);
         if (face.has(codePoint)) {
            face.seen.add(codePoint);
         }
      }

      int key = face.smooth() != null ? Math.round(scale * 20.0F) * 5 : Math.round(scale * 100.0F);
      CardText.Atlas atlas = face.atlases.get(key);
      if (atlas == null) {
         atlas = new CardText.Atlas(Identifier.fromNamespaceAndPath("specter-client", "card_text_" + nextAtlas++), key / 100.0F);
         face.atlases.put(key, atlas);
         if (face.atlases.size() > 48) {
            Iterator<CardText.Atlas> oldest = face.atlases.values().iterator();
            CardText.Atlas dropped = oldest.next();
            oldest.remove();
            if (dropped.texture != null) {
               TextureRetirement.retire(dropped.id);
            }
         }
      }

      if (atlas.builtWith != face.seen.size()) {
         build(face, atlas);
      }

      return atlas.texture != null ? atlas : null;
   }

   private static void build(CardText.Face face, CardText.Atlas atlas) {
      long started = System.nanoTime();
      float scale = atlas.scale;
      atlas.placed.clear();
      Int2ObjectMap<CardText.Glyph> glyphs = face.glyphs();
      int[] order = face.seen.toIntArray();
      int x = 0;
      int y = 0;
      int rowHeight = 0;
      int[][] spots = new int[order.length][];
      float[][] coverage = new float[order.length][];
      int[][] sizes = new int[order.length][];
      int[][] offsets = new int[order.length][];
      SmoothFont smooth = face.smooth();
      int baseline = Math.round(7.0F * scale);

      for (int i = 0; i < order.length; i++) {
         int w;
         int h;
         if (smooth != null && smooth.has(order[i])) {
            SmoothFont.Bitmap b = smooth.raster(order[i], scale, baseline);
            if (b == null) {
               continue;
            }

            w = b.width();
            h = b.height();
            coverage[i] = b.alpha();
            offsets[i] = new int[]{b.dx(), b.dy()};
         } else {
            CardText.Glyph g = (CardText.Glyph)glyphs.get(order[i]);
            if (g == null) {
               continue;
            }

            float px = g.texel * scale;
            w = Math.max(1, (int)Math.ceil(g.width * px));
            h = Math.max(1, (int)Math.ceil(g.height * px));
            coverage[i] = cover(g, px, w, h);
            offsets[i] = new int[]{0, Math.round(g.top * scale)};
         }

         if (x + w + 1 > 512) {
            x = 0;
            y += rowHeight + 1;
            rowHeight = 0;
         }

         spots[i] = new int[]{x, y};
         sizes[i] = new int[]{w, h};
         x += w + 1;
         rowHeight = Math.max(rowHeight, h);
      }

      int height = Math.max(1, y + rowHeight);
      NativeImage image = new NativeImage(512, height, true);

      for (int py = 0; py < height; py++) {
         for (int px = 0; px < 512; px++) {
            image.setPixel(px, py, 0);
         }
      }

      for (int i = 0; i < order.length; i++) {
         if (sizes[i] != null) {
            int wx = sizes[i][0];
            int hx = sizes[i][1];

            for (int py = 0; py < hx; py++) {
               for (int px = 0; px < wx; px++) {
                  int alpha = Math.round(Math.min(1.0F, coverage[i][py * wx + px]) * 255.0F);
                  if (alpha > 0) {
                     image.setPixel(spots[i][0] + px, spots[i][1] + py, alpha << 24 | 16777215);
                  }
               }
            }

            atlas.placed.put(order[i], new CardText.Placed(spots[i][0], spots[i][1], wx, hx, offsets[i][0], offsets[i][1]));
         }
      }

      if (atlas.texture != null) {
         TextureRetirement.retire(atlas.id);
         atlas.id = Identifier.fromNamespaceAndPath("specter-client", "card_text_" + nextAtlas++);
      }

      atlas.texture = new DynamicTexture(() -> "specter-card-text", image);
      SpecterCore.mc.getTextureManager().register(atlas.id, atlas.texture);
      atlas.height = height;
      atlas.builtWith = face.seen.size();
      CardText.BuildLog.note(atlas.scale, System.nanoTime() - started);
   }

   private static float[] cover(CardText.Glyph g, float px, int w, int h) {
      float[] out = new float[w * h];

      for (int ty = 0; ty < g.height; ty++) {
         for (int tx = 0; tx < g.width; tx++) {
            if (g.on[ty * g.width + tx]) {
               float x0 = tx * px;
               float x1 = x0 + px;
               float y0 = ty * px;
               float y1 = y0 + px;

               for (int sy = (int)Math.floor(y0); sy < Math.min(h, (int)Math.ceil(y1)); sy++) {
                  float oy = Math.min(y1, (float)(sy + 1)) - Math.max(y0, (float)sy);
                  if (!(oy <= 0.0F)) {
                     for (int sx = (int)Math.floor(x0); sx < Math.min(w, (int)Math.ceil(x1)); sx++) {
                        float ox = Math.min(x1, (float)(sx + 1)) - Math.max(x0, (float)sx);
                        if (ox > 0.0F) {
                           out[sy * w + sx] = out[sy * w + sx] + ox * oy;
                        }
                     }
                  }
               }
            }
         }
      }

      return out;
   }

   private static Int2ObjectMap<CardText.Glyph> load(Identifier[] fonts) {
      Int2ObjectMap<CardText.Glyph> out = new Int2ObjectOpenHashMap();

      for (Identifier font : fonts) {
         loadFont(font, !font.equals(FONT), out);
      }

      return out;
   }

   private static void loadFont(Identifier font, boolean ownAdvance, Int2ObjectMap<CardText.Glyph> out) {
      try (Reader reader = SpecterCore.mc.getResourceManager().openAsReader(font)) {
         JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

         for (JsonElement element : root.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if ("bitmap".equals(provider.get("type").getAsString())) {
               loadBitmap(provider, ownAdvance, out);
            }
         }
      } catch (Exception var10) {
         SpecterClientAddon.LOG.warn("Card text: could not read the font " + font, var10);
      }
   }

   private static void loadBitmap(JsonObject provider, boolean ownAdvance, Int2ObjectMap<CardText.Glyph> out) throws Exception {
      Identifier texture = Identifier.parse(provider.get("file").getAsString()).withPrefix("textures/");
      int lineHeight = provider.has("height") ? provider.get("height").getAsInt() : 8;
      int ascent = provider.get("ascent").getAsInt();
      List<int[]> rows = provider.getAsJsonArray("chars").asList().stream().map(rowx -> rowx.getAsString().codePoints().toArray()).toList();
      if (!rows.isEmpty() && rows.getFirst().length != 0) {
         try (
            InputStream in = SpecterCore.mc.getResourceManager().open(texture);
            NativeImage image = NativeImage.read(in);
         ) {
            int cellWidth = image.getWidth() / rows.getFirst().length;
            int cellHeight = image.getHeight() / rows.size();
            float texel = (float)lineHeight / cellHeight;
            float top = 7 - ascent;

            for (int r = 0; r < rows.size(); r++) {
               int[] row = rows.get(r);

               for (int c = 0; c < row.length; c++) {
                  int codePoint = row[c];
                  if (codePoint != 0 && codePoint != 32 && !out.containsKey(codePoint)) {
                     int width = 0;
                     boolean[] on = new boolean[cellWidth * cellHeight];

                     for (int ty = 0; ty < cellHeight; ty++) {
                        for (int tx = 0; tx < cellWidth; tx++) {
                           if (image.getPixel(c * cellWidth + tx, r * cellHeight + ty) >>> 24 != 0) {
                              on[ty * cellWidth + tx] = true;
                              width = Math.max(width, tx + 1);
                           }
                        }
                     }

                     if (width != 0) {
                        boolean[] trimmed = new boolean[width * cellHeight];

                        for (int ty = 0; ty < cellHeight; ty++) {
                           System.arraycopy(on, ty * cellWidth, trimmed, ty * width, width);
                        }

                        int advance = ownAdvance ? (int)(0.5F + width * texel) + 1 : -1;
                        out.put(codePoint, new CardText.Glyph(trimmed, width, cellHeight, texel, top, advance));
                     }
                  }
               }
            }
         }
      }
   }

   private static final class Atlas {
      Identifier id;
      final float scale;
      final Int2ObjectMap<CardText.Placed> placed = new Int2ObjectOpenHashMap();
      DynamicTexture texture;
      int height = 1;
      int builtWith = -1;

      Atlas(Identifier id, float scale) {
         this.id = id;
         this.scale = scale;
      }
   }

   private static final class BuildLog {
      private static int count;
      private static long nanos;
      private static long since;
      private static final Set<Integer> scales = new TreeSet<>();

      static void note(float scale, long took) {
         long now = System.nanoTime();
         if (since == 0L) {
            since = now;
         }

         count++;
         nanos += took;
         scales.add(Math.round(scale * 100.0F));
         if (now - since >= 5000000000L) {
            if (count > 10) {
               SpecterClientAddon.LOG
                  .warn(
                     "Specter text: {} letter atlases redrawn in the last {} s ({} ms in all), sizes x100: {}",
                     new Object[]{count, (now - since) / 1000000000L, nanos / 1000000L, scales}
                  );
            }

            count = 0;
            nanos = 0L;
            since = now;
            scales.clear();
         }
      }
   }

   private static final class Face {
      final Identifier[] fonts;
      Int2ObjectMap<CardText.Glyph> glyphs;
      final Map<Integer, CardText.Atlas> atlases = new LinkedHashMap<>(16, 0.75F, true);
      final IntSet seen = new IntOpenHashSet();
      final String smoothResource;
      SmoothFont smooth;
      boolean smoothTried;
      boolean seeded;

      Face(Identifier... fonts) {
         this(null, fonts);
      }

      Face(String smoothResource, Identifier... fonts) {
         this.smoothResource = smoothResource;
         this.fonts = fonts;
      }

      SmoothFont smooth() {
         if (this.smoothResource != null && !this.smoothTried) {
            this.smoothTried = true;
            this.smooth = SmoothFont.load(this.smoothResource);
         }

         return this.smooth;
      }

      boolean has(int codePoint) {
         SmoothFont s = this.smooth();
         return s != null && s.has(codePoint) || this.glyphs().containsKey(codePoint);
      }

      Int2ObjectMap<CardText.Glyph> glyphs() {
         if (this.glyphs == null) {
            this.glyphs = CardText.load(this.fonts);
         }

         return this.glyphs;
      }
   }

   private record Glyph(boolean[] on, int width, int height, float texel, float top, int advance) {
   }

   private record Placed(int u, int v, int width, int height, int dx, int dy) {
   }
}
