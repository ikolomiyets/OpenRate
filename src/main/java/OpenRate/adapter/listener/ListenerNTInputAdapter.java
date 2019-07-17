package OpenRate.adapter.listener;

import OpenRate.adapter.AbstractInputAdapter;
import OpenRate.configurationmanager.ClientManager;
import OpenRate.configurationmanager.IEventInterface;
import OpenRate.exception.InitializationException;
import OpenRate.exception.ProcessingException;
import OpenRate.logging.LogUtil;
import OpenRate.record.FlatRecord;
import OpenRate.record.HeaderRecord;
import OpenRate.record.IRecord;
import OpenRate.record.TrailerRecord;
import OpenRate.utils.PropertyUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Socket Listener InputAdapter (Non transactional Version) The basic function
 * of this flat file input adapter is to facilitate a reading of a flat file in
 * the batches, instead of reading a whole file in a single fetch.
 *
 * The file input adapter scans for files, and when found, opens them, reads
 * them and turns them into batches to maintain the load on the pipeline.
 *
 * Scanning and Processing -----------------------
 *
 * The basic scanning and processing loop looks like this: - The loadBatch()
 * method is called regularly by the execution model, regardless of if there is
 * work in progress or not. - If we are not processing a file, we are allowed to
 * scan for a new file to process - If we are allowed to look for a new file to
 * process, we do this: - getInputAvailable() Scan to see if there is any work
 * to do - assignInput() marks the file as being in processing if we find work
 * to do open the input stream - Calculate the file names from the base name -
 * Open the file reader - Inject the synthetic HeaderRecord into the stream as
 * the first record to synchronise the processing down the pipe
 *
 * - If we are processing a stream, we do: - Read the records in from the
 * stream, creating a basic "FlatRecord" for each record we have read - When we
 * have finished reading the batch (either because we have reached the batch
 * limit or because there are no more records to read) call procValidRecord(),
 * which allows the user to perform preparation of the record (for example,
 * creating the user defined record from the generic FlatRecord, or performing
 * record compression on the incoming stream) - See if the file reader has run
 * out of records. It it has, this is the end of the stream. If it is the end of
 * the stream, we do: - Inject a trailer record into the stream - close the
 * input stream and reset the "file in processing" flag so that we can scan for
 * more files
 */
