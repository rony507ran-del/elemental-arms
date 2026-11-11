package com.elementalarms;

import com.elementalarms.item.ModItems;
import com.elementalarms.logic.BurnManager;
import com.elementalarms.logic.FreezeManager;
import com.elementalarms.logic.GrappleManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElementalArms implements ModInitializer {
	public static final String MOD_ID = "elementalarms";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();

		// Put the three weapons in the Combat creative tab.
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
			entries.accept(ModItems.GRAPPLING_HOOK);
			entries.accept(ModItems.FIRE_GUN);
			entries.accept(ModItems.ICE_GUN);
		});

		// Everything that lasts more than one tick (rope pull, burning, freezing) runs from here.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GrappleManager.tick(server);
			BurnManager.tick(server);
			FreezeManager.tick(server);
		});

		// Mobs keep their burning / frozen state across chunk reloads and server restarts.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			BurnManager.onEntityLoad(entity);
			FreezeManager.onEntityLoad(entity);
		});

		// Never leave a mob without AI when the world is saved.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FreezeManager.thawAll(server);
			BurnManager.clear();
			GrappleManager.clear();
		});

		LOGGER.info("Elemental Arms loaded: Grappling Hook, Fire Gun, Ice Gun");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
