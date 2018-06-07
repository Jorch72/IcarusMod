package com.rwtema.antiair;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.io.IOException;

@Mod(modid = Icarus.MODID, name = Icarus.NAME, version = Icarus.VERSION)
public class Icarus {
	public static final String MODID = "icarus";
	public static final String NAME = "Icarus Mod";
	public static final String VERSION = "1.0";
	@CapabilityInject(value = PlayerData.class)
	public static Capability<PlayerData> playerDataCapability;
	public static SimpleNetworkWrapper network;
	public static int STARE_GRACE_TIME;
	public static int RADIUS_SQR;
	public static int FLIGHT_GRACE_TIME;
	public static double FALL_MULTIPLIER;
	public static double MAX_FALL_SPEED;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		Configuration configuration = new Configuration(event.getSuggestedConfigurationFile());
		int rad = getInt(configuration, "Radius", 32, "Distance between enemy and player for effect to affect.").getInt();
		RADIUS_SQR = rad * rad;
		STARE_GRACE_TIME = getInt(configuration, "Stare Grace Time", 40, "Grace period after being targeted by an enemy, before we start to affect the player.").getInt();
		FLIGHT_GRACE_TIME = getInt(configuration, "Flight Grace Time", 20, "Grace period after leaving the ground, before we start to affect the player.").getInt();
		FALL_MULTIPLIER = 1 / (Double.MIN_NORMAL + getDouble(configuration, "Fall Multiplier", 60.0, "Time before falling speed is maximised.").getDouble());
		MAX_FALL_SPEED = getDouble(configuration, "Max Fall Speed", 0.05, "Maximum fall speed.").getDouble();

		network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
		network.registerMessage((packet, ctx) -> packet.runClient(), Packet.class, 0, Side.CLIENT);
		CapabilityManager.INSTANCE.register(PlayerData.class, new Capability.IStorage<PlayerData>() {
			@Nullable
			@Override
			public NBTBase writeNBT(Capability<PlayerData> capability, PlayerData instance, EnumFacing side) {
				return null;
			}

			@Override
			public void readNBT(Capability<PlayerData> capability, PlayerData instance, EnumFacing side, NBTBase nbt) {

			}
		}, () -> {
			throw new RuntimeException();
		});

		MinecraftForge.EVENT_BUS.register(new FlyingHandler());
	}

	private Property getDouble(Configuration configuration, String key, double defaultValue, String comment) {
		Property settings = configuration.get("Settings", key, defaultValue);
		settings.setComment(comment);
		return settings;
	}

	private Property getInt(Configuration configuration, String key, int defaultValue, String comment) {
		Property settings = configuration.get("Settings", key, defaultValue);
		settings.setComment(comment);
		return settings;
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {

	}

	public static class Packet implements IMessage {
		int id;
		NBTTagCompound tag;

		public Packet() {

		}

		public Packet(int id, PlayerData capability) {
			this.id = id;
			tag = capability.serializeNBT();
		}

		IMessage runClient() {
			if (tag != null) {
				Minecraft.getMinecraft().addScheduledTask(() -> {
					WorldClient world = Minecraft.getMinecraft().world;
					Entity entityByID = world.getEntityByID(id);
					if (entityByID instanceof EntityPlayer) {
						PlayerData capability = entityByID.getCapability(Icarus.playerDataCapability, null);
						assert capability != null;
						capability.deserializeNBT(tag);
					}
				});
			}
			return null;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			id = buf.readInt();
			try {
				tag = new PacketBuffer(buf).readCompoundTag();
			} catch (IOException e) {
				tag = null;
			}
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(id);
			new PacketBuffer(buf).writeCompoundTag(tag);
		}

	}

}
