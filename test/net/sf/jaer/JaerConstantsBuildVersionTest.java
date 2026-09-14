package net.sf.jaer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JaerConstantsBuildVersionTest {

    @Test
    public void parseGitCommitFromLabeledBuildFile() {
        String text = "3.5.0\n"
                + "git.commit=779018875c0b405e61b258f872db69a7179635a\n"
                + "git.describe=3.5.0-1-g7790188\n"
                + "git.subject=3.5.0\n";
        assertEquals("779018875c0b405e61b258f872db69a7179635a", JaerConstants.parseGitCommitId(text));
        assertEquals("3.5.0-1-g7790188", JaerConstants.parseLabeledValue(text, "git.describe"));
        assertEquals("3.5.0", JaerConstants.parseLabeledValue(text, "git.subject"));
    }

    @Test
    public void parseGitCommitPrefersLaterWorkingTreeSha() {
        String text = "3.5.0\n"
                + "git.commit=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"
                + "\nWorking tree /tmp/jaer\n"
                + "git.commit=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n";
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", JaerConstants.parseGitCommitId(text));
    }

    @Test
    public void parseGitCommitIgnoresUnavailable() {
        String text = "3.5.0\ngit.commit=(unavailable)\n";
        assertNull(JaerConstants.parseGitCommitId(text));
        assertNull(JaerConstants.parseLabeledValue(text, "git.commit"));
    }

    @Test
    public void buildIdentityRowsAreUserOrderedTsv() {
        String raw = "3.5.0\n"
                + "git.commit=779018875c0b405e61b258f872db69a7179635a\n"
                + "git.describe=3.5.0-1-g7790188\n"
                + "git.subject=Add About identity table\n"
                + "git.date=2026-09-14T17:41:20-04:00\n"
                + "git.author=Tobi Delbruck\n"
                + "git.url=https://github.com/SensorsINI/jaer/commit/779018875c0b405e61b258f872db69a7179635a\n"
                + "Built September 14 2026 at 1755 by tobid\n"
                + "os.name=Windows 11\n"
                + "os.version=10.0\n"
                + "java.version=25.0.3\n"
                + "java.vendor=Eclipse Adoptium\n";
        java.util.List<String[]> rows = JaerConstants.buildIdentityRows(raw, "Windows 11, Java 25", null);
        assertEquals("Version", rows.get(0)[0]);
        assertEquals("3.5.0", rows.get(0)[1]);
        assertEquals("Revision", rows.get(1)[0]);
        assertEquals("3.5.0-1-g7790188", rows.get(1)[1]);
        assertEquals("Last change", rows.get(2)[0]);
        assertEquals("Date", rows.get(3)[0]);
        assertEquals("2026-09-14 17:41:20-04:00", rows.get(3)[1]);
        assertEquals("Commit", rows.get(5)[0]);
        String tsv = JaerConstants.formatBuildIdentityTable(rows);
        assertTrue(tsv.startsWith("Field\tValue\n"));
        assertTrue(tsv.contains("Version\t3.5.0\n"));
        assertTrue(tsv.contains("Commit\t779018875c0b405e61b258f872db69a7179635a\n"));
    }
}
