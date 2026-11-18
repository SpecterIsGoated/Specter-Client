package com.jfant.specter.core.systems.modules;

import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.ISerializable;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.notify.Notifications;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public abstract class Module implements ISerializable<Module>, Comparable<Module> {
   protected final Minecraft mc;
   public final Category category;
   public final String name;
   public final String title;
   public final String description;
   public final String[] aliases;
   public final Color color;
   public final Settings settings = new Settings();
   private boolean active;
   public boolean serialize = true;
   public boolean runInMainMenu = false;
   public boolean autoSubscribe = true;
   public final Keybind keybind = Keybind.none();
   public boolean toggleOnBindRelease = false;
   public boolean chatFeedback = true;
   public boolean favorite = false;

   public Module(Category category, String name, String description, String... aliases) {
      if (name.contains(" ")) {
         SpecterCore.LOG.warn("Module '{}' contains invalid characters in its name making it incompatible with Meteor Client commands.", name);
      }

      this.mc = Minecraft.getInstance();
      this.category = category;
      this.name = name;
      this.title = Utils.nameToTitle(name);
      this.description = description;
      this.aliases = aliases;
      this.color = Color.fromHsv(Utils.random(0.0, 360.0), 0.35, 1.0);
   }

   public Module(Category category, String name, String desc) {
      this(category, name, desc);
   }

   public Module(Category category, String name, String title, String description, String[] aliases) {
      this.mc = Minecraft.getInstance();
      this.category = category;
      this.name = name;
      this.title = title;
      this.description = description;
      this.aliases = aliases;
      this.color = Color.fromHsv(Utils.random(0.0, 360.0), 0.35, 1.0);
   }

   public WWidget getWidget(GuiTheme theme) {
      return null;
   }

   public void onActivate() {
   }

   public void onDeactivate() {
   }

   public void toggle() {
      if (!this.active) {
         this.active = true;
         Modules.get().addActive(this);
         this.settings.onActivated();
         if (this.runInMainMenu || Utils.canUpdate()) {
            if (this.autoSubscribe) {
               SpecterCore.EVENT_BUS.subscribe(this);
            }

            this.onActivate();
         }
      } else {
         if (this.runInMainMenu || Utils.canUpdate()) {
            if (this.autoSubscribe) {
               SpecterCore.EVENT_BUS.unsubscribe(this);
            }

            this.onDeactivate();
         }

         this.active = false;
         Modules.get().removeActive(this);
      }

      Animator.fire(this, "toggled", Spec.MODULE);
      if (Utils.canUpdate()) {
         Notifications.module(this, this.title, this.active);
      }
   }

   public void enable() {
      if (!this.isActive()) {
         this.toggle();
      }
   }

   public void disable() {
      if (this.isActive()) {
         this.toggle();
      }
   }

   public void sendToggledMsg() {
      if (Config.get().chatFeedback.get() && this.chatFeedback) {
         ChatUtils.forceNextPrefixClass(this.getClass());
         ChatUtils.sendMsg(
            this.hashCode(),
            ChatFormatting.GRAY,
            "Toggled (highlight)%s(default) %s(default).",
            this.title,
            this.isActive() ? ChatFormatting.GREEN + "on" : ChatFormatting.RED + "off"
         );
      }
   }

   public void info(Component message) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.sendMsg(this.title, message);
   }

   public void info(String message, Object... args) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.infoPrefix(this.title, message, args);
   }

   public void warning(String message, Object... args) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.warningPrefix(this.title, message, args);
   }

   public void error(String message, Object... args) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.errorPrefix(this.title, message, args);
   }

   public boolean isActive() {
      return this.active;
   }

   public String getInfoString() {
      return null;
   }

   @Override
   public CompoundTag toTag() {
      if (!this.serialize) {
         return null;
      } else {
         CompoundTag tag = new CompoundTag();
         tag.putString("name", this.name);
         tag.put("keybind", this.keybind.toTag());
         tag.putBoolean("toggleOnKeyRelease", this.toggleOnBindRelease);
         tag.putBoolean("chatFeedback", this.chatFeedback);
         tag.putBoolean("favorite", this.favorite);
         tag.put("settings", this.settings.toTag());
         tag.putBoolean("active", this.active);
         return tag;
      }
   }

   public Module fromTag(CompoundTag tag) {
      this.keybind.fromTag(tag.getCompoundOrEmpty("keybind"));
      this.toggleOnBindRelease = tag.getBooleanOr("toggleOnKeyRelease", false);
      this.chatFeedback = !tag.contains("chatFeedback") || tag.getBooleanOr("chatFeedback", false);
      this.favorite = tag.getBooleanOr("favorite", false);
      Tag settingsTag = tag.get("settings");
      if (settingsTag instanceof CompoundTag) {
         this.settings.fromTag((CompoundTag)settingsTag);
      }

      boolean active = tag.getBooleanOr("active", false);
      if (active != this.isActive()) {
         this.toggle();
      }

      return this;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Module module = (Module)o;
         return Objects.equals(this.name, module.name);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.name);
   }

   public int compareTo(@NotNull Module o) {
      return this.name.compareTo(o.name);
   }
}
