package com.larsvansoest.runelite.clueitems.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;

public class ItemsProvider
{
	private HashSet<Integer> beginnerItems;
	private HashSet<Integer> easyItems;
	private HashSet<Integer> mediumItems;
	private HashSet<Integer> hardItems;
	private HashSet<Integer> eliteItems;
	private HashSet<Integer> masterItems;

	public void loadItems()
	{
		this.beginnerItems = this.toHashSet(Items.Beginner.ids);
		this.easyItems = this.toHashSet(Items.Easy.ids);
		this.mediumItems = this.toHashSet(Items.Medium.ids);
		this.hardItems = this.toHashSet(Items.Hard.ids);
		this.eliteItems = this.toHashSet(Items.Elite.ids);
		this.masterItems = this.toHashSet(Items.Master.ids);
	}

	private HashSet<Integer> toHashSet(int[] ids)
	{
		return Arrays.stream(ArrayUtils.toObject(ids)).collect(Collectors.toCollection(HashSet::new));
	}

	public HashSet<Integer> getBeginnerItems()
	{
		return beginnerItems;
	}

	public HashSet<Integer> getEasyItems()
	{
		return easyItems;
	}

	public HashSet<Integer> getMediumItems()
	{
		return mediumItems;
	}

	public HashSet<Integer> getHardItems()
	{
		return hardItems;
	}

	public HashSet<Integer> getEliteItems()
	{
		return eliteItems;
	}

	public HashSet<Integer> getMasterItems()
	{
		return masterItems;
	}
}
