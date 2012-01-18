#!/usr/bin/env groovy

/**
 * This script collects the number of files, sub-directories and the total size of a given directory.
 *
 * Example usage (pass -h switch to print the full usage):
 *
 * <pre>
 *     $ dirstats -p /Users/molk/Documents/
 *     Travelling path /Users/molk/Documents  ...
 *     68,1 GB in 411443 files and 101021 directories (73097444398 bytes)
 * </pre>
 *
 * Note:
 * Making this script executable in Unix filesystems (<code>chmod +x dirstats.groovy</code>)
 * should allow you to put it on the PATH and call it without having to start the groovy interpreter
 * explicitly
 *
 * @author Marcus Olk
 */

new ArgsInterpreter(args).with {

	showUsageAndExitIfRequested()

	path = argPath

	if (!path.exists()) {
		println "Not found: $path"
		return
	}

	excludes = argExcludes
	filter	 = excludes ? { file -> excludes.any { file.name.equalsIgnoreCase it } } : null

	println "Travelling path $path.absolutePath ${excludes ? '(excludes: ' + excludes.join(', ')  + ')' : ''} ..."

	int numberOfFiles, numberOfDirs; long fileSizes

	eachFileRecurse(path, filter) { File entry ->
		if (entry.isDirectory())
			numberOfDirs++
		else {
			numberOfFiles++
			fileSizes += entry.size()
		}
	}

	println "${bytesAsString(fileSizes)} in ${numberAsString(numberOfFiles)} files and ${numberAsString(numberOfDirs)} directories (${numberAsString(fileSizes)} bytes)"
}

//~ ugly helpers ------------------------------------------------------------------------------

class ArgsInterpreter {

	final standardExcludes = '.svn, .DS_Store'

	final cli  = new CliBuilder(usage: 'dirstat')
	def   opts

	ArgsInterpreter(args) {
		cli.with {
			h ( longOpt: 'help'			     , required: false, 'show usage information'																			 )
			p ( longOpt: 'path'	    	     , required: false, argName: 'path'					, args: 1, 'the path to create the stats for'						 )
			x ( longOpt: 'excludes'		     , required: false, argName: 'exclude-list'			, args: 1, 'list of files/directories to exclude eg. \'.svn, .git\'' )
			s ( longOpt: 'standard-excludes' , required: false, "use standard excludes: $standardExcludes"																 )
		}
		
		opts = cli.parse(args)
	}

	def showUsageAndExitIfRequested() {
		if (opts.h) {
			cli.usage()
			System.exit 0
		}
	}

	def getArgPath() 	 { new File(opts.p ?: '.') }
	def getArgExcludes() { (opts.x || opts.s) ? asList(opts.x ?: standardExcludes) : null }

	private asList(String csvString) { csvString.split(',').collect { it?.trim() } }
}

def numberAsString(n) { String.format ('%,d', n) }

def bytesAsString(final long bytes) {
	units = ['bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']

	for (i in 6..0) {
		double step = Math.pow(1024, i)
		if (bytes > step) return String.format('%3.1f %s', bytes / step, units[i])
	}

	return Long.toString(bytes)
}


def eachFileRecurse(File dir, Closure closure) {
	eachFileRecurse(dir, null, closure)
}

def eachFileRecurse(File dir, Closure filter, Closure closure) {
	if (dir == null) return
	if (!dir.exists()) throw new FileNotFoundException(dir.absolute)

	if (!dir.isDirectory()) {
		closure?.call(dir)
	}
	else {
		for (file in dir.listFiles()) {
			if (!filter?.call(file)) {
				closure?.call(file)

				if (file.isDirectory()) eachFileRecurse(file, filter, closure)
			}
		}
	}
}
