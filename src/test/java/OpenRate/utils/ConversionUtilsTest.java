/* ====================================================================
 * Limited Evaluation License:
 *
 * This software is open source, but licensed. The license with this package
 * is an evaluation license, which may not be used for productive systems. If
 * you want a full license, please contact us.
 *
 * The exclusive owner of this work is the OpenRate project.
 * This work, including all associated documents and components
 * is Copyright of the OpenRate project 2006-2015.
 *
 * The following restrictions apply unless they are expressly relaxed in a
 * contractual agreement between the license holder or one of its officially
 * assigned agents and you or your organisation:
 *
 * 1) This work may not be disclosed, either in full or in part, in any form
 *    electronic or physical, to any third party. This includes both in the
 *    form of source code and compiled modules.
 * 2) This work contains trade secrets in the form of architecture, algorithms
 *    methods and technologies. These trade secrets may not be disclosed to
 *    third parties in any form, either directly or in summary or paraphrased
 *    form, nor may these trade secrets be used to construct products of a
 *    similar or competing nature either by you or third parties.
 * 3) This work may not be included in full or in part in any application.
 * 4) You may not remove or alter any proprietary legends or notices contained
 *    in or on this work.
 * 5) This software may not be reverse-engineered or otherwise decompiled, if
 *    you received this work in a compiled form.
 * 6) This work is licensed, not sold. Possession of this software does not
 *    imply or grant any right to you.
 * 7) You agree to disclose any changes to this work to the copyright holder
 *    and that the copyright holder may include any such changes at its own
 *    discretion into the work
 * 8) You agree not to derive other works from the trade secrets in this work,
 *    and that any such derivation may make you liable to pay damages to the
 *    copyright holder
 * 9) You agree to use this software exclusively for evaluation purposes, and
 *    that you shall not use this software to derive commercial profit or
 *    support your business or personal activities.
 *
 * This software is provided "as is" and any expressed or impled warranties,
 * including, but not limited to, the impled warranties of merchantability
 * and fitness for a particular purpose are disclaimed. In no event shall
 * The OpenRate Project or its officially assigned agents be liable to any
 * direct, indirect, incidental, special, exemplary, or consequential damages
 * (including but not limited to, procurement of substitute goods or services;
 * Loss of use, data, or profits; or any business interruption) however caused
 * and on theory of liability, whether in contract, strict liability, or tort
 * (including negligence or otherwise) arising in any way out of the use of
 * this software, even if advised of the possibility of such damage.
 * This software contains portions by The Apache Software Foundation, Robert
 * Half International.
 * ====================================================================
 */
package OpenRate.utils;

import OpenRate.OpenRate;
import OpenRate.resource.ConversionCache;
import TestUtils.FrameworkUtils;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.junit.*;

/**
 *
 * @author tgdspia1
 */
public class ConversionUtilsTest {

  private static URL FQConfigFileName;
  private static ConversionUtils instance;
  private static TimeZone tz;
  private static Calendar cal;

  public ConversionUtilsTest() {
  }

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Set up time zone
    tz = TimeZone.getDefault();
    cal = Calendar.getInstance();
    cal.setTimeZone(tz);

    // Get an initialise the cache
    FQConfigFileName = new URL("File:src/test/resources/TestUtils.properties.xml");

    // Set up the OpenRate internal logger - this is normally done by app startup
    OpenRate.getApplicationInstance();

    // Load the properties into the OpenRate object
    FrameworkUtils.loadProperties(FQConfigFileName);

    // Get the loggers
    FrameworkUtils.startupLoggers();

