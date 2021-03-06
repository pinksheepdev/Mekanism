package mekanism.common;

import mekanism.api.EnumGas;
import mekanism.api.IGasAcceptor;
import mekanism.api.IGasStorage;
import mekanism.api.IStorageTank;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import universalelectricity.core.vector.Vector3;

import com.google.common.io.ByteArrayDataInput;

public class TileEntityGasTank extends TileEntityContainerBlock implements IGasStorage, IGasAcceptor
{
	/** The type of gas stored in this tank. */
	public EnumGas gasType;
	
	/** The maximum amount of gas this tank can hold. */
	public int MAX_GAS = 96000;
	
	/** How much gas this tank is currently storing. */
	public int gasStored;
	
	/** How fast this tank can output gas. */
	public int output = 16;
	
	public TileEntityGasTank()
	{
		super("Gas Tank");
		gasType = EnumGas.NONE;
		inventory = new ItemStack[2];
	}
	
	@Override
	public void onUpdate()
	{
		if(inventory[0] != null && gasStored > 0)
		{
			if(inventory[0].getItem() instanceof IStorageTank)
			{
				if(((IStorageTank)inventory[0].getItem()).getGasType(inventory[0]) == gasType || ((IStorageTank)inventory[0].getItem()).getGasType(inventory[0]) == EnumGas.NONE)
				{
					IStorageTank item = (IStorageTank)inventory[0].getItem();
					
					if(gasType == EnumGas.NONE)
						gasType = item.getGasType(inventory[0]);
					
					if(item.canReceiveGas(inventory[0], gasType))
					{
						int sendingGas = 0;
						
						if(item.getRate() <= gasStored)
						{
							sendingGas = item.getRate();
						}
						else if(item.getRate() > gasStored)
						{
							sendingGas = gasStored;
						}
						
						int rejects = item.addGas(inventory[0], gasType, sendingGas);
						setGas(gasType, gasStored - (sendingGas - rejects));
					}
				}
			}
		}
		
		if(inventory[1] != null && gasStored < MAX_GAS)
		{
			if(inventory[1].getItem() instanceof IStorageTank)
			{
				if(((IStorageTank)inventory[1].getItem()).getGasType(inventory[1]) == gasType || gasType == EnumGas.NONE)
				{
					IStorageTank item = (IStorageTank)inventory[1].getItem();
					
					if(gasType == EnumGas.NONE)
						gasType = item.getGasType(inventory[1]);
					
					if(item.canProvideGas(inventory[1], gasType))
					{
						int received = 0;
						int gasNeeded = MAX_GAS - gasStored;
						if(item.getRate() <= gasNeeded)
						{
							received = item.removeGas(inventory[1], gasType, item.getRate());
						}
						else if(item.getRate() > gasNeeded)
						{
							received = item.removeGas(inventory[1], gasType, gasNeeded);
						}
						
						setGas(gasType, gasStored + received);
					}
				}
			}
		}
		
		if(gasStored == 0)
		{
			gasType = EnumGas.NONE;
		}
		
		if(gasStored > 0 && !worldObj.isRemote)
		{
			TileEntity tileEntity = Vector3.getTileEntityFromSide(worldObj, new Vector3(this), ForgeDirection.getOrientation(facing));
			
			if(tileEntity instanceof IGasAcceptor)
			{
				if(((IGasAcceptor)tileEntity).canReceiveGas(ForgeDirection.getOrientation(facing).getOpposite(), gasType))
				{
					int sendingGas = 0;
					if(getGas(gasType) >= output)
					{
						sendingGas = output;
					}
					else if(getGas(gasType) < output)
					{
						sendingGas = getGas(gasType);
					}
					
					int rejects = ((IGasAcceptor)tileEntity).transferGasToAcceptor(sendingGas, gasType);
					
					setGas(gasType, getGas(gasType) - (sendingGas - rejects));
				}
			}
		}
	}
	
	@Override
	public int getStartInventorySide(ForgeDirection side) 
	{
		if(side == ForgeDirection.getOrientation(1))
		{
			return 0;
		}
		
		return 1;
	}

	@Override
	public int getSizeInventorySide(ForgeDirection side)
	{
		return 1;
	}

	@Override
	public int getGas(EnumGas type) 
	{
		if(type == gasType)
		{
			return gasStored;
		}
		
		return 0;
	}

	@Override
	public void setGas(EnumGas type, int amount) 
	{
		if(type == gasType)
		{
			gasStored = Math.max(Math.min(amount, MAX_GAS), 0);
		}
	}

	@Override
	public int transferGasToAcceptor(int amount, EnumGas type) 
	{
		if(type == gasType || gasType == EnumGas.NONE)
		{
			if(gasType == EnumGas.NONE)
				gasType = type;
			
	    	int rejects = 0;
	    	int neededGas = MAX_GAS-gasStored;
	    	if(amount <= neededGas)
	    	{
	    		gasStored += amount;
	    	}
	    	else if(amount > neededGas)
	    	{
	    		gasStored += neededGas;
	    		rejects = amount-neededGas;
	    	}
	    	
	    	return rejects;
		}
		return 0;
	}

	@Override
	public boolean canReceiveGas(ForgeDirection side, EnumGas type) 
	{
		return (type == gasType || gasType == EnumGas.NONE) && (side != ForgeDirection.getOrientation(facing) && side != ForgeDirection.getOrientation(0) && side != ForgeDirection.getOrientation(1));
	}

	@Override
	public void handlePacketData(INetworkManager network, Packet250CustomPayload packet, EntityPlayer player, ByteArrayDataInput dataStream) 
	{
		try {
			facing = dataStream.readInt();
			gasStored = dataStream.readInt();
			gasType = EnumGas.getFromName(dataStream.readUTF());
			worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
		} catch (Exception e)
		{
			System.out.println("[Mekanism] Error while handling tile entity packet.");
			e.printStackTrace();
		}
	}
	
	@Override
    public void readFromNBT(NBTTagCompound nbtTags)
    {
        super.readFromNBT(nbtTags);

        gasStored = nbtTags.getInteger("gasStored");
        gasType = EnumGas.getFromName(nbtTags.getString("gasType"));
    }

	@Override
    public void writeToNBT(NBTTagCompound nbtTags)
    {
        super.writeToNBT(nbtTags);
        
        nbtTags.setInteger("gasStored", gasStored);
        nbtTags.setString("gasType", gasType.name);
    }

	@Override
	public void sendPacket() 
	{
		PacketHandler.sendTileEntityPacketToClients(this, 0, facing, gasStored, gasType.name);
	}

	@Override
	public void sendPacketWithRange() 
	{
		PacketHandler.sendTileEntityPacketToClients(this, 50, facing, gasStored, gasType.name);
	}
}
