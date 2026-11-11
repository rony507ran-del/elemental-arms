package com.elementalarms.logic;

import com.elementalarms.ElementalArms;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Creatures hit by the Ice Gun are frozen in place for 5 seconds.
 * Mobs lose their AI (so they cannot move, attack or shoot) and are pinned to their spot.
 * Players cannot walk or jump and are pulled back if they somehow drift away.
 */
public final class FreezeManager {
	/** How long the freeze lasts, in ticks (100 = 5 seconds). */
	public static final int FREEZE_TICKS = 100;

	/** Saved in the mob's data so a crash can never leave it without AI forever. */
	private static final String TAG_FROZEN = "elementalarms_frozen";
	private static final String TAG_WAS_NOAI = "elementalarms_was_noai";

	private static final Identifier LOCK_ID = ElementalArms.id("freeze_lock");

	private static final Map<UUID, Freeze> FROZEN = new HashMap<>();

	private static final class Freeze {
		final Vec3 pos;
		final ResourceKey<Level> dimension;
		final boolean wasNoAi;
		int ticksLeft;

		Freeze(Vec3 pos, ResourceKey<Level> dimension, boolean wasNoAi) {
			this.pos = pos;
			this.dimension = dimension;
			this.wasNoAi = wasNoAi;
			this.ticksLeft = FREEZE_TICKS;
		}
	}

	private FreezeManager() {
	}

	public static void freeze(LivingEntity target) {
		Freeze existing = FROZEN.get(target.getUUID());
		if (existing != null) {
			existing.ticksLeft = FREEZE_TICKS; // shot again: restart the 5 seconds
			return;
		}

		boolean wasNoAi = false;
		if (target instanceof Mob mob) {
			wasNoAi = mob.isNoAi();
			if (wasNoAi) {
				mob.addTag(TAG_WAS_NOAI);
			}
			mob.addTag(TAG_FROZEN);
			mob.getNavigation().stop();
			mob.setNoAi(true);
		} else if (target instanceof ServerPlayer player) {
			lockPlayer(player);
		}
		FROZEN.put(target.getUUID(), new Freeze(target.position(), target.level().dimension(), wasNoAi));
	}

	/** A frozen mob that was saved to disk (crash, chunk unload) is thawed when it loads again. */
	public static void onEntityLoad(Entity entity) {
		if (entity instanceof Mob mob && !FROZEN.containsKey(mob.getUUID()) && mob.getTags().contains(TAG_FROZEN)) {
			release(mob, mob.getTags().contains(TAG_WAS_NOAI));
		}
	}

	public static void thawAll(MinecraftServer server) {
		for (Map.Entry<UUID, Freeze> entry : new ArrayList<>(FROZEN.entrySet())) {
			Entity found = EntityLookup.find(server, entry.getKey());
			if (found instanceof LivingEntity target) {
				release(target, entry.getValue().wasNoAi);
			}
		}
		FROZEN.clear();
	}

	public static void tick(MinecraftServer server) {
		if (FROZEN.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Freeze> entry : new ArrayList<>(FROZEN.entrySet())) {
			UUID id = entry.getKey();
			Freeze freeze = entry.getValue();

			Entity found = EntityLookup.find(server, id);
			if (!(found instanceof LivingEntity target) || !target.isAlive()) {
				FROZEN.remove(id); // dead, gone, or unloaded (the tag thaws it on reload)
				continue;
			}
			if (!(target.level() instanceof ServerLevel level)) {
				FROZEN.remove(id);
				continue;
			}
			if (level.dimension() != freeze.dimension) {
				// Changed dimension while frozen: just thaw it.
				thaw(level, target, freeze);
				FROZEN.remove(id);
				continue;
			}

			freeze.ticksLeft--;
			if (freeze.ticksLeft <= 0) {
				thaw(level, target, freeze);
				FROZEN.remove(id);
				continue;
			}
			hold(level, target, freeze);
		}
	}

	/** Keeps a frozen creature perfectly still. */
	private static void hold(ServerLevel level, LivingEntity target, Freeze freeze) {
		target.setDeltaMovement(Vec3.ZERO);

		if (target instanceof ServerPlayer player) {
			// Movement is already zeroed by the attribute lock; this catches falling or being pushed.
			if (player.position().distanceToSqr(freeze.pos) > 0.09) {
				player.connection.teleport(freeze.pos.x, freeze.pos.y, freeze.pos.z, player.getYRot(), player.getXRot());
			}
			player.hurtMarked = true;
		} else if (target.position().distanceToSqr(freeze.pos) > 1.0E-6) {
			target.setPos(freeze.pos.x, freeze.pos.y, freeze.pos.z);
		}

		if (freeze.ticksLeft % 3 == 0) {
			Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			BeamUtil.burst(level, ParticleTypes.SNOWFLAKE, center, 4, target.getBbWidth() * 0.5, 0.02);
		}
	}

	private static void thaw(ServerLevel level, LivingEntity target, Freeze freeze) {
		release(target, freeze.wasNoAi);
		Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		BeamUtil.burst(level, ParticleTypes.SNOWFLAKE, center, 25, target.getBbWidth() * 0.6, 0.1);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
			SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.8F, 1.2F);
	}

	private static void release(LivingEntity target, boolean wasNoAi) {
		if (target instanceof Mob mob) {
			if (!wasNoAi) {
				mob.setNoAi(false);
			}
			mob.removeTag(TAG_FROZEN);
			mob.removeTag(TAG_WAS_NOAI);
		} else if (target instanceof ServerPlayer player) {
			unlockPlayer(player);
		}
	}

	private static void lockPlayer(ServerPlayer player) {
		applyLock(player, Attributes.MOVEMENT_SPEED);
		applyLock(player, Attributes.JUMP_STRENGTH);
	}

	private static void unlockPlayer(ServerPlayer player) {
		removeLock(player, Attributes.MOVEMENT_SPEED);
		removeLock(player, Attributes.JUMP_STRENGTH);
	}

	/** -100% multiplier: walk speed / jump strength become 0. */
	private static void applyLock(ServerPlayer player, Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(LOCK_ID);
			instance.addTransientModifier(
				new AttributeModifier(LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void removeLock(ServerPlayer player, Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(LOCK_ID);
		}
	}
}
