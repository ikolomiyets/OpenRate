package OpenRate.utils;

import OpenRate.exception.InitializationException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The property reader class is used to read data from XML based records.
 *
 * @author ian
 */
public class PropertyReader
{
  /**
   * Used as a worker variable when handling XML data
   */
  private Element propXMLObject = null;

  /**
   * The cache of the XML elements
   */
  private HashMap<String, Element> xmlCache = new HashMap<>(5);

 /**
  * Defines the name of the root element
  */
  private String ROOT_ELEMENT = "config";

 /**
  * Defines the location of the property folder
  */
  private String PROPERTY_FOLDER = "properties";

  // used to simplify logging and exception handling
  public String message;
  
  // used for logging and messages
  private String symbolicName = "PropertyUtils";
  
 /**
  * Creates a new instance of PropertyReader with default values
  */
  public PropertyReader()
  {
  }

 /**
  * Creates new instance of PropertyReader
  *
  * @param RootElement the name of the root element in XML file
  * @param PropertyFolder The Folder containing the file
  */
  public PropertyReader(String RootElement, String PropertyFolder)
  {
    this.ROOT_ELEMENT = RootElement;
    this.PROPERTY_FOLDER = PropertyFolder;
  }

 /**
  * Gets the root element of the xml structure
  *
  * @return The root element
  */
  public String getRootElement()
  {
      return ROOT_ELEMENT;
  }

  /**
   * Gets the properties folder
   *
   * @return The properties folder
   */
  public String getPropertyFolder()
  {
      return PROPERTY_FOLDER;
  }

 /**
  * Loading method for XML properties files. This loads the XML structure into
  * the internal representation and performs a parsing on it.
  *
  * @param filename the name of the file to load
  * @param PropertyName The symbolic name of the properties object in the cache
  * @throws OpenRate.exception.InitializationException
  */
  public void loadPropertiesXML(URL filename, String PropertyName)
  throws InitializationException
  {
    Document xmlDocument;

    try
    {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      xmlDocument = db.parse(filename.openStream());
      xmlDocument.getDocumentElement().normalize();
    } catch (IOException | ParserConfigurationException | NullPointerException | SAXException ex) {
      message = "Exception creating DOM parser";
      throw new InitializationException(message,ex,getSymbolicName());
    }

    // move the information over
    propXMLObject = xmlDocument.getDocumentElement();
    xmlCache.put(PropertyName,propXMLObject);
  }

  /**
   * Get a properties object from the cache
   *
   * @param PropertyName The name of the property to retrieve
   * @throws InitializationException
   */
  public void usePropertiesXML(String PropertyName)
      throws InitializationException
  {
      if (xmlCache.containsKey(PropertyName))
      {
        propXMLObject = xmlCache.get(PropertyName);
      }
      else
      {
        throw new InitializationException("Cannot find properties set <" + PropertyName + ">",getSymbolicName());
      }
  }

  /**
   * Returns the content of the XML node at the location indicated by the
   * "elementName".
   *
   * Element name should be referenced by a path separated by "." separators, for
   * example the elementName "Input.BatchSize" will point the the "BatchSize"
   * child element of the "Input" element.
   *
   *
   * @param elementName the path to the content to retrieve
   * @return String the content value if it was found, otherwise null
   */
  public String getXMLContent(String elementName)
  {
    int i;
    String[] PathName;
    Element foundXML = null;
    Element propsXML;
    boolean found = true;
    boolean foundPath = true;
    String tmpElemName;

    // copy the internal XML object into the working XML object
    propsXML = propXMLObject;
    
    tmpElemName = ROOT_ELEMENT+"." + elementName;
    PathName = tmpElemName.split("\\.");

    // Search through the tree
    for ( i = 0 ; i < PathName.length ; i++)
    {
      if (propsXML != null)
      {
        if (i==0)
        {
          // check the root path
          if (propsXML.getNodeName().equals(PathName[i]))
          {
            found = true;
            foundXML = propsXML;
          }
        }
        else
        {
          NodeList Children = propsXML.getElementsByTagName(PathName[i]);

          if (Children.getLength() > 0)
          {
            boolean childFound = false;
            for (int idx1 = 0 ; idx1 < propsXML.getChildNodes().getLength() ; idx1++)
            {
              Node myNode = propsXML.getChildNodes().item(idx1);
              if ((myNode.getNodeType() == Node.ELEMENT_NODE) && (myNode.getNodeName().equals(PathName[i])))
              {
                foundXML = (Element) myNode;
                childFound = true;
              }
            }
            if (childFound == false)
            {
              found = false;
            }
          }
          else
          {
            // we did not find the path
            found = false;
          }
        }
      }

      if (found)
      {
        propsXML = foundXML;
      }
      else
      {
        foundPath = false;
      }
    }

    // We should have the right element now
    if (foundPath && (propsXML != null))
    {
      return propsXML.getTextContent().trim();
    } else
    {
      // Did not find the value, return null
      return null;
    }
  }

