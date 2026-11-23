package com.jfant.specter.phone.api;

/**
 * What an app declares it uses. The phone shows these in Settings, and the {@link PhoneContext} methods that touch
 * them refuse apps that did not declare them (a refused call is logged and does nothing). Java code is not sandboxed,
 * so this keeps honest apps honest rather than stopping hostile ones.
 */
public enum AppPermission {
   NOTIFICATIONS("Notifications", "Post notifications and badges."),
   NETWORK("Internet", "Talk to online services."),
   ACCOUNT("Minecraft account", "Prove which Minecraft account you are signed in with (never your password or token)."),
   CHAT("Chat", "Open the chat box or put text in it."),
   COMMANDS("Commands", "Run server commands for you (only after you press a button)."),
   MODULES("Specter modules", "Turn Specter modules on and off and change their settings."),
   WORLD("World info", "Read your position and what is around you."),
   SCREENSHOTS("Screenshots", "Take screenshots and read your screenshots folder."),
   VISUALS("Visuals", "Change how the sky and world look.");

   public final String title;
   public final String about;

   AppPermission(String title, String about) {
      this.title = title;
      this.about = about;
   }
}
