package io.github.devmrko.companion;
import io.github.devmrko.companion.service.TestReports;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.*;
import static org.junit.jupiter.api.Assertions.*;
class TestReportsTest {
    byte[] archive(String xml) throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var tar=new TarArchiveOutputStream(new GZIPOutputStream(bytes))){var content=xml.getBytes(StandardCharsets.UTF_8);var entry=new TarArchiveEntry("module/target/surefire-reports/TEST.xml");entry.setSize(content.length);tar.putArchiveEntry(entry);tar.write(content);tar.closeArchiveEntry();}
        return bytes.toByteArray();
    }
    @Test void nestedSuitesDoNotDoubleCountAndFailuresAreVisible() throws Exception {
        var summary=TestReports.read(new ByteArrayInputStream(archive("<testsuites tests='999'><testsuite tests='999'><testcase name='ok'/><testcase name='bad' classname='Example'><failure>assertion</failure></testcase><testcase name='error'><error>crash</error></testcase><testcase name='skip'><skipped/></testcase></testsuite></testsuites>")));
        assertEquals(4,summary.tests());assertEquals(1,summary.failures());assertEquals(1,summary.errors());assertEquals(1,summary.skipped());assertEquals(2,summary.details().size());assertEquals("assertion",summary.details().getFirst().message());
    }
    @Test void missingAndEmptyAreNotPassing() throws Exception {
        assertEquals("MISSING",TestReports.read(new ByteArrayInputStream(archive("<pmd/>"))).state());
        assertEquals("EMPTY",TestReports.read(new ByteArrayInputStream(archive("<testsuite/>"))).state());
    }
    @Test void externalEntitiesAndMalformedReportsAreRejected() throws Exception {
        byte[] xxe=archive("<!DOCTYPE testsuite [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><testsuite><testcase>&x;</testcase></testsuite>");
        assertThrows(Exception.class,()->TestReports.read(new ByteArrayInputStream(xxe)));
        byte[] malformed=archive("<testsuite>");assertThrows(Exception.class,()->TestReports.read(new ByteArrayInputStream(malformed)));
    }
    @Test void actualArchiveWhenExplicitlyProvided() throws Exception {
        String path=System.getProperty("companion.report-fixture");if(path==null)return;
        try(var in=new FileInputStream(path)){var summary=TestReports.read(in);System.out.println("ACTUAL_REPORT "+summary);assertTrue(summary.tests()>0);}
    }
}
