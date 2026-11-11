package com.elementalarms.item;

import com.elementalarms.logic.BeamUtil;
import com.elementalarms.logic.BurnManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Right click: fires an instant fire bolt. Any creature it hits burns until it dies
 * (water does not put it out).
 */
public class FireGunItem extends Item {
	private static final double RANGE = 48.0;
	private static final int COOLDOWN_TICKS = 10;

	public FireGunItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer shooter)) {
			return InteractionResult.SUCCESS;
		}

		BeamUtil.Shot shot = BeamUtil.trace(serverLevel, shooter, RANGE);
		Vec3 muzzle = BeamUtil.muzzle(shooter);

		BeamUtil.drawLine(serverLevel, ParticleTypes.FLAME, muzzle, shot.end(), 0.6);
		BeamUtil.drawLine(serverLevel, ParticleTypes.SMOKE, muzzle, shot.end(), 2.0);
		serverLevel.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
			SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 1.3F);

		LivingEntity target = shot.target();
		if (target != null) {
			BurnManager.ignite(shooter, target);
			BeamUtil.burst(serverLevel, ParticleTypes.FLAME, shot.end(), 18, 0.35, 0.05);
			BeamUtil.burst(serverLevel, ParticleTypes.LAVA, shot.end(), 4, 0.25, 0.0);
			serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
		} else {
			BeamUtil.burst(serverLevel, ParticleTypes.SMOKE, shot.end(), 6, 0.15, 0.01);
		}

		shooter.getCooldowns().addCooldown(shooter.getItemInHand(hand), COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
