package OpenRate.adapter;

import OpenRate.CommonConfig;
import OpenRate.IPipeline;
import OpenRate.OpenRate;
import OpenRate.buffer.IConsumer;
import OpenRate.buffer.IEvent;
import OpenRate.buffer.IMonitor;
import OpenRate.buffer.ISupplier;
import OpenRate.configurationmanager.ClientManager;
import OpenRate.configurationmanager.IEventInterface;
import OpenRate.exception.ExceptionHandler;
import OpenRate.exception.InitializationException;
import OpenRate.exception.ProcessingException;
import OpenRate.logging.ILogger;
import OpenRate.logging.LogUtil;
import OpenRate.record.HeaderRecord;
import OpenRate.record.IRecord;
import OpenRate.record.TrailerRecord;
import OpenRate.utils.PropertyUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * AbstractSTOutputAdapter - a single threaded output adapter implementation.
 */
public abstract class AbstractOutputAdapter
        implements IOutputAdapter,
        IEventInterface,
        IMonitor {

  // This is the symbolic name that we use to identify individual instances

  private String symbolicName;

  private int sleepTime = 100;
  private ISupplier inputValidBuffer = null;
  private IConsumer outputValidBuffer = null;

  // number of records to persist at once
  private int batchSize;
  private int bufferSize;

  // Whether we are to shut down or not
  private volatile boolean shutdownFlag = false;

  // Used to store the name of this output, for deciding if records should be
  // written to this output or not
  private String outputName;

  // used to simplify logging and exception handling
  public String message;

  // This logs records to the log if they are discarded
  private boolean LogDiscardedRecords = false;

  // List of Services that this Client supports
  private final static String SERVICE_BATCHSIZE = CommonConfig.BATCH_SIZE;
  private final static String SERVICE_BUFFERSIZE = CommonConfig.BUFFER_SIZE;
  private final static String DEFAULT_BATCHSIZE = CommonConfig.DEFAULT_BATCH_SIZE;
  private final static String DEFAULT_BUFFERSIZE = CommonConfig.DEFAULT_BUFFER_SIZE;
  private final static String SERVICE_MAX_SLEEP = CommonConfig.MAX_SLEEP;
  private final static String DEFAULT_MAX_SLEEP = CommonConfig.DEFAULT_MAX_SLEEP;
  private final static String SERVICE_LOG_DISC = "LogDiscardedRecords";
  private final static String SERVICE_STATS = CommonConfig.STATS;
  private final static String SERVICE_STATSRESET = CommonConfig.STATS_RESET;
  private final static String SERVICE_OUTPUTNAME = "OutputName";

  //performance counters
  private long processingTime = 0;
  private long recordsProcessed = 0;
  private long streamsProcessed = 0;
  private int outBufferCapacity = 0;
  private int bufferHits = 0;

  // If we are the terminating output adapter, default no
  private boolean terminatingAdaptor = false;

  // This is the pipeline that we are in, used for logging and property retrieval
  private IPipeline pipeline;

  /**
   * Default constructor
   */
  public AbstractOutputAdapter() {
    super();
  }

  /**
   * Initialise the attributes relevant to this part of the output adapter
   * stack.
   *
   * @param PipelineName The name of the pipeline this module is in
   * @param ModuleName The module symbolic name of this module
   * @throws OpenRate.exception.InitializationException
   */
  @Override
  public void init(String PipelineName, String ModuleName)
          throws InitializationException {
    String ConfigHelper;
    setSymbolicName(ModuleName);

    // store the pipe we are in
    setPipeline(OpenRate.getPipelineFromMap(PipelineName));

    registerClientManager();
    ConfigHelper = initGetBatchSize();
    processControlEvent(SERVICE_BATCHSIZE, true, ConfigHelper);
    ConfigHelper = initGetBufferSize();
    processControlEvent(SERVICE_BUFFERSIZE, true, ConfigHelper);
    ConfigHelper = initGetMaxSleep();
    processControlEvent(SERVICE_MAX_SLEEP, true, ConfigHelper);
    ConfigHelper = initGetOutputName();
    processControlEvent(SERVICE_OUTPUTNAME, true, ConfigHelper);
    ConfigHelper = initLogDiscardedRecords();
    processControlEvent(SERVICE_LOG_DISC, true, ConfigHelper);
  }

  /**
   * Thread execution method. Inherited from Runnable. All this method does is
   * call write() and catch any processing exception. Any exceptions that occur
   * in the processing are intercepted and passed back via the exception handler
   * that we nominated during the pipeline creation
   */
  @Override
  public void run() {
    getBatchInboundValidBuffer().registerMonitor(this);

    // Write the records
    try {
      write();
    } catch (ProcessingException pe) {
      getExceptionHandler().reportException(pe);
    }
  }

  /**
   * The write method iterates through the batch and drives the processing thus:
   * 1) The iterator checks the streams which the record should be written to
   * and if this stream should be written to, fires either the prepValid or
   * prepError method. (Headers and trailers always fire) 2) The
   * prepValid/prepError method triggers the procValid/procError method, which
   * is where the concrete implementation class changes the record type from
   * that used in the pipeline to the required type for the output adapter, and
   * performs record decompression 3) The prepValid/prepError method then writes
   * the record (uncompressed by now) to the media 4) If the record has been
   * consumed, it is dropped, otherwise it passes into the output batch. 5) If
   * this is an output terminator, any record which was not consumed is written
   * to the PipeLog file.
   *
   * @throws ProcessingException
   */
  public void write() throws ProcessingException {
    Collection<IRecord> in;
    Collection<IRecord> out;
    Iterator<IRecord> iter;
    boolean OutBatchHasValidRecords = false;
    long startTime;
    long endTime;
    long BatchTime;
    int ThisBatchRecordCount;
    int ThisBatchRecordsWritten;
    boolean inTransaction = false;

    while (true) {
      // Start the timing for the statistics
      startTime = System.currentTimeMillis();

      in = getBatchInboundValidBuffer().pull(batchSize);
      ThisBatchRecordCount = in.size();
      ThisBatchRecordsWritten = 0;

      if (ThisBatchRecordCount > 0) {
        getPipeLog().debug("Output <" + getSymbolicName() + "> Processing a batch of " + ThisBatchRecordCount + " valid records.");
        out = new ArrayList<>();

        // Check for the case that we have an aborted transaction
        if (inTransaction && SkipRestOfStream()) {
          int SkipCount = 0;
          Iterator<IRecord> SkipIter = in.iterator();

          // fast forward to the end of the stream
          while (SkipIter.hasNext()) {
            IRecord r = SkipIter.next();
            if (r instanceof TrailerRecord) {
              // Log how many we discarded
              getPipeLog().warning("Output <" + getSymbolicName() + "> discarded <" + SkipCount + "> records because of transaction abort");

              //reset the iterator
              break;
            } else {
              // zap the record
              SkipIter.remove();
              SkipCount++;
            }
          }
        }

        iter = in.iterator();

        while (iter.hasNext()) {
          // Get the formatted information from the record
          IRecord r = iter.next();

          if (r.isValid()) {
            // this is a call to the "prepare" class, which in turn will call
            // the procValidRecord method, which is where the implementation
            // class gets its say.
            if (r.getOutput(outputName)) {
              ThisBatchRecordsWritten++;

              try {
                r = prepValidRecord(r);
              } catch (ProcessingException pe) {
                getExceptionHandler().reportException(pe);
              }

              if (!r.deleteOutput(outputName, terminatingAdaptor)) {
                // pass the record into the output stream
                out.add(r);
                OutBatchHasValidRecords = true;
              }
            } else {
              // pass the record into the output stream
              out.add(r);
              OutBatchHasValidRecords = true;
            }
          } else {
            if (r.isErrored()) {
              // this is a call to the "prepare" class, which in turn will call
              // the procErrorRecord method, which is where the implementation
              // class gets its say
              if (r.getOutput(outputName)) {
                ThisBatchRecordsWritten++;

                try {
                  r = prepErrorRecord(r);
                } catch (ProcessingException pe) {
                  getExceptionHandler().reportException(pe);
                }

                if (!r.deleteOutput(outputName, terminatingAdaptor)) {
                  // drop the record
                  out.add(r);
                  OutBatchHasValidRecords = true;
                }
              } else {
                // pass the record into the output stream
                out.add(r);
                OutBatchHasValidRecords = true;
              }
            } else {
              if (r instanceof HeaderRecord) {
                ThisBatchRecordsWritten++;
                streamsProcessed++;
                procHeader((HeaderRecord)r);
                out.add(r);
                inTransaction = true;
              }

              if (r instanceof TrailerRecord) {
                ThisBatchRecordsWritten++;

                // Flush out the rest of the stream
                try {
                  flushStream();
                } catch (ProcessingException e) {
                  getExceptionHandler().reportException(new ProcessingException(e, getSymbolicName()));
                }

                // Process the trailer and pass it on
                procTrailer((TrailerRecord)r);
                out.add(r);

                // Mark that we have finished this stream
                inTransaction = false;
              }
            }
          }
        }

        // block flush
        // We have to be a bit careful with flushing, as there is a difference
        // between the way that file streams and DB streams. The difference
        // comes from the fact that we allow 1 block to hold many file streams
        // but only 1 DB stream. If we flushed the stream, we can't flush the
        // block for DB streams (the flush causes the DB connection to close).
        try {
          flushBlock();
        } catch (ProcessingException pe) {
          getExceptionHandler().reportException(pe);
        }

        // clean up the input buffer
        in.clear();

        // Push the records that survived into the next output
        if (OutBatchHasValidRecords) {
          if (terminatingAdaptor) {
            getPipeLog().error("Output <" + getSymbolicName() + "> discarded <"
                    + out.size() + "> records at the end of the output adapter chain.");

            // dump the information out
            if (LogDiscardedRecords) {
              iter = out.iterator();
              while (iter.hasNext()) {
                //Get the formatted information from the record
                IRecord r = iter.next();

                Iterator<String> dumpIter = r.getDumpInfo().iterator();
                while (dumpIter.hasNext()) {
                  getPipeLog().info(dumpIter.next());
                }
              }
            }
          } else {
            // push the remaining records to the next adapter
            getBatchOutboundValidBuffer().push(out);

            outBufferCapacity = getBatchOutboundValidBuffer().getEventCount();

            while (outBufferCapacity > bufferSize) {
              bufferHits++;
              OpenRate.getOpenRateStatsLog().debug("Output <" + getSymbolicName() + "> buffer high water mark! Buffer max = <" + bufferSize + "> current count = <" + outBufferCapacity + ">");
              try {
                Thread.sleep(sleepTime);
              } catch (InterruptedException ex) {
                //
              }
              outBufferCapacity = getBatchOutboundValidBuffer().getEventCount();
            }
          }
        } else {
          // even if there are no valid records, we have to push the header/trailer
          // to allow the transactions to be managed
          if (!terminatingAdaptor) {
            getBatchOutboundValidBuffer().push(out);
          }
        }

        endTime = System.currentTimeMillis();
        BatchTime = (endTime - startTime);
        processingTime += BatchTime;

        recordsProcessed += ThisBatchRecordCount;
        OpenRate.getOpenRateStatsLog().info(
                "Output <" + getSymbolicName() + "> persisted <"
                + ThisBatchRecordsWritten + "> events from a batch of <"
                + ThisBatchRecordCount + "> events in <" + BatchTime + "> ms");
      } else {
        getPipeLog().debug(
                "Output <" + getSymbolicName()
                + ">, Idle Cycle, thread <" + Thread.currentThread().getName() + ">");

        // We have finished the
        if (shutdownFlag == true) {
          getPipeLog().debug(
                  "Output <" + getSymbolicName()
                  + ">, thread <" + Thread.currentThread().getName() + "> shut down. Exiting.");
          break;
        }

        // If not marked for shutdown, wait for notification from the
        // supplier that new records are available for processing.
        try {
          synchronized (this) {
            wait();
          }
        } catch (InterruptedException e) {
          // ignore
        }
      }
    } // while loop
  }

  /**
   * This is used in the case that we want to skip to the end of the stream
   * discarding records as we go. This is primarily used in the abort
   * processing, and so here never triggers the skip. If you want to use the
   * skip, you need to over write this method.
   *
   * @return true if we skip, otherwise false
   */
  public boolean SkipRestOfStream() {
    return false;
  }

  /**
   * Do any non-record level processing required to finish this batch cycle.
   *
   * @return The number of records that are in the output buffer
   */
  @Override
  public int getOutboundRecordCount() {
    if (terminatingAdaptor) {
      return 0;
    } else {
      outBufferCapacity = getBatchOutboundValidBuffer().getEventCount();
      return outBufferCapacity;
    }
  }

  /**
   * Do any required processing prior to completing the stream. The
   * flushStream() method is called for transaction stream. This differs from
   * the flushBlock(), which is called at the end of each block and the
   * cleanup() method, which is called only once upon application shutdown.
   *
   * @throws OpenRate.exception.ProcessingException
   */
  public void flushStream() throws ProcessingException {
    // no op
  }

  /**
   * Do any required processing prior to completing the batch block. The
   * flushBlock() method is called for block processed and is intended for batch
   * commit control.
   *
   * @throws OpenRate.exception.ProcessingException
   */
  public void flushBlock() throws ProcessingException {
    // no op
  }

  /**
   * Reset the adapter in to ensure that it's ready to process records again
   * after it has been exited. This method must be called after calling
   * MarkForClosedown() to reset the state.
   */
  @Override
  public void reset() {
    //getPipeLog().debug("reset called on Output Adapter <" + getSymbolicName() + ">");
    this.shutdownFlag = false;
  }

  /**
   * MarkForClosedown tells the adapter thread to close at the first chance,
   * usually as soon as an idle cycle is detected
   */
  @Override
  public void markForClosedown() {
    this.shutdownFlag = true;

    // notify any listeners that are waiting that we are flushing
    synchronized (this) {
      notifyAll();
    }
  }

  /**
   * Do anything necessary before shutting down the output adapter
   *
   * @throws OpenRate.exception.ProcessingException
   */
  @Override
  public void close() throws ProcessingException {
    getPipeLog().debug("close");
  }

  /**
   * Do any cleanup before closing
   */
  @Override
  public void cleanup() {
    getPipeLog().debug("cleanup");
  }

  /**
   * Prepare the current (valid) record for outputting. The prepValidRecord
   * calls the procValidRecord() method for the record, and then writes the
   * resulting records to the output file one at a time. This is the "record
   * expansion" part of the "record compression" strategy.
   *
   * @param r The current record we are working on
   * @return The prepared record
   * @throws ProcessingException
   */
  public abstract IRecord prepValidRecord(IRecord r) throws ProcessingException;

  /**
   * Prepare the current (error) record for outputting. The prepValidRecord
   * calls the procValidRecord() method for the record, and then writes the
   * resulting records to the output file one at a time. This is the "record
   * expansion" part of the "record compression" strategy.
   *
   * @param r The current record we are working on
   * @return The prepared record
   * @throws ProcessingException
   */
  public abstract IRecord prepErrorRecord(IRecord r) throws ProcessingException;

  /**
   * This is called when the synthetic Header record is encountered, and has the
   * meaning that the stream is starting. This is for information to the
   * implementing module only, and need not be hooked, as it is handled
   * internally by the child class
   *
   * @param r The record we are working on
   * @return The processed record
   * @throws ProcessingException
   */
  public abstract HeaderRecord procHeader(HeaderRecord r) throws ProcessingException;

  /**
   * This is called when the synthetic trailer record is encountered, and has
   * the meaning that the stream is now finished. This returns void, because we
   * do not write stream headers, thus this is for information to the
   * implementing module only.
   *
   * @param r The record we are working on
   * @return The processed record
   * @throws ProcessingException
   */
  public abstract TrailerRecord procTrailer(TrailerRecord r) throws ProcessingException;

// -----------------------------------------------------------------------------
// ----------------------- Start of IMonitor functions -------------------------
// -----------------------------------------------------------------------------
  /**
   * Simple implementation of Monitor interface based on Thread wait/notify
   * mechanism.
   *
   * @param e The event notifier
   */
  @Override
  public void notify(IEvent e) {
    synchronized (this) {
      notifyAll();
    }
  }

  // -----------------------------------------------------------------------------
  // ------------- Start of inherited IEventInterface functions ------------------
  // -----------------------------------------------------------------------------
  /**
   * registerClientManager registers this class as a client of the ECI listener
   * and publishes the commands that the plug in understands. The listener is
   * responsible for delivering only these commands to the plug in.
   *
   * @throws OpenRate.exception.InitializationException
   */
  @Override
  public void registerClientManager() throws InitializationException {
    // Set the client reference and the base services first
    ClientManager.getClientManager().registerClient(getPipeName(), getSymbolicName(), this);

    //Register services for this Client
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_BATCHSIZE, ClientManager.PARAM_MANDATORY);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_BUFFERSIZE, ClientManager.PARAM_MANDATORY);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_MAX_SLEEP, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_STATS, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_STATSRESET, ClientManager.PARAM_DYNAMIC);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_OUTPUTNAME, ClientManager.PARAM_MANDATORY);
  }

  /**
   * processControlEvent is the event processing hook for the External Control
   * Interface (ECI). This allows interaction with the external world.
   *
   * @param command The command that we are to work on
   * @param init True if the pipeline is currently being constructed
   * @param parameter The parameter value for the command
   * @return The result message of the operation
   */
  @Override
  public String processControlEvent(String command, boolean init,
          String parameter) {
    int ResultCode = -1;
    double CDRsPerSec;

    // Reset the Statistics
    if (command.equalsIgnoreCase(SERVICE_STATSRESET)) {
      // Only reset if we are told to
      switch (parameter) {
        case "true":
          processingTime = 0;
          recordsProcessed = 0;
          streamsProcessed = 0;
          bufferHits = 0;
          break;
        case "":
          return "false";
      }
    }

    // Return the Statistics
    if (command.equalsIgnoreCase(SERVICE_STATS)) {
      if (processingTime == 0) {
        CDRsPerSec = 0;
      } else {
        CDRsPerSec = (double) ((recordsProcessed * 1000) / processingTime);
      }

      return Long.toString(recordsProcessed) + ":"
              + Long.toString(processingTime) + ":"
              + Long.toString(streamsProcessed) + ":"
              + Double.toString(CDRsPerSec) + ":"
              + Long.toString(outBufferCapacity) + ":"
              + Long.toString(bufferHits);
    }

    if (command.equalsIgnoreCase(SERVICE_BUFFERSIZE)) {
      if (parameter.equals("")) {
        return Integer.toString(bufferSize);
      } else {
        try {
          bufferSize = Integer.parseInt(parameter);
        } catch (NumberFormatException nfe) {
          getPipeLog().error(
                  "Invalid number for batch size. Passed value = <"
                  + parameter + ">");
        }

        ResultCode = 0;
      }
    }

    if (command.equalsIgnoreCase(SERVICE_BATCHSIZE)) {
      if (parameter.equals("")) {
        return Integer.toString(batchSize);
      } else {
        try {
          batchSize = Integer.parseInt(parameter);
        } catch (NumberFormatException nfe) {
          getPipeLog().error(
                  "Invalid number for batch size. Passed value = <"
                  + parameter + ">");
        }

        ResultCode = 0;
      }
    }

    if (command.equalsIgnoreCase(SERVICE_OUTPUTNAME)) {
      if (init) {
        outputName = parameter;
        ResultCode = 0;
      } else {
        if (parameter.equals("")) {
          return outputName;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (command.equalsIgnoreCase(SERVICE_MAX_SLEEP)) {
      if (parameter.equals("")) {
        return Integer.toString(sleepTime);
      } else {
        try {
          sleepTime = Integer.parseInt(parameter);
        } catch (NumberFormatException nfe) {
          getPipeLog().error(
                  "Invalid number for sleep time. Passed value = <"
                  + parameter + ">");
        }

        ResultCode = 0;
      }
    }

    if (command.equalsIgnoreCase(SERVICE_LOG_DISC)) {
      if (parameter.equalsIgnoreCase("true")) {
        LogDiscardedRecords = true;
        ResultCode = 0;
      } else if (parameter.equalsIgnoreCase("false")) {
        LogDiscardedRecords = false;
        ResultCode = 0;
      } else {
        // return the current status
        if (LogDiscardedRecords) {
          return "true";
        } else {
          return "false";
        }
      }
    }

    if (ResultCode == 0) {
      getPipeLog().debug(LogUtil.LogECIPipeCommand(getSymbolicName(), getPipeName(), command, parameter));

      return "OK";
    } else {
      return "Command Not Understood \n";
    }
  }

  // -----------------------------------------------------------------------------
  // -------------------- Start of initialisation functions ----------------------
  // -----------------------------------------------------------------------------
  /**
   * Temporary function to gather the information from the properties file. Will
   *
   * be removed with the introduction of the new configuration model.
   */
  private String initGetBatchSize() throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchOutputAdapterPropertyValueDef(getPipeName(), symbolicName,
            SERVICE_BATCHSIZE, DEFAULT_BATCHSIZE);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetBufferSize()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchOutputAdapterPropertyValueDef(getPipeName(), symbolicName,
            SERVICE_BUFFERSIZE, DEFAULT_BUFFERSIZE);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetMaxSleep()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchOutputAdapterPropertyValueDef(getPipeName(), symbolicName,
            SERVICE_MAX_SLEEP, DEFAULT_MAX_SLEEP);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetOutputName()
          throws InitializationException {
    String tmpParam;
    tmpParam = PropertyUtils.getPropertyUtils().getBatchOutputAdapterPropertyValueDef(getPipeName(), symbolicName,
            SERVICE_OUTPUTNAME, "");

    if (tmpParam.equals("")) {
      message = "Output Adapter Name <"
              + getSymbolicName()
              + ".OutputName> not set for <"
              + getSymbolicName() + ">";
      throw new InitializationException(message, getSymbolicName());
    }

    return tmpParam;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initLogDiscardedRecords()
          throws InitializationException {
    String tmpParam;
    tmpParam = PropertyUtils.getPropertyUtils().getBatchOutputAdapterPropertyValueDef(getPipeName(), symbolicName,
            SERVICE_LOG_DISC, "false");

    return tmpParam;
  }

  // -----------------------------------------------------------------------------
  // -------------------- Standard getter/setter functions -----------------------
  // -----------------------------------------------------------------------------
  /**
   * Set if we are a terminating output adapter or not
   *
   * @param terminator The new value to set
   */
  @Override
  public void setTerminator(boolean terminator) {
    terminatingAdaptor = terminator;
  }

  /**
   * Set the inbound buffer for valid records
   *
   * @param ch The supplier buffer to set
   */
  @Override
  public void setBatchInboundValidBuffer(ISupplier ch) {
    this.inputValidBuffer = ch;
  }

  /**
   * Get the inbound buffer for valid records
   *
   * @return ch The current supplier buffer
   */
  @Override
  public ISupplier getBatchInboundValidBuffer() {
    return this.inputValidBuffer;
  }

  /**
   * Set the outbound buffer for valid records
   *
   * @param ch The consumer buffer to set
   */
  @Override
  public void setBatchOutboundValidBuffer(IConsumer ch) {
    this.outputValidBuffer = ch;
  }

  /**
   * Get the outbound buffer for valid records
   *
   * @return ch The current consumer buffer
   */
  @Override
  public IConsumer getBatchOutboundValidBuffer() {
    return this.outputValidBuffer;
  }

  /**
   * Get the batch size for commits
   *
   * @return The current batch size
   */
  public int getBatchSize() {
    return this.batchSize;
  }

  /**
   * return the symbolic name
   *
   * @return The symbolic name for this class stack
   */
  @Override
  public String getSymbolicName() {
    return symbolicName;
  }

  /**
   * set the symbolic name
   *
   * @param name The symbolic name to set for this class stack
   */
  @Override
  public void setSymbolicName(String name) {
    symbolicName = name;
  }

  /**
   * @return the pipeName
   */
  public String getPipeName() {
    return pipeline.getSymbolicName();
  }

  /**
   * @return the pipeline
   */
  @Override
  public IPipeline getPipeline() {
    return pipeline;
  }

  /**
   * Set the pipeline reference so the input adapter can control the scheduler
   *
   * @param pipeline the Pipeline to set
   */
  @Override
  public void setPipeline(IPipeline pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Return the pipeline logger.
   *
   * @return The logger
   */
  protected ILogger getPipeLog() {
    return pipeline.getPipeLog();
  }

  /**
   * Return the exception handler.
   *
   * @return The exception handler
   */
  protected ExceptionHandler getExceptionHandler() {
    return pipeline.getPipelineExceptionHandler();
  }
}
