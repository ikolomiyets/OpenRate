package OpenRate.adapter.file;

import OpenRate.CommonConfig;
import OpenRate.adapter.AbstractTransactionalInputAdapter;
import OpenRate.configurationmanager.ClientManager;
import OpenRate.configurationmanager.IEventInterface;
import OpenRate.exception.InitializationException;
import OpenRate.exception.ProcessingException;
import OpenRate.logging.LogUtil;
import OpenRate.parser.IXmlParser;
import OpenRate.parser.XMLParser;
import OpenRate.record.HeaderRecord;
import OpenRate.record.IRecord;
import OpenRate.record.TrailerRecord;
//import OpenRate.record.XMLRecord;
import OpenRate.utils.PropertyUtils;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import org.apache.oro.io.GlobFilenameFilter;
import org.apache.oro.text.GlobCompiler;

/**
 * Input adapter for XML formatted files. This adapter works by breaking down
 * the input XML stream into record sized chunks and then processing the chunks.
 * For the adapter to work, the XML must be formatted with line breaks at the
 * end of each element.
 *
 * To use the adapter, you have to define the tag which is to be used as the
 * record separation tag. For example:
 *
 * <customer customerId="20978">
 * <account>
 * <number></number>
 * <pricePlan>Telefoni Bas - 0kr</pricePlan>
 * <validFrom>2010-03-22</validFrom>
 * <validTo>2010-08-20</validTo>
 * </account>
 * </customer>
 *
 * uses the tag "customer" to define the limits of the record.
 *
 * afzaal 07-11-2008 initial version
 */
