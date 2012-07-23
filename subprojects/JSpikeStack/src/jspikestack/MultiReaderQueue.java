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
    
    Collection<LinkedBlockingQueue<T>> queues=new ArrayList();
        
    
    public void removerReader(Queue r)
    {
        queues.remove(r);
    }
            
    public boolean add(T el)
    {   
        for (Queue q:queues)
            q.add(el);
        
        return true;
    }
    
    @Override
    public String toString()
    {   
        int nReaders=queues.size();
        
        String st= "MultiReaderQueue: "+ nReaders + " readers";
        
        if (nReaders<5)
        {   
            st+=" with sizes [";
            
            for (Queue q:queues)
                st+=q.size()+" ";
            
            st+="], respectively";
        }
        return st;
    }
    
    public void clear()
    {
        for (Queue q:queues)
            q.clear();
    }
    
    public Queue<T> addReader()
    {
        LinkedBlockingQueue q=new LinkedBlockingQueue();
        queues.add(q);
        return q;
    }
    
    
    
    
    
    
    
    
}
