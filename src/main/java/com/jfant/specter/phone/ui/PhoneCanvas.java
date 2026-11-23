package com.jfant.specter.phone.ui;

import com.jfant.specter.core.mixin.GuiGraphicsExtractorAccessor;
import com.jfant.specter.phone.api.AppIcon;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.gfx.Colors;
import com.jfant.specter.phone.gfx.PhoneTextures;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.QuadBatch;
import com.jfant.specter.render.Shapes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * Drawing in phone points. The phone screen is {@link #width} x {@link #height} points wherever and however big it
 * is on screen; this maps points to the GUI and keeps text and corners sharp at the real pixel size (text is
 * rasterised for the actual scale, shapes are snapped to real pixels).
 *
 * <p>Only translation and uniform scale are used, so everything stays pixel-crisp; transitions move and fade rather
 * than zoom text.
 */
public final class PhoneCanvas {
   public static final float WIDTH = 180.0F;
   public static final float HEIGHT = 392.0F;
   /** Status bar height. */
   public static final float SAFE_TOP = 24.0F;
   /** Navigation bar height. */
   public static final float SAFE_BOTTOM = 20.0F;
   /** Corner radius of the glass. */
   public static final float SCREEN_RADIUS = 19.8F;

   public final float width = WIDTH;
   public final float height = HEIGHT;

   private GuiGraphicsExtractor ctx;
   private PhoneTheme theme;
   /** GUI units per point. */
   private float scale;
   private int guiScale;
   /** Current translation in points (relative to the phone origin), for clip rectangles. */
   private float tx;
   private float ty;
   private final float[] txStack = new float[32];
   private final float[] tyStack = new float[32];
   private int depth;
   private final List<float[]> clips = new ArrayList<>();
   private float originX;
   private float originY;

   public void begin(GuiGraphicsExtractor ctx, PhoneTheme theme, float originX, float originY, float scale) {
      this.ctx = ctx;
      this.theme = theme;
      this.scale = scale;
      this.originX = originX;
      this.originY = originY;
      this.guiScale = Minecraft.getInstance().getWindow().getGuiScale();
      this.tx = 0;
      this.ty = 0;
      this.depth = 0;
      this.clips.clear();
      ctx.pose().pushMatrix();
      ctx.pose().translate(originX, originY);
      ctx.pose().scale(scale, scale);
      CardText.pushSmooth();
   }

   public void end() {
      while (this.depth > 0) this.pop();
      while (!this.clips.isEmpty()) this.unclip();
      CardText.popSmooth();
      this.ctx.pose().popMatrix();
   }

   public GuiGraphicsExtractor gui() {
      return this.ctx;
   }

   public PhoneTheme theme() {
      return this.theme;
   }

   /** Real screen pixels per point (for sizing painted textures 1:1). */
   public float pixelsPerPoint() {
      return this.scale * this.guiScale;
   }

   public int pixels(float points) {
      return Math.max(1, Math.round(points * this.pixelsPerPoint()));
   }

   // ------------------------------------------------------------------------------------------------ transform

   public void push(float dx, float dy) {
      if (this.depth < this.txStack.length) {
         this.txStack[this.depth] = this.tx;
         this.tyStack[this.depth] = this.ty;
      }
      this.depth++;
      this.tx += dx;
      this.ty += dy;
      this.ctx.pose().pushMatrix();
      this.ctx.pose().translate(dx, dy);
   }

   public void pop() {
      if (this.depth == 0) return;
      this.depth--;
      if (this.depth < this.txStack.length) {
         this.tx = this.txStack[this.depth];
         this.ty = this.tyStack[this.depth];
      }
      this.ctx.pose().popMatrix();
   }

   public float offsetX() {
      return this.tx;
   }

   public float offsetY() {
      return this.ty;
   }

   /** Multiplies the alpha of everything drawn until {@link #popAlpha()}. */
   public void alpha(float alpha) {
      QuadBatch.pushAlpha(alpha);
   }

   public void popAlpha() {
      QuadBatch.popAlpha();
   }

   /** Clips drawing to a rectangle (points, in the current translation). Nested clips intersect. */
   public void clip(float x, float y, float w, float h) {
      float ax = this.tx + x, ay = this.ty + y;
      float[] parent = this.clips.isEmpty() ? null : this.clips.get(this.clips.size() - 1);
      float x0 = ax, y0 = ay, x1 = ax + w, y1 = ay + h;
      if (parent != null) {
         x0 = Math.max(x0, parent[0]);
         y0 = Math.max(y0, parent[1]);
         x1 = Math.min(x1, parent[2]);
         y1 = Math.min(y1, parent[3]);
      }
      this.clips.add(new float[]{x0, y0, Math.max(x0, x1), Math.max(y0, y1)});
      int gx0 = (int)Math.floor(this.originX + x0 * this.scale);
      int gy0 = (int)Math.floor(this.originY + y0 * this.scale);
      int gx1 = (int)Math.ceil(this.originX + Math.max(x0, x1) * this.scale);
      int gy1 = (int)Math.ceil(this.originY + Math.max(y0, y1) * this.scale);
      ((GuiGraphicsExtractorAccessor)this.ctx).getScissorStack().push(new ScreenRectangle(gx0, gy0, Math.max(0, gx1 - gx0), Math.max(0, gy1 - gy0)));
   }

   public void unclip() {
      if (this.clips.isEmpty()) return;
      this.clips.remove(this.clips.size() - 1);
      this.ctx.disableScissor();
   }

   /** Is the point (current translation) inside the active clip? Widgets use it so hidden parts can't be clicked. */
   public boolean visible(float x, float y) {
      if (this.clips.isEmpty()) return true;
      float[] c = this.clips.get(this.clips.size() - 1);
      float ax = this.tx + x, ay = this.ty + y;
      return ax >= c[0] && ax < c[2] && ay >= c[1] && ay < c[3];
   }

   /** The active clip in absolute points, or null. */
   float[] clipRect() {
      return this.clips.isEmpty() ? null : this.clips.get(this.clips.size() - 1);
   }

   // ------------------------------------------------------------------------------------------------ shapes

   public void rect(float x, float y, float w, float h, int color) {
      if (w <= 0 || h <= 0 || color >>> 24 == 0) return;
      Shapes.fill(this.ctx, x, y, x + w, y + h, 0, 0, color, color);
   }

   public void gradient(float x, float y, float w, float h, int top, int bottom) {
      if (w <= 0 || h <= 0) return;
      Shapes.fill(this.ctx, x, y, x + w, y + h, 0, 0, top, bottom);
   }

   public void round(float x, float y, float w, float h, float radius, int color) {
      if (w <= 0 || h <= 0 || color >>> 24 == 0) return;
      Shapes.fill(this.ctx, x, y, x + w, y + h, radius, 15, color, color);
   }

   public void round(float x, float y, float w, float h, float radius, int top, int bottom) {
      if (w <= 0 || h <= 0) return;
      Shapes.fill(this.ctx, x, y, x + w, y + h, radius, 15, top, bottom);
   }

   /** Rounded on some corners only: mask of 1 top-left, 2 top-right, 4 bottom-left, 8 bottom-right. */
   public void roundCorners(float x, float y, float w, float h, float radius, int corners, int color) {
      if (w <= 0 || h <= 0) return;
      Shapes.fill(this.ctx, x, y, x + w, y + h, radius, corners, color, color);
   }

   /** A rounded outline about {@code thickness} points wide (at least one real pixel). */
   public void stroke(float x, float y, float w, float h, float radius, float thickness, int color) {
      if (w <= 0 || h <= 0) return;
      Shapes.ring(this.ctx, x, y, x + w, y + h, radius, 0, Math.max(1, Math.round(thickness * this.pixelsPerPoint())), color, color);
   }

   public void circle(float cx, float cy, float r, int color) {
      this.round(cx - r, cy - r, r * 2, r * 2, r, color);
   }

   public void ring(float cx, float cy, float r, float thickness, int color) {
      this.stroke(cx - r, cy - r, r * 2, r * 2, r, thickness, color);
   }

   /** Paints {@code color} outside the rounded corners of the rectangle (rounds off an image under it). */
   public void cornerMask(float x, float y, float w, float h, float radius, int color) {
      Shapes.mask(this.ctx, x, y, x + w, y + h, radius, color);
   }

   /** A soft shadow under a rounded card (a few stacked translucent rings). */
   public void shadow(float x, float y, float w, float h, float radius, float spread, int color) {
      int steps = 4;
      for (int i = steps; i >= 1; i--) {
         float g = spread * i / steps;
         this.round(x - g, y - g + spread * 0.35F, w + 2 * g, h + 2 * g, radius + g, Colors.alpha(color, 0.9F / (steps + 1)));
      }
   }

   // ------------------------------------------------------------------------------------------------ images

   public void image(Identifier texture, float x, float y, float w, float h) {
      this.image(texture, x, y, w, h, 0, 0, 1, 1, 0xFFFFFFFF);
   }

   public void image(Identifier texture, float x, float y, float w, float h, int tint) {
      this.image(texture, x, y, w, h, 0, 0, 1, 1, tint);
   }

   public void image(Identifier texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      if (texture == null || w <= 0 || h <= 0) return;
      QuadBatch batch = QuadBatch.textured(this.ctx, texture);
      if (batch == null) return;
      batch.textured(x, y, x + w, y + h, u0, v0, u1, v1, tint, tint);
      batch.submit(this.ctx);
   }

   /** Image cropped to fill the box (like CSS object-fit: cover). */
   public void imageCover(Identifier texture, int texW, int texH, float x, float y, float w, float h, int tint) {
      if (texW <= 0 || texH <= 0) {
         this.image(texture, x, y, w, h, tint);
         return;
      }
      float boxAspect = w / h, imgAspect = (float)texW / texH;
      float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
      if (imgAspect > boxAspect) {
         float keep = boxAspect / imgAspect;
         u0 = (1 - keep) / 2;
         u1 = 1 - u0;
      } else {
         float keep = imgAspect / boxAspect;
         v0 = (1 - keep) / 2;
         v1 = 1 - v0;
      }
      this.image(texture, x, y, w, h, u0, v0, u1, v1, tint);
   }

   public void icon(AppIcon icon, float x, float y, float size) {
      if (icon.texture != null) {
         this.image(icon.texture, x, y, size, size);
         return;
      }
      int px = this.pixels(size);
      Identifier tex = PhoneTextures.paint("icon/" + icon.key, px, px, icon.painter::paint);
      this.image(tex, x, y, size, size);
   }

   public void glyph(Glyph glyph, float x, float y, float size, int color) {
      int px = this.pixels(size);
      Identifier tex = PhoneTextures.paint("glyph/" + glyph.name(), px, px, glyph::paint);
      this.image(tex, x, y, size, size, color);
   }

   /** Glyph centred on a point. */
   public void glyphAt(Glyph glyph, float cx, float cy, float size, int color) {
      this.glyph(glyph, cx - size / 2, cy - size / 2, size, color);
   }

   // ------------------------------------------------------------------------------------------------ text

   private static float k(float size) {
      return size / 6.6F;
   }

   /** Text with its top at {@code y}; {@code size} is the cap height in points (see the sizes in PhoneTheme). */
   public void text(String text, float x, float y, float size, int color) {
      if (text == null || text.isEmpty() || color >>> 24 == 0) return;
      float k = k(size);
      Matrix3x2fStack pose = this.ctx.pose();
      pose.pushMatrix();
      pose.translate(x, y - 0.4F * k);
      pose.scale(k, k);
      CardText.draw(this.ctx, text, 0, 0, color, false);
      pose.popMatrix();
   }

   /** Text vertically centred on {@code cy}. */
   public void textMid(String text, float x, float cy, float size, int color) {
      this.text(text, x, cy - size / 2, size, color);
   }

   public void textCentered(String text, float cx, float y, float size, int color) {
      this.text(text, cx - this.width(text, size) / 2, y, size, color);
   }

   public void textRight(String text, float right, float y, float size, int color) {
      this.text(text, right - this.width(text, size), y, size, color);
   }

   public float width(String text, float size) {
      return text == null || text.isEmpty() ? 0 : CardText.width(text) * k(size);
   }

   /** Line height for wrapped text of this size. */
   public static float line(float size) {
      return size * 1.85F;
   }

   /** Cuts {@code text} with "..." so it fits {@code maxWidth} points. */
   public String fit(String text, float maxWidth, float size) {
      if (text == null) return "";
      return CardText.fit(text, (int)Math.floor(maxWidth / k(size)));
   }

   /** Word-wraps into at most {@code maxLines} lines (the last one cut with "..."). */
   public List<String> wrap(String text, float maxWidth, float size, int maxLines) {
      List<String> lines = new ArrayList<>();
      if (text == null || text.isEmpty()) return lines;
      for (String paragraph : text.split("\n", -1)) {
         String[] words = paragraph.split(" ");
         StringBuilder line = new StringBuilder();
         for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (this.width(candidate, size) <= maxWidth || line.isEmpty()) {
               line.setLength(0);
               line.append(candidate);
            } else {
               lines.add(line.toString());
               line.setLength(0);
               line.append(word);
            }
            if (lines.size() >= maxLines) break;
         }
         if (lines.size() < maxLines) lines.add(line.toString());
         if (lines.size() >= maxLines) break;
      }
      if (lines.size() > maxLines) lines = new ArrayList<>(lines.subList(0, maxLines));
      int last = lines.size() - 1;
      if (last >= 0 && this.width(lines.get(last), size) > maxWidth) lines.set(last, this.fit(lines.get(last), maxWidth, size));
      return lines;
   }

   /** Draws wrapped text and returns the height used. */
   public float paragraph(String text, float x, float y, float maxWidth, float size, int color, int maxLines) {
      List<String> lines = this.wrap(text, maxWidth, size, maxLines);
      float lh = line(size);
      for (int i = 0; i < lines.size(); i++) this.text(lines.get(i), x, y + i * lh, size, color);
      return lines.size() * lh;
   }

   /** Pose snapshot (for hit regions recorded during a translated draw). */
   Matrix3x2f poseCopy() {
      return new Matrix3x2f(this.ctx.pose());
   }
}
