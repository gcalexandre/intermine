package org.intermine.dataconversion;

/*
 * Copyright (C) 2002-2004 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.sql.SQLException;

import org.intermine.metadata.Model;
import org.intermine.sql.Database;

import org.apache.log4j.Logger;

/**
 * An implementation of the DBReader interface that attempts to speed up the iterator by performing
 * reads in a separate thread before they are asked for.
 *
 * @author Matthew Wakeling
 */
public class ReadAheadDBReader extends BatchingDBReader
{
    private static final Logger LOG = Logger.getLogger(ReadAheadDBReader.class);

    protected DBBatch referenceBatch, nextBatch;
    protected SQLException nextProblem;
    protected boolean ready = false;
    protected WorkerThread workerThread;

    /**
     * Constructs a new ReadAheadDBReader.
     *
     * @param db the Database to access
     * @param model a Model from which to get collection names to batch
     */
    public ReadAheadDBReader(Database db, Model model) {
        super(db, model);
        referenceBatch = null;
        nextBatch = null;
        workerThread = new WorkerThread();
        Thread thread = new Thread(workerThread);
        thread.setDaemon(true);
        thread.setName("ReadAheadDBReader WorkerThread");
        thread.start();
    }

    /**
     * Creates a new DBBatch for a given offset.
     *
     * @param previous the previous batch, or null if this is the first
     * @return a DBBatch
     * @throws SQLException if the database has a problem
     */
    protected synchronized DBBatch getBatch(DBBatch previous) throws SQLException {
        if ((previous != referenceBatch) || (!ready)) {
            referenceBatch = super.getBatch(previous);
        } else {
            long start = System.currentTimeMillis();
            while ((nextBatch == null) && (nextProblem == null) && ready) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            long end = System.currentTimeMillis();
            if (end - start > 10) {
                LOG.info("Had to wait for worker thread for " + (end - start) + " ms");
            }
            referenceBatch = nextBatch;
            if (nextProblem != null) {
                throw nextProblem;
            }
        }
        nextBatch = null;
        nextProblem = null;
        ready = true;
        notify();
        return referenceBatch;
    }

    private void doWork() {
        DBBatch previous = null;
        synchronized (this) {
            while ((nextBatch != null) || (nextProblem != null) || (!ready)) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            previous = referenceBatch;
        }
        DBBatch next = null;
        SQLException problem = null;
        try {
            next = super.getBatch(previous);
        } catch (SQLException e) {
            LOG.error("Problem in batch: " + e);
            problem = e;
        }
        synchronized (this) {
            if (referenceBatch != previous) {
                LOG.info("Batch prediction failed in worker thread");
            } else {
                nextBatch = next;
                nextProblem = problem;
                notify();
            }
        }
    }

    private class WorkerThread implements Runnable
    {
        public WorkerThread() {
        }

        public void run() {
            while (true) {
                doWork();
            }
        }
    }
}
        
