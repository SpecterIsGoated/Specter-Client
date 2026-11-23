package com.jfant.specter.phone.api;

/**
 * A button on a notification (lock screen, banner, notification shade): pressing it runs {@code command} (no slash)
 * on the server, e.g. {@code tpaccept Steve}. Only apps with {@link AppPermission#COMMANDS} can attach actions, and
 * actions disappear after their notification's time is up (a teleport request only lasts a minute or two).
 */
public record NotificationAction(String label, String command, boolean primary) {
   public static NotificationAction primary(String label, String command) {
      return new NotificationAction(label, command, true);
   }

   public static NotificationAction secondary(String label, String command) {
      return new NotificationAction(label, command, false);
   }
}
