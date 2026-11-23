package com.jfant.specter.phone.apps;

import com.jfant.specter.phone.api.AppCategory;
import com.jfant.specter.phone.api.AppInfo;
import com.jfant.specter.phone.api.AppPermission;
import com.jfant.specter.phone.core.PhoneApplicationRegistry;

/** The apps that ship with Donut Phone. Each is registered exactly like a third-party app would be. */
public final class BuiltinApps {
   private BuiltinApps() {
   }

   public static void register(PhoneApplicationRegistry registry) {
      String src = "specter";
      registry.register(AppInfo.builder("donutgram").name("DonutGram").version("1.0").category(AppCategory.SOCIAL).icon(AppIcons.DONUTGRAM)
         .description("Share what you see: photos from your game, gone after 24 hours. Likes count towards the monthly top three, who win Specter+ or Donut money. Posts also show in the Specter Discord.")
         .permissions(AppPermission.NETWORK, AppPermission.ACCOUNT, AppPermission.SCREENSHOTS, AppPermission.NOTIFICATIONS).order(100).background().accent(0xFFFF5CA8).build(),
         com.jfant.specter.phone.apps.gram.DonutGramApp::new, src);
      registry.register(AppInfo.builder("travel").name("DonutTravel").version("1.0").category(AppCategory.TRAVEL).icon(AppIcons.TRAVEL)
         .description("Change the whole look of the world: Tropical Paradise, Tokyo Night, Cyberpunk City, Sunset Beach, Arctic, Outer Space, Desert, Fantasy Forest. Return home puts every setting back.")
         .permissions(AppPermission.VISUALS, AppPermission.MODULES, AppPermission.NOTIFICATIONS).order(110).background().accent(0xFF0EA5E9).build(),
         com.jfant.specter.phone.apps.travel.TravelApp::new, src);
      registry.register(AppInfo.builder("maps").name("Maps").version("1.0").category(AppCategory.NAVIGATION).icon(AppIcons.MAPS)
         .description("A live map of the world around you, homes (/sethome and /home 1-99), waypoints per server, navigation with a beam and arrow, and waypoints shared through chat.")
         .permissions(AppPermission.WORLD, AppPermission.CHAT, AppPermission.COMMANDS, AppPermission.NOTIFICATIONS).order(120).background().accent(0xFF10B981).build(),
         com.jfant.specter.phone.apps.maps.MapsApp::new, src);
      registry.register(AppInfo.builder("games").name("Games").version("1.0").category(AppCategory.GAMES).icon(AppIcons.GAMES)
         .description("Snake, Minesweeper, Tic-Tac-Toe, Reaction Time and Memory Match, with your best scores.")
         .order(130).accent(0xFF7C3AED).build(), com.jfant.specter.phone.apps.games.GamesApp::new, src);
      registry.register(AppInfo.builder("specter").name("Specter").version("1.0").category(AppCategory.UTILITIES).icon(AppIcons.SPECTER)
         .description("Every Specter module: switch them on and off and change their settings, HUD elements and client preferences.")
         .permissions(AppPermission.MODULES).order(140).accent(0xFF8B5CF6).build(), SpecterApp::new, src);
      registry.register(AppInfo.builder("wallet").name("Wallet").version("1.0").category(AppCategory.FINANCE).icon(AppIcons.WALLET)
         .description("Pay a player (pick someone online, type 5M or 1.5B, confirm - it runs /pay), your balance from the sidebar, and your money history. Payments to you and bounties on you arrive as notifications.")
         .permissions(AppPermission.COMMANDS, AppPermission.NOTIFICATIONS).order(125).background().accent(0xFF16A34A).build(), WalletApp::new, src);
      registry.register(AppInfo.builder("profile").name("Profile").version("1.0").category(AppCategory.SOCIAL).icon(AppIcons.PROFILE)
         .description("Your Minecraft account, Specter+ membership (as confirmed by the Specter server), DonutGram numbers, statistics and achievements.")
         .permissions(AppPermission.ACCOUNT, AppPermission.NETWORK, AppPermission.NOTIFICATIONS).order(150).accent(0xFF3B82F6).build(), ProfileApp::new, src);
      registry.register(AppInfo.builder("settings").name("Settings").version("1.0").category(AppCategory.SYSTEM).icon(AppIcons.SETTINGS)
         .description("Theme, wallpaper, how the phone is held, notifications, and every installed app's permissions and settings.")
         .permissions(AppPermission.NOTIFICATIONS).order(900).system().accent(0xFF6B7280).build(), SettingsApp::new, src);
      registry.register(AppInfo.builder("chat").name("Chat").version("1.0").category(AppCategory.SOCIAL).icon(AppIcons.CHAT)
         .description("The server's chat on your phone. Reply or run /commands without putting the phone away.")
         .permissions(AppPermission.CHAT, AppPermission.COMMANDS).order(950).system().background().accent(0xFF22C55E).build(), ChatApp::new, src);
   }
}
