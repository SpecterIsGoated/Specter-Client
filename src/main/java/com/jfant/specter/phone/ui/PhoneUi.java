package com.jfant.specter.phone.ui;

import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.gfx.Colors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;

/**
 * The phone's widget kit, immediate mode: widgets are drawn every frame and register what clicking them does, so an
 * app is just a render method. Clicks fire on release (like a touch screen), and pressing inside a scrolling list
 * then dragging scrolls the list instead of clicking. Each app has its own PhoneUi, so scroll positions, text
 * fields and animations survive between frames.
 *
 * <p>All the visual decisions (sizes, radii, colours) live here and in {@link PhoneTheme}, which is what gives every
 * app the same design language.
 */
public final class PhoneUi {
   private static final float DRAG_SLOP = 4.0F;

   private PhoneCanvas c;
   private PhoneTheme t;
   private float mx = -1000;
   private float my = -1000;
   private float dt;
   private List<Region> building = new ArrayList<>();
   private List<Region> live = new ArrayList<>();
   private final Map<Object, ScrollView> scrolls = new HashMap<>();
   private final Map<Object, TextInput> fields = new HashMap<>();
   private final Map<Object, float[]> anims = new HashMap<>();
   private TextInput focused;
   private Region pressed;
   private ScrollView dragScroll;
   private Region dragging;
   private float pressX;
   private float pressY;
   private boolean scrolledSincePress;
   /** Plays the tap sound; set by the phone. */
   public Runnable tapSound = () -> {
   };

   private record Region(float x0, float y0, float x1, float y1, Runnable click, Consumer<float[]> drag, ScrollView scroll, TextInput field) {
      boolean contains(float x, float y) {
         return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
      }
   }

   // ============================================================================================== frame

   public void begin(PhoneCanvas canvas, float mouseX, float mouseY, float dt) {
      this.c = canvas;
      this.t = canvas.theme();
      this.mx = mouseX;
      this.my = mouseY;
      this.dt = Math.clamp(dt, 0, 0.1F);
      this.building.clear();
   }

   public void end() {
      List<Region> swap = this.live;
      this.live = this.building;
      this.building = swap;
   }

   public PhoneCanvas canvas() {
      return this.c;
   }

   public PhoneTheme theme() {
      return this.t;
   }

   /** Seconds since the last frame. */
   public float dt() {
      return this.dt;
   }

   public float mouseX() {
      return this.mx;
   }

   public float mouseY() {
      return this.my;
   }

   // ============================================================================================== regions

   private Region add(float x, float y, float w, float h, Runnable click, Consumer<float[]> drag, ScrollView scroll, TextInput field) {
      float x0 = this.c.offsetX() + x, y0 = this.c.offsetY() + y, x1 = x0 + w, y1 = y0 + h;
      float[] clip = this.c.clipRect();
      if (clip != null) {
         x0 = Math.max(x0, clip[0]);
         y0 = Math.max(y0, clip[1]);
         x1 = Math.min(x1, clip[2]);
         y1 = Math.min(y1, clip[3]);
      }
      if (x1 <= x0 || y1 <= y0) return null;
      Region r = new Region(x0, y0, x1, y1, click, drag, scroll, field);
      this.building.add(r);
      return r;
   }

   /** Makes a rectangle clickable (fires on release inside it). */
   public void onClick(float x, float y, float w, float h, Runnable action) {
      this.add(x, y, w, h, action, null, null, null);
   }

   /** Makes a rectangle draggable: {@code drag} gets {x, y} in the same coordinates, on press and while dragging. */
   public void onDrag(float x, float y, float w, float h, Consumer<float[]> drag) {
      this.add(x, y, w, h, null, drag, null, null);
   }

   void registerScroll(ScrollView view) {
      this.building.add(new Region(view.x0, view.y0, view.x1, view.y1, null, null, view, null));
   }

   /** Is the mouse over this rectangle (and not clipped away)? */
   public boolean hovered(float x, float y, float w, float h) {
      float ax = this.c.offsetX() + x, ay = this.c.offsetY() + y;
      if (!(this.mx >= ax && this.mx < ax + w && this.my >= ay && this.my < ay + h)) return false;
      float[] clip = this.c.clipRect();
      return clip == null || this.mx >= clip[0] && this.mx < clip[2] && this.my >= clip[1] && this.my < clip[3];
   }

   /** Is this rectangle being held down right now? */
   public boolean held(float x, float y, float w, float h) {
      return this.pressed != null && !this.scrolledSincePress && this.hovered(x, y, w, h);
   }

