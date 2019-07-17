
package OpenRate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.*;

/**
 * Tests OpenRate application framework loading, stopping, version handling,
 * console access and sundry functions. These tests are generally higher level
 * than process based tests, and attempt to provide a "roll up" of the other
 * tests.
 *
 * @author TGDSPIA1
 */
public class OpenRateTest {

  // The revision number has to be changed to match the current revision
  String OpenRateVersion = "V1.5.2.6";

  // By default we check that the build date is created on each build
  SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
  String revisionDate = sdf.format(new Date());

  // this is the OpenRate application object
  private static OpenRate appl;

  public OpenRateTest() {
  }

  @BeforeClass
  public static void setUpClass() throws Exception {
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
  }

  @Before
  public void setUp() {
  }

  @After
  public void tearDown() {
  }

  /**
   * Test of main method, of class OpenRate.
   */
  @Test
  public void testCheckParameters() {
    System.out.println("--> checkParameters");

    // no parameters passed at all
    String[] argsTestBad1 = new String[0];
    int expectedResult = -3;

    // get the application instance
    appl = OpenRate.getApplicationInstance();

    int result = appl.checkParameters(argsTestBad1);
    Assert.assertEquals(expectedResult, result);

    // Only 1 parameter
    String[] argsTestBad2 = new String[1];
    argsTestBad2[0] = "-p";
    expectedResult = -3;
    result = appl.checkParameters(argsTestBad2);
    Assert.assertEquals(expectedResult, result);

    // Only the other 1 parameter
    argsTestBad2[0] = "Simple.properties.xml";
    expectedResult = -3;
    result = appl.checkParameters(argsTestBad2);
    Assert.assertEquals(expectedResult, result);

    // Both parameters, but non existent file
    String[] argsTestBad4 = new String[2];
    argsTestBad4[0] = "-p";
    argsTestBad4[1] = "Notme.properties.xml";
    expectedResult = -5;
    result = appl.checkParameters(argsTestBad4);
    Assert.assertEquals(expectedResult, result);

    // ahhhh, finally! Someone got it right!
    String[] argsTestGood1 = new String[2];
    argsTestGood1[0] = "-p";
    argsTestGood1[1] = "TestDB.properties.xml";
    expectedResult = 0;
    result = appl.checkParameters(argsTestGood1);
    Assert.assertEquals(expectedResult, result);

    appl.cleanup();
  }

  /**
   * Test of version string method, of class OpenRate.
   */
  @Test
  public void testGetVersionString() {
    System.out.println("--> checkVersionString");

    // get the date portion of the version string
    String result;

    // get the application instance
    appl = OpenRate.getApplicationInstance();

    result = appl.getApplicationVersion();

    String expResult = "OpenRate " + OpenRateVersion + " (" + revisionDate + ")";
    Assert.assertEquals(expResult, result);

    appl.cleanup();
  }

  /**
   * Test of application startup. This test builds a real (but very simple)
   * processing pipeline using the standard framework startup procedure.
   */
  @Test(timeout = 10000)
  public void testApplicationStartup() {
    System.out.println("--> OpenRate startup");

    // get the date portion of the version string
    int expResult = 0;

    // Define the property file we are using
    String[] args = new String[2];
    args[0] = "-p";
    args[1] = "TestFramework.properties.xml";

    // Start up the framework
    appl = OpenRate.getApplicationInstance();
    int status = appl.createApplication(args);

    // check the start up of the framework
    Assert.assertEquals(expResult, status);

    Thread openRateThread = new Thread(appl);
    openRateThread.start();

    System.out.println("Waiting for startup to complete");
    while (!appl.isFrameworkActive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
      }
    }
    
    // wait for it to start
    System.out.println("Waiting for the system to come up");
    while (!appl.isFrameworkActive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        Logger.getLogger(OpenRateTest.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    // And test the shutdown using an injected stop message
    appl.processControlEvent("Shutdown", false, "true");

    // wait for it to stop
    System.out.println("Waiting for the system to stop");
    while (appl.isFrameworkActive()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ex) {
      }
    }

    // Finish off
    appl.finaliseApplication();
  }

  /**
   * Test of application startup. This test builds a real (but very simple)
   * processing pipeline using the standard framework startup procedure.
   */
  @Test(timeout = 10000)
  public void testApplicationStartupFail() {
    System.out.println("--> OpenRate startup failure: Properties not found");

    // get the date portion of the version string
    int expResult = -5;

    // Define the property file we are using
    String[] args = new String[2];
    args[0] = "-p";
    args[1] = "DoesNotExist.properties.xml";

    // Start up the framework
    appl = OpenRate.getApplicationInstance();
    int status = appl.createApplication(args);

    // check the start up of the framework
    Assert.assertEquals(expResult, status);

    // Finish off
    appl.finaliseApplication();
  }

  /**
   * Test of application startup. This test builds a real (but very simple)
   * processing pipeline using the standard framework startup procedure.
   */
  @Test(timeout = 10000)
  public void testApplicationCloseViaSemaphore() {
    System.out.println("--> OpenRate shutdown on Semaphore");

    // get the date portion of the version string
    int expResult = 0;

    // Define the property file we are using
    String[] args = new String[2];
    args[0] = "-p";
    args[1] = "TestFramework.properties.xml";

    // Start up the framework
    appl = OpenRate.getApplicationInstance();
    int status = appl.createApplication(args);

    // check the start up of the framework
    Assert.assertEquals(expResult, status);

    Thread openRateThread = new Thread(appl);
    openRateThread.start();

    System.out.println("Waiting for startup to complete");
    while (!appl.isFrameworkActive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
      }
    }
    
