package mekanism.common;

import ic2.api.ElectricItem;
import ic2.api.IElectricItem;
import mekanism.api.SideData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraftforge.common.ForgeDirection;
import universalelectricity.core.implement.IItemElectric;

import com.google.common.io.ByteArrayDataInput;

import dan200.computer.api.IComputerAccess;

public abstract class TileEntityElectricMachine extends TileEntityBasicMachine
{
	/**
	 * A simple electrical machine. This has 3 slots - the input slot (0), the energy slot (1), 
	 * output slot (2), and the upgrade slot (3). It will not run if it does not have enough energy.
	 * 
	 * @param soundPath - location of the sound effect
	 * @param name - full name of this machine
	 * @param path - GUI texture path of this machine
	 * @param perTick - energy used per tick.
	 * @param ticksRequired - ticks required to operate -- or smelt an item.
	 * @param maxEnergy - maximum energy this machine can hold.
	 */
	public TileEntityElectricMachine(String soundPath, String name, String path, int perTick, int ticksRequired, int maxEnergy)
	{
		super(soundPath, name, path, perTick, ticksRequired, maxEnergy);
		
		sideOutputs.add(new SideData(EnumColor.GREY, 0, 0));
		sideOutputs.add(new SideData(EnumColor.DARK_RED, 0, 1));
		sideOutputs.add(new SideData(EnumColor.DARK_GREEN, 1, 1));
		sideOutputs.add(new SideData(EnumColor.DARK_BLUE, 2, 1));
		sideOutputs.add(new SideData(EnumColor.ORANGE, 3, 1));
		
		sideConfig = new byte[] {2, 1, 0, 0, 4, 3};
		
		inventory = new ItemStack[4];
	}
	
	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		if(inventory[1] != null)
		{
			if(electricityStored < MekanismUtils.getEnergy(energyMultiplier, MAX_ELECTRICITY))
			{
				if(inventory[1].getItem() instanceof IItemElectric)
				{
					IItemElectric electricItem = (IItemElectric)inventory[1].getItem();

					if (electricItem.canProduceElectricity())
					{
						double joulesNeeded = MekanismUtils.getEnergy(energyMultiplier, MAX_ELECTRICITY)-electricityStored;
						double joulesReceived = electricItem.onUse(Math.min(electricItem.getMaxJoules(inventory[1])*0.005, joulesNeeded), inventory[1]);
						setJoules(electricityStored + joulesReceived);
					}
				}
				else if(inventory[1].getItem() instanceof IElectricItem)
				{
					IElectricItem item = (IElectricItem)inventory[1].getItem();
					if(item.canProvideEnergy())
					{
						double gain = ElectricItem.discharge(inventory[1], (int)((MAX_ELECTRICITY - electricityStored)*Mekanism.TO_IC2), 3, false, false)*Mekanism.FROM_IC2;
						setJoules(electricityStored + gain);
					}
				}
			}
			if(inventory[1].itemID == Item.redstone.itemID && electricityStored+1000 <= MekanismUtils.getEnergy(energyMultiplier, MAX_ELECTRICITY))
			{
				setJoules(electricityStored + 1000);
				--inventory[1].stackSize;
				
	            if (inventory[1].stackSize <= 0)
	            {
	                inventory[1] = null;
	            }
			}
		}
		
		if(inventory[3] != null)
		{
			if(inventory[3].isItemEqual(new ItemStack(Mekanism.EnergyUpgrade)) && speedMultiplier < 8)
			{
				if(upgradeTicks < UPGRADE_TICKS_REQUIRED)
				{
					upgradeTicks++;
				}
				else if(upgradeTicks == UPGRADE_TICKS_REQUIRED)
				{
					upgradeTicks = 0;
					energyMultiplier+=1;
					
					inventory[3].stackSize--;
					
					if(inventory[3].stackSize == 0)
					{
						inventory[3] = null;
					}
				}
			}
			else if(inventory[3].isItemEqual(new ItemStack(Mekanism.SpeedUpgrade)) && speedMultiplier < 8)
			{
				if(upgradeTicks < UPGRADE_TICKS_REQUIRED)
				{
					upgradeTicks++;
				}
				else if(upgradeTicks == UPGRADE_TICKS_REQUIRED)
				{
					upgradeTicks = 0;
					speedMultiplier+=1;
					
					inventory[3].stackSize--;
					
					if(inventory[3].stackSize == 0)
					{
						inventory[3] = null;
					}
				}
			}
			else {
				upgradeTicks = 0;
			}
		}
		else {
			upgradeTicks = 0;
		}
		
