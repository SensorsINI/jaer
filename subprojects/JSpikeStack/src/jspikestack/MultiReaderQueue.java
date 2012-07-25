/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author Peter
 */
public class MultiReaderQueue<T> {

//    HashMap<Object,Queue<T>> map=new HashMap();
//    Collection queues;
    
    
    
    class Subscriber<T>
    {   LinkedBlockingQueue<T> queue;
        Comparable<T> comp;
        
        public Subscriber(LinkedBlockingQueue<T> que,Comparable<T> com)
        {
            queue=que;
            comp=com;
        }
    }
    
    final ArrayList<Subscriber> subs=new ArrayList();
    
    
//    Collection<LinkedBlockingQueue<T>> queues=new ArrayList();
        
    
    public void removerReader(Queue r)
    {
        for (Subscriber s: subs)
            if (s.queue==r)
                subs.remove(s);
    }
            
    public boolean add(T el)
    {   //synchronized(subs)
//    {
        for (Subscriber s:subs)
            if (s.comp.compareTo(el)>0)
                s.queue.add(el);
//            else
//                System.out.println("aa");
    //}//   
        return true;
    }
    
    @Override
    synchronized public String toString()
    {   
        synchronized(subs)
        {
            int nReaders=subs.size();

            String st= "MultiReaderQueue: "+ nReaders + " readers";

            if (nReaders<5)
            {   
                st+=" with sizes [";

                for (Subscriber s:subs)
                    st+=s.queue.size()+" ";

                st+="], respectively";
        }
        return st;
        }
    }
    
    public void clear()
    {
        for (Subscriber s:subs)
            s.queue.clear();
    }
    
    synchronized public Queue<T> addReader(Comparable c)
    {
            LinkedBlockingQueue q=new LinkedBlockingQueue();
            subs.add(new Subscriber(q,c));
            return q;
    }
    
    synchronized public Queue<T> addReader()
    {
        return addReader(new Comparable<T>(){
            @Override
            public int compareTo(T o) {
                return 1;
            }
        });
        
    }
    
    
    
    
    
    
}








//
//
//
//
//
//
///*
// * To change this template, choose Tools | Templates
// * and open the template in the editor.
// */
//package jspikestack;
//
//import java.util.*;
//import java.util.concurrent.LinkedBlockingQueue;
//
///**
// *
// * @author Peter
// */
//public class MultiReaderQueue<T> {
//
////    HashMap<Object,Queue<T>> map=new HashMap();
////    Collection queues;
//    
//    
//    
//    class Subscribers<T>
//    {   LinkedBlockingQueue<T> queue;
//        Comparable<T> comp;
//    }
//    
//    ArrayList<Subscribers> subs=new ArrayList();
//    
//    
////    Collection<LinkedBlockingQueue<T>> queues=new ArrayList();
//        
//    
//    public void removerReader(Queue r)
//    {
//        queues.remove(r);
//    }
//            
//    public boolean add(T el)
//    {   
//        for (Queue q:queues)
//            q.add(el);
//        
//        return true;
//    }
//    
//    @Override
//    public String toString()
//    {   
//        int nReaders=queues.size();
//        
//        String st= "MultiReaderQueue: "+ nReaders + " readers";
//        
//        if (nReaders<5)
//        {   
//            st+=" with sizes [";
//            
//            for (Queue q:queues)
//                st+=q.size()+" ";
//            
//            st+="], respectively";
//        }
//        return st;
//    }
//    
//    public void clear()
//    {
//        for (Queue q:queues)
//            q.clear();
//    }
//    
//    public Queue<T> addReader()
//    {
//        LinkedBlockingQueue q=new LinkedBlockingQueue();
//        queues.add(q);
//        return q;
//    }
//    
//    
//    
//    
//    
//    
//    
//    
//}
