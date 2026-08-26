package com.betawithgamma.microstructure;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FyersBookReconstructorTest {
    private static FyersProtoDecoder.LevelUpdate full(int num,long price,long qty,long nord){return new FyersProtoDecoder.LevelUpdate(num,price,qty,nord);}
    private static FyersProtoDecoder.Message msg(long seq,boolean snapshot,List<FyersProtoDecoder.LevelUpdate> asks,List<FyersProtoDecoder.LevelUpdate>bids){
        var d=new FyersProtoDecoder.DepthUpdate(1000L,1000L,asks,bids);
        var f=new FyersProtoDecoder.Feed("token","NSE:TEST",seq,1L,2L,d);
        return new FyersProtoDecoder.Message(6,snapshot,false,"",List.of(f));
    }
    private static List<FyersProtoDecoder.LevelUpdate> asks(){var x=new ArrayList<FyersProtoDecoder.LevelUpdate>();for(int i=0;i<50;i++)x.add(full(i,10000+i*5,100+i,1+i));return x;}
    private static List<FyersProtoDecoder.LevelUpdate> bids(){var x=new ArrayList<FyersProtoDecoder.LevelUpdate>();for(int i=0;i<50;i++)x.add(full(i,9995-i*5,100+i,1+i));return x;}

    @Test public void sparseUpdateUsesNumAndPreservesMissingFields(){
        var r=new FyersBookReconstructor();assertTrue(r.accept(msg(100,true,asks(),bids())).get(0).certified());
        var au=List.of(new FyersProtoDecoder.LevelUpdate(5,null,999L,null));
        var bu=List.of(new FyersProtoDecoder.LevelUpdate(9,9949L,null,null));
        var s=r.accept(msg(101,false,au,bu)).get(0);
        assertTrue(s.certified());assertTrue(s.canonical().contains("10025,999,6"));assertTrue(s.canonical().contains("9949,109,10"));
    }

    @Test public void sequenceGapFailsClosedUntilSnapshot(){
        var r=new FyersBookReconstructor();r.accept(msg(100,true,asks(),bids()));
        var s=r.accept(msg(102,false,List.of(),List.of())).get(0);assertFalse(s.certified());assertEquals("SEQUENCE_GAP",s.reason());
        var recovered=r.accept(msg(200,true,asks(),bids())).get(0);assertTrue(recovered.certified());
    }

    @Test public void crossedBookIsRejectedNotRepaired(){
        var a=asks();a.set(0,full(0,9900,100,1));
        var r=new FyersBookReconstructor();var s=r.accept(msg(1,true,a,bids())).get(0);assertFalse(s.certified());assertEquals("CROSSED",s.reason());
    }
}