public abstract class ListenerNTInputAdapter
        extends AbstractInputAdapter implements IEventInterface {

  // This is the locally cached base name that we have recovered from the
  // file name
  private static String IntBaseName;

  // The port which we will listen on
  private int intPort = 1123;

  // The host which we are running on
  private String strHost = "localhost";

  // This tells us if we should look for a file to open
  // or continue reading from the one we have
  private boolean InputStreamOpen = false;

  // used to track the status of the stream processing
  private int InputRecordNumber = 0;

  /*
   * Reader is initialized in the init() method and is kept open for loadBatch()
   * calls and then closed in cleanup(). This facilitates batching of input.
   */
  private BufferedReader reader;

  // List of Services that this Client supports
  private final static String SERVICE_I_NAME = "InputFileName";
  private final static String SERVICE_D_NAME = "DoneFileName";
  private final static String SERVICE_E_NAME = "ErrFileName";
  private final static String SERVICE_I_PATH = "InputFilePath";
  private final static String SERVICE_D_PATH = "DoneFilePath";
  private final static String SERVICE_E_PATH = "ErrFilePath";
  private final static String SERVICE_I_PREFIX = "InputFilePrefix";
  private final static String SERVICE_D_PREFIX = "DoneFilePrefix";
  private final static String SERVICE_E_PREFIX = "ErrFilePrefix";
  private final static String SERVICE_I_SUFFIX = "InputFileSuffix";
  private final static String SERVICE_D_SUFFIX = "DoneFileSuffix";
  private final static String SERVICE_E_SUFFIX = "ErrFileSuffix";

  /**
   * Default Constructor
   */
  public ListenerNTInputAdapter() {
    super();
  }

// -----------------------------------------------------------------------------
// --------------- Start of inherited Input Adapter functions ------------------
// -----------------------------------------------------------------------------
  /**
   * initialize input adapter. sets the filename to use & initializes the file
   * reader.
   *
   * @param ModuleName The module symbolic name of this module
   * @throws OpenRate.exception.InitializationException
   */
  @Override
  public void init(String PipelineName, String ModuleName)
          throws InitializationException {
    super.init(PipelineName, ModuleName);

    //sets the port number and the host to connect
    this.intPort = Integer.parseInt(PropertyUtils.getPropertyUtils().getPropertyValue("SERVER_LISTENER_PORT"));
    this.strHost = PropertyUtils.getPropertyUtils().getPropertyValue("SERVER_LISTENER_HOST");

  }

  /**
   * loadBatch() is called regularly by the framework to either process records
   * or to scan for work to do, depending on whether we are already processing
   * or not.
   *
   * @return records collected in this load
   * @throws ProcessingException
   */
  @Override
  protected Collection<IRecord> loadBatch()
          throws ProcessingException {

    String tmpFileRecord;
    String baseName = null;
    Collection<IRecord> Outbatch;
    int ThisBatchCounter = 0;

    // The Record types we will have to deal with
    HeaderRecord tmpHeader;
    TrailerRecord tmpTrailer;
    FlatRecord tmpDataRecord;
    IRecord batchRecord;
    Outbatch = new ArrayList<>();

    // This layer deals with opening the stream if we need to
    if (InputStreamOpen == false) {
      try {
        Socket skt = new Socket(strHost, intPort);

        if (skt.isConnected()) {

          reader = new BufferedReader(new InputStreamReader(skt.getInputStream()));

          while (!reader.ready()) {

            //do nothing; just wait for the reader to get ready
          }

          InputStreamOpen = true;
          InputRecordNumber = 0;

          // Inject a stream header record into the stream
          tmpHeader = new HeaderRecord();
          tmpHeader.setStreamName(baseName);

          // Increment the stream counter
          incrementStreamCount();

          // Pass the header to the user layer for any processing that
          // needs to be done
          tmpHeader = procHeader(tmpHeader);
          Outbatch.add(tmpHeader);
          ThisBatchCounter++;

          skt.close();

        } else {
          // No work to do - return the empty batch
          getPipeLog().info("Unable to connect to Host : " + strHost + ":" + intPort);
          return Outbatch;
        }
      } catch (UnknownHostException ex) {

        getPipeLog().error("Unknown Host : " + strHost + ":" + intPort);
        throw new ProcessingException("Unknown Host : " + strHost + ":" + intPort, getSymbolicName());

      } catch (IOException ex) {

        getPipeLog().error("IOException reading stream on " + strHost + ":" + intPort);
        throw new ProcessingException("IOException reading stream on " + strHost + ":" + intPort, getSymbolicName());

      }
    }

    if (InputStreamOpen) {

      try {

        // read from the file and prepare the batch
        while ((reader.ready()) & (ThisBatchCounter < batchSize)) {
          ThisBatchCounter++;
          tmpFileRecord = reader.readLine();
          tmpDataRecord = new FlatRecord(tmpFileRecord, InputRecordNumber);
          InputRecordNumber++;

          // Call the user layer for any processing that needs to be done
          batchRecord = procValidRecord(tmpDataRecord);

          // Add the prepared record to the batch
          Outbatch.add(batchRecord);
        }

        // see the reason that we closed
        if (reader.ready() == false) {

          // we have finished
          InputStreamOpen = false;

          //close the input file
          closeStream();

          // Inject a stream header record into the stream
          tmpTrailer = new TrailerRecord();
          tmpTrailer.setStreamName(GetBaseName());

          // Pass the trailer to the user layer for any processing that
          // needs to be done
          tmpTrailer = procTrailer(tmpTrailer);
          Outbatch.add(tmpTrailer);
          ThisBatchCounter++;
        }
      } catch (IOException ex) {
        getPipeLog().fatal("Error reading input stream");
      }
    }

    return Outbatch;
  }

  /**
   * Closes down the input stream after all the input has been collected
   *
   * @throws ProcessingException
   */
  public void closeStream()
          throws ProcessingException {
    try {
      reader.close();
    } catch (IOException ex) {
      getPipeLog().error("IOException closing stream on " + strHost + ":" + intPort);
      throw new ProcessingException("IOException closing stream on " + strHost + ":" + intPort, getSymbolicName());

    }
  }

  /**
   * This is called when a data record is encountered. You should do any normal
   * processing here.
   *
   * @param r The record we are working on
   * @return The processed record
   * @throws ProcessingException
   */
  public abstract IRecord procValidRecord(FlatRecord r) throws ProcessingException;

  /**
   * This is called when a data record with errors is encountered. You should do
   * any processing here that you have to do for error records, e.g. statistics,
   * special handling, even error correction!
   *
   * @param r The record we are working on
   * @return The processed record
   * @throws ProcessingException
   */
  public abstract IRecord procErrorRecord(FlatRecord r) throws ProcessingException;

// -----------------------------------------------------------------------------
// ------------------------ Start of utility functions -------------------------
// -----------------------------------------------------------------------------
  /**
   * Provides reader created during init()
   *
   * @return The file reader
   */
  public BufferedReader getFileReader() {

    return reader;
  }

  /**
   * Get the internal cache of the base name that we are using.
   */
  private String GetBaseName() {

    return IntBaseName;
  }

  /**
   * Set the internal cache of the base name that we are using. Note that we
   * will include this information in the Header *and* the trailer record, in
   * order to save the processing or output adapters having to store this state
   * information.
   */
  private void SetBaseName(String baseName) {
    IntBaseName = baseName;
  }

// -----------------------------------------------------------------------------
// ------------- Start of inherited IEventInterface functions ------------------
// -----------------------------------------------------------------------------
  /**
   * processControlEvent is the event processing hook for the External Control
   * Interface (ECI). This allows interaction with the external world.
   */
  @Override
  public String processControlEvent(String Command, boolean Init, String Parameter) {

    int ResultCode = -1;

//    if (Command.equals(SERVICE_??))
    {

    }

    if (ResultCode == 0) {
      getPipeLog().debug(LogUtil.LogECIPipeCommand(getSymbolicName(), getPipeName(), Command, Parameter));

      return "OK";
    } else {
      // This is not our event, pass it up the stack
      return super.processControlEvent(Command, Init, Parameter);
    }
  }

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
    super.registerClientManager();

    //Register services for this Client
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_I_NAME, ClientManager.PARAM_NONE);

  }
}
