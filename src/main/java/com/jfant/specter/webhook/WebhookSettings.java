package com.jfant.specter.webhook;

import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;

public final class WebhookSettings {
   private final String source;
   private final Setting<String> url;
   private final Setting<String> ping;

   public WebhookSettings(SettingGroup group, String source) {
      this.source = source;
      this.url = group.add(
         new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook address to send alerts to, so you hear about them away from the game. Leave blank to turn this off.")
            .defaultValue("")
            .placeholder("https://discord.com/api/webhooks/...")
            .wide()
            .build()
      );
      this.ping = group.add(
         new StringSetting.Builder()
            .name("webhook-ping")
            .description("Your Discord user ID, to be @mentioned in each alert so your phone buzzes. Leave blank for no mention.")
            .defaultValue("")
            .visible(() -> !this.url.get().isBlank())
            .build()
      );
   }

   public boolean enabled() {
      return Webhook.valid(this.url.get());
   }

   public void send(Webhook.Embed embed) {
      Webhook.send(this.url.get(), this.ping.get(), this.source, embed);
   }
}
