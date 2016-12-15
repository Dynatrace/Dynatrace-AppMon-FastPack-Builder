package com.gardner.fastpackbuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.gardner.fastpackbuilder.Constants.IConstants;

// Official Documentation: https://community.dynatrace.com/community/display/DOCDT99/Building+FastPacks

public class FastPackBuilder
{

    
    private File m_oInputDir;
    private File m_oWebDashboardsDir;
    private static String FASTPACK_NAME = ""; // Fastpack name
    private static String FASTPACK_VERSION = ""; // Fastpack version
    
    
    List<File> filesListInDir = new ArrayList<File>();
    List<File> m_oDirList = new ArrayList<File>();
    
    
    /*
     * List to hold exclusions. These are used to exclude from the plugin.xml file
     * Currently, exclusions are: 
     *    MANIFEST.MF
     *    plugin.xml
     *    META-INF (folder)
     */
    List<String> m_oExclusions = new ArrayList<String>();
    
    private DocumentBuilderFactory DOC_FACTORY = DocumentBuilderFactory.newInstance();
    private DocumentBuilder DOC_BUILDER;
    
    public FastPackBuilder(File oInputDir)
    {
        m_oInputDir = oInputDir;

        // Do not add these files to the plugin.xml <resource tag.
        m_oExclusions = new ArrayList<String>();
        m_oExclusions.add("MANIFEST.MF");
        m_oExclusions.add("plugin.xml");
        m_oExclusions.add("META-INF");
        m_oExclusions.add("webdashboards");
        try
        {
            DOC_BUILDER = DOC_FACTORY.newDocumentBuilder();
        }
        catch (Exception e)
        {
            System.out.println("Exception creating doc builder");
        }
    }
    
    public boolean processFastpack(String strFastpackName, String strFastpackVersion)
    {
        FASTPACK_NAME = strFastpackName;
        FASTPACK_VERSION = strFastpackVersion;
        
        // Step 1: Build Manifest
        Manifest oManifest = buildManifest();
        // Step 2: Read Dashboards
        try
        {
            populateFilesList(m_oInputDir);
        }
        catch (Exception e)
        {
            System.out.println("Exception caught populating files list");
        }
        
        // Step 4: Build Plugin XML File
        StringWriter oXMLContent = buildPluginXMLFile();
        
        // Step 5: Build and save JAR
        return zipDirectory(oManifest,m_oInputDir, oXMLContent);
    }
    
    private Manifest buildManifest()
    {
        // Fastpack name with spaces removed & lowercase
        String strBundleName = FASTPACK_NAME.replaceAll("\\s+","").toLowerCase();

        String strBundleVersion = FASTPACK_VERSION;
        String strImplementationVersion = FASTPACK_VERSION;
        
        Manifest oManifest = new Manifest();
        oManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0"); //Manifest-Version: 1.0
        oManifest.getMainAttributes().putValue("Bundle-ManifestVersion", "2"); //Bundle-ManifestVersion: 2
        oManifest.getMainAttributes().putValue("Bundle-Name", strBundleName); //Bundle-Name: Demopatch
        oManifest.getMainAttributes().putValue("Bundle-Version", strBundleVersion); //Bundle-Version: 4.1.0.2599
        oManifest.getMainAttributes().putValue("Bundle-Vendor", "dynaTrace software GmbH"); //Bundle-Vendor: dynaTrace software GmbH
        oManifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, strImplementationVersion); //Implementation-Version: 4.5.0.2599
        oManifest.getMainAttributes().putValue("Bundle-ManifestVersion", "2"); //Bundle-ManifestVersion: 2
        oManifest.getMainAttributes().putValue("Require-Bundle", "com.dynatrace.diagnostics.sdk"); //Require-Bundle: com.dynatrace.diagnostics.sdk
        oManifest.getMainAttributes().putValue("Bundle-SymbolicName", "com.dynatrace.diagnostics."+strBundleName+";singleton:=true"); //Bundle-SymbolicName: com.dynatrace.diagnostics.demopatch;singleton:=true

