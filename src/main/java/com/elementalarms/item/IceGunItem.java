package com.elementalarms.item;

import com.elementalarms.logic.BeamUtil;
import com.elementalarms.logic.FreezeManager;
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
 * Right click: fires an instant ice bolt. Any creature it hits is frozen solid
 * for 5 seconds and cannot move, no matter what it is.
 */
public class IceGunItem extends Item {
	private static final double RANGE = 48.0;
	private static final int COOLDOWN_TICKS = 15;

	public IceGunItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer shooter)) {
			return InteractionResult.SUCCESS;
		}

		BeamUtil.Shot shot = BeamUtil.trace(serverLevel, shooter, RANGE);
		Vec3 muzzle = BeamUtil.muzzle(shooter);

		BeamUtil.drawLine(serverLevel, ParticleTypes.SNOWFLAKE, muzzle, shot.end(), 0.5);
		BeamUtil.drawLine(serverLevel, ParticleTypes.ITEM_SNOWBALL, muzzle, shot.end(), 2.0);
		serverLevel.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
			SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.PLAYERS, 0.9F, 1.4F);

		LivingEntity target = shot.target();
		if (target != null) {
			FreezeManager.freeze(target);
			BeamUtil.burst(serverLevel, ParticleTypes.SNOWFLAKE, shot.end(), 25, 0.4, 0.08);
			serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.8F);
		} else {
			BeamUtil.burst(serverLevel, ParticleTypes.SNOWFLAKE, shot.end(), 8, 0.2, 0.02);
		}

		shooter.getCooldowns().addCooldown(shooter.getItemInHand(hand), COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
