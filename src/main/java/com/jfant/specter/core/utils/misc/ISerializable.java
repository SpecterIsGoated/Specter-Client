package com.jfant.specter.core.utils.misc;

import net.minecraft.nbt.CompoundTag;

public interface ISerializable<T> {
   CompoundTag toTag();

   T fromTag(CompoundTag var1);
}
