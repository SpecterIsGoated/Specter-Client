package com.jfant.specter.core.settings;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.meteor.KeyEvent;
import com.jfant.specter.core.events.meteor.MouseClickEvent;
import com.jfant.specter.core.gui.widgets.WKeybind;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.misc.input.KeyAction;
import java.util.function.Consumer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;

public class KeybindSetting extends Setting<Keybind> {
   private final Runnable action;
   public WKeybind widget;

   public KeybindSetting(
      String name,
      String description,
      Keybind defaultValue,
      Consumer<Keybind> onChanged,
      Consumer<Setting<Keybind>> onModuleActivated,
      IVisible visible,
      Runnable action
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.action = action;
      SpecterCore.EVENT_BUS.subscribe(this);
   }

   @EventHandler(
      priority = 200
   )
   private void onKeyBinding(KeyEvent event) {
      if (this.widget != null) {
         if (event.action == KeyAction.Press && event.key() == 256 && this.widget.onClear()) {
            event.cancel();
         } else if (event.action == KeyAction.Release && this.widget.onAction(true, event.key(), event.modifiers())) {
            event.cancel();
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onMouseClickBinding(MouseClickEvent event) {
      if (event.action == KeyAction.Press && this.widget != null && this.widget.onAction(false, event.button(), 0)) {
         event.cancel();
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onKey(KeyEvent event) {
      if (event.action == KeyAction.Release && this.get().matches(event.input) && (this.module == null || this.module.isActive()) && this.action != null) {
         this.action.run();
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseClick(MouseClickEvent event) {
      if (event.action == KeyAction.Release && this.get().matches(event.input) && (this.module == null || this.module.isActive()) && this.action != null) {
         this.action.run();
      }
   }

   @Override
   public void resetImpl() {
      if (this.value == null) {
         this.value = this.defaultValue.copy();
      } else {
         this.value.set(this.defaultValue);
      }

      if (this.widget != null) {
         this.widget.reset();
      }
   }

   protected Keybind parseImpl(String str) {
      try {
         return Keybind.fromKey(Integer.parseInt(str.trim()));
      } catch (NumberFormatException var3) {
         return null;
      }
   }

   protected boolean isValueValid(Keybind value) {
      return true;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.put("value", this.get().toTag());
      return tag;
   }

   public Keybind load(CompoundTag tag) {
      this.get().fromTag(tag.getCompoundOrEmpty("value"));
      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<KeybindSetting.Builder, Keybind, KeybindSetting> {
      private Runnable action;

      public Builder() {
         super(Keybind.none());
      }

      public KeybindSetting.Builder action(Runnable action) {
         this.action = action;
         return this;
      }

      public KeybindSetting build() {
         return new KeybindSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible, this.action);
      }
   }
}
