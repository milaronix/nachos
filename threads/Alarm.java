package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
    Machine.timer().setInterruptHandler(new Runnable() {
        public void run() { timerInterrupt(); }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        //KThread.currentThread().yield();//ORIGINAL

        //int ia = 0;

        //System.out.println("entro al timerInterrupt " + Machine.timer().getTime());

        //Lib.assertTrue(Machine.interrupt().disabled());
        long time = Machine.timer().getTime();
        //if(cola.get(ia).getTime() > time)
          //  return;

        for(int i = 0; i < cola.size(); i++){
            if(cola.get(i).getTime() <= time){
                cola.get(i).getThread().ready();                
                cola.remove(i);
            }

        }
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param   x   the minimum number of clock ticks to wait.
     *
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
    // for now, cheat just to get something working (busy waiting is bad)
    long wakeTime = Machine.timer().getTime() + x;
    /*while (wakeTime > Machine.timer().getTime())
        KThread.yield();*/
        if(x > 0){
            boolean intStatus = Machine.interrupt().disable();
            System.out.println("thread waintuntil: " + KThread.currentThread().getName());
            System.out.println("tiempo: " + wakeTime);
            waitingThread toAlarm = new waitingThread();
            toAlarm.setTime(wakeTime);
            toAlarm.setThread(KThread.currentThread());
            cola.add(toAlarm);
            KThread.currentThread().sleep();
            Machine.interrupt().restore(intStatus);
        }    
    }

    private class waitingThread{
        long time;
        KThread thread = null;
        

        long getTime (){
            return time;
        }
        
        KThread getThread (){
            return thread;
        }

        void setTime(long nTime){
            time = nTime;
        }
        void setThread(KThread nThread){
            thread = nThread;
        }

    }

    //private static ThreadQueue waitingThread = null;
    private ArrayList <waitingThread> cola = new ArrayList<waitingThread>();
   //private static ThreadQueue priorityQueue = null;
    
}
