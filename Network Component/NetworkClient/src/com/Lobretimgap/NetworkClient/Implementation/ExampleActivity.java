package com.Lobretimgap.NetworkClient.Implementation;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import networkTransferObjects.NetworkMessage;
import networkTransferObjects.NetworkMessageLarge;
import networkTransferObjects.NetworkMessageMedium;
import networkTransferObjects.Lokemon.LokemonPlayer;
import networkTransferObjects.Lokemon.LokemonPotion;
import networkTransferObjects.Lokemon.LokemonSpatialObject;

import com.Lobretimgap.NetworkClient.NetworkComBinder;
import com.Lobretimgap.NetworkClient.NetworkComService;
import com.Lobretimgap.NetworkClient.NetworkVariables;
import com.Lobretimgap.NetworkClient.Events.NetworkEvent;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

public class ExampleActivity extends Activity {
	private TextView tv;
	private boolean networkBound = false;
	private NetworkComBinder binder;
	
	private final Timer timer = new Timer();
	private final int recurranceDelay = 1; //in seconds
	
	private int pingsPerformed = 0;
	private int pingsReceived = 0;
	private int highest=0;
	private int lowest=100000;
	private int total=0;

	public void onCreate(Bundle bundle)
	{
		super.onCreate(bundle);
		tv = new TextView(this);
		setContentView(tv);		
	}
	
	public void onStart()
	{
		super.onStart();
		tv.append("Starting networking component tests...\n");
		tv.setMovementMethod(new ScrollingMovementMethod());
		
		//Bind network component
		Intent intent = new Intent(this, NetworkComService.class);
		bindService(intent, connection, Context.BIND_AUTO_CREATE);
	}
	
