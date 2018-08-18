package org.eclipse.titan.designer.compiler;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.titan.designer.GeneralConstants;
import org.eclipse.titan.designer.AST.MarkerHandler;
import org.eclipse.titan.designer.AST.Module;

/**
 * This is project level root of all java compiler related activities.
 * @author Arpad Lovassy
 */
public class ProjectSourceCompiler {

	/** the root package of the generated java source */
	private final static String PACKAGE_GENERATED_ROOT = "org.eclipse.titan.generated";
	private final static String PACKAGE_RUNTIME_ROOT = "org.eclipse.titan.runtime.core";
//	private final static String PACKAGE_RUNTIME_TYPES = PACKAGE_RUNTIME_ROOT + ".types";

	/** the root folder of the generated java source */
	private final static String DIR_GENERATED_ROOT = "java_src/org/eclipse/titan/generated";

	/**
	 * Generates java code for a module
	 * @param timestamp the timestamp of this build
	 * @param aModule module to compile
	 * @param aDebug true: debug info is added to the source code 
	 * @throws CoreException
	 */
	public static void compile(final BuildTimestamp timestamp, final Module aModule, final boolean aDebug ) throws CoreException {
		final IResource sourceFile = aModule.getLocation().getFile();
		if(MarkerHandler.hasMarker(GeneralConstants.ONTHEFLY_SYNTACTIC_MARKER, sourceFile, IMarker.SEVERITY_ERROR)
				|| MarkerHandler.hasMarker(GeneralConstants.ONTHEFLY_MIXED_MARKER, sourceFile)
				|| MarkerHandler.hasMarker(GeneralConstants.ONTHEFLY_SEMANTIC_MARKER, sourceFile, IMarker.SEVERITY_ERROR)) {
			// if there are syntactic errors in the module don't generate code for it
			// TODO semantic errors need to be checked for severity
			return;
		}

		final JavaGenData data = new JavaGenData(timestamp);
		data.collectProjectSettings(aModule.getLocation());
		data.setDebug( aDebug );
		aModule.generateCode( data );

		if (data.getAddSourceInfo() && (data.getPreInit().length() > 0 || data.getPostInit().length() > 0)) {
			data.addCommonLibraryImport("TTCN_Logger.TTCN_Location");
			data.addCommonLibraryImport("TTCN_Logger.TTCN_Location.entity_type_t");
		}
		if (data.getStartPTCFunction().length() > 0) {
			data.addBuiltinTypeImport("Text_Buf");
		}

		//write imports
		final StringBuilder headerSb = new StringBuilder();
		writeHeader( headerSb, data );

		data.getSrc().append(data.getGlobalVariables());
		for(final StringBuilder typeString: data.getTypes().values()) {
			data.getSrc().append(typeString);
		}

		writeFooter(data, sourceFile, aModule);


		//write src file body
		final IProject project  = aModule.getProject();
		final IFolder folder = project.getFolder( DIR_GENERATED_ROOT );
		final IFile file = folder.getFile( aModule.getName() + ".java");
		createDir( folder );

		//write to file if changed
		final String content = headerSb.append(data.getSrc().toString()).toString();
		if (file.exists()) {
			if(needsUpdate(file, content) ) {
				final InputStream outputStream = new ByteArrayInputStream( content.getBytes() );
				file.setContents( outputStream, IResource.FORCE | IResource.KEEP_HISTORY, null );
			}
		} else {
			final InputStream outputStream = new ByteArrayInputStream( content.getBytes() );
			file.create( outputStream, IResource.FORCE, null );
		}
	}

