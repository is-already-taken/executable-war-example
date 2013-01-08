package net.noorg.test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/* Used custom bootloader code from:
 *	http://www.javablogging.com/java-classloader-2-write-your-own-classloader/
 * 
 * Modified: getClass() (now "getLocalClass()"), loadClass(), loadClassData()
 * 
 * 
 */
class Bootstrap extends ClassLoader {

	private final static String CLASSES_DIR = "WEB-INF/classes/";
	private final static String LIB_DIR = "WEB-INF/lib/";
	
	// This will contain the .jars listed in the manifest's X-Jars entry.
	private static String[] mainfestJars;
	
	
    /**
     * Parent ClassLoader passed to this constructor
     * will be used if this ClassLoader can not resolve a particular class.
     *
     * @param parent Parent ClassLoader (may be from getClass().getClassLoader())
     */
	public Bootstrap(ClassLoader parent) {
		super(parent);
	}

	
    /**
     * Loads a given class from .class file just like
     * the default ClassLoader. This method could be
     * changed to load the class over network from some
     * other server or from the database.
     *
     * @param name Full class name
     */
	private Class<?> getLocalClass(String name) throws ClassNotFoundException {
		String file = name.replace('.', File.separatorChar) + ".class";

		// prepend .war's folder structure
		file = CLASSES_DIR + file;

		byte[] b = null;
		try {
			System.out.println("Loading class '" + file + "'");
			b = loadClassData(file);
			Class<?> c = defineClass(name, b, 0, b.length);
			resolveClass(c);
			return c;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
    /**
     * Every request for a class passes through this method.
     * It attempts to load the passed class name in this order:
     * <ul>
     * <li>try loading the class through the parent class loader</li>
     * <li>if not found, look for it in CLASSES_DIR and try loading it</li>
     * <li>if not found too, look for JARs in LIB_DIR and try loading a class from that JAR</li>
     * <li>if the class is still not found, throw an ClassNotFoundException</li>
     * </ul> 
     * 
     * @param name Full class name
     * @throws ClassNotFoundException if the class was not found through parent class loader, locally and in JARs
     * @return The class
     */
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		System.out.println("Loading class '" + name + "'");

		try {
			System.out.println("Trying parent class loader ...");
			return super.loadClass(name);
			
		} catch (ClassNotFoundException e) {
			System.out.println("The default class loader did not found '" + name + "', trying custom class loader ...");
			
			try {
				// try to get class from this .jar file
				return getLocalClass(name);
			
			} catch (ClassNotFoundException cnfe) {
				System.out.println("Our class loader did not found '" + name + "' as .class, looking for classes in .jar files ...");
				
				// Our class loading method has not found the class.
				// Try to extract class file from a .jar listed within our manifest.  
				
				for (String jar : mainfestJars) {
					try {
						byte[] clsData = extractClassData(LIB_DIR + jar, name);
						
						Class<?> c = defineClass(name, clsData, 0, clsData.length);
						resolveClass(c);
						return c;
						
					} catch (IOException ioe) {
						throw new RuntimeException("Error unzipping class from '"+ jar +"'", ioe);
					}
				}
			}
		}
		
		return null;
	}

	
    /**
     * Loads a given file (presumably .class) into a byte array.
     * The file should be accessible as a resource, for example
     * it could be located on the classpath.
     *
     * @param name File name to load
     * @return Byte array read from the file
     * @throws IOException Is thrown when there was some problem reading the file
     */
	private byte[] loadClassData(String name) throws IOException, ClassNotFoundException {
		InputStream stream = Bootstrap.class.getClassLoader().getResourceAsStream(name);
		
		if (stream == null) {
			throw new ClassNotFoundException("Class not found");
		}
		
		int size = stream.available();
		byte buff[] = new byte[size];
		DataInputStream in = new DataInputStream(stream);

		in.readFully(buff);
		in.close();
		return buff;
	}
	
	/** Load class file as byte array from .jar
	 * 
	 * @param jarName complete path to the .jar resource within this .war
	 * @param className class name in package-notation
	 * @return class data
	 * @throws IOException on errors during unzip
	 */
	static public byte[] extractClassData(String jarName, String className) throws
			IOException {
		System.out.println("  Attempting to extract '" + className + "' from '"+ jarName + "'");
		
		InputStream in = Bootstrap.class.getClassLoader().getResourceAsStream(jarName);
		ZipInputStream zip = new ZipInputStream(in);
		ZipEntry entry;
		String name;
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		// while there are entries I process them
		while ((entry = zip.getNextEntry()) != null) {
			if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
				// ignoring non-files and non-classes
				continue;
			}
			
			name = entry.getName();
			
			// remove .class part, make path out of package-notation
			name = name.substring(0, name.length() - 6).replaceAll("/", ".");
			
			if (!name.equals(className)) {
				// this is not the searched class file, skip
				continue;
			}

			System.out.print("    Found class: " + name + " ");
			
			while (zip.available() > 0) {
				int data = zip.read();
				
				if (data == -1) {
					return baos.toByteArray();
				}
				
				baos.write((byte) data);
			}
			
			System.out.println("(" + baos.size() + ")");
			
			zip.closeEntry();
		}
		
		return null;
	}
	
	/** Load MANIFEST.MF from standard location in .jar and read "X-Jars" entry.
	 */
	private static void loadManifest(){
		InputStream is = Bootstrap.class.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF");
		String jarsStr = null;
		
		try {
			Manifest manifest = new Manifest(is);
			jarsStr = manifest.getMainAttributes().getValue("X-Jars");
		} catch (IOException e) {
			throw new RuntimeException("Error reading JAR list from Manifest: ", e);
		}
		
		mainfestJars = jarsStr.split(" ");
	}

	public static void main(String[] args) throws ClassNotFoundException,
			InstantiationException, IllegalAccessException,
			IllegalArgumentException, SecurityException,
			InvocationTargetException, NoSuchMethodException, ZipException, IOException {

		loadManifest();

		System.out.println(":)");
		
		// Actually instantiate our classloader, passing the default 
		// class loader as parent
		Bootstrap loader = new Bootstrap(Bootstrap.class.getClassLoader());

		// Loading our first business class through our own class loader
		Class<?> clazz = loader.loadClass("net.noorg.test.Business");
		Object instance = clazz.newInstance();
		clazz.getMethod("doSomething").invoke(instance);
		
	}

}