    // And test the shutdown
    try {
      // Create file 
      FileWriter fstream = new FileWriter("Semaphore.txt");
      try (BufferedWriter out = new BufferedWriter(fstream)) {
        out.write("Framework:Shutdown=true");
      }
    } catch (IOException e) {//Catch exception if any
      Assert.fail();
    }

    // wait for it to stop
    System.out.println("Waiting for the system to stop");
    while (appl.isFrameworkActive()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ex) {
      }
    }

    // Finish off
    appl.finaliseApplication();
  }

  /**
   * Test of application console. This test builds a real (but very simple)
   * processing pipeline using the standard framework startup procedure.
   */
  @Test(timeout = 10000)
  public void testApplicationConsole() {
    System.out.println("--> OpenRate console");

    // get the date portion of the version string
    int expResult = 0;

    // Define the property file we are using
    String[] args = new String[2];
    args[0] = "-p";
    args[1] = "TestFramework.properties.xml";

    // Start up the framework
    appl = OpenRate.getApplicationInstance();
    int status = appl.createApplication(args);

    Thread openRateThread = new Thread(appl);
    openRateThread.start();

    System.out.println("Waiting for startup to complete");
    while (!appl.isFrameworkActive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
      }
    }

    // check the start up of the framework
    Assert.assertEquals(expResult, status);

    // Now test that we can connect via socket
    Socket testSocket = null;
    try {
      testSocket = new Socket("localhost", 8086);
    } catch (UnknownHostException ex) {
      Assert.fail("Unable to open socket");
    } catch (IOException ex) {
      Assert.fail("Unable to open socket");
    }

    if (testSocket == null) {
      Assert.fail("Could not get socket");
      return;
    }

    BufferedReader inputReader = null;
    try {
      inputReader = new BufferedReader(new InputStreamReader(testSocket.getInputStream()));
    } catch (IOException ex) {
      Logger.getLogger(OpenRateTest.class.getName()).log(Level.SEVERE, null, ex);
    }

    if (inputReader == null) {
      Assert.fail("Unable to get reader");
      return;
    }

    String[] headerResponse = new String[7];
    headerResponse[0] = "--------------------------------------------------------------";
    headerResponse[1] = "OpenRate Admin Console, " + OpenRate.getApplicationVersionString();
    headerResponse[2] = "Copyright The OpenRate Project, 2006-2015";
    headerResponse[3] = headerResponse[0];
    headerResponse[4] = "";
    headerResponse[5] = "Type 'Help' for more information.";
    headerResponse[6] = "";

    // Get the welcome message
    String responseLine;
    int index = 0;
    try {
      while ((responseLine = inputReader.readLine()) != null) {
        // Check that we got the right response
        Assert.assertEquals(headerResponse[index], responseLine);
        index++;
        System.out.println("Server: " + responseLine);
        if (index == 7) {
          break;
        }
      }
    } catch (IOException ex) {
      Logger.getLogger(OpenRateTest.class.getName()).log(Level.SEVERE, null, ex);
    }

    char responseVal;
    index = 1;
    try {
      // Now see if we got the promt (this is not a full line)
      while ((responseVal = (char) inputReader.read()) != -1) {
        System.out.print(responseVal);
        if (index == 10) {
          break;
        }
        index++;
      }
    } catch (IOException ex) {
      Logger.getLogger(OpenRateTest.class.getName()).log(Level.SEVERE, null, ex);
    }

    // Now check the list of modules
    PrintWriter out = null;
    try {
      out = new PrintWriter(testSocket.getOutputStream(), true);
    } catch (IOException ex) {
      Logger.getLogger(OpenRateTest.class.getName()).log(Level.SEVERE, null, ex);
    }

    if (out == null) {
      Assert.fail("Could not get socket to write to");
    } else {
      out.println("m");
      String[] moduleResponse = new String[12];
      moduleResponse[0] = "OpenRate module listing:";
      moduleResponse[1] = "+--------------------+----------------------------------------+----------------------------------------------------+";
      moduleResponse[2] = "| Pipeline Name      | Module Name                            | Class                                              |";
      moduleResponse[3] = moduleResponse[1];
      moduleResponse[4] = "| DBTestPipe         | DBTestPipe                             | OpenRate.Pipeline                                  | ";
      moduleResponse[5] = "| Framework          | Framework                              | OpenRate.OpenRate                                  | ";
      moduleResponse[6] = "| Resource           | LogFactory                             | OpenRate.logging.LogFactory                        | ";
      moduleResponse[7] = "| DBTestPipe         | SOutAdapter                            | OpenRate.adapter.NullOutputAdapter                 | ";
      moduleResponse[8] = "| DBTestPipe         | TestInpAdapter                         | OpenRate.adapter.NullInputAdapter                  | ";
      moduleResponse[9] = "| Resource           | TransactionManager                     | OpenRate.transaction.TransactionManager            | ";
      moduleResponse[10] = moduleResponse[1];

      index = 0;
      try {
        while ((responseLine = inputReader.readLine()) != null) {
          // Check that we got the right response
          Assert.assertEquals(moduleResponse[index], responseLine);
          index++;
          System.out.println("Server: " + responseLine);
          if (index == 11) {
            break;
          }
        }
      } catch (IOException ex) {
        Logger.getLogger(OpenRateTest.class.getName()).log(Level.SEVERE, null, ex);
      }

      // Stop
      out.println("Framework:Shutdown=true");
      
      // get rid of console
      out.flush();
      out.close();
      
      try {
          testSocket.close();
      } catch (IOException ex) {
      }

      // wait for it to stop
      System.out.println("Waiting for the system to stop");
      while (appl.isFrameworkActive()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ex) {
          Logger.getLogger(OpenRateTest.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
    }

    // Finish off
    appl.finaliseApplication();
  }
}
