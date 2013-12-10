import static Globals.*
import static LogFileProcessor.commitsByAuthorInLogFile
import static Statistics.commitStatisticsFor

/**
 * Turns svn log XML output into some statistics.
 * (Yes, TortoiseSVN does this out of the box and much better ...)
 * 
 * Create a logfile using the 'svn log' command, e.g.
 *
 * <pre>
 *     svn log -v -r{2013-01-01}:head --xml > svn-log.xml
 * </pre>
 *
 * The goal is to turn this ...
 *
 * <pre>
 *   <log>
 *    <logentry revision="23419">
 *      <author>mrx</author>
 *      <date>2013-11-30T19:34:11.280185Z</date>
 *      <paths>
 *        <path action="A" kind="file">
 *          /foo/src/main/resources/foo/Bar.groovy
 *        </path>
 *      </paths>
 *      <msg>Very important foo</msg>
 *    </logentry>
 *  </log>
 * </pre>
 *
 * ... into this:
 *
 * <pre>
 * -----------------------------------------------------------------------------------------------------------
 *  mrx
 *    2013-01-01
 *      09:00 (rev 2342: Very important foo)
 *        A file /foo/src/main/resources/foo/Bar.groovy
 *
 *  -----------------------------------------------------------------------------------------------------------
 *
 *  Statistics:
 *
 *  Ordered by: Active Days
 *  -------------------------------------------------------------------------
 *  |     Author      |   Active Days   |  Total Commits  |  Commits / Day  |
 *  -------------------------------------------------------------------------
 *  |             foo |              10 |              34 |               3 |
 *  |             bar |               9 |             194 |              21 |
 * </pre>
 */

// path to svn log file
// created by e.g.: svn log -v -r{2013-01-01}:head --xml > svn-log.xml)
svnlogfile = '/Users/molk/projects/foo/svn-log.xml'

// ugly, but a script generates a class with a main function on the fly and that's why ...
class Globals {

    // set this to true for some noise
    // showing the reformatted XML contents
    static final doLog = false

    static log(text) { if (doLog) println text }

    static line = '-' * 100
    static PADDING = 15
    static pad = { (it as String).padLeft(PADDING) }

    static String toNumInUnits(long bytes) {
        int u = 0
        for (;bytes > 1024*1024; bytes >>= 10) u++
        if (bytes > 1024) u++
        String.format('%.1f %cB', bytes/1024f, ' KMGTPE'.charAt(u))
    }
}

println "Processing $svnlogfile ..."

commitStatistics = commitStatisticsFor commitsByAuthorInLogFile(svnlogfile)

commitStatistics.removeAll { it.author == 'luntbuild' }

println ''
println line
println 'Svn Commit Statistics'
println "Based on $svnlogfile (${toNumInUnits(new File(svnlogfile).size())})"
println line
println ''

tableLine   = ' ' + ('-' * (PADDING * 5 + 14))
columnNames = '| ' + ['Author', 'Active Days', 'Total Commits', 'Changes', 'Commits / Day'].collect { it.center(PADDING) } .join(' | ') + ' |'

sorting = [
    'Active Days'     : { it.daysWithCommits * -1 },
    'Commits per Day' : { it.commitsPerDay   * -1 },
    'Total Commits'   : { it.totalCommits    * -1 },
    'Changes'         : { it.changes         * -1 }
]

sorting.each { title, order ->
    println "Ordered by: $title"
    println tableLine
    println columnNames
    println tableLine
    commitStatistics.sort(order).each { println it }
    println tableLine + '\n'
}

// log file processing and statistics generation namespaces -------------------------------------------------------

class LogFileProcessor {

    static Map commitsByAuthorInLogFile(String svnlogfile) {
        final commitsByAuthor = [:]

        final logEntries = new XmlSlurper().parse(svnlogfile).logentry

        logEntries.each { logEntry ->

            String author = logEntry.author

            final entryForAuthor = commitsByAuthor[author]

            if (entryForAuthor == null) {
                entryForAuthor = [:]
                commitsByAuthor[author] = entryForAuthor
            }

            String   revision = logEntry.'@revision'
            String   message  = str(logEntry.msg).replaceAll('\\n', ' ')
            DateTime dateTime = DateTime.parseFrom(logEntry.date)

            final changes = logEntry.paths.path.collect { path ->
                String action = str(path.'@action') + ' ' + str(path.'@kind')
                String value  = str(path)
                "$action: $value"
            }

            final activityForDate = entryForAuthor[dateTime.date]

            if (activityForDate == null) {
                activityForDate = [:]
                entryForAuthor[dateTime.date] = activityForDate
            }

            activityForDate[dateTime.time] = [
                revision : revision,
                changes  : changes,
                message  : message ?: '*** COMMIT MESSAGE MISSING ***'
            ]
        }

        commitsByAuthor
    }

    static String str(node) {
        node as String
    }
}

class Statistics {

    String  author
    int     daysWithCommits
    int     totalCommits
    int     changes
    int     getCommitsPerDay() { (daysWithCommits ? (totalCommits / daysWithCommits) : 0 )  as Integer }

    @Override
    String toString() {
        '| ' + [author, daysWithCommits, totalCommits, changes, commitsPerDay].collect { pad it } .join(' | ') + ' |'
    }

    static commitStatisticsFor(Map commitsByAuthor) {
        final commitStatistics = []

        commitsByAuthor.each { author, entries ->

            log line
            log author
            log line

            final dates = entries.keySet().sort()

            final stats = new Statistics(author : author)

            stats.daysWithCommits = dates.size()

            dates.each { date ->
                final activitiesForDate = entries[date]
                final timesOfDay = activitiesForDate.keySet().sort()

                log "\t$date"

                timesOfDay.each { timeOfDay ->
                    final activity = activitiesForDate[timeOfDay]

                    log "\t\t$timeOfDay (rev $activity.revision: $activity.message)"

                    stats.totalCommits += 1
                    stats.changes += activity.changes.size()

                    if (doLog) {
                        activity.changes.each { commit ->
                            log "\t\t\t$commit"
                        }
                    }
                }
            }

            commitStatistics << stats
        }

        commitStatistics
    }

}

class DateTime {

    String date, time

    static DateTime parseFrom(xmlDateTime) {

        final javaDate = Date.parse(DATE_TIME_PATTERN, parsableDate(xmlDateTime))

        new DateTime (
            date : javaDate.format(DATE_PATTERN),
            time : javaDate.format(TIME_PATTERN)
        )
    }

    static String parsableDate(xmlDateTime) {
        // Date.parse can't digest this: 2013-11-30T19:34:11.280185Z
        // so remove the 'T' and cut off the tail after the seconds '.'
        xmlDateTime = (xmlDateTime as String).replace('T', ' ')
        xmlDateTime[0..<(xmlDateTime.indexOf('.'))]
    }

    static final DATE_PATTERN = 'yyyy-MM-dd'
    static final TIME_PATTERN = 'HH:mm:ss'
    static final DATE_TIME_PATTERN = DATE_PATTERN + ' ' + TIME_PATTERN
}
