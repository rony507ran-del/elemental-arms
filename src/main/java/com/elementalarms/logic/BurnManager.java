package com.elementalarms.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Creatures hit by the Fire Gun burn until they die. The damage is dealt by this class
 * (not by vanilla fire), so water, fire resistance and fire immunity do not stop it.
 */
public final class BurnManager {
	/** Damage per hit. 1.0 = half a heart. */
	private static final float DAMAGE = 1.0F;
	/** Ticks between hits (10 = every half second, so 1 heart per second with the default damage). */
	private static final int DAMAGE_INTERVAL_TICKS = 10;

	/** Saved in the mob's data so it keeps burning after a chunk reload or server restart. */
	private static final String TAG_BURNING = "elementalarms_burning";

	private static final Map<UUID, Burn> BURNING = new HashMap<>();

	private static final class Burn {
		UUID owner;
		int age;

		Burn(UUID owner) {
			this.owner = owner;
		}
	}

	private BurnManager() {
	}

	public static void ignite(ServerPlayer owner, LivingEntity target) {
		Burn burn = BURNING.get(target.getUUID());
		if (burn == null) {
			BURNING.put(target.getUUID(), new Burn(owner.getUUID()));
		} else {
			burn.owner = owner.getUUID();
		}
		// Players are not tagged, so a burning player is not re-lit after respawning.
		if (!(target instanceof Player)) {
			target.addTag(TAG_BURNING);
		}
		target.igniteForSeconds(5.0F);
	}

	public static void onEntityLoad(Entity entity) {
		if (entity instanceof LivingEntity && !(entity instanceof Player)
			&& !BURNING.containsKey(entity.getUUID()) && entity.getTags().contains(TAG_BURNING)) {
			BURNING.put(entity.getUUID(), new Burn(null));
		}
	}

	public static void clear() {
		BURNING.clear();
	}

	public static void tick(MinecraftServer server) {
		if (BURNING.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Burn> entry : new ArrayList<>(BURNING.entrySet())) {
			UUID id = entry.getKey();
			Burn burn = entry.getValue();

			Entity found = EntityLookup.find(server, id);
			if (!(found instanceof LivingEntity target) || !target.isAlive()) {
				BURNING.remove(id); // dead, gone, or unloaded (the tag restores it on reload)
				continue;
			}
			if (target instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())) {
				BURNING.remove(id);
				continue;
			}
			if (!(target.level() instanceof ServerLevel level)) {
				BURNING.remove(id);
				continue;
			}

			burn.age++;

			if (burn.age % 4 == 0) {
				Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
				BeamUtil.burst(level, ParticleTypes.FLAME, center, 2, target.getBbWidth() * 0.35, 0.01);
			}
			if (burn.age % 20 == 0 && !target.isOnFire()) {
				target.igniteForSeconds(5.0F); // keeps the vanilla burning look when possible
			}
			if (burn.age % DAMAGE_INTERVAL_TICKS == 0) {
				hurt(server, level, target, burn.owner);
			}
		}
	}

	private static void hurt(MinecraftServer server, ServerLevel level, LivingEntity target, UUID ownerId) {
		// Credit the shooter so kills give XP and drops that need a player kill (blaze rods, etc.).
		ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);

		var damageTypes = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
		DamageSource fire = new DamageSource(damageTypes.getOrThrow(DamageTypes.ON_FIRE), null, owner);
		if (!target.hurtServer(level, fire, DAMAGE) && target.isAlive()) {
			// Fire-immune creatures (blaze, wither skeleton...) or fire resistance: use magic damage instead.
			DamageSource magic = new DamageSource(damageTypes.getOrThrow(DamageTypes.MAGIC), null, owner);
			target.hurtServer(level, magic, DAMAGE);
		}
	}
}
