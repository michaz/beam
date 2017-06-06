package beam.sim.traveltime;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.concurrent.Executors;

/**
 * BEAM
 */
public class TripInfoCacheMapDB {
    private static final Logger log = Logger.getLogger(TripInfoCacheMapDB.class);

    private File dbFile, dbTempFile;
    public DB dbDisk, dbMemory;
    public HTreeMap<?, ?> onDisk;
    public HTreeMap<String, TripInformation> inMemory;
    public HashSet<String> keySet = new HashSet<>();

    public TripInfoCacheMapDB(String dbPath) {
        String permanentDbPath = dbPath;
        String temporaryDbPath = dbPath + "-LIVE";
        dbFile = new File(permanentDbPath);
        dbTempFile = new File(temporaryDbPath);
        if (dbFile.exists()) {
            try {
                FileUtils.copyFile(dbFile, dbTempFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (dbTempFile.exists()) {
            FileUtils.deleteQuietly(dbTempFile);
        }
        openDBs();
        log.info("In memory cache opened of size: "+inMemory.size()+" with on disk overflow of size "+onDisk.size());
    }

    public void openDBs(){
        dbDisk = DBMaker.fileDB(dbTempFile).make();

        dbMemory = DBMaker.memoryDB().make(); //sizeLimit(20*1024*1024*1024).make();

        // Big map populated with data expired from cache
        onDisk = dbDisk.hashMap("onDisk").createOrOpen();

        // fast in-memory collection with limited size
        inMemory = dbMemory.hashMap("inMemory").
                expireOverflow((HTreeMap)onDisk).
                expireExecutor(Executors.newScheduledThreadPool(2)).
                create();
                //this registers overflow to `onDisk`
//                .expireOverflow((HTreeMap) onDisk)
                //good idea is to enable background expiration
//                .expireExecutor(Executors.newScheduledThreadPool(2))
//                .create();

        // Populate the keySet for fast "containsKey" query
        keySet.addAll(onDisk.keySet());

        analyze();
    }

    public synchronized TripInformation getTripInformation(String key){
//        return inMemory.isClosed() ? null : inMemory.get(key);
        return (TripInformation) inMemory.get(key);
    }

    public synchronized boolean containsKey(String key){
        return keySet.contains(key);
    }

    public Integer getCacheSize() {
        return inMemory.size();
    }

    public String cacheSizeAsString() {
//        return "InMem size: "+(inMemory.isClosed()?"<closed>":inMemory.size())+
//                " DiskOverflow size: "+(onDisk.isClosed()?"<closed>":onDisk.size());
        return "";
    }

    public synchronized void putTripInformation(String key, TripInformation tripInfo){
//        if(!inMemory.isClosed()){
            inMemory.put(key,tripInfo);
            keySet.add(key);
//        }
    }
    public String toString(){
        return "MapDB Cache "+cacheSizeAsString();
    }

    public synchronized void persistStore(){
        log.info("In memory cache about to be persisted of size: "+inMemory.size()+" with on disk overflow of size "+onDisk.size());
        close();
        try {
            FileUtils.copyFile(dbTempFile,dbFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        openDBs();
        log.info("In memory cache now persisted of size: "+inMemory.size()+" with on disk overflow of size "+onDisk.size());
    }
    public void close() {
//        inMemory.clearWithExpire();
        inMemory.close();
        dbMemory.close();
        onDisk.close();
        dbDisk.close();
    }
    public void analyze(){
        LinkedList<String> badKeys = new LinkedList<>();
        LinkedHashMap<String,Integer> badTimeCount = new LinkedHashMap<>();
        for(Object key : onDisk.keySet()){
            String keyStr = (String)key;
            TripInformation trip = (TripInformation) onDisk.get(keyStr);
            if(trip.getRouteInfoElements().size()==0) {
                badKeys.add(keyStr);
                String time = keyStr.split("---")[2];
                if(!badTimeCount.containsKey(time)){
                    badTimeCount.put(time,0);
                }
                badTimeCount.put(time,badTimeCount.get(time)+1);
            }
        }
        log.info(badKeys.size() + " bad trips found out of " + onDisk.size());
    }
}