   // ============================================================================================== input (phone points)

   public boolean press(float x, float y) {
      this.pressX = x;
      this.pressY = y;
      this.scrolledSincePress = false;
      this.pressed = null;
      this.dragScroll = null;
      this.dragging = null;
      Region hitField = null;
      for (int i = this.live.size() - 1; i >= 0; i--) {
         Region r = this.live.get(i);
         if (!r.contains(x, y)) continue;
         if (r.scroll != null) {
            if (this.dragScroll == null) this.dragScroll = r.scroll;
            continue;
         }
         if (this.pressed == null && this.dragging == null && hitField == null) {
            if (r.field != null) hitField = r;
            else if (r.drag != null) this.dragging = r;
            else if (r.click != null) this.pressed = r;
         }
      }
      if (hitField != null) {
         this.focus(hitField.field);
         return true;
      }
      if (this.focused != null) this.focus(null);
      if (this.dragging != null) {
         this.dragging.drag.accept(new float[]{x, y});
         this.dragScroll = null;
         return true;
      }
      if (this.dragScroll != null) this.dragScroll.dragStart(y);
      return this.pressed != null || this.dragScroll != null;
   }

   public boolean move(float x, float y) {
      if (this.dragging != null) {
         this.dragging.drag.accept(new float[]{x, y});
         return true;
      }
      if (this.dragScroll != null) {
         if (!this.scrolledSincePress && Math.abs(y - this.pressY) > DRAG_SLOP) this.scrolledSincePress = true;
         if (this.scrolledSincePress) this.dragScroll.drag(y);
         return true;
      }
      return this.pressed != null;
   }

   public boolean release(float x, float y) {
      boolean used = this.pressed != null || this.dragScroll != null || this.dragging != null;
      if (this.dragScroll != null) this.dragScroll.dragEnd();
      if (this.pressed != null && !this.scrolledSincePress && this.pressed.contains(x, y)) {
         Runnable click = this.pressed.click;
         this.pressed = null;
         this.tapSound.run();
         click.run();
      }
      this.pressed = null;
      this.dragScroll = null;
      this.dragging = null;
      return used;
   }

   public boolean wheel(float x, float y, double amount) {
      for (int i = this.live.size() - 1; i >= 0; i--) {
         Region r = this.live.get(i);
         if (r.scroll != null && r.contains(x, y)) {
            r.scroll.wheel(amount);
            return true;
         }
      }
      return false;
   }

   public boolean key(int key) {
      return this.focused != null && this.focused.key(key);
   }

   public boolean chr(int codePoint) {
      return this.focused != null && this.focused.chr(codePoint);
   }

   public boolean typing() {
      return this.focused != null;
   }

   public void focus(TextInput field) {
      this.focused = field;
      if (field != null) field.focusedAt = System.currentTimeMillis();
   }

   /** Drops focus and any press in progress (app hidden, phone closing). */
   public void reset() {
      this.focused = null;
      this.pressed = null;
      if (this.dragScroll != null) this.dragScroll.dragEnd();
      this.dragScroll = null;
      this.dragging = null;
      this.live.clear();
   }

   // ============================================================================================== state

   public ScrollView scroll(Object key) {
      return this.scrolls.computeIfAbsent(key, k -> new ScrollView());
   }

   public TextInput field(Object key) {
      return this.fields.computeIfAbsent(key, k -> new TextInput());
   }

   /** A value that eases towards {@code target} ({@code speed} = 1/seconds), kept per key. */
   public float ease(Object key, float target, float speed) {
      float[] v = this.anims.computeIfAbsent(key, k -> new float[]{target});
      v[0] += (target - v[0]) * (1 - (float)Math.exp(-this.dt * speed));
      if (Math.abs(target - v[0]) < 0.001F) v[0] = target;
      return v[0];
   }

   public void forget(Object key) {
      this.anims.remove(key);
      this.scrolls.remove(key);
      this.fields.remove(key);
   }

   // ============================================================================================== widgets

   public enum Style {
      PRIMARY,
      SECONDARY,
      GHOST,
      DANGER
   }

   public void button(float x, float y, float w, float h, String label, Style style, Runnable action) {
      this.button(x, y, w, h, label, null, style, action);
   }

