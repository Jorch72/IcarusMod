package com.rwtema.antiair;

import gnu.trove.iterator.TIntIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FlyingHandler {
	private final ResourceLocation LOC = new ResourceLocation(Icarus.MODID, "PlayerData");
	private int flightTime = 0;
	private int stareTime = 0;
	private double prevY = Double.NaN;

	@SubscribeEvent
	public void playerTick(TickEvent.PlayerTickEvent event) {
		EntityPlayer player = event.player;
		PlayerData capability = player.getCapability(Icarus.playerDataCapability, null);
		assert capability != null;
		World world = player.world;
		if (world.isRemote) {
			tickClient(player, capability);
		} else {
			tickServer(world, player, capability);
		}

	}

	private void tickServer(World world, EntityPlayer player, PlayerData capability) {
		boolean dirty = false;
		TIntIterator iterator = capability.entities.iterator();
		while (iterator.hasNext()) {
			int next = iterator.next();
			Entity entityByID = world.getEntityByID(next);
			if (entityByID == null || entityByID.isDead || !(entityByID instanceof EntityLiving) || ((EntityLiving) entityByID).getAttackTarget() != player) {
				iterator.remove();
				dirty = true;
			}
		}

		if (dirty) {
			update((EntityPlayerMP) player, capability);
		}
	}

	@SubscribeEvent
	public void onStartTracking(PlayerEvent.StartTracking event) {
		if (event.getTarget() instanceof EntityPlayerMP) {
			PlayerData capability = event.getTarget().getCapability(Icarus.playerDataCapability, null);
			Icarus.network.sendTo(new Icarus.Packet(event.getTarget().getEntityId(), capability), (EntityPlayerMP) event.getEntityPlayer());
		}
	}

	private void update(EntityPlayerMP player, PlayerData capability) {
		((WorldServer) player.world).getEntityTracker().sendToTrackingAndSelf(player,
				Icarus.network.getPacketFrom(new Icarus.Packet(player.getEntityId(), capability))
		);
	}

	@SideOnly(Side.CLIENT)
	private void tickClient(EntityPlayer player, PlayerData capability) {
		if (player == Minecraft.getMinecraft().player) {
			if (Double.isNaN(prevY)) {
				prevY = player.posY;
				return;
			}

			if (player.onGround || player.isInWater()) {
				flightTime = 0;
			} else {
				flightTime++;
			}

			if (!capability.entities.isEmpty()) {
				boolean flag = false;
				TIntIterator iterator = capability.entities.iterator();
				while (iterator.hasNext()) {
					int next = iterator.next();
					Entity entityByID = Minecraft.getMinecraft().world.getEntityByID(next);
					if (entityByID == null || entityByID.isDead || !(entityByID instanceof EntityLiving)) {
						continue;
					}
					double d0 = entityByID.posX - player.posX;
					double d1 = entityByID.posY - player.posY;
					double d2 = entityByID.posZ - player.posZ;
					double v = d0 * d0 + 4 * d1 * d1 + d2 * d2;
					if (v > Icarus.RADIUS_SQR) continue;
					if (((EntityLiving) entityByID).canEntityBeSeen(player)) {
						flag = true;
						break;
					}
				}

				if (stareTime > Icarus.STARE_GRACE_TIME && player.capabilities.isFlying) {
					player.capabilities.isFlying = false;
					player.sendPlayerAbilities();
				}
				if (flag) {
					stareTime++;
					int i = Math.min(stareTime - Icarus.STARE_GRACE_TIME, flightTime - Icarus.FLIGHT_GRACE_TIME);
					if (i > 0) {
						double min = Math.min(i * Icarus.FALL_MULTIPLIER * Icarus.MAX_FALL_SPEED, Icarus.MAX_FALL_SPEED);
						double v = Math.min(player.posY, prevY - min) - player.posY;
						if (v < 0) {
							player.move(MoverType.PLAYER, 0, v, 0);
						}
					}
				} else {
					stareTime = 0;
				}
			}
			prevY = player.posY;
		}
	}

	@SubscribeEvent
	public void onGatherCap(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof EntityPlayer) {
			PlayerData playerData = new PlayerData();
			event.addCapability(LOC, new ICapabilityProvider() {
				@Override
				public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
					return capability == Icarus.playerDataCapability;
				}

				@Nullable
				@Override
				public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
					return capability == Icarus.playerDataCapability ? Icarus.playerDataCapability.cast(playerData) : null;
				}
			});
		}
	}

	@SubscribeEvent
	public void onTest(LivingSetAttackTargetEvent event) {
		EntityLivingBase target = event.getTarget();
		if (target instanceof EntityPlayerMP
				&& event.getEntity() instanceof EntityLiving
				&& canTarget((EntityLiving) event.getEntity())) {
			PlayerData capability = target.getCapability(Icarus.playerDataCapability, null);
			assert capability != null;
			if (capability.entities.add(event.getEntity().getEntityId())) {
				update((EntityPlayerMP) target, capability);
			}
		}
	}

	private boolean canTarget(EntityLiving mob) {
		return !mob.isNonBoss() || mob instanceof IMob;
	}
}
