/* ====================================================================
 * Limited Evaluation License:
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

/**
 * Balance Impact class to map the balance impacts in the pipe.
 *
 * @author Ian
 */
public class BalanceImpact
{
 /**
  * The BalanceImpact structure holds the information about the impacts that
  * have been created on the record. This is used for both rating and discounting
  * cases. For the management of the fields, see the comments. D = Discounting
  * R = Rating
  */

 /**
  * (D,R) The name of Balance Group - can be used to manage impacts on
  * multiple balance groups in a single event.
  * @ added by Denis, Benjamin
  */
  public long balanceGroup=0;

 /**
  * The type of packet D = Discount, R = Rating
  */
  public String type=null;

 /**
  * (D) The name of the CPI that caused this impact
  */
  public String cpiName=null;

 /**
  * (D) The name of the rule that caused the impact
  */
  public String ruleName=null;

 /**
  * (D,R) The RUM that was used to cause the impact
  */
  public String rumUsed=null;

 /**
  * (D) The amount of the RUM used (R) the original RUM value
  */
  public double rumValueUsed;

 /**
  * (D) The amount of the RUM after (R) 0
  */
  public double rumValueAfter;

 /**
  * (D) The value of the RUM after (R) The Rated Value
  */
  public double balanceDelta;

 /**
  * (D) The value of the RUM after (R) The Rated Value
  */
  public double balanceAfter;

 /**
  * (R) The Resource that was impacted
  */
  public String Resource=null;

 /**
  * The ID of the counter
  */
  public int    counterID;

 /**
  * Internal identifier of the Counter period
  */
  public long   recID;

 /**
  * Start of the counter period
  */
  public long   startDate;

 /**
  * End of the counter period
  */
  public long   endDate;

  /**
   * Creates a new instance of BalanceImpact
   */
  public BalanceImpact()
  {
  }
}