	private ServiceConnection connection = new ServiceConnection() {
		
		public void onServiceDisconnected(ComponentName name) {
			networkBound = false;			
		}
		
		public void onServiceConnected(ComponentName name, IBinder service) {
			//Gets us an instance of the binder for the service.
			binder = (NetworkComBinder)service;
			networkBound = true;
			tv.append("Service connected! Starting connection...\n");
			
			binder.registerMessenger(eventMessenger);
			if(!binder.isConnectedToServer())
				binder.ConnectToServer();			
			
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					if(pingsPerformed < 60)
					{
						if(networkBound)
							binder.requestLatency();
							pingsPerformed++;
						}
					else
					{
						this.cancel();
					}
				}
				
			}, recurranceDelay * 1000, recurranceDelay * 1000);
			
		}
	};	
	
	
	class eventHandler extends Handler{
		
		public void handleMessage(Message msg) 
		{
			tv.append("Event Received: ");
			
			switch (NetworkComBinder.EventType.values()[msg.what])
			{
				case CONNECTION_ESTABLISHED:
					tv.append("Connection established with host!\n");
					break;
					
				case CONNECTION_LOST:
					tv.append("Connection to host lost...\n");
					timer.schedule(new TimerTask() {
						
						@Override
						public void run() {							
							binder.ConnectToServer();
						}
					}, 3000);
					break;
					
				case CONNECTION_FAILED:
					tv.append("Failed to connect to host...\n");
					break;
					
				case LATENCY_UPDATE_RECEIVED:
					tv.append("Latency reported as: " + ((NetworkEvent)msg.obj).getMessage()+"ms\n");
					Log.d("LAT", "" + ((NetworkEvent)msg.obj).getMessage());
					int latency = ((Long)((NetworkEvent)msg.obj).getMessage()).intValue();
					if(latency > highest)
						highest = latency;					
					if(latency < lowest)
						lowest = latency;
					total += latency;
					
					pingsReceived++;
					
					if(pingsPerformed == 60)
					{
						tv.append("Max ="+highest+", min = "+lowest+", average = "+(total/pingsReceived)+"\n");
					}
					else if(pingsPerformed == 2)
					{
						NetworkMessageMedium medMessage = new NetworkMessageMedium("LocationUpdate");
						medMessage.doubles.add(18.5);
						medMessage.doubles.add(32.5);
						binder.sendGameUpdate(medMessage);
						NetworkMessage busyMessage = new NetworkMessage("EnteredBattle");
						//binder.sendGameUpdate(busyMessage);
						tv.append("Sent Location update!");
						
					}
					else if (pingsPerformed == 5)
					{
						//DEBUG
						//binder.sendGameStateRequest(new NetworkMessage("GetPlayers"));
						//tv.append("Sent player request...\n");
						
					}
					else if(pingsPerformed == 15)
					{
						//binder.sendGameStateRequest(new NetworkMessage("GetGameObjects"));
						//tv.append("Sent item request...\n");
					}					
					else if (pingsPerformed == 3)
					{
						tv.append("Sending spatial request!\n");
						NetworkMessageMedium mMed = new NetworkMessageMedium("MapDataRequest");
						mMed.doubles.add(-33.957657);
						mMed.doubles.add(18.46125);
						mMed.doubles.add(100.0);
						
						binder.sendRequest(mMed);
						
					}
					
					
					break;		
					
				case GAMESTATE_RECEIVED:
					NetworkMessage mMsg = (NetworkMessage)(((NetworkEvent)msg.obj).getMessage()); 
					if(mMsg.getMessage().equals("Response:GetGameObjects"))
					{
						tv.append("Received item list from server!\n");
						if(((NetworkMessageLarge)mMsg).objectDict.containsKey("ItemList"))
						{
							Object pl = ((NetworkMessageLarge)mMsg).objectDict.get("ItemList");
							if(pl instanceof ArrayList<?>)
							{
								ArrayList<LokemonPotion> players = (ArrayList<LokemonPotion>)pl;
								tv.append("Item list successfully extracted! Size : "+players.size()+"\n");
								tv.append("Items received");
								for(LokemonPotion pot : players)
								{
									tv.append(", "+pot.getType());
								}
								tv.append("\n");
							}
							else
							{
								tv.append("Item list is in object dict, but is not an array list!\n");
							}
							
						}
						else
						{
							tv.append("Item list not in object dict!\n");
						}
						
					}
					else if(mMsg.getMessage().equals("Response:GetPlayers"))
					{
						tv.append("Received player list from server!\n");
						if(((NetworkMessageLarge)mMsg).objectDict.containsKey("PlayerList"))
						{
							Object pl = ((NetworkMessageLarge)mMsg).objectDict.get("PlayerList");
							if(pl instanceof ArrayList<?>)
							{
								ArrayList<LokemonPlayer> players = (ArrayList<LokemonPlayer>)pl;
								tv.append("Player list successfully extracted! Size : "+players.size()+"\n");
								if(players.size() > 0)
								{
									tv.append("First Item: "+players.get(0).getPlayerID()+"\n");
									tv.append("Player busy? : "+players.get(0).getBusy()+"\n");
									tv.append("Avatar: "+ players.get(0).getAvatar()+", Name: "+players.get(0).getPlayerName()+"\n");
								}
							}
							else
							{
								tv.append("Player list is in object dict, but is not an array list!\n");
							}
							
						}
						else
						{
							tv.append("Item list not in object dict!\n");							
						}
						
					}
					else if(mMsg.getMessage().equals("MapDataResponse"))
					{
						Log.d(NetworkVariables.TAG, "Received spatial object response!");
						if(((NetworkMessageLarge)mMsg).objectDict.containsKey("SpatialObjects"))
						{
							Object pl = ((NetworkMessageLarge)mMsg).objectDict.get("SpatialObjects");
							if(pl instanceof ArrayList<?>)
							{
								ArrayList<LokemonSpatialObject> players = (ArrayList<LokemonSpatialObject>)pl;
								tv.append("Player list successfully extracted! Size : "+players.size()+"\n");
								if(players.size() > 0)
								{
									tv.append("Number of objects: "+players.size()+"\n");										
								}
							}
							else
							{
								tv.append("Player list is in object dict, but is not an array list!\n");
							}
						}
						
					}
					else
					{
						tv.append("Received this from server: "+mMsg.getMessage()+"\n");
					}
					break;
				case PLAYER_REGISTERED:
					tv.append("Player registered with server! \n");
					break;
				default:
					tv.append("Unrecognised event of type "+ NetworkComBinder.EventType.values()[msg.what] + " received.\n");					
			}
		}
	}
	
	final Messenger eventMessenger = new Messenger(new eventHandler());
		
	
	public void onStop()
	{
		super.onStop();
	}
	
	public void onDestroy()
	{
		super.onDestroy();
	}
}