		if(electricityStored >= ENERGY_PER_TICK)
		{
			if(canOperate() && (operatingTicks+1) < MekanismUtils.getTicks(speedMultiplier))
			{
					operatingTicks++;
					electricityStored -= ENERGY_PER_TICK;
			}
			else if(canOperate() && (operatingTicks+1) >= MekanismUtils.getTicks(speedMultiplier))
			{
				if(!worldObj.isRemote)
				{
					operate();
				}
				operatingTicks = 0;
				electricityStored -= ENERGY_PER_TICK;
			}
		}
		
		if(!canOperate())
		{
			operatingTicks = 0;
		}
		
		if(!worldObj.isRemote)
		{
			if(canOperate() && electricityStored >= ENERGY_PER_TICK)
			{
				setActive(true);
			}
			else {
				setActive(false);
			}
		}
	}

	@Override
    public void operate()
    {
        ItemStack itemstack = RecipeHandler.getOutput(inventory[0], true, getRecipes());

        if (inventory[0].stackSize <= 0)
        {
            inventory[0] = null;
        }

        if (inventory[2] == null)
        {
            inventory[2] = itemstack;
        }
        else
        {
            inventory[2].stackSize += itemstack.stackSize;
        }
    }

	@Override
    public boolean canOperate()
    {
        if (inventory[0] == null)
        {
            return false;
        }

        ItemStack itemstack = RecipeHandler.getOutput(inventory[0], false, getRecipes());

        if (itemstack == null)
        {
            return false;
        }

        if (inventory[2] == null)
        {
            return true;
        }

        if (!inventory[2].isItemEqual(itemstack))
        {
            return false;
        }
        else
        {
            return inventory[2].stackSize + itemstack.stackSize <= inventory[2].getMaxStackSize();
        }
    }
	
	@Override
	public void handlePacketData(INetworkManager network, Packet250CustomPayload packet, EntityPlayer player, ByteArrayDataInput dataStream)
	{
		try {
			facing = dataStream.readInt();
			isActive = dataStream.readBoolean();
			operatingTicks = dataStream.readInt();
			electricityStored = dataStream.readDouble();
			energyMultiplier = dataStream.readInt();
			speedMultiplier = dataStream.readInt();
			upgradeTicks = dataStream.readInt();
			worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
			worldObj.updateAllLightTypes(xCoord, yCoord, zCoord);
		} catch (Exception e)
		{
			System.out.println("[Mekanism] Error while handling tile entity packet.");
			e.printStackTrace();
		}
	}
    
	@Override
    public void sendPacket()
    {
		PacketHandler.sendTileEntityPacketToClients(this, 0, facing, isActive, operatingTicks, electricityStored, energyMultiplier, speedMultiplier, upgradeTicks);
    }
    
	@Override
    public void sendPacketWithRange()
    {
		PacketHandler.sendTileEntityPacketToClients(this, 50, facing, isActive, operatingTicks, electricityStored, energyMultiplier, speedMultiplier, upgradeTicks);
    }

	@Override
	public String[] getMethodNames() 
	{
		return new String[] {"getStored", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, int method, Object[] arguments) throws Exception 
	{
		switch(method)
		{
			case 0:
				return new Object[] {electricityStored};
			case 1:
				return new Object[] {operatingTicks};
			case 2:
				return new Object[] {isActive};
			case 3:
				return new Object[] {facing};
			case 4:
				return new Object[] {canOperate()};
			case 5:
				return new Object[] {MekanismUtils.getEnergy(energyMultiplier, MAX_ELECTRICITY)};
			case 6:
				return new Object[] {(MekanismUtils.getEnergy(energyMultiplier, MAX_ELECTRICITY)-electricityStored)};
			default:
				System.err.println("[Mekanism] Attempted to call unknown method with computer ID " + computer.getID());
				return new Object[] {"Unknown command."};
		}
	}
}
