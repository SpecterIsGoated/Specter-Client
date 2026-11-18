package com.jfant.specter.core.settings;

import com.jfant.specter.core.utils.misc.IChangeable;
import com.jfant.specter.core.utils.misc.ICopyable;
import com.jfant.specter.core.utils.misc.IGetter;
import com.jfant.specter.core.utils.misc.ISerializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public class BlockDataSetting<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> extends Setting<Map<Block, T>> {
   public final IGetter<T> defaultData;

   public BlockDataSetting(
      String name,
      String description,
      Map<Block, T> defaultValue,
      Consumer<Map<Block, T>> onChanged,
      Consumer<Setting<Map<Block, T>>> onModuleActivated,
      IGetter<T> defaultData,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.defaultData = defaultData;
   }

   @Override
   public void resetImpl() {
      this.value = new HashMap<>(this.defaultValue);
   }

   protected Map<Block, T> parseImpl(String str) {
      return new HashMap<>(0);
   }

   protected boolean isValueValid(Map<Block, T> value) {
      return true;
   }

   @Override
   protected CompoundTag save(CompoundTag tag) {
      CompoundTag valueTag = new CompoundTag();

      for (Block block : this.get().keySet()) {
         valueTag.put(BuiltInRegistries.BLOCK.getKey(block).toString(), this.get().get(block).toTag());
      }

      tag.put("value", valueTag);
      return tag;
   }

   protected Map<Block, T> load(CompoundTag tag) {
      this.get().clear();
      CompoundTag valueTag = tag.getCompoundOrEmpty("value");

      for (String key : valueTag.keySet()) {
         this.get().put(BuiltInRegistries.BLOCK.getValue(Identifier.parse(key)), this.defaultData.get().copy().fromTag(valueTag.getCompoundOrEmpty(key)));
      }

      return this.get();
   }

   public static class Builder<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>>
      extends Setting.SettingBuilder<BlockDataSetting.Builder<T>, Map<Block, T>, BlockDataSetting<T>> {
      private IGetter<T> defaultData;

      public Builder() {
         super(new HashMap<>(0));
      }

      public BlockDataSetting.Builder<T> defaultData(IGetter<T> defaultData) {
         this.defaultData = defaultData;
         return this;
      }

      public BlockDataSetting<T> build() {
         return new BlockDataSetting<>(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.defaultData, this.visible);
      }
   }
}
