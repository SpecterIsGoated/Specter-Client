package com.jfant.specter.phone.api;

/** Where an app files itself (Settings lists apps by category; future app stores can filter by it). */
public enum AppCategory {
   SOCIAL("Social"),
   ENTERTAINMENT("Entertainment"),
   GAMES("Games"),
   TRAVEL("Travel"),
   NAVIGATION("Navigation"),
   PRODUCTIVITY("Productivity"),
   UTILITIES("Utilities"),
   FINANCE("Finance"),
   NEWS("News"),
   SYSTEM("System");

   public final String title;

   AppCategory(String title) {
      this.title = title;
   }
}
