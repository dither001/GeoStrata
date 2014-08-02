/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2014
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.GeoStrata.Guardian;

import java.util.ArrayList;
import java.util.Iterator;

import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.EventPriority;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import Reika.DragonAPI.Libraries.ReikaPlayerAPI;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;
import Reika.GeoStrata.Registry.GeoBlocks;
;

public class GuardianStoneManager {

	public static final GuardianStoneManager instance = new GuardianStoneManager();

	private final ArrayList<ProtectionZone> zones = new ArrayList();

	private GuardianStoneManager() {

	}

	public boolean canPlayerOverrideProtections(EntityPlayer ep) {
		if ("Reika_Kalseki".equals(ep.getEntityName()))
			return true;
		return ReikaPlayerAPI.isAdmin(ep);
	}

	public ProtectionZone addZone(World world, int x, int y, int z, EntityPlayer ep, int r) {
		ProtectionZone zone = new ProtectionZone(world, ep, x, y, z, r);
		zones.add(zone);
		return zone;
	}

	private ArrayList<ProtectionZone> getProtectionZonesForArea(World world, int x, int y, int z) {
		ArrayList<ProtectionZone> in = new ArrayList();
		for (int i = 0; i < zones.size(); i++) {
			ProtectionZone zone = zones.get(i);
			if (world.provider.dimensionId == zone.dimensionID) {
				if (zone.isBlockInZone(x, y, z))
					in.add(zone);
			}
		}
		return in;
	}

	protected ArrayList<ProtectionZone> getProtectionZonesForPlayer(EntityPlayer ep) {
		ArrayList<ProtectionZone> in = new ArrayList();
		for (int i = 0; i < zones.size(); i++) {
			ProtectionZone zone = zones.get(i);
			if (zone.creator.equals(ep.getEntityName())) {
				in.add(zone);
			}
		}
		return in;
	}

	public void removeAreasForStone(TileEntityGuardianStone te) {
		int id = te.worldObj.provider.dimensionId;
		int x = te.xCoord;
		int y = te.yCoord;
		int z = te.zCoord;
		Iterator<ProtectionZone> it = zones.iterator();
		while (it.hasNext()) {
			ProtectionZone zone = it.next();
			if (zone.originX == x && zone.originY == y && zone.originZ == z && zone.dimensionID == id)
				it.remove();
		}
	}

	public boolean doesPlayerHavePermissions(World world, int x, int y, int z, EntityPlayer ep) {
		if (this.canPlayerOverrideProtections(ep))
			return true;
		for (int i = 0; i < zones.size(); i++) {
			ProtectionZone zone = zones.get(i);
			if (world.provider.dimensionId == zone.dimensionID) {
				if (zone.isBlockInZone(x, y, z)) {
					if (!zone.canPlayerEditIn(ep))
						return false;
				}
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return zones.toString();
	}

	@ForgeSubscribe(priority = EventPriority.LOWEST, receiveCanceled = true)
	public void guardArea(PlayerInteractEvent event) {
		EntityPlayer ep = event.entityPlayer;
		World world = ep.worldObj;
		int x = event.x;
		int y = event.y;
		int z = event.z;
		if (!world.isRemote) {
			if (!this.doesPlayerHavePermissions(world, x, y, z, ep))
				event.setCanceled(true);
		}
	}

	@ForgeSubscribe(priority = EventPriority.LOWEST, receiveCanceled = true)
	public void preventEndermen(EnderTeleportEvent event) {
		if (event.entityLiving instanceof EntityEnderman) {
			int x = MathHelper.floor_double(event.targetX);
			int y = MathHelper.floor_double(event.targetY);
			int z = MathHelper.floor_double(event.targetZ);
			if (!this.getProtectionZonesForArea(event.entityLiving.worldObj, x, y, z).isEmpty()) {
				event.setCanceled(true);
			}
		}
	}

	protected static final class ProtectionZone {

		public final String creator;
		public final int dimensionID;
		public final int originX;
		public final int originY;
		public final int originZ;
		public final int range;

		public ProtectionZone(World world, EntityPlayer ep, int x, int y, int z, int r) {
			creator = ep.getEntityName();
			dimensionID = world.provider.dimensionId;
			originX = x;
			originY = y;
			originZ = z;
			range = r;
		}

		public boolean hasTile(World world) {
			return world.getBlockId(originX, originY, originZ) == GeoBlocks.GUARDIAN.getBlockID();
		}

		public boolean canPlayerEditIn(EntityPlayer ep) {
			if (ep.getEntityName().equals(creator))
				return true;
			return this.isPlayerOnAuxList(ep);
		}

		private boolean isPlayerOnAuxList(EntityPlayer ep) {
			TileEntityGuardianStone te = this.getControllingGuardianStone();
			return te != null ? te.isPlayerInList(ep) : false;
		}

		private ProtectionZone(String player, int id, int x, int y, int z, int r) {
			creator = player;
			dimensionID = id;
			originX = x;
			originY = y;
			originZ = z;
			range = r;
		}

		public boolean isBlockInZone(int x, int y, int z) {
			double dd = ReikaMathLibrary.py3d(x-originX, y-originY, z-originZ);
			return dd <= range+0.5;
		}

		@Override
		public String toString() {
			return "Zone by "+creator+" in world "+dimensionID+" at "+originX+", "+originY+", "+originZ+" (Radius "+range+")";
		}

		public TileEntityGuardianStone getControllingGuardianStone() {
			World world = DimensionManager.getWorld(dimensionID);
			int x = originX;
			int y = originY;
			int z = originZ;
			int id = world.getBlockId(x, y, z);
			if (id != GeoBlocks.GUARDIAN.getBlockID())
				return null;
			TileEntity te = world.getBlockTileEntity(x, y, z);
			return (TileEntityGuardianStone)te;
		}

		public String getSerialString() {
			StringBuilder sb = new StringBuilder();
			sb.append("P:");
			sb.append(creator);
			sb.append(";");

			sb.append("W:");
			sb.append(String.valueOf(dimensionID));
			sb.append(";");

			sb.append("X:");
			sb.append(String.valueOf(originX));
			sb.append(";");

			sb.append("Y:");
			sb.append(String.valueOf(originY));
			sb.append(";");

			sb.append("Z:");
			sb.append(String.valueOf(originZ));
			sb.append(";");

			sb.append("R:");
			sb.append(String.valueOf(range));
			return sb.toString();
		}

		protected static ProtectionZone getFromSerialString(String sg) {
			try {
				String[] s = sg.split(";");
				if (s == null || s.length != 6)
					return null;
				for (int i = 0; i < 6; i++)
					s[i] = s[i].substring(2);
				int w = Integer.parseInt(s[1]);
				int x = Integer.parseInt(s[2]);
				int y = Integer.parseInt(s[3]);
				int z = Integer.parseInt(s[4]);
				int r = Integer.parseInt(s[5]);
				return new ProtectionZone(s[0], w, x, y, z, r);
			}
			catch (Exception e) {
				return null;
			}
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof ProtectionZone) {
				ProtectionZone p = (ProtectionZone)o;
				if (!creator.equals(p.creator))
					return false;
				if (p.dimensionID != dimensionID)
					return false;
				if (p.range != range)
					return false;
				if (p.originX != originX)
					return false;
				if (p.originY != originY)
					return false;
				if (p.originZ != originZ)
					return false;
				return true;
			}
			return false;
		}

	}

}
