package bravo.utils;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

public class MemoryInfo {
    
    public static long freeMemory() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        return hal.getMemory().getAvailable();
    }
    
}
