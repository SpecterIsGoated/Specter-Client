package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.util.Leaving;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class FakeBanScreen extends Module {
   private static final List<String> TEMP_BAN = List.of(
      "&c{headline}", "", "&7Time Left: &f{time}", "", "&7Ban ID: &f{id}", "&7You may be able to appeal this ban on", "&fsupport.donutsmp.net"
   );
   private static final List<String> ACCOUNT_HOLD = List.of(
      "&f&lYour account has been placed on hold.",
      "&7We need to chat with you about something.",
      "",
      "&7Please open a ticket in the Donut SMP Discord",
      "&7and we'll get you back as soon as possible!",
      "",
      "&ediscord.gg/donutsmp",
      "",
      "&7Date: &f{date}",
      "",
      "&7Ban ID: &f{id}",
      "&7You may be able to appeal this ban on",
      "&fdiscord.gg/donutsmp"
   );
   private static final int TRAILING_BLANKS = 3;
   private static final String ID_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
   private static final int ID_LENGTH = 8;
   private static final String[] TIME_UNITS = new String[]{" day", " hours", " minutes"};
   private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
   private static final SecureRandom RANDOM = new SecureRandom();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<FakeBanScreen.Screen> screen = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("screen"))
                  .description("The kind of fake screen you get."))
               .defaultValue(FakeBanScreen.Screen.Esp))
            .build()
      );
   private final Setting<Boolean> secondsOnly = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("seconds-only")
            .description("Give the time left in seconds instead of days, hours and minutes.")
            .defaultValue(false)
            .visible(this::tempBan)
            .build()
      );
   private final Setting<Integer> days = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("days")
            .description("Days to go on the ban.")
            .defaultValue(30)
            .min(0)
            .sliderMax(100)
            .visible(() -> this.tempBan() && !this.secondsOnly.get())
            .build()
      );
   private final Setting<Integer> hours = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("hours")
            .description("Hours to go on top of the days.")
            .defaultValue(0)
            .range(0, 23)
            .sliderRange(0, 23)
            .visible(() -> this.tempBan() && !this.secondsOnly.get())
            .build()
      );
   private final Setting<Integer> minutes = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("minutes")
            .description("Minutes to go on top of the hours.")
            .defaultValue(0)
            .range(0, 59)
            .sliderRange(0, 59)
            .visible(() -> this.tempBan() && !this.secondsOnly.get())
            .build()
      );
   private final Setting<Integer> seconds = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("seconds")
            .description("Seconds to go.")
            .defaultValue(30)
            .range(0, 59)
            .sliderRange(0, 59)
            .visible(() -> this.tempBan() && this.secondsOnly.get())
            .build()
      );
   private final Setting<String> banId = this.sgGeneral
      .add(new StringSetting.Builder().name("ban-id").description("The ban ID shown. Blank makes up a new one each time.").defaultValue("").build());
   private final Setting<String> date = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("date")
            .description("The date on the hold screen. Blank uses today.")
            .defaultValue("")
            .visible(() -> this.screen.get() == FakeBanScreen.Screen.Hold)
            .build()
      );

   public FakeBanScreen() {
      super(SpecterClientAddon.DONUT, "fake-ban-screen", "Drops you from the server onto a fake ban screen. Only on your side; the server is never told.");
   }

   private boolean tempBan() {
      return this.screen.get() != FakeBanScreen.Screen.Hold;
   }

   @Override
   public void onActivate() {
      this.toggle();
      if (this.mc.getConnection() == null) {
         this.error("Join a server first.", new Object[0]);
      } else {
         Leaving.onPurpose("Fake Ban Screen");
         this.mc.getConnection().getConnection().disconnect(this.message());
      }
   }

   private Component message() {
      List<String> template = switch ((FakeBanScreen.Screen)this.screen.get()) {
         case Esp, Cheating -> TEMP_BAN;
         case Hold -> ACCOUNT_HOLD;
      };
      String headline = this.screen.get() == FakeBanScreen.Screen.Esp
         ? "You are temporarily banned for the use of ESP."
         : "You are temporarily banned for cheating.";
      String time = this.timeLeft();
      String id = this.id();
      String shownDate = this.shownDate();
      MutableComponent out = Component.empty();
      int line = 0;

      for (String row : template) {
         String filled = row.replace('&', '§').replace("{headline}", headline).replace("{time}", time).replace("{id}", id).replace("{date}", shownDate);
         if (line++ > 0) {
            out.append("\n");
         }

         out.append(Component.literal(filled));
      }

      for (int i = 0; i < 3; i++) {
         out.append("\n").append(Component.literal(" "));
      }

      return out;
   }

   private String timeLeft() {
      if (this.secondsOnly.get()) {
         return this.seconds.get() + " seconds";
      } else {
         int[] amounts = new int[]{this.days.get(), this.hours.get(), this.minutes.get()};
         StringJoiner parts = new StringJoiner(" ");

         for (int unit = 0; unit < amounts.length; unit++) {
            if (amounts[unit] != 0) {
               parts.add(amounts[unit] + TIME_UNITS[unit]);
            }
         }

         return parts.length() == 0 ? "0" + TIME_UNITS[2] : parts.toString();
      }
   }

   private String id() {
      String given = this.banId.get().strip();
      if (given.isEmpty()) {
         char[] made = new char[8];

         for (int i = 0; i < made.length; i++) {
            made[i] = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
               .charAt(RANDOM.nextInt("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".length()));
         }

         given = new String(made);
      }

      return given.charAt(0) == '#' ? given : "#" + given;
   }

   private String shownDate() {
      String given = this.date.get().strip();
      return given.isEmpty() ? DATE_FORMAT.format(LocalDate.now()) : given;
   }

   private static enum Screen {
      Esp("ESP ban"),
      Cheating("Cheating ban"),
      Hold("Account hold");

      private final String title;

      private Screen(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
