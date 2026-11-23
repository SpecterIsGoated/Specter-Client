package com.jfant.specter.phone.core;

import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.render.PhoneRenderer;
import com.jfant.specter.phone.ui.PhoneUi;
import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;

/**
 * Turns mouse and keyboard input on the phone screen into phone-point events and hands them to whatever is on top:
 * the notification shade, then the status and navigation bars, then the home screen / app switcher / app (its
 * widgets first, then the app's own raw handlers). A press is followed by its drags and release wherever it started.
 *
 * <p>Buttons are normalised for apps: 0 left, 1 right, 2 middle, 3 back, 4 forward.
 */
public final class PhoneInputManager {
   private final PhoneOS os;
   @Nullable
   private PhoneUi active;
   private boolean rawPress;
   private int rawButton;
   private float lastX;
   private float lastY;

   public PhoneInputManager(PhoneOS os) {
      this.os = os;
   }

   /** GUI coordinates to phone points using this frame's glass rectangle. NaN when the phone isn't on screen. */
   public float toPhoneX(double guiX) {
      PhoneRenderer.ScreenRect r = this.os.rect();
      return r.valid ? (float)((guiX - r.x) / r.scale) : Float.NaN;
   }

   public float toPhoneY(double guiY) {
      PhoneRenderer.ScreenRect r = this.os.rect();
      return r.valid ? (float)((guiY - r.y) / r.scale) : Float.NaN;
   }

   public static int appButton(int mcButton) {
      if (mcButton == InputConstants.MOUSE_BUTTON_LEFT) return 0;
      if (mcButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
      if (mcButton == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
      if (mcButton == InputConstants.MOUSE_BUTTON_4) return 3;
      if (mcButton == InputConstants.MOUSE_BUTTON_5) return 4;
      return 5;
   }

   private boolean inside(float x, float y) {
      return x >= 0 && y >= 0 && x < 180 && y < 392;
   }

   public boolean press(double gx, double gy, int mcButton) {
      if (!this.os.animation().interactive()) return true;
      float x = this.toPhoneX(gx), y = this.toPhoneY(gy);
      int button = appButton(mcButton);
      this.lastX = x;
      this.lastY = y;
      if (button == 3) {
         this.os.back();
         return true;
      }
      if (!this.inside(x, y)) return false;
      PhoneSystemUi sys = this.os.system();
      if (sys.shadeOpen()) {
         if (button == 0 && sys.bars.press(x, y)) {
            this.active = sys.bars;
         } else if (button == 0 && sys.shadeUi.press(x, y)) {
            this.active = sys.shadeUi;
         } else {
            sys.closeShade();
         }
         return true;
      }
      if (button == 0 && sys.bars.press(x, y)) {
         this.active = sys.bars;
         return true;
      }
      PhoneUi layer = this.layerUi();
      if (button == 0 && layer != null && layer.press(x, y)) {
         this.active = layer;
         return true;
      }
      PhoneApplicationManager.Running app = this.os.navigation().current();
      if (app != null && app.app() != null) {
         PhoneApplication a = app.app();
         this.rawPress = this.os.apps().guard(app, "mouseClicked", () -> a.mouseClicked(x, y, button), false);
         this.rawButton = button;
      }
      return true;
   }

   public boolean drag(double gx, double gy) {
      float x = this.toPhoneX(gx), y = this.toPhoneY(gy);
      float dx = x - this.lastX, dy = y - this.lastY;
      this.lastX = x;
      this.lastY = y;
      if (this.active != null) {
         this.active.move(x, y);
         return true;
      }
      if (this.rawPress) {
         PhoneApplicationManager.Running app = this.os.navigation().current();
         if (app != null && app.app() != null) {
            PhoneApplication a = app.app();
            this.os.apps().guard(app, "mouseDragged", () -> a.mouseDragged(x, y, this.rawButton, dx, dy), false);
         }
         return true;
      }
      return false;
   }

   public boolean release(double gx, double gy, int mcButton) {
      float x = this.toPhoneX(gx), y = this.toPhoneY(gy);
      if (this.active != null) {
         PhoneUi ui = this.active;
         this.active = null;
         ui.release(x, y);
         return true;
      }
      if (this.rawPress) {
         this.rawPress = false;
         PhoneApplicationManager.Running app = this.os.navigation().current();
         if (app != null && app.app() != null) {
            PhoneApplication a = app.app();
            int button = appButton(mcButton);
            this.os.apps().guard(app, "mouseReleased", () -> a.mouseReleased(x, y, button), false);
         }
         return true;
      }
      return false;
   }

   public boolean scroll(double gx, double gy, double amount) {
      if (!this.os.animation().interactive()) return true;
      float x = this.toPhoneX(gx), y = this.toPhoneY(gy);
      if (!this.inside(x, y)) return false;
      PhoneSystemUi sys = this.os.system();
      if (sys.shadeOpen()) {
         sys.shadeUi.wheel(x, y, amount);
         return true;
      }
      PhoneUi layer = this.layerUi();
      if (layer != null && layer.wheel(x, y, amount)) return true;
      if (this.os.navigation().layer() == PhoneNavigationManager.Layer.HOME) {
         if (y < 40 && amount > 0) sys.openShade();
         else this.os.home().wheel(amount);
         return true;
      }
      PhoneApplicationManager.Running app = this.os.navigation().current();
      if (app != null && app.app() != null) {
         PhoneApplication a = app.app();
         this.os.apps().guard(app, "mouseScrolled", () -> a.mouseScrolled(x, y, amount), false);
      }
      return true;
   }

   /** Keyboard. Returns false for keys the phone does not use. */
   public boolean key(int key, int modifiers) {
      PhoneUi layer = this.layerUi();
      if (layer != null && layer.typing()) {
         if (key == InputConstants.KEY_ESCAPE) {
            layer.focus(null);
            return true;
         }
         layer.key(key);
         return true;
      }
      if (key == InputConstants.KEY_ESCAPE) {
         if (this.os.system().shadeOpen()) {
            this.os.system().closeShade();
            return true;
         }
         this.os.close();
         return true;
      }
      if (key == InputConstants.KEY_BACKSPACE) {
         this.os.back();
         return true;
      }
      PhoneApplicationManager.Running app = this.os.navigation().current();
      if (app != null && app.app() != null) {
         PhoneApplication a = app.app();
         return this.os.apps().guard(app, "keyPressed", () -> a.keyPressed(key, modifiers), false);
      }
      return false;
   }

   public boolean chr(int codePoint) {
      PhoneUi layer = this.layerUi();
      if (layer != null && layer.typing()) return layer.chr(codePoint);
      PhoneApplicationManager.Running app = this.os.navigation().current();
      if (app != null && app.app() != null) {
         PhoneApplication a = app.app();
         return this.os.apps().guard(app, "charTyped", () -> a.charTyped(codePoint), false);
      }
      return false;
   }

   /** Is some text field taking the keyboard (then the chat key types a letter instead of opening chat)? */
   public boolean typing() {
      PhoneUi layer = this.layerUi();
      return layer != null && layer.typing();
   }

   @Nullable
   private PhoneUi layerUi() {
      if (this.os.locked()) return this.os.system().lockUi;
      return switch (this.os.navigation().layer()) {
         case HOME -> this.os.home().ui;
         case SWITCHER -> this.os.system().switcherUi;
         case APP -> {
            PhoneApplicationManager.Running r = this.os.navigation().current();
            yield r == null ? null : r.ui;
         }
      };
   }

   public void reset() {
      if (this.active != null) this.active.reset();
      this.active = null;
      this.rawPress = false;
   }
}
