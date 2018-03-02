package si.pronic.noise;

import java.io.ByteArrayOutputStream;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.RecordControl;

import net.rim.device.api.applicationcontrol.ApplicationPermissions;
import net.rim.device.api.applicationcontrol.ApplicationPermissionsManager;
import net.rim.device.api.system.Backlight;
import net.rim.device.api.system.Bitmap;
import net.rim.device.api.system.Display;
import net.rim.device.api.system.PersistentObject;
import net.rim.device.api.system.PersistentStore;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.component.BitmapField;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.util.MathUtilities;

public class NoiseMeterScreen extends MainScreen implements Runnable
{
	public static final long PERS_ID = 999911118822L;
	
	private BitmapField infoLabel;
	private Bitmap bitmap;
	private Graphics g;
	
	private boolean ok = true;
	private boolean leave = false;
	private int powerCache[];
	private int graphCache[];
	private int counter = 0;
	
	private Font normalFont;
	private Font largeFont;
	private Font subLargeFont;
	
	private int screenWidth;
	private int screenHeight;
	private int fontHeight;
	private int fontWidth;
	private int fontXWidth;
	private int halfScreenHeight;
	private int halfScreenWidth;
	private int graphHeight;
	private int tempGraphWidth;
	private int deltaGraphH;
	private int nGraphW;
	private int graphWidth;
	private int graphX;
	private int graphY;
	private int barHeight;
	
	private int currPower = 100;
	private int currLogPower = 7;
	private int currLevel = 0;
	private int delta = 0;
	
	public NoiseMeterScreen()
    {
		ApplicationPermissionsManager permissionManager =  ApplicationPermissionsManager.getInstance();
		ApplicationPermissions permissions = new ApplicationPermissions();
		permissions.addPermission(ApplicationPermissions.PERMISSION_RECORDING);
		permissionManager.invokePermissionsRequest(permissions);
		
		PersistentObject p = PersistentStore.getPersistentObject(PERS_ID);
		if (p == null || p.getContents() == null)
		{
			p.setContents(new Integer(delta));
			p.commit();
		}
		delta = ((Integer)p.getContents()).intValue();
		
		screenWidth = Display.getWidth();
        screenHeight = Display.getHeight();
        
        if (screenWidth > screenHeight)
        {
            bitmap = new Bitmap(screenWidth, screenWidth);
        }
        else
        {
            bitmap = new Bitmap(screenHeight, screenHeight);
        }

        g = Graphics.create(bitmap);
        
        infoLabel = new BitmapField(bitmap);
        add(infoLabel);

        fontHeight = g.getFont().getHeight() + 5;
        fontWidth = g.getFont().getAdvance("100 dB") + 5;
        fontXWidth = g.getFont().getAdvance("8 min");
        normalFont = g.getFont();
        largeFont = g.getFont().derive(Font.BOLD, (int)(g.getFont().getHeight() * 2.5));
        subLargeFont = g.getFont().derive(Font.BOLD, g.getFont().getHeight());
        
        calcUI();

        Thread thr = new Thread(this);
        thr.start();
    }
	
    protected void makeMenu(Menu menu, int instance)
    {
    	super.makeMenu(menu, instance);
    	if (instance != Menu.INSTANCE_CONTEXT)
    	{
//    		menu.add(resetMenuItem);
    		menu.add(helpMenuItem);
    		menu.add(aboutMenuItem);
    	}
    }

    private MenuItem resetMenuItem = new MenuItem("Reset", 110, 10)
    {
    	public void run()
    	{
    		PersistentObject p = PersistentStore.getPersistentObject(PERS_ID);
    		delta = 0;
    		p.setContents(new Integer(delta));
    		p.commit();
			UiApplication.getUiApplication().invokeLater(new Runnable(){public void run(){
				Dialog.inform("Recorder's calibration is reset.");
				return;
			}});
    	}
    }; 
    
    private MenuItem aboutMenuItem = new MenuItem("About", 110, 10)
    {
    	public void run()
    	{
    		String text = "NOISE METER\n\nNoise measuring tool\n\n" +
    				"(c)2012, Pronic\n";
    		Dialog.alert(text);
    	}
    }; 
    
/*    public boolean navigationClick(int status, int time)
    {
		PersistentObject p = PersistentStore.getPersistentObject(PERS_ID);
		if (delta == 0)
		{
			delta = currPower;
			p.setContents(new Integer(delta));
			p.commit();
			UiApplication.getUiApplication().invokeLater(new Runnable(){public void run(){
				Dialog.inform("Recorder is calibrated.");
				return;
			}});
		}
		else
		{
			delta = 0;
			p.setContents(new Integer(delta));
			p.commit();
			UiApplication.getUiApplication().invokeLater(new Runnable(){public void run(){
				Dialog.inform("Recorder's calibration is reset.");
				return;
			}});
		}
		return true;
    }*/
    
