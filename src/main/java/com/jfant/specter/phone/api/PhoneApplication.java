package com.jfant.specter.phone.api;

import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import java.util.List;

/**
 * Base class of every Donut Phone app.
 *
 * <p>Making a new app is three steps: extend this class and implement {@link #render}, describe it with an
 * {@link AppInfo}, and register both with {@code PhoneApps.register(info, MyApp::new)} (from Specter's start-up or a
 * mod's {@code specter:phone} entrypoint). The phone then shows the icon, opens and closes the app, routes input,
 * keeps its data, and isolates it: an exception from any method below closes just that app with an error card.
 *
 * <p>Coordinates are phone points: the screen is {@link PhoneCanvas#width} x {@link PhoneCanvas#height} (180 x 392),
 * origin top left. The status bar covers the top {@link PhoneCanvas#SAFE_TOP} points and the navigation bar the
 * bottom {@link PhoneCanvas#SAFE_BOTTOM}; draw your background under them and keep content between.
 *
 * <p>Most apps never override the mouse methods: widgets from {@link PhoneUi} (buttons, lists, scroll views, text
 * fields) register their own clicks while drawing. The raw input methods are for games and custom controls; they only
 * see input that no widget took.
 *
 * <p>Lifecycle: {@link #onCreate} once, then {@link #onOpen}/{@link #onClose} each time the app comes to the front or
 * leaves it (going home, switching apps, putting the phone away), and {@link #onDestroy} when the phone drops the app
 * (closed from the app switcher, crashed, or the game left the world). Release textures and stop network work there.
 */
public abstract class PhoneApplication {
   private PhoneContext phone;

   /** Called by the phone before {@link #onCreate}. */
   public final void attach(PhoneContext context) {
      if (this.phone != null) throw new IllegalStateException("already attached");
      this.phone = context;
   }

   /** The phone, for notifications, storage, navigation, theme. */
   protected final PhoneContext phone() {
      return this.phone;
   }

   protected void onCreate() {
   }

   protected void onOpen() {
   }

   protected void onClose() {
   }

   protected void onDestroy() {
   }

   /**
    * The app was opened from a notification or by another app with a route (for example {@code post/123}). Called
    * after {@link #onOpen}. Unknown routes should be ignored.
    */
   protected void onRoute(String route) {
   }

   /** 20 times a second while the app is in front and the phone is out. */
   public void tick() {
   }

   /** 20 times a second at all times, only for apps whose {@link AppInfo#background()} is set. Keep it cheap. */
   public void backgroundTick() {
   }

   /** Draws one frame. {@code mouseX/Y} are in phone points (negative or past the edges when the mouse is off the screen). */
   public abstract void render(PhoneCanvas c, PhoneUi ui, float mouseX, float mouseY, float delta);

   public boolean mouseClicked(float x, float y, int button) {
      return false;
   }

   public boolean mouseReleased(float x, float y, int button) {
      return false;
   }

   public boolean mouseDragged(float x, float y, int button, float dx, float dy) {
      return false;
   }

   public boolean mouseScrolled(float x, float y, double amount) {
      return false;
   }

   /** A key press nobody else took (GLFW/SDL key codes as in {@code InputConstants}). */
   public boolean keyPressed(int key, int modifiers) {
      return false;
   }

   public boolean charTyped(int codePoint) {
      return false;
   }

   /** The back button: go back inside the app and return true, or return false to let the phone go home. */
   public boolean back() {
      return false;
   }

   /** True when the status bar should draw dark text (the app's top is light). */
   public boolean lightTop() {
      return false;
   }

   /** Settings shown under this app in the phone's Settings app. */
   public List<AppSetting<?>> settings() {
      return List.of();
   }
}