	/**
	 * Generates the class that will be the entry point for single mode execution.
	 *
	 * @param project the project in which the code is generated.
	 * @param modules the list of modules generated during this build.
	 *
	 * @throws CoreException if file operations can not be performed.
	 * */
	public static void generateSingleMain(final IProject project, final Collection<Module> modules) throws CoreException {
		final IFolder folder = project.getFolder( DIR_GENERATED_ROOT );
		final IFile file = folder.getFile( "Single_main.java");
		createDir( folder );

		final StringBuilder contentBuilder = new StringBuilder();

		contentBuilder.append( "// This Java file was generated by the TITAN Designer eclipse plug-in\n" );
		contentBuilder.append( "// of the TTCN-3 Test Executor version " ).append(GeneralConstants.VERSION_STRING).append('\n');
		contentBuilder.append( "// for (").append(System.getProperty("user.name")).append('@');
		try {
			contentBuilder.append(InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			contentBuilder.append("unknown");
		}
		//TODO date will need to be simplified to have optimize build time
//		aSb.append(") on ").append(new Date()).append('\n');
		contentBuilder.append(")\n");
		contentBuilder.append('\n' );
		contentBuilder.append( "// ").append(GeneralConstants.COPYRIGHT_STRING).append('\n' );
		contentBuilder.append('\n' );
		contentBuilder.append( "// Do not edit this file unless you know what you are doing.\n" );
		contentBuilder.append('\n' );
		contentBuilder.append( "package " );
		contentBuilder.append( PACKAGE_GENERATED_ROOT );
		contentBuilder.append( ";\n\n" );

		contentBuilder.append(MessageFormat.format("import {0}.Module_List;\n", PACKAGE_RUNTIME_ROOT));
		contentBuilder.append(MessageFormat.format("import {0}.Runtime_Single_main;\n", PACKAGE_RUNTIME_ROOT));

		for ( final Module module : modules ) {
			contentBuilder.append(MessageFormat.format("import {0}.{1};\n", PACKAGE_GENERATED_ROOT, module.getIdentifier().getName()));
		}

		contentBuilder.append( "public class Single_main {\n\n" );
		contentBuilder.append( "public static void main( String[] args ) {\n" );
		contentBuilder.append("long absoluteStart = System.nanoTime();\n");
		for ( final Module module : modules ) {
			contentBuilder.append(MessageFormat.format("Module_List.add_module(new {0}());\n",module.getIdentifier().getName()));
		}
		contentBuilder.append("int returnValue = Runtime_Single_main.singleMain();\n");
		contentBuilder.append("System.out.println(\"Total execution took \" + (System.nanoTime() - absoluteStart) * (1e-9) + \" seconds to complete\");\n");
		contentBuilder.append( "System.exit(returnValue);\n" );
		contentBuilder.append( "}\n" );
		contentBuilder.append( "}\n\n" );

		final String content = contentBuilder.toString();
		if (file.exists()) {
			if(needsUpdate(file, content.toString()) ) {
				final InputStream outputStream = new ByteArrayInputStream( content.getBytes() );
				file.setContents( outputStream, IResource.FORCE | IResource.KEEP_HISTORY, null );
			}
		} else {
			final InputStream outputStream = new ByteArrayInputStream( content.getBytes() );
			file.create( outputStream, IResource.FORCE, null );
		}
	}

	/**
	 * Generates the class that will be the entry point for parallel mode execution.
	 *
	 * @param project the project in which the code is generated.
	 * @param modules the list of modules generated during this build.
	 *
	 * @throws CoreException if file operations can not be performed.
	 * */
	public static void generateParallelMain(final IProject project, final Collection<Module> modules) throws CoreException {
		final IFolder folder = project.getFolder( DIR_GENERATED_ROOT );
		final IFile file = folder.getFile( "Parallel_main.java");
		createDir( folder );

		final StringBuilder contentBuilder = new StringBuilder();

		contentBuilder.append( "// This Java file was generated by the TITAN Designer eclipse plug-in\n" );
		contentBuilder.append( "// of the TTCN-3 Test Executor version " ).append(GeneralConstants.VERSION_STRING).append('\n');
		contentBuilder.append( "// for (").append(System.getProperty("user.name")).append('@');
		try {
			contentBuilder.append(InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			contentBuilder.append("unknown");
		}
		//TODO date will need to be simplified to have optimize build time
//		aSb.append(") on ").append(new Date()).append('\n');
		contentBuilder.append(")\n");
		contentBuilder.append('\n' );
		contentBuilder.append( "// ").append(GeneralConstants.COPYRIGHT_STRING).append('\n' );
		contentBuilder.append('\n' );
		contentBuilder.append( "// Do not edit this file unless you know what you are doing.\n" );
		contentBuilder.append('\n' );
		contentBuilder.append( "package " );
		contentBuilder.append( PACKAGE_GENERATED_ROOT );
		contentBuilder.append( ";\n\n" );

		contentBuilder.append(MessageFormat.format("import {0}.Module_List;\n", PACKAGE_RUNTIME_ROOT));
		contentBuilder.append(MessageFormat.format("import {0}.Runtime_Parallel_main;\n", PACKAGE_RUNTIME_ROOT));

		for ( final Module module : modules ) {
			contentBuilder.append(MessageFormat.format("import {0}.{1};\n", PACKAGE_GENERATED_ROOT, module.getIdentifier().getName()));
		}

		contentBuilder.append( "public class Parallel_main {\n\n" );
		contentBuilder.append( "public static void main( String[] args ) {\n" );
		contentBuilder.append("long absoluteStart = System.nanoTime();\n");
		for ( final Module module : modules ) {
			contentBuilder.append(MessageFormat.format("Module_List.add_module(new {0}());\n",module.getIdentifier().getName()));
		}
		contentBuilder.append("int returnValue = Runtime_Parallel_main.parallelMain(args);\n");
		contentBuilder.append("System.out.println(\"Total execution took \" + (System.nanoTime() - absoluteStart) * (1e-9) + \" seconds to complete\");\n");
		contentBuilder.append( "System.exit(returnValue);\n" );
		contentBuilder.append( "}\n" );
		contentBuilder.append( "}\n\n" );

		final String content = contentBuilder.toString();
		if (file.exists()) {
			if(needsUpdate(file, content.toString()) ) {
				final InputStream outputStream = new ByteArrayInputStream( content.getBytes() );
				file.setContents( outputStream, IResource.FORCE | IResource.KEEP_HISTORY, null );
			}
		} else {
			final InputStream outputStream = new ByteArrayInputStream( content.getBytes() );
			file.create( outputStream, IResource.FORCE, null );
		}
	}

	/**
	 * Compares the content of the file and the provided string content,
	 *  to determine if the file content needs to be updated or not.
	 * 
	 * @param file the file to check
	 * @param content the string to be generated if not already present in the file
	 * @return true if the file does not contain the provided string parameter
	 * */
	private static boolean needsUpdate(final IFile file, final String content) throws CoreException {
		boolean result = true;
		final InputStream filestream = file.getContents();
		final BufferedInputStream bufferedFile = new BufferedInputStream(filestream);
		final InputStream contentStream = new ByteArrayInputStream( content.getBytes() );
		final BufferedInputStream bufferedOutput = new BufferedInputStream(contentStream);
		try {
			int read1 = bufferedFile.read();
			int read2 = bufferedOutput.read();
			while (read1 != -1 && read1 == read2) {
				read1 = bufferedFile.read();
				read2 = bufferedOutput.read();
			}

			result = read1 != read2;
			bufferedFile.close();
			bufferedOutput.close();
		} catch (IOException exception) {
			return true;
		}

		return result;
	}


	/**
	 * RECURSIVE
	 * Creates full directory path
	 * @param aFolder directory to create
	 * @throws CoreException
	 */
	private static void createDir( final IFolder aFolder ) throws CoreException {
		if (!aFolder.exists()) {
			final IContainer parent = aFolder.getParent();
			if (parent instanceof IFolder) {
				createDir( (IFolder) parent );
			}
			aFolder.create( true, true, new NullProgressMonitor() );
		}
	}

	/**
	 * Builds header part of the java source file.
	 * <ul>
	 *   <li> header comment
	 *   <li> package
	 *   <li> includes
	 * </ul>
	 * @param aSb string buffer, where the result is written
	 * @param aData data collected during code generation, we need the include files form it
	 */
	private static void writeHeader( final StringBuilder aSb, final JavaGenData aData ) {
		aSb.append( "// This Java file was generated by the TITAN Designer eclipse plug-in\n" );
		aSb.append( "// of the TTCN-3 Test Executor version " ).append(GeneralConstants.VERSION_STRING).append('\n');
		aSb.append( "// for (").append(System.getProperty("user.name")).append('@');
		try {
			aSb.append(InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			aSb.append("unknown");
		}
		//TODO date will need to be simplified to have optimize build time
//		aSb.append(") on ").append(new Date()).append('\n');
		aSb.append(")\n");
		aSb.append('\n' );
		aSb.append( "// ").append(GeneralConstants.COPYRIGHT_STRING).append('\n' );
		aSb.append('\n' );
		aSb.append( "// Do not edit this file unless you know what you are doing.\n" );
		aSb.append('\n' );
		aSb.append( "package " );
		aSb.append( PACKAGE_GENERATED_ROOT );
		aSb.append( ";\n\n" );

		for ( final String importName : aData.getInternalImports() ) {
			aSb.append( "import " );
			aSb.append( PACKAGE_RUNTIME_ROOT );
			aSb.append( '.' );
			aSb.append( importName );
			aSb.append( ";\n" );
		}

		for (final String importName : aData.getInterModuleImports()) {
			aSb.append(MessageFormat.format("import {0}.{1}.*;\n", PACKAGE_GENERATED_ROOT, importName));
		}

		for ( final String importName : aData.getImports() ) {
			writeImport( aSb, importName );
		}
		aSb.append( '\n' );
	}

	/**
	 * Builds footer part of the java source file.
	 * <ul>
	 *   <li> pre init function: to initialize constants before module parameters are processed
	 *   <li> post init function: to initialize local "constants" after module parameters were processed.
	 * </ul>
	 * 
	 * @param aData data collected during code generation, we need the include files form it
	 * @param sourceFile the source of the code.
	 * @param aModule module to compile
	 */
	private static void writeFooter( final JavaGenData aData, final IResource sourceFile, final Module aModule) {
		final StringBuilder aSb = aData.getSrc();
		if (aData.getSetModuleParameters().length() > 0) {
			aSb.append("public boolean set_module_param(final Param_Types.Module_Parameter param)\n");
			aSb.append("{\n");
			aSb.append("final String par_name = param.get_id().get_current_name();\n");
			aSb.append(aData.getSetModuleParameters());
			aSb.append("{\n");
			aSb.append("return false;\n");
			aSb.append("}\n");
			aSb.append("}\n\n");
		}

		if (aData.getPreInit().length() > 0) {
			aSb.append("public void pre_init_module()\n");
			aSb.append("{\n");
			aSb.append("if (pre_init_called) {\n");
			aSb.append("return;\n");
			aSb.append("}\n");
			aSb.append("pre_init_called = true;\n");
			if (aData.getAddSourceInfo()) {
				aSb.append(MessageFormat.format("final TTCN_Location current_location = TTCN_Location.enter(\"{0}\", {1}, entity_type_t.LOCATION_UNKNOWN, \"{2}\");\n", sourceFile.getName(), 0, aModule.getIdentifier().getDisplayName()));
			}
			aSb.append(aData.getPreInit());
			if (aData.getAddSourceInfo()) {
				aSb.append("current_location.leave();\n");
			}
			aSb.append("}\n\n");
		}

		if (aData.getPostInit().length() > 0) {
			aSb.append("public void post_init_module()\n");
			aSb.append("{\n");
			aSb.append("if (post_init_called) {\n");
			aSb.append("return;\n");
			aSb.append("}\n");
			aSb.append("post_init_called = true;\n");
			aSb.append("TTCN_Logger.log_module_init(name, false);\n");
			if (aData.getAddSourceInfo()) {
				aSb.append(MessageFormat.format("final TTCN_Location current_location = TTCN_Location.enter(\"{0}\", {1}, entity_type_t.LOCATION_UNKNOWN, \"{2}\");\n", sourceFile.getName(), 0, aModule.getIdentifier().getDisplayName()));
			}
			aSb.append(aData.getPostInit());
			if (aData.getAddSourceInfo()) {
				aSb.append("current_location.leave();\n");
			}
			aSb.append("TTCN_Logger.log_module_init(name, true);\n");
			aSb.append("}\n\n");
		}

		if (aData.getStartPTCFunction().length() > 0) {
			aSb.append("@Override\n");
			aSb.append("public boolean start_ptc_function(final String function_name, final Text_Buf function_arguments) {\n");
			aSb.append(aData.getStartPTCFunction());
			aSb.append("throw new TtcnError(MessageFormat.format(\"Internal error: Startable function {0} does not exist in module {1}.\", function_name, name));\n");
			aSb.append("}\n\n");
		}

		if (aData.getInitComp().length() > 0) {
			aSb.append("public boolean init_comp_type(final String component_type, final boolean init_base_comps)\n");
			aSb.append("{\n");
			aSb.append(aData.getInitComp());
			aSb.append("{\n");
			aSb.append("return false;\n");
			aSb.append("}\n");
			aSb.append("}\n\n");
		}

		if (aData.getInitSystemPort().length() > 0) {
			aSb.append("public boolean init_system_port(final String component_type, final String port_name)\n");
			aSb.append("{\n");
			aSb.append(aData.getInitSystemPort());
			aSb.append("{\n");
			aSb.append("return false;\n");
			aSb.append("}\n");
			aSb.append("}\n\n");
		}

		aSb.append( "}\n" );
	}

	/**
	 * Writes an import to the header
	 * @param aSb string buffer, where the result is written
	 * @param aImportName short class name to import. This function knows the package of all the runtime classes.
	 */
	private static void writeImport( final StringBuilder aSb, final String aImportName ) {
		aSb.append( "import " );
		aSb.append( aImportName );
		aSb.append( ";\n" );
	}
}