        return oManifest;
    }
        
    private StringWriter buildPluginXMLFile()
    {
        Document oPluginXMLDoc = DOC_BUILDER.newDocument();
        StringWriter oWriter = new StringWriter();
        
        // plugin
        Element rootElement = oPluginXMLDoc.createElement("plugin");
        oPluginXMLDoc.appendChild(rootElement);

        // extension
        Element extension = oPluginXMLDoc.createElement("extension");
        extension.setAttribute("id", "Installer Content");
        extension.setAttribute("point", "com.dynatrace.diagnostics.InstallerContent");
        rootElement.appendChild(extension);

        // metainfo
        Element metainfo = oPluginXMLDoc.createElement("metainfo");
        metainfo.setAttribute("installer_type", "resourcepack");
        metainfo.setAttribute("name", FASTPACK_NAME);
        extension.appendChild(metainfo);

        // Process web dashboards
        if (hasWebDashboards()) processWebDashboards(metainfo,oPluginXMLDoc);
        
        //resources
        for (File oTmpFile : filesListInDir)
        {
            // Ignore any excluded files
            if (m_oExclusions.contains(oTmpFile.getName())) continue;
            
            Element resource = oPluginXMLDoc.createElement(IConstants.RESOURCE);
            resource.setAttribute(IConstants.RESOURCE, oTmpFile.getName());
            
            /* Set resource type accordingly
             * dashboards:                      resource_type="dashboard"
             * profiles:                        resource_type="systemProfile"
             * user plugin (directory or .jar)  resource_type="userPlugin" 
             * license file:                    resource_type="licenseFile" 
             * sensor packs:                    resource_type="sensorPack"
             * TODO v2. arbitrary files
             */
            if (oTmpFile.getName().endsWith(".dashboard.xml")) resource.setAttribute(IConstants.RESOURCE_TYPE,"dashboard");
            else if (oTmpFile.getName().endsWith(".profile.xml")) resource.setAttribute(IConstants.RESOURCE_TYPE,"systemProfile");
            else if (oTmpFile.getName().endsWith(".jar")) resource.setAttribute(IConstants.RESOURCE_TYPE,"userPlugin");
            else if (oTmpFile.getName().endsWith(".key")) resource.setAttribute(IConstants.RESOURCE_TYPE,"licenseFile");
            else if (oTmpFile.isDirectory()) resource.setAttribute(IConstants.RESOURCE_TYPE,"sensorPack");
            else if (oTmpFile.getName().endsWith(".dts")) resource.setAttribute(IConstants.RESOURCE_TYPE,"session"); // This is not in the official documentation: https://community.dynatrace.com/community/display/DOCDT99/Building+FastPacks
            //TODO - Additional items (session files, arbitrary files). 
            
            metainfo.appendChild(resource);
        }
        
        for (File oDir : m_oDirList)
        {
            // Ignore any excluded directories
            if (m_oExclusions.contains(oDir.getName())) continue;
            
            Element resource = oPluginXMLDoc.createElement(IConstants.RESOURCE); // TODO - Move to constant.
            resource.setAttribute(IConstants.RESOURCE, oDir.getName()); // TODO - Move to constant.
            resource.setAttribute(IConstants.RESOURCE_TYPE,"sensorPack");
            metainfo.appendChild(resource);
        }

        // write the content into xml file
        try
        {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(oPluginXMLDoc);

            // Write results to a string
            StreamResult oResult = new StreamResult(oWriter);
            transformer.transform(source,oResult);

        }
        catch (Exception e)
        {
            System.out.println("Exception caught transforming XML result.");
            e.printStackTrace();
        }
        return oWriter;
    }
        
    /**
     * This method zips the directory
     * @param dir
     * @param zipDirName
     */
    private boolean zipDirectory(Manifest oManifest, File oInputDir, StringWriter oXMLContent)
    {
        try {
            populateFilesList(oInputDir);
            
            //now zip files one by one
            //create JarOutputStream to write to the zip file
            FileOutputStream fos = new FileOutputStream(new File(oInputDir.getAbsolutePath()+"/"+FASTPACK_NAME+"_"+FASTPACK_VERSION+".jar"));
            JarOutputStream zos = new JarOutputStream(fos, oManifest);
            
            // Add plugin XML file
            JarEntry oXMLEntry = new JarEntry("plugin.xml");
            oXMLEntry.setTime(System.currentTimeMillis());
            // Put the file into the ZIP: Note - it has no actual content yet.
            zos.putNextEntry(oXMLEntry);
            
            // Write the true plugin.xml content.
            zos.write(oXMLContent.toString().getBytes("UTF-8"));
            
            for(File oFile : filesListInDir){
                String strFilePath = oFile.getAbsolutePath();
                //for ZipEntry we need to keep only relative file path, so we used substring on absolute path
                ZipEntry ze = new ZipEntry(strFilePath.substring(oInputDir.getAbsolutePath().length()+1, strFilePath.length()));
                zos.putNextEntry(ze);
                //read the file and write to JarOutputStream
                FileInputStream fis = new FileInputStream(strFilePath);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
                fis.close();
            }
            zos.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
     
    /**
     * This method populates all the files in a directory to a List
     * @param dir
     * @throws IOException
     */
    private void populateFilesList(File dir) throws IOException
    {
        File[] files = dir.listFiles();
        if (files == null) return; // Got to the bottom of a directory. No more files.
        for(File file : files)
        {
            // Check that file is indeed a file & that we aren't adding the same file twice.
            if (file.isFile() && !filesListInDir.contains(file)) filesListInDir.add(file);
            else
            {
                m_oDirList.add(file);
                
                // Recursively traverse down directory structure.
                populateFilesList(file);
            }
        }
    }
    
    private boolean hasWebDashboards()
    {
    	File[] files = m_oInputDir.listFiles();
    	if (files == null) return false;
    	
    	for (File file : files) if (file.isDirectory() && file.getName().equalsIgnoreCase(IConstants.WEB_DASHBOARDS)) return true;
    	return false;
    }
    
    private Element processWebDashboards(Element metainfo, Document oPluginXMLDoc)
    {
    	// Get the web dashboards directory from input dir.
    	File[] files = m_oInputDir.listFiles();
    	File oWebDashboardDir = null;
    	if (files == null) return metainfo;
    	
    	for (File file : files) if (file.isDirectory() && file.getName().equalsIgnoreCase(IConstants.WEB_DASHBOARDS)) oWebDashboardDir = file;
    	
    	if (oWebDashboardDir == null) return metainfo;
    	
    	/* 
    	 * For each file in IConstants.WEB_DASHBOARDS
    	 * Build the relevant XML and attach to the metainfo then return the metainfo object.
    	 */
    	File[] oWebDashboardFiles = oWebDashboardDir.listFiles();
        for (File oTmpFile : oWebDashboardFiles)
        {
            // Ignore any excluded files
            if (m_oExclusions.contains(oTmpFile.getName())) continue;
            
            Element resource = oPluginXMLDoc.createElement(IConstants.RESOURCE);
            resource.setAttribute(IConstants.RESOURCE, oTmpFile.getName());
            
            /* 
             * <resource resource="dashboards/web/7b8ed814-49c7-4e62-9f08-d4b57c2ff0dd.xml" resource_type="resource" target_dir="conf/dashboards/web">
			 *		<instance instance="server"/>
			 * </resource>
			 * <resource resource="dashboards/web/7b8ed814-49c7-4e62-9f08-d4b57c2ff0dd.json" resource_type="resource" target_dir="conf/dashboards/web">
			 *		<instance instance="server"/>
			 * </resource>
             */
            resource.setAttribute(IConstants.RESOURCE,"webdashboards/"+oTmpFile.getName()); //<resource resource="dashboards/web/543dscs.json"....
            resource.setAttribute(IConstants.RESOURCE_TYPE, IConstants.RESOURCE); // ... resource_type="resource"....
            resource.setAttribute("target_dir", "conf/dashboards/web"); // ..... target_dir="conf/dahsboards/web"
            	Element oInstanceElem = oPluginXMLDoc.createElement("instance"); // <instance instance="server" />
            	oInstanceElem.setAttribute("instance", "server");
            	resource.appendChild(oInstanceElem);
            
            metainfo.appendChild(resource);
            
            // We've already written relevant plugin.xml web dashboard stuff
            // Add to exclusion list to prevent anything else adding a duplicate.
            m_oExclusions.add(oTmpFile.getName());
        }
        
        return metainfo;
    }
}
