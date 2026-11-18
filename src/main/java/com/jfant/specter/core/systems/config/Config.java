package com.jfant.specter.core.systems.config;

import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.renderer.Fonts;
import com.jfant.specter.core.renderer.text.FontFace;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.FontFaceSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.ModuleListSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.System;
import com.jfant.specter.core.systems.Systems;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.festive.Season;
import com.jfant.specter.notify.Notifications;
import com.jfant.specter.render.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public class Config extends System<Config> {
   public final Settings settings = new Settings();
   private final SettingGroup sgVisual = this.settings.createGroup("Visual");
   private final SettingGroup sgMotion = this.settings.createGroup("Animations");
   private final SettingGroup sgNotify = this.settings.createGroup("Notifications");
   private final SettingGroup sgSeason = this.settings.createGroup("Season");
   private final SettingGroup sgModules = this.settings.createGroup("Modules");
   private final SettingGroup sgChat = this.settings.createGroup("Chat");
   private final SettingGroup sgMisc = this.settings.createGroup("Misc");
   public final Setting<Theme> theme = this.sgVisual
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("theme"))
                  .description(
                     "How the GUI and HUD cards look. Blocky: pixel font, pixel corners, glints and sparkles. Clean: smooth font (Plus Jakarta Sans) and smooth glass."
                  ))
               .defaultValue(Theme.Clean))
            .build()
      );
   public final Setting<Boolean> customFont = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("custom-font")
            .description("Blocky theme: draws Specter's text (the GUI and the HUD cards) in Pixelify Sans. Off, in Minecraft's own font.")
            .defaultValue(true)
            .visible(() -> this.theme.get() == Theme.Blocky)
            .build()
      );
   public final Setting<FontFace> font = this.sgVisual
      .add(new FontFaceSetting.Builder().name("font").description("Custom font to use.").visible(() -> false).onChanged(Fonts::load).build());
   public final Setting<Double> rainbowSpeed = this.sgVisual
      .add(new DoubleSetting.Builder().name("rainbow-speed").description("The global rainbow speed.").defaultValue(0.5).range(0.0, 10.0).sliderMax(5.0).build());
   public final Setting<Boolean> customWindowTitle = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("custom-window-title")
            .description("Show custom text in the window title.")
            .defaultValue(false)
            .onModuleActivated(setting -> SpecterCore.mc.updateTitle())
            .onChanged(value -> SpecterCore.mc.updateTitle())
            .build()
      );
   public final Setting<String> customWindowTitleText = this.sgVisual
      .add(
         new StringSetting.Builder()
            .name("window-title-text")
            .description("The text it displays in the window title.")
            .visible(this.customWindowTitle::get)
            .defaultValue("Specter Client")
            .onChanged(value -> SpecterCore.mc.updateTitle())
            .build()
      );
   public final Setting<SettingColor> friendColor = this.sgVisual
      .add(new ColorSetting.Builder().name("friend-color").description("The color used to show friends.").defaultValue(new SettingColor(0, 255, 180)).build());
   public final Setting<Boolean> syncListSettingWidths = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("sync-list-setting-widths")
            .description("Prevents the list setting screens from moving around as you add & remove elements.")
            .defaultValue(false)
            .build()
      );
   public final Setting<Config.ButtonPosition> accountButtonAnchor = this.sgVisual
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("accounts-button"))
                  .description("Controls the position and visibility of the accounts button in the multiplayer screen."))
               .defaultValue(Config.ButtonPosition.TopRight))
            .build()
      );
   public final Setting<Boolean> showAccountStatus = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("account-status")
            .description("Shows information about the current account in the multiplayer screen.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Config.ButtonPosition> proxiesButtonAnchor = this.sgVisual
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("proxies-button"))
                  .description("Controls the position and visibility of the proxies button in the multiplayer screen."))
               .defaultValue(Config.ButtonPosition.TopRight))
            .build()
      );
   public final Setting<Boolean> showProxiesStatus = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("proxy-status")
            .description("Shows information about the current proxy in the multiplayer screen.")
            .defaultValue(true)
            .build()
      );
   public final Setting<List<Module>> hiddenModules = this.sgModules
      .add(new ModuleListSetting.Builder().name("hidden-modules").description("Prevent these modules from being rendered as options in the clickgui.").build());
   public final Setting<Integer> moduleSearchCount = this.sgModules
      .add(
         new IntSetting.Builder()
            .name("module-search-count")
            .description("Amount of modules and settings to be shown in the module search bar.")
            .defaultValue(8)
            .min(1)
            .sliderMax(12)
            .build()
      );
   public final Setting<Boolean> moduleAliases = this.sgModules
      .add(
         new BoolSetting.Builder()
            .name("search-module-aliases")
            .description("Whether or not module aliases will be used in the module search bar.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> animations = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("animations")
            .description("The master switch for motion: off, everything in the client changes at once instead of easing.")
            .defaultValue(true)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Double> animationSpeed = this.sgMotion
      .add(
         new DoubleSetting.Builder()
            .name("animation-speed")
            .description("How fast animations play. 1 is as designed, 2 is twice as fast, 0.5 half speed.")
            .defaultValue(1.0)
            .range(0.25, 4.0)
            .sliderRange(0.25, 3.0)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Double> animationIntensity = this.sgMotion
      .add(
         new DoubleSetting.Builder()
            .name("animation-intensity")
            .description("How far things travel when they move: slides, pops and flashes. 0 keeps the fades and drops the movement, 1 is as designed.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> reducedMotion = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("reduced-motion")
            .description("No sliding, scaling or drifting anywhere: things cross-fade quickly into place. Easier on the eyes and on motion sickness.")
            .defaultValue(false)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> animateGui = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("gui-animations")
            .description("The click GUI: panels, drop-downs, switches, sliders, hover, scrolling, tooltips.")
            .defaultValue(true)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> animateModules = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("module-animations")
            .description("Feedback when a module goes on or off: its row lighting up, the module list sliding.")
            .defaultValue(true)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> animateHud = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("hud-animations")
            .description("Notifications and other cards over the game sliding in, stacking and leaving.")
            .defaultValue(true)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> animateWorld = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("world-animations")
            .description("The world easing between looks: colour grades cross-fading, fog rolling in, glow rising at dusk.")
            .defaultValue(true)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> animateCamera = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("camera-animations")
            .description("Field of view and zoom gliding to their new value instead of jumping.")
            .defaultValue(true)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> animateFestive = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("seasonal-animations")
            .description("Seasonal decoration that moves: falling snow, twinkling lights.")
            .defaultValue(true)
            .visible(this.animations::get)
            .onChanged(v -> this.applyMotion())
            .build()
      );
   public final Setting<Boolean> advancedAnimations = this.sgMotion
      .add(new BoolSetting.Builder().name("advanced").description("Shows the finer switches below.").defaultValue(false).visible(this.animations::get).build());
   public final Setting<Boolean> smoothScrolling = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("smooth-scrolling")
            .description("Lists glide when you scroll instead of jumping a step.")
            .defaultValue(true)
            .visible(() -> this.animations.get() && this.advancedAnimations.get())
            .build()
      );
   public final Setting<Boolean> openTransition = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("open-close-transition")
            .description("The click GUI's panels fall into place one after another when it opens, and fade away when it closes.")
            .defaultValue(true)
            .visible(() -> this.animations.get() && this.advancedAnimations.get())
            .build()
      );
   public final Setting<Boolean> hoverEffects = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("hover-effects")
            .description("Rows and buttons light up softly under the mouse and give a little when clicked.")
            .defaultValue(true)
            .visible(() -> this.animations.get() && this.advancedAnimations.get())
            .build()
      );
   public final Setting<Boolean> toggleFlash = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("toggle-flash")
            .description("A soft wash sweeps across a module's row when it goes on or off.")
            .defaultValue(true)
            .visible(() -> this.animations.get() && this.advancedAnimations.get())
            .build()
      );
   public final Setting<Boolean> backgroundFade = this.sgMotion
      .add(
         new BoolSetting.Builder()
            .name("background-fade")
            .description("The game darkens gently behind the click GUI as it opens.")
            .defaultValue(true)
            .visible(() -> this.animations.get() && this.advancedAnimations.get())
            .build()
      );
   public final Setting<Boolean> notifications = this.sgNotify
      .add(
         new BoolSetting.Builder()
            .name("notifications")
            .description(
               "Small cards in a corner of the screen for things worth knowing: a module going on or off, an automation finishing or running into trouble."
            )
            .defaultValue(true)
            .onChanged(v -> this.applyNotifications())
            .build()
      );
   public final Setting<Boolean> moduleNotifications = this.sgNotify
      .add(
         new BoolSetting.Builder()
            .name("module-toggles")
            .description("A notification when a module is switched on or off.")
            .defaultValue(true)
            .visible(this.notifications::get)
            .onChanged(v -> this.applyNotifications())
            .build()
      );
   public final Setting<Notifications.Corner> notificationCorner = this.sgNotify
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("corner"))
                        .description("Which corner of the screen notifications stack in."))
                     .defaultValue(Notifications.Corner.BottomRight))
                  .visible(this.notifications::get))
               .onChanged(v -> this.applyNotifications()))
            .build()
      );
   public final Setting<Double> notificationSeconds = this.sgNotify
      .add(
         new DoubleSetting.Builder()
            .name("stay-seconds")
            .description("How long a notification stays up.")
            .defaultValue(3.2)
            .range(1.0, 12.0)
            .sliderRange(1.0, 8.0)
            .decimalPlaces(1)
            .visible(this.notifications::get)
            .onChanged(v -> this.applyNotifications())
            .build()
      );
   public final Setting<Season> season = this.sgSeason
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("season"))
                  .description(
                     "Seasonal dress for the click GUI. Winter: frost on the glass and snow settling on the panels. Christmas: that plus a string of lights along the top bar. Off: plain Specter."
                  ))
               .defaultValue(Season.Christmas))
            .build()
      );
   public final Setting<Boolean> guiSnow = this.sgSeason
      .add(
         new BoolSetting.Builder()
            .name("menu-snow")
            .description("Snow falls behind the click GUI.")
            .defaultValue(true)
            .visible(() -> this.season.get() != Season.Off)
            .build()
      );
   public final Setting<Double> guiSnowAmount = this.sgSeason
      .add(
         new DoubleSetting.Builder()
            .name("menu-snow-amount")
            .description("How much snow falls behind the click GUI.")
            .defaultValue(1.0)
            .range(0.2, 3.0)
            .sliderRange(0.2, 3.0)
            .visible(() -> this.season.get() != Season.Off && this.guiSnow.get())
            .build()
      );
   public final Setting<Boolean> settlingSnow = this.sgSeason
      .add(
         new BoolSetting.Builder()
            .name("settling-snow")
            .description("Snow builds up along the tops of the panels while the GUI is open, and slides off a panel when you drag it.")
            .defaultValue(true)
            .visible(() -> this.season.get() != Season.Off)
            .build()
      );
   public final Setting<Boolean> titleSnow = this.sgSeason
      .add(
         new BoolSetting.Builder()
            .name("title-screen-snow")
            .description("Snow falls over the game's title screen too.")
            .defaultValue(true)
            .visible(() -> this.season.get() != Season.Off)
            .build()
      );
   public final Setting<String> prefix = this.sgChat.add(new StringSetting.Builder().name("prefix").description("Prefix.").defaultValue(".").build());
   public final Setting<Boolean> chatFeedback = this.sgChat
      .add(new BoolSetting.Builder().name("chat-feedback").description("Sends chat feedback when meteor performs certain actions.").defaultValue(true).build());
   public final Setting<Boolean> deleteChatFeedback = this.sgChat
      .add(
         new BoolSetting.Builder()
            .name("delete-chat-feedback")
            .description("Delete previous matching chat feedback to keep chat clear.")
            .visible(this.chatFeedback::get)
            .defaultValue(true)
            .build()
      );
   public final Setting<Integer> rotationHoldTicks = this.sgMisc
      .add(
         new IntSetting.Builder()
            .name("rotation-hold")
            .description("Hold long to hold server side rotation when not sending any packets.")
            .defaultValue(4)
            .build()
      );
   public final Setting<Boolean> useTeamColor = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("use-team-color")
            .description("Uses player's team color for rendering things like esp and tracers.")
            .defaultValue(true)
            .build()
      );
   public List<String> dontShowAgainPrompts = new ArrayList<>();

   public void applyMotion() {
      Motion.configure(this.animations.get(), this.animationSpeed.get(), this.animationIntensity.get(), this.reducedMotion.get());
      Motion.channel(Channel.GUI, this.animateGui.get());
      Motion.channel(Channel.MODULES, this.animateModules.get());
      Motion.channel(Channel.HUD, this.animateHud.get());
      Motion.channel(Channel.WORLD, this.animateWorld.get());
      Motion.channel(Channel.CAMERA, this.animateCamera.get());
      Motion.channel(Channel.FESTIVE, this.animateFestive.get());
   }

   public void applyNotifications() {
      Notifications.configure(this.notifications.get(), this.moduleNotifications.get(), this.notificationCorner.get(), this.notificationSeconds.get());
   }

   public Config() {
      super("config");
   }

   public static Config get() {
      return Systems.get(Config.class);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("version", SpecterCore.VERSION.toString());
      tag.put("settings", this.settings.toTag());
      tag.put("dontShowAgainPrompts", this.listToTag(this.dontShowAgainPrompts));
      return tag;
   }

   public Config fromTag(CompoundTag tag) {
      if (tag.contains("settings")) {
         this.settings.fromTag(tag.getCompoundOrEmpty("settings"));
      }

      if (tag.contains("dontShowAgainPrompts")) {
         this.dontShowAgainPrompts = this.listFromTag(tag, "dontShowAgainPrompts");
      }

      this.applyMotion();
      this.applyNotifications();
      return this;
   }

   private ListTag listToTag(List<String> list) {
      ListTag nbt = new ListTag();

      for (String item : list) {
         nbt.add(StringTag.valueOf(item));
      }

      return nbt;
   }

   private List<String> listFromTag(CompoundTag tag, String key) {
      List<String> list = new ArrayList<>();

      for (Tag item : tag.getListOrEmpty(key)) {
         list.add(item.asString().orElse(""));
      }

      return list;
   }

   public static enum ButtonPosition {
      TopLeft,
      TopRight,
      BottomLeft,
      BottomRight,
      Hidden;
   }
}