   public void button(float x, float y, float w, float h, String label, Glyph glyph, Style style, Runnable action) {
      boolean enabled = action != null;
      boolean hover = enabled && this.hovered(x, y, w, h);
      boolean down = enabled && this.held(x, y, w, h);
      int bg, fg;
      switch (style) {
         case PRIMARY -> {
            bg = this.t.accent();
            fg = this.t.onAccent();
         }
         case DANGER -> {
            bg = this.t.danger();
            fg = 0xFFFFFFFF;
         }
         case SECONDARY -> {
            bg = this.t.surfaceHigh();
            fg = this.t.text();
         }
         default -> {
            bg = hover ? this.t.accentSoft() : 0;
            fg = this.t.accent();
         }
      }
      if (!enabled) {
         bg = Colors.alpha(bg, 0.45F);
         fg = Colors.alpha(fg, 0.5F);
      } else if (down) {
         bg = Colors.shade(bg, 0.86F);
      } else if (hover && style != Style.GHOST) {
         bg = Colors.shade(bg, 1.1F);
      }
      float r = Math.min(h / 2, PhoneTheme.RADIUS_M);
      this.c.round(x, y, w, h, r, bg);
      float size = h >= 26 ? PhoneTheme.BODY : PhoneTheme.SMALL;
      float tw = this.c.width(label, size) + (glyph != null ? size * 2.4F : 0);
      float lx = x + (w - tw) / 2;
      if (glyph != null) {
         float gs = size * 2.0F;
         this.c.glyph(glyph, lx, y + (h - gs) / 2, gs, fg);
         lx += size * 2.4F;
      }
      this.c.textMid(label, lx, y + h / 2, size, fg);
      if (enabled) this.onClick(x, y, w, h, action);
   }

   /** A round icon button (toolbar). */
   public void iconButton(float cx, float cy, float size, Glyph glyph, int color, Runnable action) {
      float r = size / 2 + 4;
      if (action != null && this.hovered(cx - r, cy - r, r * 2, r * 2)) this.c.circle(cx, cy, r, this.held(cx - r, cy - r, r * 2, r * 2) ? Colors.alpha(color, 0.22F) : Colors.alpha(color, 0.12F));
      this.c.glyphAt(glyph, cx, cy, size, action == null ? Colors.alpha(color, 0.4F) : color);
      if (action != null) this.onClick(cx - r, cy - r, r * 2, r * 2, action);
   }

   /** An animated on/off switch, 26 x 15 points. {@code key} identifies it between frames (a setting name, say). */
   public void toggle(Object key, float x, float y, boolean on, Consumer<Boolean> changed) {
      float k = this.ease(new ToggleKey(key), on ? 1 : 0, 18);
      int track = Colors.lerp(this.t.dark() ? 0xFF3A3448 : 0xFFCFC9DB, this.t.accent(), k);
      this.c.round(x, y, 26, 15, 7.5F, track);
      float kx = x + 1.5F + k * 11;
      this.c.circle(kx + 6, y + 7.5F, 6.0F, 0x30000000);
      this.c.circle(kx + 6, y + 7.5F - 0.3F, 5.6F, 0xFFFFFFFF);
      this.onClick(x - 4, y - 4, 34, 23, () -> changed.accept(!on));
   }

   private record ToggleKey(Object owner) {
   }

   /** A slider: value in [min, max], dragged anywhere on the track. */
   public void slider(float x, float y, float w, double value, double min, double max, DoubleConsumer changed) {
      float k = (float)Math.clamp((value - min) / (max - min), 0, 1);
      float cy = y + 6;
      this.c.round(x, cy - 2, w, 4, 2, this.t.dark() ? 0xFF3A3448 : 0xFFD9D4E4);
      this.c.round(x, cy - 2, Math.max(4, w * k), 4, 2, this.t.accent());
      float kx = x + w * k;
      this.c.circle(kx, cy, 6.5F, 0x30000000);
      this.c.circle(kx, cy - 0.3F, 6.0F, 0xFFFFFFFF);
      float ox = this.c.offsetX();
      this.onDrag(x - 6, y - 4, w + 12, 20, p -> changed.accept(min + (max - min) * Math.clamp((p[0] - ox - x) / w, 0, 1)));
   }