  /**
   * Get a list of elements from the configuration properties, starting at the
   * location defined by the search key
   *
   * @param searchKey
   * @return The list of the pipelines in this configuration
   */
  public ArrayList<String> getGenericNameList(String searchKey)
  {
    ArrayList<String> tmpGenericList;
    tmpGenericList = new ArrayList<>();

    ArrayList<Element> valuesFound;
    valuesFound = getXMLChildren(searchKey);

    if (valuesFound != null)
    {
      Iterator<Element> childIterator = valuesFound.iterator();
      while (childIterator.hasNext())
      {
        Element tmpElement = childIterator.next();
        tmpGenericList.add(tmpElement.getTagName());
      }
    }

    return tmpGenericList;
  }

  /**
   * Returns the names of all of the child elements pointed to by "elementName".
   * This is only for the direct children of that element, and is not recursive.
   *
   * Element name should be referenced by a path separated by "." separators, for
   * example the elementName "Input.BatchSize" will point the the "BatchSize"
   * child element of the "Input" element.
   *
   *
   * @param elementName the path to the content to retrieve
   * @return ArrayList the names of the child elements
   */
  public ArrayList<Element> getXMLChildren(String elementName) {
    int idxo;
    String[] PathName;
    Element foundXML = null;
    Element propsXML;
    boolean found = true;
    boolean foundPath = true;
    String tmpElemName;

    // copy the internal XML object into the working XML object
    propsXML = propXMLObject;

    tmpElemName = ROOT_ELEMENT+"." + elementName;
    PathName = tmpElemName.split("\\.");

    // Search through the tree - because we are getting a list, we need to stop earlier
    for ( idxo = 0 ; idxo < PathName.length ; idxo++)
    {
      if (propsXML != null)
      {
        if (idxo==0)
        {
          // check the root path
          if (propsXML.getNodeName().equals(PathName[idxo]))
          {
            foundXML = propsXML;
          }
        }
        else
        {
          NodeList Children = propsXML.getElementsByTagName(PathName[idxo]);

          if (Children.getLength() > 0)
          {
            boolean childFound = false;
            for (int idx1 = 0 ; idx1 < propsXML.getChildNodes().getLength() ; idx1++)
            {
              Node myNode = propsXML.getChildNodes().item(idx1);
              if ((myNode.getNodeType() == Node.ELEMENT_NODE) && (myNode.getNodeName().equals(PathName[idxo])))
              {
                foundXML = (Element) myNode;
                childFound = true;
              }
            }
            if (childFound == false)
            {
              found = false;
            }
          }
          else
          {
            found = false;
          }
        }
      }

      if (found)
      {
        propsXML = foundXML;
      }
      else
      {
        foundPath = false;
      }
    }

    // We should have the right element now
    if (foundPath && (propsXML != null))
    {
      // Create the arraylist out of the node list
      ArrayList<Element> resultList = new ArrayList<>();
      for (int idx = 0 ; idx < propsXML.getChildNodes().getLength() ; idx++)
      {
        Node myNode = propsXML.getChildNodes().item(idx);
        if (myNode.getNodeType() == Node.ELEMENT_NODE)
        {
          resultList.add((Element)myNode);
        }
      }

      // Return it
      return resultList;
    } else
    {
      // Did not find the value, return null
      return null;
    }
  }

