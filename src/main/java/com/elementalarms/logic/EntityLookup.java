package com.elementalarms.logic;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Finds a loaded entity by UUID in any dimension. Returns null when it is not loaded. */
final class EntityLookup {
	private EntityLookup() {
	}

	static Entity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}
}
