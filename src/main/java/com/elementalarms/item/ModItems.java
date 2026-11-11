package com.elementalarms.item;

import com.elementalarms.ElementalArms;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item GRAPPLING_HOOK = register("grappling_hook", GrapplingHookItem::new, Rarity.UNCOMMON);
	public static final Item FIRE_GUN = register("fire_gun", FireGunItem::new, Rarity.RARE);
	public static final Item ICE_GUN = register("ice_gun", IceGunItem::new, Rarity.RARE);

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ElementalArms.id(name));
		Item item = factory.apply(new Item.Properties().setId(key).stacksTo(1).rarity(rarity));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** Calling this loads the class, which registers the items. */
	public static void init() {
	}
}
