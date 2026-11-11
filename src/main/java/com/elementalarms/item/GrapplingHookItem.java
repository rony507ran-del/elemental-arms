package com.elementalarms.item;

import com.elementalarms.logic.GrappleManager;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right click: the hook flies to the block you are looking at and pulls you to it.
 * Right click again (or sneak, or put the hook away) to let go.
 */
public class GrapplingHookItem extends Item {
	private static final int COOLDOWN_TICKS = 8;
	private static final int MISS_COOLDOWN_TICKS = 6;

	public GrapplingHookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// The client only plays the swing animation, all logic runs on the server.
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);

		if (GrappleManager.isActive(serverPlayer)) {
			GrappleManager.release(serverPlayer);
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
			return InteractionResult.SUCCESS;
		}

		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(GrappleManager.RANGE));
		BlockHitResult hit = serverLevel.clip(
			new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		if (hit.getType() != HitResult.Type.BLOCK) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.5F);
			player.getCooldowns().addCooldown(stack, MISS_COOLDOWN_TICKS);
			return InteractionResult.FAIL;
		}

		// Aim for a point just in front of the block face so we are pulled to the surface, not into it.
		Direction face = hit.getDirection();
		Vec3 anchor = hit.getLocation().add(face.getStepX() * 0.4, face.getStepY() * 0.4, face.getStepZ() * 0.4);
		GrappleManager.attach(serverPlayer, anchor);

		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.9F, 1.0F);
		serverLevel.playSound(null, anchor.x, anchor.y, anchor.z,
			SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
