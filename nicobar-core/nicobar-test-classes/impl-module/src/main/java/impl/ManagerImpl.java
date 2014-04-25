package impl;

/**
 * @author Aaron Tull
 *
 */
public class ManagerImpl implements interfaces.Manager {
    public String supervise(interfaces.Helper worker) {
        return getClass().getName() + " supervising " + worker.getClass().getName() + " doing " + worker.doWork();
    }
}