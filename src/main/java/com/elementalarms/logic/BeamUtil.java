package com.elementalarms.logic;

import java.util.Optional;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PartEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Instant ("hitscan") shot helpers shared by the guns and the grappling hook. */
public final class BeamUtil {
	private BeamUtil() {
	}

	/** Result of a shot. {@code target} is null when nothing living was hit. */
	public record Shot(Vec3 start, Vec3 end, LivingEntity target) {
	}

	/**
	 * Fires a ray from the shooter's eyes. The ray stops at the first solid block,
	 * and returns the first living creature on the way (if any).
	 */
	public static Shot trace(ServerLevel level, Player shooter, double range) {
		Vec3 start = shooter.getEyePosition();
		Vec3 farEnd = start.add(shooter.getLookAngle().scale(range));

		BlockHitResult blockHit = level.clip(
			new ClipContext(start, farEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		Vec3 end = blockHit.getType() == HitResult.Type.MISS ? farEnd : blockHit.getLocation();

		LivingEntity best = null;
		Vec3 bestPoint = end;
		double bestDistSqr = start.distanceToSqr(end);

		AABB search = new AABB(start, end).inflate(2.0);
		for (Entity candidate : level.getEntities(shooter, search, e -> !e.isSpectator())) {
			// Big multi-part mobs (the Ender Dragon) are hit through their parts.
			Entity resolved = candidate instanceof PartEntity<?> part ? part.getParent() : candidate;
			if (!(resolved instanceof LivingEntity living) || resolved == shooter
				|| resolved instanceof ArmorStand || !living.isAlive()) {
				continue;
			}

			AABB box = candidate.getBoundingBox().inflate(0.3);
			Vec3 point;
			if (box.contains(start)) {
				point = start;
			} else {
				Optional<Vec3> hit = box.clip(start, end);
				if (hit.isEmpty()) {
					continue;
				}
				point = hit.get();
			}

			double distSqr = start.distanceToSqr(point);
			if (distSqr < bestDistSqr) {
				best = living;
				bestPoint = point;
				bestDistSqr = distSqr;
			}
		}

		return new Shot(start, bestPoint, best);
	}

	/** Where the barrel of the weapon is, for drawing the tracer. */
	public static Vec3 muzzle(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		return player.getEyePosition()
			.add(look.scale(0.7))
			.add(right.scale(0.25))
			.subtract(0.0, 0.3, 0.0);
	}

	/** Draws a straight line of particles. */
	public static void drawLine(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double spacing) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 0.01) {
			return;
		}
		Vec3 step = delta.scale(spacing / length);
		int count = (int) (length / spacing);
		Vec3 point = from;
		for (int i = 0; i <= count; i++) {
			level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			point = point.add(step);
		}
	}

	/** Spawns a small cloud of particles around a point. */
	public static void burst(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
		level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}
}