    // Get the conversion cache resource
    FrameworkUtils.startupConversionCache();
  }

  @AfterClass
  public static void tearDownClass() {
    // Deallocate
    if (tz != null) {
      TimeZone.setDefault(tz);
    }
    OpenRate.getApplicationInstance().cleanup();
  }

  @Before
  public void setUp() {
    getInstance();
  }

  @After
  public void tearDown() {
    releaseInstance();
  }

  /**
   * Test of getConversionCache method, of class ConversionUtils.
   */
  @Test
  public void testGetConversionCache() {
    System.out.println("getConversionCache");
    ConversionCache result = ConversionUtils.getConversionCache();

    // Check that we get something back
    Assert.assertNotNull(result);
  }

  /**
   * Test of getConversionCache method, of class ConversionUtils.
   */
  @Test
  public void testGetConversionCacheSymbolicName() {
    System.out.println("getConversionCache");
    String result = ConversionUtils.getConversionCache().getSymbolicName();

    // Check that we get something back
    Assert.assertEquals("ConversionCache", result);
  }

  /**
   * Test of convertInputDateToUTC method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testConvertInputDateToUTC() throws ParseException {
    long expResult;
    long result;
    String amorphicDate;

    System.out.println("convertInputDateToUTC");

    // Test the standard date formatsTimeZone
    amorphicDate = "20120101000000";
    instance.setInputDateFormat("yyyyMMddhhmmss");
    expResult = getUTCDateExpected(2012,1,1,0,0,0);
    
    result = instance.convertInputDateToUTC(amorphicDate);
    Assert.assertEquals(expResult, result);

    // another common one
    amorphicDate = "2012-01-01 00:00:00";
    instance.setInputDateFormat("yyyy-MM-dd hh:mm:ss");
    result = instance.convertInputDateToUTC(amorphicDate);
    Assert.assertEquals(expResult, result);

    // an integer
    amorphicDate = Long.toString(expResult);
    instance.setInputDateFormat("integer");
    result = instance.convertInputDateToUTC(amorphicDate);
    Assert.assertEquals(expResult, result);

    // a long
    expResult = 1325372400;
    amorphicDate = Long.toString(expResult) + "000";
    instance.setInputDateFormat("long");
    result = instance.convertInputDateToUTC(amorphicDate);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getDayOfWeek method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testGetDayOfWeek() throws ParseException {
    long expResult;
    long result;
    long UTCDate;
    String amorphicDate;

    System.out.println("getDayOfWeek");

    // Test the standard date formats
    amorphicDate = "20120101000000";
    instance.setInputDateFormat("yyyyMMddhhmmss");
    UTCDate = instance.convertInputDateToUTC(amorphicDate);
    expResult = getUTCDateExpected(2012,1,1,0,0,0);
    Assert.assertEquals(expResult, expResult);

    result = instance.getDayOfWeek(UTCDate);

    // We expect 1 (Sunday)
    expResult = Calendar.SUNDAY;
    Assert.assertEquals(expResult, result);

    // now try rolling over
    amorphicDate = "20120106000000";
    UTCDate = instance.convertInputDateToUTC(amorphicDate);
    result = instance.getDayOfWeek(UTCDate);

    // We expect 6 (Friday)
    expResult = Calendar.FRIDAY;
    Assert.assertEquals(expResult, result);

    // now try rolling over
    UTCDate += 86400 * 2;
    result = instance.getDayOfWeek(UTCDate);

    // We expect 1 (Sunday)
    expResult = Calendar.SUNDAY;
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getMinuteOfDay method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testGetMinuteOfDay() throws ParseException {
    long expResult;
    long result;
    long UTCDate;
    String amorphicDate;

    System.out.println("getMinuteOfDay");

    amorphicDate = "20120101000000";
    instance.setInputDateFormat("yyyyMMddhhmmss");
    UTCDate = instance.convertInputDateToUTC(amorphicDate);
    expResult = getUTCDateExpected(2012,1,1,0,0,0);
    Assert.assertEquals(expResult, expResult);

    // minute 0 of day
    result = instance.getMinuteOfDay(UTCDate);
    expResult = 0;
    Assert.assertEquals(expResult, result);

    // after 1 hour 1 min
    amorphicDate = "20120101010101";
    instance.setInputDateFormat("yyyyMMddhhmmss");
    UTCDate = instance.convertInputDateToUTC(amorphicDate);

    // minute 61 of day
    result = instance.getMinuteOfDay(UTCDate);
    expResult = 61;
    Assert.assertEquals(expResult, result);

    // after 23 hour 59 min
    amorphicDate = "20120101235959";
    instance.setInputDateFormat("yyyyMMddhhmmss");
    UTCDate = instance.convertInputDateToUTC(amorphicDate);

    // minute 1439 of day
    result = instance.getMinuteOfDay(UTCDate);
    expResult = 1439;
    Assert.assertEquals(expResult, result);

    // Rollover
    UTCDate += 1;

    // minute 0 of day
    result = instance.getMinuteOfDay(UTCDate);
    expResult = 0;
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of setInputDateFormat method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testSetInputDateFormat() throws ParseException {
    String amorphicDate;

    System.out.println("setInputDateFormat");

    amorphicDate = "20120101000000";
    instance.setInputDateFormat("yyyyMMddhhmmss");

    try {
      instance.convertInputDateToUTC(amorphicDate);
    } catch (Exception ex) {
      Assert.fail("We expect no exception.");
    }
  }

  /**
   * Test of getInputDateFormat method, of class ConversionUtils.
   */
  @Test
  public void testGetInputDateFormat() {
    String expResult;
    String result;

    System.out.println("getInputDateFormat");

    // default format
    expResult = "yyyy-MM-dd HH:mm:ss";
    result = instance.getInputDateFormat();
    Assert.assertEquals(expResult, result);

    // another
    instance.setInputDateFormat("yyyyMMddhhmmss");
    expResult = "yyyyMMddhhmmss";
    result = instance.getInputDateFormat();
    Assert.assertEquals(expResult, result);

    // and another
    instance.setInputDateFormat("yyyy-MM-dd hh:mm:ss");
    expResult = "yyyy-MM-dd hh:mm:ss";
    result = instance.getInputDateFormat();
    Assert.assertEquals(expResult, result);

    // and another
    instance.setInputDateFormat("integer");
    expResult = "integer";
    result = instance.getInputDateFormat();
    Assert.assertEquals(expResult, result);

    // and another
    instance.setInputDateFormat("long");
    expResult = "long";
    result = instance.getInputDateFormat();
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of setOutputDateFormat method, of class ConversionUtils.
   */
  @Test
  public void testSetOutputDateFormat() {
    System.out.println("setOutputDateFormat");

    try {
      instance.setOutputDateFormat("yyyyMMddhhmmss");
    } catch (Exception ex) {
      Assert.fail("We expect no exception.");
    }
  }

  /**
   * Test of getOutputDateFormat method, of class ConversionUtils.
   */
  @Test
  public void testGetOutputDateFormat() {
    String expResult;
    String result;

    System.out.println("getOutputDateFormat");

    // default format
    expResult = "yyyy-MM-dd HH:mm:ss";
    result = instance.getOutputDateFormat();
    Assert.assertEquals(expResult, result);

    // another
    instance.setOutputDateFormat("yyyyMMddhhmmss");
    expResult = "yyyyMMddhhmmss";
    result = instance.getOutputDateFormat();
    Assert.assertEquals(expResult, result);

    // and another
    instance.setOutputDateFormat("yyyy-MM-dd hh:mm:ss");
    expResult = "yyyy-MM-dd hh:mm:ss";
    result = instance.getOutputDateFormat();
    Assert.assertEquals(expResult, result);

    // and another
    instance.setOutputDateFormat("integer");
    expResult = "integer";
    result = instance.getOutputDateFormat();
    Assert.assertEquals(expResult, result);

    // and another
    instance.setOutputDateFormat("long");
    expResult = "long";
    result = instance.getOutputDateFormat();
    Assert.assertEquals(expResult, result);

    // make sure that we didn't change the input date format
    expResult = "yyyy-MM-dd HH:mm:ss";
    result = instance.getInputDateFormat();
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of formatLongDate method, of class ConversionUtils.
   */
  @Test
  public void testFormatLongDate_long() {
    String result;
    long dateToFormat;
    String expResult;

    System.out.println("formatLongDate");

    // simple case
    dateToFormat = getUTCDateExpected(2012,1,1,0,0,0);
    instance.setOutputDateFormat("yyyyMMddHHmmss");
    result = instance.formatLongDate(dateToFormat);
    expResult = "20120101000000";
    Assert.assertEquals(expResult, result);

    //
    dateToFormat += 3621;
    result = instance.formatLongDate(dateToFormat);
    expResult = "20120101010021";
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of formatLongDate method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testFormatLongDate_Date() throws ParseException {
    String result;
    Date dateToFormat;
    String expResult;
    long dateInput;

    System.out.println("formatLongDate");

    // simple case
    dateInput = this.getUTCDateExpected(2012,1,1,0,0,0);
    dateToFormat = instance.getDateFromUTC(dateInput);
    instance.setOutputDateFormat("yyyyMMddHHmmss");
    result = instance.formatLongDate(dateToFormat);
    expResult = "20120101000000";
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getDatefromLongFormat method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testGetDatefromLongFormat() throws ParseException {
    String DateToFormat;
    Date expResult;

    System.out.println("getDatefromLongFormat");

    // simple case
    expResult = getDateExpected(2012,1,1,0,0,0);
    DateToFormat = "20120101000000";
    instance.setInputDateFormat("yyyyMMddHHmmss");
    Date result = instance.getDatefromLongFormat(DateToFormat);
    Assert.assertTrue(expResult.compareTo(result) == 0);
  }

  /**
   * Test of getGmtDate method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testGetGmtDate() throws ParseException {
    int offSet;
    int dateInput;
    String DateToFormat;
    Date dateFormatted;
    Date expResult;
    Date result;

    System.out.println("getGmtDate");

    // No DST
    DateToFormat = "20120101000000";
    instance.setInputDateFormat("yyyyMMddHHmmss");
    dateFormatted = instance.getDatefromLongFormat(DateToFormat);
    dateInput = (int) (dateFormatted.getTime() / 1000);
    offSet = tz.getOffset(dateInput * 1000);
    dateFormatted = instance.getDateFromUTC(dateInput);
    expResult = instance.getDateFromUTC(dateInput - (offSet / 1000));
    result = instance.getGmtDate(dateFormatted);
    Assert.assertEquals(expResult, result);

    // DST
    DateToFormat = "20120601000000"; // winter - no DST
    instance.setInputDateFormat("yyyyMMddHHmmss");
    dateFormatted = instance.getDatefromLongFormat(DateToFormat);
    dateInput = (int) (dateFormatted.getTime() / 1000);
    offSet = tz.getOffset(dateInput * 1000) + tz.getDSTSavings();
    dateFormatted = instance.getDateFromUTC(dateInput);
    expResult = instance.getDateFromUTC(dateInput - (offSet / 1000));
    result = instance.getGmtDate(dateFormatted);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getDateInDST method, of class ConversionUtils. Not all time zones
   * have DST. We take a day a month and check it.
   */
  @Test
  public void testGetDateInDST() {
    System.out.println("getDateInDST");

    for (int month = 1 ; month < 12 ; month++) {
      Date expDate = getDateExpected(2012, month, 1, 0, 0, 0);
      boolean expResult = tz.inDaylightTime(expDate);
      boolean result = instance.getDateInDST(expDate);
      Assert.assertEquals(expResult, result);
    }
  }

  /**
   * Test of getUTCMonthStart method, of class ConversionUtils.
   */
  @Test
  public void testGetUTCMonthStart() {
    System.out.println("getUTCMonthStart");

    long dateInput = getUTCDateExpected(2012,1,1,0,0,0);
    long expResult = dateInput;

    // Move the date 20+ days on
    dateInput += 2322134;
    Date EventStartDate = instance.getDateFromUTC(dateInput);
    long result = instance.getUTCMonthStart(EventStartDate);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getUTCMonthEnd method, of class ConversionUtils.
   */
  @Test
  public void testGetUTCMonthEnd() {
    System.out.println("getUTCMonthEnd");

    long dateInput = getUTCDateExpected(2012,1,31,23,59,59);
    long expResult = dateInput;

    // Move the date 20+ days back
    dateInput -= 2322134;
    Date EventStartDate = instance.getDateFromUTC(dateInput);
    long result = instance.getUTCMonthEnd(EventStartDate);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getUTCDayStart method, of class ConversionUtils.
   */
  @Test
  public void testGetUTCDayStart() {
    System.out.println("getUTCDayStart");

    long dateInput = getUTCDateExpected(2012,1,1,0,0,0);
    long expResult = dateInput;

    // Move the date 4+ hours on, but when we get the result, it should be
    // rounded back down to the start date
    dateInput += 12134;
    Date EventStartDate = instance.getDateFromUTC(dateInput);
    long result = instance.getUTCDayStart(EventStartDate);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getUTCDate method, of class ConversionUtils.
   */
  @Test
  public void testGetUTCDate() {
    System.out.println("getUTCDate");

    int dateInput = 1325372400;
    Date EventStartDate = instance.getDateFromUTC(dateInput);
    long expResult = dateInput;
    long result = instance.getUTCDate(EventStartDate);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getDateFromUTC method, of class ConversionUtils.
   *
   * @throws java.text.ParseException
   */
  @Test
  public void testGetDateFromUTC() throws ParseException {
    System.out.println("getDateFromUTC");

    long EventStartDate = 1325372400;
    DateFormat dfm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dfm.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));
    Date expResult = dfm.parse("2012-01-01 00:00:00");
    Date result = instance.getDateFromUTC(EventStartDate);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getUTCDayEnd method, of class ConversionUtils.
   */
  @Test
  public void testGetUTCDayEnd_Date() {
    System.out.println("getUTCDayEnd");

    long dateInput = getUTCDateExpected(2012,1,1,23,59,59);
    long expResult = dateInput;

    // Move the date 4+ hours back, but when we get the result, it should have
    // been rounded up again
    dateInput -= 12134;
    Date EventStartDate = instance.getDateFromUTC(dateInput);
    long result = instance.getUTCDayEnd(EventStartDate);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getUTCDayEnd method, of class ConversionUtils.
   */
  @Test
  public void testGetUTCDayEnd_Date_int() {
    System.out.println("getUTCDayEnd");

    long dateInput = getUTCDateExpected(2012,1,1,23,59,59);
    long expResult = dateInput;

    // Move the date 4+ hours back, but when we get the result, it should have
    // been rounded up again
    dateInput -= 12134;
    Date EventStartDate = instance.getDateFromUTC(dateInput);

    // get the end of the day with offset 0, i.e. today
    long result = instance.getUTCDayEnd(EventStartDate, 0);
    Assert.assertEquals(expResult, result);
  }

  /**
   * Test of getRoundedValue method, of class ConversionUtils.
   */
  @Test
  public void testGetRoundedValue() {
    double valueToRound;
    int decimalPlaces = 2;
    double expResult;
    double result;

    System.out.println("getRoundedValue");

    // already rounded - round it up
    valueToRound = 1.26;
    expResult = 1.26;
    result = instance.getRoundedValue(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // simple case of round up
    valueToRound = 1.260001;
    expResult = 1.26;
    result = instance.getRoundedValue(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // simple case of round up
    valueToRound = 1.265001;
    expResult = 1.27;
    result = instance.getRoundedValue(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);
  }

  /**
   * Test of getRoundedValueRoundUp method, of class ConversionUtils.
   */
  @Test
  public void testGetRoundedValueRoundUp() {
    double valueToRound;
    int decimalPlaces = 2;
    double expResult;
    double result;

    System.out.println("getRoundedValueRoundUp");

    // already rounded - round it up
    valueToRound = 1.26;
    expResult = 1.27;
    result = instance.getRoundedValueRoundUp(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // simple case of round up
    valueToRound = 1.260001;
    expResult = 1.27;
    result = instance.getRoundedValueRoundUp(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // round up on half
    valueToRound = 1.555;
    expResult = 1.56;
    result = instance.getRoundedValueRoundUp(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // round up on half
    valueToRound = 1.5699999;
    expResult = 1.57;
    result = instance.getRoundedValueRoundUp(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);
  }

  /**
   * Test of getRoundedValueRoundDown method, of class ConversionUtils.
   */
  @Test
  public void testGetRoundedValueRoundDown() {
    double valueToRound;
    int decimalPlaces = 2;
    double expResult;
    double result;

    System.out.println("getRoundedValueRoundDown");

    // simple case of truncation
    valueToRound = 1.254;
    expResult = 1.25;
    result = instance.getRoundedValueRoundDown(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // simple case of round down
    valueToRound = 1.2551;
    expResult = 1.25;
    result = instance.getRoundedValueRoundDown(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // round up on half
    valueToRound = 1.555;
    expResult = 1.55;
    result = instance.getRoundedValueRoundDown(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // round down on half
    valueToRound = 1.565;
    expResult = 1.56;
    result = instance.getRoundedValueRoundDown(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);
  }

  /**
   * Test of getRoundedValueRoundHalfEven method, of class ConversionUtils.
   */
  @Test
  public void testGetRoundedValueRoundHalfEven() {
    double valueToRound;
    int decimalPlaces = 2;
    double expResult;
    double result;

    System.out.println("getRoundedValueRoundHalfEven");

    // simple case of truncation
    valueToRound = 1.254;
    expResult = 1.25;
    result = instance.getRoundedValueRoundHalfEven(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // simple case of round up
    valueToRound = 1.2551;
    expResult = 1.26;
    result = instance.getRoundedValueRoundHalfEven(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // round up on half
    valueToRound = 1.555;
    expResult = 1.55;
    result = instance.getRoundedValueRoundHalfEven(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);

    // round down on half
    valueToRound = 1.565;
    expResult = 1.56;
    result = instance.getRoundedValueRoundHalfEven(valueToRound, decimalPlaces);
    Assert.assertEquals(expResult, result, 0.0);
  }

  /**
   * Get the UTC date using the calendar. We expect 
   */
  private long getUTCDateExpected(int year, int month, int day, int hour, int min, int sec) {
    cal.set(year, month-1, day, hour, min, sec);
    return cal.getTimeInMillis()/1000;
  }
  
  /**
   * Get the UTC date using the calendar. We expect 
   */
  private Date getDateExpected(int year, int month, int day, int hour, int min, int sec) {
    cal.set(year, month-1, day, hour, min, sec);
    cal.set(Calendar.MILLISECOND,0);
    return cal.getTime();
  }
  
  /**
   * Method to get an instance of the implementation. Done this way to allow
   * tests to be executed individually.
   *
   * @throws InitializationException
   */
  private void getInstance() {
    if (instance == null) {
      // Get an initialise the cache
      instance = new ConversionUtils();
    } else {
      org.junit.Assert.fail("Instance already allocated");
    }
  }

  /**
   * Method to release an instance of the implementation.
   */
  private void releaseInstance() {
    instance = null;
  }
}
