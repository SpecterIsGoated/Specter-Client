package com.jfant.specter.phone.ui;

/**
 * A vertical scrolling area: mouse wheel, drag-to-scroll with a fling, rubber-band edges and a scroll bar that fades
 * when idle. Keep one per list (PhoneUi.scroll(key) does that) and wrap the list's drawing in begin/end.
 */
public final class ScrollView {
   private float offset;
   private float target;
   private float velocity;
   private float content;
   private float view;
   private boolean dragging;
   private float dragFrom;
   private float dragOffset;
   private float lastDragY;
   private long lastDragAt;
   private long lastMove;
   float x0, y0, x1, y1;

   /**
    * Starts the area: clips to it, registers it for wheel/drag and shifts drawing by the scroll offset. Draw content
    * from {@code y} downwards as if it were not scrolled; call {@link #end} after.
    */
   public void begin(PhoneUi ui, float x, float y, float w, float h, float contentHeight) {
      PhoneCanvas c = ui.canvas();
      this.view = h;
      this.content = contentHeight;
      this.step(ui.dt());
      this.x0 = c.offsetX() + x;
      this.y0 = c.offsetY() + y;
      this.x1 = this.x0 + w;
      this.y1 = this.y0 + h;
      ui.registerScroll(this);
      c.clip(x, y, w, h);
      c.push(0, -Math.round(this.offset * c.pixelsPerPoint()) / c.pixelsPerPoint());
   }

   public void end(PhoneUi ui) {
      PhoneCanvas c = ui.canvas();
      c.pop();
      float max = this.max();
      long idle = System.currentTimeMillis() - this.lastMove;
      if (max > 1 && idle < 1100) {
         float a = idle < 700 ? 1 : 1 - (idle - 700) / 400.0F;
         float barH = Math.max(18, this.view * this.view / this.content);
         float barY = this.y0 - c.offsetY() + (this.view - barH) * Math.clamp(this.offset / max, 0, 1);
         c.round(this.x1 - c.offsetX() - 3.2F, barY + 2, 2.2F, barH - 4, 1.1F, (int)(110 * a) << 24 | (c.theme().dark() ? 0xFFFFFF : 0x000000));
      }
      c.unclip();
   }

   public float offset() {
      return this.offset;
   }

   public float max() {
      return Math.max(0, this.content - this.view);
   }

   public void toTop() {
      this.target = 0;
      this.velocity = 0;
   }

   public void jumpTo(float y) {
      this.offset = this.target = Math.clamp(y, 0, this.max());
      this.velocity = 0;
   }

   public boolean atBottom(float margin) {
      return this.offset >= this.max() - margin;
   }

   boolean contains(float x, float y) {
      return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
   }

   void wheel(double amount) {
      this.velocity = 0;
      this.target = Math.clamp(this.target - (float)amount * 38.0F, 0, this.max());
      this.lastMove = System.currentTimeMillis();
   }

   void dragStart(float y) {
      this.dragging = true;
      this.dragFrom = y;
      this.dragOffset = this.offset;
      this.lastDragY = y;
      this.lastDragAt = System.nanoTime();
      this.velocity = 0;
   }

   void drag(float y) {
      long now = System.nanoTime();
      float dt = Math.max(1.0E-3F, (now - this.lastDragAt) / 1.0E9F);
      float v = -(y - this.lastDragY) / dt;
      this.velocity = this.velocity * 0.6F + v * 0.4F;
      this.lastDragY = y;
      this.lastDragAt = now;
      float raw = this.dragOffset - (y - this.dragFrom);
      float max = this.max();
      if (raw < 0) raw = raw * 0.35F;
      else if (raw > max) raw = max + (raw - max) * 0.35F;
      this.offset = this.target = raw;
      this.lastMove = System.currentTimeMillis();
   }

   void dragEnd() {
      this.dragging = false;
      if (System.nanoTime() - this.lastDragAt > 80_000_000L) this.velocity = 0;
      this.target = Math.clamp(this.offset, 0, this.max());
   }

   private void step(float dt) {
      if (this.dragging) return;
      float max = this.max();
      if (Math.abs(this.velocity) > 20) {
         this.offset += this.velocity * dt;
         this.velocity *= (float)Math.exp(-dt * 4.5F);
         if (this.offset < 0 || this.offset > max) this.velocity *= 0.5F;
         this.target = Math.clamp(this.offset, 0, max);
         this.lastMove = System.currentTimeMillis();
         if (this.offset >= 0 && this.offset <= max) return;
      }
      this.velocity = 0;
      this.target = Math.clamp(this.target, 0, max);
      float k = 1 - (float)Math.exp(-dt * 16.0F);
      if (Math.abs(this.target - this.offset) > 0.05F) {
         this.offset += (this.target - this.offset) * k;
         this.lastMove = System.currentTimeMillis();
      } else {
         this.offset = this.target;
      }
   }
}
