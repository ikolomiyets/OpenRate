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

package OpenRate.process;

import OpenRate.cache.ICacheManager;
import OpenRate.cache.MultipleValidityCache;
import OpenRate.exception.InitializationException;
import OpenRate.record.IRecord;
import OpenRate.resource.CacheFactory;
import OpenRate.utils.PropertyUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * This class looks up which periods of validity cover the given key at a
 * given date and time. It is possible to locate the first match, or all
 * matches.
 *
 */
public abstract class AbstractMultipleValidityMatch extends AbstractPlugIn
{
  // This is the object will be using the find the cache manager
  private ICacheManager CMV = null;

  /**
   * The validity segment cache
   */
  protected MultipleValidityCache MV;

  // -----------------------------------------------------------------------------
  // ------------------ Start of inherited Plug In functions ---------------------
  // -----------------------------------------------------------------------------

 /**
  * Initialise the module. Called during pipeline creation to initialise:
  *  - Configuration properties that are defined in the properties file.
  *  - The references to any cache objects that are used in the processing
  *  - The symbolic name of the module
  *
  * @param PipelineName The name of the pipeline this module is in
  * @param ModuleName The name of this module in the pipeline
  * @throws OpenRate.exception.InitializationException
  */
  @Override
  public void init(String PipelineName, String ModuleName)
            throws InitializationException
  {
    // Variable for holding the cache object name
    String CacheObjectName;

    super.init(PipelineName,ModuleName);

    // Get the cache object reference
    CacheObjectName = PropertyUtils.getPropertyUtils().getPluginPropertyValue(PipelineName,
                                                           ModuleName,
                                                           "DataCache");

    CMV = CacheFactory.getGlobalManager(CacheObjectName);

    if (CMV == null)
    {
      message = "Could not find cache entry for <" + CacheObjectName + ">";
      throw new InitializationException(message,getSymbolicName());
    }

    // Load up the mapping array
    MV = (MultipleValidityCache)CMV.get(CacheObjectName);

    if (MV == null)
    {
      message = "Could not find cache entry for <" + CacheObjectName + ">";
      throw new InitializationException(message,getSymbolicName());
    }
  }

 /**
  * Loop through for the header ...
  */
  @Override
  public IRecord procHeader(IRecord r)
  {
    return r;
  }

 /**
  * ... and trailer
  */
  @Override
  public IRecord procTrailer(IRecord r)
  {
    return r;
  }

  // -----------------------------------------------------------------------------
  // -------------------- Start of custom Plug In functions ----------------------
  // -----------------------------------------------------------------------------

 /**
  * This returns the validity segment match
  *
  * @param group The regular expression group to search
  * @param resourceID The resource id to get the match for
  * @param eventTime the UTC event date to match for
  * @return The returned value, or NOMATCH if none was found
  */
  public String getFirstValidityMatch(String group, String resourceID, long eventTime)
  {
    return MV.getFirstValidityMatch(group, resourceID, eventTime);
  }

 /**
  * This returns the regular expression match
  *
  * @param group The regular expression group to search
  * @param resourceID The resource ID to search for
  * @param EventTime The UTC event time to search at
  * @return The returned value, or NOMATCH if none was found
  */
  public ArrayList<String> getFirstValidityMatchWithChildData(String group, String resourceID, long eventTime)
  {
    return MV.getFirstValidityMatchWithChildData(group, resourceID, eventTime);
  }

 /**
  * This returns the validity segment match
  *
  * @param group The regular expression group to search
  * @param resourceID The resource id to get the match for
  * @param eventTime the UTC event date to match for
  * @return The returned value, or NOMATCH if none was found
  */
  public ArrayList<String> getAllValidityMatches(String group, String resourceID, long eventTime)
  {
    return MV.getAllValidityMatches(group, resourceID, eventTime);
  }

 /**
  * This returns the regular expression match
  *
  * @param group The regular expression group to search
  * @param resourceID The resource ID to search for
  * @param eventTime The UTC event time to search at
  * @return The returned value, or NOMATCH if none was found
  */
  public ArrayList<ArrayList<String>> getAllValidityMatchesWithChildData(String group, String resourceID, long eventTime)
  {
    return MV.getAllValidityMatchesWithChildData(group, resourceID, eventTime);
  }

 /**
   * checks if the lookup result is valid or not
   *
   * @param resultToCheck The result to check
   * @return true if the result is valid, otherwise false
   */
  public boolean isValidMultipleValidityMatchResult(List<?> resultToCheck)
  {
    // check the outer container
    if (resultToCheck == null || resultToCheck.isEmpty())
    {
      return false;
    }

    // if there is an inner container, check it
    if (resultToCheck.get(0) instanceof List)
    {
      List tmpResult = (List) resultToCheck.get(0);

      if ( tmpResult == null || tmpResult.isEmpty())
      {
        return false;
      }

      if ( tmpResult.get(0).equals(MultipleValidityCache.NO_VALIDITY_MATCH))
      {
        return false;
      }
    }
    else
    {
      // No inner container - just check the value we have
      if ( resultToCheck.isEmpty())
      {
        return false;
      }

      if ( resultToCheck.get(0).equals(MultipleValidityCache.NO_VALIDITY_MATCH))
      {
        return false;
      }
    }

    return true;
  }

 /**
   * checks if the lookup result is valid or not
   *
   * @param resultToCheck The result to check
   * @return true if the result is valid, otherwise false
   */
  public boolean isValidMultipleValidityMatchResult(String resultToCheck)
  {
    if ( resultToCheck == null)
    {
      return false;
    }

    if (resultToCheck.equalsIgnoreCase(MultipleValidityCache.NO_VALIDITY_MATCH))
    {
      return false;
    }

    return true;
  }
}
