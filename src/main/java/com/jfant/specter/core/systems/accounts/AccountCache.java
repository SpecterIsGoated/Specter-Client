package com.jfant.specter.core.systems.accounts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.misc.ISerializable;
import com.jfant.specter.core.utils.misc.NbtException;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import com.jfant.specter.core.utils.render.PlayerHeadTexture;
import com.jfant.specter.core.utils.render.PlayerHeadUtils;
import com.mojang.util.UndashedUuid;
import net.minecraft.nbt.CompoundTag;

public class AccountCache implements ISerializable<AccountCache> {
   public String username = "";
   public String uuid = "";
   private PlayerHeadTexture headTexture;
   private volatile boolean loadingHead;

   public PlayerHeadTexture getHeadTexture() {
      return this.headTexture != null ? this.headTexture : PlayerHeadUtils.STEVE_HEAD;
   }

   public void loadHead() {
      this.loadHead(null);
   }

   public void loadHead(Runnable callback) {
      if (this.headTexture == null && this.uuid != null && !this.uuid.isBlank()) {
         if (!this.loadingHead) {
            this.loadingHead = true;
            MeteorExecutor.execute(() -> {
               byte[] head = PlayerHeadUtils.fetchHead(UndashedUuid.fromStringLenient(this.uuid));
               SpecterCore.mc.execute(() -> {
                  if (head != null) {
                     this.headTexture = new PlayerHeadTexture(head, true);
                  }

                  this.loadingHead = false;
                  if (callback != null) {
                     callback.run();
                  }
               });
            });
         }
      } else {
         if (callback != null) {
            SpecterCore.mc.execute(callback);
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("username", this.username);
      tag.putString("uuid", this.uuid);
      return tag;
   }

   public AccountCache fromTag(CompoundTag tag) {
      if (!tag.getString("username").isEmpty() && !tag.getString("uuid").isEmpty()) {
         this.username = tag.getString("username").get();
         this.uuid = tag.getString("uuid").get();
         this.loadHead();
         return this;
      } else {
         throw new NbtException();
      }
   }
}
