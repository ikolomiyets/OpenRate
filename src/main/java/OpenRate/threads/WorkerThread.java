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

package OpenRate.threads;

/**
 * Worker Threads performs a given work and sleeps after the work is finished.
 *
 * The thread is managed by a IThreadPool and is used in conjuction with a
 * IThreadPool.
 *
 * The threads sleeps as long as there is no work to be performed.
 * IThreadPool assigns work to the thread and wakes up the WorkerThread to
 * perform the given work. Once the thread finishes the work it sets itself
 * to as having no work.
 */
public class WorkerThread extends Thread
{
  /**
   * CVS version info - Automatically captured and written to the Framework
   * Version Audit log at Framework startup. For more information
   * please <a target='new' href='http://www.open-rate.com/wiki/index.php?title=Framework_Version_Map'>click here</a> to go to wiki page.
   */
  public static String CVS_MODULE_INFO = "OpenRate, $RCSfile: WorkerThread.java,v $, $Revision: 1.18 $, $Date: 2013-05-13 18:12:13 $";

  // assigned work
  private Runnable work = null;

  // indicates if the thread is marked for shutdown by the IThreadPool.
  private boolean shutDown = false;

  /**
   * Constructor - constructs a WorkerThread
   *
   * @param pool - reference to the managing IThreadPool
   */
  public WorkerThread(IThreadPool pool)
  {
    setDaemon(true);
  }

  /**
   * Perform the following steps, in a loop, until asked to shutdown:
   *  1. If there is no work then go to sleep ( wait ).
   *  2. If there is work:
   *      - perform the work
   *      - reset work to null
   *  3. Exit the loop, if asked to shutdown.
   */
  @Override
  public void run()
  {
    while (true)
    {
      if (noWork())
      {
        try
        {
          synchronized (this)
          {
            wait();
          }
        }
        catch (InterruptedException e)
        {
          // ignore
        }
      }
      else
      {
        work.run();
        setNoWork();
      }

      if (shutDown)
      {
        break;
      }
    }
  }

  /**
   * return true is there is no work to be performed.
   *
   * @return boolean
   */
  public boolean noWork()
  {
    return work == null;
  }

  /**
   * assign no work to the worked thread.
   */
  public void setNoWork()
  {
    work = null;
  }

  /**
   * mark the thread to be shutdown.
   */
  public void markForShutDown()
  {
    shutDown = true;
  }

  /**
   * Sets the work to be performed by the thread.
   *
   * @param action - action to be performed by the thread
   */
  public void setWork(Runnable action)
  {
    work = action;
  }
}
