package engine.mobileAI.Threads;

import engine.gameManager.CompanionManager;
import engine.gameManager.ConfigManager;
import engine.mobileAI.MobAI;
import engine.gameManager.ZoneManager;
import engine.objects.Mob;
import engine.objects.Zone;
import engine.server.MBServerStatics;
import org.pmw.tinylog.Logger;

public class MobAIThread implements Runnable{
    public static int AI_BASE_AGGRO_RANGE = 60;
    public static int AI_DROP_AGGRO_RANGE = 60;
    public static int AI_PULSE_MOB_THRESHOLD = 200;
    public static int AI_PATROL_DIVISOR = 15;
    public static float AI_CAST_FREQUENCY;
    // Thread constructor

    public MobAIThread() {
        Logger.info(" MobAIThread thread has started!");

    }

    @Override
    public void run() {
        //cache config value for mobile casting delay
        AI_CAST_FREQUENCY = Float.parseFloat(ConfigManager.MB_AI_CAST_FREQUENCY.getValue());
        AI_BASE_AGGRO_RANGE = (int)(60 * Float.parseFloat(ConfigManager.MB_AI_AGGRO_RANGE.getValue()));
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            CompanionManager.pulse_companions();
            for (Zone zone : ZoneManager.getAllZones()) {

                for (Mob mob : zone.zoneMobSet) {

                    try {
                        if (mob != null && mob.companionType == null)
                            MobAI.DetermineAction(mob);
                    } catch (Exception e) {
                        Logger.error("Mob: " + mob.getName() + " UUID: " + mob.getObjectUUID() + " ERROR: " + e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public static void startAIThread() {
        Thread aiThread;
        aiThread = new Thread(new MobAIThread());
        aiThread.setName("aiThread");
        aiThread.start();
    }
}
