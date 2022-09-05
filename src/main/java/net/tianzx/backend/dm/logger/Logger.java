package net.tianzx.backend.dm.logger;

import java.io.File;

public interface Logger {
    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();

    public static Logger create(String path) {
        File f = new File(path+LoggerImpl.LOG_SUFFIX);
        return null;
    }

    public static Logger open(String path) {
        return null;
    }
}
