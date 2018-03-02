package si.pronic.noise;

import net.rim.device.api.ui.UiApplication;

public class NoiseMeter extends UiApplication 
{

    public static void main(String[] args) 
    {
        NoiseMeter theApp = new NoiseMeter();
        theApp.enterEventDispatcher();
    }

    public NoiseMeter()
    {
        int directions = net.rim.device.api.system.Display.DIRECTION_NORTH;
        net.rim.device.api.ui.Ui.getUiEngineInstance().setAcceptableDirections(directions);
        NoiseMeterScreen screen = new NoiseMeterScreen();
        pushScreen(screen);
    }
}
