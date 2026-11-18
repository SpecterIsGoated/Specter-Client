package com.jfant.specter.render;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

public final class CardSpot {
   public static final double DEFAULT_SCALE = 0.65;
   private static final List<CardSpot> ALL = new ArrayList<>();
   private final Module owner;
   private final Setting<Corner> corner;
   private final Setting<Integer> offsetX;
   private final Setting<Integer> offsetY;
   private final CardDrag drag = new CardDrag(this);
   private BiPredicate<Float, Float> click;
   private float x;
   private float y;

   public CardSpot(Module owner, SettingGroup group, Corner startCorner, int startX, int startY) {
      this.owner = owner;
      ALL.add(this);
      CardLayout.init();
      this.corner = group.add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("corner"))
                     .description("The screen corner the card's position is measured from. Set by dragging."))
                  .defaultValue(startCorner))
               .visible(() -> false))
            .build()
      );
      this.offsetX = group.add(
         new IntSetting.Builder()
            .name("x-offset")
            .description("How far the card is from its side of the screen, or left (-) or right (+) of the middle for a centred card. Set by dragging.")
            .defaultValue(startX)
            .visible(() -> false)
            .build()
      );
      this.offsetY = group.add(
         new IntSetting.Builder()
            .name("y-offset")
            .description("How far the card is from the top or bottom of the screen. Set by dragging.")
            .defaultValue(startY)
            .min(0)
            .visible(() -> false)
            .build()
      );
   }

   public void place(float width, float height) {
      float[] shown = CardLayout.shown(this, width, height);
      this.drag.update(this.corner, this.offsetX, this.offsetY, shown[0], shown[1], width, height);
      float[] at = CardLayout.place(this, width, height);
      this.x = at[0];
      this.y = at[1];
   }

   float spotX(float width) {
      return this.corner.get().x(SpecterCore.mc.getWindow().getGuiScaledWidth(), width, this.offsetX.get());
   }

   float spotY(float height) {
      return this.corner.get().y(SpecterCore.mc.getWindow().getGuiScaledHeight(), height, this.offsetY.get());
   }

   public float x() {
      return this.x;
   }

   public float y() {
      return this.y;
   }

   public void onClick(BiPredicate<Float, Float> handler) {
      this.click = handler;
   }

   boolean click(float x, float y) {
      return this.click != null && this.click.test(x, y);
   }

   public Module owner() {
      return this.owner;
   }

   public static List<CardSpot> all() {
      return Collections.unmodifiableList(ALL);
   }

   public void moveTo(float x, float y) {
      this.corner.set(Corner.TopLeft);
      this.offsetX.set(Math.max(0, Math.round(x)));
      this.offsetY.set(Math.max(0, Math.round(y)));
   }

   public boolean right() {
      return this.corner.get().right;
   }
}
