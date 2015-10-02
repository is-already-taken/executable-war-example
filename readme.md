# Demonstration of executable WAR file

This an demonstration of an executable WAR file.
It uses a custom class loader to load classes from an alternative path or JAR within the web archive (WAR).

# What is this useful for?

Think of a web application that should have a command line interface too.
Of course you would create three modules and move the business logic to one module and the web application and CLI to other modules which depend on the first. Each of them would spit out a .war and executable .jar respectively. But deploying these two files might be a bit annoying, so you could create one file that does both.

# How does it work?

Making an WAR executable involves three things:

* Make the WAR look like a executable JAR
* Loading classes from an alternative location
* Knowing the contained JARs (libraries)

How this is done, is described below.

## Making the WAR executable

This is done though a trick. There is a `MANIFEST.MF` at a location typical for JAR files.

It contains the `Main-Class` entry to denote the class that contains a `main()` method. Further is there a `Bootstrap` class that's responsible for loading classes from an alternative location.
 
The `maven-war-plugin` is instructed to

* add the custom `MANIFEST.MF` to the web archive
* add the `Bootstrap` class directly into the archive's root

All other classes are then loaded from their non-standard-JAR location through a custom class loader...

## Intercepting the class loader

The custom class loader code is based on code that I found in an article about custom class loaders on [javablogging.com](http://web.archive.org/web/20130529111208/http://www.javablogging.com/java-classloader-2-write-your-own-classloader). Their code is used to load a class from a special package with their class loader, which is indicated by console output.  

The class loader code now works slightly different. It loades classes this way: 

1. try loading classes though the parent class loader 
2. if not found, try looking for the class in the WAR's `WEB-INF/classes` directory by prefixing the class path 
3. if not found, try looking for it in the WAR's `WEB-INF/lib` directory (this is described below)
4. if still not found, throw ClassNotFoundException 
 
## Loading classes from packaged (contained) JARs

The JAR files have to be declared somewhere. In this example they are declared through an Manifest entry.  

The class is loaded from an contained JAR this way:

1. get a list of JAR files contained in the WAR by examining the Manifest (described below)  
2. iterate over all contained JAR files and look for the class name
3. if found extract the class file
4. load the class data

## Knowing the contained JARs

Unfortunately it is not possible to enumerate all resources with a JAR, so the JAR files have to be declared explicitly. In this example this is done though a custom MANIFEST.MF. The Manifest is read and the list of JARs (`X-Jars` entry) is interally stored.

# Requirements

* Maven 3 to build this project
* Oracle/Sun JDK 1.6+ (this code does not work with OpenJDK 1.6 unfortuately)
* An application server to test the WAR (optional as this example is focussed on the executability of the WAR) 

# Limitations

This is only an example, a proof of concept. It has limitations which however can be eliminated easily.

* The contained JARs are listed manually. However you could create the `MANIFEST.MF` automatically using ANT or a custom Maven plugin or Groovy script. 
* Threadsafety might be an issue

