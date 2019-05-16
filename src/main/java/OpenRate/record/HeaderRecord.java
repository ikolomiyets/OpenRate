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

package OpenRate.record;

import java.util.ArrayList;

/**
 * Flat record is the basic type of record used by the file adapters, and
 * returns the data as a single string, which can then be split and processed
 * as required.
 */
public class HeaderRecord extends AbstractRecord
{
  private static final long serialVersionUID = 2340874768931864174L;

  // this holds the base name of the file we are working on
  private String StreamName = null;

  // This is used to pass the transaction number down the pipe
  private int TransactionNumber = 0;

  /** Overloaded constructor for derived classes */
  public HeaderRecord()
  {
    super();

    // mark the record as invalid so that it does not take part in the
    // processing
    this.setValid(false);
  }

 /**
  * This returns the dump information.
  */
  @Override
  public ArrayList<String> getDumpInfo()
  {
    ArrayList<String> tmpDumpList;
    tmpDumpList = new ArrayList<>();

    // Format the fields
    tmpDumpList.add("============ STREAM HEADER ===========");
    tmpDumpList.add("  Stream name    = <" + this.StreamName + ">");
    tmpDumpList.add("  Transaction    = <" + this.TransactionNumber + ">");

    return tmpDumpList;
  }

 /**
  * Set the stream level base name, so that non transactional modules can also
  * access the inforamtion if they need.
  *
  * @param NewStreamName The stream name to set
  */
  public void setStreamName(String NewStreamName)
  {
    StreamName = NewStreamName;
  }

 /**
  * Return the stream base name for anyone that needs it
  *
  * @return The stream name of the stream
  */
  public String getStreamName()
  {
    return StreamName;
  }

 /**
  * Set the transaction Number which this is the header record for
  *
  * @param newTransNumber The transaction number to set
  */
  public void setTransactionNumber(int newTransNumber)
  {
    TransactionNumber = newTransNumber;
  }

 /**
  * Return the transaction number for anyone that needs it
  *
  * @return The transaction number of this stream
  */
  public int getTransactionNumber()
  {
    return TransactionNumber;
  }
}
