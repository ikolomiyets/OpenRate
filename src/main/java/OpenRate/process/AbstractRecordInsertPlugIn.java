

package OpenRate.process;

import OpenRate.OpenRate;
import OpenRate.exception.ProcessingException;
import OpenRate.record.HeaderRecord;
import OpenRate.record.IRecord;
import OpenRate.record.TrailerRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * The AbstractRecordInsertPlugIn provides the base class for inserting records
 * in the processing chain at any point. It is not a general use transport
 * plug-in and instead provides targeted functionality for creating records
 * and inserting them into the processing chain.
 */
public abstract class AbstractRecordInsertPlugIn extends AbstractPlugIn
{
 /**
  * Process() gets the records from the upstream FIFO and iterates through all
  * of the records in the collection that is gets. For each of these records,
  * the appropriate method is called in the implementation class, and the
  * results are collected and are emptied out of the downstream FIFO.
  *
  * This method id triggered by the wait/notify mechanism and wakes up as soon
  * as a batch of records is pushed into the input buffer (either real time or
  * batch records).
  *
  * This method must be thread safe as it is the fundamental multi-processing
  * hook in the framework.
  *
  * This module implements batch/real time convergence. It can be triggered by
  * either batch or real time events, but in any case, real time events are
  * processed in precedence to the batch events. This is achieved by processing
  * all real time events through the pipeline between each batch event. This
  * creates a very high priority path through the pipe for real time events.
  */
  @Override
  public void process()
  {
    Iterator<IRecord> iter;
    long startTime;
    long endTime;
    long BatchTime;

    // processing list for batch events
    ArrayList<IRecord> in;

    // output buffer for preparing the events to push
    ArrayList<IRecord> outTemp = new ArrayList<>();

    // processing list for real time events
    ArrayList<IRecord> inRT;

    // output buffer for preparing the events to push
    ArrayList<IRecord> outRTTemp = new ArrayList<>();

    // Print the thread startup message
    OpenRate.getOpenRateStatsLog().debug("PlugIn <" + Thread.currentThread().getName() +
                   "> started, pulling from buffer <" + getBatchInbound().toString() +
                   ">, pushing to buffer <" + getBatchOutbound().toString() + ">");

    // Check to see if we have the naughty batch size of 0. this is usually
    // because someone has overwritten the init() without calling the parent
    // init
    if (getBatchSize() == 0)
    {
      message = "Batch size is 0 in plugin <" + this.toString() + ">. " +
              "Please ensure that you have called the parent init().";
      getExceptionHandler().reportException(new ProcessingException(message,getSymbolicName()));
    }

    // main thread loop. This will not be exited until the thread is
    // ordered to shut down.
    while (true)
    {
      // get the timestamp of the start of the processing. This will happen on
      // each thread wake up
      startTime = System.currentTimeMillis();

      // get the batch records to process
      in = (ArrayList<IRecord>) getBatchInbound().pull(getBatchSize());

      int ThisBatchRecordCount = in.size();

      if (ThisBatchRecordCount > 0)
      {
        if (isActive())
        {
          iter = in.iterator();

          // Process each of the block of records and trigger the processing
          // functions for each type (header, trailer, valid and error)
          while (iter.hasNext())
          {
            // Get the formatted information from the record
            IRecord r = iter.next();
            Collection<IRecord> rb = null;

            // Trigger the correct user level functions according to the state of
            // the record
            if (r.isValid())
            {
              rb = procValidRecordBatch(r);
            }
            else
            {
              if (r.isErrored())
              {
                rb = procErrorRecordBatch(r);
              }
              else
              {
                if (r instanceof HeaderRecord)
                {
                  rb = procHeaderBatch(r);
                  incStreamsProcessed();
                }

                if (r instanceof TrailerRecord)
                {
                  rb = procTrailerBatch(r);
                }
              }
            }

            // push the records into the output buffer
            if (rb != null)
            {
              Iterator<IRecord> rbi = rb.iterator();
              while (rbi.hasNext())
              {
                outTemp.add(rbi.next());
              }
            }
          }
        }

        // Push the list we constructed, not the original batch
        getBatchOutbound().push(outTemp);

        OpenRate.getOpenRateStatsLog().debug("PlugIn <" + Thread.currentThread().getName() + "> pushed <" + String.valueOf(ThisBatchRecordCount) + "> batch records to buffer <" + getBatchOutbound().toString() + ">");

        int outBufferCapacity = getBatchOutbound().getEventCount();

        endTime = System.currentTimeMillis();
        BatchTime = (endTime - startTime);
        updateProcessingTime(BatchTime);

        while (outBufferCapacity > getBufferSize())
        {
          incBufferHits();
          OpenRate.getOpenRateStatsLog().debug("PlugIn <" + Thread.currentThread().getName() + "> buffer high water mark! Buffer max = <" + getBufferSize() + "> current count = <" + outBufferCapacity + ">");
          try
          {
            Thread.sleep(100);
          }
          catch (InterruptedException ex)
          {
            // Nothing
          }
          outBufferCapacity = getBatchOutbound().getEventCount();
        }

        OpenRate.getOpenRateStatsLog().info(
          "Plugin <" + Thread.currentThread().getName() + "> processed <" +
          String.valueOf(ThisBatchRecordCount) + "> events in <" + BatchTime + "> ms" );

        // Update the statistics
        updateBatchRecordsProcessed(ThisBatchRecordCount);
      }
      else
      {
        OpenRate.getOpenRateStatsLog().debug(
              "PlugIn <" + Thread.currentThread().getName() + "> going to sleep");

        // We want to shut down the processing
        if (getShutdownFlag() == true)
        {
          OpenRate.getOpenRateStatsLog().debug("PlugIn <" + Thread.currentThread().getName() + "> shut down. Exiting.");
          break;
        }

        // If not marked for shutdown, wait for notification from the
        // suppler that new records are available for processing.
        try
        {
          synchronized (this)
          {
            wait();
          }
        }
        catch (InterruptedException e)
        {
          // ignore interrupt exceptions
        }
      } // else
    } // while loop
  }

// -----------------------------------------------------------------------------
// ----------------- Start of published hookable functions ---------------------
// -----------------------------------------------------------------------------

 /**
  * This is called when the synthetic Header record is encountered, and has the
  * meaning that the stream is starting. In this case we have to open a new
  * dump file each time a stream starts.
  *
  * @param r The record we are working on
  * @return The processed record
  */
  public abstract Collection<IRecord> procHeaderBatch(IRecord r);

 /**
  * This is called when a data record is encountered. You should do any normal
  * processing here.
  *
  * @param r The record we are working on
  * @return The processed record
  */
  public abstract Collection<IRecord> procValidRecordBatch(IRecord r);

 /**
  * This is called when a data record with errors is encountered. You should do
  * any processing here that you have to do for error records, e.g. statistics,
  * special handling, even error correction!
  *
  * @param r The record we are working on
  * @return The processed record
  */
  public abstract Collection<IRecord> procErrorRecordBatch(IRecord r);

 /**
  * This is called when the synthetic trailer record is encountered, and has the
  * meaning that the stream is now finished. In this example, all we do is
  * pass the control back to the transactional layer.
  *
  * @param r The record we are working on
  * @return The processed record
  */
  public abstract Collection<IRecord> procTrailerBatch(IRecord r);
}
