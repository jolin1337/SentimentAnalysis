/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.joli.maltparser;

import java.util.List;

/**
 *
 * @author jolin1337
 */
public class Helper {
    
    public static final boolean inArray(String[] array, String str) {
        for(String sc : array) 
            if(sc.equals(str))
                return true;
        
        return false;
    }
    public static final boolean inArray(List<String> array, String str) {
        for(String sc : array) 
            if(sc.equals(str))
                return true;
        
        return false;
    }
}