  /**
   * This utility function returns the value specified from the group specified,
   * meaning that we will look for the prefix, a "." and then the value provided
   * and return the value of that.
   *
   * @param     groupPrefix the group prefix to search for
   * @param     propertyName the property suffix to search for
   * @return    the value string that we are searching for if found, otherwise
   *            null
   */
  public String getGroupPropertyValue(String     groupPrefix,
                                      String     propertyName)
  {
      String         searchKey;
      String         ValueFound;

      searchKey = groupPrefix+"."+propertyName;
      ValueFound = this.getPropertyValue(searchKey);

      return ValueFound;
  }

  /**
   * This utility function returns the value specified from the group specified,
   * meaning that we will look for the prefix, a "." and then the value provided
   * and return the value of that.
   *
   * @param     groupPrefix the group prefix to earch for
   * @param     propertyName the property suffix to search for
   * @param     defaultValue the value that should be returned in the case that
   *            no match is found
   * @return    the value string that we are searching for if found, otherwise
   *            defaultValue
   */
  public String getGroupPropertyValueDef(String     groupPrefix,
                                         String     propertyName,
                                         String     defaultValue) {
      String valueFound;

      valueFound = this.getGroupPropertyValue(groupPrefix,propertyName);

      if (valueFound == null) {
          valueFound = defaultValue;
      }

      return valueFound;
  }


  /**
   * This utility function returns the value specified from the property
   * specified and returns the value
   * @param      propertyName the property value to search for
   * @return    the value string that we are searching for if found, otherwise
   *            null
   */
  public String getPropertyValue(String propertyName) {
      String valueFound;

      valueFound = getXMLContent(propertyName);
      return valueFound;
  }

  /**
   * This utility function returns the value specified from the property
   * specified and returns the value
   * @param     propertyName the property value to search for
   * @param     defaultValue the value that should be returned in the case that
   *            no match is found
   * @return    the value string that we are searching for if found, otherwise
   *            defaultValue
   */
  public String getPropertyValueDef(String propertyName,
          String defaultValue) {
      String valueFound;

      valueFound = this.getPropertyValue(propertyName);

      if (valueFound == null) {
          valueFound = defaultValue;
      }

      return valueFound;
  }

 /**
  * Get the fully qualified name for the properties file
  *
  * @param propFileName The properties file name
  * @return The fully qualified path to the properties file
  */
  public String createFQConfigFileName(String propFileName)
  {
    String ConfigurationPath = System.getProperty("user.dir");
    String FQConfigFileName = ConfigurationPath + "/"+PROPERTY_FOLDER+"/" + propFileName;

    return FQConfigFileName;
  }

  /**
   * Get an XML element
   *
   * @param elementName The element name to retrieve
   * @return The XML element
   */
  public Element getXMLElement(String elementName)
  {
    int i;
    String[] PathName;
    Element foundXML = null;
    Element propsXML;
    boolean found = false;
    boolean foundPath = true;
    String  tmpElemName;

    // copy the internal XML object into the working XML object
    propsXML = propXMLObject;

    tmpElemName = ROOT_ELEMENT+"." + elementName;
    PathName = tmpElemName.split("\\.");

    // Search through the tree
    for ( i = 0 ; i < PathName.length ; i++)
    {
      if (propsXML != null)
      {
        if (i==0)
        {
          // check the root path
          if (propsXML.getNodeName().equals(PathName[i]))
          {
            found = true;
            foundXML = propsXML;
          }
        }
        else
        {
          NodeList Children = propsXML.getElementsByTagName(PathName[i]);

          if (Children.getLength() > 0)
          {
            foundXML = (Element) Children.item(0);
            found = true;
          }
        }
      }

      if (found)
      {
        propsXML = foundXML;
      }
      else
      {
        foundPath = false;
      }
    }

    // We should have the right element now
    if (foundPath)
    {
      return propsXML;
    } else
    {
      // Did not find the value, return null
      return null;
    }
  }

    /**
     * Return the symbolic name of this module.
     * 
     * @return the symbolicName
     */
    public String getSymbolicName() {
        return symbolicName;
    }
}