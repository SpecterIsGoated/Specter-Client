package com.jfant.specter.phone.core;

/**
 * Posted on Specter's event bus when you send a server command (typed in chat or run by anything else), without the
 * slash: {@code sethome 2}. Donut Maps uses it to remember where you set homes. Read-only.
 */
public final class PhoneCommandEvent {
   private static final PhoneCommandEvent INSTANCE = new PhoneCommandEvent();
   public String command;

   private PhoneCommandEvent() {
   }

   public static PhoneCommandEvent get(String command) {
      INSTANCE.command = command;
      return INSTANCE;
   }
}
