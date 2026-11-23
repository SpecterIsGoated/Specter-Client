package com.jfant.specter.phone.api;

/** Internal: lets the phone call an app's protected lifecycle methods. Apps never use this. */
public final class Hooks {
   private Hooks() {
   }

   public static void create(PhoneApplication app) {
      if (app != null) app.onCreate();
   }

   public static void open(PhoneApplication app) {
      if (app != null) app.onOpen();
   }

   public static void close(PhoneApplication app) {
      if (app != null) app.onClose();
   }

   public static void destroy(PhoneApplication app) {
      if (app != null) app.onDestroy();
   }

   public static void route(PhoneApplication app, String route) {
      if (app != null) app.onRoute(route);
   }
}
