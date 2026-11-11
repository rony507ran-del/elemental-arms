package com.elementalarms.logic;

import com.elementalarms.item.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Keeps track of players who are currently hanging on a grappling hook and pulls them. */
public final class GrappleManager {
	/** Maximum distance the hook can reach, in blocks. */
	public static final double RANGE = 48.0;

	/** Pull speed in blocks per tick (1.15 = about 23 blocks per second). */
	private static final double PULL_SPEED = 1.15;
	/** How fast the player reaches the pull speed (0..1, higher = snappier). */
	private static final double ACCELERATION = 0.35;
	/** The hook lets go when the player is this close to the anchor. */
	private static final double ARRIVE_DISTANCE = 1.3;
	/** Safety limit: the hook lets go after this many ticks (160 = 8 seconds). */
	private static final int MAX_TICKS = 160;
	/** After letting go the player takes no fall damage for this long (40 = 2 seconds). */
	private static final int NO_FALL_GRACE_TICKS = 40;

	private static final Map<UUID, Grapple> ACTIVE = new HashMap<>();
	private static final Map<UUID, Integer> NO_FALL = new HashMap<>();

	private static final class Grapple {
		final Vec3 anchor;
		Vec3 velocity;
		Vec3 lastPos;
		int age;
		int stuck;

		Grapple(Vec3 anchor, Vec3 velocity) {
			this.anchor = anchor;
			this.velocity = velocity;
		}
	}

	private GrappleManager() {
	}

	public static boolean isActive(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	public static void attach(ServerPlayer player, Vec3 anchor) {
		ACTIVE.put(player.getUUID(), new Grapple(anchor, new Vec3(0.0, 0.3, 0.0)));
		NO_FALL.remove(player.getUUID());
		player.fallDistance = 0.0;
	}

	public static void release(ServerPlayer player) {
		if (ACTIVE.remove(player.getUUID()) != null) {
			NO_FALL.put(player.getUUID(), NO_FALL_GRACE_TICKS);
			if (player.level() instanceof ServerLevel level) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8F, 1.2F);
			}
		}
	}

	public static void clear() {
		ACTIVE.clear();
		NO_FALL.clear();
	}

	public static void tick(MinecraftServer server) {
		if (!ACTIVE.isEmpty()) {
			for (Map.Entry<UUID, Grapple> entry : new ArrayList<>(ACTIVE.entrySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) {
					ACTIVE.remove(entry.getKey());
					continue;
				}
				if (!pullStep(player, entry.getValue())) {
					release(player);
				}
			}
		}

		if (!NO_FALL.isEmpty()) {
			for (Map.Entry<UUID, Integer> entry : new ArrayList<>(NO_FALL.entrySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				int left = entry.getValue() - 1;
				if (player == null || left <= 0) {
					NO_FALL.remove(entry.getKey());
					continue;
				}
				player.fallDistance = 0.0;
				NO_FALL.put(entry.getKey(), left);
			}
		}
	}

	/** Moves the player one tick closer to the anchor. Returns false when the hook should let go. */
	private static boolean pullStep(ServerPlayer player, Grapple grapple) {
		if (!player.isAlive() || player.isPassenger() || !holdsHook(player) || grapple.age >= MAX_TICKS) {
			return false;
		}
		grapple.age++;

		Vec3 body = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 toAnchor = grapple.anchor.subtract(body);
		double distance = toAnchor.length();
		if (distance < ARRIVE_DISTANCE) {
			return false;
		}
		// Sneak to let go.
		if (player.isShiftKeyDown() && grapple.age > 4) {
			return false;
		}

		// Detect being blocked by something.
		Vec3 here = player.position();
		if (grapple.lastPos != null && here.distanceToSqr(grapple.lastPos) < 0.0009) {
			grapple.stuck++;
		} else {
			grapple.stuck = 0;
		}
		grapple.lastPos = here;
		if ((grapple.stuck >= 3 && distance < 2.5) || grapple.stuck > 12) {
			return false;
		}

		Vec3 desired = toAnchor.scale(PULL_SPEED / distance);
		grapple.velocity = grapple.velocity.lerp(desired, ACCELERATION);
		Vec3 push = grapple.velocity;
		// Blocked while the anchor is above us: hop up so we can climb over ledges.
		if (grapple.stuck >= 2 && toAnchor.y > 0.5) {
			push = new Vec3(push.x, Math.max(push.y, 0.45), push.z);
		}

		player.setDeltaMovement(push);
		player.hurtMarked = true; // makes the server send the new velocity to the player's client
		player.fallDistance = 0.0;

		// The rope.
		if (grapple.age % 3 == 0 && player.level() instanceof ServerLevel level) {
			BeamUtil.drawLine(level, ParticleTypes.CRIT, BeamUtil.muzzle(player), grapple.anchor, 1.0);
		}
		return true;
	}

	private static boolean holdsHook(ServerPlayer player) {
		return player.getMainHandItem().is(ModItems.GRAPPLING_HOOK)
			|| player.getOffhandItem().is(ModItems.GRAPPLING_HOOK);
	}
}