    private MenuItem helpMenuItem = new MenuItem("Help", 110, 10)
    {
    	public void run()
    	{
    		String text = "Noise meter records noises in the vicinity of the device and calculates their " +
    				"intensity in % of linear microphone signal, decibels and 16-bit digital amplitude values (DAV).\n\n";
//    				"To calibrate your device, go to a silent place and press TRACKPAD/TRACKBALL. Calibrated value will be stored " +
//    				"in the device database. To reset calibration choose 'Reset' from menu.\n\n"

    		Dialog.alert(text);
    	}
    }; 
    
    private void calcUI()
    {
		screenWidth = Display.getWidth();
        screenHeight = Display.getHeight();
        
		halfScreenHeight = screenHeight / 2;
		halfScreenWidth = screenWidth / 2;
		graphHeight = halfScreenHeight - 20 - fontHeight;
		tempGraphWidth = screenWidth - fontWidth - 20;
		deltaGraphH = graphHeight / 4;
		nGraphW = (tempGraphWidth - 1) / 60;
		graphWidth = nGraphW * 60;
		graphX = (screenWidth - graphWidth - fontWidth) / 2;
		graphY = halfScreenHeight + 10;
		barHeight = (halfScreenHeight - 85) / 10;
		
	    powerCache = new int[nGraphW * 20 * 10]; // 10-times per second
	    graphCache = new int[nGraphW * 20];
    }

