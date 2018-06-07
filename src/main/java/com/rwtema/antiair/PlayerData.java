package com.rwtema.antiair;

import gnu.trove.set.hash.TIntHashSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

public class PlayerData {
	int flightTime = 0;
	double prevHeight = 0;
	TIntHashSet entities = new TIntHashSet();


	public NBTTagCompound serializeNBT() {
		NBTTagCompound tag = new NBTTagCompound();
		NBTTagList list = new NBTTagList();
		entities.forEach(n -> {
			list.appendTag(new NBTTagInt(n));
			return true;
		});
		tag.setTag("entities", list);
		return tag;
	}


	public void deserializeNBT(NBTTagCompound tag) {
		entities.clear();
		NBTTagList list = tag.getTagList("entities", Constants.NBT.TAG_INT);
		for (int i = 0; i < list.tagCount(); i++) {
			entities.add(list.getIntAt(i));
		}
	}
}
