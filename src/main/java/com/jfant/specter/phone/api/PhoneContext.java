package com.jfant.specter.phone.api;

import com.google.gson.JsonObject;
import com.jfant.specter.phone.core.PhoneTheme;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * The phone as an app sees it. Every app gets its own context (so notifications, storage and permission checks know
 * which app is asking). Methods that need a permission do nothing (and log once) if the app did not declare it.
 */
public interface PhoneContext {
   AppInfo info();

   Minecraft mc();

   /** The active theme: colours, radii and type sizes every app should use. */
   PhoneTheme theme();

   /** Seconds since the game started, for animations (smooth, never jumps). */
   double time();

   /** Posts a notification from this app (needs {@link AppPermission#NOTIFICATIONS}). */
   void notify(String title, String text);

   /** Posts a notification that opens this app with {@code route} when tapped (passed to {@link PhoneApplication#onRoute}). */
   void notify(String title, String text, String route);

   /**
    * Posts a notification with buttons (needs NOTIFICATIONS, and COMMANDS for the buttons, which run server commands
    * when pressed). The buttons work for {@code actionsMillis}.
    */
   void notify(String title, String text, String route, java.util.List<NotificationAction> actions, long actionsMillis);

   /** A short message at the bottom of the phone screen, gone after a moment. */
   void toast(String text);

   void openApp(String appId);

   void openApp(String appId, String route);

   void goHome();

   void closePhone();

   /** Puts the phone away and opens the chat box with {@code text} typed in (needs {@link AppPermission#CHAT}). */
   void openChat(String text);

   /** Sends a chat command such as {@code home 1} (no slash) - only call this from a button press (needs COMMANDS). */
   boolean runCommand(String command);

   /** This app's own data (a JSON object kept in {@code specter/phone/apps/<id>.json}). Call {@link #saveStorage()} after changing it. */
   JsonObject storage();

   void saveStorage();

   /** A folder for this app's larger files (caches, downloads). */
   Path dataDir();

   void sound(PhoneSound sound);

   /** Runs on the game thread (use when a network callback wants to change app state). */
   void runOnMain(Runnable task);
}