    public void run()
    {
    	Player p;
    	try 
    	{
			p = Manager.createPlayer("capture://audio?encoding=audio/basic");
			p.realize();
			RecordControl rc = (RecordControl)p.getControl("RecordControl");
			ByteArrayOutputStream out = new ByteArrayOutputStream(20000);
			rc.setRecordStream(out);
			byte buf[] = new byte[2 * powerCache.length];
	
			while (ok)
			{
				if (screenWidth != Display.getWidth())
				{
					calcUI();
				}
				
				try
				{
					out.reset();
					rc.startRecord();
					p.start();
				}
				catch (Exception ex)
				{
				}

				Thread.sleep(90);
				
				try
				{
					p.stop();
					rc.stopRecord();
				}
				catch (Exception ex)
				{
				}
				
				buf = out.toByteArray();

				int maxPower = 0;
				for (int i = 0; i < buf.length; i += 2)
				{
					byte data[] = {buf[i], buf[i + 1]};
					
					int power = signedShortToInt(data);
					
					if (power > 32767)
					{
						power = 100;
					}
					else if (power < 100)
					{
						power = 100;
					}
					
					if (power > maxPower)
					{
						maxPower = power;
					}
				}

				for (int j = powerCache.length - 1; j > 0; j--)
				{
					powerCache[j] = powerCache[j - 1];
				}
				powerCache[0] = maxPower;
				           
				counter++;
				
/*				int goalPower = 0;
				for (int j = 0; j < 10; j++)
				{
					goalPower += powerCache[j];
				}
				goalPower = (int)(goalPower / 10);
				
				if (currPower > 3000)
				{
					if (currPower > goalPower)
					{
						if (currPower - goalPower < 3000)
						{
							currPower = goalPower;
						}
						else
						{
							currPower -= 3000;
						}
					}
					else if (currPower < goalPower)
					{
						if (goalPower - currPower < 3000)
						{
							currPower = goalPower;
						}
						else
						{
							currPower += 3000;
						}
					}
				}
				else
				{
					if (currPower > goalPower)
					{
						if (currPower - goalPower < 300)
						{
							currPower = goalPower;
						}
						else
						{
							currPower -= 300;
						}
					}
					else if (currPower < goalPower)
					{
						if (goalPower - currPower < 300)
						{
							currPower = goalPower;
						}
						else
						{
							currPower += 300;
						}
					}
				}
				currPower -= delta;*/
				currPower = maxPower;
				if (currPower < 100)
				{
					currPower = 100;
				}
				
				currLevel = (int)(currPower * 100 / 32767);
				currLogPower = (int)(22 * MathUtilities.log(currPower) / MathUtilities.log(10) - 14);

				if (counter == 10)
				{
					counter = 0;
					for (int j = graphCache.length - 1; j > 0; j--)
					{
						graphCache[j] = graphCache[j - 1];
					}
					graphCache[0] = currLogPower;
					Backlight.enable(true);
				}

				// reset screen
				g.setColor(0x000000);
				g.fillRect(0, 0, screenWidth, screenHeight);
				g.setFont(normalFont);

				// background limit
				g.setColor(0x004000);
				g.fillRect(graphX + fontWidth, graphY, graphWidth, graphHeight);
				g.setColor(0x404000);
				g.fillRect(graphX + fontWidth, graphY, graphWidth, (int)(graphHeight * 0.5));
				g.setColor(0x400000);
				g.fillRect(graphX + fontWidth, graphY, graphWidth, (int)(graphHeight * 0.3));

				// graph
				for (int i = 0; i < graphCache.length - 1; i++)
				{
					g.setColor(0xffffff);
					g.drawLine(graphX + 1 + 3 * i + fontWidth, 
							graphY + graphHeight - 2 - ((graphHeight - 2) * graphCache[i] / 100), 
							graphX + 1 + 3 * i + fontWidth + 3, 
							graphY + graphHeight - 2 - ((graphHeight - 2) * graphCache[i + 1] / 100));
				}

				// upper right border
				g.setColor(0x777777);
				g.drawRect(halfScreenWidth + 10, 10, halfScreenWidth - 20, halfScreenHeight - 20);

				// color (POINTER)
				for (int i = 9; i >= 0; i--)
				{
					if (currLogPower >= i * 10 + 1)
					{
						if (i == 9 || i == 8 || i == 7)
						{
							g.setColor(0xff0000);
						}
						else if (i == 5 || i == 6)
						{
							g.setColor(0xffff00);
						}
						else
						{
							g.setColor(0x00ff00);
						}
					}
					else
					{
						g.setColor(0x101010);
					}
					g.fillRect(halfScreenWidth + 20, 20 + (9 - i) * (barHeight + 5), halfScreenWidth - 40, barHeight);					
				}
				
				// bottom border
				g.setColor(0x777777);
				g.drawRect(graphX + fontWidth, graphY, graphWidth, graphHeight);
				for (int i = 1; i < 4; i++)
				{
				    g.drawLine(graphX + fontWidth, graphY + i * deltaGraphH, 
				    		graphX + fontWidth + graphWidth - 1, graphY + i * deltaGraphH);
				}
				for (int i = 1; i < nGraphW; i++)
				{
				    g.drawLine(graphX + fontWidth + i * 60, graphY, 
				    		graphX + fontWidth + i * 60, graphY + graphHeight - 1);
				    if (i % 3 == 0)
				    {
				    	g.drawText((i / 3) + " min", graphX + fontWidth - fontXWidth / 2 + i * 60, 
				    			graphY + graphHeight + 1);
				    }
				}
				
				// graph labels
				g.drawText("100 dB", graphX, graphY);
				g.drawText("50 dB", graphX, graphY + graphHeight / 2 - fontHeight / 2);
				g.drawText("0 dB", graphX, graphY + graphHeight - fontHeight);

				// main
				g.setColor(0xffffff);
				int largeFontH = largeFont.getHeight();
				int largeFontW = largeFont.getAdvance(currLogPower + " dB");  // POINTER
				int subLargeFontH = subLargeFont.getHeight();
				int subLargeFontW1 = subLargeFont.getAdvance(currLevel + " %"); // POINTER
				int subLargeFontW2 = subLargeFont.getAdvance(currPower + " DAV"); // POINTER
				g.setFont(largeFont);
				g.drawText(currLogPower + " dB", (halfScreenWidth - largeFontW) / 2, (halfScreenHeight / 2 - 
						largeFontH) / 2);
				g.setFont(subLargeFont);
				g.drawText(currLevel + " %", (halfScreenWidth - subLargeFontW1) / 2, halfScreenHeight / 2 + 
						(halfScreenHeight / 2 - 2 * subLargeFontH - 10) / 2);
				g.drawText(currPower + " DAV", (halfScreenWidth - subLargeFontW2) / 2, halfScreenHeight / 2 +
						(halfScreenHeight / 2 - 2 * subLargeFontH - 10) / 2 + subLargeFontH + 10);

				UiApplication.getUiApplication().invokeLater(new Runnable(){public void run(){
					
					infoLabel.setBitmap(bitmap);
					infoLabel.getScreen().updateDisplay();

					return;}});
			
/*				try 
				{
					Thread.sleep(100);
				} 
				catch (InterruptedException ex)
				{
				}*/
			} 
			rc.commit();
			p.stop();
			p.close();
		}
    	catch (Exception ex) 
		{
 		} 
		leave = true;

    }
    
    public boolean onClose()
    {
    	ok = false;
    	while (!leave)
    	{
	    	try 
	    	{
				Thread.sleep(100);
			} 
	    	catch (InterruptedException ex) 
			{
			}
    	}
    	System.exit(0);    	
    	return true;
    }

    public static int signedShortToInt(byte b[])
    {
    	int result = (b[0] & 0xff) | (b[1] & 0xff) << 8;
    	return result;
    }
}