   /** Tabs in a pill. */
   public void segmented(float x, float y, float w, float h, String[] options, int selected, IntConsumer changed) {
      this.c.round(x, y, w, h, h / 2, this.t.surfaceHigh());
      float seg = w / options.length;
      float pos = this.ease(new SegKey(x, y, options.length), selected, 16);
      this.c.round(x + 2 + pos * seg, y + 2, seg - 4, h - 4, (h - 4) / 2, this.t.dark() ? 0xFF3B3150 : 0xFFFFFFFF);
      for (int i = 0; i < options.length; i++) {
         int idx = i;
         boolean sel = i == selected;
         this.c.textCentered(this.c.fit(options[i], seg - 6, PhoneTheme.SMALL), x + seg * i + seg / 2, y + h / 2 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL,
            sel ? this.t.text() : this.t.textDim());
         if (!sel) this.onClick(x + seg * i, y, seg, h, () -> changed.accept(idx));
      }
   }

   private record SegKey(float x, float y, int n) {
   }

   /**
    * The app bar: big title, optional back chevron, optional action on the right. Draws its own background and
    * returns where content starts.
    */
   public float header(String title, Runnable back, Glyph action, Runnable onAction) {
      float top = PhoneCanvas.SAFE_TOP;
      float h = 30;
      this.c.rect(0, 0, this.c.width, top + h, this.t.background());
      float x = PhoneTheme.PAD;
      if (back != null) {
         this.iconButton(x + 5, top + h / 2, 11, Glyph.BACK, this.t.accent(), back);
         x += 16;
      }
      this.c.textMid(this.c.fit(title, this.c.width - x - 34, PhoneTheme.TITLE), x, top + h / 2, PhoneTheme.TITLE, this.t.text());
      if (action != null) this.iconButton(this.c.width - PhoneTheme.PAD - 6, top + h / 2, 12, action, this.t.accent(), onAction);
      return top + h + 2;
   }

   public void section(String title, float x, float y) {
      this.c.text(title.toUpperCase(java.util.Locale.ROOT), x, y, PhoneTheme.CAPTION, this.t.textFaint());
   }

   /** A list row with title, optional subtitle and a chevron when it does something. Returns its height. */
   public float row(float x, float y, float w, String title, String subtitle, Runnable action) {
      float h = subtitle == null ? PhoneTheme.ROW - 6 : PhoneTheme.ROW;
      if (action != null && this.hovered(x, y, w, h)) this.c.rect(x, y, w, h, this.held(x, y, w, h) ? this.t.accentSoft() : Colors.alpha(this.t.accentSoft(), 0.5F));
      float tx = x + 10;
      if (subtitle == null) {
         this.c.textMid(this.c.fit(title, w - 34, PhoneTheme.BODY), tx, y + h / 2, PhoneTheme.BODY, this.t.text());
      } else {
         this.c.text(this.c.fit(title, w - 34, PhoneTheme.BODY), tx, y + 8, PhoneTheme.BODY, this.t.text());
         this.c.text(this.c.fit(subtitle, w - 34, PhoneTheme.SMALL), tx, y + 20, PhoneTheme.SMALL, this.t.textDim());
      }
      if (action != null) {
         this.c.glyphAt(Glyph.CHEVRON, x + w - 12, y + h / 2, 8, this.t.textFaint());
         this.onClick(x, y, w, h, action);
      }
      return h;
   }

   /** A grouped card background (iOS-style inset list). */
   public void card(float x, float y, float w, float h) {
      this.c.round(x, y, w, h, PhoneTheme.RADIUS_M, this.t.surface());
   }

   public void divider(float x, float y, float w) {
      this.c.rect(x, y, w, Math.max(0.5F, 1 / this.c.pixelsPerPoint()), this.t.separator());
   }

   /** A spinning arc (loading). */
   public void spinner(float cx, float cy, float r, int color) {
      double now = System.nanoTime() / 1.0E9;
      for (int i = 0; i < 10; i++) {
         double a = now * 5.0 - i * 0.38;
         float alpha = 1.0F - i / 10.0F;
         this.c.circle(cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r, r * 0.22F * (1 - i * 0.05F), Colors.alpha(color, alpha));
      }
   }

   /** Centred message for an empty list or an error. */
   public void empty(Glyph glyph, String title, String text, float y) {
      float cx = this.c.width / 2;
      if (glyph != null) {
         this.c.circle(cx, y + 16, 18, this.t.surfaceHigh());
         this.c.glyphAt(glyph, cx, y + 16, 16, this.t.textDim());
      }
      this.c.textCentered(title, cx, y + 42, PhoneTheme.HEADLINE, this.t.text());
      List<String> lines = this.c.wrap(text, this.c.width - 40, PhoneTheme.SMALL, 4);
      for (int i = 0; i < lines.size(); i++) {
         this.c.textCentered(lines.get(i), cx, y + 58 + i * PhoneCanvas.line(PhoneTheme.SMALL), PhoneTheme.SMALL, this.t.textDim());
      }
   }

