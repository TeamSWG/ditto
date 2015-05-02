package network;

import intents.CloseConnectionIntent;
import intents.OutboundPacketIntent;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import resources.control.Intent;
import resources.control.Service;
import network.packets.Packet;
import network.packets.soe.DataChannelA;
import network.packets.soe.MultiPacket;
import network.packets.soe.SessionResponse;
import network.packets.swg.SWGPacket;

public class OutboundPacketService {
	
	private final Map <Long, OutboundPacketSender> outboundPackets = new HashMap<Long, OutboundPacketSender>();
	
	public OutboundPacketService() {
		new Service() {
			public void onIntentReceived(Intent i) {
				if (i instanceof CloseConnectionIntent)
					closeConnection(((CloseConnectionIntent)i).getNetworkId());
			}
		}.registerForIntent(CloseConnectionIntent.TYPE);
	}
	
	public void sendPacket(long networkId, Packet ... packets) {
		getSender(networkId).add(packets);
	}
	
	public void sendPacket(long networkId, Packet packet) {
		getSender(networkId).add(packet);
	}
	
	public int flushPackets() {
		int count = 0;
		synchronized (outboundPackets) {
			for (OutboundPacketSender sender : outboundPackets.values())
				count += sender.sendPackets();
		}
		return count;
	}
	
	private void closeConnection(long networkId) {
		synchronized (outboundPackets) {
			outboundPackets.remove(networkId);
		}
	}
	
	private OutboundPacketSender getSender(long networkId) {
		synchronized (outboundPackets) {
			OutboundPacketSender outbound = outboundPackets.get(networkId);
			if (outbound == null) {
				outbound = new OutboundPacketSender(networkId);
				outboundPackets.put(networkId, outbound);
			}
			return outbound;
		}
	}
	
	private static class OutboundPacketSender {
		
		private final List <Packet> outbound;
		private final long networkId;
		private Intent prevOutbound;
		private boolean hasSoe;
		private boolean hasSwg;
		
		public OutboundPacketSender(long networkId) {
			outbound = new LinkedList<Packet>();
			this.networkId = networkId;
			prevOutbound = null;
			hasSoe = false;
			hasSwg = false;
		}
		
		public synchronized void add(Packet ... packets) {
			for (Packet p : packets)
				add(p);
		}
		
		public synchronized void add(Packet p) {
			if (p instanceof SessionResponse) {
				send(p);
			} else {
				if (p instanceof SWGPacket)
					hasSwg = true;
				else
					hasSoe = true;
				outbound.add(p);
			}
		}
		
		public synchronized int sendPackets() {
			if (outbound.isEmpty())
				return 0;
			int size = outbound.size();
			packageAndSendPackets();
			return size;
		}
		
		private synchronized void send(Packet p) {
			Intent out = new OutboundPacketIntent(p, networkId);
			out.broadcastAfterIntent(prevOutbound);
			prevOutbound = out;
		}
		
		private synchronized void clear() {
			hasSoe = false;
			hasSwg = false;
			outbound.clear();
		}
		
		private synchronized void packageAndSendPackets() {
			if (hasSwg) {
				if (hasSoe)
					packageMix();
				else
					packageSwg();
			} else {
				if (hasSoe)
					packageSoe();
			}
			clear();
		}
		
		private synchronized void packageSoe() {
			send(new MultiPacket(new LinkedList<Packet>(outbound)));
		}
		
		private synchronized void packageSwg() {
			send(new DataChannelA(outbound.toArray(new SWGPacket[outbound.size()])));
		}
		
		private synchronized void packageMix() {
			MultiPacket multi = new MultiPacket();
			DataChannelA data = null;
			for (Packet p : outbound) {
				if (p instanceof SWGPacket) {
					if (data == null)
						data = new DataChannelA();
					data.addPacket((SWGPacket) p);
				} else {
					if (data != null) {
						multi.addPacket(data);
						data = null;
					}
					multi.addPacket(p);
				}
			}
			if (data != null)
				multi.addPacket(data);
			send(multi);
		}
		
	}
	
}
