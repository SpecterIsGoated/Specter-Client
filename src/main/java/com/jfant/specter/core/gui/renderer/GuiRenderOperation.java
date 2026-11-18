package com.jfant.specter.core.gui.renderer;

import com.jfant.specter.core.utils.misc.Pool;
import com.jfant.specter.core.utils.render.color.Color;

public abstract class GuiRenderOperation<T extends GuiRenderOperation<T>> {
   protected double x;
   protected double y;
   protected Color color;

   public void set(double x, double y, Color color) {
      this.x = x;
      this.y = y;
      this.color = color;
   }

   public void run(Pool<T> pool) {
      this.onRun();
      pool.free((T)this);
   }

   protected abstract void onRun();
}
