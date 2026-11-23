package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.meteor.KeyInputEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.misc.input.KeyAction;
import com.jfant.specter.phone.PhoneOS;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.input.KeyEvent;

/**
 * Donut Phone: a smartphone your character takes out with the chat key. Apps, theme and notifications are set up
 * in the phone's own Settings app; this module holds how it opens and where it sits.
 */
public class DonutPhone extends Module {
   private final SettingGroup sgOpen = this.settings.getDefaultGroup();
   private final SettingGroup sgLook = this.settings.createGroup("Look");

   private final Setting<Boolean> chatKey = this.sgOpen
      .add(new BoolSetting.Builder()
         .name("open-with-chat-key")
         .description("Your chat key (T unless you changed it) takes out the phone instead of opening chat. The command key (/) still opens chat.")
         .defaultValue(true)
         .build());
   private final Setting<Boolean> chatTwice = this.sgOpen
      .add(new BoolSetting.Builder()
         .name("chat-key-again-for-chat")
         .description("While the phone is out and you aren't typing in it, pressing the chat key again puts it away and opens regular chat.")
         .defaultValue(true)
         .visible(this.chatKey::get)
         .build());
   private final Setting<Keybind> openKey = this.sgOpen
      .add(new KeybindSetting.Builder()
         .name("open-key")
         .description("Only used when open-with-chat-key is off: a key of your choice takes out the phone.")
         .defaultValue(Keybind.none())
         .visible(() -> !this.chatKey.get())
         .build());
   private final Setting<Boolean> sounds = this.sgOpen
      .add(new BoolSetting.Builder().name("sounds").description("Quiet taps, notification chimes and the swish of taking it out.").defaultValue(true).build());
   private final Setting<Double> scale = this.sgLook
      .add(new DoubleSetting.Builder().name("scale").description("How big the phone is held.").defaultValue(1.0).range(0.6, 1.3).sliderRange(0.6, 1.3).build());
   private final Setting<Double> offsetX = this.sgLook
      .add(new DoubleSetting.Builder().name("horizontal").description("Move the phone left (-) or right (+).").defaultValue(0.0).range(-1.0, 1.0).sliderRange(-1.0, 1.0).build());
   private final Setting<Double> offsetY = this.sgLook
      .add(new DoubleSetting.Builder().name("vertical").description("Move the phone down (-) or up (+).").defaultValue(0.0).range(-1.0, 1.0).sliderRange(-1.0, 1.0).build());
   private final Setting<Double> speed = this.sgLook
      .add(new DoubleSetting.Builder().name("animation-speed").description("How fast it comes out and goes away.").defaultValue(1.0).range(0.3, 3.0).sliderRange(0.3, 3.0).build());
   private final Setting<Boolean> showArm = this.sgLook
      .add(new BoolSetting.Builder().name("show-arm").description("Your arm (with your skin) holding the phone in first person.").defaultValue(true).build());
   private final Setting<Boolean> dim = this.sgLook
      .add(new BoolSetting.Builder().name("dim-background").description("Darken the top and bottom of the game a little while the phone is out.").defaultValue(true).build());

   private boolean chatRequested;

   public DonutPhone() {
      super(SpecterClientAddon.TOOLS, "donut-phone", "A smartphone in your hand: press your chat key to take it out. DonutGram, Travel, Maps, Games and more.");
   }

   @Override
   public void onDeactivate() {
      if (PhoneOS.started() && PhoneOS.get().isOut()) PhoneOS.get().close();
   }

   public boolean opensWithChatKey() {
      return this.chatKey.get();
   }

   public boolean chatKeyTwice() {
      return this.chatKey.get() && this.chatTwice.get();
   }

   public boolean openKeyMatches(KeyEvent event) {
      return !this.chatKey.get() && this.openKey.get().isSet() && this.openKey.get().matches(event);
   }

   public boolean sounds() {
      return this.sounds.get();
   }

   public void setSounds(boolean on) {
      this.sounds.set(on);
   }

   public double scale() {
      return this.scale.get();
   }

   public double offsetX() {
      return this.offsetX.get();
   }

   public double offsetY() {
      return this.offsetY.get();
   }

   public double speed() {
      return this.speed.get();
   }

   public boolean showArm() {
      return this.showArm.get();
   }

   public boolean dimBackground() {
      return this.dim.get();
   }

   public Setting<Double> scaleSetting() {
      return this.scale;
   }

   public Setting<Double> offsetXSetting() {
      return this.offsetX;
   }

   public Setting<Double> offsetYSetting() {
      return this.offsetY;
   }

   public Setting<Double> speedSetting() {
      return this.speed;
   }

   public Setting<Boolean> showArmSetting() {
      return this.showArm;
   }

   public Setting<Boolean> chatKeySetting() {
      return this.chatKey;
   }

   /** Open regular chat on the next tick (the phone screen's key press would otherwise type a letter into it). */
   public void requestChat() {
      this.chatRequested = true;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.chatRequested) {
         this.chatRequested = false;
         PhoneOS.get().openChat(null);
      }
   }

   @EventHandler
   private void onKey(KeyInputEvent event) {
      if (event.action == KeyAction.Press && this.mc.gui.screen() == null && this.mc.player != null && this.openKeyMatches(event.input)) {
         PhoneOS.get().open();
      }
   }

   @EventHandler
   private void onHud(Render2DEvent event) {
      PhoneOS.get().renderClosingFade(event.graphics);
   }
}
