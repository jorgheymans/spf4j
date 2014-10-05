/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.spf4j.perf.tsdb;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Future;
import org.junit.Test;
import org.spf4j.base.AbstractRunnable;
import org.spf4j.concurrent.DefaultExecutor;

/**
 *
 * @author zoly
 */
public final class TSDBTailerTest {
    
    public TSDBTailerTest() {
    }

    private static final String FILE_NAME = System.getProperty("java.io.tmpdir") + "/testdb.tsdb";
    
    /**
     * Test of close method, of class TimeSeriesDatabase.
     */
    @Test
    public void testWriteTSDB() throws Exception {
        System.out.println("testWriteTSDB");
        if (new File(FILE_NAME).delete()) {
            System.out.println("existing tsdb file deleted");
        }
        final TimeSeriesDatabase instance = new TimeSeriesDatabase(FILE_NAME, new byte[] {});
        Future<?> tailFut = DefaultExecutor.INSTANCE.submit(new AbstractRunnable(false) {

            @Override
            public void doRun() throws Exception {
                instance.tail(1, 0, new TSDataHandler() {

                    @Override
                    public void newTable(final String tableName, final String[] columnNames) {
                        System.out.println("New Table: " + tableName
                                + " columns: " + Arrays.toString(columnNames));
                    }

                    @Override
                    public void newData(final String tableName, final TimeSeries data) {
                        System.out.println("Table " + tableName + " - " + data);
                    }
                });
            }
        });
        
        instance.addTSTable("gr1", new byte []{}, 5, new String[]{"a", "b"}, new byte [][] {});
        instance.write(System.currentTimeMillis(), "gr1", new long[] {0, 1});
        Thread.sleep(5);
        instance.write(System.currentTimeMillis(), "gr1", new long[] {1, 2});
        Thread.sleep(5);
        instance.write(System.currentTimeMillis(), "gr1", new long[] {3, 4});
        Thread.sleep(5);
        instance.addTSTable("gr2", new byte []{}, 5, new String[] {"a", "b"}, new byte [][] {});
        instance.write(System.currentTimeMillis(), "gr2",  new long[] {7, 8});
        instance.flush();
        
        instance.addTSTable("gr3", new byte []{}, 5, new String[] {"a", "b"}, new byte [][] {});
        instance.write(System.currentTimeMillis(), "gr3",  new long[] {7, 8});
        Thread.sleep(5);
        instance.write(System.currentTimeMillis(), "gr3",  new long[] {9, 10});
        instance.flush();
        Thread.sleep(1000);
        tailFut.cancel(true);
              
    }




}
