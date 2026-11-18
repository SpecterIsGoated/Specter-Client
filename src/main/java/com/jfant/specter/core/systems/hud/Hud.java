package com.jfant.specter.core.systems.hud;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.meteor.CustomFontChangedEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorListSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.systems.System;
import com.jfant.specter.core.systems.Systems;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.misc.NbtUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.gui.screen.SpecterScreen;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

public class Hud extends System<Hud> implements Iterable<HudElement> {
   public static final HudGroup GROUP = new HudGroup("Meteor");
   public boolean active;
   public Settings settings = new Settings();
   public final Map<String, HudElementInfo<?>> infos = new TreeMap<>();
   private final List<HudElement> elements = new ArrayList<>();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgEditor = this.settings.createGroup("Editor");
   private final SettingGroup sgKeybind = this.settings.createGroup("Bind");
   private final Setting<Boolean> customFont = this.sgGeneral
      .add(new BoolSetting.Builder().name("custom-font").description("Text will use custom font.").defaultValue(true).onChanged(aBoolean -> {
         for (HudElement element : this.elements) {
            element.onFontChanged();
         }
      }).build());
   private final Setting<Boolean> hideInMenus = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hide-in-menus")
            .description("Hides the meteor hud when in inventory screens or game menus.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> textScale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("text-scale")
            .description("Scale of text if not overridden by the element.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   public final Setting<List<SettingColor>> textColors = this.sgGeneral
      .add(
         new ColorListSetting.Builder()
            .name("text-colors")
            .description("Colors used for the Text element.")
            .defaultValue(List.of(new SettingColor(), new SettingColor(175, 175, 175), new SettingColor(25, 225, 25), new SettingColor(225, 25, 25)))
            .build()
      );
   public final Setting<Integer> border = this.sgEditor
      .add(new IntSetting.Builder().name("border").description("Space around the edges of the screen.").defaultValue(4).sliderMax(20).build());
   public final Setting<Integer> snappingRange = this.sgEditor
      .add(new IntSetting.Builder().name("snapping-range").description("Snapping range in editor.").defaultValue(10).sliderMax(20).build());
   private final Setting<Keybind> keybind = this.sgKeybind
      .add(new KeybindSetting.Builder().name("bind").defaultValue(Keybind.none()).action(() -> this.active = !this.active).build());
   private boolean resetToDefaultElements;

   public Hud() {
      super("hud");
   }

   public static Hud get() {
      return Systems.get(Hud.class);
   }

   @Override
   public void init() {
      this.settings.registerColorSettings(null);
      if (this.isFirstInit) {
         this.resetToDefaultElements();
      }
   }

   public static Color getSectionColor(int i) {
      List<SettingColor> colors = get().textColors.get();
      return i >= 0 && i < colors.size() ? colors.get(i) : Color.WHITE;
   }

   public void register(HudElementInfo<?> info) {
      this.infos.put(info.name, info);
   }

   private void add(HudElement element, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
      element.box.setPos(x, y);
      if (xAnchor != null && yAnchor != null) {
         element.box.xAnchor = xAnchor;
         element.box.yAnchor = yAnchor;
      } else {
         element.box.updateAnchors();
      }

      element.settings.registerColorSettings(null);
      this.elements.add(element);
   }

   public void add(HudElementInfo<?> info, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
      this.add(info.create(), x, y, xAnchor, yAnchor);
   }

   public void add(HudElementInfo<?> info, int x, int y) {
      this.add(info, x, y, null, null);
   }

   public void add(@NotNull HudElementInfo.Preset preset, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
      HudElement element = preset.info.create();
      preset.callback.accept(element);
      this.add(element, x, y, xAnchor, yAnchor);
   }

   public void add(@NotNull HudElementInfo<?>.Preset preset, int x, int y) {
      this.add(preset, x, y, null, null);
   }

   void remove(HudElement element) {
      element.settings.unregisterColorSettings();
      this.elements.remove(element);
   }

   public void clear() {
      this.elements.clear();
   }

   public void resetToDefaultElements() {
      this.resetToDefaultElements = true;
   }

   private void resetToDefaultElementsImpl() {
      this.elements.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (!Utils.isLoading()) {
         if (this.resetToDefaultElements) {
            this.resetToDefaultElementsImpl();
            this.resetToDefaultElements = false;
         }

         if (this.active || HudEditorScreen.isOpen()) {
            for (HudElement element : this.elements) {
               if (element.isActive() || element.isInEditor()) {
                  element.tick(HudRenderer.INSTANCE);
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render2DEvent event) {
      if (!Utils.isLoading()) {
         if (this.active && !this.shouldHideHud()) {
            if (!SpecterCore.mc.options.hideGui && !SpecterCore.mc.debugEntries.isOverlayVisible() || HudEditorScreen.isOpen()) {
               HudRenderer.INSTANCE.begin(event.drawContext);

               for (HudElement element : this.elements) {
                  element.updatePos();
                  if (element.isActive() || element.isInEditor()) {
                     element.render(HudRenderer.INSTANCE);
                  }
               }

               HudRenderer.INSTANCE.end();
            }
         }
      }
   }

   private boolean shouldHideHud() {
      return this.hideInMenus.get()
         && SpecterCore.mc.screen != null
         && !(SpecterCore.mc.screen instanceof WidgetScreen)
         && !(SpecterCore.mc.screen instanceof SpecterScreen);
   }

   @EventHandler
   private void onCustomFontChanged(CustomFontChangedEvent event) {
      if (this.customFont.get()) {
         for (HudElement element : this.elements) {
            element.onFontChanged();
         }
      }
   }

   public boolean hasCustomFont() {
      return this.customFont.get();
   }

   public double getTextScale() {
      return this.textScale.get();
   }

   @NotNull
   @Override
   public Iterator<HudElement> iterator() {
      return this.elements.iterator();
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("__version__", 1);
      tag.putBoolean("active", this.active);
      tag.put("settings", this.settings.toTag());
      tag.put("elements", NbtUtils.listToTag(this.elements));
      return tag;
   }

   public Hud fromTag(CompoundTag tag) {
      if (!tag.contains("__version__")) {
         this.resetToDefaultElements();
         return this;
      } else {
         tag.getBoolean("active").ifPresent(active1 -> this.active = active1);
         this.settings.fromTag(tag.getCompoundOrEmpty("settings"));
         this.elements.clear();

         for (Tag e : tag.getListOrEmpty("elements")) {
            CompoundTag c = (CompoundTag)e;
            if (!c.getString("name").isEmpty()) {
               HudElementInfo<?> info = this.infos.get(c.getString("name").get());
               if (info != null) {
                  HudElement element = info.create();
                  element.fromTag(c);
                  element.settings.registerColorSettings(null);
                  this.elements.add(element);
               }
            }
         }

         return this;
      }
   }
}