   /** Red count bubble. */
   public void badge(float cx, float cy, int count) {
      if (count <= 0) return;
      String s = count > 99 ? "99+" : Integer.toString(count);
      float w = Math.max(11, this.c.width(s, PhoneTheme.CAPTION) + 7);
      this.c.round(cx - w / 2, cy - 5.5F, w, 11, 5.5F, 0xFFFF3B47);
      this.c.textCentered(s, cx, cy - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, 0xFFFFFFFF);
   }

   /** A text field. */
   public void textField(TextInput field, float x, float y, float w, float h) {
      boolean focus = this.focused == field;
      this.c.round(x, y, w, h, Math.min(h / 2, PhoneTheme.RADIUS_M), this.t.surfaceHigh());
      if (focus) this.c.stroke(x, y, w, h, Math.min(h / 2, PhoneTheme.RADIUS_M), 1.0F, this.t.accent());
      float size = PhoneTheme.BODY;
      float pad = 9;
      float room = w - pad * 2;
      String text = field.get();
      if (text.isEmpty() && !focus) {
         this.c.textMid(this.c.fit(field.placeholder, room, size), x + pad, y + h / 2, size, this.t.textFaint());
      } else {
         String before = text.substring(0, field.caret());
         float caretX = this.c.width(before, size);
         float shift = Math.max(0, caretX - room + 2);
         this.c.clip(x + pad - 1, y, room + 2, h);
         if (field.allSelected() && !text.isEmpty()) this.c.rect(x + pad - shift, y + h / 2 - size, this.c.width(text, size), size * 2, this.t.accentSoft());
         if (text.isEmpty()) this.c.textMid(this.c.fit(field.placeholder, room, size), x + pad, y + h / 2, size, this.t.textFaint());
         this.c.textMid(text, x + pad - shift, y + h / 2, size, this.t.text());
         if (focus && (System.currentTimeMillis() - field.focusedAt) % 1000 < 550) {
            this.c.rect(x + pad - shift + caretX, y + h / 2 - size * 0.95F, 0.9F, size * 1.9F, this.t.accent());
         }
         this.c.unclip();
      }
      this.add(x, y, w, h, null, null, null, field);
   }

   /** A round avatar from a label only (its first letter on a colour of the label) - never looks anyone up. */
   public void initialAvatar(String label, float cx, float cy, float r) {
      int hash = label == null ? 0 : label.toLowerCase(java.util.Locale.ROOT).hashCode();
      this.c.circle(cx, cy, r, Colors.hsv((hash & 0xFFFF) / 65535.0F, 0.55F, 0.85F));
      String initial = label == null || label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
      float size = r * 0.9F;
      this.c.textCentered(initial, cx, cy - size / 2, size, 0xFFFFFFFF);
   }

   /** A round avatar: the player's skin face when they are online, otherwise their initial on a colour of their name. */
   public void avatar(String name, UUID uuid, float cx, float cy, float r) {
      Identifier skin = null;
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null) {
         PlayerInfo info = uuid != null ? mc.getConnection().getPlayerInfo(uuid) : name != null ? mc.getConnection().getPlayerInfo(name) : null;
         if (info != null) skin = info.getSkin().body().texturePath();
      }
      if (skin != null) {
         this.c.circle(cx, cy, r, 0xFF2A2433);
         float s = r * 1.42F;
         this.c.image(skin, cx - s / 2, cy - s / 2, s, s, 8 / 64.0F, 8 / 64.0F, 16 / 64.0F, 16 / 64.0F, 0xFFFFFFFF);
         this.c.image(skin, cx - s / 2, cy - s / 2, s, s, 40 / 64.0F, 8 / 64.0F, 48 / 64.0F, 16 / 64.0F, 0xFFFFFFFF);
         this.c.ring(cx, cy, r, 1.2F, this.t.background());
         return;
      }
      int hash = name == null ? 0 : name.toLowerCase(java.util.Locale.ROOT).hashCode();
      int color = Colors.hsv((hash & 0xFFFF) / 65535.0F, 0.55F, 0.85F);
      this.c.circle(cx, cy, r, color);
      String initial = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
      float size = r * 0.9F;
      this.c.textCentered(initial, cx, cy - size / 2, size, 0xFFFFFFFF);
   }
}
