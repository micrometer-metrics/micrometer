package io.micrometer.dolphindb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.xxdb.*;

public class DolphinDBSession {

//    private final DolphinDBConfig config;

    private static final Logger logger = LoggerFactory.getLogger(DolphinDBMeterRegistry.class);

    private boolean success;

    public static DBConnection conn = new DBConnection();
    public DolphinDBSession(DolphinDBConfig config) {
//        this.config = config;
        try {
            success = conn.connect(config.ip(), config.port(), config.userName(), config.password());
        }
        catch (Throwable e) {
            logger.error("Unable to connect to the DolphinDB");
        }
    }

    public DBConnection getConn() {

        if (success) {
            return conn;
        }
        else {
            System.out.println("Unable to connect to the DolphinDB");
            logger.error("Unable to connect to the DolphinDB");
            return new DBConnection();
        }
    }
}