public abstract class XMLFileInputAdapter
        extends AbstractTransactionalInputAdapter
        implements IEventInterface, IXmlParser {

  /**
   * CVS version info - Automatically captured and written to the Framework
   * Version Audit log at Framework startup. For more information please <a
   * target='new'
   * href='http://www.open-rate.com/wiki/index.php?title=Framework_Version_Map'>click
   * here</a> to go to wiki page.
   */
  public static String CVS_MODULE_INFO = "OpenRate, $RCSfile: XMLFileInputAdapter.java,v $, $Revision: 1.25 $, $Date: 2013-05-13 18:12:11 $";

  // The buffer size is the size of the buffer in the buffered reader
  private static final int BUF_SIZE = 65536;

  /**
   * Set the strategy to an invalid value
   */
  protected int InputFileStrategy = 0;

  // used by setAttribute method
  HashMap<String, String> xmlValues;

  /**
   * The record identifier is used as the separator between records. It is not
   * possible/efficient to perform parsing on very long xml streams, we
   * therefore use traditional flat file based techniques to separate the
   * records out of the stream, before passing each individual record for
   * parsing.
   */
  protected String DEFAULT_RECORD_IDENTIFIER = "record";

  /**
   * This is used to determine the split the XML stream into records for
   * processing.
   */
  private String recordIdentifier;

  /**
   * The input path of the prefix (strategy 2) input file
   */
  protected String InputFilePath = null;

  /**
   * The done path of the prefix (strategy 2) input file
   */
  protected String DoneFilePath = null;

  /**
   * The error path of the prefix (strategy 2) input file
   */
  protected String ErrFilePath = null;

  /**
   * The input prefix of the prefix (strategy 2) input file
   */
  protected String InputFilePrefix = null;

  /**
   * The done prefix of the prefix (strategy 2) input file
   */
  protected String DoneFilePrefix = null;

  /**
   * The error prefix of the prefix (strategy 2) input file
   */
  protected String ErrFilePrefix = null;

  /**
   * The input suffix of the prefix (strategy 2) input file
   */
  protected String InputFileSuffix = null;

  /**
   * The done suffix of the prefix (strategy 2) input file
   */
  protected String DoneFileSuffix = null;

  /**
   * The error suffix of the prefix (strategy 2) input file
   */
  protected String ErrFileSuffix = null;

  /**
   * This tells us if we should look for a file to open or continue reading from
   * the one we have
   */
  protected boolean InputStreamOpen = false;

  /**
   * used to track the status of the stream processing
   */
  protected int InputRecordNumber = 0;

  /**
   * Used as the processing prefix to rename files in processing
   */
  protected String ProcessingPrefix;

  // This is used for queueing up files ready for processing
  private final ArrayList<Integer> FileTransactionNumbers = new ArrayList<>();

  // This is the current transaction number we are working on
  private int transactionNumber = 0;

  // This tells us if we are in a record or not - it is set by the record start
  // tag and reset by the record stop tag
  private boolean inRecord;

  /*
   * Reader is initialized in the init() method and is kept open for loadBatch()
   * calls and then closed in cleanup(). This facilitates batching of input.
   */
  private BufferedReader reader;

  // List of Services that this Client supports
  private static final String SERVICE_I_PATH = "InputFilePath";
  private static final String SERVICE_D_PATH = "DoneFilePath";
  private static final String SERVICE_E_PATH = "ErrFilePath";
  private static final String SERVICE_I_PREFIX = "InputFilePrefix";
  private static final String SERVICE_D_PREFIX = "DoneFilePrefix";
  private static final String SERVICE_E_PREFIX = "ErrFilePrefix";
  private static final String SERVICE_I_SUFFIX = "InputFileSuffix";
  private static final String SERVICE_D_SUFFIX = "DoneFileSuffix";
  private static final String SERVICE_E_SUFFIX = "ErrFileSuffix";
  private static final String SERVICE_PROCPREFIX = "ProcessingPrefix";

  // This is used to hold the calculated file names
  private class TransControlStructure {

    String InputFileName;
    String ProcFileName;
    String DoneFileName;
    String ErrorFileName;
    String BaseName;
  }

  // This holds the file names for the files that are in processing at any
  // given moment
  private HashMap<Integer, TransControlStructure> CurrentFileNames;

  /**
   * Constructor - set the default recr identifier. Override this to change the
   * behaviour.
   *
   * Set the default stream identifier. (Almost always this will have to be set
   * in the implementation class).
   */
  public XMLFileInputAdapter() {
    recordIdentifier = DEFAULT_RECORD_IDENTIFIER;
  }

  // -----------------------------------------------------------------------------
  // --------------- Start of inherited Input Adapter functions ------------------
  // -----------------------------------------------------------------------------
  /**
   * Initialise the module. Called during pipeline creation. initialize input
   * adapter. sets the filename to use & initializes the file reader.
   *
   * @param PipelineName The name of the pipeline this module is in
   * @param ModuleName The module symbolic name of this module
   * @throws OpenRate.exception.InitializationException
   */
  @Override
  public void init(String PipelineName, String ModuleName)
          throws InitializationException {
    String ConfigHelper;

    // Register ourself with the client manager
    super.init(PipelineName, ModuleName);

    // Now we load the properties and use the event interface to initialise
    // the adapter. Note that this architecture will change to be completely
    // event driven in the near future.
    ConfigHelper = initGetInputFilePath();
    processControlEvent(SERVICE_I_PATH, true, ConfigHelper);
    ConfigHelper = initGetDoneFilePath();
    processControlEvent(SERVICE_D_PATH, true, ConfigHelper);
    ConfigHelper = initGetErrFilePath();
    processControlEvent(SERVICE_E_PATH, true, ConfigHelper);
    ConfigHelper = initGetInputFilePrefix();
    processControlEvent(SERVICE_I_PREFIX, true, ConfigHelper);
    ConfigHelper = initGetDoneFilePrefix();
    processControlEvent(SERVICE_D_PREFIX, true, ConfigHelper);
    ConfigHelper = initGetErrFilePrefix();
    processControlEvent(SERVICE_E_PREFIX, true, ConfigHelper);
    ConfigHelper = initGetInputFileSuffix();
    processControlEvent(SERVICE_I_SUFFIX, true, ConfigHelper);
    ConfigHelper = initGetDoneFileSuffix();
    processControlEvent(SERVICE_D_SUFFIX, true, ConfigHelper);
    ConfigHelper = initGetErrFileSuffix();
    processControlEvent(SERVICE_E_SUFFIX, true, ConfigHelper);
    ConfigHelper = initGetProcPrefix();
    processControlEvent(SERVICE_PROCPREFIX, true, ConfigHelper);

    // Check the file name scanning variables, throw initialisation exception
    // if something is wrong.
    initFileName();

    // create the structure for storing filenames
    CurrentFileNames = new HashMap<>(10);
  }

  /**
   * loadBatch() is called regularly by the framework to either process records
   * or to scan for work to do, depending on whether we are already processing
   * or not.
   *
   * The way this works is that we assign a batch of files to work on, and then
   * work our way through them. This minimises the directory scans that we have
   * to do and improves performance.
   *
   * @return
   * @throws OpenRate.exception.ProcessingException
   */
  @Override
  protected Collection<IRecord> loadBatch()
          throws ProcessingException {
    StringBuilder tmpFileRecord;
    String tmpRecordLine;
    String baseName = null;
    Collection<IRecord> Outbatch;
    int ThisBatchCounter = 0;
    XMLParser parser;

    // The Record types we will have to deal with
    HeaderRecord tmpHeader;
    TrailerRecord tmpTrailer;
    //XMLRecord     tmpDataRecord;
    IRecord batchRecord;

    Outbatch = new ArrayList<>();

    // Check to see if there is any work to do, and if the transaction
    // manager can accept the new work (if it can't, no files will be assigned
    ArrayList<Integer> fileNames = assignInput();
    if (fileNames.size() > 0) {
      // There is a file available, so open it and rename it to
      // show that we are doing something
      FileTransactionNumbers.addAll(fileNames);
    }

    // This layer deals with opening the stream if we need to
    if (FileTransactionNumbers.size() > 0) {
      // we have something to do
      // See if we are trying to finish off a file that is already in process
      if (InputStreamOpen == false) {
        // Open the stream
        // we don't have anything open, so get something from the head of the
        // waiting list
        transactionNumber = FileTransactionNumbers.get(0);

        // Now that we have the file name, try to open it from
        // the renamed file provided by assignInput
        try {
          reader = new BufferedReader(new FileReader(getProcName(transactionNumber)), BUF_SIZE);
          InputStreamOpen = true;
          InputRecordNumber = 0;

          // Inform the transactional layer that we have started processing
          setTransactionProcessing(transactionNumber);

          // Inject a stream header record into the stream
          tmpHeader = new HeaderRecord();
          tmpHeader.setStreamName(getBaseName(transactionNumber));
          tmpHeader.setTransactionNumber(transactionNumber);

          // Increment the stream counter
          incrementStreamCount();

          // Pass the header to the user layer for any processing that
          // needs to be done
          tmpHeader = procHeader(tmpHeader);
          Outbatch.add(tmpHeader);
        } catch (FileNotFoundException exFileNotFound) {
          getPipeLog().error(
                  "Application is not able to read file : '" + getProcName(transactionNumber)
                  + "' ");
          throw new ProcessingException("Application is not able to read file <"
                  + getProcName(transactionNumber) + ">",
                  exFileNotFound,
                  getSymbolicName());
        }
      }

      // Continue with the open file
      try {
        // read from the file and prepare the batch
        while ((reader.ready()) & (ThisBatchCounter < batchSize)) {
          tmpFileRecord = new StringBuilder();
          parser = new XMLParser(this);
          xmlValues = new HashMap<>();

          while (reader.ready()) {
            tmpRecordLine = reader.readLine();

            // Start of record
            if ((tmpRecordLine.contains("<" + recordIdentifier + ">"))
                    || (tmpRecordLine.contains("<" + recordIdentifier + " "))) {
              inRecord = true;
            }

            // if we are in a record, then append the line data to the record
            if (inRecord) {
              tmpFileRecord.append(tmpRecordLine);
            }

            // End of record
            if (tmpRecordLine.contains("</" + recordIdentifier + ">")) {
              // reset to say that we are no longer in a record
              inRecord = false;

              // We are ready to submit the record to xml parser
              try {
                parser.parseXML(tmpFileRecord.toString(), DEFAULT_RECORD_IDENTIFIER);
              } catch (Exception exRecordError) {
                message = "Application is not able to parse the record : '"
                        + getProcName(transactionNumber) + "' ";
                getPipeLog().error(message);
                throw new ProcessingException(message, exRecordError, getSymbolicName());
              }
              break;
            }

            // skip blank records
            if (tmpFileRecord.length() == 0) {
            }
          }

          ThisBatchCounter++;
          //tmpDataRecord = new XMLRecord(tmpFileRecord.toString(), xmlValues, InputRecordNumber);

          // Call the user layer for any processing that needs to be done
          //batchRecord = procValidRecord((IRecord) tmpDataRecord);
          // Add the prepared record to the batch, because of record compression
          // we may receive a null here. If we do, don't bother adding it
          //if (batchRecord != null)
          {
            InputRecordNumber++;
            //Outbatch.add(batchRecord);
          }
        }

        // see if we have to abort
        if (transactionAbortRequest(transactionNumber)) {
          // if so, clear down the out batch, so we don't keep filling the pipe
          getPipeLog().warning("Pipe <" + getSymbolicName() + "> discarded <" + Outbatch.size() + "> input records, because of pending abort.");
          Outbatch.clear();
        }

        // Update the statistics with the number of COMPRESSED final records
        updateRecordCount(transactionNumber, InputRecordNumber);

        // see the reason that we closed
        if (reader.ready() == false) {
          // we have finished
          InputStreamOpen = false;

          // Inject a stream header record into the stream
          tmpTrailer = new TrailerRecord();
          tmpTrailer.setStreamName(baseName);
          tmpTrailer.setTransactionNumber(transactionNumber);

          // Pass the header to the user layer for any processing that
          // needs to be done. To allow for purging in the case of record
          // compression, we allow multiple calls to procTrailer until the
          // trailer is returned
          batchRecord = procTrailer(tmpTrailer);

          while (!(batchRecord instanceof TrailerRecord)) {
            // the call the trailer returned a purged record. Add this
            // to the batch and fetch again
            Outbatch.add(batchRecord);
            batchRecord = procTrailer(tmpTrailer);
          }

          Outbatch.add(tmpTrailer);
          ThisBatchCounter++;

          // Close the reader
          try {
            // close the input stream
            closeStream(transactionNumber);
          } catch (ProcessingException ex) {
            getPipeLog().error("Error flushing transaction in module <" + getSymbolicName() + ">. Message <" + ex.getMessage() + ">");
          }

          // Notify the transaction layer that we have finished
          setTransactionFlushed(transactionNumber);

          // Remove the transaction from the list
          FileTransactionNumbers.remove(0);
          transactionNumber = 0;
        }
      } catch (IOException ioex) {
        getPipeLog().fatal("Error reading input file. Message <" + ioex.getMessage() + ">");
      }
    }

    return Outbatch;
  }

  /**
   * Closes down the input stream after all the input has been collected
   *
   * @param TransactionNumber The number of the transaction we are working on
   * @throws OpenRate.exception.ProcessingException
   */
  public void closeStream(int TransactionNumber)
          throws ProcessingException {
    try {
      reader.close();
    } catch (IOException exFileNotFound) {
      getPipeLog().error("Application is not able to close file : '" + getProcName(TransactionNumber)
              + "' ");
      throw new ProcessingException("Application is not able to read file <"
              + getProcName(TransactionNumber) + ">",
              exFileNotFound,
              getSymbolicName());
    }
  }

  /**
   * Provides reader created during init()
   *
   * @return The buffered Reader to use
   */
  public BufferedReader getFileReader() {
    return reader;
  }

  // -----------------------------------------------------------------------------
  // --------------- Start of transactional layer functions ----------------------
  // -----------------------------------------------------------------------------
  /**
   * Perform any processing that needs to be done when we are flushing the
   * transaction;
   *
   * @return 0 if the transaction was closed OK, otherwise -1
   */
  @Override
  public int flushTransaction(int transactionNumber) {
    return 0;
  }

  /**
   * Perform any processing that needs to be done when we are committing the
   * transaction;
   */
  @Override
  public void commitTransaction(int transactionNumber) {
    shutdownStreamProcessOK(transactionNumber);
  }

  /**
   * Perform any processing that needs to be done when we are rolling back the
   * transaction;
   */
  @Override
  public void rollbackTransaction(int transactionNumber) {
    shutdownStreamProcessERR(transactionNumber);
  }

  /**
   * Close Transaction is the trigger to clean up transaction related
   * information such as variables, status etc.
   *
   * @param transactionNumber The transaction we are working on
   */
  @Override
  public void closeTransaction(int transactionNumber) {
    // Nothing needed
  }

  // -----------------------------------------------------------------------------
  // ------------- Start of inherited IEventInterface functions ------------------
  // -----------------------------------------------------------------------------
  /**
   * processControlEvent is the event processing hook for the External Control
   * Interface (ECI). This allows interaction with the external world.
   *
   * @param Command The command that we are to work on
   * @param Init True if the pipeline is currently being constructed
   * @param Parameter The parameter value for the command
   * @return The result message of the operation
   */
  @Override
  public String processControlEvent(String Command, boolean Init,
          String Parameter) {
    int ResultCode = -1;

    if (Command.equalsIgnoreCase(SERVICE_I_PATH)) {
      if (Init) {
        InputFilePath = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return InputFilePath;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equals(SERVICE_D_PATH)) {
      if (Init) {
        DoneFilePath = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return DoneFilePath;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_E_PATH)) {
      if (Init) {
        ErrFilePath = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return ErrFilePath;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_I_PREFIX)) {
      if (Init) {
        InputFilePrefix = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return InputFilePrefix;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_D_PREFIX)) {
      if (Init) {
        DoneFilePrefix = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return DoneFilePrefix;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_E_PREFIX)) {
      if (Init) {
        ErrFilePrefix = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return ErrFilePrefix;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_I_SUFFIX)) {
      if (Init) {
        InputFileSuffix = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return InputFileSuffix;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_D_SUFFIX)) {
      if (Init) {
        DoneFileSuffix = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return DoneFileSuffix;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_E_SUFFIX)) {
      if (Init) {
        ErrFileSuffix = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return ErrFileSuffix;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
    }

    if (Command.equalsIgnoreCase(SERVICE_PROCPREFIX)) {
      if (Init) {
        ProcessingPrefix = Parameter;
        ResultCode = 0;
      } else {
        if (Parameter.equals("")) {
          return ProcessingPrefix;
        } else {
          return CommonConfig.NON_DYNAMIC_PARAM;
        }
      }
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
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_I_PATH, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_D_PATH, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_E_PATH, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_I_PREFIX, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_D_PREFIX, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_E_PREFIX, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_I_SUFFIX, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_D_SUFFIX, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_E_SUFFIX, ClientManager.PARAM_NONE);
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_PROCPREFIX, ClientManager.PARAM_NONE);
  }

  // -----------------------------------------------------------------------------
  // ------------------------ Start of custom functions --------------------------
  // -----------------------------------------------------------------------------
  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetInputFilePath()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_I_PATH);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetDoneFilePath()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_D_PATH);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetErrFilePath()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_E_PATH);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetInputFilePrefix()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_I_PREFIX);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetDoneFilePrefix()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_D_PREFIX);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetErrFilePrefix()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_E_PREFIX);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetInputFileSuffix()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_I_SUFFIX);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetDoneFileSuffix()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_D_SUFFIX);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetErrFileSuffix()
          throws InitializationException {
    String tmpFile;
    tmpFile = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValue(getPipeName(), getSymbolicName(), SERVICE_E_SUFFIX);

    return tmpFile;
  }

  /**
   * Temporary function to gather the information from the properties file. Will
   * be removed with the introduction of the new configuration model.
   */
  private String initGetProcPrefix()
          throws InitializationException {
    String tmpProcPrefix;
    tmpProcPrefix = PropertyUtils.getPropertyUtils().getBatchInputAdapterPropertyValueDef(getPipeName(), getSymbolicName(),
            SERVICE_PROCPREFIX,
            "tmp");

    return tmpProcPrefix;
  }

  /**
   * Checks the file name from the input parameters. Refactored from init() into
   * a method of its own so that derived classes can still reuse most of the
   * functionality provided by this adapter and selectively change only the
   * logic to pickup file for processing.
   *
   * The method checks for validity of the input parameters that have been
   * configured, for example if the directory does not exist, an exception will
   * be thrown.
   *
   * Two methods of finding the file are supported: 1) You can specify a file
   * name and only that file will be read 2) You can specify a file path and a
   * regular expression prefix and suffix
   */
  private void initFileName()
          throws InitializationException {
    File dir;

    /*
     * Validate the inputs we have received. We must end up with three
     * distinct paths for input done and error files. We detect this by
     * checking the sum of the parameters.
     */
    // Set default values
    if (InputFilePath == null) {
      InputFilePath = ".";
      message = "Input file path not set. Defaulting to <.>.";
      getPipeLog().warning(message);
    }

    // is the input file path valid?
    dir = new File(InputFilePath);
    if (!dir.isDirectory()) {
      message = "Input file path <" + InputFilePath + "> does not exist or is not a directory";
      getPipeLog().fatal(message);
      throw new InitializationException(message, getSymbolicName());
    }

    if (DoneFilePath == null) {
      DoneFilePath = ".";
      message = "Done file path not set. Defaulting to <.>.";
      getPipeLog().warning(message);
    }

    // is the input file path valid?
    dir = new File(DoneFilePath);
    if (!dir.isDirectory()) {
      message = "Done file path <" + DoneFilePath + "> does not exist or is not a directory";
      getPipeLog().fatal(message);
      throw new InitializationException(message, getSymbolicName());
    }

    if (ErrFilePath == null) {
      ErrFilePath = ".";
      message = "Error file path not set. Defaulting to <.>.";
      getPipeLog().warning(message);
    }

    // is the input file path valid?
    dir = new File(ErrFilePath);
    if (!dir.isDirectory()) {
      message = "Error file path <" + ErrFilePath + "> does not exist or is not a directory";
      getPipeLog().fatal(message);
      throw new InitializationException(message, getSymbolicName());
    }

    // Check that there is some variance in what we have received
    if ((DoneFilePath + DoneFilePrefix + DoneFileSuffix).equals(ErrFilePath + ErrFilePrefix
            + ErrFileSuffix)) {
      // These look suspiciously similar
      message = "Done file and Error file cannot be the same";
      getPipeLog().fatal(message);
      throw new InitializationException(message, getSymbolicName());
    }

    // Check that there is some variance in what we have received
    if ((InputFilePath + InputFilePrefix + InputFileSuffix).equals(ErrFilePath + ErrFilePrefix
            + ErrFileSuffix)) {
      // These look suspiciously similar
      message = "Input file and Error file cannot be the same";
      getPipeLog().fatal(message);
      throw new InitializationException(message, getSymbolicName());
    }

    // Check that there is some variance in what we have received
    if ((DoneFilePath + DoneFilePrefix + DoneFileSuffix).equals(InputFilePath + InputFilePrefix
            + InputFileSuffix)) {
      // These look suspiciously similar
      message = "Input file and Input file cannot be the same";
      getPipeLog().fatal(message);
      throw new InitializationException(message, getSymbolicName());
    }
  }

  /**
   * Set the record identifier for splitting the records up.
   *
   * @param newRecordIdentifier The new record identifier to set
   */
  public void setRecordIdentifier(String newRecordIdentifier) {
    recordIdentifier = newRecordIdentifier;
  }

  // -----------------------------------------------------------------------------
  // ---------------------- Start stream handling functions ----------------------
  // -----------------------------------------------------------------------------
  /**
   * Selects input from the pending list for processing and marks it as being in
   * processing. Creates the transaction object that we will be using and
   * calculates the file names that will be used.
   *
   * @return The number of files assigned
   * @throws OpenRate.exception.ProcessingException
   */
  public ArrayList<Integer> assignInput()
          throws ProcessingException {
    String procName;
    String doneName;
    String errName;
    String inpName;
    String baseName;

    String[] fileNames;
    File dir;
    FilenameFilter filter;
    int FilesAssigned;
    int tmpTransNumber;
    int FilesOpened;
    TransControlStructure tmpFileNames;
    ArrayList<Integer> OpenedTransactions = new ArrayList<>();

    // This is the current filename we are working on
    String fileName;

    // get the first file name from the directory that matches the
    dir = new File(InputFilePath);
    filter = new GlobFilenameFilter(InputFilePrefix + "*"
            + InputFileSuffix,
            GlobCompiler.STAR_CANNOT_MATCH_NULL_MASK);
    // sort files
    fileNames = getOrderedFileListForProcessing(dir, filter);

    // if we have a file, add it to the list of transaction files
    if (fileNames.length > 0) {
      // Open up the maximum number of files that we can
      FilesAssigned = 0;
      for (FilesOpened = 0; FilesOpened < fileNames.length; FilesOpened++) {
        fileName = fileNames[FilesOpened];

        // See if we want to open this file
        if (filterFileName(fileName)) {
          // We want to open it, will the transaction manager allow it?
          if (canStartNewTransaction()) {
            // Create the new transaction to hold the information. This is done in
            // The transactional layer - we just trigger it here
            tmpTransNumber = createNewTransaction();

            getPipeLog().info("Input File name is <" + fileName + ">");

            // Calculate the processing file name that we are using for this file
            procName = getProcFilePath(fileName,
                    InputFilePath,
                    InputFilePrefix,
                    InputFileSuffix,
                    ProcessingPrefix,
                    tmpTransNumber);

            doneName = getDoneFilePath(fileName,
                    InputFilePrefix,
                    InputFileSuffix,
                    DoneFilePath,
                    DoneFilePrefix,
                    DoneFileSuffix,
                    tmpTransNumber);

            errName = getErrorFilePath(fileName,
                    InputFilePrefix,
                    InputFileSuffix,
                    ErrFilePath,
                    ErrFilePrefix,
                    ErrFileSuffix,
                    tmpTransNumber);

            inpName = getInputFilePath(fileName,
                    InputFilePath);

            baseName = getFileBaseName(fileName,
                    InputFilePrefix,
                    InputFileSuffix,
                    tmpTransNumber);

            tmpFileNames = new TransControlStructure();
            tmpFileNames.InputFileName = inpName;
            tmpFileNames.ProcFileName = procName;
            tmpFileNames.DoneFileName = doneName;
            tmpFileNames.ErrorFileName = errName;
            tmpFileNames.BaseName = baseName;

            // Store the names for later
            CurrentFileNames.put(tmpTransNumber, tmpFileNames);

            // rename the input file to show that its our little piggy now
            File f = new File(inpName);
            f.renameTo(new File(procName));
            FilesAssigned++;

            // Add the transaction to the list of the transactions that we
            // have opened this time around
            OpenedTransactions.add(tmpTransNumber);
          } else {
            // filled up the possibilities - finish for the moment
            break;
          }
        }
      }
    }

    return OpenedTransactions;
  }

  /**
   * shutdownStreamProcessOK closes down the processing and renames the input
   * file to show that we have done with it. It then completes the transaction
   * from the point of view of the Transaction Manager. This represents the
   * successful completion of the transaction.
   *
   * @param TransactionNumber The number of the transaction we are working on
   */
  public void shutdownStreamProcessOK(int TransactionNumber) {
    // rename the input file to show that it is no longer under the TMs control
    File f = new File(getProcName(TransactionNumber));
    f.renameTo(new File(getDoneName(TransactionNumber)));
  }

  /**
   * shutdownStreamProcessERR closes down the processing and renames the input
   * file to show that we have done with it. It then completes the transaction
   * from the point of view of the Transaction Manager. This represents the
   * failed completion of the transaction, and should leave everything as it was
   * before the transaction started.
   *
   * @param TransactionNumber The number of the transaction we are working on
   */
  public void shutdownStreamProcessERR(int TransactionNumber) {
    // rename the input file to show that it is no longer under the TMs control
    File f = new File(getProcName(TransactionNumber));
    f.renameTo(new File(getErrName(TransactionNumber)));
  }

  /**
   * Calculate and return the processing file path for the given base name. This
   * is the name the file will have during the processing.
   *
   * @param fileName The base file name of the file to work on
   * @param InputFilePath The path of the input file
   * @param InputFilePrefix The file prefix of the input file
   * @param InputFileSuffix The file suffix of the input file
   * @param ProcessingPrefix the file processing prefix to use
   * @param tmpTransNumber The transaction number
   * @return The full file path of the file in processing
   */
  protected String getProcFilePath(String fileName,
          String InputFilePath,
          String InputFilePrefix,
          String InputFileSuffix,
          String ProcessingPrefix,
          int tmpTransNumber) {
    return InputFilePath + System.getProperty("file.separator")
            + ProcessingPrefix + fileName;
  }

  /**
   * Calculate and return the done file path for the given base name. This is
   * the name the file will have during the processing.
   *
   * @param fileName The base file name of the file to work on
   * @param InputFilePrefix The file prefix of the input file
   * @param DoneFilePath The path of the done file
   * @param DoneFilePrefix The prefix of the done file
   * @param DoneFileSuffix The suffix of the done file
   * @param InputFileSuffix The file suffix of the input file
   * @param tmpTransNumber The transaction number
   * @return The full file path of the file in processing
   */
  protected String getDoneFilePath(String fileName,
          String InputFilePrefix,
          String InputFileSuffix,
          String DoneFilePath,
          String DoneFilePrefix,
          String DoneFileSuffix,
          int tmpTransNumber) {
    String baseName;

    baseName = fileName.replaceAll("^" + InputFilePrefix, "");
    baseName = baseName.replaceAll(InputFileSuffix + "$", "");

    return DoneFilePath + System.getProperty("file.separator")
            + DoneFilePrefix + baseName + DoneFileSuffix;
  }

  /**
   * Calculate and return the error file path for the given base name. This is
   * the name the file will have during the processing.
   *
   * @param fileName The base file name of the file to work on
   * @param InputFilePrefix The file prefix of the input file
   * @param ErrFilePath The file path fo the error file
   * @param ErrFilePrefix The prefix of the error file
   * @param ErrFileSuffix The suffix of the error file
   * @param InputFileSuffix The file suffix of the input file
   * @param tmpTransNumber The transaction number
   * @return The full file path of the file in processing
   */
  protected String getErrorFilePath(String fileName,
          String InputFilePrefix,
          String InputFileSuffix,
          String ErrFilePath,
          String ErrFilePrefix,
          String ErrFileSuffix,
          int tmpTransNumber) {
    String baseName;

    baseName = fileName.replaceAll("^" + InputFilePrefix, "");
    baseName = baseName.replaceAll(InputFileSuffix + "$", "");

    return ErrFilePath + System.getProperty("file.separator")
            + ErrFilePrefix + baseName + ErrFileSuffix;
  }

  /**
   * Calculate and return the base file path for the given base name. This is
   * the name the file will have during the processing.
   *
   * @param fileName The file name to use
   * @param InputFilePrefix The input file prefix
   * @param InputFileSuffix The input file suffix
   * @param tmpTransNumber Transaction number to get the name for
   * @return The base name for the transaction
   */
  protected String getFileBaseName(String fileName,
          String InputFilePrefix,
          String InputFileSuffix,
          int tmpTransNumber) {
    String baseName;

    baseName = fileName.replaceAll("^" + InputFilePrefix, "");
    baseName = baseName.replaceAll(InputFileSuffix + "$", "");

    return baseName;
  }

  /**
   * Calculate and return the input file path for the given base name.
   *
   * @param fileName The file name base name
   * @param InputFilePath The pate of the file
   * @return The full file path of the file in input
   */
  protected String getInputFilePath(String fileName,
          String InputFilePath) {
    return InputFilePath + System.getProperty("file.separator") + fileName;
  }

  /**
   * Get the proc file name for the given transaction
   *
   * @param TransactionNumber The number of the transaction we are working for
   * @return The processing name associated with this transaction
   */
  protected String getProcName(int TransactionNumber) {
    TransControlStructure tmpFileNames;

    // Get the name to work on
    tmpFileNames = CurrentFileNames.get(TransactionNumber);

    return tmpFileNames.ProcFileName;
  }

  /**
   * Get the done file name for the given transaction
   *
   * @param TransactionNumber The number of the transaction we are working for
   * @return The done name associated with this transaction
   */
  protected String getDoneName(int TransactionNumber) {
    TransControlStructure tmpFileNames;

    // Get the name to work on
    tmpFileNames = CurrentFileNames.get(TransactionNumber);

    return tmpFileNames.DoneFileName;
  }

  /**
   * Get the error file name for the given transaction
   *
   * @param TransactionNumber The number of the transaction we are working for
   * @return The error name associated with this transaction
   */
  protected String getErrName(int TransactionNumber) {
    TransControlStructure tmpFileNames;

    // Get the name to work on
    tmpFileNames = CurrentFileNames.get(TransactionNumber);

    return tmpFileNames.ErrorFileName;
  }

  /**
   * Get the base name for the given transaction
   *
   * @param TransactionNumber The number of the transaction we are working for
   * @return The base name associated with this transaction
   */
  protected String getBaseName(int TransactionNumber) {
    TransControlStructure tmpFileNames;

    // Get the name to work on
    tmpFileNames = CurrentFileNames.get(TransactionNumber);

    return tmpFileNames.BaseName;
  }

  /**
   * Set and XML attribute with the given value
   *
   * @param key The key to set
   * @param value The value to set
   */
  @Override
  public void setAttribute(String key, String value) {
    xmlValues.put(key, value);
  }

  /**
   * Provides a second level file name filter for files - may be overridden by
   * the implementation class
   *
   * @param fileNameToFilter The name of the file to filter
   * @return true if the file is to be processed, otherwise false
   */
  public boolean filterFileName(String fileNameToFilter) {
    // Filter out files that already have the processing prefix
    return (fileNameToFilter.startsWith(ProcessingPrefix) == false);
  }

  /**
   * Order the list of files. This is can be overridden so that the sure may
   * define their own rules.
   *
   * @param dir The directory to scan
   * @param filter The filter we are using
   * @return A list of files to process, first in list gets processed first
   */
  public String[] getOrderedFileListForProcessing(File dir, FilenameFilter filter) {
    // standard: no ordering
    return dir.list(filter);
  }
}
